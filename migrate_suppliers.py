import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.ApplicationDefault()
firebase_admin.initialize_app(cred)
db = firestore.client()

# 1. Get all suppliers
suppliers = {s.to_dict().get('name').strip().lower(): s.id for s in db.collection('suppliers').get()}
print(f"Found {len(suppliers)} suppliers")

# 2. Get stock entries missing supplierId but having supplier name
entries = db.collection('stock_entries').where('supplierId', '==', '').get()
print(f"Found {len(entries)} entries missing supplierId")

count = 0
batch = db.batch()
for doc in entries:
    data = doc.to_dict()
    name = data.get('supplier', '').strip().lower()
    if name in suppliers:
        batch.update(doc.reference, {'supplierId': suppliers[name]})
        count += 1
        if count % 500 == 0:
            batch.commit()
            batch = db.batch()

if count % 500 != 0:
    batch.commit()

print(f"Migrated {count} entries")
