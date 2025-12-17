package dev.pekelund.pklnd.tags;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

public record TagDefinition(String id, Map<String, String> translations, String color) {

    public TagDefinition {
        translations = translations != null ? new LinkedHashMap<>(translations) : new LinkedHashMap<>();
    }

    public Map<String, String> translations() {
        return Collections.unmodifiableMap(translations);
    }

    public String displayName(Locale locale) {
        if (translations.isEmpty()) {
            return null;
        }
        if (locale != null) {
            String exact = translations.get(locale.getLanguage());
            if (StringUtils.hasText(exact)) {
                return exact;
            }
        }
        return translations.values().stream().filter(StringUtils::hasText).findFirst().orElse(null);
    }
}
