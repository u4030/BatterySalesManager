package com.batterysales.viewmodel;

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
public final class UserManagementViewModel_Factory implements Factory<UserManagementViewModel> {
  private final Provider<UserRepository> userRepositoryProvider;

  private final Provider<WarehouseRepository> warehouseRepositoryProvider;

  private UserManagementViewModel_Factory(Provider<UserRepository> userRepositoryProvider,
      Provider<WarehouseRepository> warehouseRepositoryProvider) {
    this.userRepositoryProvider = userRepositoryProvider;
    this.warehouseRepositoryProvider = warehouseRepositoryProvider;
  }

  @Override
  public UserManagementViewModel get() {
    return newInstance(userRepositoryProvider.get(), warehouseRepositoryProvider.get());
  }

  public static UserManagementViewModel_Factory create(
      Provider<UserRepository> userRepositoryProvider,
      Provider<WarehouseRepository> warehouseRepositoryProvider) {
    return new UserManagementViewModel_Factory(userRepositoryProvider, warehouseRepositoryProvider);
  }

  public static UserManagementViewModel newInstance(UserRepository userRepository,
      WarehouseRepository warehouseRepository) {
    return new UserManagementViewModel(userRepository, warehouseRepository);
  }
}
