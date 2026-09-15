package io.github.acsvhs.aicontract.model;

import java.util.List;

public record CaseResult(String caseId, boolean passed, long durationMs, List<AssertionResult> assertions) {
    public CaseResult {
        assertions = List.copyOf(assertions);
    }
}
