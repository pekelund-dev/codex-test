# Categories and Tags Feature Implementation

## Overview

This document describes the categories and tags feature that allows users to categorize receipt items and track spending by category.

## Features Implemented

### 1. Domain Models

**Category** (`dev.pekelund.pklnd.firestore.Category`)
- Hierarchical structure with parent-child relationships
- Support for predefined system categories and user-created categories
- Swedish category names

**ItemTag** (`dev.pekelund.pklnd.firestore.ItemTag`)
- Simple tagging system for additional item classification
- Support for predefined and user-created tags

**ItemCategoryMapping** (`dev.pekelund.pklnd.firestore.ItemCategoryMapping`)
- Links receipt items to categories
- Tracks who assigned the category and when

**ItemTagMapping** (`dev.pekelund.pklnd.firestore.ItemTagMapping`)
- Links receipt items to tags
- Supports multiple tags per item

### 2. Service Layer

**CategoryService** (`dev.pekelund.pklnd.firestore.CategoryService`)
- CRUD operations for categories
- Hierarchy management and validation
- Prevents circular references
- Prevents deletion of categories with subcategories

**TagService** (`dev.pekelund.pklnd.firestore.TagService`)
- CRUD operations for tags
- Simple name-based tag management

**ItemCategorizationService** (`dev.pekelund.pklnd.firestore.ItemCategorizationService`)
- Assigns categories and tags to receipt items
- Retrieves categorization data for receipts
- Manages category and tag removal

**CategoryStatisticsService** (`dev.pekelund.pklnd.web.CategoryStatisticsService`)
- Calculates spending by category
- Supports monthly and yearly breakdowns
- Hierarchical category grouping

**CategoryTagInitializer** (`dev.pekelund.pklnd.firestore.CategoryTagInitializer`)
- Automatically seeds predefined categories and tags on startup
- Predefined categories: Kött, Fisk, Grönsaker, Frukt, Bröd, Mejeri, Chark, Godis och snacks, Dryck
- Subcategories for Kött: Fläsk, Kyckling, Nöt, Vilt
- Predefined tags: Fryst, Konserv

### 3. REST API

**CategorizationController** (`dev.pekelund.pklnd.web.CategorizationController`)

Endpoints:
- `GET /api/categorization/categories` - List all categories in hierarchical structure
- `GET /api/categorization/categories/{id}` - Get specific category
- `POST /api/categorization/categories` - Create new category
- `PUT /api/categorization/categories/{id}` - Update category
- `DELETE /api/categorization/categories/{id}` - Delete category
- `GET /api/categorization/tags` - List all tags
- `GET /api/categorization/tags/{id}` - Get specific tag
- `POST /api/categorization/tags` - Create new tag
- `DELETE /api/categorization/tags/{id}` - Delete tag
- `POST /api/categorization/receipts/{receiptId}/items/category` - Assign category to item
- `DELETE /api/categorization/receipts/{receiptId}/items/category` - Remove category from item
- `POST /api/categorization/receipts/{receiptId}/items/tags` - Assign tag to item
- `DELETE /api/categorization/receipts/{receiptId}/items/tags/{tagId}` - Remove tag from item

### 4. User Interface

**Receipt Detail View** (`receipt-detail.html`)
- Category dropdown with hierarchical structure (parent categories with indented subcategories)
- Multi-select tags dropdown
- Inline creation of new categories and tags via "+" buttons
- Automatic saving on selection change
- Mobile-responsive design using Bootstrap 5

**JavaScript Functionality**
- CSRF token handling for secure API calls
- Automatic category/tag assignment on selection
- Prompt-based inline creation of new categories and tags
- Page reload after creating new categories/tags to update dropdowns

### 5. Localization

All UI elements are translated to Swedish in `messages.properties`:
- Category management labels
- Tag management labels
- Item categorization labels
- Statistics labels

## Firestore Collections

The feature uses the following Firestore collections:

1. **categories**
   - Document fields: `id`, `name`, `parentId`, `createdAt`, `updatedAt`, `predefined`

2. **tags**
   - Document fields: `id`, `name`, `createdAt`, `predefined`

3. **item_categories**
   - Document fields: `id`, `receiptId`, `itemIndex`, `itemEan`, `categoryId`, `assignedAt`, `assignedBy`

4. **item_tags**
   - Document fields: `id`, `receiptId`, `itemIndex`, `itemEan`, `tagId`, `assignedAt`, `assignedBy`

## Usage Instructions

### For Users

1. **Viewing Receipt Items with Categories**
   - Navigate to a receipt detail page
   - Each item now has a "Category & Tags" column (if categorization is enabled)

2. **Assigning a Category to an Item**
   - Click the category dropdown for an item
   - Select a category or subcategory from the hierarchical list
   - The category is automatically saved

3. **Adding Tags to an Item**
   - Use the tags multi-select dropdown
   - Select one or more tags
   - Tags are automatically saved on selection

4. **Creating New Categories**
   - Click the "+" button next to the category dropdown
   - Enter the category name when prompted
   - The page will reload to show the new category

5. **Creating New Tags**
   - Click the "+" button next to the tags dropdown
   - Enter the tag name when prompted
   - The page will reload to show the new tag

### For Developers

**Adding More Predefined Categories**

Edit `CategoryTagInitializer.java` and add to the `definitions` list:

```java
new CategoryDefinition("New Category", null, List.of("Subcategory 1", "Subcategory 2"))
```

**Adding More Predefined Tags**

Edit `CategoryTagInitializer.java` and add to the `predefinedTags` list:

```java
"New Tag Name"
```

**Querying Items by Category**

```java
List<ItemCategoryMapping> mappings = itemCategorizationService.getCategoriesForReceipt(receiptId);
```

**Getting Category Statistics**

```java
CategorySpendingStats stats = categoryStatisticsService.getSpendingByCategory(owner);
Map<String, CategorySpending> byCategory = stats.byCategory();
```

## Future Enhancements

The following features are planned but not yet implemented:

1. **Statistics Dashboard Integration**
   - Add category statistics cards to the main dashboard
   - Show top spending categories

2. **Monthly/Yearly Statistics**
   - Toggle to show category breakdown in monthly stats
   - Toggle to show category breakdown in yearly stats
   - Category spending charts and visualizations

3. **Filtering**
   - Filter receipts by category
   - Filter receipts by tag
   - Search items by category/tag combination

4. **Bulk Operations**
   - Assign category to all similar items (by EAN)
   - Batch edit categories and tags

5. **Category Management UI**
   - Dedicated page for managing categories and their hierarchy
   - Drag-and-drop to reorganize category structure
   - Edit category names
   - Merge categories

6. **AI-Powered Auto-Categorization**
   - Automatically suggest categories based on item names
   - Learn from user assignments to improve suggestions

7. **Export and Reporting**
   - Export category spending reports as CSV/PDF
   - Monthly category spending comparison charts
   - Year-over-year category trends

## Testing

All existing tests pass with the new feature:
- 49 unit tests passing
- No breaking changes to existing functionality
- New services are optional (gracefully disabled if Firestore is not configured)

## Security Considerations

- All API endpoints require authentication
- CSRF protection is enabled for all state-changing operations
- Category/tag assignments track who made the assignment
- Predefined categories and tags cannot be deleted
- Input validation prevents invalid category hierarchies

## Performance Considerations

- Category and tag lists are cached for the duration of the request
- Hierarchical queries are optimized with parent-child indexing
- Item categorization mappings use composite keys for fast lookups
- Statistics calculations process receipts in memory for speed

## Compatibility

- Fully compatible with existing receipt processing
- Works with or without Firestore enabled
- Gracefully degrades if categorization services are unavailable
- Mobile-responsive design works on all screen sizes
