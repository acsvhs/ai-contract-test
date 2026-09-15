package io.github.acsvhs.aicontract.model;

import java.util.List;
import java.util.Map;

public record TargetResponse(int status, Map<String, List<String>> headers, String body, long durationMs) {
    public TargetResponse {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        body = body == null ? "" : body;
    }
}
