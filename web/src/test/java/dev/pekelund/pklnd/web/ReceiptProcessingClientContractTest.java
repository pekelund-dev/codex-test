package dev.pekelund.pklnd.web;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import dev.pekelund.pklnd.receipts.ReceiptProcessingClient;
import dev.pekelund.pklnd.receipts.ReceiptProcessingProperties;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.storage.StoredReceiptReference;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * Consumer-side contract test verifying that {@link ReceiptProcessingClient} sends requests
 * in the format expected by the receipt-parser {@code POST /events/storage} endpoint.
 *
 * <p>Validates request/response shape for the key API contract between web and receipt-parser.
 */
class ReceiptProcessingClientContractTest {

    private MockRestServiceServer mockServer;
    private ReceiptProcessingClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);

        ReceiptProcessingProperties properties = new ReceiptProcessingProperties();
        properties.setBaseUrl("http://receipt-parser");
        properties.setEventPath("/events/storage");
        properties.setUseIdToken(false);

        client = new ReceiptProcessingClient(restTemplate, properties);
    }

    @Test
    void notifyUploads_sendsBucketAndNameInPayload() {
        mockServer.expect(requestTo("http://receipt-parser/events/storage"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Content-Type", Matchers.startsWith(MediaType.APPLICATION_JSON_VALUE)))
            .andExpect(header("ce-type", "google.cloud.storage.object.v1.finalized"))
            .andExpect(content().string(Matchers.containsString("\"bucket\":\"my-bucket\"")))
            .andExpect(content().string(Matchers.containsString("\"name\":\"receipts/sample.pdf\"")))
            .andRespond(withStatus(HttpStatus.ACCEPTED));

        ReceiptOwner owner = new ReceiptOwner("user-1", "Test User", "test@example.com");
        StoredReceiptReference ref = new StoredReceiptReference("my-bucket", "receipts/sample.pdf", owner);

        client.notifyUploads(List.of(ref));

        mockServer.verify();
    }

    @Test
    void reparseReceipt_sendsReparseMetadata() {
        mockServer.expect(requestTo("http://receipt-parser/events/storage"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("ce-type", "google.cloud.storage.object.v1.finalized"))
            .andExpect(content().string(Matchers.containsString("\"bucket\":\"my-bucket\"")))
            .andExpect(content().string(Matchers.containsString("\"name\":\"receipts/reparse.pdf\"")))
            .andExpect(content().string(Matchers.containsString("\"metadata\":")))
            .andRespond(withStatus(HttpStatus.ACCEPTED));

        client.reparseReceipt("my-bucket", "receipts/reparse.pdf",
            new ReceiptOwner("user-1", "Test User", "test@example.com"));

        mockServer.verify();
    }
}
