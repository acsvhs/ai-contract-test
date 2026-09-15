package io.github.acsvhs.aicontract.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
            schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(schemaData, InputFormat.JSON);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load bundled contract schema", exception);
        }
    }

    public void validate(JsonNode contract, Path file) {
        if (contract == null) {
            throw new ContractConfigurationException(file.toAbsolutePath().normalize()
                    + ": contract schema validation failed: $: document must not be empty");
        }
        var errors = schema.validate(contract.toString(), InputFormat.JSON);
        if (!errors.isEmpty()) {
            var details = errors.stream()
                    .map(error -> error.getInstanceLocation() + ": " + error.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new ContractConfigurationException(
                    file.toAbsolutePath().normalize() + ": contract schema validation failed: " + details);
        }
    }
}
