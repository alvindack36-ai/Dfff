package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY createdAt DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE type = :type ORDER BY createdAt DESC")
    fun getMessagesByType(type: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageById(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)

    @Query("DELETE FROM messages")
    suspend fun clearHistory()

    @Query("SELECT COUNT(*) FROM messages WHERE type = 'SENT' AND createdAt >= :since")
    fun getSentCountToday(since: Long = getStartOfDay()): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE type = 'FAILED' AND createdAt >= :since")
    fun getFailedCountToday(since: Long = getStartOfDay()): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE type = 'INCOMING' AND createdAt >= :since")
    fun getReceivedCountToday(since: Long = getStartOfDay()): Flow<Int>
}

fun getStartOfDay(): Long {
    val calendar = java.util.Calendar.getInstance()
    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
    calendar.set(java.util.Calendar.MINUTE, 0)
    calendar.set(java.util.Calendar.SECOND, 0)
    calendar.set(java.util.Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}
