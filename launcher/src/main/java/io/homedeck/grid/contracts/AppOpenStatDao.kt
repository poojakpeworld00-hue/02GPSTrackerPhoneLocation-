package io.homedeck.grid.contracts

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.homedeck.grid.entities.AppOpenStat

@Dao
interface AppOpenStatDao {
    @Query("SELECT * FROM app_usage")
    fun getAll(): List<AppOpenStat>

    @Query("SELECT * FROM app_usage WHERE package_name = :packageName")
    fun get(packageName: String): AppOpenStat?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(usage: AppOpenStat)

    @Query("DELETE FROM app_usage WHERE package_name = :packageName")
    fun remove(packageName: String)

    /** Counts one more launch of the app, now. */
    @Transaction
    fun recordLaunch(packageName: String, activityName: String, now: Long = System.currentTimeMillis()) {
        val previous = get(packageName)
        insert(AppOpenStat(packageName, activityName, (previous?.launchCount ?: 0) + 1, now))
    }
}
