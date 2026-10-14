package com.find.phone.gps.location.tracker.grants

class GrantBacklog(rules: List<GrantPolicy>) {

    private val items: ArrayDeque<GrantPolicy> =
        ArrayDeque(rules.sortedBy { it.priority })

    fun poll(): GrantPolicy? = items.removeFirstOrNull()

    fun isEmpty(): Boolean = items.isEmpty()

    fun size(): Int = items.size
}
