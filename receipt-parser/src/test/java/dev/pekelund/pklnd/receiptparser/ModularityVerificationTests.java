package dev.pekelund.pklnd.receiptparser;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
class ModularityVerificationTests {

    @Test
    void modulesShouldRespectDeclaredBoundaries() {
        ApplicationModules.of(ReceiptProcessorApplication.class).verify();
    }
}
