package com.batterysales.viewmodel;

import com.batterysales.data.repositories.ProductRepository;
import com.batterysales.data.repositories.ProductVariantRepository;
import com.batterysales.data.repositories.WarehouseRepository;
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
public final class ProductManagementViewModel_Factory implements Factory<ProductManagementViewModel> {
  private final Provider<ProductRepository> productRepositoryProvider;

  private final Provider<ProductVariantRepository> productVariantRepositoryProvider;

  private final Provider<WarehouseRepository> warehouseRepositoryProvider;

  private ProductManagementViewModel_Factory(Provider<ProductRepository> productRepositoryProvider,
      Provider<ProductVariantRepository> productVariantRepositoryProvider,
      Provider<WarehouseRepository> warehouseRepositoryProvider) {
    this.productRepositoryProvider = productRepositoryProvider;
    this.productVariantRepositoryProvider = productVariantRepositoryProvider;
    this.warehouseRepositoryProvider = warehouseRepositoryProvider;
  }

  @Override
  public ProductManagementViewModel get() {
    return newInstance(productRepositoryProvider.get(), productVariantRepositoryProvider.get(), warehouseRepositoryProvider.get());
  }

  public static ProductManagementViewModel_Factory create(
      Provider<ProductRepository> productRepositoryProvider,
      Provider<ProductVariantRepository> productVariantRepositoryProvider,
      Provider<WarehouseRepository> warehouseRepositoryProvider) {
    return new ProductManagementViewModel_Factory(productRepositoryProvider, productVariantRepositoryProvider, warehouseRepositoryProvider);
  }

  public static ProductManagementViewModel newInstance(ProductRepository productRepository,
      ProductVariantRepository productVariantRepository, WarehouseRepository warehouseRepository) {
    return new ProductManagementViewModel(productRepository, productVariantRepository, warehouseRepository);
  }
}
