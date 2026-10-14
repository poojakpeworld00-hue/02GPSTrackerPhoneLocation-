package io.homedeck.grid.screens

import android.content.Context
import android.os.Bundle
import org.fossify.commons.activities.BaseSimpleActivity
import io.homedeck.grid.R
import io.homedeck.grid.bridge.HomeServiceLocator
import io.homedeck.grid.support.REPOSITORY_NAME

open class HomeFoundationPane : BaseSimpleActivity() {

    private var spoofFossifyPackageName = false

    private companion object {
        val FOSSIFY_SPOOF: String = listOf("org", "fossify", "home").joinToString(".")
    }

    protected fun <T> withFossifyPackageNameSpoofed(block: () -> T): T {
        spoofFossifyPackageName = true
        try {
            return block()
        } finally {
            spoofFossifyPackageName = false
        }
    }

    private var builtLocale: String? = null

    override fun attachBaseContext(newBase: Context) {
        HomeServiceLocator.ensure(newBase)
        val localized = HomeServiceLocator.bridge.localizeContext(newBase)
        builtLocale = localized.resources.configuration.locales[0]?.toString()
        super.attachBaseContext(localized)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        withFossifyPackageNameSpoofed { super.onCreate(savedInstanceState) }
    }

    override fun onResume() {
        super.onResume()

        val now = HomeServiceLocator.bridge.localizeContext(applicationContext)
            .resources.configuration.locales[0]?.toString()
        if (now != builtLocale) recreate()
    }

    override fun getPackageName(): String {

        return if (spoofFossifyPackageName) FOSSIFY_SPOOF else super.getPackageName()
    }

    override fun getAppIconIDs() = ArrayList<Int>()

    override fun getAppLauncherName() = getString(R.string.launcher_app_launcher_name)

    override fun getRepositoryName() = REPOSITORY_NAME
}
