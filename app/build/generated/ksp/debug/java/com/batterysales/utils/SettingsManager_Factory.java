package com.batterysales.utils;

import android.content.Context;
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
public final class SettingsManager_Factory implements Factory<SettingsManager> {
  private final Provider<Context> contextProvider;

  private SettingsManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public SettingsManager get() {
    return newInstance(contextProvider.get());
  }

  public static SettingsManager_Factory create(Provider<Context> contextProvider) {
    return new SettingsManager_Factory(contextProvider);
  }

  public static SettingsManager newInstance(Context context) {
    return new SettingsManager(context);
  }
}
