package io.github.acsvhs.aicontract.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.acsvhs.aicontract.core.ContractConfigurationException;
import io.github.acsvhs.aicontract.model.SuiteResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

final class ReportComparison {
    private ReportComparison() {}

    static boolean compare(SuiteResult current, Path previousFile) throws IOException {
        JsonNode root = new ObjectMapper().readTree(previousFile.toFile());
        if (!"1".equals(root.path("reportVersion").asText())
                || !root.path("result").path("cases").isArray()) {
            throw new ContractConfigurationException("Invalid baseline report: " + previousFile);
        }
        Map<String, Double> previous = new HashMap<>();
        for (var item : root.path("result").path("cases")) {
            String id = item.path("caseId").asText();
            double rate = item.has("passRate")
                    ? item.path("passRate").asDouble()
                    : (item.path("passed").asBoolean() ? 1.0 : 0.0);
            previous.put(id, rate);
        }
        boolean noRegression = true;
        for (var item : current.cases()) {
            var before = previous.get(item.caseId());
            if (before == null) {
                throw new ContractConfigurationException("Baseline has no case '" + item.caseId() + "'");
            }
            double delta = item.passRate() - before;
            System.out.printf(
                    java.util.Locale.ROOT,
                    "%s: %.1f%% -> %.1f%% (%+.1f pp)%n",
                    item.caseId(),
                    before * 100,
                    item.passRate() * 100,
                    delta * 100);
            if (delta < -1e-9) noRegression = false;
        }
        return noRegression;
    }
}
