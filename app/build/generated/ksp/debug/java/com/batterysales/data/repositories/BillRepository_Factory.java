package com.batterysales.data.repositories;

import com.google.firebase.firestore.FirebaseFirestore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class BillRepository_Factory implements Factory<BillRepository> {
  private final Provider<FirebaseFirestore> firestoreProvider;

  private BillRepository_Factory(Provider<FirebaseFirestore> firestoreProvider) {
    this.firestoreProvider = firestoreProvider;
  }

  @Override
  public BillRepository get() {
    return newInstance(firestoreProvider.get());
  }

  public static BillRepository_Factory create(Provider<FirebaseFirestore> firestoreProvider) {
    return new BillRepository_Factory(firestoreProvider);
  }

  public static BillRepository newInstance(FirebaseFirestore firestore) {
    return new BillRepository(firestore);
  }
}
