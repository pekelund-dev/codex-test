package dev.pekelund.pklnd;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityVerificationTests {

    @Test
    void coreModulesShouldVerifySuccessfully() {
        ApplicationModules.of("dev.pekelund.pklnd").verify();
    }
}
