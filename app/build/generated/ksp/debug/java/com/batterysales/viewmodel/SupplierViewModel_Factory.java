package com.batterysales.viewmodel;

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
public final class SupplierViewModel_Factory implements Factory<SupplierViewModel> {
  private final Provider<SupplierRepository> supplierRepositoryProvider;

  private SupplierViewModel_Factory(Provider<SupplierRepository> supplierRepositoryProvider) {
    this.supplierRepositoryProvider = supplierRepositoryProvider;
  }

  @Override
  public SupplierViewModel get() {
    return newInstance(supplierRepositoryProvider.get());
  }

  public static SupplierViewModel_Factory create(
      Provider<SupplierRepository> supplierRepositoryProvider) {
    return new SupplierViewModel_Factory(supplierRepositoryProvider);
  }

  public static SupplierViewModel newInstance(SupplierRepository supplierRepository) {
    return new SupplierViewModel(supplierRepository);
  }
}
