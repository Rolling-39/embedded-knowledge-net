package com.rolling.learningguider.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 网页版导出 JSON 的结构（embed-kb-vX 备份格式）。
 * 用户记录（userSkills/userPaths/userJobs/userRes）刻意保持 JsonElement 原样，
 * 不做类型化往返 —— 网页版合并是 Object.assign 字段级语义，partial payload
 * 一旦经过「解码→重编码」会丢失「字段未提供」信息，破坏覆盖语义。
 */
@Serializable
data class WebBackup(
    val type: String = "",
    val exportedAt: String = "",
    val theme: String = "",
    val reduce: String = "0",
    val animoff: String = "0",
    val progress: List<ProgressEntry> = emptyList(),
    val skillDone: Map<String, JsonElement> = emptyMap(),
    val jobState: Map<String, JsonElement> = emptyMap(),
    val userSkills: Map<String, JsonElement> = emptyMap(),
    val userPaths: Map<String, JsonElement> = emptyMap(),
    val userJobs: Map<String, JsonElement> = emptyMap(),
    val userRes: List<JsonElement> = emptyList(),
)
