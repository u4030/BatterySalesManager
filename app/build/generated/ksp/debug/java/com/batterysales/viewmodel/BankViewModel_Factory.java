package com.batterysales.viewmodel;

import com.batterysales.data.repositories.BankRepository;
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
public final class BankViewModel_Factory implements Factory<BankViewModel> {
  private final Provider<BankRepository> repositoryProvider;

  private BankViewModel_Factory(Provider<BankRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public BankViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static BankViewModel_Factory create(Provider<BankRepository> repositoryProvider) {
    return new BankViewModel_Factory(repositoryProvider);
  }

  public static BankViewModel newInstance(BankRepository repository) {
    return new BankViewModel(repository);
  }
}
