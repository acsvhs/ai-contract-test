package io.github.acsvhs.aicontract.core.assertion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.github.acsvhs.aicontract.core.ContractAssertion;
import io.github.acsvhs.aicontract.core.ContractConfigurationException;
import io.github.acsvhs.aicontract.core.ExecutionContext;
import io.github.acsvhs.aicontract.model.AssertionDefinition;
import io.github.acsvhs.aicontract.model.AssertionResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Collectors;

public final class JsonSchemaAssertion implements ContractAssertion {
    private static final long MAX_SCHEMA_BYTES = 1024 * 1024;
    private static final int ACTUAL_LIMIT = 500;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String type() {
        return "jsonSchema";
    }

    @Override
    public AssertionResult evaluate(AssertionDefinition definition, ExecutionContext context) {
        var schemaFile = resolveSchema(
                context.contractDirectory(), definition.parameter("file").asText());
        try {
            if (Files.size(schemaFile) > MAX_SCHEMA_BYTES) {
                throw new ContractConfigurationException("JSON Schema exceeds the 1 MiB limit: " + schemaFile);
            }
            var schemaData = Files.readString(schemaFile, StandardCharsets.UTF_8);
            rejectRemoteReferences(mapper.readTree(schemaData));
            var schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12, builder -> {})
                    .getSchema(schemaData, InputFormat.JSON);
            try {
                if (mapper.readTree(context.response().body()) == null) {
                    return invalidJson();
                }
            } catch (JsonProcessingException exception) {
                return invalidJson();
            }
            var errors = schema.validate(
                    context.response().body(),
                    InputFormat.JSON,
                    executionContext -> executionContext.executionConfig(config -> config.locale(Locale.ENGLISH)));
            if (errors.isEmpty()) {
                return AssertionResult.passed(type());
            }
            var details = errors.stream().map(Object::toString).sorted().collect(Collectors.joining("; "));
            details = details.substring(0, Math.min(details.length(), ACTUAL_LIMIT));
            return AssertionResult.failed(
                    type(), definition.parameter("file").asText(), details, "Response did not match JSON Schema");
        } catch (ContractConfigurationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ContractConfigurationException("Cannot load JSON Schema '" + schemaFile + "'", exception);
        }
    }

    private AssertionResult invalidJson() {
        return AssertionResult.failed(
                type(), "valid JSON matching schema", "invalid JSON", "Response body was not valid JSON");
    }

    private Path resolveSchema(Path contractDirectory, String configuredPath) {
        try {
            var base = contractDirectory.toRealPath();
            var candidate = base.resolve(configuredPath).normalize();
            if (!candidate.startsWith(base) || !Files.isRegularFile(candidate)) {
                throw new ContractConfigurationException(
                        "JSON Schema must be a regular file inside the contract directory: " + configuredPath);
            }
            var realCandidate = candidate.toRealPath();
            if (!realCandidate.startsWith(base)) {
                throw new ContractConfigurationException(
                        "JSON Schema must remain inside the contract directory: " + configuredPath);
            }
            return realCandidate;
        } catch (IOException exception) {
            throw new ContractConfigurationException("Cannot resolve JSON Schema '" + configuredPath + "'", exception);
        }
    }

    private void rejectRemoteReferences(JsonNode node) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                if ("$ref".equals(entry.getKey())
                        && entry.getValue().isTextual()
                        && (entry.getValue().asText().toLowerCase(Locale.ROOT).startsWith("http://")
                                || entry.getValue()
                                        .asText()
                                        .toLowerCase(Locale.ROOT)
                                        .startsWith("https://"))) {
                    throw new ContractConfigurationException("Remote JSON Schema references are not allowed");
                }
                rejectRemoteReferences(entry.getValue());
            });
        } else if (node.isArray()) {
            node.forEach(this::rejectRemoteReferences);
        }
    }
}
