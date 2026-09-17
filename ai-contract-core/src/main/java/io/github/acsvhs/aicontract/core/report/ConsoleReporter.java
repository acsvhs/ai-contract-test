package io.github.acsvhs.aicontract.core.report;

import io.github.acsvhs.aicontract.model.SuiteResult;
import java.io.PrintWriter;
import java.nio.file.Path;

public final class ConsoleReporter implements ContractReporter {
    private final PrintWriter output;

    public ConsoleReporter(PrintWriter output) {
        this.output = output;
    }

    @Override
    public void report(SuiteResult result, Path reportDirectory) {
        output.println("AI Contract Test");
        output.println("Suite: " + result.suiteName());
        output.printf(
                "%d contracts executed - %d passed, %d failed%n",
                result.cases().size(), result.passedCount(), result.cases().size() - result.passedCount());
        for (var caseResult : result.cases()) {
            if (caseResult.runs() > 1) {
                output.printf(
                        java.util.Locale.ROOT,
                        "%s: %d/%d passed (%.1f%%)%s%n",
                        caseResult.caseId(),
                        caseResult.passedRuns(),
                        caseResult.runs(),
                        caseResult.passRate() * 100,
                        caseResult.flaky() ? " flaky" : "");
            }
            if (!caseResult.passed()) {
                output.println();
                output.println("FAILED: " + caseResult.caseId());
                caseResult.assertions().stream()
                        .filter(assertion -> !assertion.passed())
                        .forEach(assertion -> output.printf(
                                "  %s: %s (expected %s, actual %s)%n",
                                assertion.type(), assertion.message(), assertion.expected(), assertion.actual()));
            }
        }
        output.flush();
    }
}
