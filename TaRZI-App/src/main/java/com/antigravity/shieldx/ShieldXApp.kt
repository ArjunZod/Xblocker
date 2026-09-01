package com.antigravity.shieldx

import android.app.Application
import com.antigravity.shieldx.core.security.SecurityManager

class ShieldXApp : Application() {

    lateinit var securityManager: SecurityManager
        private set

    override fun onCreate() {
        super.onCreate()
        securityManager = SecurityManager.getInstance(this)
    }
}
