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
public final class InvoiceRepository_Factory implements Factory<InvoiceRepository> {
  private final Provider<FirebaseFirestore> firestoreProvider;

  private InvoiceRepository_Factory(Provider<FirebaseFirestore> firestoreProvider) {
    this.firestoreProvider = firestoreProvider;
  }

  @Override
  public InvoiceRepository get() {
    return newInstance(firestoreProvider.get());
  }

  public static InvoiceRepository_Factory create(Provider<FirebaseFirestore> firestoreProvider) {
    return new InvoiceRepository_Factory(firestoreProvider);
  }

  public static InvoiceRepository newInstance(FirebaseFirestore firestore) {
    return new InvoiceRepository(firestore);
  }
}
