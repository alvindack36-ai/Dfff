package com.example.data.local

import androidx.room.*

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox_events ORDER BY nextRetryAt ASC")
    suspend fun getAllEvents(): List<OutboxEventEntity>

    @Query("SELECT * FROM outbox_events WHERE nextRetryAt <= :now ORDER BY nextRetryAt ASC")
    suspend fun getPendingEvents(now: Long = System.currentTimeMillis()): List<OutboxEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: OutboxEventEntity)

    @Delete
    suspend fun deleteEvent(event: OutboxEventEntity)

    @Update
    suspend fun updateEvent(event: OutboxEventEntity)
}
