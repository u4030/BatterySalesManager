import * as admin from "firebase-admin";

admin.initializeApp();

export * from "./triggers/invoices";
export * from "./triggers/stockEntries";
export * from "./triggers/transactions";
export * from "./triggers/bankTransactions";
export * from "./triggers/bills";
export * from "./triggers/variants";
export * from "./triggers/alerts";
export * from "./triggers/oldBatteries";
export * from "./triggers/warehouses";
export * from "./triggers/suppliers";
