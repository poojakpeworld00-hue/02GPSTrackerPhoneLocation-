package io.homedeck.grid.support

import io.homedeck.grid.entities.LaunchableApp

object GlyphMemo {
    @Volatile
    private var cachedLaunchers = emptyList<LaunchableApp>()

    var launchers: List<LaunchableApp>
        get() = cachedLaunchers
        set(value) {
            synchronized(this) {
                cachedLaunchers = value
            }
        }

    @Volatile
    var installedSignature: String? = null

    @Volatile
    var sourceDirs: Map<String, String> = emptyMap()

    fun clear() {
        launchers = emptyList()
        installedSignature = null
        sourceDirs = emptyMap()

        GlyphFetcher.clear()
    }
}