package io.github.acsvhs.aicontract.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.acsvhs.aicontract.core.ContractConfigurationException;
import io.github.acsvhs.aicontract.model.ContractRequest;
import io.github.acsvhs.aicontract.model.TargetDefinition;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleTargetAdapterTest {
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(OpenAiCompatibleTargetAdapter.CHAT_COMPLETIONS_PATH, exchange -> {
            var requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var response =
                    """
                    {"choices":[{"message":{"content":"ok","tool_calls":[{"type":"function","function":{"name":"lookupOrder","arguments":"{\\\"id\\\":42}"}}]}}],"usage":{"prompt_tokens":7,"completion_tokens":3,"total_tokens":10}}
                    """
                            .getBytes(StandardCharsets.UTF_8);
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
    void executesChatCompletionsAgainstAConfigurableBaseUrl() throws Exception {
        var body = new ObjectMapper()
                .readTree(
                        """
                {"model":"local-model","messages":[{"role":"user","content":"hello"}]}
                """);
        var request = new ContractRequest(
                "POST", OpenAiCompatibleTargetAdapter.CHAT_COMPLETIONS_PATH, Map.of(), Map.of(), body);
        var response = new OpenAiCompatibleTargetAdapter()
                .execute(new TargetDefinition("openai-compatible", baseUrl, Map.of()), request, 1000);

        assertEquals(200, response.status());
        assertEquals("lookupOrder", response.metadata().toolCalls().getFirst().name());
        assertTrue(response.metadata().toolCalls().getFirst().arguments().contains("42"));
        assertEquals(7, response.metadata().tokenUsage().inputTokens());
        assertEquals(3, response.metadata().tokenUsage().outputTokens());
        assertEquals(10, response.metadata().tokenUsage().totalTokens());
    }

    @Test
    void rejectsRequestsOutsideTheSupportedProtocolSubset() throws Exception {
        var body = new ObjectMapper().readTree("{\"model\":\"local-model\",\"messages\":[]}");
        var request = new ContractRequest("POST", "/v1/responses", Map.of(), Map.of(), body);

        assertThrows(ContractConfigurationException.class, () -> new OpenAiCompatibleTargetAdapter()
                .execute(new TargetDefinition("openai-compatible", baseUrl, Map.of()), request, 1000));
    }
}
