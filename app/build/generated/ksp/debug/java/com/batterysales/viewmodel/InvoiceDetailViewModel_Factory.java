package com.batterysales.viewmodel;

import androidx.lifecycle.SavedStateHandle;
import com.batterysales.data.repositories.AccountingRepository;
import com.batterysales.data.repositories.InvoiceRepository;
import com.batterysales.data.repositories.PaymentRepository;
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
public final class InvoiceDetailViewModel_Factory implements Factory<InvoiceDetailViewModel> {
  private final Provider<InvoiceRepository> invoiceRepositoryProvider;

  private final Provider<PaymentRepository> paymentRepositoryProvider;

  private final Provider<AccountingRepository> accountingRepositoryProvider;

  private final Provider<SavedStateHandle> savedStateHandleProvider;

  private InvoiceDetailViewModel_Factory(Provider<InvoiceRepository> invoiceRepositoryProvider,
      Provider<PaymentRepository> paymentRepositoryProvider,
      Provider<AccountingRepository> accountingRepositoryProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    this.invoiceRepositoryProvider = invoiceRepositoryProvider;
    this.paymentRepositoryProvider = paymentRepositoryProvider;
    this.accountingRepositoryProvider = accountingRepositoryProvider;
    this.savedStateHandleProvider = savedStateHandleProvider;
  }

  @Override
  public InvoiceDetailViewModel get() {
    return newInstance(invoiceRepositoryProvider.get(), paymentRepositoryProvider.get(), accountingRepositoryProvider.get(), savedStateHandleProvider.get());
  }

  public static InvoiceDetailViewModel_Factory create(
      Provider<InvoiceRepository> invoiceRepositoryProvider,
      Provider<PaymentRepository> paymentRepositoryProvider,
      Provider<AccountingRepository> accountingRepositoryProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    return new InvoiceDetailViewModel_Factory(invoiceRepositoryProvider, paymentRepositoryProvider, accountingRepositoryProvider, savedStateHandleProvider);
  }

  public static InvoiceDetailViewModel newInstance(InvoiceRepository invoiceRepository,
      PaymentRepository paymentRepository, AccountingRepository accountingRepository,
      SavedStateHandle savedStateHandle) {
    return new InvoiceDetailViewModel(invoiceRepository, paymentRepository, accountingRepository, savedStateHandle);
  }
}
