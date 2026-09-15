package io.github.acsvhs.aicontract.core.report;

import io.github.acsvhs.aicontract.model.SuiteResult;
import java.io.IOException;
import java.nio.file.Path;

public interface ContractReporter {
    void report(SuiteResult result, Path reportDirectory) throws IOException;
}
