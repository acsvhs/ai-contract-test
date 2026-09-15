package io.github.acsvhs.aicontract.core.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.acsvhs.aicontract.model.SuiteResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class JsonReporter implements ContractReporter {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public void report(SuiteResult result, Path reportDirectory) throws IOException {
        Files.createDirectories(reportDirectory);
        mapper.writeValue(
                reportDirectory.resolve("report.json").toFile(), Map.of("reportVersion", "1", "result", result));
    }
}
