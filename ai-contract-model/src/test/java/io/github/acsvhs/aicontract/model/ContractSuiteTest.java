package io.github.acsvhs.aicontract.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ContractSuiteTest {
    @Test
    void defensivelyCopiesCases() {
        var cases = new java.util.ArrayList<ContractCase>();
        var suite = new ContractSuite("1", new SuiteDefinition("demo", null, null, null), Map.of(), null, cases);
        cases.add(null);
        assertThrows(UnsupportedOperationException.class, () -> suite.cases().add(null));
    }
}
