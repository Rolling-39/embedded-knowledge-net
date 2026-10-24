@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rolling.learningguider.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rolling.learningguider.data.model.LearnPath
import com.rolling.learningguider.data.model.SkillDone
import com.rolling.learningguider.ui.UiState
import com.rolling.learningguider.ui.common.BodyText
import com.rolling.learningguider.ui.common.Dot
import com.rolling.learningguider.ui.common.InfoLabel
import com.rolling.learningguider.ui.common.SectionTitle
import com.rolling.learningguider.ui.common.StarRow
import com.rolling.learningguider.ui.common.TagChip
import com.rolling.learningguider.ui.common.ThreeColorBar
import com.rolling.learningguider.ui.common.catColor
import com.rolling.learningguider.ui.common.levelColor
import com.rolling.learningguider.ui.common.parseHexColor
import com.rolling.learningguider.ui.common.pressable
import com.rolling.learningguider.ui.common.statusColor
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/* ============== 通用小件 ============== */

@Composable
fun StatusButtons(current: String, onSet: (String) -> Unit, labels: Triple<String, String, String> = Triple("未学", "学习中", "已学")) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("none" to labels.first, "learning" to labels.second, "done" to labels.third).forEach { (st, label) ->
            val selected = current == st
            val color = statusColor(st)
            Surface(
                color = if (selected) color else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50),
                onClick = { onSet(st) },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    label,
                    color = if (selected) Color.White else color,
                    textAlign = TextAlign.Center,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
fun DatePickerField(label: String, value: String, onPick: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .pressable { show = true }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InfoLabel(label, Modifier.weight(1f))
        Text(
            value.ifBlank { "未设置" },
            style = MaterialTheme.typography.bodyMedium,
            color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
    if (show) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value.takeIf { it.isNotBlank() }?.let {
                runCatching { LocalDate.parse(it).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
            },
        )
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(it / 86_400_000L).toString()) }
                    show = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("取消") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
fun DebouncedNoteField(initial: String, onChanged: (String) -> Unit, label: String = "学习笔记") {
    var text by remember(initial) { mutableStateOf(initial) }
    LaunchedEffect(text) {
        if (text != initial) {
            delay(600)
            onChanged(text)
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )
}

@Composable
private fun SheetSection(title: String, body: String) {
    if (body.isBlank()) return
    SectionTitle(title)
    BodyText(body)
}

/* ============== 技能详情 ============== */

@Composable
fun SkillDetailSheet(
    skillId: String,
    ui: UiState,
    onOpenSkill: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onSetStatus: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onStartDate: (String) -> Unit,
    onGoalDate: (String) -> Unit,
    onFocusNet: (() -> Unit)? = null,
) {
    val skill = ui.effective.skills[skillId] ?: return
    val done = ui.skillDone[skillId] ?: SkillDone()
    val relatedJobs = ui.effective.paths.filter { skillId in it.skills }.mapNotNull { ui.effective.jobs[it.job] }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TagChip(skill.cat, catColor(skill.cat))
                Spacer(Modifier.width(10.dp))
                Text(skill.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                InfoLabel("难度")
                StarRow(skill.diff, starSize = 16.sp)
            }
            StatusButtons(done.st, onSetStatus)
            Spacer(Modifier.height(8.dp))

            // 日期与倒计时
            DatePickerField("开始日期", done.startDate, onStartDate)
            DatePickerField("目标完成日", done.goalDate, onGoalDate)
            val daysInfo = buildString {
                if (done.st != "none") {
                    val days = if (done.startDate.isNotBlank()) {
                        ChronoUnit.DAYS.between(LocalDate.parse(done.startDate), LocalDate.now()).toInt() + 1
                    } else if (done.updated > 0) {
                        ((System.currentTimeMillis() - done.updated) / 86_400_000L + 1).toInt()
                    } else 1
                    append("已学 ${maxOf(1, days)} 天")
                    if (done.goalDate.isNotBlank()) {
                        val left = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(done.goalDate)).toInt()
                        append(" · ")
                        when {
                            left > 0 -> append("距目标 $left 天")
                            left == 0 -> append("目标今天到期")
                            else -> append("目标已逾期 ${-left} 天")
                        }
                    }
                }
            }
            if (daysInfo.isNotEmpty()) {
                InfoLabel(daysInfo, Modifier.padding(vertical = 4.dp))
            }

            DebouncedNoteField(done.note, onNoteChange)
            Spacer(Modifier.height(8.dp))

            SheetSection("学什么", skill.content)
            SheetSection("为什么学 / 用在哪", skill.role)
            SheetSection("难点", skill.hard)
            SheetSection("重点", skill.key)

            if (skill.res.isNotEmpty()) {
                SectionTitle("学习资源")
                FlowChips(skill.res.map { it to null }, MaterialTheme.colorScheme.tertiary)
            }
            val validPre = skill.pre.mapNotNull { pre -> ui.effective.skills[pre]?.let { pre to it } }
            if (validPre.isNotEmpty()) {
                SectionTitle("前置技能")
                FlowChips(validPre.map { (id, s) -> s.name to id }, MaterialTheme.colorScheme.primary, onOpenSkill)
            }
            val dependents = ui.effective.skills.entries.filter { skillId in it.value.pre }
            if (dependents.isNotEmpty()) {
                SectionTitle("被依赖技能")
                FlowChips(dependents.map { it.value.name to it.key }, Color(0xFF0EA5E9), onOpenSkill)
            }
            if (relatedJobs.isNotEmpty()) {
                SectionTitle("出现岗位")
                FlowChips(relatedJobs.map { it.name to null }, Color(0xFF8B5CF6))
            }

            if (onFocusNet != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onFocusNet, modifier = Modifier.fillMaxWidth()) { Text("在知识网中聚焦") }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("隐藏该技能（可恢复）", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("隐藏技能") },
            text = { Text("隐藏「${skill.name}」？它将从列表和所有路径中消失，可在列表底部恢复。") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("隐藏", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

/** 横向滚动 chips：items 为 (显示文本, 可点击的 id)；id 为 null 时纯展示 */
@Composable
fun FlowChips(items: List<Pair<String, String?>>, color: Color, onIdClick: ((String) -> Unit)? = null) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { (text, id) ->
            TagChip(
                text, color,
                onClick = if (id != null && onIdClick != null) ({ onIdClick(id) }) else null,
            )
        }
    }
}

/* ============== 岗位详情 ============== */

@Composable
fun JobDetailSheet(
    jobId: String,
    ui: UiState,
    onOpenSkill: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onSetStatus: (String) -> Unit,
    onNoteChange: (String) -> Unit,
) {
    val job = ui.effective.jobs[jobId] ?: return
    val state = ui.jobState[jobId]
    val path = ui.effective.paths.firstOrNull { it.job == jobId }
    val note = ui.progress[jobId]?.note ?: ""
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(job.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TagChip(job.level, levelColor(job.level))
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(job.salary, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                InfoLabel("市场需求")
                StarRow(job.demand, color = MaterialTheme.colorScheme.primary)
            }
            StatusButtons(state?.st ?: "none", onSetStatus, Triple("未学", "正在学习", "已掌握"))
            Spacer(Modifier.height(8.dp))
            DebouncedNoteField(note, onNoteChange, "岗位笔记")

            SheetSection("工作内容", job.work)
            if (job.tiers.isNotEmpty()) {
                SectionTitle("分层薪资")
                job.tiers.forEach { t ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TagChip(t.t, MaterialTheme.colorScheme.primary)
                        Text(t.s, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp).width(90.dp))
                        Text(t.d, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (job.companies.isNotEmpty()) {
                SectionTitle("代表企业")
                FlowChips(job.companies.map { it to null }, Color(0xFF0EA5E9))
            }
            SheetSection("行业评价", job.review)
            if (job.projects.isNotEmpty()) {
                SectionTitle("推荐练手项目")
                FlowChips(job.projects.map { it to null }, Color(0xFF10B981))
            }
            if (path != null && path.skills.isNotEmpty()) {
                SectionTitle("路线技能（${path.period}）")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    path.skills.forEachIndexed { i, sid ->
                        val s = ui.effective.skills[sid] ?: return@forEachIndexed
                        val st = ui.skillDone[sid]?.st ?: "none"
                        TagChip("${i + 1}.${s.name}", statusColor(st)) { onOpenSkill(sid) }
                    }
                }
                val doneCnt = path.skills.count { (ui.skillDone[it]?.st ?: "none") == "done" }
                ThreeColorBar(
                    doneCnt,
                    path.skills.count { (ui.skillDone[it]?.st ?: "none") == "learning" },
                    path.skills.size,
                    Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("隐藏该岗位（可恢复）", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("隐藏岗位") },
            text = { Text("隐藏「${job.name}」？其学习路线也会一并隐藏，可在岗位列表底部恢复。") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("隐藏", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

/* ============== 路线详情 ============== */

@Composable
fun PathDetailSheet(path: LearnPath, ui: UiState, onOpenSkill: (String) -> Unit, onDismiss: () -> Unit) {
    val job = ui.effective.jobs[path.job]
    val doneCnt = path.skills.count { (ui.skillDone[it]?.st ?: "none") == "done" }
    val learningCnt = path.skills.count { (ui.skillDone[it]?.st ?: "none") == "learning" }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(parseHexColor(path.color), 12.dp)
                Text(path.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp).weight(1f))
                InfoLabel(path.period)
            }
            if (job != null) InfoLabel("目标岗位：${job.name}", Modifier.padding(top = 4.dp))
            ThreeColorBar(doneCnt, learningCnt, path.skills.size, Modifier.padding(top = 10.dp))
            InfoLabel("已学 $doneCnt · 学习中 $learningCnt · 共 ${path.skills.size} 项", Modifier.padding(top = 4.dp))
            SectionTitle("学习顺序")
            path.skills.forEachIndexed { i, sid ->
                val s = ui.effective.skills[sid] ?: return@forEachIndexed
                val st = ui.skillDone[sid]?.st ?: "none"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable { onOpenSkill(sid) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${i + 1}",
                        fontWeight = FontWeight.Bold,
                        color = parseHexColor(path.color),
                        modifier = Modifier.width(28.dp),
                    )
                    Dot(statusColor(st), 10.dp)
                    Text(s.name, modifier = Modifier.padding(start = 10.dp).weight(1f), fontWeight = FontWeight.Medium)
                    Text(
                        when (st) { "done" -> "已学"; "learning" -> "学习中"; else -> "" },
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor(st),
                    )
                }
                if (i < path.skills.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }
            if (!job?.projects.isNullOrEmpty()) {
                SectionTitle("推荐练手项目")
                FlowChips(job!!.projects.map { it to null }, Color(0xFF10B981))
            }
        }
    }
}
