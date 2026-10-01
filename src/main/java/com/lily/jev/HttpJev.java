package com.lily.jev;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** TypeSafe Jev {@code POST /v1/systemone}. 실패는 예외로 올리지 않고 빈 답으로 돌려준다. */
public final class HttpJev implements Jev {

    static final URI ENDPOINT = URI.create("https://api.typesafe.ai/v1/systemone");

    private final HttpClient http;
    private final URI endpoint;
    private final String apiKey;
    private final double minChoiceConfidence;
    private final ObjectMapper json = new ObjectMapper();

    public HttpJev(String apiKey, double minChoiceConfidence) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                ENDPOINT, apiKey, minChoiceConfidence);
    }

    HttpJev(HttpClient http, URI endpoint, String apiKey, double minChoiceConfidence) {
        this.http = http;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.minChoiceConfidence = minChoiceConfidence;
    }

    @Override
    public Optional<Answer> ask(Map<String, ?> state, Question question) {
        try {
            ObjectNode body = json.createObjectNode();
            body.put("model", "jev-latest");
            body.set("state", json.valueToTree(state));
            ObjectNode questions = body.putObject("questions");
            ObjectNode spec = questions.putObject(question.id());
            spec.put("instructions", question.instructions());
            if (question instanceof Question.Choice choice) {
                spec.put("type", "choice");
                spec.set("criteria", json.valueToTree(choice.criteria()));
            } else {
                spec.put("type", "noul");
            }
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(4))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            return Answers.parse(json.readTree(response.body()), question, minChoiceConfidence);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
