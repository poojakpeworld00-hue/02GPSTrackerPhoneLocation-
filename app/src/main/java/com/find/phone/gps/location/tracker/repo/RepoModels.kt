package com.find.phone.gps.location.tracker.repo

enum class CallKind { INCOMING, OUTGOING, MISSED, SPAM }

data class CallTileData(
    val name: String,
    val time: String,
    val info: String,
    val initials: String,
    val type: CallKind,
    val number: String = "",

    val identified: Boolean = true
)

data class PersonRow(
    val name: String,
    val detail: String,
    val initials: String,
    val photoUri: String? = null,
    val starred: Boolean = false,
    val lastContacted: Long = 0L,
    val inGroup: Boolean = false
)

object SampleFixtures {

    val recents: List<CallTileData> = listOf(
        CallTileData("Sarah Khan", "9:24", "Incoming · 4m 12s", "SK", CallKind.INCOMING),
        CallTileData("+1 (800) 244-0199", "8:50", "Spam · Telemarketer", "!", CallKind.SPAM),
        CallTileData("Dad Mobile", "7:32", "Missed call", "DM", CallKind.MISSED),
        CallTileData("+44 20 7946 0321", "Tue", "Outgoing · London, UK", "+9", CallKind.OUTGOING),
        CallTileData("Aisha Lawson", "Tue", "Incoming · 1m 03s", "AL", CallKind.INCOMING)
    )

    val homeRecent: List<CallTileData> = recents.take(2)

    val contacts: List<PersonRow> = listOf(
        PersonRow("Aisha Lawson", "+1 (415) 555-0178", "AL"),
        PersonRow("Amir Raza", "Acme Corp", "AR"),
        PersonRow("Dad Mobile", "+1 (415) 555-0143", "DM"),
        PersonRow("Sarah Khan", "Brightline Bank", "SK")
    )
}
