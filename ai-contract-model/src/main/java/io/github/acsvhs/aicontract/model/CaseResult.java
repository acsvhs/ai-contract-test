package io.github.acsvhs.aicontract.model;

import java.util.List;

public record CaseResult(
        String caseId,
        boolean passed,
        long durationMs,
        List<AssertionResult> assertions,
        int runs,
        int passedRuns,
        double passRate,
        boolean flaky) {
    public CaseResult {
        assertions = List.copyOf(assertions);
    }

    public CaseResult(String caseId, boolean passed, long durationMs, List<AssertionResult> assertions) {
        this(caseId, passed, durationMs, assertions, 1, passed ? 1 : 0, passed ? 1.0 : 0.0, false);
    }
}
