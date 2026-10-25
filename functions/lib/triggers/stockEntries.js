"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.onStockEntryDeleted = exports.onStockEntryUpdated = exports.onStockEntryCreated = void 0;
const firestore_1 = require("firebase-functions/v2/firestore");
const dashboardHub_1 = require("../aggregators/dashboardHub");
exports.onStockEntryCreated = (0, firestore_1.onDocumentCreated)("stock_entries/{entryId}", async (event) => {
    if (!event.data)
        return;
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        updatedDomains: ["inventory", "suppliers", "financial"],
    });
});
exports.onStockEntryUpdated = (0, firestore_1.onDocumentUpdated)("stock_entries/{entryId}", async (event) => {
    if (!event.data)
        return;
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        updatedDomains: ["inventory", "suppliers", "financial"],
    });
});
exports.onStockEntryDeleted = (0, firestore_1.onDocumentDeleted)("stock_entries/{entryId}", async (event) => {
    if (!event.data)
        return;
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        updatedDomains: ["inventory", "suppliers", "financial"],
    });
});
//# sourceMappingURL=stockEntries.js.map