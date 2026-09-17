package io.github.acsvhs.aicontract.model;

import java.util.List;

public record ContractCase(
        String id,
        String description,
        List<String> tags,
        ContractRequest request,
        List<AssertionDefinition> assertions,
        Integer repeat,
        Double minimumPassRate) {
    public ContractCase {
        tags = tags == null ? List.of() : List.copyOf(tags);
        assertions = assertions == null ? List.of() : List.copyOf(assertions);
    }

    public ContractCase(
            String id,
            String description,
            List<String> tags,
            ContractRequest request,
            List<AssertionDefinition> assertions) {
        this(id, description, tags, request, assertions, null, null);
    }

    public int effectiveRepeat() {
        return repeat == null ? 1 : repeat;
    }

    public double effectiveMinimumPassRate() {
        return minimumPassRate == null ? 1.0 : minimumPassRate;
    }
}
