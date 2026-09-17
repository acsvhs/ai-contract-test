package io.github.acsvhs.aicontract.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.acsvhs.aicontract.core.report.EvaluationReporter;
import io.github.acsvhs.aicontract.model.AssertionResult;
import io.github.acsvhs.aicontract.model.CaseResult;
import io.github.acsvhs.aicontract.model.SuiteResult;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvaluationReporterTest {
    @TempDir
    Path directory;

    @Test
    void reportsScoresAndDatasetAveragesWithoutResponseText() throws Exception {
        var result = new SuiteResult(
                "demo",
                List.of(
                        new CaseResult(
                                "faq[first]",
                                true,
                                3,
                                List.of(new AssertionResult("semanticSimilarity", true, ">= 0.8", "0.900000", "")),
                                2,
                                2,
                                1,
                                false),
                        new CaseResult(
                                "faq[second]",
                                false,
                                3,
                                List.of(new AssertionResult("llmJudge", false, ">= 0.8", "0.400000", "low")),
                                2,
                                1,
                                0.5,
                                true)));
        new EvaluationReporter().report(result, directory);
        var report =
                new ObjectMapper().readTree(directory.resolve("evaluation.json").toFile());
        assertEquals(
                0.75,
                report.path("datasets").path("faq").path("averagePassRate").asDouble());
        assertEquals(
                0.9,
                report.path("cases")
                        .get(0)
                        .path("evaluations")
                        .get(0)
                        .path("score")
                        .asDouble());
        assertFalse(report.toString().contains("response text"));
    }
}
