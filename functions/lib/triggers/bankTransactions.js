"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.onBankTransactionDeleted = exports.onBankTransactionUpdated = exports.onBankTransactionCreated = void 0;
const firestore_1 = require("firebase-functions/v2/firestore");
const dashboardHub_1 = require("../aggregators/dashboardHub");
function getBankDelta(data) {
    if (!data)
        return 0;
    const type = (data.type || "").toUpperCase();
    const amount = Number(data.amount || 0);
    if (type === "DEPOSIT" || type === "INCOME") {
        return amount;
    }
    else if (type === "WITHDRAWAL" || type === "EXPENSE") {
        return -amount;
    }
    return 0;
}
exports.onBankTransactionCreated = (0, firestore_1.onDocumentCreated)("bank_transactions/{transactionId}", async (event) => {
    if (!event.data)
        return;
    const delta = getBankDelta(event.data.data());
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        bankDelta: delta,
        updatedDomains: ["financial"],
    });
});
exports.onBankTransactionUpdated = (0, firestore_1.onDocumentUpdated)("bank_transactions/{transactionId}", async (event) => {
    if (!event.data)
        return;
    const beforeDelta = getBankDelta(event.data.before.data());
    const afterDelta = getBankDelta(event.data.after.data());
    const netDelta = afterDelta - beforeDelta;
    if (Math.abs(netDelta) < 0.0001)
        return;
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        bankDelta: netDelta,
        updatedDomains: ["financial"],
    });
});
exports.onBankTransactionDeleted = (0, firestore_1.onDocumentDeleted)("bank_transactions/{transactionId}", async (event) => {
    if (!event.data)
        return;
    const delta = -getBankDelta(event.data.data());
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        bankDelta: delta,
        updatedDomains: ["financial"],
    });
});
//# sourceMappingURL=bankTransactions.js.map