package com.find.phone.gps.location.tracker.repo

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.find.phone.gps.location.tracker.BuildConfig
import com.find.phone.gps.location.tracker.helpers.AppPrefsStore
import java.util.Locale

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

    fun localized(base: Context): Context {
        val tag = savedTag(base) ?: return base
        val locale = Locale.forLanguageTag(tag)
        val current = base.resources.configuration.locales[0]
        if (current != null && current.language == locale.language) return base
        val config = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }

    private fun savedTag(context: Context): String? =
        AppPrefsStore.selectedLanguage(context).takeIf { it.isNotEmpty() }?.let { if (it == "in") "id" else it }

    fun imprintSaved(context: Context) {
        val savedLang = AppPrefsStore.selectedLanguage(context)
        if (savedLang.isEmpty()) return

        val normalizedLang = if (savedLang == "in") "id" else savedLang
        val current = context.resources.configuration.locales[0].language
        if (normalizedLang == current) return

        apply(normalizedLang)
    }

}
