package io.github.acsvhs.aicontract.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContractParserTest {
    @Test
    void parsesAndInterpolatesExplicitVariables() throws Exception {
        var resource = Path.of(getClass().getResource("/contracts/valid.yaml").toURI());
        var suite = new ContractParser().parse(resource, Map.of("TEST_BASE_URL", "http://localhost:9876"));
        assertEquals("parser-test", suite.suite().name());
        assertEquals("http://localhost:9876", suite.target().baseUrl());
    }

    @Test
    void usesVariablesDeclaredInContract(@TempDir Path directory) throws Exception {
        var file = directory.resolve("variables.yaml");
        Files.writeString(
                file,
                "version: '1'\nsuite: {name: demo}\nvariables: {HOST: '127.0.0.1'}\n"
                        + "target: {type: http, baseUrl: 'http://${HOST}:8080'}\n"
                        + "cases: [{id: one, request: {path: /}, assertions: [{type: httpStatus, equals: 200}]}]\n");
        var suite = new ContractParser().parse(file, Map.of());
        assertEquals("http://127.0.0.1:8080", suite.target().baseUrl());
    }

    @Test
    void rejectsUnknownFields(@TempDir Path directory) throws Exception {
        var file = directory.resolve("unknown.yaml");
        Files.writeString(
                file,
                "version: '1'\nsuite: {name: demo, typo: true}\ntarget: {type: http, baseUrl: 'http://localhost'}\ncases: []\n");
        var exception =
                assertThrows(ContractConfigurationException.class, () -> new ContractParser().parse(file, Map.of()));
        assertTrue(exception.getMessage().contains("typo"));
    }

    @Test
    void rejectsDuplicateCaseIds(@TempDir Path directory) throws Exception {
        var file = directory.resolve("duplicate.yaml");
        Files.writeString(
                file,
                "version: '1'\nsuite: {name: demo}\ntarget: {type: http, baseUrl: 'http://localhost'}\ncases:\n"
                        + "  - {id: same, request: {path: /}, assertions: [{type: httpStatus, equals: 200}]}\n"
                        + "  - {id: same, request: {path: /}, assertions: [{type: httpStatus, equals: 200}]}\n");
        var exception =
                assertThrows(ContractConfigurationException.class, () -> new ContractParser().parse(file, Map.of()));
        assertTrue(exception.getMessage().contains("duplicate case ID"));
    }

    @Test
    void rejectsInvalidRegularExpressions(@TempDir Path directory) throws Exception {
        var file = directory.resolve("regex.yaml");
        Files.writeString(
                file,
                "version: '1'\nsuite: {name: demo}\ntarget: {type: http, baseUrl: 'http://localhost'}\n"
                        + "cases: [{id: one, request: {path: /}, assertions: [{type: regexAbsent, patterns: ['[']}]}]\n");
        var exception =
                assertThrows(ContractConfigurationException.class, () -> new ContractParser().parse(file, Map.of()));
        assertTrue(exception.getMessage().contains("invalid regular expression"));
    }
}
