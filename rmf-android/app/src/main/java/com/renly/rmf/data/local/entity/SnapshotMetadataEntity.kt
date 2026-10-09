package com.renly.rmf.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "snapshot_metadata")
data class SnapshotMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = "current",

    @ColumnInfo(name = "device_id")
    val deviceId: String = "",

    @ColumnInfo(name = "version_counter", defaultValue = "1")
    val versionCounter: Int = 1,

    @ColumnInfo(name = "file_hash")
    val fileHash: String = "",

    @ColumnInfo(name = "updated_at")
    val updatedAt: String = ""
)
