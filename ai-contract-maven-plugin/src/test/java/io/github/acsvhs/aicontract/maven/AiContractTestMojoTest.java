package io.github.acsvhs.aicontract.maven;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiContractTestMojoTest {
    @TempDir
    private Path temporaryDirectory;

    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            var body = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
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
    void verifyPassesAndFailsWithConsumerContracts() throws Exception {
        var contracts = Files.createDirectories(temporaryDirectory.resolve("contracts"));
        var reports = Files.createDirectories(temporaryDirectory.resolve("reports"));
        var mojo = new AiContractTestMojo(contracts.toFile(), reports.toFile(), false);

        writeContract(contracts.resolve("contract.yaml"), 200);
        assertDoesNotThrow(mojo::execute);

        writeContract(contracts.resolve("contract.yaml"), 201);
        assertThrows(MojoFailureException.class, mojo::execute);
    }

    @Test
    void rejectsMissingAndEmptyContractDirectories() throws Exception {
        var reports = temporaryDirectory.resolve("reports").toFile();
        assertThrows(MojoExecutionException.class, () -> new AiContractTestMojo(
                        temporaryDirectory.resolve("missing").toFile(), reports, false)
                .execute());
        assertThrows(
                MojoFailureException.class,
                () -> new AiContractTestMojo(temporaryDirectory.toFile(), reports, false).execute());
    }

    @Test
    void supportsSkippingExecution() {
        assertDoesNotThrow(() -> new AiContractTestMojo(null, null, true).execute());
    }

    private void writeContract(Path file, int expectedStatus) throws Exception {
        Files.writeString(
                file,
                """
                version: "1"
                suite:
                  name: maven-consumer
                target:
                  type: http
                  baseUrl: http://127.0.0.1:%d
                cases:
                  - id: health
                    request:
                      path: /health
                    assertions:
                      - type: httpStatus
                        equals: %d
                      - type: jsonPath
                        path: $.status
                        equals: ok
                """
                        .formatted(server.getAddress().getPort(), expectedStatus));
    }
}
