package com.rolling.learningguider.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/* ============== 实体 ============== */

/** 用户定制记录（技能/路径/岗位覆盖 + 墓碑），payload 为原始 JSON，保留 partial 字段语义 */
@Entity(tableName = "user_records")
data class UserRecordEntity(
    @PrimaryKey val id: String,
    val kind: String, // skill | path | job
    val user: Boolean,
    val deleted: Boolean,
    val payload: String,
)

@Entity(tableName = "user_resources")
data class UserResourceEntity(
    @PrimaryKey val id: String,
    val cat: String,
    val n: String,
    val d: String,
)

@Entity(tableName = "skill_done")
data class SkillDoneEntity(
    @PrimaryKey val skillId: String,
    val st: String,
    val note: String,
    val updated: Long,
    val startDate: String,
    val goalDate: String,
)

@Entity(tableName = "job_state")
data class JobStateEntity(
    @PrimaryKey val jobId: String,
    val st: String,
    val updated: Long,
)

@Entity(tableName = "job_progress")
data class ProgressEntity(
    @PrimaryKey val jobId: String,
    val mastery: Int,
    val done: Boolean,
    val note: String,
)

@Entity(tableName = "kv")
data class KvEntity(@PrimaryKey val key: String, val value: String)

/* ============== DAO ============== */

@Dao
interface UserDao {
    @Query("SELECT * FROM user_records")
    fun recordsFlow(): Flow<List<UserRecordEntity>>

    @Query("SELECT * FROM user_records")
    suspend fun records(): List<UserRecordEntity>

    @Upsert
    suspend fun upsertRecords(items: List<UserRecordEntity>)

    @Query("DELETE FROM user_records")
    suspend fun clearRecords()

    @Query("SELECT * FROM user_resources")
    fun resourcesFlow(): Flow<List<UserResourceEntity>>

    @Query("SELECT * FROM user_resources")
    suspend fun resources(): List<UserResourceEntity>

    @Upsert
    suspend fun upsertResources(items: List<UserResourceEntity>)

    @Query("DELETE FROM user_resources")
    suspend fun clearResources()
}

@Dao
interface StateDao {
    @Query("SELECT * FROM skill_done")
    fun skillDoneFlow(): Flow<List<SkillDoneEntity>>

    @Query("SELECT * FROM skill_done")
    suspend fun skillDone(): List<SkillDoneEntity>

    @Upsert
    suspend fun upsertSkillDone(items: List<SkillDoneEntity>)

    @Query("DELETE FROM skill_done")
    suspend fun clearSkillDone()

    @Query("SELECT * FROM job_state")
    fun jobStateFlow(): Flow<List<JobStateEntity>>

    @Query("SELECT * FROM job_state")
    suspend fun jobState(): List<JobStateEntity>

    @Upsert
    suspend fun upsertJobState(items: List<JobStateEntity>)

    @Query("DELETE FROM job_state")
    suspend fun clearJobState()

    @Query("SELECT * FROM job_progress")
    fun progressFlow(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM job_progress")
    suspend fun progress(): List<ProgressEntity>

    @Upsert
    suspend fun upsertProgress(items: List<ProgressEntity>)

    @Query("DELETE FROM job_progress")
    suspend fun clearProgress()

    @Query("SELECT * FROM kv WHERE `key` = :key")
    suspend fun kv(key: String): KvEntity?

    @Upsert
    suspend fun putKv(item: KvEntity)
}

@Database(
    entities = [
        UserRecordEntity::class, UserResourceEntity::class, SkillDoneEntity::class,
        JobStateEntity::class, ProgressEntity::class, KvEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun stateDao(): StateDao

    companion object {
        const val NAME = "learningguider.db"
    }
}
