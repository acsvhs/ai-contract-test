package io.github.acsvhs.aicontract.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.acsvhs.aicontract.model.CaseResult;
import io.github.acsvhs.aicontract.model.SuiteResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportComparisonTest {
    @TempDir
    Path directory;

    @Test
    void detectsPassRateRegression() throws Exception {
        var baseline = directory.resolve("report.json");
        Files.writeString(
                baseline,
                """
                {"reportVersion":"1","result":{"cases":[{"caseId":"answer","passRate":0.9}]}}
                """);
        var lower = new SuiteResult("model-b", List.of(new CaseResult("answer", true, 1, List.of(), 10, 8, 0.8, true)));
        var higher =
                new SuiteResult("model-b", List.of(new CaseResult("answer", true, 1, List.of(), 10, 10, 1.0, false)));
        assertFalse(ReportComparison.compare(lower, baseline));
        assertTrue(ReportComparison.compare(higher, baseline));
    }
}
