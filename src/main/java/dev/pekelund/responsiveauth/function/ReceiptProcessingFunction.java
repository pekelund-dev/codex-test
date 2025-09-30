package dev.pekelund.responsiveauth.function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.functions.CloudEventsFunction;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.events.cloud.storage.v1.StorageObjectData;
import io.cloudevents.CloudEvent;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiApi;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;

/**
 * Cloud Function entry point for receipt parsing.
 */
public class ReceiptProcessingFunction implements CloudEventsFunction {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptProcessingFunction.class);

    private final ObjectMapper objectMapper;
    private final ReceiptParsingHandler handler;

    public ReceiptProcessingFunction() {
        this(initializeRuntime());
    }

    ReceiptProcessingFunction(RuntimeComponents runtimeComponents) {
        this.handler = runtimeComponents.handler();
        this.objectMapper = runtimeComponents.objectMapper();
    }

    @Override
    public void accept(CloudEvent cloudEvent) throws Exception {
        if (cloudEvent == null) {
            LOGGER.warn("Received null CloudEvent");
            return;
        }
        if (cloudEvent.getData() == null) {
            LOGGER.warn("CloudEvent did not contain any data");
            return;
        }
        byte[] payload = cloudEvent.getData().toBytes();
        StorageObjectData storageObjectData = parseStorageObject(payload);
        handler.handle(storageObjectData);
    }

    private StorageObjectData parseStorageObject(byte[] payload) throws IOException {
        return objectMapper.readValue(payload, StorageObjectData.class);
    }

    private static RuntimeComponents initializeRuntime() {
        ReceiptProcessingSettings settings = ReceiptProcessingSettings.fromEnvironment();
        ObjectMapper mapper = createObjectMapper();

        Storage storage = StorageOptions.getDefaultInstance().getService();
        Firestore firestore = FirestoreOptions.getDefaultInstance().getService();

        VertexAiGeminiApi api = new VertexAiGeminiApi(settings.vertexProjectId(), settings.vertexLocation(), settings.vertexModel());
        ChatModel chatModel = new VertexAiGeminiChatModel(api);
        GeminiReceiptExtractor extractor = new GeminiReceiptExtractor(chatModel, mapper);
        ReceiptExtractionRepository repository = new ReceiptExtractionRepository(firestore, settings.receiptsCollection());
        ReceiptParsingHandler handler = new ReceiptParsingHandler(storage, repository, extractor);

        return new RuntimeComponents(handler, mapper);
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }

    record RuntimeComponents(ReceiptParsingHandler handler, ObjectMapper objectMapper) { }
}
