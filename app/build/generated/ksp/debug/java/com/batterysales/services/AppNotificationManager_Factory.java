package com.batterysales.services;

import android.content.Context;
import com.batterysales.data.repositories.ProductRepository;
import com.batterysales.data.repositories.ProductVariantRepository;
import com.batterysales.data.repositories.StockEntryRepository;
import com.batterysales.data.repositories.UserRepository;
import com.batterysales.data.repositories.WarehouseRepository;
import com.google.firebase.firestore.FirebaseFirestore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class AppNotificationManager_Factory implements Factory<AppNotificationManager> {
  private final Provider<Context> contextProvider;

  private final Provider<StockEntryRepository> stockEntryRepositoryProvider;

  private final Provider<ProductVariantRepository> productVariantRepositoryProvider;

  private final Provider<ProductRepository> productRepositoryProvider;

  private final Provider<UserRepository> userRepositoryProvider;

  private final Provider<WarehouseRepository> warehouseRepositoryProvider;

  private final Provider<FirebaseFirestore> firestoreProvider;

  private AppNotificationManager_Factory(Provider<Context> contextProvider,
      Provider<StockEntryRepository> stockEntryRepositoryProvider,
      Provider<ProductVariantRepository> productVariantRepositoryProvider,
      Provider<ProductRepository> productRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider,
      Provider<WarehouseRepository> warehouseRepositoryProvider,
      Provider<FirebaseFirestore> firestoreProvider) {
    this.contextProvider = contextProvider;
    this.stockEntryRepositoryProvider = stockEntryRepositoryProvider;
    this.productVariantRepositoryProvider = productVariantRepositoryProvider;
    this.productRepositoryProvider = productRepositoryProvider;
    this.userRepositoryProvider = userRepositoryProvider;
    this.warehouseRepositoryProvider = warehouseRepositoryProvider;
    this.firestoreProvider = firestoreProvider;
  }

  @Override
  public AppNotificationManager get() {
    return newInstance(contextProvider.get(), stockEntryRepositoryProvider.get(), productVariantRepositoryProvider.get(), productRepositoryProvider.get(), userRepositoryProvider.get(), warehouseRepositoryProvider.get(), firestoreProvider.get());
  }

  public static AppNotificationManager_Factory create(Provider<Context> contextProvider,
      Provider<StockEntryRepository> stockEntryRepositoryProvider,
      Provider<ProductVariantRepository> productVariantRepositoryProvider,
      Provider<ProductRepository> productRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider,
      Provider<WarehouseRepository> warehouseRepositoryProvider,
      Provider<FirebaseFirestore> firestoreProvider) {
    return new AppNotificationManager_Factory(contextProvider, stockEntryRepositoryProvider, productVariantRepositoryProvider, productRepositoryProvider, userRepositoryProvider, warehouseRepositoryProvider, firestoreProvider);
  }

  public static AppNotificationManager newInstance(Context context,
      StockEntryRepository stockEntryRepository, ProductVariantRepository productVariantRepository,
      ProductRepository productRepository, UserRepository userRepository,
      WarehouseRepository warehouseRepository, FirebaseFirestore firestore) {
    return new AppNotificationManager(context, stockEntryRepository, productVariantRepository, productRepository, userRepository, warehouseRepository, firestore);
  }
}
