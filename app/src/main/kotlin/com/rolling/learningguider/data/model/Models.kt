package com.rolling.learningguider.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * 领域模型 —— 字段与网页版 embed-kb.html 的 DEF_* / localStorage 记录逐字段对应。
 * 所有字段带默认值：对应网页版导入时的「字段兜底」，缺失字段安全落到默认。
 */

@Immutable
@Serializable
data class Skill(
    val name: String = "",
    val cat: String = "语言基础",
    val diff: Int = 3,
    val content: String = "",
    val role: String = "",
    val hard: String = "",
    val key: String = "",
    val res: List<String> = emptyList(),
    val pre: List<String> = emptyList(),
    val user: Boolean = false,
    val deleted: Boolean = false,
) {
    companion object {
        val CATEGORIES = listOf("语言基础", "硬件", "系统", "协议", "AI/EDA")
    }
}

@Immutable
@Serializable
data class JobTier(val t: String = "", val s: String = "", val d: String = "")

@Immutable
@Serializable
data class Job(
    val id: String = "",
    val name: String = "",
    val level: String = "进阶",
    val diff: Int = 3,
    val demand: Int = 3,
    val salary: String = "面议",
    val color: String = "",
    val tiers: List<JobTier> = emptyList(),
    val work: String = "",
    val companies: List<String> = emptyList(),
    val review: String = "",
    val projects: List<String> = emptyList(),
    val user: Boolean = false,
    val deleted: Boolean = false,
) {
    companion object {
        val LEVELS = setOf("基础", "进阶", "高阶")
    }
}

@Immutable
@Serializable
data class LearnPath(
    val id: String = "",
    val name: String = "",
    val job: String = "",
    val period: String = "",
    val color: String = "",
    val skills: List<String> = emptyList(),
    val user: Boolean = false,
    val deleted: Boolean = false,
)

/** 自定义学习资源（网页版 userRes 条目：id 为导入白名单必需字段） */
@Immutable
@Serializable
data class ResourceItem(
    val id: String = "",
    val cat: String = "",
    val n: String = "",
    val d: String = "",
)

@Serializable
data class ResourceCategory(
    val title: String = "",
    val items: List<ResourceItem> = emptyList(),
)

/** assets/builtin.json 的顶层结构（由网页版 tools/export-builtin.js 生成） */
@Serializable
data class BuiltinData(
    val version: String = "",
    val generatedAt: String = "",
    val skills: Map<String, Skill> = emptyMap(),
    val paths: List<LearnPath> = emptyList(),
    val jobs: Map<String, Job> = emptyMap(),
    val resources: Map<String, ResourceCategory> = emptyMap(),
)

@Immutable
@Serializable
data class SkillDone(
    val st: String = "none",
    val note: String = "",
    val updated: Long = 0,
    val startDate: String = "",
    val goalDate: String = "",
)

@Immutable
@Serializable
data class JobState(val st: String = "none", val updated: Long = 0)

@Serializable
data class ProgressEntry(
    val jobId: String = "",
    val mastery: Int = 0,
    val done: Boolean = false,
    val note: String = "",
    /** 旧版备份兼容字段（导入时迁移为 jobId，导出时不写出） */
    val dirId: String = "",
)

/** 合并后的有效数据（UI 只读这一层） */
@Immutable
data class EffectiveData(
    val version: String,
    val skills: Map<String, Skill> = emptyMap(),
    val paths: List<LearnPath> = emptyList(),
    val jobs: Map<String, Job> = emptyMap(),
    val resources: Map<String, ResourceCategory> = emptyMap(),
)

/** 与网页版对齐的常量（资源分类标题 / 旧版 dirId 迁移 / id 白名单） */
object KbConstants {
    val CAT_TITLES = linkedMapOf(
        "videos" to "视频课程（B站等）",
        "platforms" to "学习平台",
        "tools" to "在线工具与开发环境",
        "community" to "社区与文档",
        "open" to "开源项目（练手与源码）",
        "books" to "推荐书籍",
    )

    val DIR2JOB = mapOf(
        "mcu" to "job-mcu", "rtos" to "job-rtos", "linux-app" to "job-linuxapp",
        "linux-drv" to "job-linuxdrv", "iot" to "job-iot", "ai" to "job-ai",
        "fpga" to "job-fpga", "proto" to "job-proto",
    )

    /** 网页版 v9.18 的 id 白名单：防 HTML/选择器注入 */
    val VALID_ID = Regex("^[A-Za-z0-9_-]+$")
}
