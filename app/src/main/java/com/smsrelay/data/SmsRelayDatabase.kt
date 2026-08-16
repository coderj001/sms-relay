package com.smsrelay.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Delete

@Entity(tableName = "sms_rules")
data class SmsRuleEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean,
    val senderFilter: String?,
    val messageRegex: String,
    val destinationNumber: String,
    val outputTemplate: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "execution_logs", indices = [androidx.room.Index(value = ["fingerprint"], unique = true)])
data class ExecutionLogEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fingerprint: String,
    val ruleId: Long?,
    val receivedAt: Long,
    val senderPreview: String?,
    val destinationMasked: String?,
    val status: String,
    val detail: String?,
    val createdAt: Long,
)

@Dao
interface SmsRelayDao {
    @Query("SELECT * FROM sms_rules ORDER BY updatedAt DESC")
    suspend fun allRules(): List<SmsRuleEntity>

    @Query("SELECT * FROM sms_rules WHERE enabled = 1")
    suspend fun enabledRules(): List<SmsRuleEntity>

    @Insert
    suspend fun insertRule(rule: SmsRuleEntity): Long

    @Update
    suspend fun updateRule(rule: SmsRuleEntity)

    @Delete
    suspend fun deleteRule(rule: SmsRuleEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExecutionLog(log: ExecutionLogEntity): Long

    @Query("SELECT COUNT(*) FROM execution_logs WHERE createdAt >= :since")
    suspend fun countSince(since: Long): Int
}

@Database(entities = [SmsRuleEntity::class, ExecutionLogEntity::class], version = 1, exportSchema = false)
abstract class SmsRelayDatabase : RoomDatabase() {
    abstract fun dao(): SmsRelayDao
}
