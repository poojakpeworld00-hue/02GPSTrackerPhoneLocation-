package com.find.phone.gps.location.tracker.screens.locale

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.find.phone.gps.location.tracker.foundation.FoundationViewModel

class LanguageViewModel : FoundationViewModel() {

    val languages: List<LanguageEntry> = Locales.all

    // The "Suggested" group is chosen by the user's region (GEO); "All languages" holds the
    // rest. Both are filtered by the search query. LiveData because the region can arrive
    // asynchronously (IP lookup), refining the guess made from the device locale.
    private val _suggested = MutableLiveData<List<LanguageEntry>>()
    val suggested: LiveData<List<LanguageEntry>> = _suggested

    private val _others = MutableLiveData<List<LanguageEntry>>()
    val others: LiveData<List<LanguageEntry>> = _others

    private val _selectedTag = MutableLiveData(Locales.all.first().tag)
    val selectedTag: LiveData<String> = _selectedTag

    private var appliedCountry: String? = "__none__"
    private var query: String = ""
    private var suggestedAll: List<LanguageEntry> = emptyList()
    private var othersAll: List<LanguageEntry> = emptyList()

    fun init(currentTag: String) {
        if (languages.any { it.tag == currentTag }) {
            _selectedTag.value = currentTag
        }
    }

    /** Rebuilds the Suggested / All groups for the given country (ISO-3166 alpha-2, null = unknown). */
    fun imprintCountry(iso2: String?) {
        val normalized = iso2?.uppercase()
        if (normalized == appliedCountry) return
        appliedCountry = normalized

        suggestedAll = Locales.suggestedFor(normalized)
        val suggestedTags = suggestedAll.map { it.tag }.toSet()
        othersAll = languages.filterNot { it.tag in suggestedTags }
        publish()
    }

    /** The search field: matches the English name, the endonym or the code, case-insensitively. */
    fun setQuery(text: String) {
        val q = text.trim()
        if (q == query) return
        query = q
        publish()
    }

    private fun publish() {
        _suggested.value = suggestedAll.filter(::matches)
        _others.value = othersAll.filter(::matches)
    }

    private fun matches(item: LanguageEntry): Boolean =
        query.isEmpty() ||
            item.name.contains(query, ignoreCase = true) ||
            item.nativeName.contains(query, ignoreCase = true) ||
            item.code.contains(query, ignoreCase = true)

    /** True once the user tapped a language (vs. continuing with the preselected one). */
    var userPicked = false
        private set

    fun select(tag: String) {
        userPicked = true
        if (_selectedTag.value != tag) _selectedTag.value = tag
    }
}
