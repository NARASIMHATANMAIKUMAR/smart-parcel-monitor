package com.smartparcel.gateway

import android.app.Application
import com.smartparcel.gateway.engine.GatewayEngine

class SmartParcelApplication : Application() {

    lateinit var gatewayEngine: GatewayEngine
        private set

    override fun onCreate() {
        super.onCreate()
        gatewayEngine = GatewayEngine.getInstance(this)
    }
}

