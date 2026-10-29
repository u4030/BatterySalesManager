import * as admin from "firebase-admin";

if (!admin.apps.length) {
  admin.initializeApp({
    projectId: process.env.GCLOUD_PROJECT || process.env.FIREBASE_CONFIG ? JSON.parse(process.env.FIREBASE_CONFIG!).projectId : "demo-battery-sales",
  });
}

export * from "./triggers/invoices";
export * from "./triggers/salesOrders";
export * from "./triggers/stockEntries";
export * from "./triggers/transactions";
export * from "./triggers/bankTransactions";
export * from "./triggers/bills";
export * from "./triggers/variants";
export * from "./triggers/alerts";
export * from "./triggers/oldBatteries";
export * from "./triggers/warehouses";
export * from "./triggers/suppliers";
