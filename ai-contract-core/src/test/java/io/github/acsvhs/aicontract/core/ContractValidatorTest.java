package io.github.acsvhs.aicontract.core;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.acsvhs.aicontract.model.AssertionDefinition;
import io.github.acsvhs.aicontract.model.ContractCase;
import io.github.acsvhs.aicontract.model.ContractRequest;
import io.github.acsvhs.aicontract.model.ContractSuite;
import io.github.acsvhs.aicontract.model.SuiteDefinition;
import io.github.acsvhs.aicontract.model.TargetDefinition;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContractValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ContractValidator validator = new ContractValidator();
    private final Path file = Path.of("contract.yaml");

    @Test
    void rejectsMissingTopLevelSectionsAndUnsupportedVersions() {
        assertError(null, "document must not be empty");
        assertError(new ContractSuite("2", null, Map.of(), null, List.of()), "only version \"1\" is supported");
    }

    @Test
    void rejectsInvalidSuiteAndTargets() throws Exception {
        var badSuite = new ContractSuite(
                "1",
                new SuiteDefinition(" ", null, 0, List.of()),
                Map.of(),
                new TargetDefinition("smtp", "not a URL", Map.of()),
                List.of(validCase("one", definition("{\"type\":\"httpStatus\",\"equals\":200}"))));
        var exception = assertThrows(ContractConfigurationException.class, () -> validator.validate(badSuite, file));
        assertTrue(exception.getMessage().contains("suite.name"));
        assertTrue(exception.getMessage().contains("suite.defaultTimeoutMs"));
        assertTrue(exception.getMessage().contains("unsupported adapter 'smtp'"));
        assertTrue(exception.getMessage().contains("absolute HTTP(S) URL"));
    }

    @Test
    void rejectsInvalidCasesAndDuplicateIds() throws Exception {
        var missingParts = new ContractCase("same", null, List.of(), null, List.of());
        var invalidPath = new ContractCase(
                "same",
                null,
                List.of(),
                new ContractRequest("GET", "relative", Map.of(), Map.of(), null),
                List.of(definition("{\"type\":\"unknown\"}")));
        var suite = suite(List.of(missingParts, invalidPath));
        var exception = assertThrows(ContractConfigurationException.class, () -> validator.validate(suite, file));
        assertTrue(exception.getMessage().contains("duplicate case ID 'same'"));
        assertTrue(exception.getMessage().contains("request: is required"));
        assertTrue(exception.getMessage().contains("request.path: must start with '/'"));
        assertTrue(exception.getMessage().contains("assertions: at least one assertion is required"));
        assertTrue(exception.getMessage().contains("unsupported assertion 'unknown'"));
    }

    @Test
    void rejectsInvalidAssertionParameters() throws Exception {
        var cases = List.of(
                validCase("status-both", definition("{\"type\":\"httpStatus\",\"equals\":200,\"oneOf\":[201]}")),
                validCase("status-range", definition("{\"type\":\"httpStatus\",\"equals\":99}")),
                validCase("status-empty", definition("{\"type\":\"httpStatus\",\"oneOf\":[]}")),
                validCase("contains", definition("{\"type\":\"contains\"}")),
                validCase("regex-empty", definition("{\"type\":\"regexAbsent\",\"patterns\":[]}")),
                validCase("regex-invalid", definition("{\"type\":\"regexAbsent\",\"patterns\":[\"[\"]}")),
                validCase("latency", definition("{\"type\":\"maxLatency\",\"milliseconds\":-1}")));
        var exception =
                assertThrows(ContractConfigurationException.class, () -> validator.validate(suite(cases), file));
        assertTrue(exception.getMessage().contains("requires exactly one"));
        assertTrue(exception.getMessage().contains("status codes from 100 to 599"));
        assertTrue(exception.getMessage().contains("must be a string"));
        assertTrue(exception.getMessage().contains("must be a non-empty array"));
        assertTrue(exception.getMessage().contains("invalid regular expression"));
        assertTrue(exception.getMessage().contains("must be a non-negative integer"));
    }

    private ContractSuite suite(List<ContractCase> cases) {
        return new ContractSuite(
                "1",
                new SuiteDefinition("demo", null, 100, List.of()),
                Map.of(),
                new TargetDefinition("http", "http://localhost", Map.of()),
                cases);
    }

    private ContractCase validCase(String id, AssertionDefinition assertion) {
        return new ContractCase(
                id, null, List.of(), new ContractRequest("GET", "/", Map.of(), Map.of(), null), List.of(assertion));
    }

    private AssertionDefinition definition(String json) throws Exception {
        return mapper.readValue(json, AssertionDefinition.class);
    }

    private void assertError(ContractSuite suite, String expected) {
        var exception = assertThrows(ContractConfigurationException.class, () -> validator.validate(suite, file));
        assertTrue(exception.getMessage().contains(expected));
    }
}
