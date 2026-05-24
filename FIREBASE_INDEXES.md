# Firestore Composite Indexes Requirements

The following composite indexes must be created in the Firebase Console to support optimized queries and pagination.

## Required Indexes

| Collection | Fields | Scope |
|------------|--------|-------|
| **stock_entries** | productVariantId (Asc), timestamp (Desc) | Collection |
| **stock_entries** | productVariantId (Asc), warehouseId (Asc), timestamp (Desc) | Collection |
| **stock_entries** | status (Asc), timestamp (Desc) | Collection |
| **stock_entries** | invoiceId (Asc), timestamp (Desc) | Collection |
| **bills** | supplierId (Asc), dueDate (Asc) | Collection |
| **product_variants** | productId (Asc), capacity (Asc) | Collection |
| **product_variants** | barcode (Asc), archived (Asc) | Collection |
| **suppliers** | name (Asc), id (Asc) | Collection |

## Notes
- Indexing is required for any query that combines a filter (`whereEqualTo`) with a sort (`orderBy`).
- Paging 3 operations that use `orderBy` on timestamps after filtering by ID will fail without these indexes.
- You can create these indexes by following the links provided in the error messages in the Android Studio Logcat.
