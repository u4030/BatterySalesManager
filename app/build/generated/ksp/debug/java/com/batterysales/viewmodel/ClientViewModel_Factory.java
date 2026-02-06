package com.batterysales.viewmodel;

import com.batterysales.data.repositories.ClientRepository;
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
public final class ClientViewModel_Factory implements Factory<ClientViewModel> {
  private final Provider<ClientRepository> clientRepositoryProvider;

  private ClientViewModel_Factory(Provider<ClientRepository> clientRepositoryProvider) {
    this.clientRepositoryProvider = clientRepositoryProvider;
  }

  @Override
  public ClientViewModel get() {
    return newInstance(clientRepositoryProvider.get());
  }

  public static ClientViewModel_Factory create(
      Provider<ClientRepository> clientRepositoryProvider) {
    return new ClientViewModel_Factory(clientRepositoryProvider);
  }

  public static ClientViewModel newInstance(ClientRepository clientRepository) {
    return new ClientViewModel(clientRepository);
  }
}
