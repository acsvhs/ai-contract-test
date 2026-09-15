package io.github.acsvhs.aicontract.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.acsvhs.aicontract.core.ContractExecutionException;
import io.github.acsvhs.aicontract.model.ContractRequest;
import io.github.acsvhs.aicontract.model.TargetDefinition;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpTargetAdapterTest {
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", exchange -> {
            var requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var response = (exchange.getRequestMethod() + " "
                            + exchange.getRequestURI().getQuery() + " " + requestBody)
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsRequestToLocalServerAndCapturesResponse() throws Exception {
        var body = new ObjectMapper().readTree("{\"message\":\"hello\"}");
        var request = new ContractRequest(
                "POST", "/echo", Map.of("Content-Type", "application/json"), Map.of("q", "a b"), body);
        var response = new HttpTargetAdapter().execute(new TargetDefinition("http", baseUrl, Map.of()), request, 1000);
        assertEquals(201, response.status());
        assertTrue(response.body().contains("q=a+b"));
        assertTrue(response.body().contains("hello"));
    }

    @Test
    void enforcesTheConfiguredResponseSizeLimit() {
        var request = new ContractRequest("GET", "/echo", Map.of(), Map.of(), null);
        var target = new TargetDefinition("http", baseUrl, Map.of(), 4);
        var exception = assertThrows(
                ContractExecutionException.class, () -> new HttpTargetAdapter().execute(target, request, 1000));
        assertTrue(exception.getMessage().contains("configured 4 byte safety limit"));
    }
}
