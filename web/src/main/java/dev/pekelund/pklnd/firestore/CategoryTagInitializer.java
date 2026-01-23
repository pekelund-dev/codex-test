package dev.pekelund.pklnd.firestore;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Component responsible for initializing predefined categories and tags when the application starts.
 */
@Component
public class CategoryTagInitializer {

    private static final Logger log = LoggerFactory.getLogger(CategoryTagInitializer.class);

    private final CategoryService categoryService;
    private final TagService tagService;

    public CategoryTagInitializer(CategoryService categoryService, TagService tagService) {
        this.categoryService = categoryService;
        this.tagService = tagService;
    }

    @PostConstruct
    public void initializePredefinedData() {
        if (!categoryService.isEnabled() || !tagService.isEnabled()) {
            log.info("Firestore not enabled, skipping category and tag initialization");
            return;
        }

        log.info("Initializing predefined categories and tags");
        
        try {
            initializeCategories();
            initializeTags();
            log.info("Successfully initialized predefined categories and tags");
        } catch (Exception ex) {
            log.error("Failed to initialize predefined categories and tags", ex);
        }
    }

    private void initializeCategories() {
        List<CategoryDefinition> definitions = List.of(
            new CategoryDefinition("Kött", null, List.of("Fläsk", "Kyckling", "Nöt", "Vilt")),
            new CategoryDefinition("Fisk", null, List.of()),
            new CategoryDefinition("Grönsaker", null, List.of()),
            new CategoryDefinition("Frukt", null, List.of()),
            new CategoryDefinition("Bröd", null, List.of()),
            new CategoryDefinition("Mejeri", null, List.of()),
            new CategoryDefinition("Chark", null, List.of()),
            new CategoryDefinition("Godis och snacks", null, List.of()),
            new CategoryDefinition("Dryck", null, List.of())
        );

        for (CategoryDefinition def : definitions) {
            try {
                // Check if parent category already exists
                var existingParent = categoryService.findByName(def.name);
                Category parent;
                
                if (existingParent.isEmpty()) {
                    parent = categoryService.createCategory(def.name, null, true);
                    log.info("Created predefined category: {}", def.name);
                } else {
                    parent = existingParent.get();
                    log.debug("Category already exists: {}", def.name);
                }

                // Create subcategories
                for (String subcategoryName : def.subcategories) {
                    var existingSubcategory = categoryService.findByName(subcategoryName);
                    if (existingSubcategory.isEmpty()) {
                        categoryService.createCategory(subcategoryName, parent.id(), true);
                        log.info("Created predefined subcategory: {} under {}", subcategoryName, def.name);
                    } else {
                        log.debug("Subcategory already exists: {}", subcategoryName);
                    }
                }
            } catch (IllegalStateException ex) {
                // Category already exists, continue
                log.debug("Category initialization skipped: {}", ex.getMessage());
            } catch (Exception ex) {
                log.error("Failed to initialize category: {}", def.name, ex);
            }
        }
    }

    private void initializeTags() {
        List<String> predefinedTags = List.of(
            "Fryst",
            "Konserv"
        );

        for (String tagName : predefinedTags) {
            try {
                var existing = tagService.findByName(tagName);
                if (existing.isEmpty()) {
                    tagService.createTag(tagName, true);
                    log.info("Created predefined tag: {}", tagName);
                } else {
                    log.debug("Tag already exists: {}", tagName);
                }
            } catch (IllegalStateException ex) {
                // Tag already exists, continue
                log.debug("Tag initialization skipped: {}", ex.getMessage());
            } catch (Exception ex) {
                log.error("Failed to initialize tag: {}", tagName, ex);
            }
        }
    }

    private record CategoryDefinition(String name, String parentName, List<String> subcategories) {
        CategoryDefinition {
            subcategories = subcategories != null ? subcategories : List.of();
        }
    }
}
