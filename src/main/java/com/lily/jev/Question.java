package com.lily.jev;

import java.util.Map;

/** 한 요청에 실리는 질문 하나. 선택지는 호출한 쪽이 허용한 값만 담는다. */
public sealed interface Question permits Question.Choice, Question.Noul {

    String id();

    String instructions();

    /** 허용한 키 중 하나를 고른다. */
    record Choice(String id, String instructions, Map<String, String> criteria) implements Question {
    }

    /** 예라고 볼 확률(0~1)을 받는다. */
    record Noul(String id, String instructions) implements Question {
    }
}
