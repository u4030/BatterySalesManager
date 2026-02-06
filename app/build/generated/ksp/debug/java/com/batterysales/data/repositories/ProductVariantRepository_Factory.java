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
public final class ProductVariantRepository_Factory implements Factory<ProductVariantRepository> {
  private final Provider<FirebaseFirestore> firestoreProvider;

  private ProductVariantRepository_Factory(Provider<FirebaseFirestore> firestoreProvider) {
    this.firestoreProvider = firestoreProvider;
  }

  @Override
  public ProductVariantRepository get() {
    return newInstance(firestoreProvider.get());
  }

  public static ProductVariantRepository_Factory create(
      Provider<FirebaseFirestore> firestoreProvider) {
    return new ProductVariantRepository_Factory(firestoreProvider);
  }

  public static ProductVariantRepository newInstance(FirebaseFirestore firestore) {
    return new ProductVariantRepository(firestore);
  }
}
