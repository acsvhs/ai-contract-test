package io.github.acsvhs.aicontract.maven;

import io.github.acsvhs.aicontract.core.ContractConfigurationException;
import io.github.acsvhs.aicontract.core.ContractExecutionException;
import io.github.acsvhs.aicontract.core.ContractParser;
import io.github.acsvhs.aicontract.core.ContractRunner;
import io.github.acsvhs.aicontract.core.DefaultSecretRedactor;
import io.github.acsvhs.aicontract.core.assertion.AllowedToolCallsAssertion;
import io.github.acsvhs.aicontract.core.assertion.ContainsAssertion;
import io.github.acsvhs.aicontract.core.assertion.ForbiddenToolCallsAssertion;
import io.github.acsvhs.aicontract.core.assertion.HttpStatusAssertion;
import io.github.acsvhs.aicontract.core.assertion.JsonPathAssertion;
import io.github.acsvhs.aicontract.core.assertion.JsonSchemaAssertion;
import io.github.acsvhs.aicontract.core.assertion.MaxEstimatedCostAssertion;
import io.github.acsvhs.aicontract.core.assertion.MaxLatencyAssertion;
import io.github.acsvhs.aicontract.core.assertion.MaxTokensAssertion;
import io.github.acsvhs.aicontract.core.assertion.RegexAbsentAssertion;
import io.github.acsvhs.aicontract.core.report.ConsoleReporter;
import io.github.acsvhs.aicontract.core.report.JunitXmlReporter;
import io.github.acsvhs.aicontract.http.HttpTargetAdapter;
import io.github.acsvhs.aicontract.model.CaseResult;
import io.github.acsvhs.aicontract.model.SuiteResult;
import io.github.acsvhs.aicontract.openai.OpenAiCompatibleTargetAdapter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "test", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public final class AiContractTestMojo extends AbstractMojo {
    @Parameter(
            property = "aiContract.contractsDirectory",
            defaultValue = "${project.basedir}/src/test/ai-contract",
            required = true)
    private File contractsDirectory;

    @Parameter(
            property = "aiContract.reportsDirectory",
            defaultValue = "${project.build.directory}/ai-contract",
            required = true)
    private File reportsDirectory;

    @Parameter(property = "aiContract.skip", defaultValue = "false")
    private boolean skip;

    public AiContractTestMojo() {}

    AiContractTestMojo(File contractsDirectory, File reportsDirectory, boolean skip) {
        this.contractsDirectory = contractsDirectory;
        this.reportsDirectory = reportsDirectory;
        this.skip = skip;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("AI contract tests are skipped");
            return;
        }
        var directory = contractsDirectory.toPath().toAbsolutePath().normalize();
        var contractFiles = contractFiles(directory);
        if (contractFiles.isEmpty()) {
            throw new MojoFailureException("No .yaml or .yml contracts found in " + directory);
        }

        var failedCases = new java.util.ArrayList<String>();
        var reportCases = new java.util.ArrayList<CaseResult>();
        for (var contractFile : contractFiles) {
            try {
                var contract = new ContractParser(getLog()::warn).parse(contractFile, Map.of());
                var redactor = new DefaultSecretRedactor(secretVariableValues(contract.variables()));
                var result = runner(redactor).run(contract, contractFile);
                new ConsoleReporter(new PrintWriter(System.out, true)).report(result, reportsDirectory.toPath());
                result.cases().stream()
                        .map(caseResult -> new CaseResult(
                                contractFile.getFileName() + ":" + caseResult.caseId(),
                                caseResult.passed(),
                                caseResult.durationMs(),
                                caseResult.assertions()))
                        .forEach(reportCases::add);
                result.cases().stream()
                        .filter(caseResult -> !caseResult.passed())
                        .map(caseResult -> contractFile.getFileName() + ":" + caseResult.caseId())
                        .forEach(failedCases::add);
            } catch (ContractConfigurationException exception) {
                throw new MojoFailureException("Invalid AI contract: " + exception.getMessage(), exception);
            } catch (ContractExecutionException exception) {
                throw new MojoExecutionException("AI contract execution failed: " + exception.getMessage(), exception);
            }
        }
        try {
            new JunitXmlReporter().report(new SuiteResult("ai-contract", reportCases), reportsDirectory.toPath());
        } catch (IOException exception) {
            throw new MojoExecutionException("Cannot write AI contract report: " + exception.getMessage(), exception);
        }
        if (!failedCases.isEmpty()) {
            throw new MojoFailureException("AI contract failures: " + String.join(", ", failedCases));
        }
    }

    private List<Path> contractFiles(Path directory) throws MojoExecutionException {
        if (!Files.isDirectory(directory)) {
            throw new MojoExecutionException("Contracts directory does not exist: " + directory);
        }
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> {
                        var name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                        return name.endsWith(".yaml") || name.endsWith(".yml");
                    })
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new MojoExecutionException("Cannot list contracts in " + directory, exception);
        }
    }

    private ContractRunner runner(DefaultSecretRedactor redactor) {
        return new ContractRunner(
                List.of(new HttpTargetAdapter(), new OpenAiCompatibleTargetAdapter()),
                List.of(
                        new HttpStatusAssertion(),
                        new ContainsAssertion(),
                        new RegexAbsentAssertion(),
                        new MaxLatencyAssertion(),
                        new JsonSchemaAssertion(),
                        new JsonPathAssertion(),
                        new AllowedToolCallsAssertion(),
                        new ForbiddenToolCallsAssertion(),
                        new MaxTokensAssertion(),
                        new MaxEstimatedCostAssertion()),
                redactor);
    }

    private List<String> secretVariableValues(Map<String, String> variables) {
        return variables.entrySet().stream()
                .filter(entry -> entry.getKey().matches("(?i).*(KEY|TOKEN|SECRET|PASSWORD).*$"))
                .map(Map.Entry::getValue)
                .toList();
    }
}
