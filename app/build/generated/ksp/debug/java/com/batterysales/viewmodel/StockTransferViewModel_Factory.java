package com.batterysales.viewmodel;

import com.batterysales.data.repositories.ProductRepository;
import com.batterysales.data.repositories.ProductVariantRepository;
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
public final class StockTransferViewModel_Factory implements Factory<StockTransferViewModel> {
  private final Provider<ProductRepository> productRepositoryProvider;

  private final Provider<ProductVariantRepository> productVariantRepositoryProvider;

  private final Provider<WarehouseRepository> warehouseRepositoryProvider;

  private final Provider<StockEntryRepository> stockEntryRepositoryProvider;

  private final Provider<UserRepository> userRepositoryProvider;

  private StockTransferViewModel_Factory(Provider<ProductRepository> productRepositoryProvider,
      Provider<ProductVariantRepository> productVariantRepositoryProvider,
      Provider<WarehouseRepository> warehouseRepositoryProvider,
      Provider<StockEntryRepository> stockEntryRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider) {
    this.productRepositoryProvider = productRepositoryProvider;
    this.productVariantRepositoryProvider = productVariantRepositoryProvider;
    this.warehouseRepositoryProvider = warehouseRepositoryProvider;
    this.stockEntryRepositoryProvider = stockEntryRepositoryProvider;
    this.userRepositoryProvider = userRepositoryProvider;
  }

  @Override
  public StockTransferViewModel get() {
    return newInstance(productRepositoryProvider.get(), productVariantRepositoryProvider.get(), warehouseRepositoryProvider.get(), stockEntryRepositoryProvider.get(), userRepositoryProvider.get());
  }

  public static StockTransferViewModel_Factory create(
      Provider<ProductRepository> productRepositoryProvider,
      Provider<ProductVariantRepository> productVariantRepositoryProvider,
      Provider<WarehouseRepository> warehouseRepositoryProvider,
      Provider<StockEntryRepository> stockEntryRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider) {
    return new StockTransferViewModel_Factory(productRepositoryProvider, productVariantRepositoryProvider, warehouseRepositoryProvider, stockEntryRepositoryProvider, userRepositoryProvider);
  }

  public static StockTransferViewModel newInstance(ProductRepository productRepository,
      ProductVariantRepository productVariantRepository, WarehouseRepository warehouseRepository,
      StockEntryRepository stockEntryRepository, UserRepository userRepository) {
    return new StockTransferViewModel(productRepository, productVariantRepository, warehouseRepository, stockEntryRepository, userRepository);
  }
}
