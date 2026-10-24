@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rolling.learningguider.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rolling.learningguider.data.model.Job
import com.rolling.learningguider.data.model.JobTier
import com.rolling.learningguider.data.model.LearnPath
import com.rolling.learningguider.data.model.ResourceItem
import com.rolling.learningguider.data.model.Skill
import com.rolling.learningguider.ui.UiState
import com.rolling.learningguider.ui.common.InfoLabel
import com.rolling.learningguider.ui.common.catColor
import com.rolling.learningguider.ui.common.pressable
import com.rolling.learningguider.ui.common.statusColor

/* ============== 表单小件 ============== */

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    placeholder: String = "",
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) ({ Text(placeholder) }) else null,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        minLines = minLines,
    )
}

@Composable
private fun StarPicker(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        InfoLabel(label, Modifier.width(72.dp))
        for (i in 1..5) {
            Text(
                "★",
                fontSize = 26.sp,
                color = if (i <= value) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outline,
                modifier = Modifier.pressable { onChange(i) }.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun SingleSelectChips(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        InfoLabel(label)
        Row(Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { opt ->
                val sel = opt == selected
                Surface(
                    color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50),
                    onClick = { onSelect(opt) },
                ) {
                    Text(
                        opt,
                        color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/** 岗位配色板（网页版 PALETTE） */
private val PALETTE = listOf(
    "#2563EB", "#7C3AED", "#0891B2", "#CA8A04", "#DB2777",
    "#4F46E5", "#059669", "#C2410C", "#475569", "#9333EA",
)

@Composable
private fun ColorPicker(selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        InfoLabel("主题色")
        Row(Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PALETTE.forEach { hex ->
                val color = com.rolling.learningguider.ui.common.parseHexColor(hex)
                val sel = hex.equals(selected, ignoreCase = true)
                Surface(
                    color = color,
                    shape = CircleShape,
                    border = if (sel) {
                        androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface)
                    } else null,
                    modifier = Modifier.size(if (sel) 34.dp else 30.dp).pressable { onSelect(hex) },
                ) {}
            }
        }
    }
}

/* ============== 技能选择器（前置多选 / 路径有序多选，可搜索） ============== */

@Composable
fun SkillPicker(
    ui: UiState,
    selected: List<String>,
    onChanged: (List<String>) -> Unit,
    ordered: Boolean = false,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(if (ordered) "搜索并点选加入路线" else "搜索并点选前置") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        if (ordered && selected.isNotEmpty()) {
            Column(Modifier.padding(top = 4.dp)) {
                selected.forEachIndexed { i, sid ->
                    val s = ui.effective.skills[sid] ?: return@forEachIndexed
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(30.dp))
                        Text(s.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { if (i > 0) onChanged(selected.toMutableList().apply { add(i - 1, removeAt(i)) }) }) { Text("↑") }
                        TextButton(onClick = { if (i < selected.size - 1) onChanged(selected.toMutableList().apply { add(i + 1, removeAt(i)) }) }) { Text("↓") }
                        TextButton(onClick = { onChanged(selected - sid) }) { Text("✕") }
                    }
                }
            }
        }
        val q = query.trim()
        val pool = ui.effective.skills.entries
            .filter { q.isEmpty() || it.value.name.contains(q, true) }
            .take(30)
        Row(
            Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            pool.forEach { (id, s) ->
                val inSel = id in selected
                Surface(
                    color = if (inSel) statusColor(if (ordered) "done" else "learning") else catColor(s.cat).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(50),
                    onClick = {
                        onChanged(
                            when {
                                ordered && !inSel -> selected + id
                                ordered -> selected - id
                                inSel -> selected - id
                                else -> selected + id
                            },
                        )
                    },
                ) {
                    Text(
                        s.name,
                        color = if (inSel) Color.White else catColor(s.cat),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
        if (!ordered && selected.isNotEmpty()) {
            InfoLabel("已选：${selected.mapNotNull { ui.effective.skills[it]?.name }.joinToString("、")}", Modifier.padding(top = 6.dp))
        }
    }
}

/* ============== 技能表单 ============== */

@Composable
fun SkillFormSheet(initial: Skill?, ui: UiState, onSave: (Skill) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var cat by rememberSaveable { mutableStateOf(initial?.cat ?: "语言基础") }
    var diff by rememberSaveable { mutableIntStateOf(initial?.diff ?: 3) }
    var content by rememberSaveable { mutableStateOf(initial?.content ?: "") }
    var role by rememberSaveable { mutableStateOf(initial?.role ?: "") }
    var hard by rememberSaveable { mutableStateOf(initial?.hard ?: "") }
    var key by rememberSaveable { mutableStateOf(initial?.key ?: "") }
    var resText by rememberSaveable { mutableStateOf(initial?.res?.joinToString("\n") ?: "") }
    var preIds by rememberSaveable { mutableStateOf(initial?.pre ?: emptyList()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(if (initial == null) "新增技能" else "编辑技能", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Field("名称 *", name, { name = it })
            SingleSelectChips("分类", Skill.CATEGORIES, cat) { cat = it }
            StarPicker("难度", diff) { diff = it }
            Field("学什么", content, { content = it }, minLines = 2)
            Field("为什么学 / 用在哪", role, { role = it }, minLines = 2)
            Field("难点", hard, { hard = it })
            Field("重点", key, { key = it })
            Field("学习资源（每行一条）", resText, { resText = it }, minLines = 2)
            InfoLabel("前置技能", Modifier.padding(top = 8.dp))
            SkillPicker(ui, preIds, { preIds = it })
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    onSave(
                        Skill(
                            name = name.trim(), cat = cat, diff = diff,
                            content = content.trim(), role = role.trim(), hard = hard.trim(), key = key.trim(),
                            res = resText.split('\n').map { it.trim() }.filter { it.isNotEmpty() },
                            pre = preIds, user = true,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        }
    }
}

/* ============== 岗位表单 ============== */

@Composable
fun JobFormSheet(initial: Job?, onSave: (Job) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var level by rememberSaveable { mutableStateOf(initial?.level ?: "进阶") }
    var diff by rememberSaveable { mutableIntStateOf(initial?.diff ?: 3) }
    var demand by rememberSaveable { mutableIntStateOf(initial?.demand ?: 3) }
    var salary by rememberSaveable { mutableStateOf(initial?.salary ?: "¥10-30K") }
    var color by rememberSaveable { mutableStateOf(initial?.color?.takeIf { it.isNotBlank() } ?: PALETTE.first()) }
    var work by rememberSaveable { mutableStateOf(initial?.work ?: "") }
    var review by rememberSaveable { mutableStateOf(initial?.review ?: "") }
    var t1s by rememberSaveable { mutableStateOf(initial?.tiers?.getOrNull(0)?.s ?: "") }
    var t1d by rememberSaveable { mutableStateOf(initial?.tiers?.getOrNull(0)?.d ?: "") }
    var t2s by rememberSaveable { mutableStateOf(initial?.tiers?.getOrNull(1)?.s ?: "") }
    var t2d by rememberSaveable { mutableStateOf(initial?.tiers?.getOrNull(1)?.d ?: "") }
    var t3s by rememberSaveable { mutableStateOf(initial?.tiers?.getOrNull(2)?.s ?: "") }
    var t3d by rememberSaveable { mutableStateOf(initial?.tiers?.getOrNull(2)?.d ?: "") }
    var companies by rememberSaveable { mutableStateOf(initial?.companies?.joinToString("\n") ?: "") }
    var projects by rememberSaveable { mutableStateOf(initial?.projects?.joinToString("\n") ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(if (initial == null) "新增岗位" else "编辑岗位", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Field("名称 *", name, { name = it })
            SingleSelectChips("层级", listOf("基础", "进阶", "高阶"), level) { level = it }
            StarPicker("难度", diff) { diff = it }
            StarPicker("市场需求", demand) { demand = it }
            Field("薪资区间", salary, { salary = it }, placeholder = "¥10-30K")
            ColorPicker(color) { color = it }
            Field("工作内容", work, { work = it }, minLines = 2)
            InfoLabel("分层薪资（可留空）", Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("初级薪资", t1s, { t1s = it }, Modifier.weight(1f))
                Field("初级职责", t1d, { t1d = it }, Modifier.weight(1.6f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("中级薪资", t2s, { t2s = it }, Modifier.weight(1f))
                Field("中级职责", t2d, { t2d = it }, Modifier.weight(1.6f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("高级薪资", t3s, { t3s = it }, Modifier.weight(1f))
                Field("高级职责", t3d, { t3d = it }, Modifier.weight(1.6f))
            }
            Field("行业评价", review, { review = it }, minLines = 2)
            Field("代表企业（每行一个）", companies, { companies = it }, minLines = 2)
            Field("练手项目（每行一个）", projects, { projects = it }, minLines = 2)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    onSave(
                        Job(
                            name = name.trim(), level = level, diff = diff, demand = demand,
                            salary = salary.trim().ifBlank { "面议" }, color = color,
                            tiers = listOf(
                                JobTier("初级", t1s.trim(), t1d.trim()),
                                JobTier("中级", t2s.trim(), t2d.trim()),
                                JobTier("高级", t3s.trim(), t3d.trim()),
                            ),
                            work = work.trim(), review = review.trim(),
                            companies = companies.split('\n').map { it.trim() }.filter { it.isNotEmpty() },
                            projects = projects.split('\n').map { it.trim() }.filter { it.isNotEmpty() },
                            user = true,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        }
    }
}

/* ============== 路径表单 ============== */

@Composable
fun PathFormSheet(initial: LearnPath?, ui: UiState, onSave: (LearnPath) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var jobId by rememberSaveable { mutableStateOf(initial?.job ?: "") }
    var period by rememberSaveable { mutableStateOf(initial?.period ?: "") }
    var skillIds by rememberSaveable { mutableStateOf(initial?.skills ?: emptyList()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(if (initial == null) "新增路线" else "编辑路线", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Field("名称 *", name, { name = it })
            InfoLabel("目标岗位（决定主题色与归属）", Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ui.effective.jobs.entries.forEach { (id, j) ->
                    val sel = id == jobId
                    Surface(
                        color = if (sel) com.rolling.learningguider.ui.common.parseHexColor(j.color) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50),
                        onClick = {
                            jobId = id
                            if (name.isBlank()) name = j.name
                        },
                    ) {
                        Text(
                            j.name,
                            color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
            Field("周期", period, { period = it }, placeholder = "如 3-6个月")
            InfoLabel("技能顺序（共 ${skillIds.size} 项）", Modifier.padding(top = 8.dp))
            SkillPicker(ui, skillIds, { skillIds = it }, ordered = true)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (name.isBlank() || jobId.isBlank()) return@Button
                    val jobColor = ui.effective.jobs[jobId]?.color ?: "#2563EB"
                    onSave(
                        LearnPath(
                            name = name.trim(), job = jobId, period = period.trim(),
                            color = jobColor, skills = skillIds, user = true,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && jobId.isNotBlank(),
            ) { Text("保存") }
        }
    }
}

/* ============== 资源表单 ============== */

@Composable
fun ResourceFormSheet(initial: ResourceItem?, onSave: (ResourceItem) -> Unit, onDismiss: () -> Unit) {
    var n by rememberSaveable { mutableStateOf(initial?.n ?: "") }
    var d by rememberSaveable { mutableStateOf(initial?.d ?: "") }
    var cat by rememberSaveable { mutableStateOf(initial?.cat?.takeIf { it.isNotBlank() } ?: "videos") }
    val cats = listOf("videos", "platforms", "tools", "community", "open", "books")

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(if (initial == null) "新增资源" else "编辑资源", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Field("名称 *", n, { n = it })
            Field("说明", d, { d = it }, minLines = 2)
            InfoLabel("分类", Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cats.forEach { c ->
                    val title = com.rolling.learningguider.data.model.KbConstants.CAT_TITLES[c] ?: c
                    val sel = c == cat
                    Surface(
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50),
                        onClick = { cat = c },
                    ) {
                        Text(
                            title,
                            color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (n.isBlank()) return@Button
                    onSave(ResourceItem(id = initial?.id ?: "", cat = cat, n = n.trim(), d = d.trim()))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = n.isNotBlank(),
            ) { Text("保存") }
        }
    }
}
