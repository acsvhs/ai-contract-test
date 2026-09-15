package io.github.acsvhs.aicontract.core;

import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.utils.JsonNodes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Collectors;

public final class ContractSchemaValidator {
    private static final String SCHEMA_RESOURCE = "/schema/ai-contract-v1.schema.json";
    private final com.networknt.schema.Schema schema;

    public ContractSchemaValidator() {
        try (var stream = ContractSchemaValidator.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Bundled contract schema is missing");
            }
            var schemaData = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            schema = SchemaRegistry.withDefaultDialect(
                            SpecificationVersion.DRAFT_2020_12,
                            builder -> builder.nodeReader(reader -> reader.locationAware()))
                    .getSchema(schemaData, InputFormat.JSON);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load bundled contract schema", exception);
        }
    }

    public void validate(String contract, Path file) {
        var errors = schema.validate(
                contract,
                InputFormat.YAML,
                executionContext -> executionContext.executionConfig(config -> config.locale(Locale.ENGLISH)));
        if (!errors.isEmpty()) {
            var details = errors.stream()
                    .map(error -> {
                        var logicalPath =
                                logicalPath(error.getInstanceLocation().toString(), error.getProperty());
                        var location = JsonNodes.tokenStreamLocationOf(error.getInstanceNode());
                        var sourceLocation = location == null
                                ? ""
                                : " (line " + location.getLineNr() + ", column " + location.getColumnNr() + ")";
                        return logicalPath + sourceLocation + ": " + error.getMessage();
                    })
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new ContractConfigurationException(
                    file.toAbsolutePath().normalize() + ": contract schema validation failed: " + details);
        }
    }

    private String logicalPath(String pointer, String property) {
        var path = new StringBuilder("$");
        if (pointer != null && !pointer.isBlank()) {
            for (var segment : pointer.split("/")) {
                if (segment.isEmpty()) {
                    continue;
                }
                var decoded = segment.replace("~1", "/").replace("~0", "~");
                if (decoded.chars().allMatch(Character::isDigit)) {
                    path.append('[').append(decoded).append(']');
                } else {
                    path.append('.').append(decoded);
                }
            }
        }
        if (property != null && !property.isBlank() && !path.toString().endsWith("." + property)) {
            path.append('.').append(property);
        }
        return path.toString();
    }
}
