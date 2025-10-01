package dev.pekelund.responsiveauth.function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.functions.CloudEventsFunction;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.cloudevents.CloudEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

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
        StorageObjectEvent storageObjectEvent = parseStorageObject(payload);
        handler.handle(storageObjectEvent);
    }

    private StorageObjectEvent parseStorageObject(byte[] payload) throws IOException {
        return objectMapper.readValue(payload, StorageObjectEvent.class);
    }

    private static RuntimeComponents initializeRuntime() {
        ReceiptProcessingSettings settings = ReceiptProcessingSettings.fromEnvironment();
        ObjectMapper mapper = createObjectMapper();

        System.out.println("DEBUG: Starting Spring context for ChatModel auto-configuration");
        
        // Create Spring context to get auto-configured ChatModel
        SpringApplication app = new SpringApplication(FunctionApplication.class);
        app.setDefaultProperties(buildDefaultSpringProperties());
        ConfigurableApplicationContext context = app.run();

        ChatModel chatModel = context.getBean(ChatModel.class);
        System.out.println("DEBUG: ChatModel auto-configured successfully: " + chatModel.getClass().getSimpleName());
        
        Storage storage = StorageOptions.getDefaultInstance().getService();
        Firestore firestore = FirestoreOptions.getDefaultInstance().getService();

        GeminiReceiptExtractor extractor = new GeminiReceiptExtractor(chatModel, mapper);
        ReceiptExtractionRepository repository = new ReceiptExtractionRepository(firestore, settings.receiptsCollection());
        ReceiptParsingHandler handler = new ReceiptParsingHandler(storage, repository, extractor);

        return new RuntimeComponents(handler, mapper, context);
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }

    private static Map<String, Object> buildDefaultSpringProperties() {
        Map<String, String> env = System.getenv();
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("spring.main.web-application-type", "none");
        defaults.put("logging.level.root", "WARN");
        defaults.put("spring.ai.vertex.ai.gemini.project-id",
            env.getOrDefault("VERTEX_AI_PROJECT_ID", "codex-test-473008"));
        defaults.put("spring.ai.vertex.ai.gemini.location",
            env.getOrDefault("VERTEX_AI_LOCATION", "us-east1"));
        defaults.put("spring.ai.vertex.ai.gemini.chat.options.model",
            env.getOrDefault("VERTEX_AI_GEMINI_MODEL", "gemini-2.0-flash"));

        System.out.println("DEBUG: Vertex AI Gemini model set to "
            + defaults.get("spring.ai.vertex.ai.gemini.chat.options.model"));
        return defaults;
    }

    record RuntimeComponents(ReceiptParsingHandler handler, ObjectMapper objectMapper, ConfigurableApplicationContext context) { }
}
