package com.batterysales.viewmodel;

import com.batterysales.data.repositories.ProductRepository;
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
public final class PurchaseOrderViewModel_Factory implements Factory<PurchaseOrderViewModel> {
  private final Provider<ProductRepository> productRepositoryProvider;

  private PurchaseOrderViewModel_Factory(Provider<ProductRepository> productRepositoryProvider) {
    this.productRepositoryProvider = productRepositoryProvider;
  }

  @Override
  public PurchaseOrderViewModel get() {
    return newInstance(productRepositoryProvider.get());
  }

  public static PurchaseOrderViewModel_Factory create(
      Provider<ProductRepository> productRepositoryProvider) {
    return new PurchaseOrderViewModel_Factory(productRepositoryProvider);
  }

  public static PurchaseOrderViewModel newInstance(ProductRepository productRepository) {
    return new PurchaseOrderViewModel(productRepository);
  }
}
