package io.github.acsvhs.aicontract.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.acsvhs.aicontract.model.ContractSuite;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ContractParser {
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private final ObjectMapper yamlMapper =
            new ObjectMapper(new YAMLFactory()).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public ContractSuite parse(Path path, Map<String, String> providedVariables) {
        var absolutePath = path.toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(absolutePath)) {
                throw new ContractConfigurationException(absolutePath + ": contract file does not exist");
            }
            var yaml = Files.readString(absolutePath);
            JsonNode root = yamlMapper.readTree(yaml);
            var variables = new java.util.HashMap<String, String>();
            var declaredVariables = root == null ? null : root.get("variables");
            if (declaredVariables != null && declaredVariables.isObject()) {
                declaredVariables
                        .fields()
                        .forEachRemaining(entry ->
                                variables.put(entry.getKey(), entry.getValue().asText()));
            }
            new ContractSchemaValidator().validate(yaml, absolutePath);
            variables.putAll(providedVariables);
            interpolate(root, variables, absolutePath.toString());
            var contract = yamlMapper.treeToValue(root, ContractSuite.class);
            new ContractValidator().validate(contract, absolutePath);
            return contract;
        } catch (ContractConfigurationException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            var location = exception.getLocation();
            var where =
                    location == null ? "" : " at line " + location.getLineNr() + ", column " + location.getColumnNr();
            var logicalReference = exception instanceof JsonMappingException mapping
                            && !mapping.getPath().isEmpty()
                    ? " [" + mapping.getPathReference() + "]"
                    : "";
            throw new ContractConfigurationException(
                    absolutePath
                            + ": invalid contract"
                            + where
                            + logicalReference
                            + ": "
                            + exception.getOriginalMessage(),
                    exception);
        } catch (IOException exception) {
            throw new ContractConfigurationException(
                    absolutePath + ": cannot read contract: " + exception.getMessage(), exception);
        }
    }

    private void interpolate(JsonNode node, Map<String, String> provided, String file) {
        if (node instanceof ObjectNode object) {
            object.fields().forEachRemaining(entry -> {
                var value = entry.getValue();
                if (value.isTextual()) {
                    object.set(entry.getKey(), TextNode.valueOf(resolve(value.textValue(), provided, file)));
                } else {
                    interpolate(value, provided, file);
                }
            });
        } else if (node instanceof ArrayNode array) {
            for (int index = 0; index < array.size(); index++) {
                var value = array.get(index);
                if (value.isTextual()) {
                    array.set(index, TextNode.valueOf(resolve(value.textValue(), provided, file)));
                } else {
                    interpolate(value, provided, file);
                }
            }
        }
    }

    private String resolve(String input, Map<String, String> provided, String file) {
        Matcher matcher = VARIABLE.matcher(input);
        var result = new StringBuilder();
        while (matcher.find()) {
            var name = matcher.group(1);
            var value = provided.containsKey(name) ? provided.get(name) : System.getenv(name);
            if (value == null) {
                throw new ContractConfigurationException(file + ": unresolved variable ${" + name + "}");
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
