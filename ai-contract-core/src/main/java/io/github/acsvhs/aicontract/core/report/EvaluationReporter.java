package io.github.acsvhs.aicontract.core.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.acsvhs.aicontract.model.SuiteResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Numeric evaluation scores and dataset summaries, without response text. */
public final class EvaluationReporter implements ContractReporter {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public void report(SuiteResult result, Path reportDirectory) throws IOException {
        Files.createDirectories(reportDirectory);
        var cases = new ArrayList<Map<String, Object>>();
        var groups = new LinkedHashMap<String, List<Double>>();
        for (var item : result.cases()) {
            var evaluations = new ArrayList<Map<String, Object>>();
            for (var assertion : item.assertions()) {
                if ("semanticSimilarity".equals(assertion.type()) || "llmJudge".equals(assertion.type())) {
                    evaluations.add(Map.of(
                            "type",
                            assertion.type(),
                            "passed",
                            assertion.passed(),
                            "score",
                            Double.parseDouble(assertion.actual()),
                            "minimum",
                            Double.parseDouble(assertion.expected().substring(3))));
                }
            }
            cases.add(Map.of(
                    "id",
                    item.caseId(),
                    "passed",
                    item.passed(),
                    "runs",
                    item.runs(),
                    "passRate",
                    item.passRate(),
                    "flaky",
                    item.flaky(),
                    "evaluations",
                    evaluations));
            int bracket = item.caseId().lastIndexOf('[');
            if (bracket > 0 && item.caseId().endsWith("]")) {
                groups.computeIfAbsent(item.caseId().substring(0, bracket), ignored -> new ArrayList<>())
                        .add(item.passRate());
            }
        }
        var datasets = new LinkedHashMap<String, Map<String, Object>>();
        groups.forEach((name, rates) -> datasets.put(
                name,
                Map.of(
                        "rows",
                        rates.size(),
                        "averagePassRate",
                        rates.stream()
                                .mapToDouble(Double::doubleValue)
                                .average()
                                .orElse(0))));
        mapper.writeValue(
                reportDirectory.resolve("evaluation.json").toFile(),
                Map.of("reportVersion", "1", "suite", result.suiteName(), "cases", cases, "datasets", datasets));
    }
}
