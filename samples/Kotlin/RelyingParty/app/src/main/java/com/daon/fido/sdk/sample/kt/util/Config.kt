package com.daon.fido.sdk.sample.kt.util

import android.content.Context
import java.io.IOException
import java.util.Properties
import kotlin.jvm.Throws

object Config {
    const val SERVICE_TYPE = "serviceType"
    const val RPSA_SERVER_URL = "rpsaServerUrl"
    const val REST_SERVER_URL = "restServerUrl"
    const val REST_PATH = "restPath"
    const val REST_APP_ID = "restAppId"
    const val REST_REG_POLICY = "restRegPolicy"
    const val REST_AUTH_POLICY = "restAuthPolicy"
    const val REST_USERNAME = "restUsername"
    const val REST_PASSWORD = "restPassword"

    @Throws(IOException::class)
    fun getProperty(key: String, context: Context): String? {
        val properties = Properties()
        context.assets.open("config.properties").use { inputStream -> properties.load(inputStream) }
        return properties.getProperty(key)
    }
}
