package com.batterysales.viewmodel;

import com.batterysales.data.repositories.AccountingRepository;
import com.batterysales.data.repositories.BankRepository;
import com.batterysales.data.repositories.BillRepository;
import com.batterysales.data.repositories.StockEntryRepository;
import com.batterysales.data.repositories.SupplierRepository;
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
public final class BillViewModel_Factory implements Factory<BillViewModel> {
  private final Provider<BillRepository> repositoryProvider;

  private final Provider<SupplierRepository> supplierRepositoryProvider;

  private final Provider<StockEntryRepository> stockEntryRepositoryProvider;

  private final Provider<AccountingRepository> accountingRepositoryProvider;

  private final Provider<BankRepository> bankRepositoryProvider;

  private BillViewModel_Factory(Provider<BillRepository> repositoryProvider,
      Provider<SupplierRepository> supplierRepositoryProvider,
      Provider<StockEntryRepository> stockEntryRepositoryProvider,
      Provider<AccountingRepository> accountingRepositoryProvider,
      Provider<BankRepository> bankRepositoryProvider) {
    this.repositoryProvider = repositoryProvider;
    this.supplierRepositoryProvider = supplierRepositoryProvider;
    this.stockEntryRepositoryProvider = stockEntryRepositoryProvider;
    this.accountingRepositoryProvider = accountingRepositoryProvider;
    this.bankRepositoryProvider = bankRepositoryProvider;
  }

  @Override
  public BillViewModel get() {
    return newInstance(repositoryProvider.get(), supplierRepositoryProvider.get(), stockEntryRepositoryProvider.get(), accountingRepositoryProvider.get(), bankRepositoryProvider.get());
  }

  public static BillViewModel_Factory create(Provider<BillRepository> repositoryProvider,
      Provider<SupplierRepository> supplierRepositoryProvider,
      Provider<StockEntryRepository> stockEntryRepositoryProvider,
      Provider<AccountingRepository> accountingRepositoryProvider,
      Provider<BankRepository> bankRepositoryProvider) {
    return new BillViewModel_Factory(repositoryProvider, supplierRepositoryProvider, stockEntryRepositoryProvider, accountingRepositoryProvider, bankRepositoryProvider);
  }

  public static BillViewModel newInstance(BillRepository repository,
      SupplierRepository supplierRepository, StockEntryRepository stockEntryRepository,
      AccountingRepository accountingRepository, BankRepository bankRepository) {
    return new BillViewModel(repository, supplierRepository, stockEntryRepository, accountingRepository, bankRepository);
  }
}
