package io.github.acsvhs.aicontract.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.acsvhs.aicontract.core.report.ConsoleReporter;
import io.github.acsvhs.aicontract.core.report.JsonReporter;
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
        assertTrue(console.toString().contains("[REDACTED]"));
        assertTrue(Files.readString(directory.resolve("report.json")).contains("[REDACTED]"));
    }
}
