package io.github.acsvhs.aicontract.core.report;

import io.github.acsvhs.aicontract.model.CaseResult;
import io.github.acsvhs.aicontract.model.SuiteResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

public final class JunitXmlReporter implements ContractReporter {
    @Override
    public void report(SuiteResult result, Path reportDirectory) throws IOException {
        Files.createDirectories(reportDirectory);
        var reportFile = reportDirectory.resolve("TEST-ai-contract.xml");
        try (var output = Files.newBufferedWriter(reportFile, StandardCharsets.UTF_8)) {
            var xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
            try {
                writeReport(xml, result);
            } finally {
                xml.close();
            }
        } catch (XMLStreamException exception) {
            throw new IOException("Cannot write JUnit XML report", exception);
        }
    }

    private void writeReport(XMLStreamWriter xml, SuiteResult result) throws XMLStreamException {
        var failures = result.cases().stream()
                .filter(caseResult -> !caseResult.passed())
                .count();
        var totalDurationMs =
                result.cases().stream().mapToLong(CaseResult::durationMs).sum();
        xml.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
        xml.writeStartElement("testsuite");
        xml.writeAttribute("name", validXml(result.suiteName()));
        xml.writeAttribute("tests", Integer.toString(result.cases().size()));
        xml.writeAttribute("failures", Long.toString(failures));
        xml.writeAttribute("errors", "0");
        xml.writeAttribute("skipped", "0");
        xml.writeAttribute("time", seconds(totalDurationMs));
        for (var caseResult : result.cases()) {
            writeCase(xml, result.suiteName(), caseResult);
        }
        xml.writeEndElement();
        xml.writeEndDocument();
    }

    private void writeCase(XMLStreamWriter xml, String suiteName, CaseResult caseResult) throws XMLStreamException {
        xml.writeStartElement("testcase");
        xml.writeAttribute("name", validXml(caseResult.caseId()));
        xml.writeAttribute("classname", validXml(suiteName));
        xml.writeAttribute("time", seconds(caseResult.durationMs()));
        if (!caseResult.passed()) {
            var failures = caseResult.assertions().stream()
                    .filter(assertion -> !assertion.passed())
                    .map(assertion -> assertion.type()
                            + ": "
                            + assertion.message()
                            + " (expected "
                            + assertion.expected()
                            + ", actual "
                            + assertion.actual()
                            + ")")
                    .toList();
            xml.writeStartElement("failure");
            xml.writeAttribute("type", "ai-contract");
            xml.writeAttribute("message", failures.size() + " contract assertion(s) failed");
            xml.writeCharacters(validXml(String.join(System.lineSeparator(), failures)));
            xml.writeEndElement();
        }
        xml.writeEndElement();
    }

    private String seconds(long milliseconds) {
        return String.format(Locale.ROOT, "%.3f", milliseconds / 1000.0);
    }

    private String validXml(String value) {
        var sanitized = new StringBuilder();
        value.codePoints()
                .filter(codePoint -> codePoint == 0x9
                        || codePoint == 0xA
                        || codePoint == 0xD
                        || (codePoint >= 0x20 && codePoint <= 0xD7FF)
                        || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
                        || (codePoint >= 0x10000 && codePoint <= 0x10FFFF))
                .forEach(sanitized::appendCodePoint);
        return sanitized.toString();
    }
}
