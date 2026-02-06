package com.batterysales;

import com.batterysales.services.AppNotificationManager;
import com.batterysales.utils.SettingsManager;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

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
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<AppNotificationManager> notificationManagerProvider;

  private final Provider<SettingsManager> settingsManagerProvider;

  private MainActivity_MembersInjector(Provider<AppNotificationManager> notificationManagerProvider,
      Provider<SettingsManager> settingsManagerProvider) {
    this.notificationManagerProvider = notificationManagerProvider;
    this.settingsManagerProvider = settingsManagerProvider;
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectNotificationManager(instance, notificationManagerProvider.get());
    injectSettingsManager(instance, settingsManagerProvider.get());
  }

  public static MembersInjector<MainActivity> create(
      Provider<AppNotificationManager> notificationManagerProvider,
      Provider<SettingsManager> settingsManagerProvider) {
    return new MainActivity_MembersInjector(notificationManagerProvider, settingsManagerProvider);
  }

  @InjectedFieldSignature("com.batterysales.MainActivity.notificationManager")
  public static void injectNotificationManager(MainActivity instance,
      AppNotificationManager notificationManager) {
    instance.notificationManager = notificationManager;
  }

  @InjectedFieldSignature("com.batterysales.MainActivity.settingsManager")
  public static void injectSettingsManager(MainActivity instance, SettingsManager settingsManager) {
    instance.settingsManager = settingsManager;
  }
}
