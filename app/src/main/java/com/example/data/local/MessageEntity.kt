package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val recipient: String,
    val body: String,
    val type: String, // "SENT", "FAILED", "INCOMING"
    val status: String, // "PENDING", "SENT", "FAILED"
    val attempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val simIndex: Int = -1 // -1 means default, 0 is SIM 1, 1 is SIM 2
)
