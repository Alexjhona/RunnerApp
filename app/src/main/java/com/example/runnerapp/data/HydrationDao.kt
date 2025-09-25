package com.example.runnerapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface HydrationDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: HydrationRecord): Long
    
    @Query("SELECT * FROM hydration_records WHERE userEmail = :userEmail AND date = :date")
    suspend fun getRecordForDate(userEmail: String, date: String): HydrationRecord?
    
    @Query("SELECT * FROM hydration_records WHERE userEmail = :userEmail ORDER BY date DESC LIMIT 30")
    suspend fun getRecentRecords(userEmail: String): List<HydrationRecord>
    
    @Query("UPDATE hydration_records SET waterIntakeMl = waterIntakeMl + :amount WHERE userEmail = :userEmail AND date = :date")
    suspend fun addWaterIntake(userEmail: String, date: String, amount: Int)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: HydrationSettings)
    
    @Update
    suspend fun updateSettings(settings: HydrationSettings)
    
    @Query("SELECT * FROM hydration_settings WHERE userEmail = :userEmail")
    suspend fun getSettings(userEmail: String): HydrationSettings?
}
