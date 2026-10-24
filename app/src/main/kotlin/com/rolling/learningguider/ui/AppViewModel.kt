package com.rolling.learningguider.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolling.learningguider.data.model.EffectiveData
import com.rolling.learningguider.data.model.JobState
import com.rolling.learningguider.data.model.ProgressEntry
import com.rolling.learningguider.data.model.SkillDone
import com.rolling.learningguider.data.repo.KbRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 隐藏项（墓碑）条目：列表底部「已隐藏可恢复」用 */
data class HiddenItem(val id: String, val kind: String, val name: String)

data class UiState(
    val effective: EffectiveData = EffectiveData(""),
    val skillDone: Map<String, SkillDone> = emptyMap(),
    val jobState: Map<String, JobState> = emptyMap(),
    val progress: Map<String, ProgressEntry> = emptyMap(),
    val themeMode: String = "",
    val reduceMotion: Boolean = false,
    val hiddenSkills: List<HiddenItem> = emptyList(),
    val hiddenJobs: List<HiddenItem> = emptyList(),
    val hiddenPaths: List<HiddenItem> = emptyList(),
)

class AppViewModel(private val repo: KbRepository) : ViewModel() {

    val ui: StateFlow<UiState> = combine(
        repo.effective,
        repo.skillDone,
        repo.jobState,
        repo.progressFlow,
        combine(repo.prefs, repo.userRecordsFlow) { prefs, records -> prefs to records },
    ) { effective, skillDone, jobState, progress, prefsAndRecords ->
        val prefs = prefsAndRecords.first
        val records = prefsAndRecords.second
        val hidden: List<HiddenItem> = records.filter { it.deleted }.map { r ->
            val payloadName: String? = runCatching {
                val el = com.rolling.learningguider.data.backup.BackupEngine.json.parseToJsonElement(r.payload)
                (el as? kotlinx.serialization.json.JsonObject)?.get("name")
                    ?.let { n -> (n as? kotlinx.serialization.json.JsonPrimitive)?.content }
            }.getOrNull()
            HiddenItem(r.id, r.kind, payloadName ?: r.id)
        }
        UiState(
            effective = effective,
            skillDone = skillDone,
            jobState = jobState,
            progress = progress,
            themeMode = prefs.theme,
            reduceMotion = prefs.reduceMotion,
            hiddenSkills = hidden.filter { it.kind == KbRepository.KIND_SKILL },
            hiddenJobs = hidden.filter { it.kind == KbRepository.KIND_JOB },
            hiddenPaths = hidden.filter { it.kind == KbRepository.KIND_PATH },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun setSkillStatus(id: String, st: String) = launch { repo.setSkillStatus(id, st) }
    fun setSkillNote(id: String, note: String) = launch { repo.setSkillNote(id, note) }
    fun setSkillStartDate(id: String, date: String) = launch { repo.setSkillStartDate(id, date) }
    fun setSkillGoalDate(id: String, date: String) = launch { repo.setSkillGoalDate(id, date) }
    fun setJobStatus(id: String, st: String) = launch { repo.setJobStatus(id, st) }
    fun setJobNote(id: String, note: String) = launch { repo.setJobNote(id, note) }
    fun deleteRecord(kind: String, id: String) = launch { repo.deleteRecord(kind, id) }
    fun restoreRecord(id: String) = launch { repo.restoreRecord(id) }
    fun deleteResource(id: String) = launch { repo.deleteResource(id) }
    fun setTheme(mode: String) = launch { repo.setTheme(mode) }
    fun setReduceMotion(on: Boolean) = launch { repo.setReduceMotion(on) }

    fun clearAll() = launch {
        repo.clearAll()
    }

    /** 导出到 SAF 文档，返回结果提示 */
    suspend fun exportTo(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(repo.exportBackup().toByteArray(Charsets.UTF_8))
            } ?: return@withContext "导出失败：无法打开文件"
            "导出成功"
        }.getOrElse { "导出失败：${it.message}" }
    }

    /** 从 SAF 文档导入，返回结果提示（含统计） */
    suspend fun importFrom(context: Context, uri: Uri, merge: Boolean): String = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return@withContext "导入失败：无法读取文件"
            val stats = repo.importBackup(text, merge)
            if (merge) {
                "合并导入成功：新增 技能 ${stats.skills} · 岗位 ${stats.jobs} · 路径 ${stats.paths} · 资源 ${stats.resources}；进度 ${stats.progressCount} 条以导入为准"
            } else {
                "导入成功：进度 ${stats.progressCount} 条"
            }
        }.getOrElse { "导入失败：文件格式不正确" }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
