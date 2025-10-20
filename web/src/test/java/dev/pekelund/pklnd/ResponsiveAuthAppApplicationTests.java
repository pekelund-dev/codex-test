package dev.pekelund.pklnd;

import dev.pekelund.pklnd.support.TestReceiptProcessingConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {
    "gcs.bucket=test-bucket",
    "receipt.processing.pubsub-verification-token=test-token"
})
@Import(TestReceiptProcessingConfiguration.class)
class ResponsiveAuthAppApplicationTests {

	@Test
	void contextLoads() {
	}

}
