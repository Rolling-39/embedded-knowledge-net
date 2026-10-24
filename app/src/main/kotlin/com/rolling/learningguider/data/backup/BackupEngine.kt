package com.rolling.learningguider.data.backup

import com.rolling.learningguider.data.merge.Merger
import com.rolling.learningguider.data.merge.UserState
import com.rolling.learningguider.data.model.BuiltinData
import com.rolling.learningguider.data.model.JobState
import com.rolling.learningguider.data.model.KbConstants.DIR2JOB
import com.rolling.learningguider.data.model.KbConstants.VALID_ID
import com.rolling.learningguider.data.model.ProgressEntry
import com.rolling.learningguider.data.model.ResourceItem
import com.rolling.learningguider.data.model.SkillDone
import com.rolling.learningguider.data.model.WebBackup
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** 本地数据快照（Room 表的直接映射，导入/导出的操作单元） */
data class LocalSnapshot(
    val skillRecords: Map<String, JsonObject> = emptyMap(),
    val pathRecords: Map<String, JsonObject> = emptyMap(),
    val jobRecords: Map<String, JsonObject> = emptyMap(),
    val userResources: List<ResourceItem> = emptyList(),
    val skillDone: Map<String, SkillDone> = emptyMap(),
    val jobState: Map<String, JobState> = emptyMap(),
    val progress: List<ProgressEntry> = emptyList(),
    val theme: String = "",
    val reduce: String = "0",
    val animoff: String = "0",
)

data class ImportStats(val skills: Int, val jobs: Int, val paths: Int, val resources: Int, val progressCount: Int)

/** 快照 → 合并层用户状态 */
fun LocalSnapshot.toUserState(): UserState =
    UserState(skillRecords, pathRecords, jobRecords, userResources)

/**
 * 备份编解码 —— 逐条移植网页版 importJSON/exportJSON：
 * id 白名单过滤、覆盖/合并两模式（合并=本地已有保留、新增且非墓碑加入、进度状态以导入为准）、
 * dirId 旧格式迁移、skillDone 旧字符串值归一化、字段兜底。
 */
object BackupEngine {

    val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val pretty: Json = Json { prettyPrint = true; prettyPrintIndent = "  " }

    /** 解析网页版导出文本（兼容最老的裸数组 progress 格式） */
    fun decode(text: String): WebBackup {
        val element = json.parseToJsonElement(text)
        val root: JsonElement = when (element) {
            is JsonArray -> buildJsonObject { put("progress", element) }
            is JsonObject -> element
            else -> throw IllegalArgumentException("未识别的备份格式")
        }
        return json.decodeFromJsonElement(WebBackup.serializer(), root)
    }

    /** id 白名单 + 值必须为对象（网页版 v9.18 防注入口径） */
    private fun Map<String, JsonElement>.validObjects(): Map<String, JsonObject> =
        mapNotNull { (k, v) -> if (v is JsonObject && VALID_ID.matches(k)) k to v else null }.toMap()

    private fun JsonObject.deletedFlag(): Boolean =
        (this["deleted"] as? JsonPrimitive)?.content == "true"

    /**
     * 应用导入（纯函数）。
     * @param merge true=合并模式（本地记录保留，新增非墓碑加入）；false=覆盖
     */
    fun applyImport(builtin: BuiltinData, local: LocalSnapshot, backup: WebBackup, merge: Boolean): Pair<LocalSnapshot, ImportStats> {
        val inSkills = backup.userSkills.validObjects()
        val inPaths = backup.userPaths.validObjects()
        val inJobs = backup.userJobs.validObjects()
        val inRes = backup.userRes.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val item = runCatching { json.decodeFromJsonElement(ResourceItem.serializer(), obj) }.getOrNull()
            if (item != null && item.id.isNotBlank() && VALID_ID.matches(item.id)) item else null
        }

        val stats: ImportStats
        val skills: Map<String, JsonObject>
        val paths: Map<String, JsonObject>
        val jobs: Map<String, JsonObject>
        val resources: List<ResourceItem>
        if (merge) {
            val addSkills = inSkills.filterKeys { it !in local.skillRecords && !(inSkills[it]?.deletedFlag() ?: false) }
            val addPaths = inPaths.filterKeys { it !in local.pathRecords && !(inPaths[it]?.deletedFlag() ?: false) }
            val addJobs = inJobs.filterKeys { it !in local.jobRecords && !(inJobs[it]?.deletedFlag() ?: false) }
            val haveRes = local.userResources.map { it.id }.toHashSet()
            val addRes = inRes.filter { it.id !in haveRes }
            skills = local.skillRecords + addSkills
            paths = local.pathRecords + addPaths
            jobs = local.jobRecords + addJobs
            resources = local.userResources + addRes
            stats = ImportStats(addSkills.size, addJobs.size, addPaths.size, addRes.size, 0)
        } else {
            skills = inSkills
            paths = inPaths
            jobs = inJobs
            resources = inRes
            stats = ImportStats(0, 0, 0, 0, 0)
        }

        // 进度：dirId 旧格式迁移，按合并后的有效岗位过滤
        val effective = Merger.buildEffective(builtin, UserState(skills, paths, jobs, resources))
        val progress = backup.progress
            .map { p -> if (p.jobId.isBlank() && p.dirId.isNotBlank()) p.copy(jobId = DIR2JOB[p.dirId] ?: p.dirId) else p }
            .filter { effective.jobs.containsKey(it.jobId) }

        // 学习状态：以导入为准；旧字符串值归一化
        val skillDone = backup.skillDone.mapNotNull { (id, el) ->
            if (!VALID_ID.matches(id)) return@mapNotNull null
            val v = when (el) {
                is JsonPrimitive -> SkillDone(st = el.content.takeIf { c -> c.isNotBlank() } ?: "none")
                is JsonObject -> runCatching { json.decodeFromJsonElement(SkillDone.serializer(), el) }.getOrDefault(SkillDone())
                else -> null
            } ?: return@mapNotNull null
            id to v
        }.toMap()
        val jobState = backup.jobState.mapNotNull { (id, el) ->
            if (!VALID_ID.matches(id)) return@mapNotNull null
            val v = when (el) {
                is JsonPrimitive -> JobState(st = el.content.takeIf { c -> c.isNotBlank() } ?: "none")
                is JsonObject -> runCatching { json.decodeFromJsonElement(JobState.serializer(), el) }.getOrDefault(JobState())
                else -> null
            } ?: return@mapNotNull null
            id to v
        }.toMap()

        val snapshot = local.copy(
            skillRecords = skills,
            pathRecords = paths,
            jobRecords = jobs,
            userResources = resources,
            skillDone = skillDone,
            jobState = jobState,
            progress = progress,
            theme = if (backup.theme == "dark" || backup.theme == "light") backup.theme else local.theme,
            reduce = if (backup.reduce == "1") "1" else "0",
            animoff = if (backup.animoff == "1") "1" else "0",
        )
        return snapshot to stats.copy(progressCount = progress.size)
    }

    /** 导出为网页版可直接导入的 JSON 文本 */
    fun export(type: String, local: LocalSnapshot): String {
        val root = buildJsonObject {
            put("type", type)
            put("exportedAt", java.time.Instant.now().toString())
            put("theme", local.theme)
            put("reduce", local.reduce)
            put("animoff", local.animoff)
            put("progress", json.encodeToJsonElement(ListSerializer(ExportProgress.serializer()),
                local.progress.map { ExportProgress(it.jobId, it.mastery, it.done, it.note) }))
            put("skillDone", buildJsonObject {
                local.skillDone.forEach { (id, v) ->
                    put(id, buildJsonObject {
                        put("st", v.st); put("note", v.note); put("updated", v.updated)
                        put("startDate", v.startDate); put("goalDate", v.goalDate)
                    })
                }
            })
            put("jobState", buildJsonObject {
                local.jobState.forEach { (id, v) ->
                    put(id, buildJsonObject { put("st", v.st); put("updated", v.updated) })
                }
            })
            put("userSkills", JsonObject(local.skillRecords))
            put("userPaths", JsonObject(local.pathRecords))
            put("userJobs", JsonObject(local.jobRecords))
            put("userRes", JsonArray(local.userResources.map { json.encodeToJsonElement(ResourceItem.serializer(), it) }))
        }
        return pretty.encodeToString(JsonObject.serializer(), root)
    }

    @kotlinx.serialization.Serializable
    private data class ExportProgress(val jobId: String, val mastery: Int, val done: Boolean, val note: String)
}
