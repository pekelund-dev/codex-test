package dev.pekelund.pklnd;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityVerificationTests {

    @Test
    void webModulesShouldRespectDeclaredBoundaries() {
        ApplicationModules.of(ResponsiveAuthAppApplication.class).verify();
    }
}
