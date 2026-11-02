"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.onAlertDeleted = exports.onAlertUpdated = exports.onAlertCreated = void 0;
const firestore_1 = require("firebase-functions/v2/firestore");
const dashboardHub_1 = require("../aggregators/dashboardHub");
exports.onAlertCreated = (0, firestore_1.onDocumentCreated)("system_alerts/{alertId}", async (event) => {
    if (!event.data)
        return;
    const isResolved = event.data.data()?.resolved === true;
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        activeAlertsDelta: isResolved ? 0 : 1,
        updatedDomains: ["alerts"],
    });
});
exports.onAlertUpdated = (0, firestore_1.onDocumentUpdated)("system_alerts/{alertId}", async (event) => {
    if (!event.data)
        return;
    const beforeResolved = event.data.before.data()?.resolved === true;
    const afterResolved = event.data.after.data()?.resolved === true;
    let delta = 0;
    if (beforeResolved && !afterResolved)
        delta = 1;
    if (!beforeResolved && afterResolved)
        delta = -1;
    if (delta !== 0) {
        await (0, dashboardHub_1.applyDashboardHubDelta)({
            activeAlertsDelta: delta,
            updatedDomains: ["alerts"],
        });
    }
});
exports.onAlertDeleted = (0, firestore_1.onDocumentDeleted)("system_alerts/{alertId}", async (event) => {
    if (!event.data)
        return;
    const wasResolved = event.data.data()?.resolved === true;
    if (!wasResolved) {
        await (0, dashboardHub_1.applyDashboardHubDelta)({
            activeAlertsDelta: -1,
            updatedDomains: ["alerts"],
        });
    }
});
//# sourceMappingURL=alerts.js.map