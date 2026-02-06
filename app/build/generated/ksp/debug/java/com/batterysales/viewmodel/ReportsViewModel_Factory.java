package com.batterysales.viewmodel;

import com.batterysales.data.repositories.BillRepository;
import com.batterysales.data.repositories.OldBatteryRepository;
import com.batterysales.data.repositories.ProductRepository;
import com.batterysales.data.repositories.ProductVariantRepository;
import com.batterysales.data.repositories.StockEntryRepository;
import com.batterysales.data.repositories.SupplierRepository;
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
public final class ReportsViewModel_Factory implements Factory<ReportsViewModel> {
  private final Provider<ProductRepository> productRepositoryProvider;

  private final Provider<ProductVariantRepository> productVariantRepositoryProvider;

  private final Provider<WarehouseRepository> warehouseRepositoryProvider;

  private final Provider<StockEntryRepository> stockEntryRepositoryProvider;

  private final Provider<SupplierRepository> supplierRepositoryProvider;

  private final Provider<BillRepository> billRepositoryProvider;

  private final Provider<OldBatteryRepository> oldBatteryRepositoryProvider;

  private ReportsViewModel_Factory(Provider<ProductRepository> productRepositoryProvider,
      Provider<ProductVariantRepository> productVariantRepositoryProvider,
      Provider<WarehouseRepository> warehouseRepositoryProvider,
      Provider<StockEntryRepository> stockEntryRepositoryProvider,
      Provider<SupplierRepository> supplierRepositoryProvider,
      Provider<BillRepository> billRepositoryProvider,
      Provider<OldBatteryRepository> oldBatteryRepositoryProvider) {
    this.productRepositoryProvider = productRepositoryProvider;
    this.productVariantRepositoryProvider = productVariantRepositoryProvider;
    this.warehouseRepositoryProvider = warehouseRepositoryProvider;
    this.stockEntryRepositoryProvider = stockEntryRepositoryProvider;
    this.supplierRepositoryProvider = supplierRepositoryProvider;
    this.billRepositoryProvider = billRepositoryProvider;
    this.oldBatteryRepositoryProvider = oldBatteryRepositoryProvider;
  }

  @Override
  public ReportsViewModel get() {
    return newInstance(productRepositoryProvider.get(), productVariantRepositoryProvider.get(), warehouseRepositoryProvider.get(), stockEntryRepositoryProvider.get(), supplierRepositoryProvider.get(), billRepositoryProvider.get(), oldBatteryRepositoryProvider.get());
  }

  public static ReportsViewModel_Factory create(
      Provider<ProductRepository> productRepositoryProvider,
      Provider<ProductVariantRepository> productVariantRepositoryProvider,
      Provider<WarehouseRepository> warehouseRepositoryProvider,
      Provider<StockEntryRepository> stockEntryRepositoryProvider,
      Provider<SupplierRepository> supplierRepositoryProvider,
      Provider<BillRepository> billRepositoryProvider,
      Provider<OldBatteryRepository> oldBatteryRepositoryProvider) {
    return new ReportsViewModel_Factory(productRepositoryProvider, productVariantRepositoryProvider, warehouseRepositoryProvider, stockEntryRepositoryProvider, supplierRepositoryProvider, billRepositoryProvider, oldBatteryRepositoryProvider);
  }

  public static ReportsViewModel newInstance(ProductRepository productRepository,
      ProductVariantRepository productVariantRepository, WarehouseRepository warehouseRepository,
      StockEntryRepository stockEntryRepository, SupplierRepository supplierRepository,
      BillRepository billRepository, OldBatteryRepository oldBatteryRepository) {
    return new ReportsViewModel(productRepository, productVariantRepository, warehouseRepository, stockEntryRepository, supplierRepository, billRepository, oldBatteryRepository);
  }
}
