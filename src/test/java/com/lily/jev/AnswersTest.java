package com.lily.jev;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswersTest {

    private final ObjectMapper json = new ObjectMapper();
    private final Question.Choice stack = new Question.Choice("stack", "어느 파일인가",
            Map.of("pom.xml", "maven", "package.json", "node"));

    @Test
    void 확신이_기준_이상이고_허용한_키일_때만_선택을_받는다() throws Exception {
        Optional<Answer> sure = Answers.parse(json.readTree("""
                {"answers":{"stack":{"type":"choice","choice":"package.json","confidence":0.91}}}
                """), stack, 0.8);

        assertEquals("package.json", sure.orElseThrow().choice());

        assertTrue(Answers.parse(json.readTree("""
                {"answers":{"stack":{"type":"choice","choice":"package.json","confidence":0.5}}}
                """), stack, 0.8).isEmpty());
        assertTrue(Answers.parse(json.readTree("""
                {"answers":{"stack":{"type":"choice","choice":"go.mod","confidence":0.99}}}
                """), stack, 0.8).isEmpty());
    }

    @Test
    void 예_아니오_확률은_그대로_돌려준다() throws Exception {
        Question.Noul rollback = new Question.Noul("rollback", "롤백할 장애인가");
        Optional<Answer> answer = Answers.parse(json.readTree("""
                {"answers":{"rollback":{"type":"noul","noul":0.12}}}
                """), rollback, 0.8);

        assertEquals(0.12, answer.orElseThrow().noul());
    }
}
