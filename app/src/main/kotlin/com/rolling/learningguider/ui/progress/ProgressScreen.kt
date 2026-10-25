package com.rolling.learningguider.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rolling.learningguider.data.model.Skill
import com.rolling.learningguider.ui.AppViewModel
import com.rolling.learningguider.ui.common.CategoryProgressRow
import com.rolling.learningguider.ui.common.Dot
import com.rolling.learningguider.ui.common.InfoLabel
import com.rolling.learningguider.ui.common.ProgressRing
import com.rolling.learningguider.ui.common.SectionTitle
import com.rolling.learningguider.ui.common.ThreeColorBar
import com.rolling.learningguider.ui.common.levelColor
import com.rolling.learningguider.ui.common.parseHexColor
import com.rolling.learningguider.ui.common.pressable
import com.rolling.learningguider.ui.common.statusColor
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 我的进度：今日卡片 + 总进度环 + 分类进度 + 岗位掌握度 */
@Composable
fun ProgressScreen(vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    val e = ui.effective

    LazyColumn(Modifier.fillMaxSize()) {
        // 空状态引导
        val hasAnyRecord = ui.skillDone.values.any { it.st != "none" } || ui.jobState.values.any { it.st != "none" }
        if (!hasAnyRecord) {
            item(key = "guide") {
                com.rolling.learningguider.ui.common.KbCard(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    container = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                ) {
                    Text("从这里开始你的学习追踪", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "到「知识网」长按技能节点快速标记，或在「知识库」打开技能详情点「学习中」——你的进度、目标日和岗位掌握度都会显示在这里。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        // 今日卡片
        val learningSkills = e.skills.entries.filter { (ui.skillDone[it.key]?.st) == "learning" }
        val learningJobs = e.jobs.entries.filter { (ui.jobState[it.key]?.st) == "learning" }
        if (learningSkills.isNotEmpty() || learningJobs.isNotEmpty()) {
            item(key = "today") { TodayCard(ui, learningSkills, learningJobs) }
        }
        // 总进度
        item(key = "overall") {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("总进度 · 全部技术栈加权平均", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    ProgressRing(percent = avgMastery(ui), animate = !ui.reduceMotion)
                    Spacer(Modifier.height(10.dp))
                    val done = e.skills.entries.count { (ui.skillDone[it.key]?.st) == "done" }
                    val learning = e.skills.entries.count { (ui.skillDone[it.key]?.st) == "learning" }
                    ThreeColorBar(done, learning, e.skills.size)
                    Row(Modifier.padding(top = 6.dp)) {
                        Dot(com.rolling.learningguider.ui.common.StatusDone, 8.dp)
                        Text("已学 $done", Modifier.padding(start = 4.dp, end = 12.dp), style = MaterialTheme.typography.labelMedium)
                        Dot(com.rolling.learningguider.ui.common.StatusLearning, 8.dp)
                        Text("学习中 $learning", Modifier.padding(start = 4.dp, end = 12.dp), style = MaterialTheme.typography.labelMedium)
                        Dot(MaterialTheme.colorScheme.outline, 8.dp)
                        Text("未学 ${e.skills.size - done - learning}", Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        "已学计 100% · 学习中计 50%（与网页版口径一致）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        // 分类进度
        item(key = "cats") {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("分类掌握度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    val cats = Skill.CATEGORIES + e.skills.values.map { it.cat }.filter { it !in Skill.CATEGORIES }.distinct()
                    cats.forEach { cat ->
                        val list = e.skills.entries.filter { it.value.cat == cat }
                        if (list.isEmpty()) return@forEach
                        CategoryProgressRow(
                            cat,
                            list.count { (ui.skillDone[it.key]?.st) == "done" },
                            list.count { (ui.skillDone[it.key]?.st) == "learning" },
                            list.size,
                        )
                    }
                }
            }
        }
        // 岗位掌握度
        item(key = "jobTitle") { SectionTitle("岗位掌握度", Modifier.padding(horizontal = 16.dp)) }
        e.jobs.values.forEach { j ->
            item(key = "job-${j.id}") {
                JobMasteryCard(ui, j.id, onNoteChange = { vm.setJobNote(j.id, it) })
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TodayCard(
    ui: com.rolling.learningguider.ui.UiState,
    learningSkills: List<Map.Entry<String, com.rolling.learningguider.data.model.Skill>>,
    learningJobs: List<Map.Entry<String, com.rolling.learningguider.data.model.Job>>,
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("正在学习", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            learningSkills.forEach { (id, s) ->
                val d = ui.skillDone[id] ?: com.rolling.learningguider.data.model.SkillDone()
                val days = if (d.startDate.isNotBlank()) {
                    ChronoUnit.DAYS.between(LocalDate.parse(d.startDate), LocalDate.now()).toInt() + 1
                } else if (d.updated > 0) {
                    ((System.currentTimeMillis() - d.updated) / 86_400_000L + 1).toInt()
                } else 1
                val goal = if (d.goalDate.isNotBlank()) {
                    val left = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(d.goalDate)).toInt()
                    when {
                        left > 0 -> " · 距目标 $left 天"
                        left == 0 -> " · 目标今天到期"
                        else -> " · 已逾期 ${-left} 天"
                    }
                } else ""
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(statusColor("learning"), 8.dp)
                    Text(s.name, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp).weight(1f))
                    InfoLabel("已学 ${maxOf(1, days)} 天$goal")
                }
                if (d.note.isNotBlank()) {
                    Text(
                        "“${d.note}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                    )
                }
            }
            learningJobs.forEach { (id, j) ->
                val js = ui.jobState[id]
                val days = if ((js?.updated ?: 0L) > 0) ((System.currentTimeMillis() - js!!.updated) / 86_400_000L + 1).toInt() else 1
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(statusColor("learning"), 8.dp)
                    Text(j.name, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp).weight(1f))
                    InfoLabel("已学习 ${maxOf(1, days)} 天")
                }
            }
        }
    }
}

@Composable
private fun JobMasteryCard(ui: com.rolling.learningguider.ui.UiState, jobId: String, onNoteChange: (String) -> Unit) {
    val job = ui.effective.jobs[jobId] ?: return
    val path = ui.effective.paths.firstOrNull { it.job == jobId }
    var editNote by remember { mutableStateOf(false) }
    var noteText by remember(ui.progress[jobId]?.note) { mutableStateOf(ui.progress[jobId]?.note ?: "") }
    val mastery = path?.let {
        val sum = it.skills.sumOf { sid ->
            when (ui.skillDone[sid]?.st ?: "none") {
                "done" -> 100L
                "learning" -> 50L
                else -> 0L
            }
        }
        if (it.skills.isEmpty()) 0 else (sum / it.skills.size).toInt()
    } ?: 0

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(parseHexColor(job.color), 10.dp)
                Text(job.name, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp).weight(1f))
                Surface(color = levelColor(job.level).copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
                    Text(job.level, color = levelColor(job.level), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("$mastery%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                ThreeColorBar(
                    path?.skills?.count { (ui.skillDone[it]?.st) == "done" } ?: 0,
                    path?.skills?.count { (ui.skillDone[it]?.st) == "learning" } ?: 0,
                    path?.skills?.size ?: 0,
                    Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                InfoLabel("${path?.skills?.count { (ui.skillDone[it]?.st) == "done" } ?: 0}/${path?.skills?.size ?: 0}")
            }
            val note = ui.progress[jobId]?.note ?: ""
            if (note.isNotBlank()) {
                Text(
                    "“$note”",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Text(
                if (note.isBlank()) "添加笔记" else "编辑笔记",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp).pressable { editNote = true },
            )
        }
    }
    if (editNote) {
        AlertDialog(
            onDismissRequest = { editNote = false },
            title = { Text("岗位笔记 · ${job.name}") },
            text = {
                OutlinedTextField(value = noteText, onValueChange = { noteText = it }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            },
            confirmButton = { TextButton(onClick = { onNoteChange(noteText); editNote = false }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { editNote = false }) { Text("取消") } },
        )
    }
}

/** 全部技术栈加权平均（与网页版 avgMastery 同口径：已学100% + 学习中50%） */
private fun avgMastery(ui: com.rolling.learningguider.ui.UiState): Int {
    val skills = ui.effective.skills
    if (skills.isEmpty()) return 0
    val sum = skills.keys.sumOf { id ->
        when (ui.skillDone[id]?.st ?: "none") {
            "done" -> 100L
            "learning" -> 50L
            else -> 0L
        }
    }
    return (sum / skills.size).toInt()
}
