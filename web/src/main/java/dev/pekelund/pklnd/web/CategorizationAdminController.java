package dev.pekelund.pklnd.web;

import dev.pekelund.pklnd.firestore.Category;
import dev.pekelund.pklnd.firestore.CategoryService;
import dev.pekelund.pklnd.firestore.ItemTag;
import dev.pekelund.pklnd.firestore.TagService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Controller for checking and initializing categories/tags.
 * Useful for debugging and manual initialization.
 */
@Controller
@RequestMapping("/api/admin/categorization")
public class CategorizationAdminController {

    private final CategoryService categoryService;
    private final TagService tagService;

    public CategorizationAdminController(
        CategoryService categoryService,
        TagService tagService
    ) {
        this.categoryService = categoryService;
        this.tagService = tagService;
    }

    /**
     * Check the status of categories and tags.
     */
    @GetMapping("/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        
        boolean categoryServiceEnabled = categoryService.isEnabled();
        boolean tagServiceEnabled = tagService.isEnabled();
        
        status.put("categoryServiceEnabled", categoryServiceEnabled);
        status.put("tagServiceEnabled", tagServiceEnabled);
        
        if (categoryServiceEnabled) {
            List<Category> categories = categoryService.listCategories();
            status.put("categoryCount", categories.size());
            status.put("categories", categories.stream().map(Category::name).toList());
        } else {
            status.put("categoryCount", 0);
            status.put("categories", List.of());
        }
        
        if (tagServiceEnabled) {
            List<ItemTag> tags = tagService.listTags();
            status.put("tagCount", tags.size());
            status.put("tags", tags.stream().map(ItemTag::name).toList());
        } else {
            status.put("tagCount", 0);
            status.put("tags", List.of());
        }
        
        return ResponseEntity.ok(status);
    }

    /**
     * Manually initialize predefined categories and tags.
     */
    @PostMapping("/initialize")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> initialize() {
        Map<String, Object> result = new HashMap<>();
        
        if (!categoryService.isEnabled() || !tagService.isEnabled()) {
            result.put("success", false);
            result.put("message", "Firestore is not enabled");
            return ResponseEntity.ok(result);
        }
        
        try {
            // Initialize categories
            String[] categories = {"Kött", "Fisk", "Grönsaker", "Frukt", "Bröd", "Mejeri", "Chark", "Godis och snacks", "Dryck"};
            int categoriesCreated = 0;
            
            for (String categoryName : categories) {
                var existing = categoryService.findByName(categoryName);
                if (existing.isEmpty()) {
                    categoryService.createCategory(categoryName, null, true);
                    categoriesCreated++;
                }
            }
            
            // Create subcategories for Kött
            var kottCategory = categoryService.findByName("Kött");
            if (kottCategory.isPresent()) {
                String[] subcategories = {"Fläsk", "Kyckling", "Nöt", "Vilt"};
                for (String subcategoryName : subcategories) {
                    var existing = categoryService.findByName(subcategoryName);
                    if (existing.isEmpty()) {
                        categoryService.createCategory(subcategoryName, kottCategory.get().id(), true);
                        categoriesCreated++;
                    }
                }
            }
            
            // Initialize tags
            String[] tags = {"Fryst", "Konserv"};
            int tagsCreated = 0;
            
            for (String tagName : tags) {
                var existing = tagService.findByName(tagName);
                if (existing.isEmpty()) {
                    tagService.createTag(tagName, true);
                    tagsCreated++;
                }
            }
            
            result.put("success", true);
            result.put("categoriesCreated", categoriesCreated);
            result.put("tagsCreated", tagsCreated);
            result.put("totalCategories", categoryService.listCategories().size());
            result.put("totalTags", tagService.listTags().size());
            
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            result.put("success", false);
            result.put("message", "Error: " + ex.getMessage());
            return ResponseEntity.ok(result);
        }
    }
}
