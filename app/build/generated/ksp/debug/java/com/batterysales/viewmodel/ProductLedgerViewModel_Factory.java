package com.batterysales.viewmodel;

import androidx.lifecycle.SavedStateHandle;
import com.batterysales.data.repositories.StockEntryRepository;
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
public final class ProductLedgerViewModel_Factory implements Factory<ProductLedgerViewModel> {
  private final Provider<StockEntryRepository> stockEntryRepositoryProvider;

  private final Provider<UserRepository> userRepositoryProvider;

  private final Provider<WarehouseRepository> warehouseRepositoryProvider;

  private final Provider<SavedStateHandle> savedStateHandleProvider;

  private ProductLedgerViewModel_Factory(
      Provider<StockEntryRepository> stockEntryRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider,
      Provider<WarehouseRepository> warehouseRepositoryProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    this.stockEntryRepositoryProvider = stockEntryRepositoryProvider;
    this.userRepositoryProvider = userRepositoryProvider;
    this.warehouseRepositoryProvider = warehouseRepositoryProvider;
    this.savedStateHandleProvider = savedStateHandleProvider;
  }

  @Override
  public ProductLedgerViewModel get() {
    return newInstance(stockEntryRepositoryProvider.get(), userRepositoryProvider.get(), warehouseRepositoryProvider.get(), savedStateHandleProvider.get());
  }

  public static ProductLedgerViewModel_Factory create(
      Provider<StockEntryRepository> stockEntryRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider,
      Provider<WarehouseRepository> warehouseRepositoryProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    return new ProductLedgerViewModel_Factory(stockEntryRepositoryProvider, userRepositoryProvider, warehouseRepositoryProvider, savedStateHandleProvider);
  }

  public static ProductLedgerViewModel newInstance(StockEntryRepository stockEntryRepository,
      UserRepository userRepository, WarehouseRepository warehouseRepository,
      SavedStateHandle savedStateHandle) {
    return new ProductLedgerViewModel(stockEntryRepository, userRepository, warehouseRepository, savedStateHandle);
  }
}
