package io.homedeck.grid.contracts

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.homedeck.grid.entities.LaunchableApp

@Dao
interface LaunchableAppDao {
    @Query("SELECT * FROM apps")
    fun getAppLaunchers(): List<LaunchableApp>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(appLaunchers: List<LaunchableApp>)

    @Query("DELETE FROM apps WHERE package_name = :packageName")
    fun deleteApp(packageName: String)

    @Query("DELETE FROM apps WHERE id = :id")
    fun deleteById(id: Long)
}
