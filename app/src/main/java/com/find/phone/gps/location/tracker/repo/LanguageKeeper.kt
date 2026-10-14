package com.find.phone.gps.location.tracker.repo

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.find.phone.gps.location.tracker.BuildConfig
import com.find.phone.gps.location.tracker.helpers.AppPrefsStore

object LanguageKeeper {

    private const val TAG = "LanguageKeeper"

    fun apply(languageTag: String) {
        try {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(languageTag)
            )
        } catch (e: IllegalStateException) {
            if (BuildConfig.DEBUG) Log.w(TAG, "locale '$languageTag' refused: ${e.message}")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "locale '$languageTag' failed: ${e.message}")
        }
    }

    fun imprintSaved(context: Context) {
        val savedLang = AppPrefsStore.selectedLanguage(context)
        if (savedLang.isEmpty()) return

        val normalizedLang = if (savedLang == "in") "id" else savedLang
        val current = context.resources.configuration.locales[0].language
        if (normalizedLang == current) return

        apply(normalizedLang)
    }

}
