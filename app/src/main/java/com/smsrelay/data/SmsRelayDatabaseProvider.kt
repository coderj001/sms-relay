package com.smsrelay.data

import android.content.Context
import androidx.room.Room

object SmsRelayDatabaseProvider {
    @Volatile private var instance: SmsRelayDatabase? = null

    fun get(context: Context): SmsRelayDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(context.applicationContext, SmsRelayDatabase::class.java, "sms-relay.db").build().also { instance = it }
    }
}
