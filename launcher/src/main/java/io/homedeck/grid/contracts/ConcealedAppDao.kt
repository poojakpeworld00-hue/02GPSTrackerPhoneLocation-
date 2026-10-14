package io.homedeck.grid.contracts

import androidx.room.*
import io.homedeck.grid.entities.ConcealedApp

@Dao
interface ConcealedAppDao {
    @Query("SELECT * FROM hidden_icons")
    fun getHiddenIcons(): List<ConcealedApp>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(hiddenIconUi: ConcealedApp): Long

    @Delete
    fun removeHiddenIcons(icons: List<ConcealedApp>)
}
