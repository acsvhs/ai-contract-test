package io.github.acsvhs.aicontract.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class AiContractCliTest {
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            var response = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
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
    void returnsZeroWhenAllAssertionsPass(@TempDir Path directory) throws Exception {
        assertEquals(0, run(writeContract(directory, baseUrl, 200)));
    }

    @Test
    void returnsOneWhenAnAssertionFails(@TempDir Path directory) throws Exception {
        assertEquals(1, run(writeContract(directory, baseUrl, 201)));
    }

    @Test
    void returnsTwoForAnInvalidContract(@TempDir Path directory) throws Exception {
        var contract = directory.resolve("invalid.yaml");
        Files.writeString(contract, "version: \"1\"\n");
        assertEquals(2, run(contract));
    }

    @Test
    void returnsThreeForAnInfrastructureError(@TempDir Path directory) throws Exception {
        server.stop(0);
        assertEquals(3, run(writeContract(directory, baseUrl, 200)));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    private int run(Path contract) {
        return new CommandLine(new AiContractCli()).execute("run", contract.toString(), "--report", "console");
    }

    private Path writeContract(Path directory, String targetBaseUrl, int expectedStatus) throws Exception {
        var contract = directory.resolve("contract.yaml");
        Files.writeString(
                contract,
                """
                version: "1"
                suite:
                  name: cli-e2e
                  defaultTimeoutMs: 1000
                target:
                  type: http
                  baseUrl: %s
                cases:
                  - id: health
                    request:
                      method: GET
                      path: /health
                    assertions:
                      - type: httpStatus
                        equals: %d
                """
                        .formatted(targetBaseUrl, expectedStatus));
        return contract;
    }
}
