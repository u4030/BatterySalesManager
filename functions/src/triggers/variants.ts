import {
  onDocumentCreated,
  onDocumentUpdated,
  onDocumentDeleted,
} from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { applyDashboardHubDelta } from "../aggregators/dashboardHub";

export const onVariantCreated = onDocumentCreated(
  "product_variants/{variantId}",
  async (event) => {
    if (!event.data) return;
    const db = admin.firestore();
    const variantId = event.params.variantId;
    const variant = event.data.data();

    if (!variant || variant.archived || variant.isDiscontinued) {
      await applyDashboardHubDelta({ updatedDomains: ["inventory"] });
      return;
    }

    let createdAlertsCount = 0;
    try {
      const warehousesSnap = await db.collection("warehouses").get();
      const warehouseDocs = warehousesSnap.docs;

      let pName = variant.productName || "";
      if (!pName && variant.productId) {
        const productSnap = await db.collection("products").doc(variant.productId).get();
        if (productSnap.exists) {
          pName = productSnap.data()?.name || "منتج";
        }
      }
      if (!pName) pName = "منتج";

      const batch = db.batch();
      const currentStockMap = (variant.currentStock || {}) as Record<string, number>;
      const minQuantitiesMap = (variant.minQuantities || {}) as Record<string, number>;
      const defaultMinQty = Number(variant.minQuantity || 0);

      warehouseDocs.forEach((whDoc) => {
        const whId = whDoc.id;
        const whName = whDoc.data()?.name || "مخزن";
        const currentQty = currentStockMap[whId] ?? 0;
        const threshold = minQuantitiesMap[whId] ?? defaultMinQty;

        if (threshold > 0 && currentQty <= threshold) {
          const alertRef = db.collection("system_alerts").doc(`low_stock_${variantId}_${whId}`);
          const specSuffix = variant.specification ? ` | ${variant.specification}` : "";

          batch.set(alertRef, {
            id: alertRef.id,
            type: "low_stock",
            title: `مخزون منخفض: ${pName}`,
            message: `${variant.capacity || 0}A${specSuffix} في ${whName} | الكمية: ${currentQty} (الحد: ${threshold})`,
            relatedId: variantId,
            warehouseId: whId,
            warehouseName: whName,
            resolved: false,
            timestamp: FieldValue.serverTimestamp(),
            data: {
              capacity: variant.capacity || 0,
              specification: variant.specification || "",
              currentStock: currentQty,
              threshold: threshold,
            },
          });
          createdAlertsCount += 1;
        }
      });

      if (createdAlertsCount > 0) {
        await batch.commit();
      }
    } catch (error) {
      console.error("Error creating low_stock alerts in onVariantCreated:", error);
    }

    await applyDashboardHubDelta({
      activeAlertsDelta: createdAlertsCount,
      updatedDomains: ["inventory", "alerts"],
    });
  }
);

export const onVariantUpdated = onDocumentUpdated(
  "product_variants/{variantId}",
  async (event) => {
    if (!event.data) return;
    await applyDashboardHubDelta({
      updatedDomains: ["inventory"],
    });
  }
);

export const onVariantDeleted = onDocumentDeleted(
  "product_variants/{variantId}",
  async (event) => {
    if (!event.data) return;
    await applyDashboardHubDelta({
      updatedDomains: ["inventory"],
    });
  }
);
