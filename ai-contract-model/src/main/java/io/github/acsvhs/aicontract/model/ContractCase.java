package io.github.acsvhs.aicontract.model;

import java.util.List;

public record ContractCase(
        String id,
        String description,
        List<String> tags,
        ContractRequest request,
        List<AssertionDefinition> assertions) {
    public ContractCase {
        tags = tags == null ? List.of() : List.copyOf(tags);
        assertions = assertions == null ? List.of() : List.copyOf(assertions);
    }
}
