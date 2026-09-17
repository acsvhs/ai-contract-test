package io.github.acsvhs.aicontract.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.acsvhs.aicontract.core.TargetAdapter;
import io.github.acsvhs.aicontract.model.ContractRequest;
import io.github.acsvhs.aicontract.model.TargetDefinition;
import io.github.acsvhs.aicontract.model.TargetResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NativeProviderTargetAdapterTest {
    @Test
    void normalizesNativeUsageAndToolCalls() throws Exception {
        check(
                "openai",
                "/v1/chat/completions",
                "{"
                        + "\"choices\":[{\"message\":{\"tool_calls\":[{\"function\":{\"name\":\"search\",\"arguments\":\"{\\\"q\\\":1}\"}}]}}],"
                        + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":4,\"total_tokens\":14}}",
                10,
                4,
                14);
        check(
                "anthropic",
                "/v1/messages",
                "{\"content\":[{\"type\":\"tool_use\",\"name\":\"search\",\"input\":{\"q\":1}}],"
                        + "\"usage\":{\"input_tokens\":10,\"output_tokens\":4}}",
                10,
                4,
                14);
        check(
                "gemini",
                "/v1beta/models/test:generateContent",
                "{\"candidates\":[{\"content\":{\"parts\":[{\"functionCall\":{\"name\":\"search\",\"args\":{\"q\":1}}}]}}],"
                        + "\"usageMetadata\":{\"promptTokenCount\":10,\"candidatesTokenCount\":4,\"totalTokenCount\":15}}",
                10,
                4,
                15);
    }

    private void check(String provider, String path, String body, int input, int output, int total) throws Exception {
        TargetAdapter fake = new TargetAdapter() {
            public String type() {
                return "http";
            }

            public TargetResponse execute(TargetDefinition target, ContractRequest request, int timeoutMs) {
                return new TargetResponse(200, Map.of(), body, 1);
            }
        };
        var adapter = new NativeProviderTargetAdapter(provider, fake);
        var request = new ContractRequest("POST", path, Map.of(), Map.of(), new ObjectMapper().readTree("{}"));
        var result = adapter.execute(new TargetDefinition(provider, "https://example.test", Map.of()), request, 100);
        assertEquals("search", result.metadata().toolCalls().getFirst().name());
        assertEquals("{\"q\":1}", result.metadata().toolCalls().getFirst().arguments());
        assertEquals(input, result.metadata().tokenUsage().inputTokens());
        assertEquals(output, result.metadata().tokenUsage().outputTokens());
        assertEquals(total, result.metadata().tokenUsage().totalTokens());
    }
}
