package com.batterysales.viewmodel;

import com.batterysales.data.repositories.AccountingRepository;
import com.batterysales.data.repositories.InvoiceRepository;
import com.batterysales.data.repositories.PaymentRepository;
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
public final class InvoiceViewModel_Factory implements Factory<InvoiceViewModel> {
  private final Provider<InvoiceRepository> invoiceRepositoryProvider;

  private final Provider<StockEntryRepository> stockEntryRepositoryProvider;

  private final Provider<WarehouseRepository> warehouseRepositoryProvider;

  private final Provider<PaymentRepository> paymentRepositoryProvider;

  private final Provider<AccountingRepository> accountingRepositoryProvider;

  private InvoiceViewModel_Factory(Provider<InvoiceRepository> invoiceRepositoryProvider,
      Provider<StockEntryRepository> stockEntryRepositoryProvider,
      Provider<WarehouseRepository> warehouseRepositoryProvider,
      Provider<PaymentRepository> paymentRepositoryProvider,
      Provider<AccountingRepository> accountingRepositoryProvider) {
    this.invoiceRepositoryProvider = invoiceRepositoryProvider;
    this.stockEntryRepositoryProvider = stockEntryRepositoryProvider;
    this.warehouseRepositoryProvider = warehouseRepositoryProvider;
    this.paymentRepositoryProvider = paymentRepositoryProvider;
    this.accountingRepositoryProvider = accountingRepositoryProvider;
  }

  @Override
  public InvoiceViewModel get() {
    return newInstance(invoiceRepositoryProvider.get(), stockEntryRepositoryProvider.get(), warehouseRepositoryProvider.get(), paymentRepositoryProvider.get(), accountingRepositoryProvider.get());
  }

  public static InvoiceViewModel_Factory create(
      Provider<InvoiceRepository> invoiceRepositoryProvider,
      Provider<StockEntryRepository> stockEntryRepositoryProvider,
      Provider<WarehouseRepository> warehouseRepositoryProvider,
      Provider<PaymentRepository> paymentRepositoryProvider,
      Provider<AccountingRepository> accountingRepositoryProvider) {
    return new InvoiceViewModel_Factory(invoiceRepositoryProvider, stockEntryRepositoryProvider, warehouseRepositoryProvider, paymentRepositoryProvider, accountingRepositoryProvider);
  }

  public static InvoiceViewModel newInstance(InvoiceRepository invoiceRepository,
      StockEntryRepository stockEntryRepository, WarehouseRepository warehouseRepository,
      PaymentRepository paymentRepository, AccountingRepository accountingRepository) {
    return new InvoiceViewModel(invoiceRepository, stockEntryRepository, warehouseRepository, paymentRepository, accountingRepository);
  }
}
