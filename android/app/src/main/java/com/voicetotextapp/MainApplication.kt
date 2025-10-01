package com.voicetotextapp

import android.app.Application
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultReactNativeHost
import com.voicetotextapp.SpeechPackage
import com.voicetotextapp.SmsPackage

class MainApplication : Application(), ReactApplication {

    override val reactNativeHost =
        object : DefaultReactNativeHost(this) {
            override fun getPackages(): List<ReactPackage> {
                val packages = PackageList(this).packages.toMutableList()
                packages.add(SpeechPackage())
                packages.add(SmsPackage())
                return packages
            }

            override fun getJSMainModuleName(): String = "index"
            override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG
            override val isNewArchEnabled: Boolean = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
            override val isHermesEnabled: Boolean = BuildConfig.IS_HERMES_ENABLED
        }

    override val reactHost: ReactHost
        get() = com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost(
            applicationContext,
            reactNativeHost
        )

    override fun onCreate() {
        super.onCreate()
        // ✅ Lưu ReactApplication vào AppContextHolder
        AppContextHolder.app = this
        com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative(this)
    }
}
