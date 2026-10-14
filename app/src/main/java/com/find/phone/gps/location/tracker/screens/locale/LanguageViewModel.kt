package com.find.phone.gps.location.tracker.screens.locale

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.find.phone.gps.location.tracker.foundation.FoundationViewModel

class LanguageViewModel : FoundationViewModel() {

    val languages: List<LanguageEntry> = Locales.all

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

    fun imprintCountry(iso2: String?) {
        val normalized = iso2?.uppercase()
        if (normalized == appliedCountry) return
        appliedCountry = normalized

        suggestedAll = Locales.suggestedFor(normalized)
        val suggestedTags = suggestedAll.map { it.tag }.toSet()
        othersAll = languages.filterNot { it.tag in suggestedTags }
        publish()
    }

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

    var userPicked = false
        private set

    fun select(tag: String) {
        userPicked = true
        if (_selectedTag.value != tag) _selectedTag.value = tag
    }
}
