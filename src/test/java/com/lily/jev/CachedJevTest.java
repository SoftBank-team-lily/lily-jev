package com.lily.jev;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CachedJevTest {
    @Test
    void concurrentRequestsShareOnePendingCallAndOtherKeysStayIndependent() throws Exception {
        var entered=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        var calls=new AtomicInteger();
        Jev delegate=(state,question) -> {
            calls.incrementAndGet();
            if (state.get("key").equals("same")) {
                entered.countDown();
                try { if (!release.await(5,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return Optional.empty(); }
            }
            return Optional.of(new Answer("aws",null,.9));
        };
        var cached=new CachedJev(delegate,Duration.ofMinutes(1),16);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            var first=pool.submit(() -> cached.ask(Map.of("key","same"),cloud));
            assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));
            var second=pool.submit(() -> cached.ask(Map.of("key","same"),cloud));
            var other=pool.submit(() -> cached.ask(Map.of("key","other"),cloud));
            assertTrue(other.get(2,java.util.concurrent.TimeUnit.SECONDS).isPresent());
            Thread.sleep(50);
            assertEquals(2,calls.get());
            release.countDown();
            assertEquals(first.get(2,java.util.concurrent.TimeUnit.SECONDS),second.get(2,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(2,calls.get());
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test
    void failedOwnerClearsPendingEntryForTheNextCall() {
        var calls=new AtomicInteger();
        var cached=new CachedJev((s,q) -> {
            if (calls.incrementAndGet()==1) throw new IllegalStateException("upstream failure");
            return Optional.of(new Answer("gcp",null,.9));
        },Duration.ofMinutes(1),4);
        assertThrows(IllegalStateException.class,() -> cached.ask(Map.of(),cloud));
        assertEquals("gcp",cached.ask(Map.of(),cloud).orElseThrow().choice());
        assertEquals(2,calls.get());
    }

    private final Question.Choice cloud = new Question.Choice("cloud", "어디에 배포할까",
            Map.of("aws", "AWS", "gcp", "GCP", "hold", "보류"));

    /** 테스트에서 시간을 앞으로 돌린다 */
    private static final class MovingClock extends Clock {
        private Instant now = Instant.parse("2026-10-03T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** 부를 때마다 다른 확신도로 답하는 모델 */
    private static Jev counting(AtomicInteger calls) {
        return (state, question) -> Optional.of(new Answer("aws", null, 0.8 + 0.01 * calls.incrementAndGet()));
    }

    @Test
    void 같은_질문과_상태면_한_번만_묻고_같은_답을_돌려준다() {
        AtomicInteger calls = new AtomicInteger();
        CachedJev jev = new CachedJev(counting(calls), Duration.ofMinutes(10), 10, new MovingClock());
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("priority", "balanced");
        first.put("candidates", List.of(Map.of("provider", "aws", "p95Ms", 80)));
        Map<String, Object> reordered = new LinkedHashMap<>();
        reordered.put("candidates", List.of(Map.of("p95Ms", 80, "provider", "aws")));
        reordered.put("priority", "balanced");

        Answer preview = jev.ask(first, cloud).orElseThrow();
        Answer deploy = jev.ask(reordered, cloud).orElseThrow();

        assertEquals(1, calls.get());
        assertEquals(preview, deploy);
    }

    @Test
    void 상태나_질문이_다르거나_시간이_지나면_다시_묻는다() {
        AtomicInteger calls = new AtomicInteger();
        MovingClock clock = new MovingClock();
        CachedJev jev = new CachedJev(counting(calls), Duration.ofMinutes(10), 10, clock);

        jev.ask(Map.of("p95Ms", 80), cloud);
        jev.ask(Map.of("p95Ms", 81), cloud);
        jev.ask(Map.of("p95Ms", 80), new Question.Choice("cloud", "다른 지시", cloud.criteria()));
        assertEquals(3, calls.get());

        clock.advance(Duration.ofMinutes(10));
        jev.ask(Map.of("p95Ms", 80), cloud);
        assertEquals(4, calls.get());
    }

    @Test
    void 빈_답도_다시_써서_미리보기와_실행이_같은_결론을_낸다() {
        AtomicInteger calls = new AtomicInteger();
        Jev flaky = (state, question) -> calls.incrementAndGet() == 1
                ? Optional.empty()
                : Optional.of(new Answer("gcp", null, 0.95));
        CachedJev jev = new CachedJev(flaky, Duration.ofMinutes(10), 10, new MovingClock());

        assertTrue(jev.ask(Map.of("p95Ms", 80), cloud).isEmpty());
        assertTrue(jev.ask(Map.of("p95Ms", 80), cloud).isEmpty());
        assertEquals(1, calls.get());
    }

    @Test
    void JSON으로_바꿀_수_없는_상태는_저장하지_않고_그대로_묻는다() {
        AtomicInteger calls = new AtomicInteger();
        CachedJev jev = new CachedJev(counting(calls), Duration.ofMinutes(10), 10, new MovingClock());
        Map<String, Object> state = Map.of("value", new Object());

        jev.ask(state, cloud);
        jev.ask(state, cloud);

        assertEquals(2, calls.get());
    }

    @Test
    void 오래_안_쓴_항목부터_지운다() {
        AtomicInteger calls = new AtomicInteger();
        CachedJev jev = new CachedJev(counting(calls), Duration.ofMinutes(10), 2, new MovingClock());

        jev.ask(Map.of("n", 1), cloud);
        jev.ask(Map.of("n", 2), cloud);
        jev.ask(Map.of("n", 1), cloud);
        jev.ask(Map.of("n", 3), cloud);
        assertEquals(3, calls.get());

        jev.ask(Map.of("n", 1), cloud);
        assertEquals(3, calls.get());
        jev.ask(Map.of("n", 2), cloud);
        assertEquals(4, calls.get());
    }

    @Test
    void 키가_없는_모델은_계속_꺼진_상태로_보인다() {
        assertFalse(new CachedJev(Jev.disabled(), Duration.ofMinutes(1), 1).available());
        assertThrows(IllegalArgumentException.class, () -> new CachedJev(Jev.disabled(), Duration.ZERO, 1));
    }
}
