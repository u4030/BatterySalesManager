"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.onSupplierDeleted = exports.onSupplierUpdated = exports.onSupplierCreated = void 0;
const firestore_1 = require("firebase-functions/v2/firestore");
const dashboardHub_1 = require("../aggregators/dashboardHub");
exports.onSupplierCreated = (0, firestore_1.onDocumentCreated)("suppliers/{supplierId}", async (event) => {
    if (!event.data)
        return;
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        updatedDomains: ["suppliers"],
    });
});
exports.onSupplierUpdated = (0, firestore_1.onDocumentUpdated)("suppliers/{supplierId}", async (event) => {
    if (!event.data)
        return;
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        updatedDomains: ["suppliers"],
    });
});
exports.onSupplierDeleted = (0, firestore_1.onDocumentDeleted)("suppliers/{supplierId}", async (event) => {
    if (!event.data)
        return;
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        updatedDomains: ["suppliers"],
    });
});
//# sourceMappingURL=suppliers.js.map