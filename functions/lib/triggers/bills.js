"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.onBillDeleted = exports.onBillUpdated = exports.onBillCreated = void 0;
const firestore_1 = require("firebase-functions/v2/firestore");
const dashboardHub_1 = require("../aggregators/dashboardHub");
function getBillUnpaidDetails(data) {
    if (!data)
        return { unpaidChecks: 0, unpaidBills: 0, totalAmount: 0 };
    const status = (data.status || "").toUpperCase();
    const type = (data.billType || "").toUpperCase();
    const amount = Number(data.amount || 0);
    const paidAmount = Number(data.paidAmount || 0);
    const remaining = Math.max(0, amount - paidAmount);
    let unpaidChecks = 0;
    let unpaidBills = 0;
    if (status !== "PAID" && status !== "CANCELLED") {
        if (type === "CHECK") {
            unpaidChecks = remaining;
        }
        else {
            unpaidBills = remaining;
        }
    }
    return { unpaidChecks, unpaidBills, totalAmount: amount };
}
exports.onBillCreated = (0, firestore_1.onDocumentCreated)("bills/{billId}", async (event) => {
    if (!event.data)
        return;
    const details = getBillUnpaidDetails(event.data.data());
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        unpaidChecksDelta: details.unpaidChecks,
        unpaidBillsDelta: details.unpaidBills,
        supplierDebtDelta: details.totalAmount,
        updatedDomains: ["financial", "suppliers"],
    });
});
exports.onBillUpdated = (0, firestore_1.onDocumentUpdated)("bills/{billId}", async (event) => {
    if (!event.data)
        return;
    const beforeDetails = getBillUnpaidDetails(event.data.before.data());
    const afterDetails = getBillUnpaidDetails(event.data.after.data());
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        unpaidChecksDelta: afterDetails.unpaidChecks - beforeDetails.unpaidChecks,
        unpaidBillsDelta: afterDetails.unpaidBills - beforeDetails.unpaidBills,
        supplierDebtDelta: afterDetails.totalAmount - beforeDetails.totalAmount,
        updatedDomains: ["financial", "suppliers"],
    });
});
exports.onBillDeleted = (0, firestore_1.onDocumentDeleted)("bills/{billId}", async (event) => {
    if (!event.data)
        return;
    const details = getBillUnpaidDetails(event.data.data());
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        unpaidChecksDelta: -details.unpaidChecks,
        unpaidBillsDelta: -details.unpaidBills,
        supplierDebtDelta: -details.totalAmount,
        updatedDomains: ["financial", "suppliers"],
    });
});
//# sourceMappingURL=bills.js.map