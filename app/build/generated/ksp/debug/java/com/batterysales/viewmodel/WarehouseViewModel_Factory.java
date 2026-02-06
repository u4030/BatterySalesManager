package com.batterysales.viewmodel;

import com.batterysales.data.repositories.ProductRepository;
import com.batterysales.data.repositories.ProductVariantRepository;
import com.batterysales.data.repositories.StockEntryRepository;
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
public final class WarehouseViewModel_Factory implements Factory<WarehouseViewModel> {
  private final Provider<ProductRepository> productRepositoryProvider;

  private final Provider<ProductVariantRepository> productVariantRepositoryProvider;

  private final Provider<WarehouseRepository> warehouseRepositoryProvider;

  private final Provider<StockEntryRepository> stockEntryRepositoryProvider;

  private WarehouseViewModel_Factory(Provider<ProductRepository> productRepositoryProvider,
      Provider<ProductVariantRepository> productVariantRepositoryProvider,
      Provider<WarehouseRepository> warehouseRepositoryProvider,
      Provider<StockEntryRepository> stockEntryRepositoryProvider) {
    this.productRepositoryProvider = productRepositoryProvider;
    this.productVariantRepositoryProvider = productVariantRepositoryProvider;
    this.warehouseRepositoryProvider = warehouseRepositoryProvider;
    this.stockEntryRepositoryProvider = stockEntryRepositoryProvider;
  }

  @Override
  public WarehouseViewModel get() {
    return newInstance(productRepositoryProvider.get(), productVariantRepositoryProvider.get(), warehouseRepositoryProvider.get(), stockEntryRepositoryProvider.get());
  }

  public static WarehouseViewModel_Factory create(
      Provider<ProductRepository> productRepositoryProvider,
      Provider<ProductVariantRepository> productVariantRepositoryProvider,
      Provider<WarehouseRepository> warehouseRepositoryProvider,
      Provider<StockEntryRepository> stockEntryRepositoryProvider) {
    return new WarehouseViewModel_Factory(productRepositoryProvider, productVariantRepositoryProvider, warehouseRepositoryProvider, stockEntryRepositoryProvider);
  }

  public static WarehouseViewModel newInstance(ProductRepository productRepository,
      ProductVariantRepository productVariantRepository, WarehouseRepository warehouseRepository,
      StockEntryRepository stockEntryRepository) {
    return new WarehouseViewModel(productRepository, productVariantRepository, warehouseRepository, stockEntryRepository);
  }
}
