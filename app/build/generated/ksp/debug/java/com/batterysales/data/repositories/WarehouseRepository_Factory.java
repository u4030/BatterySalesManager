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
public final class WarehouseRepository_Factory implements Factory<WarehouseRepository> {
  private final Provider<FirebaseFirestore> firestoreProvider;

  private WarehouseRepository_Factory(Provider<FirebaseFirestore> firestoreProvider) {
    this.firestoreProvider = firestoreProvider;
  }

  @Override
  public WarehouseRepository get() {
    return newInstance(firestoreProvider.get());
  }

  public static WarehouseRepository_Factory create(Provider<FirebaseFirestore> firestoreProvider) {
    return new WarehouseRepository_Factory(firestoreProvider);
  }

  public static WarehouseRepository newInstance(FirebaseFirestore firestore) {
    return new WarehouseRepository(firestore);
  }
}
