package io.github.acsvhs.aicontract.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public record ContractRequest(
        String method, String path, Map<String, String> headers, Map<String, String> query, JsonNode body) {
    public ContractRequest {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        query = query == null ? Map.of() : Map.copyOf(query);
    }
}
