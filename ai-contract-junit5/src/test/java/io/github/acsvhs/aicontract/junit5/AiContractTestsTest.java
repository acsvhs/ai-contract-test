package io.github.acsvhs.aicontract.junit5;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiContractTestsTest {
    @TempDir
    private Path temporaryDirectory;

    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            var body = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void createsAndExecutesOneDynamicTestPerContractCase() throws Throwable {
        var contract = temporaryDirectory.resolve("contract.yaml");
        Files.writeString(
                contract,
                """
                version: "1"
                suite:
                  name: junit-consumer
                target:
                  type: http
                  baseUrl: ${TEST_BASE_URL}
                cases:
                  - id: passes
                    request: { path: /health }
                    assertions:
                      - { type: httpStatus, equals: 200 }
                  - id: fails
                    request: { path: /health }
                    assertions:
                      - { type: jsonPath, path: $.status, equals: wrong }
                """);

        var tests = AiContractTests.from(
                        contract,
                        Map.of(
                                "TEST_BASE_URL",
                                "http://127.0.0.1:" + server.getAddress().getPort()))
                .toList();

        assertEquals(2, tests.size());
        assertEquals("junit-consumer / passes", tests.getFirst().getDisplayName());
        assertDoesNotThrow(tests.getFirst().getExecutable()::execute);
        var error = assertThrows(AssertionError.class, tests.getLast().getExecutable()::execute);
        assertTrue(error.getMessage().contains("jsonPath"));
        assertTrue(error.getMessage().contains("expected \"wrong\""));
    }
}
