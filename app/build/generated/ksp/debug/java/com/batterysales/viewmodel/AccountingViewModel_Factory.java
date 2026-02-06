package com.batterysales.viewmodel;

import com.batterysales.data.repositories.AccountingRepository;
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
public final class AccountingViewModel_Factory implements Factory<AccountingViewModel> {
  private final Provider<AccountingRepository> repositoryProvider;

  private AccountingViewModel_Factory(Provider<AccountingRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public AccountingViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static AccountingViewModel_Factory create(
      Provider<AccountingRepository> repositoryProvider) {
    return new AccountingViewModel_Factory(repositoryProvider);
  }

  public static AccountingViewModel newInstance(AccountingRepository repository) {
    return new AccountingViewModel(repository);
  }
}
