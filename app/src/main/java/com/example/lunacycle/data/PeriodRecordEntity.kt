package com.example.lunacycle.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "period_records",
    indices = [Index(value = ["start_date"])]
)
data class PeriodRecordEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "start_date")
    val startDate: LocalDate,

    @ColumnInfo(name = "end_date")
    val endDate: LocalDate? = null,

    @ColumnInfo(name = "note")
    val note: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
