import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";

/**
 * Executes a function within an atomic idempotency check using the `idempotency_guards` collection.
 * If the event.id has already been processed, the operation is rejected immediately.
 *
 * @param eventId The unique event.id from Cloud Functions v2.
 * @param actionName Descriptive name of the action/domain.
 * @param operation The callback to execute if the event is unique.
 */
export async function runWithIdempotencyGuard<T>(
  eventId: string,
  actionName: string,
  operation: (transaction: admin.firestore.Transaction) => Promise<T>
): Promise<T | null> {
  if (!eventId) {
    throw new Error("[IdempotencyGuard] Missing eventId.");
  }

  const db = admin.firestore();
  const guardRef = db.collection("idempotency_guards").doc(eventId);

  return await db.runTransaction(async (transaction) => {
    const guardSnap = await transaction.get(guardRef);
    if (guardSnap.exists) {
      console.warn(`[IdempotencyGuard] Event ${eventId} for [${actionName}] already processed. Rejecting duplicate event.`);
      return null;
    }

    // Mark event as processed atomically inside the transaction
    transaction.set(guardRef, {
      eventId,
      action: actionName,
      processedAt: FieldValue.serverTimestamp(),
    });

    return await operation(transaction);
  });
}
