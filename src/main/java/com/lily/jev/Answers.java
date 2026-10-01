package com.lily.jev;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

final class Answers {

    private Answers() {
    }

    static Optional<Answer> parse(JsonNode root, Question question, double minChoiceConfidence) {
        JsonNode answer = root.path("answers").path(question.id());
        String type = answer.path("type").asText("");
        if ("choice".equals(type)) {
            String choice = answer.path("choice").asText("");
            double confidence = answer.path("confidence").asDouble(0);
            if (!(question instanceof Question.Choice allowed) || !allowed.criteria().containsKey(choice)) {
                return Optional.empty();
            }
            if (confidence < minChoiceConfidence) {
                return Optional.empty();
            }
            return Optional.of(new Answer(choice, null, confidence));
        }
        if ("noul".equals(type)) {
            if (!answer.hasNonNull("noul")) {
                return Optional.empty();
            }
            double noul = answer.path("noul").asDouble(-1);
            if (noul < 0 || noul > 1) {
                return Optional.empty();
            }
            return Optional.of(new Answer(null, noul, noul));
        }
        return Optional.empty();
    }
}
