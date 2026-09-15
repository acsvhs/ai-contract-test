package io.github.acsvhs.aicontract.recorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.acsvhs.aicontract.core.ContractConfigurationException;
import io.github.acsvhs.aicontract.core.DefaultSecretRedactor;
import io.github.acsvhs.aicontract.core.TargetAdapter;
import io.github.acsvhs.aicontract.model.AiResponseMetadata;
import io.github.acsvhs.aicontract.model.ContractRequest;
import io.github.acsvhs.aicontract.model.TargetDefinition;
import io.github.acsvhs.aicontract.model.TargetResponse;
import io.github.acsvhs.aicontract.model.TokenUsage;
import io.github.acsvhs.aicontract.model.ToolCall;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordingTargetAdapterTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void recordsSanitizedResponseAndReplaysWithoutCallingNetwork() throws Exception {
        var calls = new AtomicInteger();
        TargetAdapter live = adapter(
                calls,
                new TargetResponse(
                        200,
                        Map.of("Authorization", List.of("Bearer top-secret")),
                        "{\"answer\":\"top-secret\"}",
                        42,
                        new AiResponseMetadata(
                                List.of(new ToolCall("lookup", "{\"token\":\"top-secret\"}")),
                                new TokenUsage(8, 2, 10))));
        var target = new TargetDefinition("http", "http://127.0.0.1:9", Map.of("Authorization", "Bearer top-secret"));
        var request = request("top-secret");
        var redactor = new DefaultSecretRedactor(List.of("top-secret"));

        new RecordingTargetAdapter(live, ExecutionMode.RECORD, temporaryDirectory, redactor)
                .execute(target, request, 1000, "safe-case");
        assertEquals(1, calls.get());
        var cassette =
                Files.readString(Files.list(temporaryDirectory).findFirst().orElseThrow());
        assertFalse(cassette.contains("top-secret"));
        assertFalse(cassette.toLowerCase(java.util.Locale.ROOT).contains("authorization"));

        TargetAdapter noNetwork = adapter(calls, null);
        var replay = new RecordingTargetAdapter(noNetwork, ExecutionMode.REPLAY, temporaryDirectory, redactor)
                .execute(target, request, 1000, "safe-case");
        assertEquals(1, calls.get());
        assertEquals(200, replay.status());
        assertEquals(
                "[REDACTED]",
                new ObjectMapper().readTree(replay.body()).path("answer").asText());
        assertEquals(10, replay.metadata().tokenUsage().totalTokens());
        assertTrue(replay.metadata().replayed());
    }

    @Test
    void rejectsReplayWhenTheSanitizedRequestChanges() throws Exception {
        var calls = new AtomicInteger();
        var target = new TargetDefinition("http", "http://127.0.0.1:9", Map.of());
        var redactor = new DefaultSecretRedactor(List.of());
        var recorder = new RecordingTargetAdapter(
                adapter(calls, new TargetResponse(200, Map.of(), "ok", 1)),
                ExecutionMode.RECORD,
                temporaryDirectory,
                redactor);
        recorder.execute(target, request("first"), 1000, "case");

        var replay =
                new RecordingTargetAdapter(adapter(calls, null), ExecutionMode.REPLAY, temporaryDirectory, redactor);
        assertThrows(
                ContractConfigurationException.class, () -> replay.execute(target, request("changed"), 1000, "case"));
        assertEquals(1, calls.get());
    }

    private ContractRequest request(String message) throws Exception {
        return new ContractRequest(
                "POST",
                "/chat",
                Map.of("X-Test", "yes"),
                Map.of(),
                new ObjectMapper().readTree("{\"message\":\"" + message + "\"}"));
    }

    private TargetAdapter adapter(AtomicInteger calls, TargetResponse response) {
        return new TargetAdapter() {
            @Override
            public String type() {
                return "http";
            }

            @Override
            public TargetResponse execute(TargetDefinition target, ContractRequest request, int timeoutMs) {
                calls.incrementAndGet();
                if (response == null) {
                    throw new AssertionError("network adapter must not be called during replay");
                }
                return response;
            }
        };
    }
}
