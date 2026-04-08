package com.daon.fido.sdk.sample.kt.di

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import com.daon.fido.sdk.sample.kt.service.rest.RestService
import com.daon.fido.sdk.sample.kt.service.rpsa.RPSAService
import com.daon.fido.sdk.sample.kt.settings.ServiceType
import com.daon.fido.sdk.sample.kt.settings.loadDevSettings
import com.daon.sdk.xauth.IXUAFService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * AppModule class which provides application level dependencies using Dagger Hilt. Includes methods
 * to provide instances of `IXUAF` and `SharedPreferences`.
 */
@Module
@InstallIn(SingletonComponent::class)
class AppModule {
    @Provides
    @Singleton
    fun provideService(
        @ApplicationContext appContext: Context,
        prefs: SharedPreferences,
    ): IXUAFService {
        val settings = loadDevSettings(appContext, prefs)

        return when (settings.serviceType) {
            ServiceType.RPSA -> {
                val rpsaParams = Bundle().apply { putString("server_url", settings.rpsaServerUrl) }
                RPSAService(appContext, rpsaParams)
            }
            ServiceType.REST -> {
                val restParams = Bundle()
                restParams.putString("server_url", settings.restServerUrl)
                restParams.putString("rest_path", settings.restPath)
                restParams.putString("appId", settings.restAppId)
                restParams.putString("regPolicy", settings.restRegPolicy)
                restParams.putString("authPolicy", settings.restAuthPolicy)
                // Basic Auth
                restParams.putString("username", settings.restUsername)
                restParams.putString("password", settings.restPassword)

                RestService(appContext, restParams)
            }
        }
    }

    // Provides an instance of `SharedPreferences` using the application context.
    @Provides
    @Singleton
    fun provideSharedPreference(@ApplicationContext appContext: Context): SharedPreferences {
        return appContext.getSharedPreferences("pref_sampleapp_kotlin", Context.MODE_PRIVATE)
    }
}
