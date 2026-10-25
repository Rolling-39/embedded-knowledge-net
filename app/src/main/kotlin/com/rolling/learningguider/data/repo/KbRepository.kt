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
import com.rolling.learningguider.data.model.Job
import com.rolling.learningguider.data.model.JobState
import com.rolling.learningguider.data.model.LearnPath
import com.rolling.learningguider.data.model.ResourceItem
import com.rolling.learningguider.data.model.Skill
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
        scope.launch {
            val t = db.stateDao().kv(KV_THEME)?.value ?: ""
            val r = db.stateDao().kv(KV_REDUCE)?.value == "1"
            val h = db.stateDao().kv(KV_NETHINT)?.value != "0"
            _prefs.value = Prefs(t, r, h)
        }
    }

    /** 学习状态（M2+ 界面用）：技能/岗位状态与岗位进度 */
    val skillDone: Flow<Map<String, SkillDone>> =
        db.stateDao().skillDoneFlow().map { list -> list.associate { it.skillId to SkillDone(it.st, it.note, it.updated, it.startDate, it.goalDate) } }
    val jobState: Flow<Map<String, JobState>> =
        db.stateDao().jobStateFlow().map { list -> list.associate { it.jobId to JobState(it.st, it.updated) } }
    val progressFlow: Flow<Map<String, com.rolling.learningguider.data.model.ProgressEntry>> =
        db.stateDao().progressFlow().map { list ->
            list.associate { it.jobId to com.rolling.learningguider.data.model.ProgressEntry(it.jobId, it.mastery, it.done, it.note) }
        }
    val userRecordsFlow: Flow<List<com.rolling.learningguider.data.db.UserRecordEntity>> = db.userDao().recordsFlow()

    /* ============== 偏好 ============== */

    data class Prefs(val theme: String = "", val reduceMotion: Boolean = false, val showNetHint: Boolean = true)

    private val _prefs = MutableStateFlow(Prefs())
    val prefs: StateFlow<Prefs> = _prefs.asStateFlow()

    /* ============== 状态写操作 ============== */

    suspend fun setSkillStatus(id: String, st: String) = withContext(Dispatchers.IO) {
        val cur = db.stateDao().skillDoneOf(id)
        val updated = if (st == "learning" && (cur?.updated ?: 0L) == 0L) System.currentTimeMillis() else cur?.updated ?: 0L
        db.stateDao().upsertSkillDone(
            listOf(SkillDoneEntity(id, st, cur?.note ?: "", updated, cur?.startDate ?: "", cur?.goalDate ?: "")),
        )
    }

    suspend fun setSkillNote(id: String, note: String) = withContext(Dispatchers.IO) {
        val cur = db.stateDao().skillDoneOf(id)
        db.stateDao().upsertSkillDone(
            listOf(SkillDoneEntity(id, cur?.st ?: "none", note, cur?.updated ?: 0L, cur?.startDate ?: "", cur?.goalDate ?: "")),
        )
    }

    suspend fun setSkillStartDate(id: String, date: String) = withContext(Dispatchers.IO) {
        val cur = db.stateDao().skillDoneOf(id)
        db.stateDao().upsertSkillDone(
            listOf(SkillDoneEntity(id, cur?.st ?: "none", cur?.note ?: "", cur?.updated ?: 0L, date, cur?.goalDate ?: "")),
        )
    }

    suspend fun setSkillGoalDate(id: String, date: String) = withContext(Dispatchers.IO) {
        val cur = db.stateDao().skillDoneOf(id)
        db.stateDao().upsertSkillDone(
            listOf(SkillDoneEntity(id, cur?.st ?: "none", cur?.note ?: "", cur?.updated ?: 0L, cur?.startDate ?: "", date)),
        )
    }

    suspend fun setJobStatus(id: String, st: String) = withContext(Dispatchers.IO) {
        val cur = db.stateDao().jobStateOf(id)
        val updated = if (st == "learning" && (cur?.updated ?: 0L) == 0L) System.currentTimeMillis() else cur?.updated ?: 0L
        db.stateDao().upsertJobState(listOf(JobStateEntity(id, st, updated)))
    }

    suspend fun setJobNote(jobId: String, note: String) = withContext(Dispatchers.IO) {
        val cur = db.stateDao().progressOf(jobId)
        db.stateDao().upsertProgress(
            listOf(ProgressEntity(jobId, cur?.mastery ?: 0, cur?.done ?: false, note)),
        )
    }

    /* ============== 记录删除 / 恢复 ==============
     * 与网页版不同：删除=纯墓碑（不做路径手术）——合并层会把不存在的技能从路径、
     * 把缺失岗位的路径自动过滤掉，恢复即删墓碑，行为等价且完全可逆；导出后网页版
     * 的 buildEffective 同样按过滤处理，互导语义一致。 */

    suspend fun deleteRecord(kind: String, id: String) = withContext(Dispatchers.IO) {
        val payload: JsonObject = when (kind) {
            KIND_SKILL -> {
                val def = builtin.skills[id]
                if (def != null) tombstone(Merger.json.encodeToJsonElement(Skill.serializer(), def).jsonObject)
                else tombstone(Merger.json.encodeToJsonElement(Skill.serializer(), _effective.value.skills[id] ?: Skill(name = id)).jsonObject, user = true)
            }
            KIND_PATH -> {
                val def = builtin.paths.firstOrNull { it.id == id }
                if (def != null) tombstone(Merger.json.encodeToJsonElement(LearnPath.serializer(), def).jsonObject)
                else tombstone(Merger.json.encodeToJsonElement(LearnPath.serializer(), _effective.value.paths.firstOrNull { it.id == id } ?: LearnPath(id = id)).jsonObject, user = true)
            }
            else -> {
                val def = builtin.jobs[id]
                if (def != null) tombstone(Merger.json.encodeToJsonElement(Job.serializer(), def).jsonObject)
                else tombstone(Merger.json.encodeToJsonElement(Job.serializer(), _effective.value.jobs[id] ?: Job(id = id)).jsonObject, user = true)
            }
        }
        db.userDao().upsertRecords(
            listOf(UserRecordEntity(id, kind, payload.flag("user"), true, payload.toString())),
        )
    }

    suspend fun restoreRecord(id: String) = withContext(Dispatchers.IO) { db.userDao().deleteRecord(id) }

    suspend fun deleteResource(id: String) = withContext(Dispatchers.IO) { db.userDao().deleteResource(id) }

    /** 清空全部用户数据（内置内容保留，双确认后调用） */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        db.userDao().clearRecords()
        db.userDao().clearResources()
        db.stateDao().clearSkillDone()
        db.stateDao().clearJobState()
        db.stateDao().clearProgress()
    }

    private fun tombstone(base: JsonObject, user: Boolean = false): JsonObject =
        JsonObject(
            base.toMap() + mapOf(
                "deleted" to kotlinx.serialization.json.JsonPrimitive(true),
                *(if (user) arrayOf("user" to kotlinx.serialization.json.JsonPrimitive(true)) else emptyArray()),
            ),
        )

    suspend fun setTheme(mode: String) = withContext(Dispatchers.IO) {
        db.stateDao().putKv(com.rolling.learningguider.data.db.KvEntity(KV_THEME, mode))
        _prefs.value = _prefs.value.copy(theme = mode)
    }

    /** 新增/编辑记录：payload 应为完整记录（user=true），upsert 覆盖旧覆盖或墓碑 */
    suspend fun upsertRecord(kind: String, id: String, payload: JsonObject) = withContext(Dispatchers.IO) {
        db.userDao().upsertRecords(
            listOf(UserRecordEntity(id, kind, payload.flag("user"), false, payload.toString())),
        )
    }

    /** 新增自定义资源 */
    suspend fun addResource(item: ResourceItem) = withContext(Dispatchers.IO) {
        db.userDao().upsertResources(
            listOf(UserResourceEntity(item.id.ifBlank { "ur${System.currentTimeMillis()}" }, item.cat, item.n, item.d)),
        )
    }

    suspend fun setReduceMotion(on: Boolean) = withContext(Dispatchers.IO) {
        db.stateDao().putKv(com.rolling.learningguider.data.db.KvEntity(KV_REDUCE, if (on) "1" else "0"))
        _prefs.value = _prefs.value.copy(reduceMotion = on)
    }

    suspend fun dismissNetHint() = withContext(Dispatchers.IO) {
        db.stateDao().putKv(com.rolling.learningguider.data.db.KvEntity(KV_NETHINT, "0"))
        _prefs.value = _prefs.value.copy(showNetHint = false)
    }

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
            s.skillRecords.map { (id, o) -> UserRecordEntity(id, KIND_SKILL, o.flag("user"), o.flag("deleted"), o.toString()) } +
                s.pathRecords.map { (id, o) -> UserRecordEntity(id, KIND_PATH, o.flag("user"), o.flag("deleted"), o.toString()) } +
                s.jobRecords.map { (id, o) -> UserRecordEntity(id, KIND_JOB, o.flag("user"), o.flag("deleted"), o.toString()) },
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

    private fun JsonObject.flag(name: String): Boolean =
        (this[name] as? kotlinx.serialization.json.JsonPrimitive)?.let { it.booleanOrNull ?: false } ?: false

    companion object {
        const val BUILTIN_ASSET = "builtin.json"
        const val KIND_SKILL = "skill"
        const val KIND_PATH = "path"
        const val KIND_JOB = "job"
        const val KV_THEME = "theme"
        const val KV_REDUCE = "reduce"
        const val KV_ANIMOFF = "animoff"
        const val KV_NETHINT = "netHint"
    }
}
