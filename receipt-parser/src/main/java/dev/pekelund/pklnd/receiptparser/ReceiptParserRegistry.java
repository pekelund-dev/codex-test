package dev.pekelund.pklnd.receiptparser;

import dev.pekelund.pklnd.receiptparser.legacy.LegacyPdfReceiptExtractor;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registry that exposes the available receipt parsers to HTTP controllers and other
 * consumers that need to invoke a specific extractor implementation on demand.
 */
@Component
public class ReceiptParserRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptParserRegistry.class);

    private final Map<String, ParserRegistration> registrations;

    public ReceiptParserRegistry(HybridReceiptExtractor hybridReceiptExtractor,
        LegacyPdfReceiptExtractor legacyPdfReceiptExtractor, AIReceiptExtractor aiReceiptExtractor) {

        Map<String, ParserRegistration> mutable = new LinkedHashMap<>();
        register(mutable, new ReceiptParserDescriptor("hybrid", "Hybrid", "Combines the legacy PDF parser with Gemini and falls back when needed"),
            hybridReceiptExtractor);
        register(mutable, new ReceiptParserDescriptor("legacy", "Legacy PDF", "Runs only the legacy PDF parser without Gemini"),
            legacyPdfReceiptExtractor);
        register(mutable, new ReceiptParserDescriptor("gemini", "Gemini", "Invokes Gemini directly without the legacy pre-parser"),
            aiReceiptExtractor);
        this.registrations = Collections.unmodifiableMap(mutable);
        LOGGER.info("Initialised receipt parser registry with parsers: {}", registrations.keySet());
    }

    public Collection<ReceiptParserDescriptor> listParsers() {
        return registrations.values().stream()
            .map(ParserRegistration::descriptor)
            .collect(java.util.stream.Collectors.toList());
    }

    public Optional<ParserRegistration> find(String parserId) {
        if (parserId == null) {
            return Optional.empty();
        }
        String normalised = parserId.trim().toLowerCase(Locale.US);
        return Optional.ofNullable(registrations.get(normalised));
    }

    private void register(Map<String, ParserRegistration> target, ReceiptParserDescriptor descriptor,
        ReceiptDataExtractor extractor) {
        target.put(descriptor.id(), new ParserRegistration(descriptor, extractor));
    }

    public record ReceiptParserDescriptor(String id, String displayName, String description) { }

    public record ParserRegistration(ReceiptParserDescriptor descriptor, ReceiptDataExtractor extractor) { }
}
