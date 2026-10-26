"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.onTransactionDeleted = exports.onTransactionUpdated = exports.onTransactionCreated = void 0;
const firestore_1 = require("firebase-functions/v2/firestore");
const dashboardHub_1 = require("../aggregators/dashboardHub");
function getTransactionDelta(data) {
    if (!data)
        return 0;
    const type = (data.type || "").toUpperCase();
    const amount = Number(data.amount || 0);
    if (type === "INCOME" || type === "PAYMENT") {
        return amount;
    }
    else if (type === "EXPENSE" || type === "REFUND") {
        return -amount;
    }
    return 0;
}
exports.onTransactionCreated = (0, firestore_1.onDocumentCreated)("transactions/{transactionId}", async (event) => {
    if (!event.data)
        return;
    const data = event.data.data();
    const delta = getTransactionDelta(data);
    const whId = data.warehouseId;
    const subDeltas = {};
    if (whId && whId !== "main" && whId !== "MAIN") {
        subDeltas[whId] = delta;
    }
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        cashDelta: delta,
        mainTreasuryDelta: (!whId || whId === "main" || whId === "MAIN") ? delta : 0,
        subWarehouseDeltas: Object.keys(subDeltas).length > 0 ? subDeltas : undefined,
        updatedDomains: ["financial"],
    });
});
exports.onTransactionUpdated = (0, firestore_1.onDocumentUpdated)("transactions/{transactionId}", async (event) => {
    if (!event.data)
        return;
    const beforeData = event.data.before.data();
    const afterData = event.data.after.data();
    const beforeDelta = getTransactionDelta(beforeData);
    const afterDelta = getTransactionDelta(afterData);
    const netDelta = afterDelta - beforeDelta;
    if (Math.abs(netDelta) < 0.0001)
        return;
    const whId = afterData.warehouseId;
    const subDeltas = {};
    if (whId && whId !== "main" && whId !== "MAIN") {
        subDeltas[whId] = netDelta;
    }
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        cashDelta: netDelta,
        mainTreasuryDelta: (!whId || whId === "main" || whId === "MAIN") ? netDelta : 0,
        subWarehouseDeltas: Object.keys(subDeltas).length > 0 ? subDeltas : undefined,
        updatedDomains: ["financial"],
    });
});
exports.onTransactionDeleted = (0, firestore_1.onDocumentDeleted)("transactions/{transactionId}", async (event) => {
    if (!event.data)
        return;
    const data = event.data.data();
    const delta = -getTransactionDelta(data);
    const whId = data.warehouseId;
    const subDeltas = {};
    if (whId && whId !== "main" && whId !== "MAIN") {
        subDeltas[whId] = delta;
    }
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        cashDelta: delta,
        mainTreasuryDelta: (!whId || whId === "main" || whId === "MAIN") ? delta : 0,
        subWarehouseDeltas: Object.keys(subDeltas).length > 0 ? subDeltas : undefined,
        updatedDomains: ["financial"],
    });
});
//# sourceMappingURL=transactions.js.map