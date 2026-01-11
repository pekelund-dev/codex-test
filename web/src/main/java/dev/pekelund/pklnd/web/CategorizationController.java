package dev.pekelund.pklnd.web;

import dev.pekelund.pklnd.firestore.Category;
import dev.pekelund.pklnd.firestore.CategoryService;
import dev.pekelund.pklnd.firestore.ItemCategorizationService;
import dev.pekelund.pklnd.firestore.ItemTag;
import dev.pekelund.pklnd.firestore.TagService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * REST controller for managing categories and tags for receipt items.
 */
@Controller
@RequestMapping("/api/categorization")
public class CategorizationController {

    private static final Logger log = LoggerFactory.getLogger(CategorizationController.class);

    private final Optional<CategoryService> categoryService;
    private final Optional<TagService> tagService;
    private final Optional<ItemCategorizationService> itemCategorizationService;

    public CategorizationController(
        @Autowired(required = false) CategoryService categoryService,
        @Autowired(required = false) TagService tagService,
        @Autowired(required = false) ItemCategorizationService itemCategorizationService
    ) {
        this.categoryService = Optional.ofNullable(categoryService);
        this.tagService = Optional.ofNullable(tagService);
        this.itemCategorizationService = Optional.ofNullable(itemCategorizationService);
    }

    /**
     * List all categories in hierarchical structure.
     */
    @GetMapping("/categories")
    @ResponseBody
    public ResponseEntity<Map<Category, List<Category>>> listCategories() {
        if (categoryService.isEmpty() || !categoryService.get().isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        Map<Category, List<Category>> hierarchy = categoryService.get().getCategoriesHierarchy();
        return ResponseEntity.ok(hierarchy);
    }

    /**
     * Get a specific category by ID.
     */
    @GetMapping("/categories/{id}")
    @ResponseBody
    public ResponseEntity<Category> getCategory(@PathVariable String id) {
        if (!categoryService.isEmpty() || !categoryService.get().isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        Optional<Category> category = categoryService.get().findById(id);
        return category.map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new category.
     */
    @PostMapping("/categories")
    @ResponseBody
    public ResponseEntity<Category> createCategory(@RequestBody CreateCategoryRequest request) {
        if (!categoryService.isEmpty() || !categoryService.get().isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            Category category = categoryService.get().createCategory(
                request.name(),
                request.parentId(),
                false // User-created categories are not predefined
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(category);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("Failed to create category: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update an existing category.
     */
    @PutMapping("/categories/{id}")
    @ResponseBody
    public ResponseEntity<Category> updateCategory(
        @PathVariable String id,
        @RequestBody UpdateCategoryRequest request
    ) {
        if (!categoryService.isEmpty() || !categoryService.get().isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            Category category = categoryService.get().updateCategory(
                id,
                request.name(),
                request.parentId()
            );
            return ResponseEntity.ok(category);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("Failed to update category: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete a category.
     */
    @DeleteMapping("/categories/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteCategory(@PathVariable String id) {
        if (!categoryService.isEmpty() || !categoryService.get().isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            categoryService.get().deleteCategory(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException ex) {
            log.warn("Failed to delete category: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * List all tags.
     */
    @GetMapping("/tags")
    @ResponseBody
    public ResponseEntity<List<ItemTag>> listTags() {
        if (!tagService.isEmpty() || !tagService.get().isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        List<ItemTag> tags = tagService.get().listTags();
        return ResponseEntity.ok(tags);
    }

    /**
     * Get a specific tag by ID.
     */
    @GetMapping("/tags/{id}")
    @ResponseBody
    public ResponseEntity<ItemTag> getTag(@PathVariable String id) {
        if (!tagService.isEmpty() || !tagService.get().isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        Optional<ItemTag> tag = tagService.get().findById(id);
        return tag.map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new tag.
     */
    @PostMapping("/tags")
    @ResponseBody
    public ResponseEntity<ItemTag> createTag(@RequestBody CreateTagRequest request) {
        if (!tagService.isEmpty() || !tagService.get().isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            ItemTag tag = tagService.get().createTag(
                request.name(),
                false // User-created tags are not predefined
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(tag);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("Failed to create tag: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete a tag.
     */
    @DeleteMapping("/tags/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteTag(@PathVariable String id) {
        if (!tagService.isEmpty() || !tagService.get().isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            tagService.get().deleteTag(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException ex) {
            log.warn("Failed to delete tag: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Assign a category to a receipt item.
     * If the item has an EAN, also assigns to all other items with the same EAN.
     */
    @PostMapping("/receipts/{receiptId}/items/category")
    @ResponseBody
    public ResponseEntity<String> assignCategoryToItem(
        @PathVariable String receiptId,
        @RequestBody AssignCategoryRequest request,
        Authentication authentication
    ) {
        if (itemCategorizationService.isEmpty() || !itemCategorizationService.get().isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            String assignedBy = authentication != null ? authentication.getName() : "anonymous";
            
            // If item has EAN, assign category to all items with that EAN
            if (request.itemEan() != null && !request.itemEan().isBlank()) {
                int count = itemCategorizationService.get().assignCategoryByEan(
                    request.itemEan(),
                    request.categoryId(),
                    assignedBy
                );
                return ResponseEntity.ok("Category assigned to " + count + " items with EAN " + request.itemEan());
            } else {
                // No EAN, just assign to this specific item
                itemCategorizationService.get().assignCategory(
                    receiptId,
                    request.itemIndex(),
                    request.itemEan(),
                    request.categoryId(),
                    assignedBy
                );
                return ResponseEntity.ok("Category assigned successfully");
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("Failed to assign category: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    /**
     * Remove category from a receipt item.
     */
    @DeleteMapping("/receipts/{receiptId}/items/category")
    @ResponseBody
    public ResponseEntity<Void> removeCategoryFromItem(
        @PathVariable String receiptId,
        @RequestParam String itemIdentifier
    ) {
        if (!itemCategorizationService.isEmpty() || !itemCategorizationService.get().isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        itemCategorizationService.get().removeCategoryFromItem(receiptId, itemIdentifier);
        return ResponseEntity.noContent().build();
    }

    /**
     * Assign a tag to a receipt item.
     */
    @PostMapping("/receipts/{receiptId}/items/tags")
    @ResponseBody
    public ResponseEntity<String> assignTagToItem(
        @PathVariable String receiptId,
        @RequestBody AssignTagRequest request,
        Authentication authentication
    ) {
        if (!itemCategorizationService.isEmpty() || !itemCategorizationService.get().isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            String assignedBy = authentication != null ? authentication.getName() : "anonymous";
            itemCategorizationService.get().assignTag(
                receiptId,
                request.itemIndex(),
                request.itemEan(),
                request.tagId(),
                assignedBy
            );
            return ResponseEntity.ok("Tag assigned successfully");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("Failed to assign tag: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    /**
     * Remove a tag from a receipt item.
     */
    @DeleteMapping("/receipts/{receiptId}/items/tags/{tagId}")
    @ResponseBody
    public ResponseEntity<Void> removeTagFromItem(
        @PathVariable String receiptId,
        @RequestParam String itemIdentifier,
        @PathVariable String tagId
    ) {
        if (!itemCategorizationService.isEmpty() || !itemCategorizationService.get().isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        itemCategorizationService.get().removeTagFromItem(receiptId, itemIdentifier, tagId);
        return ResponseEntity.noContent().build();
    }

    // Request/Response DTOs
    public record CreateCategoryRequest(String name, String parentId) {}
    public record UpdateCategoryRequest(String name, String parentId) {}
    public record CreateTagRequest(String name) {}
    public record AssignCategoryRequest(String itemIndex, String itemEan, String categoryId) {}
    public record AssignTagRequest(String itemIndex, String itemEan, String tagId) {}
}
