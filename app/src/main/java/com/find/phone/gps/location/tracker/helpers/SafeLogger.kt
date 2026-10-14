package com.find.phone.gps.location.tracker.helpers

import android.util.Log
import com.find.phone.gps.location.tracker.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics

object SafeLogger {

    private const val TAG = "SafeLogger"

    fun log(tag: String = TAG, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    fun error(tag: String = TAG, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.e(tag, message, throwable)
            return
        }

        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.log("$tag: $message")
            if (throwable != null) crashlytics.recordException(throwable)
        }
    }

    inline fun runDebug(block: () -> Unit) {
        if (BuildConfig.DEBUG) block()
    }
}
