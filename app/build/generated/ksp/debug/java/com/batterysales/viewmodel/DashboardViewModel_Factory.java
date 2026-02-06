package com.batterysales.viewmodel;

import com.batterysales.data.repositories.BillRepository;
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
public final class DashboardViewModel_Factory implements Factory<DashboardViewModel> {
  private final Provider<StockEntryRepository> stockEntryRepositoryProvider;

  private final Provider<ProductVariantRepository> productVariantRepositoryProvider;

  private final Provider<ProductRepository> productRepositoryProvider;

  private final Provider<BillRepository> billRepositoryProvider;

  private final Provider<WarehouseRepository> warehouseRepositoryProvider;

  private DashboardViewModel_Factory(Provider<StockEntryRepository> stockEntryRepositoryProvider,
      Provider<ProductVariantRepository> productVariantRepositoryProvider,
      Provider<ProductRepository> productRepositoryProvider,
      Provider<BillRepository> billRepositoryProvider,
      Provider<WarehouseRepository> warehouseRepositoryProvider) {
    this.stockEntryRepositoryProvider = stockEntryRepositoryProvider;
    this.productVariantRepositoryProvider = productVariantRepositoryProvider;
    this.productRepositoryProvider = productRepositoryProvider;
    this.billRepositoryProvider = billRepositoryProvider;
    this.warehouseRepositoryProvider = warehouseRepositoryProvider;
  }

  @Override
  public DashboardViewModel get() {
    return newInstance(stockEntryRepositoryProvider.get(), productVariantRepositoryProvider.get(), productRepositoryProvider.get(), billRepositoryProvider.get(), warehouseRepositoryProvider.get());
  }

  public static DashboardViewModel_Factory create(
      Provider<StockEntryRepository> stockEntryRepositoryProvider,
      Provider<ProductVariantRepository> productVariantRepositoryProvider,
      Provider<ProductRepository> productRepositoryProvider,
      Provider<BillRepository> billRepositoryProvider,
      Provider<WarehouseRepository> warehouseRepositoryProvider) {
    return new DashboardViewModel_Factory(stockEntryRepositoryProvider, productVariantRepositoryProvider, productRepositoryProvider, billRepositoryProvider, warehouseRepositoryProvider);
  }

  public static DashboardViewModel newInstance(StockEntryRepository stockEntryRepository,
      ProductVariantRepository productVariantRepository, ProductRepository productRepository,
      BillRepository billRepository, WarehouseRepository warehouseRepository) {
    return new DashboardViewModel(stockEntryRepository, productVariantRepository, productRepository, billRepository, warehouseRepository);
  }
}
