package dev.pekelund.pklnd.receipts;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.storage.StoredReceiptReference;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

public class ReceiptProcessingClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptProcessingClient.class);

    private final RestClient restClient;
    private final ReceiptProcessingProperties properties;
    private final AtomicReference<IdTokenCredentials> cachedCredentials = new AtomicReference<>();

    public ReceiptProcessingClient(RestClient restClient, ReceiptProcessingProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public ProcessingResult notifyUploads(List<StoredReceiptReference> references) {
        if (references == null || references.isEmpty()) {
            return new ProcessingResult(0, 0, List.of());
        }

        int successes = 0;
        List<ProcessingFailure> failures = new ArrayList<>();

        for (StoredReceiptReference reference : references) {
            try {
                sendNotification(reference);
                successes++;
            } catch (ReceiptProcessingException | RestClientException ex) {
                LOGGER.error("Failed to notify receipt processor for gs://{}/{}", reference.bucket(), reference.objectName(), ex);
                failures.add(new ProcessingFailure(reference, ex.getMessage()));
            }
        }

        return new ProcessingResult(references.size(), successes, List.copyOf(failures));
    }

    private void sendNotification(StoredReceiptReference reference) {
        URI uri = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
            .path(properties.getEventPath())
            .build()
            .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("ce-specversion", "1.0");
        headers.add("ce-type", "google.cloud.storage.object.v1.finalized");
        headers.add("ce-source", "projects/_/buckets/" + reference.bucket() + "/objects/" + reference.objectName());
        headers.add("ce-subject", "objects/" + reference.objectName());
        headers.add("ce-id", UUID.randomUUID().toString());
        headers.add("ce-time", Instant.now().toString());

        if (properties.isUseIdToken()) {
            headers.setBearerAuth(fetchIdToken());
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("bucket", reference.bucket());
        payload.put("name", reference.objectName());

        ReceiptOwner owner = reference.owner();
        if (owner != null && owner.hasValues()) {
            payload.put("metadata", owner.toMetadata());
            Map<String, String> ownerAttributes = owner.toAttributes();
            if (!ownerAttributes.isEmpty()) {
                payload.put("owner", ownerAttributes);
            }
        }

        restClient
            .post()
            .uri(uri)
            .headers(httpHeaders -> httpHeaders.addAll(headers))
            .body(payload)
            .retrieve()
            .toBodilessEntity();
    }

    private String fetchIdToken() {
        try {
            IdTokenCredentials credentials = cachedCredentials.updateAndGet(existing -> {
                if (existing != null) {
                    return existing;
                }
                return buildCredentials();
            });
            credentials.refreshIfExpired();
            AccessToken token = credentials.getAccessToken();
            if (token == null || !StringUtils.hasText(token.getTokenValue())) {
                throw new ReceiptProcessingException("Failed to obtain ID token for receipt processor request");
            }
            return token.getTokenValue();
        } catch (IOException ex) {
            throw new ReceiptProcessingException("Unable to obtain ID token for receipt processor request", ex);
        }
    }

    private IdTokenCredentials buildCredentials() {
        try {
            GoogleCredentials googleCredentials = GoogleCredentials.getApplicationDefault();
            if (!(googleCredentials instanceof IdTokenProvider idTokenProvider)) {
                throw new ReceiptProcessingException(
                    "Default Google credentials do not support ID tokens. Set receipt.processing.use-id-token=false to disable authentication.");
            }
            String audience = properties.getAudience();
            if (!StringUtils.hasText(audience)) {
                audience = properties.getBaseUrl();
            }
            return IdTokenCredentials.newBuilder()
                .setIdTokenProvider(idTokenProvider)
                .setTargetAudience(audience)
                .build();
        } catch (IOException ex) {
            throw new ReceiptProcessingException("Unable to initialize Google credentials for receipt processor requests", ex);
        }
    }

    public record ProcessingResult(int requestedCount, int succeededCount, List<ProcessingFailure> failures) {
    }

    public record ProcessingFailure(StoredReceiptReference reference, String message) {
    }

}
