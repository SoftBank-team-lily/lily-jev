package com.lily.jev;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpJevTest {

    @Test
    void 오류이거나_시간이_아니면_빈_답을_돌려_기존_규칙을_유지하게_한다() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/systemone", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] payload = """
                    {"answers":{"stack":{"type":"choice","choice":"pom.xml","confidence":0.4}}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();
        try {
            HttpJev jev = new HttpJev(HttpClient.newHttpClient(),
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/systemone"),
                    "test-key", 0.8);
            Optional<Answer> answer = jev.ask(Map.of("pom.xml", "<project>"),
                    new Question.Choice("stack", "어느 파일인가", Map.of("pom.xml", "maven")));

            assertTrue(answer.isEmpty());
            assertTrue(body.get().contains("\"type\":\"choice\""));
            assertTrue(body.get().contains("jev-latest"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void 지정한_모델_이름으로_묻고_비우면_기본_모델을_쓴다() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/systemone", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] payload = """
                    {"answers":{"stack":{"type":"choice","choice":"pom.xml","confidence":0.9}}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();
        try {
            java.net.URI endpoint = java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/systemone");
            Question.Choice stack = new Question.Choice("stack", "어느 파일인가", Map.of("pom.xml", "maven"));

            new HttpJev(HttpClient.newHttpClient(), endpoint, "test-key", 0.8, "jev-2026-10").ask(Map.of(), stack);
            assertTrue(body.get().contains("\"model\":\"jev-2026-10\""));

            new HttpJev(HttpClient.newHttpClient(), endpoint, "test-key", 0.8, " ").ask(Map.of(), stack);
            assertTrue(body.get().contains("\"model\":\"" + HttpJev.DEFAULT_MODEL + "\""));
        } finally {
            server.stop(0);
        }
    }
}
