package io.github.acsvhs.aicontract.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.acsvhs.aicontract.core.report.ConsoleReporter;
import io.github.acsvhs.aicontract.core.report.JsonReporter;
import io.github.acsvhs.aicontract.core.report.JunitXmlReporter;
import io.github.acsvhs.aicontract.model.AssertionResult;
import io.github.acsvhs.aicontract.model.CaseResult;
import io.github.acsvhs.aicontract.model.SuiteResult;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RedactionReporterTest {
    @Test
    void redactsConfiguredAndNamedSecrets() {
        var redactor = new DefaultSecretRedactor(List.of("super-secret-value"));
        var redacted = redactor.redact("headers={Authorization=[Bearer abc123], Cookie=[session=cookie-secret]} "
                + "body={\"password\":\"body-secret\"} known=super-secret-value exception api_key=visible-no-more");
        assertFalse(redacted.contains("abc123"));
        assertFalse(redacted.contains("cookie-secret"));
        assertFalse(redacted.contains("body-secret"));
        assertFalse(redacted.contains("super-secret-value"));
        assertFalse(redacted.contains("visible-no-more"));
    }

    @Test
    void redactsBearerTokensJwtsAndPrivateKeysFromExceptionText() {
        var redactor = new DefaultSecretRedactor(List.of());
        var unsafe = "request failed: Bearer standalone-token eyJheader.payload.signature "
                + "-----BEGIN PRIVATE KEY-----\\nprivate-material\\n-----END PRIVATE KEY-----";

        var redacted = redactor.redact(unsafe);

        assertFalse(redacted.contains("standalone-token"));
        assertFalse(redacted.contains("eyJheader.payload.signature"));
        assertFalse(redacted.contains("private-material"));
        assertTrue(redacted.contains("[REDACTED]"));
    }

    @Test
    void reportersOnlyWriteAlreadySanitizedResults(@TempDir Path directory) throws Exception {
        var result = new SuiteResult(
                "safe-suite",
                List.of(new CaseResult(
                        "safe-case",
                        false,
                        1,
                        List.of(AssertionResult.failed("contains", "safe", "[REDACTED]", "missing")))));
        var console = new StringWriter();
        new ConsoleReporter(new PrintWriter(console)).report(result, directory);
        new JsonReporter().report(result, directory);
        new JunitXmlReporter().report(result, directory);
        assertTrue(console.toString().contains("[REDACTED]"));
        assertTrue(Files.readString(directory.resolve("report.json")).contains("[REDACTED]"));
        var junitReport = directory.resolve("TEST-ai-contract.xml");
        assertTrue(Files.readString(junitReport).contains("[REDACTED]"));
        var document = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(junitReport.toFile());
        assertTrue(document.getDocumentElement().getAttribute("failures").equals("1"));
    }

    @Test
    void junitXmlEscapesValuesAndRemovesInvalidCharacters(@TempDir Path directory) throws Exception {
        var result = new SuiteResult(
                "suite<&>\u0001",
                List.of(new CaseResult("case<&>", true, 1250, List.of(AssertionResult.passed("status")))));

        new JunitXmlReporter().report(result, directory);

        var document = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(directory.resolve("TEST-ai-contract.xml").toFile());
        assertTrue(document.getDocumentElement().getAttribute("name").equals("suite<&>"));
        assertTrue(document.getDocumentElement().getAttribute("time").equals("1.250"));
    }
}
