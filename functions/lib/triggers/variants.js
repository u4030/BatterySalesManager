"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.onVariantDeleted = exports.onVariantUpdated = exports.onVariantCreated = void 0;
const firestore_1 = require("firebase-functions/v2/firestore");
const dashboardHub_1 = require("../aggregators/dashboardHub");
exports.onVariantCreated = (0, firestore_1.onDocumentCreated)("product_variants/{variantId}", async (event) => {
    if (!event.data)
        return;
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        updatedDomains: ["inventory"],
    });
});
exports.onVariantUpdated = (0, firestore_1.onDocumentUpdated)("product_variants/{variantId}", async (event) => {
    if (!event.data)
        return;
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        updatedDomains: ["inventory"],
    });
});
exports.onVariantDeleted = (0, firestore_1.onDocumentDeleted)("product_variants/{variantId}", async (event) => {
    if (!event.data)
        return;
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        updatedDomains: ["inventory"],
    });
});
//# sourceMappingURL=variants.js.map