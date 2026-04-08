package com.daon.fido.sdk.sample.kt.util

import android.app.Application
import androidx.lifecycle.AndroidViewModel

/** Extension property to get application context from AndroidViewModel */
val AndroidViewModel.context
    get() = getApplication<Application>()

/** Extension function to get string resources from AndroidViewModel */
fun AndroidViewModel.getResourceString(id: Int) = context.resources.getString(id)
