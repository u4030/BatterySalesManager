"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.onOldBatteryTransactionDeleted = exports.onOldBatteryTransactionUpdated = exports.onOldBatteryTransactionCreated = void 0;
const firestore_1 = require("firebase-functions/v2/firestore");
const dashboardHub_1 = require("../aggregators/dashboardHub");
function getScrapDeltas(data) {
    if (!data)
        return { amperes: 0, amount: 0 };
    const type = (data.type || "").toUpperCase();
    const amperes = Number(data.totalAmperes || 0);
    const amount = Number(data.amount || 0);
    if (type === "INTAKE") {
        return { amperes, amount };
    }
    else if (type === "SALE") {
        return { amperes: -amperes, amount: -amount };
    }
    return { amperes: 0, amount: 0 };
}
exports.onOldBatteryTransactionCreated = (0, firestore_1.onDocumentCreated)("old_battery_transactions/{transactionId}", async (event) => {
    if (!event.data)
        return;
    const { amperes, amount } = getScrapDeltas(event.data.data());
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        scrapAmperesDelta: amperes,
        scrapValueDelta: amount,
        updatedDomains: ["inventory", "financial"],
    });
});
exports.onOldBatteryTransactionUpdated = (0, firestore_1.onDocumentUpdated)("old_battery_transactions/{transactionId}", async (event) => {
    if (!event.data)
        return;
    const before = getScrapDeltas(event.data.before.data());
    const after = getScrapDeltas(event.data.after.data());
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        scrapAmperesDelta: after.amperes - before.amperes,
        scrapValueDelta: after.amount - before.amount,
        updatedDomains: ["inventory", "financial"],
    });
});
exports.onOldBatteryTransactionDeleted = (0, firestore_1.onDocumentDeleted)("old_battery_transactions/{transactionId}", async (event) => {
    if (!event.data)
        return;
    const { amperes, amount } = getScrapDeltas(event.data.data());
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        scrapAmperesDelta: -amperes,
        scrapValueDelta: -amount,
        updatedDomains: ["inventory", "financial"],
    });
});
//# sourceMappingURL=oldBatteries.js.map