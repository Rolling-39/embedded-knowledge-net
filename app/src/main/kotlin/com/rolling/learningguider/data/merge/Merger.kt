package com.rolling.learningguider.data.merge

import com.rolling.learningguider.data.model.BuiltinData
import com.rolling.learningguider.data.model.EffectiveData
import com.rolling.learningguider.data.model.Job
import com.rolling.learningguider.data.model.LearnPath
import com.rolling.learningguider.data.model.ResourceCategory
import com.rolling.learningguider.data.model.ResourceItem
import com.rolling.learningguider.data.model.Skill
import com.rolling.learningguider.data.model.KbConstants.CAT_TITLES
import com.rolling.learningguider.data.model.KbConstants.VALID_ID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/** 用户定制数据：记录为原始 payload（保留 partial 字段语义） */
data class UserState(
    val skillPayloads: Map<String, JsonObject> = emptyMap(),
    val pathPayloads: Map<String, JsonObject> = emptyMap(),
    val jobPayloads: Map<String, JsonObject> = emptyMap(),
    val userResources: List<ResourceItem> = emptyList(),
)

/**
 * 合并层 —— 逐行移植网页版 buildEffective()：
 * 内置 + 用户覆盖(字段级) → 有效数据；墓碑=隐藏；自定义=追加；
 * 路径技能过滤到存在的技能；岗位缺失的路径隐藏；资源按分类追加（未知分类归 videos）。
 */
object Merger {

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun JsonObject.flag(name: String): Boolean =
        (this[name] as? kotlinx.serialization.json.JsonPrimitive)?.let { it.booleanOrNull ?: false } ?: false

    private fun <T> mergeOver(base: T, override: JsonObject, serializer: KSerializer<T>): T {
        val baseObj = json.encodeToJsonElement(serializer, base) as? JsonObject ?: return base
        val merged = JsonObject(baseObj.toMap() + override.toMap()) // = Object.assign({}, base, override)
        return json.decodeFromJsonElement(serializer, merged)
    }

    fun buildEffective(builtin: BuiltinData, user: UserState): EffectiveData {
        // 技能：内置顺序 + 覆盖/墓碑，然后追加自定义
        val skills = LinkedHashMap<String, Skill>()
        builtin.skills.forEach { (id, def) ->
            val o = user.skillPayloads[id]
            when {
                o == null -> skills[id] = def
                o.flag("deleted") -> Unit
                o.flag("user") -> skills[id] = mergeOver(def, o, Skill.serializer()).normalized()
                else -> skills[id] = def
            }
        }
        user.skillPayloads.forEach { (id, o) ->
            if (o.flag("user") && !o.flag("deleted") && !skills.containsKey(id)) {
                skills[id] = json.decodeFromJsonElement(Skill.serializer(), o).normalized()
            }
        }

        // 岗位：同构
        val jobs = LinkedHashMap<String, Job>()
        builtin.jobs.forEach { (id, def) ->
            val o = user.jobPayloads[id]
            when {
                o == null -> jobs[id] = def
                o.flag("deleted") -> Unit
                o.flag("user") -> jobs[id] = mergeOver(def, o, Job.serializer()).normalized()
                else -> jobs[id] = def
            }
        }
        user.jobPayloads.forEach { (id, o) ->
            if (o.flag("user") && !o.flag("deleted") && !jobs.containsKey(id)) {
                jobs[id] = json.decodeFromJsonElement(Job.serializer(), o).normalized()
            }
        }

        // 路径：内置顺序，墓碑/岗位缺失则隐藏，技能过滤；自定义追加
        val paths = mutableListOf<LearnPath>()
        builtin.paths.forEach { p ->
            val o = user.pathPayloads[p.id]
            if (o != null && o.flag("deleted")) return@forEach
            val base = if (o != null && o.flag("user")) mergeOver(p, o, LearnPath.serializer()) else p
            if (!jobs.containsKey(base.job)) return@forEach
            paths.add(base.copy(skills = base.skills.filter { skills.containsKey(it) }))
        }
        user.pathPayloads.forEach { (id, o) ->
            if (o.flag("user") && !o.flag("deleted") && paths.none { it.id == id }) {
                val p = json.decodeFromJsonElement(LearnPath.serializer(), o).normalized()
                if (jobs.containsKey(p.job)) {
                    paths.add(p.copy(skills = p.skills.filter { skills.containsKey(it) }))
                }
            }
        }

        // 资源：内置分类深拷贝 + 自定义追加（未知分类归 videos，与网页版一致）
        val resources = LinkedHashMap<String, ResourceCategory>()
        builtin.resources.forEach { (cat, c) -> resources[cat] = ResourceCategory(c.title, c.items) }
        user.userResources.forEach { r ->
            val cat = if (CAT_TITLES.containsKey(r.cat)) r.cat else "videos"
            val existing = resources.getOrPut(cat) { ResourceCategory(CAT_TITLES[cat] ?: cat) }
            resources[cat] = existing.copy(items = existing.items + r)
        }

        return EffectiveData(
            version = builtin.version,
            skills = skills,
            paths = paths,
            jobs = jobs,
            resources = resources,
        )
    }

    /** 导入字段兜底（移植网页版 importJSON 的 normalize 段） */
    private fun Skill.normalized(): Skill =
        if (diff in 1..5 && cat.isNotBlank()) this
        else copy(diff = if (diff in 1..5) diff else 3, cat = if (cat.isBlank()) Skill.CATEGORIES.first() else cat)

    private fun Job.normalized(): Job = copy(
        level = if (level in Job.LEVELS) level else "进阶",
        diff = if (diff in 1..5) diff else 3,
        demand = if (demand in 1..5) demand else 3,
        salary = salary.ifBlank { "面议" },
    )

    private fun LearnPath.normalized(): LearnPath =
        copy(name = name.ifBlank { "自定义路线" })
}
