"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.onWarehouseDeleted = exports.onWarehouseUpdated = exports.onWarehouseCreated = void 0;
const firestore_1 = require("firebase-functions/v2/firestore");
const admin = __importStar(require("firebase-admin"));
const dashboardHub_1 = require("../aggregators/dashboardHub");
exports.onWarehouseCreated = (0, firestore_1.onDocumentCreated)("warehouses/{warehouseId}", async (event) => {
    if (!event.data)
        return;
    const db = admin.firestore();
    const whId = event.params.warehouseId;
    const warehouse = event.data.data();
    if (!warehouse)
        return;
    try {
        const batch = db.batch();
        const scrapDocRef = db.collection("scrap_warehouses").doc(`scrap_wh_${whId}`);
        batch.set(scrapDocRef, {
            id: scrapDocRef.id,
            name: `سكراب - ${warehouse.name || whId}`,
            parentWarehouseId: whId,
            totalQuantity: 0,
            totalAmperes: 0,
        }, { merge: true });
        await batch.commit();
    }
    catch (error) {
        console.error("Error creating scrap_warehouse in onWarehouseCreated:", error);
    }
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        subWarehouseDeltas: { [whId]: 0 },
        updatedDomains: ["inventory", "financial"],
    });
});
exports.onWarehouseUpdated = (0, firestore_1.onDocumentUpdated)("warehouses/{warehouseId}", async (event) => {
    if (!event.data)
        return;
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        updatedDomains: ["inventory", "financial"],
    });
});
exports.onWarehouseDeleted = (0, firestore_1.onDocumentDeleted)("warehouses/{warehouseId}", async (event) => {
    if (!event.data)
        return;
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        updatedDomains: ["inventory", "financial"],
    });
});
//# sourceMappingURL=warehouses.js.map