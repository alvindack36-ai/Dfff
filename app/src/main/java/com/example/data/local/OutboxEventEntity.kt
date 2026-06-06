package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbox_events")
data class OutboxEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val endpoint: String,
    val payloadJson: String,
    val attempts: Int = 0,
    val nextRetryAt: Long = System.currentTimeMillis()
)
