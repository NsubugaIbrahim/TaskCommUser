package com.example.taskcomm1.data.local

import androidx.room.TypeConverter
import com.google.firebase.Timestamp
import java.util.Date

class Converters {
    
    @TypeConverter
    fun fromTimestamp(timestamp: Timestamp?): Date? {
        return timestamp?.toDate()
    }
    
    @TypeConverter
    fun dateToTimestamp(date: Date?): Timestamp? {
        return date?.let { Timestamp(it) }
    }
    
    @TypeConverter
    fun fromTimestampToLong(timestamp: Timestamp?): Long? {
        return timestamp?.seconds
    }
    
    @TypeConverter
    fun longToTimestamp(seconds: Long?): Timestamp? {
        return seconds?.let { Timestamp(it, 0) }
    }
}

