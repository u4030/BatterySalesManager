package com.batterysales.data.repositories;

import com.google.firebase.firestore.FirebaseFirestore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
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
public final class SupplierRepository_Factory implements Factory<SupplierRepository> {
  private final Provider<FirebaseFirestore> firestoreProvider;

  private SupplierRepository_Factory(Provider<FirebaseFirestore> firestoreProvider) {
    this.firestoreProvider = firestoreProvider;
  }

  @Override
  public SupplierRepository get() {
    return newInstance(firestoreProvider.get());
  }

  public static SupplierRepository_Factory create(Provider<FirebaseFirestore> firestoreProvider) {
    return new SupplierRepository_Factory(firestoreProvider);
  }

  public static SupplierRepository newInstance(FirebaseFirestore firestore) {
    return new SupplierRepository(firestore);
  }
}
