package com.rolling.learningguider.data.repo

import android.content.res.AssetManager
import com.rolling.learningguider.data.backup.BackupEngine
import com.rolling.learningguider.data.backup.ImportStats
import com.rolling.learningguider.data.backup.LocalSnapshot
import com.rolling.learningguider.data.db.AppDatabase
import com.rolling.learningguider.data.db.JobStateEntity
import com.rolling.learningguider.data.db.ProgressEntity
import com.rolling.learningguider.data.db.SkillDoneEntity
import com.rolling.learningguider.data.db.UserRecordEntity
import com.rolling.learningguider.data.db.UserResourceEntity
import com.rolling.learningguider.data.merge.Merger
import com.rolling.learningguider.data.merge.UserState
import com.rolling.learningguider.data.model.BuiltinData
import com.rolling.learningguider.data.model.EffectiveData
import com.rolling.learningguider.data.model.JobState
import com.rolling.learningguider.data.model.ResourceItem
import com.rolling.learningguider.data.model.SkillDone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 数据仓库：assets 内置内容（单一事实源=网页版导出）+ Room 用户数据 → 合并成有效数据。
 * UI 只读 [effective]；写操作走 import/export 与（M2+ 的）记录编辑接口。
 */
class KbRepository(
    private val assets: AssetManager,
    private val db: AppDatabase,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    val builtin: BuiltinData by lazy {
        runCatching {
            assets.open(BUILTIN_ASSET).bufferedReader(Charsets.UTF_8).use {
                BackupEngine.json.decodeFromString(BuiltinData.serializer(), it.readText())
            }
        }.getOrElse { BuiltinData() }
    }

    /** 用户记录（技能/路径/岗位 payload + 自定义资源），驱动有效数据重算 */
    private val userState: Flow<UserState> = combine(
        db.userDao().recordsFlow(),
        db.userDao().resourcesFlow(),
    ) { records, resources ->
        val skills = LinkedHashMap<String, JsonObject>()
        val paths = LinkedHashMap<String, JsonObject>()
        val jobs = LinkedHashMap<String, JsonObject>()
        records.forEach { r ->
            val obj = runCatching { BackupEngine.json.parseToJsonElement(r.payload).jsonObject }.getOrNull()
            when (r.kind) {
                KIND_SKILL -> if (obj != null) skills[r.id] = obj
                KIND_PATH -> if (obj != null) paths[r.id] = obj
                KIND_JOB -> if (obj != null) jobs[r.id] = obj
            }
        }
        UserState(skills, paths, jobs, resources.map { ResourceItem(it.id, it.cat, it.n, it.d) })
    }

    private val _effective = MutableStateFlow(EffectiveData(builtin.version))
    val effective: StateFlow<EffectiveData> = _effective.asStateFlow()

    init {
        scope.launch {
            userState.collect { _effective.value = Merger.buildEffective(builtin, it) }
        }
    }

    /** 学习状态（M2+ 界面用）：技能/岗位状态与岗位进度 */
    val skillDone: Flow<Map<String, SkillDone>> =
        db.stateDao().skillDoneFlow().map { list -> list.associate { it.skillId to SkillDone(it.st, it.note, it.updated, it.startDate, it.goalDate) } }
    val jobState: Flow<Map<String, JobState>> =
        db.stateDao().jobStateFlow().map { list -> list.associate { it.jobId to JobState(it.st, it.updated) } }

    /* ============== 导入 / 导出 ============== */

    suspend fun importBackup(text: String, merge: Boolean): ImportStats = withContext(Dispatchers.IO) {
        val backup = BackupEngine.decode(text)
        val local = readSnapshot()
        val (next, stats) = BackupEngine.applyImport(builtin, local, backup, merge)
        writeSnapshot(next)
        stats
    }

    suspend fun exportBackup(): String = withContext(Dispatchers.IO) {
        val snapshot = readSnapshot()
        BackupEngine.export("embed-kb-" + builtin.version, snapshot)
    }

    private suspend fun readSnapshot(): LocalSnapshot {
        val records = db.userDao().records()
        val res = db.userDao().resources()
        val skillDone = db.stateDao().skillDone().associate {
            it.skillId to SkillDone(it.st, it.note, it.updated, it.startDate, it.goalDate)
        }
        val jobState = db.stateDao().jobState().associate { it.jobId to JobState(it.st, it.updated) }
        val progress = db.stateDao().progress().map { com.rolling.learningguider.data.model.ProgressEntry(it.jobId, it.mastery, it.done, it.note) }
        val skills = LinkedHashMap<String, JsonObject>()
        val paths = LinkedHashMap<String, JsonObject>()
        val jobs = LinkedHashMap<String, JsonObject>()
        records.forEach { r ->
            val obj = runCatching { BackupEngine.json.parseToJsonElement(r.payload).jsonObject }.getOrNull() ?: return@forEach
            when (r.kind) {
                KIND_SKILL -> skills[r.id] = obj
                KIND_PATH -> paths[r.id] = obj
                KIND_JOB -> jobs[r.id] = obj
            }
        }
        return LocalSnapshot(
            skillRecords = skills,
            pathRecords = paths,
            jobRecords = jobs,
            userResources = res.map { ResourceItem(it.id, it.cat, it.n, it.d) },
            skillDone = skillDone,
            jobState = jobState,
            progress = progress,
            theme = db.stateDao().kv(KV_THEME)?.value ?: "",
            reduce = db.stateDao().kv(KV_REDUCE)?.value ?: "0",
            animoff = db.stateDao().kv(KV_ANIMOFF)?.value ?: "0",
        )
    }

    private suspend fun writeSnapshot(s: LocalSnapshot) {
        val dao = db.userDao()
        dao.clearRecords()
        dao.upsertRecords(
            s.skillRecords.map { (id, o) -> UserRecordEntity(id, KIND_SKILL, flag(o, "user"), flag(o, "deleted"), o.toString()) } +
                s.pathRecords.map { (id, o) -> UserRecordEntity(id, KIND_PATH, flag(o, "user"), flag(o, "deleted"), o.toString()) } +
                s.jobRecords.map { (id, o) -> UserRecordEntity(id, KIND_JOB, flag(o, "user"), flag(o, "deleted"), o.toString()) },
        )
        dao.clearResources()
        dao.upsertResources(s.userResources.map { UserResourceEntity(it.id, it.cat, it.n, it.d) })

        val state = db.stateDao()
        state.clearSkillDone()
        state.upsertSkillDone(s.skillDone.map { (id, v) -> SkillDoneEntity(id, v.st, v.note, v.updated, v.startDate, v.goalDate) })
        state.clearJobState()
        state.upsertJobState(s.jobState.map { (id, v) -> JobStateEntity(id, v.st, v.updated) })
        state.clearProgress()
        state.upsertProgress(s.progress.map { ProgressEntity(it.jobId, it.mastery, it.done, it.note) })
        state.putKv(com.rolling.learningguider.data.db.KvEntity(KV_THEME, s.theme))
        state.putKv(com.rolling.learningguider.data.db.KvEntity(KV_REDUCE, s.reduce))
        state.putKv(com.rolling.learningguider.data.db.KvEntity(KV_ANIMOFF, s.animoff))
    }

    private fun flag(o: JsonObject, name: String): Boolean =
        (o[name] as? kotlinx.serialization.json.JsonPrimitive)?.let { it.booleanOrNull ?: false } ?: false

    companion object {
        const val BUILTIN_ASSET = "builtin.json"
        const val KIND_SKILL = "skill"
        const val KIND_PATH = "path"
        const val KIND_JOB = "job"
        const val KV_THEME = "theme"
        const val KV_REDUCE = "reduce"
        const val KV_ANIMOFF = "animoff"
    }
}
