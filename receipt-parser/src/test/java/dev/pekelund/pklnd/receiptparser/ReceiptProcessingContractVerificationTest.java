package dev.pekelund.pklnd.receiptparser;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.PactFolder;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.spring.junit5.MockMvcTestTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@Provider("receipt-processor")
@PactFolder("pacts")
@WebMvcTest(controllers = ReceiptProcessingController.class)
class ReceiptProcessingContractVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReceiptParsingHandler receiptParsingHandler;

    @BeforeEach
    void configureTarget(PactVerificationContext context) {
        context.setTarget(new MockMvcTestTarget(mockMvc));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyPactInteractions(PactVerificationContext context) {
        context.verifyInteraction();
    }
}
