import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";

/**
 * Executes a function within an atomic idempotency check using the `idempotency_guards` collection.
 * If the event.id has already been processed, the operation is rejected immediately.
 * Enforces Firestore's rule: ALL READS MUST PRECEDE ALL WRITES WITHIN A TRANSACTION.
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
    const guardSnap = await transaction.get(guardRef); // Read phase 1
    if (guardSnap.exists) {
      console.warn(`[IdempotencyGuard] Event ${eventId} for [${actionName}] already processed. Rejecting duplicate.`);
      return null;
    }

    // Execute inner operation which runs its own read phase first
    const result = await operation(transaction);

    // Write phase appended safely at the end
    transaction.set(guardRef, {
      eventId,
      action: actionName,
      processedAt: FieldValue.serverTimestamp(),
    });

    return result;
  });
}
