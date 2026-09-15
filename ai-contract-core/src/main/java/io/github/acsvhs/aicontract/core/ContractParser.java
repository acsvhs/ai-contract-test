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
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;

public final class ContractParser {
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private static final Pattern SECRET_NAME = Pattern.compile(
            "(?i).*(authorization|proxy[_-]?authorization|api[_-]?key|access[_-]?token|secret|password|cookie).*");
    private final ObjectMapper yamlMapper =
            new ObjectMapper(new YAMLFactory()).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final Consumer<String> warningSink;

    public ContractParser() {
        this(message -> LoggerFactory.getLogger(ContractParser.class).warn(message));
    }

    public ContractParser(Consumer<String> warningSink) {
        this.warningSink = java.util.Objects.requireNonNull(warningSink, "warningSink");
    }

    public ContractSuite parse(Path path, Map<String, String> providedVariables) {
        var absolutePath = path.toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(absolutePath)) {
                throw new ContractConfigurationException(absolutePath + ": contract file does not exist");
            }
            var yaml = Files.readString(absolutePath);
            JsonNode root = yamlMapper.readTree(yaml);
            new ContractSchemaValidator().validate(yaml, absolutePath);
            warnAboutHardcodedSecrets(root, absolutePath);
            var variables = new java.util.HashMap<String, String>();
            var declaredVariables = root == null ? null : root.get("variables");
            if (declaredVariables != null && declaredVariables.isObject()) {
                declaredVariables
                        .properties()
                        .forEach(entry ->
                                variables.put(entry.getKey(), entry.getValue().asText()));
            }
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

    private void warnAboutHardcodedSecrets(JsonNode root, Path file) {
        inspectSecretValues(root.path("variables"), "$.variables", file, false);
        inspectSecretValues(root.path("target").path("headers"), "$.target.headers", file, false);
        var cases = root.path("cases");
        for (int index = 0; index < cases.size(); index++) {
            var request = cases.path(index).path("request");
            inspectSecretValues(request.path("headers"), "$.cases[" + index + "].request.headers", file, false);
            inspectSecretValues(request.path("body"), "$.cases[" + index + "].request.body", file, true);
        }
    }

    private void inspectSecretValues(JsonNode node, String path, Path file, boolean recursive) {
        if (!node.isObject()) {
            return;
        }
        node.properties().forEach(entry -> {
            var childPath = path + "." + entry.getKey();
            var value = entry.getValue();
            if (SECRET_NAME.matcher(entry.getKey()).matches()
                    && value.isTextual()
                    && !VARIABLE.matcher(value.textValue()).find()) {
                warningSink.accept(file.toAbsolutePath().normalize()
                        + ": warning: probable hardcoded secret at "
                        + childPath
                        + "; use an environment variable such as ${NAME}");
            }
            if (recursive && value.isObject()) {
                inspectSecretValues(value, childPath, file, true);
            }
        });
    }

    private void interpolate(JsonNode node, Map<String, String> provided, String file) {
        if (node instanceof ObjectNode object) {
            object.properties().forEach(entry -> {
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
