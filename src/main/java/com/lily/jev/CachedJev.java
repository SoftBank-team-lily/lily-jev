package com.lily.jev;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 같은 질문·같은 상태의 답을 정해 둔 시간 동안 다시 쓴다.
 * 미리보기와 실제 실행이 같은 상태로 물으면 같은 결론을 낸다 (모델은 같은 입력에도 확신도가 달라질 수 있다).
 * 빈 답(실패·확신 부족)도 다시 쓴다. 그래야 미리보기에서 규칙으로 정한 결론이 실행에서 모델 답으로 바뀌지 않는다.
 * 상태를 JSON 으로 바꿀 수 없으면 저장하지 않고 그대로 묻는다.
 */
public final class CachedJev implements Jev {

    private record Entry(Optional<Answer> answer, Instant expiresAt) {
    }

    // 맵에 넣은 순서와 상관없이 같은 내용이면 같은 키가 되게 정렬한다
    private static final ObjectMapper CANONICAL = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    private final Jev delegate;
    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;
    private final Map<String, Entry> entries;

    public CachedJev(Jev delegate, Duration ttl, int maxEntries) {
        this(delegate, ttl, maxEntries, Clock.systemUTC());
    }

    CachedJev(Jev delegate, Duration ttl, int maxEntries, Clock clock) {
        if (ttl.isNegative() || ttl.isZero() || maxEntries < 1) {
            throw new IllegalArgumentException("ttl 과 maxEntries 는 0 보다 커야 한다");
        }
        this.delegate = delegate;
        this.ttl = ttl;
        this.maxEntries = maxEntries;
        this.clock = clock;
        // 오래 안 쓴 것부터 지운다
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                return size() > CachedJev.this.maxEntries;
            }
        };
    }

    @Override
    public Optional<Answer> ask(Map<String, ?> state, Question question) {
        String key = key(state, question);
        if (key == null) {
            return delegate.ask(state, question);
        }
        Optional<Answer> stored = fresh(key);
        if (stored != null) {
            return stored;
        }
        Optional<Answer> answer = delegate.ask(state, question);
        synchronized (entries) {
            // 같은 질문을 동시에 물었으면 먼저 저장된 답으로 맞춘다
            Entry other = entries.get(key);
            if (other != null && other.expiresAt().isAfter(clock.instant())) {
                return other.answer();
            }
            entries.put(key, new Entry(answer, clock.instant().plus(ttl)));
        }
        return answer;
    }

    @Override
    public boolean available() {
        return delegate.available();
    }

    private Optional<Answer> fresh(String key) {
        synchronized (entries) {
            Entry entry = entries.get(key);
            if (entry == null) {
                return null;
            }
            if (!entry.expiresAt().isAfter(clock.instant())) {
                entries.remove(key);
                return null;
            }
            return entry.answer();
        }
    }

    private static String key(Map<String, ?> state, Question question) {
        try {
            Map<String, Object> material = new LinkedHashMap<>();
            material.put("type", question instanceof Question.Choice ? "choice" : "noul");
            material.put("id", question.id());
            material.put("instructions", question.instructions());
            if (question instanceof Question.Choice choice) {
                material.put("criteria", choice.criteria());
            }
            material.put("state", state);
            byte[] canonical = CANONICAL.writeValueAsString(material).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (Exception e) {
            return null;
        }
    }
}
