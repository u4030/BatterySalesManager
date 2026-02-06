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
public final class ApprovalsViewModel_Factory implements Factory<ApprovalsViewModel> {
  private final Provider<StockEntryRepository> stockEntryRepositoryProvider;

  private final Provider<ProductRepository> productRepositoryProvider;

  private final Provider<ProductVariantRepository> productVariantRepositoryProvider;

  private final Provider<WarehouseRepository> warehouseRepositoryProvider;

  private ApprovalsViewModel_Factory(Provider<StockEntryRepository> stockEntryRepositoryProvider,
      Provider<ProductRepository> productRepositoryProvider,
      Provider<ProductVariantRepository> productVariantRepositoryProvider,
      Provider<WarehouseRepository> warehouseRepositoryProvider) {
    this.stockEntryRepositoryProvider = stockEntryRepositoryProvider;
    this.productRepositoryProvider = productRepositoryProvider;
    this.productVariantRepositoryProvider = productVariantRepositoryProvider;
    this.warehouseRepositoryProvider = warehouseRepositoryProvider;
  }

  @Override
  public ApprovalsViewModel get() {
    return newInstance(stockEntryRepositoryProvider.get(), productRepositoryProvider.get(), productVariantRepositoryProvider.get(), warehouseRepositoryProvider.get());
  }

  public static ApprovalsViewModel_Factory create(
      Provider<StockEntryRepository> stockEntryRepositoryProvider,
      Provider<ProductRepository> productRepositoryProvider,
      Provider<ProductVariantRepository> productVariantRepositoryProvider,
      Provider<WarehouseRepository> warehouseRepositoryProvider) {
    return new ApprovalsViewModel_Factory(stockEntryRepositoryProvider, productRepositoryProvider, productVariantRepositoryProvider, warehouseRepositoryProvider);
  }

  public static ApprovalsViewModel newInstance(StockEntryRepository stockEntryRepository,
      ProductRepository productRepository, ProductVariantRepository productVariantRepository,
      WarehouseRepository warehouseRepository) {
    return new ApprovalsViewModel(stockEntryRepository, productRepository, productVariantRepository, warehouseRepository);
  }
}
