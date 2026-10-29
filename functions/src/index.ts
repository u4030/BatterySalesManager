import * as admin from "firebase-admin";

if (!admin.apps.length) {
  admin.initializeApp({
    projectId: process.env.GCP_PROJECT || process.env.GCLOUD_PROJECT || "demo-battery-sales"
  });
}

export { processSalesOrder, processInvoiceCreated } from "./triggers/salesOrders";
export { processPaymentCommand } from "./triggers/paymentCommands";
export { processReturnCommand } from "./triggers/returnCommands";
