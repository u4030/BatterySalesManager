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
exports.onInvoiceDeleted = exports.onInvoiceUpdated = exports.onInvoiceCreated = void 0;
const firestore_1 = require("firebase-functions/v2/firestore");
const admin = __importStar(require("firebase-admin"));
const firestore_2 = require("firebase-admin/firestore");
const dashboardHub_1 = require("../aggregators/dashboardHub");
const idempotency_1 = require("../utils/idempotency");
exports.onInvoiceCreated = (0, firestore_1.onDocumentCreated)("invoices/{invoiceId}", async (event) => {
    if (!event.data)
        return;
    const invoiceId = event.params.invoiceId;
    const eventId = event.id;
    console.time(`onInvoiceCreated_${invoiceId}`);
    await (0, idempotency_1.runWithIdempotencyGuard)(eventId, `invoice_created_${invoiceId}`, async (transaction) => {
        const db = admin.firestore();
        const invoice = event.data?.data();
        if (!invoice)
            return;
        // Scoped Block Variables: Isolation to prevent shared reference pollution
        const currentWarehouseId = String(invoice.warehouseId || "").trim();
        const items = (invoice.items || []);
        const totalAmount = Number(invoice.totalAmount || invoice.finalAmount || 0);
        const paidAmount = Number(invoice.paidAmount || 0);
        const initialDebt = Math.max(0, totalAmount - paidAmount);
        const paymentMethod = invoice.paymentMethod || "cash";
        const isBank = paymentMethod === "bank";
        const now = firestore_2.FieldValue.serverTimestamp();
        // --- READ PHASE: ALL READS MUST OCCUR BEFORE ANY WRITES ---
        let correctWarehouseName = "مستودع غير معروف";
        if (currentWarehouseId) {
            const whSnap = await transaction.get(db.collection("warehouses").doc(currentWarehouseId));
            correctWarehouseName = whSnap.exists ? (whSnap.data()?.name || "المستودع") : "مستودع غير معروف";
        }
        // Pre-fetch all variant documents strictly in the READ phase before any writes
        const validItems = items.filter((it) => it.productId && Number(it.quantity || 0) > 0);
        const variantDocRefs = validItems.map((it) => db.doc(`product_variants/${it.productId}`));
        const variantSnaps = await Promise.all(variantDocRefs.map((ref) => transaction.get(ref)));
        // --- WRITE PHASE ---
        validItems.forEach((item, index) => {
            const variantId = item.productId;
            const qty = Number(item.quantity || 0);
            const variantSnap = variantSnaps[index];
            if (currentWarehouseId) {
                transaction.set(db.doc(`product_variants/${variantId}`), {
                    currentStock: {
                        [currentWarehouseId]: firestore_2.FieldValue.increment(-qty),
                    },
                }, { merge: true });
                transaction.set(db.doc(`summaries/inventory_wh_${currentWarehouseId}`), {
                    [`items.${variantId}.currentStock`]: firestore_2.FieldValue.increment(-qty),
                    lastUpdated: now,
                    version: firestore_2.FieldValue.increment(1),
                }, { merge: true });
                // Check if sale dropped stock to <= minQuantity and create low-stock alert atomically
                if (variantSnap.exists) {
                    const vData = variantSnap.data() || {};
                    const stockMap = (vData.currentStock || {});
                    const existingWhStock = Number(stockMap[currentWarehouseId] || 0);
                    const newWhStock = existingWhStock - qty;
                    const minQty = Number((vData.minQuantities || {})[currentWarehouseId] ?? vData.minQuantity ?? 0);
                    if (newWhStock <= minQty && minQty > 0) {
                        const alertDocId = `low_stock_${variantId}_${currentWarehouseId}`;
                        const alertRef = db.doc(`system_alerts/${alertDocId}`);
                        const cap = Number(vData.capacity || 0);
                        const sp = String(vData.specification || "");
                        transaction.set(alertRef, {
                            id: alertDocId,
                            type: "low_stock",
                            title: "مخزون منخفض حرج",
                            message: `الكمية المتوفرة حرجة في ${correctWarehouseName}`,
                            variantId: variantId,
                            warehouseId: currentWarehouseId,
                            warehouseName: correctWarehouseName,
                            resolved: false,
                            status: "active",
                            timestamp: now,
                            currentStock: newWhStock,
                            threshold: minQty,
                            capacity: cap,
                            specification: sp,
                            data: {
                                capacity: cap,
                                specification: sp,
                                currentStock: newWhStock,
                                threshold: minQty,
                                warehouseName: correctWarehouseName,
                            },
                        }, { merge: true });
                    }
                }
            }
            transaction.set(db.doc("summaries/inventory_global"), {
                [`items.${variantId}.currentStock`]: firestore_2.FieldValue.increment(-qty),
                lastUpdated: now,
                version: firestore_2.FieldValue.increment(1),
            }, { merge: true });
        });
        // Calculate localized date string in Asia/Amman (UTC+3) to ensure temporal consistency
        const todayDateStr = new Intl.DateTimeFormat("en-CA", {
            timeZone: "Asia/Amman",
            year: "numeric",
            month: "2-digit",
            day: "2-digit",
        }).format(new Date());
        const whBalanceUpdate = {
            cashBalance: firestore_2.FieldValue.increment(!isBank ? paidAmount : 0),
            bankBalance: firestore_2.FieldValue.increment(isBank ? paidAmount : 0),
            todayCollection: firestore_2.FieldValue.increment(paidAmount),
            todayCollectionCount: firestore_2.FieldValue.increment(paidAmount > 0 ? 1 : 0),
            lastInvoiceDate: todayDateStr,
        };
        if (initialDebt > 0) {
            whBalanceUpdate.pendingCollection = firestore_2.FieldValue.increment(initialDebt);
            whBalanceUpdate.totalReceivables = firestore_2.FieldValue.increment(initialDebt);
        }
        if (currentWarehouseId) {
            transaction.set(db.doc("summaries/financial_status"), {
                warehouseBalances: {
                    [currentWarehouseId]: whBalanceUpdate,
                },
                todayCollection: firestore_2.FieldValue.increment(paidAmount),
                todayCollectionCount: firestore_2.FieldValue.increment(paidAmount > 0 ? 1 : 0),
                totalCustomerDebt: firestore_2.FieldValue.increment(initialDebt),
                totalReceivables: firestore_2.FieldValue.increment(initialDebt),
                lastUpdated: now,
                version: firestore_2.FieldValue.increment(1),
            }, { merge: true });
            transaction.set(db.doc("summaries/dashboard_hub"), {
                version: firestore_2.FieldValue.increment(1),
                lastUpdated: now,
            }, { merge: true });
            // Compound document for isolated daily sales aggregation: summaries/daily_${currentWarehouseId}_${todayDateStr}
            // Write both currentWarehouseId and correctWarehouseName in both naming conventions to guarantee zero pollution
            transaction.set(db.doc(`summaries/daily_${currentWarehouseId}_${todayDateStr}`), {
                warehouseId: currentWarehouseId,
                warehouse_id: currentWarehouseId,
                warehouseName: correctWarehouseName,
                warehouse_name: correctWarehouseName,
                date: todayDateStr,
                totalSales: firestore_2.FieldValue.increment(totalAmount),
                totalInvoices: firestore_2.FieldValue.increment(1),
                totalPaid: firestore_2.FieldValue.increment(paidAmount),
                totalDebt: firestore_2.FieldValue.increment(initialDebt),
                unsettledCashBalance: firestore_2.FieldValue.increment(!isBank ? paidAmount : 0),
                isSettled: false,
                lastUpdated: now,
            }, { merge: true });
        }
    });
    const invoice = event.data?.data();
    if (invoice) {
        const warehouseId = invoice.warehouseId || "";
        const paidAmount = Number(invoice.paidAmount || 0);
        const isBank = (invoice.paymentMethod || "cash") === "bank";
        const scrapAmps = Number(invoice.oldBatteriesTotalAmperes || 0);
        const scrapVal = Number(invoice.oldBatteriesValue || 0);
        const subWarehouseDeltas = {};
        if (warehouseId) {
            subWarehouseDeltas[warehouseId] = !isBank ? paidAmount : 0;
        }
        await (0, dashboardHub_1.applyDashboardHubDelta)({
            cashDelta: !isBank ? paidAmount : 0,
            bankDelta: isBank ? paidAmount : 0,
            scrapAmperesDelta: scrapAmps,
            scrapValueDelta: scrapVal,
            subWarehouseDeltas: Object.keys(subWarehouseDeltas).length > 0 ? subWarehouseDeltas : undefined,
            updatedDomains: ["inventory", "financial"],
        });
    }
    console.timeEnd(`onInvoiceCreated_${invoiceId}`);
});
exports.onInvoiceUpdated = (0, firestore_1.onDocumentUpdated)("invoices/{invoiceId}", async (event) => {
    if (!event.data)
        return;
    const invoiceId = event.params.invoiceId;
    const eventId = event.id;
    const b = event.data.before.data() || {};
    const a = event.data.after.data() || {};
    if (b.paidAmount === a.paidAmount &&
        b.remainingAmount === a.remainingAmount &&
        b.status === a.status) {
        return;
    }
    await (0, idempotency_1.runWithIdempotencyGuard)(eventId, `invoice_updated_${invoiceId}`, async () => {
        await (0, dashboardHub_1.applyDashboardHubDelta)({
            updatedDomains: ["inventory", "financial"],
        });
    });
});
exports.onInvoiceDeleted = (0, firestore_1.onDocumentDeleted)("invoices/{invoiceId}", async (event) => {
    if (!event.data)
        return;
    const invoiceId = event.params.invoiceId;
    const eventId = event.id;
    const db = admin.firestore();
    const invoice = event.data.data() || {};
    const warehouseId = invoice.warehouseId || "";
    const items = (invoice.items || []);
    const now = firestore_2.FieldValue.serverTimestamp();
    await (0, idempotency_1.runWithIdempotencyGuard)(eventId, `invoice_deleted_${invoiceId}`, async (transaction) => {
        if (items.length > 0 && warehouseId) {
            // Read Phase: inspect safety stock threshold and current stock for each variant
            const variantRefs = items.map((item) => db.doc(`product_variants/${item.productId}`));
            const alertRefs = items.map((item) => db.doc(`system_alerts/low_stock_${item.productId}_${warehouseId}`));
            const variantSnaps = await Promise.all(variantRefs.map((ref) => transaction.get(ref)));
            const alertSnaps = await Promise.all(alertRefs.map((ref) => transaction.get(ref)));
            let alertDeletionsCount = 0;
            // Write Phase:
            // Perform atomic evaluation of the threshold and delete alertRef strictly inside the same transaction block
            items.forEach((item, index) => {
                const variantSnap = variantSnaps[index];
                const alertSnap = alertSnaps[index];
                const qty = Number(item.quantity || 0);
                if (variantSnap.exists && qty > 0) {
                    const variantData = variantSnap.data() || {};
                    const currentStockMap = (variantData.currentStock || {});
                    const currentWhStock = Number(currentStockMap[warehouseId] || 0);
                    const restoredStock = currentWhStock + qty;
                    const minQty = Number((variantData.minQuantities || {})[warehouseId] ?? variantData.minQuantity ?? 0);
                    // Immediate threshold evaluation: if restored stock is strictly greater than safety threshold,
                    // delete the low-stock alert atomically inside the same transaction block to eliminate timing gaps
                    if (restoredStock > minQty && alertSnap.exists) {
                        transaction.delete(alertRefs[index]);
                        alertDeletionsCount++;
                    }
                    transaction.set(variantRefs[index], {
                        currentStock: {
                            [warehouseId]: firestore_2.FieldValue.increment(qty),
                        },
                        updatedAt: now,
                    }, { merge: true });
                    transaction.set(db.doc(`summaries/inventory_wh_${warehouseId}`), {
                        [`items.${item.productId}.currentStock`]: firestore_2.FieldValue.increment(qty),
                        lastUpdated: now,
                        version: firestore_2.FieldValue.increment(1),
                    }, { merge: true });
                    transaction.set(db.doc("summaries/inventory_global"), {
                        [`items.${item.productId}.currentStock`]: firestore_2.FieldValue.increment(qty),
                        lastUpdated: now,
                        version: firestore_2.FieldValue.increment(1),
                    }, { merge: true });
                }
            });
            if (alertDeletionsCount > 0) {
                transaction.set(db.doc("summaries/dashboard_hub"), {
                    "scrapAndAlertsSummary.activeLowStockAlertsCount": firestore_2.FieldValue.increment(-alertDeletionsCount),
                    version: firestore_2.FieldValue.increment(1),
                    lastUpdated: now,
                }, { merge: true });
            }
            transaction.set(db.doc("summaries/financial_status"), {
                version: firestore_2.FieldValue.increment(1),
                lastUpdated: now,
            }, { merge: true });
        }
    });
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        updatedDomains: ["inventory", "financial", "alerts"],
    });
});
//# sourceMappingURL=invoices.js.map