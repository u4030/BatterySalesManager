package com.batterysales.ui.stockentry;

import androidx.lifecycle.SavedStateHandle;
import com.batterysales.data.repositories.ProductRepository;
import com.batterysales.data.repositories.ProductVariantRepository;
import com.batterysales.data.repositories.StockEntryRepository;
import com.batterysales.data.repositories.SupplierRepository;
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
public final class StockEntryViewModel_Factory implements Factory<StockEntryViewModel> {
  private final Provider<ProductRepository> productRepositoryProvider;

  private final Provider<ProductVariantRepository> productVariantRepositoryProvider;

  private final Provider<WarehouseRepository> warehouseRepositoryProvider;

  private final Provider<StockEntryRepository> stockEntryRepositoryProvider;

  private final Provider<SupplierRepository> supplierRepositoryProvider;

  private final Provider<UserRepository> userRepositoryProvider;

  private final Provider<SavedStateHandle> savedStateHandleProvider;

  private StockEntryViewModel_Factory(Provider<ProductRepository> productRepositoryProvider,
      Provider<ProductVariantRepository> productVariantRepositoryProvider,
      Provider<WarehouseRepository> warehouseRepositoryProvider,
      Provider<StockEntryRepository> stockEntryRepositoryProvider,
      Provider<SupplierRepository> supplierRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    this.productRepositoryProvider = productRepositoryProvider;
    this.productVariantRepositoryProvider = productVariantRepositoryProvider;
    this.warehouseRepositoryProvider = warehouseRepositoryProvider;
    this.stockEntryRepositoryProvider = stockEntryRepositoryProvider;
    this.supplierRepositoryProvider = supplierRepositoryProvider;
    this.userRepositoryProvider = userRepositoryProvider;
    this.savedStateHandleProvider = savedStateHandleProvider;
  }

  @Override
  public StockEntryViewModel get() {
    return newInstance(productRepositoryProvider.get(), productVariantRepositoryProvider.get(), warehouseRepositoryProvider.get(), stockEntryRepositoryProvider.get(), supplierRepositoryProvider.get(), userRepositoryProvider.get(), savedStateHandleProvider.get());
  }

  public static StockEntryViewModel_Factory create(
      Provider<ProductRepository> productRepositoryProvider,
      Provider<ProductVariantRepository> productVariantRepositoryProvider,
      Provider<WarehouseRepository> warehouseRepositoryProvider,
      Provider<StockEntryRepository> stockEntryRepositoryProvider,
      Provider<SupplierRepository> supplierRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    return new StockEntryViewModel_Factory(productRepositoryProvider, productVariantRepositoryProvider, warehouseRepositoryProvider, stockEntryRepositoryProvider, supplierRepositoryProvider, userRepositoryProvider, savedStateHandleProvider);
  }

  public static StockEntryViewModel newInstance(ProductRepository productRepository,
      ProductVariantRepository productVariantRepository, WarehouseRepository warehouseRepository,
      StockEntryRepository stockEntryRepository, SupplierRepository supplierRepository,
      UserRepository userRepository, SavedStateHandle savedStateHandle) {
    return new StockEntryViewModel(productRepository, productVariantRepository, warehouseRepository, stockEntryRepository, supplierRepository, userRepository, savedStateHandle);
  }
}
