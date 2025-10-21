package dev.pekelund.pklnd.receipts;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.storage.StoredReceiptReference;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

class ReceiptProcessingClientTest {

    private ReceiptProcessingProperties properties;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private ReceiptProcessingClient client;

    @BeforeEach
    void setUp() {
        properties = new ReceiptProcessingProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://localhost");
        properties.setEventPath("/events/storage");
        properties.setUseIdToken(false);

        ClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        restTemplate = new RestTemplate(requestFactory);
        server = MockRestServiceServer.createServer(restTemplate);
        client = new ReceiptProcessingClient(restTemplate, properties);
    }

    @Test
    void notifiesProcessorForEachUploadedReceipt() {
        ReceiptOwner owner = new ReceiptOwner("user-123", "Anna Andersson", "anna@example.com");
        StoredReceiptReference first = new StoredReceiptReference("bucket", "one.pdf", owner);
        StoredReceiptReference second = new StoredReceiptReference("bucket", "two.pdf");

        server.expect(ExpectedCount.once(), MockRestRequestMatchers.requestTo("http://localhost/events/storage"))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
            .andExpect(MockRestRequestMatchers.content().json("{" +
                "\"bucket\":\"bucket\"," +
                "\"name\":\"one.pdf\"," +
                "\"metadata\":{" +
                "\"receipt.owner.id\":\"user-123\"," +
                "\"receipt.owner.displayName\":\"Anna Andersson\"," +
                "\"receipt.owner.email\":\"anna@example.com\"}}"))
            .andExpect(MockRestRequestMatchers.header("ce-type", "google.cloud.storage.object.v1.finalized"))
            .andRespond(MockRestResponseCreators.withSuccess());

        server.expect(ExpectedCount.once(), MockRestRequestMatchers.requestTo("http://localhost/events/storage"))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
            .andExpect(MockRestRequestMatchers.content().json("{" +
                "\"bucket\":\"bucket\"," +
                "\"name\":\"two.pdf\"}"))
            .andRespond(MockRestResponseCreators.withSuccess());

        ReceiptProcessingClient.ProcessingResult result = client.notifyUploads(List.of(first, second));

        server.verify();
        assertThat(result.requestedCount()).isEqualTo(2);
        assertThat(result.succeededCount()).isEqualTo(2);
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void recordsFailuresWhenProcessorReturnsError() {
        StoredReceiptReference reference = new StoredReceiptReference("bucket", "broken.pdf");

        server.expect(ExpectedCount.once(), MockRestRequestMatchers.requestTo("http://localhost/events/storage"))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
            .andRespond(MockRestResponseCreators.withServerError()
                .contentType(MediaType.APPLICATION_JSON));

        ReceiptProcessingClient.ProcessingResult result = client.notifyUploads(List.of(reference));

        server.verify();
        assertThat(result.requestedCount()).isEqualTo(1);
        assertThat(result.succeededCount()).isZero();
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().getFirst().reference()).isEqualTo(reference);
    }
}
