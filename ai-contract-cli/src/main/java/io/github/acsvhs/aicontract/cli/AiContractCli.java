package io.github.acsvhs.aicontract.cli;

import io.github.acsvhs.aicontract.core.ContractConfigurationException;
import io.github.acsvhs.aicontract.core.ContractExecutionException;
import io.github.acsvhs.aicontract.core.ContractParser;
import io.github.acsvhs.aicontract.core.ContractRunner;
import io.github.acsvhs.aicontract.core.DefaultSecretRedactor;
import io.github.acsvhs.aicontract.core.assertion.ContainsAssertion;
import io.github.acsvhs.aicontract.core.assertion.HttpStatusAssertion;
import io.github.acsvhs.aicontract.core.assertion.JsonPathAssertion;
import io.github.acsvhs.aicontract.core.assertion.JsonSchemaAssertion;
import io.github.acsvhs.aicontract.core.assertion.MaxLatencyAssertion;
import io.github.acsvhs.aicontract.core.assertion.RegexAbsentAssertion;
import io.github.acsvhs.aicontract.core.report.ConsoleReporter;
import io.github.acsvhs.aicontract.core.report.JsonReporter;
import io.github.acsvhs.aicontract.http.HttpTargetAdapter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "ai-contract", mixinStandardHelpOptions = true, subcommands = AiContractCli.RunCommand.class)
public final class AiContractCli implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new AiContractCli()).execute(args);
        System.exit(exitCode);
    }

    @Command(name = "run", description = "Run an AI contract YAML file.")
    static final class RunCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Contract YAML file")
        private Path contractFile;

        @Option(names = "--report", split = ",", defaultValue = "console", description = "console,json")
        private List<String> reports;

        @Option(names = "--report-dir", defaultValue = "target/ai-contract", description = "Report output directory")
        private Path reportDirectory;

        @Override
        public Integer call() {
            var redactor = new DefaultSecretRedactor(List.of());
            try {
                var contract = new ContractParser().parse(contractFile, Map.of());
                redactor = new DefaultSecretRedactor(secretVariableValues(contract.variables()));
                var runner = new ContractRunner(
                        List.of(new HttpTargetAdapter()),
                        List.of(
                                new HttpStatusAssertion(),
                                new ContainsAssertion(),
                                new RegexAbsentAssertion(),
                                new MaxLatencyAssertion(),
                                new JsonSchemaAssertion(),
                                new JsonPathAssertion()),
                        redactor);
                var result = runner.run(contract, contractFile);
                for (var report : reports) {
                    switch (report) {
                        case "console" -> new ConsoleReporter(new PrintWriter(System.out, true))
                                .report(result, reportDirectory);
                        case "json" -> new JsonReporter().report(result, reportDirectory);
                        default -> throw new ContractConfigurationException("Unknown reporter '" + report + "'");
                    }
                }
                return result.passed() ? 0 : 1;
            } catch (ContractConfigurationException exception) {
                System.err.println("Invalid contract: " + redactor.redact(exception.getMessage()));
                return 2;
            } catch (ContractExecutionException | IOException exception) {
                System.err.println("Execution error: " + redactor.redact(exception.getMessage()));
                return 3;
            }
        }

        private List<String> secretVariableValues(Map<String, String> variables) {
            return variables.entrySet().stream()
                    .filter(entry -> entry.getKey().matches("(?i).*(KEY|TOKEN|SECRET|PASSWORD).*$"))
                    .map(Map.Entry::getValue)
                    .toList();
        }
    }
}
