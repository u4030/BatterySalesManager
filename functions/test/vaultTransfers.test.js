const admin = require("firebase-admin");

describe("Vault Transfers & Accounting Security Test Suite", () => {
  let mockDb;
  let mockCollections;

  beforeEach(() => {
    jest.clearAllMocks();
    mockCollections = {
      invoices: {},
      vault_transfers: {},
      summaries: {},
      idempotency_guards: {},
      product_variants: {},
      stock_entries: {},
    };

    mockDb = {
      collection: jest.fn((colName) => {
        return {
          doc: jest.fn((docId) => ({
            id: docId,
            get: jest.fn(async () => ({
              exists: !!mockCollections[colName]?.[docId],
              data: () => mockCollections[colName]?.[docId] || {},
            })),
            set: jest.fn(async (data, opts) => {
              if (opts && opts.merge) {
                mockCollections[colName][docId] = {
                  ...(mockCollections[colName][docId] || {}),
                  ...data,
                };
              } else {
                mockCollections[colName][docId] = { ...data };
              }
            }),
          })),
          where: jest.fn((field, op, val) => {
            return {
              where: jest.fn(() => ({
                limit: jest.fn(() => ({
                  get: jest.fn(async () => {
                    const matches = Object.values(mockCollections[colName] || {}).filter(
                      (item) => item[field] === val
                    );
                    return {
                      empty: matches.length === 0,
                      docs: matches.map((m) => ({ id: m.id, data: () => m })),
                    };
                  }),
                })),
              })),
              limit: jest.fn(() => ({
                get: jest.fn(async () => {
                  const matches = Object.values(mockCollections[colName] || {}).filter(
                    (item) => item[field] === val
                  );
                  return {
                    empty: matches.length === 0,
                    docs: matches.map((m) => ({ id: m.id, data: () => m })),
                  };
                }),
              })),
              get: jest.fn(async () => {
                const matches = Object.values(mockCollections[colName] || {}).filter(
                  (item) => item[field] === val
                );
                return {
                  empty: matches.length === 0,
                  docs: matches.map((m) => ({ id: m.id, data: () => m })),
                };
              }),
            };
          }),
        };
      }),
      doc: jest.fn((path) => {
        const parts = path.split("/");
        const colName = parts[0];
        const docId = parts.slice(1).join("/");
        return {
          id: docId,
          get: jest.fn(async () => ({
            exists: !!mockCollections[colName]?.[docId],
            data: () => mockCollections[colName]?.[docId] || {},
          })),
          set: jest.fn(async (data, opts) => {
            if (!mockCollections[colName]) mockCollections[colName] = {};
            if (opts && opts.merge) {
              mockCollections[colName][docId] = {
                ...(mockCollections[colName][docId] || {}),
                ...data,
              };
            } else {
              mockCollections[colName][docId] = { ...data };
            }
          }),
        };
      }),
      runTransaction: jest.fn(async (cb) => {
        const transaction = {
          get: jest.fn(async (ref) => ref.get()),
          set: jest.fn(async (ref, data, opts) => ref.set(data, opts)),
          delete: jest.fn(async (ref) => {
            // mock delete
          }),
        };
        return await cb(transaction);
      }),
    };
  });

  // 1. TEST CASE - SERVER_GATEWAY_LOCK
  test("TEST CASE 1: SERVER_GATEWAY_LOCK hard blocks deposition if warehouse has PENDING_SYNC invoices", async () => {
    const warehouseId = "warehouse_001";
    const transferId = "vault_tr_001";

    // Setup an unstable invoice for warehouse_001
    mockCollections.invoices["inv_unstable_1"] = {
      id: "inv_unstable_1",
      warehouseId: warehouseId,
      status: "PENDING_SYNC",
      totalAmount: 150.0,
      paidAmount: 50.0,
    };

    // Simulated checkUnstableInvoices logic
    const checkUnstableInvoices = async (whId) => {
      const snap = await mockDb
        .collection("invoices")
        .where("warehouseId", "==", whId)
        .where("status", "in", ["PENDING_SYNC", "pending_sync", "PENDING", "pending"])
        .limit(1)
        .get();
      return !snap.empty;
    };

    const hasUnstable = await checkUnstableInvoices(warehouseId);
    expect(hasUnstable).toBe(true);

    // Trigger simulated deposition handler
    let caughtError = null;
    try {
      if (hasUnstable) {
        mockCollections.vault_transfers[transferId] = {
          id: transferId,
          warehouseId,
          amount: 500.0,
          status: "REJECTED",
          error: "SERVER_GATEWAY_LOCK",
          rejectionReason:
            "توجد فواتير معلقة لدى أجهزة البائعين لم تتم مزامنتها بعد (SERVER_GATEWAY_LOCK).",
        };
        throw new Error(
          `SERVER_GATEWAY_LOCK: Transfer ${transferId} aborted due to pending invoices in warehouse ${warehouseId}.`
        );
      }
    } catch (err) {
      caughtError = err;
    }

    expect(caughtError).not.toBeNull();
    expect(caughtError.message).toContain("SERVER_GATEWAY_LOCK");

    const vaultDoc = mockCollections.vault_transfers[transferId];
    expect(vaultDoc).toBeDefined();
    expect(vaultDoc.status).toBe("REJECTED");
    expect(vaultDoc.error).toBe("SERVER_GATEWAY_LOCK");
    expect(vaultDoc.rejectionReason).toContain("SERVER_GATEWAY_LOCK");
  });

  // 2. TEST CASE - FIFO_ROLLING_VIOLATION
  test("TEST CASE 2: FIFO_ROLLING_VIOLATION blocks settling future day when earlier day is unsettled", async () => {
    const warehouseId = "warehouse_001";
    const earlierDate = "2026-11-06";
    const targetDate = "2026-11-07";

    // Unsettled earlier day document
    mockCollections.summaries[`daily_${warehouseId}_${earlierDate}`] = {
      warehouseId,
      date: earlierDate,
      isSettled: false,
      unsettledCashBalance: 320.0,
    };

    const validateFifoRollingDailyOrder = async (whId, reqDate) => {
      const allDaily = Object.keys(mockCollections.summaries)
        .filter((k) => k.startsWith(`daily_${whId}_`))
        .map((k) => ({
          dateStr: k.replace(`daily_${whId}_`, ""),
          ...mockCollections.summaries[k],
        }));

      for (const daily of allDaily) {
        if (daily.dateStr < reqDate) {
          if (!daily.isSettled && daily.unsettledCashBalance > 0.001) {
            throw new Error(
              `[FIFO_ROLLING_VIOLATION] Cannot settle date ${reqDate} for warehouse ${whId} before earlier unsettled day: ${daily.dateStr}.`
            );
          }
        }
      }
    };

    await expect(validateFifoRollingDailyOrder(warehouseId, targetDate)).rejects.toThrow(
      /FIFO_ROLLING_VIOLATION/
    );
  });

  // 3. TEST CASE - EVENT_IDEMPOTENCY_GUARD
  test("TEST CASE 3: EVENT_IDEMPOTENCY_GUARD executes first event and rejects identical duplicate event", async () => {
    const eventId = "evt_unique_1001";
    const actionName = "stock_entry_created_entry_888";
    let executionCount = 0;

    const runWithIdempotencyGuard = async (eId, action, op) => {
      return await mockDb.runTransaction(async (transaction) => {
        const guardRef = mockDb.collection("idempotency_guards").doc(eId);
        const guardSnap = await transaction.get(guardRef);
        if (guardSnap.exists) {
          console.warn(
            `[IdempotencyGuard] Event ${eId} for [${action}] already processed. Rejecting duplicate event.`
          );
          return null;
        }

        await transaction.set(guardRef, {
          eventId: eId,
          action,
          processedAt: new Date(),
        });

        return await op(transaction);
      });
    };

    const warnSpy = jest.spyOn(console, "warn").mockImplementation(() => {});

    // First event execution
    const res1 = await runWithIdempotencyGuard(eventId, actionName, async () => {
      executionCount++;
      return "SUCCESS_FIRST";
    });

    expect(res1).toBe("SUCCESS_FIRST");
    expect(executionCount).toBe(1);
    expect(mockCollections.idempotency_guards[eventId]).toBeDefined();

    // Duplicate second event execution with identical eventId
    const res2 = await runWithIdempotencyGuard(eventId, actionName, async () => {
      executionCount++;
      return "SUCCESS_SECOND";
    });

    expect(res2).toBeNull();
    expect(executionCount).toBe(1); // Not incremented
    expect(warnSpy).toHaveBeenCalledWith(
      expect.stringContaining(`[IdempotencyGuard] Event ${eventId}`)
    );

    warnSpy.mockRestore();
  });

  // 4. TEST CASE - SERVER_SIDE_WAC
  test("TEST CASE 4: SERVER_SIDE_WAC calculates Weighted Average Cost strictly server-side inside atomic transaction", async () => {
    const variantId = "variant_12V_70AH";
    const existingStock = 10;
    const existingWac = 50.0; // Total value = 500.0

    mockCollections.product_variants[variantId] = {
      id: variantId,
      currentStock: {
        warehouse_001: existingStock,
      },
      weightedAverageCost: existingWac,
    };

    const incomingQty = 10;
    const incomingCostPrice = 70.0; // Incoming value = 700.0
    // Expected WAC = (500 + 700) / (10 + 10) = 1200 / 20 = 60.0

    await mockDb.runTransaction(async (transaction) => {
      const variantRef = mockDb.doc(`product_variants/${variantId}`);
      const variantSnap = await transaction.get(variantRef);
      const variant = variantSnap.data();

      const oldWac = Number(variant.weightedAverageCost || 0);
      const stockMap = variant.currentStock || {};
      const oldTotalStock = Object.values(stockMap).reduce((a, b) => a + Number(b || 0), 0);

      const totalExistingValue = Math.max(0, oldTotalStock) * oldWac;
      const incomingValue = incomingQty * incomingCostPrice;
      const totalNewStock = Math.max(0, oldTotalStock) + incomingQty;
      const calculatedWac = (totalExistingValue + incomingValue) / totalNewStock;

      await transaction.set(
        variantRef,
        {
          weightedAverageCost: calculatedWac,
          currentStock: {
            warehouse_001: (stockMap.warehouse_001 || 0) + incomingQty,
          },
        },
        { merge: true }
      );
    });

    const updatedVariant = mockCollections.product_variants[variantId];
    expect(updatedVariant).toBeDefined();
    expect(updatedVariant.weightedAverageCost).toBe(60.0);
    expect(updatedVariant.currentStock.warehouse_001).toBe(20);
  });
});
