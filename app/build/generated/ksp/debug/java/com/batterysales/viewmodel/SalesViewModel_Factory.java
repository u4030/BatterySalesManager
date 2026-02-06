package com.batterysales.viewmodel;

import com.batterysales.data.repositories.AccountingRepository;
import com.batterysales.data.repositories.InvoiceRepository;
import com.batterysales.data.repositories.OldBatteryRepository;
import com.batterysales.data.repositories.PaymentRepository;
import com.batterysales.data.repositories.ProductRepository;
import com.batterysales.data.repositories.ProductVariantRepository;
import com.batterysales.data.repositories.StockEntryRepository;
import com.batterysales.data.repositories.UserRepository;
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
public final class SalesViewModel_Factory implements Factory<SalesViewModel> {
  private final Provider<ProductRepository> productRepositoryProvider;

  private final Provider<ProductVariantRepository> productVariantRepositoryProvider;

  private final Provider<WarehouseRepository> warehouseRepositoryProvider;

  private final Provider<StockEntryRepository> stockEntryRepositoryProvider;

  private final Provider<InvoiceRepository> invoiceRepositoryProvider;

  private final Provider<PaymentRepository> paymentRepositoryProvider;

  private final Provider<UserRepository> userRepositoryProvider;

  private final Provider<AccountingRepository> accountingRepositoryProvider;

  private final Provider<OldBatteryRepository> oldBatteryRepositoryProvider;

  private SalesViewModel_Factory(Provider<ProductRepository> productRepositoryProvider,
      Provider<ProductVariantRepository> productVariantRepositoryProvider,
      Provider<WarehouseRepository> warehouseRepositoryProvider,
      Provider<StockEntryRepository> stockEntryRepositoryProvider,
      Provider<InvoiceRepository> invoiceRepositoryProvider,
      Provider<PaymentRepository> paymentRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider,
      Provider<AccountingRepository> accountingRepositoryProvider,
      Provider<OldBatteryRepository> oldBatteryRepositoryProvider) {
    this.productRepositoryProvider = productRepositoryProvider;
    this.productVariantRepositoryProvider = productVariantRepositoryProvider;
    this.warehouseRepositoryProvider = warehouseRepositoryProvider;
    this.stockEntryRepositoryProvider = stockEntryRepositoryProvider;
    this.invoiceRepositoryProvider = invoiceRepositoryProvider;
    this.paymentRepositoryProvider = paymentRepositoryProvider;
    this.userRepositoryProvider = userRepositoryProvider;
    this.accountingRepositoryProvider = accountingRepositoryProvider;
    this.oldBatteryRepositoryProvider = oldBatteryRepositoryProvider;
  }

  @Override
  public SalesViewModel get() {
    return newInstance(productRepositoryProvider.get(), productVariantRepositoryProvider.get(), warehouseRepositoryProvider.get(), stockEntryRepositoryProvider.get(), invoiceRepositoryProvider.get(), paymentRepositoryProvider.get(), userRepositoryProvider.get(), accountingRepositoryProvider.get(), oldBatteryRepositoryProvider.get());
  }

  public static SalesViewModel_Factory create(Provider<ProductRepository> productRepositoryProvider,
      Provider<ProductVariantRepository> productVariantRepositoryProvider,
      Provider<WarehouseRepository> warehouseRepositoryProvider,
      Provider<StockEntryRepository> stockEntryRepositoryProvider,
      Provider<InvoiceRepository> invoiceRepositoryProvider,
      Provider<PaymentRepository> paymentRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider,
      Provider<AccountingRepository> accountingRepositoryProvider,
      Provider<OldBatteryRepository> oldBatteryRepositoryProvider) {
    return new SalesViewModel_Factory(productRepositoryProvider, productVariantRepositoryProvider, warehouseRepositoryProvider, stockEntryRepositoryProvider, invoiceRepositoryProvider, paymentRepositoryProvider, userRepositoryProvider, accountingRepositoryProvider, oldBatteryRepositoryProvider);
  }

  public static SalesViewModel newInstance(ProductRepository productRepository,
      ProductVariantRepository productVariantRepository, WarehouseRepository warehouseRepository,
      StockEntryRepository stockEntryRepository, InvoiceRepository invoiceRepository,
      PaymentRepository paymentRepository, UserRepository userRepository,
      AccountingRepository accountingRepository, OldBatteryRepository oldBatteryRepository) {
    return new SalesViewModel(productRepository, productVariantRepository, warehouseRepository, stockEntryRepository, invoiceRepository, paymentRepository, userRepository, accountingRepository, oldBatteryRepository);
  }
}
