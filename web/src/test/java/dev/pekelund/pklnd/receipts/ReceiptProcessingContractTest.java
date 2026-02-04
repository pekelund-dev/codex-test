package dev.pekelund.pklnd.receipts;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.storage.StoredReceiptReference;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PactConsumerTestExt.class)
class ReceiptProcessingContractTest {

    @BeforeAll
    static void configurePactOutput() {
        System.setProperty("pact.rootDir",
            Path.of("..", "receipt-parser", "src", "test", "resources", "pacts").toString());
    }

    @Pact(consumer = "pklnd-web", provider = "receipt-processor")
    RequestResponsePact storageEventContract(PactDslWithProvider builder) {
        PactDslJsonBody payload = new PactDslJsonBody();
        payload
            .stringValue("bucket", "receipts-bucket")
            .stringValue("name", "uploads/receipt-1.pdf");
        payload.object("metadata")
            .stringValue(ReceiptOwner.METADATA_OWNER_ID, "user-123")
            .stringValue(ReceiptOwner.METADATA_OWNER_DISPLAY_NAME, "Anna Andersson")
            .stringValue(ReceiptOwner.METADATA_OWNER_EMAIL, "anna@example.com")
            .closeObject();
        payload.object("owner")
            .stringValue("id", "user-123")
            .stringValue("displayName", "Anna Andersson")
            .stringValue("email", "anna@example.com")
            .closeObject();

        return builder
            .given("receipt storage event is accepted")
            .uponReceiving("a Cloud Storage finalize event")
            .path("/events/storage")
            .method("POST")
            .matchHeader("Content-Type", "application/json.*", "application/json")
            .headers(Map.of(
                "ce-specversion", "1.0",
                "ce-type", "google.cloud.storage.object.v1.finalized",
                "ce-source", "projects/_/buckets/receipts-bucket/objects/uploads/receipt-1.pdf",
                "ce-subject", "objects/uploads/receipt-1.pdf"
            ))
            .matchHeader("ce-id", "[0-9a-fA-F-]{36}", "123e4567-e89b-12d3-a456-426614174000")
            .matchHeader("ce-time", "\\d{4}-\\d{2}-\\d{2}T.*Z", "2024-01-01T00:00:00Z")
            .body(payload)
            .willRespondWith()
            .status(202)
            .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "storageEventContract", pactVersion = PactSpecVersion.V3)
    void notifyUploadsSendsCloudEvent(MockServer mockServer) {
        ReceiptProcessingProperties properties = new ReceiptProcessingProperties();
        properties.setBaseUrl(mockServer.getUrl());
        properties.setEventPath("/events/storage");
        properties.setUseIdToken(false);

        ReceiptProcessingClient client = new ReceiptProcessingClient(new RestTemplate(), properties);
        ReceiptOwner owner = new ReceiptOwner("user-123", "Anna Andersson", "anna@example.com");
        StoredReceiptReference reference = new StoredReceiptReference("receipts-bucket", "uploads/receipt-1.pdf", owner);

        ReceiptProcessingClient.ProcessingResult result = client.notifyUploads(List.of(reference));

        assertThat(result.requestedCount()).isEqualTo(1);
        assertThat(result.succeededCount()).isEqualTo(1);
        assertThat(result.failures()).isEmpty();
    }
}
