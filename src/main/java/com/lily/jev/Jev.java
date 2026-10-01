package com.lily.jev;

import java.util.Map;
import java.util.Optional;

/**
 * 한 번의 구조화 판정. 키가 없거나, 시간 안에 답이 없거나, 선택 질문의 확신이 기준보다 낮으면 빈 값을 돌려준다.
 * 호출한 쪽은 그때 기존 규칙을 그대로 쓴다.
 */
public interface Jev {

    Optional<Answer> ask(Map<String, ?> state, Question question);

    /** 키가 없어 호출하지 않으면 false. 그때는 로그도 남기지 않는다. */
    default boolean available() {
        return true;
    }

    static Jev disabled() {
        return new Jev() {
            @Override
            public Optional<Answer> ask(Map<String, ?> state, Question question) {
                return Optional.empty();
            }

            @Override
            public boolean available() {
                return false;
            }
        };
    }
}
