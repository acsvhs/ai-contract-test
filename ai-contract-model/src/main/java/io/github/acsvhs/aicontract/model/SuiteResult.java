package io.github.acsvhs.aicontract.model;

import java.util.List;

public record SuiteResult(String suiteName, List<CaseResult> cases) {
    public SuiteResult {
        cases = List.copyOf(cases);
    }

    public boolean passed() {
        return cases.stream().allMatch(CaseResult::passed);
    }

    public long passedCount() {
        return cases.stream().filter(CaseResult::passed).count();
    }
}
