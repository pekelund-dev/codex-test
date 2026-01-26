package dev.pekelund.pklnd.web.assets;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component("viteManifest")
public class ViteManifest {

    private static final Logger log = LoggerFactory.getLogger(ViteManifest.class);
    private static final String MANIFEST_LOCATION = "classpath:/static/assets/manifest.json";
    private static final String FRONTEND_PREFIX = "src/main/frontend/";

    private final Map<String, ManifestEntry> manifest;
    private final Map<String, String> fallbackAssets;
    private final Map<String, String> fallbackCss;

    public ViteManifest(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.manifest = loadManifest(objectMapper, resourceLoader);
        this.fallbackAssets = Map.of(
            "main.js", "/js/main.js",
            "receipts.js", "/js/receipts.js",
            "receipt-overview.js", "/js/receipt-overview.js",
            "receipt-uploads.js", "/js/receipt-uploads.js",
            "price-history.js", "/js/price-history.js"
        );
        this.fallbackCss = Map.of(
            "main.js", "/css/styles.css"
        );
    }

    public String asset(String entry) {
        ManifestEntry manifestEntry = manifest.get(resolveEntryKey(entry));
        if (manifestEntry != null && manifestEntry.file() != null) {
            return toAssetPath(manifestEntry.file());
        }
        return fallbackAssets.getOrDefault(entry, "/js/" + entry);
    }

    public String css(String entry) {
        ManifestEntry manifestEntry = manifest.get(resolveEntryKey(entry));
        if (manifestEntry != null && manifestEntry.css() != null && !manifestEntry.css().isEmpty()) {
            return toAssetPath(manifestEntry.css().getFirst());
        }
        return fallbackCss.getOrDefault(entry, "/css/styles.css");
    }

    private Map<String, ManifestEntry> loadManifest(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource(MANIFEST_LOCATION);
        if (!resource.exists()) {
            log.info("Vite manifest not found at {}, falling back to static assets.", MANIFEST_LOCATION);
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {});
        } catch (IOException ex) {
            log.warn("Failed to read Vite manifest, falling back to static assets.", ex);
            return Collections.emptyMap();
        }
    }

    private String resolveEntryKey(String entry) {
        if (entry.startsWith(FRONTEND_PREFIX) || entry.startsWith("src/")) {
            return entry;
        }
        return FRONTEND_PREFIX + entry;
    }

    private String toAssetPath(String file) {
        if (file.startsWith("/")) {
            return file;
        }
        if (file.startsWith("assets/")) {
            return "/" + file;
        }
        return "/assets/" + file;
    }

    private record ManifestEntry(String file, List<String> css) {}
}
