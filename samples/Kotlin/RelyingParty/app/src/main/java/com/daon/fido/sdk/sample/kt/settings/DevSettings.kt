package com.daon.fido.sdk.sample.kt.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.daon.fido.sdk.sample.kt.BuildConfig
import com.daon.fido.sdk.sample.kt.util.Config
import com.daon.sdk.xauth.uaf.UafMessageUtils

/** Service type for FIDO operations. */
enum class ServiceType {
    RPSA,
    REST,
}

/** Developer settings for the sample app. */
data class DevSettings(
    // Read-only (not persisted)
    val facetId: String = "",
    val sdkVersion: String = "",
    val appVersion: String = "",

    // Server settings (saved on explicit "Save" tap)
    val serviceType: ServiceType = ServiceType.RPSA,
    val rpsaServerUrl: String = "",
    val restServerUrl: String = "",
    val restPath: String = "",
    val restAppId: String = "",
    val restRegPolicy: String = "",
    val restAuthPolicy: String = "",
    val restUsername: String = "",
    val restPassword: String = "",

    // Immediate settings (saved on toggle/change)
    val ootpMode: String = "IdentifyWithOTP",
    val injectionDetection: Boolean = true,
    val fingerprintSilentRegistration: Boolean = false,
    val confirmationOTP: Boolean = false,
)

// ── SharedPreferences keys ──────────────────────────────────────────────────
object DevSettingsKeys {
    const val SERVICE_TYPE = "serviceType"
    const val RPSA_SERVER_URL = "rpsaServerUrl"
    const val REST_SERVER_URL = "restServerUrl"
    const val REST_PATH = "restPath"
    const val REST_APP_ID = "restAppId"
    const val REST_REG_POLICY = "restRegPolicy"
    const val REST_AUTH_POLICY = "restAuthPolicy"
    const val REST_USERNAME = "restUsername"
    const val REST_PASSWORD = "restPassword"
    const val OOTP_MODE = "ootpMode"
    const val INJECTION_DETECTION = "injectionDetectionEnabled" // reuse existing key
    const val FINGERPRINT_SILENT_REGISTRATION = "fingerprintSilentRegistration"
    const val CONFIRMATION_OTP = "confirmationOTP"
}

/** Returns true if the user has ever explicitly saved server settings via "Save & Restart". */
fun hasDevServerOverride(prefs: SharedPreferences): Boolean =
    prefs.contains(DevSettingsKeys.SERVICE_TYPE)

/**
 * Load dev settings. Resolution order: SharedPreferences → config.properties → hardcoded fallback.
 */
fun loadDevSettings(context: Context, prefs: SharedPreferences): DevSettings {
    // Read-only fields
    val facetId =
        try {
            UafMessageUtils.getFacetId(context)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    val sdkVersion = "1.0"
    val appVersion = BuildConfig.VERSION_NAME

    // For each editable field: SharedPreferences → config.properties → empty/default
    val serviceType =
        try {
            ServiceType.valueOf(
                prefs.getString(DevSettingsKeys.SERVICE_TYPE, null)
                    ?: Config.getProperty(Config.SERVICE_TYPE, context)
                    ?: ServiceType.RPSA.name
            )
        } catch (e: IllegalArgumentException) {
            ServiceType.RPSA
        }

    val rpsaServerUrl =
        prefs.getString(DevSettingsKeys.RPSA_SERVER_URL, null)
            ?: Config.getProperty(Config.RPSA_SERVER_URL, context)
            ?: ""

    val restServerUrl =
        prefs.getString(DevSettingsKeys.REST_SERVER_URL, null)
            ?: Config.getProperty(Config.REST_SERVER_URL, context)
            ?: ""

    val restPath =
        prefs.getString(DevSettingsKeys.REST_PATH, null)
            ?: Config.getProperty(Config.REST_PATH, context)
            ?: ""

    val restAppId =
        prefs.getString(DevSettingsKeys.REST_APP_ID, null)
            ?: Config.getProperty(Config.REST_APP_ID, context)
            ?: ""

    val restRegPolicy =
        prefs.getString(DevSettingsKeys.REST_REG_POLICY, null)
            ?: Config.getProperty(Config.REST_REG_POLICY, context)
            ?: ""

    val restAuthPolicy =
        prefs.getString(DevSettingsKeys.REST_AUTH_POLICY, null)
            ?: Config.getProperty(Config.REST_AUTH_POLICY, context)
            ?: ""

    val restUsername =
        prefs.getString(DevSettingsKeys.REST_USERNAME, null)
            ?: Config.getProperty(Config.REST_USERNAME, context)
            ?: ""

    val restPassword =
        prefs.getString(DevSettingsKeys.REST_PASSWORD, null)
            ?: Config.getProperty(Config.REST_PASSWORD, context)
            ?: ""

    val ootpMode =
        prefs.getString(DevSettingsKeys.OOTP_MODE, "IdentifyWithOTP") ?: "IdentifyWithOTP"
    val injectionDetection = prefs.getBoolean(DevSettingsKeys.INJECTION_DETECTION, true)
    val fingerprintSilentRegistration =
        prefs.getBoolean(DevSettingsKeys.FINGERPRINT_SILENT_REGISTRATION, false)
    val confirmationOTP = prefs.getBoolean(DevSettingsKeys.CONFIRMATION_OTP, false)

    return DevSettings(
        facetId = facetId,
        sdkVersion = sdkVersion,
        appVersion = appVersion,
        serviceType = serviceType,
        rpsaServerUrl = rpsaServerUrl,
        restServerUrl = restServerUrl,
        restPath = restPath,
        restAppId = restAppId,
        restRegPolicy = restRegPolicy,
        restAuthPolicy = restAuthPolicy,
        restUsername = restUsername,
        restPassword = restPassword,
        ootpMode = ootpMode,
        injectionDetection = injectionDetection,
        fingerprintSilentRegistration = fingerprintSilentRegistration,
        confirmationOTP = confirmationOTP,
    )
}

/** Batch-save server settings. Called when user taps "Save & Restart". */
fun saveServerSettings(prefs: SharedPreferences, settings: DevSettings) {
    prefs.edit(commit = true) {
        putString(DevSettingsKeys.SERVICE_TYPE, settings.serviceType.name)
        putString(DevSettingsKeys.RPSA_SERVER_URL, settings.rpsaServerUrl)
        putString(DevSettingsKeys.REST_SERVER_URL, settings.restServerUrl)
        putString(DevSettingsKeys.REST_PATH, settings.restPath)
        putString(DevSettingsKeys.REST_APP_ID, settings.restAppId)
        putString(DevSettingsKeys.REST_REG_POLICY, settings.restRegPolicy)
        putString(DevSettingsKeys.REST_AUTH_POLICY, settings.restAuthPolicy)
        putString(DevSettingsKeys.REST_USERNAME, settings.restUsername)
        putString(DevSettingsKeys.REST_PASSWORD, settings.restPassword)
    }
}

/** Save a single immediate setting. Called on toggle/change for OOTP, biometrics, etc. */
fun saveImmediateSetting(prefs: SharedPreferences, key: String, value: Any) {
    prefs.edit {
        when (value) {
            is Boolean -> putBoolean(key, value)
            is String -> putString(key, value)
            is Int -> putInt(key, value)
            else -> error("Unsupported preference type: ${value::class}")
        }
    }
}
