"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.onStockEntryDeleted = exports.onStockEntryUpdated = exports.onStockEntryCreated = void 0;
const firestore_1 = require("firebase-functions/v2/firestore");
const dashboardHub_1 = require("../aggregators/dashboardHub");
exports.onStockEntryCreated = (0, firestore_1.onDocumentCreated)("stock_entries/{entryId}", async (event) => {
    if (!event.data)
        return;
    console.time("onStockEntryCreated_executionTime");
    console.time("applyDashboardHubDelta");
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        updatedDomains: ["inventory", "suppliers", "financial"],
    });
    console.timeEnd("applyDashboardHubDelta");
    console.timeEnd("onStockEntryCreated_executionTime");
});
exports.onStockEntryUpdated = (0, firestore_1.onDocumentUpdated)("stock_entries/{entryId}", async (event) => {
    if (!event.data)
        return;
    console.time("onStockEntryUpdated_executionTime");
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        updatedDomains: ["inventory", "suppliers", "financial"],
    });
    console.timeEnd("onStockEntryUpdated_executionTime");
});
exports.onStockEntryDeleted = (0, firestore_1.onDocumentDeleted)("stock_entries/{entryId}", async (event) => {
    if (!event.data)
        return;
    console.time("onStockEntryDeleted_executionTime");
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        updatedDomains: ["inventory", "suppliers", "financial"],
    });
    console.timeEnd("onStockEntryDeleted_executionTime");
});
//# sourceMappingURL=stockEntries.js.map