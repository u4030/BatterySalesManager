package com.batterysales.viewmodel;

import com.batterysales.data.repositories.AccountingRepository;
import com.batterysales.data.repositories.InvoiceRepository;
import com.batterysales.data.repositories.OldBatteryRepository;
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
public final class OldBatteryViewModel_Factory implements Factory<OldBatteryViewModel> {
  private final Provider<OldBatteryRepository> repositoryProvider;

  private final Provider<AccountingRepository> accountingRepositoryProvider;

  private final Provider<InvoiceRepository> invoiceRepositoryProvider;

  private OldBatteryViewModel_Factory(Provider<OldBatteryRepository> repositoryProvider,
      Provider<AccountingRepository> accountingRepositoryProvider,
      Provider<InvoiceRepository> invoiceRepositoryProvider) {
    this.repositoryProvider = repositoryProvider;
    this.accountingRepositoryProvider = accountingRepositoryProvider;
    this.invoiceRepositoryProvider = invoiceRepositoryProvider;
  }

  @Override
  public OldBatteryViewModel get() {
    return newInstance(repositoryProvider.get(), accountingRepositoryProvider.get(), invoiceRepositoryProvider.get());
  }

  public static OldBatteryViewModel_Factory create(
      Provider<OldBatteryRepository> repositoryProvider,
      Provider<AccountingRepository> accountingRepositoryProvider,
      Provider<InvoiceRepository> invoiceRepositoryProvider) {
    return new OldBatteryViewModel_Factory(repositoryProvider, accountingRepositoryProvider, invoiceRepositoryProvider);
  }

  public static OldBatteryViewModel newInstance(OldBatteryRepository repository,
      AccountingRepository accountingRepository, InvoiceRepository invoiceRepository) {
    return new OldBatteryViewModel(repository, accountingRepository, invoiceRepository);
  }
}
