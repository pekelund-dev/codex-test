package dev.pekelund.pklnd.function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.ServiceOptions;
import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import dev.pekelund.pklnd.messaging.ReceiptProcessingMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReceiptEventPublisher implements BackgroundFunction<StorageObjectEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptEventPublisher.class);

    private final ObjectMapper objectMapper;
    private final Publisher publisher;

    public ReceiptEventPublisher() throws IOException {
        this(createPublisher(defaultProjectId(), topicNameFromEnv()));
    }

    ReceiptEventPublisher(Publisher publisher) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        LOGGER.info("ReceiptEventPublisher initialised with publisher bound to topic {}",
            publisher.getTopicNameString());
    }

    @Override
    public void accept(StorageObjectEvent event, Context context) {
        if (event == null) {
            LOGGER.warn("Cloud Storage event payload was null; skipping publish");
            return;
        }

        String bucket = event.getBucket();
        String objectName = event.getName();
        if (isBlank(bucket) || isBlank(objectName)) {
            LOGGER.warn("Storage event missing bucket ({}) or object name ({})", bucket, objectName);
            return;
        }

        LOGGER.info("Publishing receipt processing request for gs://{}/{}", bucket, objectName);
        ReceiptProcessingMessage message = ReceiptProcessingMessage.fromStorageEvent(
            bucket,
            objectName,
            event.getContentType(),
            event.getSize(),
            event.getGeneration(),
            event.getMetageneration(),
            event.getTimeCreatedInstant(),
            event.getMetadata());

        try {
            String json = objectMapper.writeValueAsString(message);
            PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                .setData(ByteString.copyFrom(json, StandardCharsets.UTF_8))
                .putAttributes("bucket", bucket)
                .putAttributes("objectName", objectName)
                .putAttributes("generation", Optional.ofNullable(message.generation()).orElse(""))
                .build();
            publisher.publish(pubsubMessage).get(60, TimeUnit.SECONDS);
            LOGGER.info("Successfully published Pub/Sub message for gs://{}/{}", bucket, objectName);
        } catch (ExecutionException ex) {
            LOGGER.error("Failed to publish receipt processing request to Pub/Sub", ex);
            throw new IllegalStateException("Failed to publish receipt processing message", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.error("Interrupted while publishing receipt processing request", ex);
            throw new IllegalStateException("Interrupted while publishing receipt processing message", ex);
        } catch (TimeoutException ex) {
            LOGGER.error("Timed out while publishing receipt processing request", ex);
            throw new IllegalStateException("Timed out while publishing receipt processing message", ex);
        } catch (IOException ex) {
            LOGGER.error("Failed to serialise receipt processing message", ex);
            throw new IllegalStateException("Failed to serialise receipt processing message", ex);
        }
    }

    private static Publisher createPublisher(String projectId, String topic) throws IOException {
        String topicName = ProjectTopicName.format(projectId, topic);
        return Publisher.newBuilder(topicName).build();
    }

    private static String defaultProjectId() {
        return Optional.ofNullable(System.getenv("RECEIPT_PUBSUB_PROJECT_ID"))
            .filter(value -> !isBlank(value))
            .orElseGet(ServiceOptions::getDefaultProjectId);
    }

    private static String topicNameFromEnv() {
        String topic = System.getenv("RECEIPT_PUBSUB_TOPIC");
        if (isBlank(topic)) {
            throw new IllegalStateException("RECEIPT_PUBSUB_TOPIC environment variable must be set");
        }
        return topic;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
