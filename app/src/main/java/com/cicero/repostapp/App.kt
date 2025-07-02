package com.cicero.repostapp

import android.app.Application
import com.facebook.FacebookSdk

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        FacebookSdk.sdkInitialize(applicationContext)
    }
}
