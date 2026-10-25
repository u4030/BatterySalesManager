"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.onInvoiceDeleted = exports.onInvoiceUpdated = exports.onInvoiceCreated = void 0;
const firestore_1 = require("firebase-functions/v2/firestore");
const dashboardHub_1 = require("../aggregators/dashboardHub");
exports.onInvoiceCreated = (0, firestore_1.onDocumentCreated)("invoices/{invoiceId}", async (event) => {
    if (!event.data)
        return;
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        updatedDomains: ["inventory", "financial", "alerts"],
    });
});
exports.onInvoiceUpdated = (0, firestore_1.onDocumentUpdated)("invoices/{invoiceId}", async (event) => {
    if (!event.data)
        return;
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        updatedDomains: ["inventory", "financial", "alerts"],
    });
});
exports.onInvoiceDeleted = (0, firestore_1.onDocumentDeleted)("invoices/{invoiceId}", async (event) => {
    if (!event.data)
        return;
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        updatedDomains: ["inventory", "financial", "alerts"],
    });
});
//# sourceMappingURL=invoices.js.map