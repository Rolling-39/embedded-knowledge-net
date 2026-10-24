@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rolling.learningguider.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rolling.learningguider.data.model.Job
import com.rolling.learningguider.data.model.LearnPath
import com.rolling.learningguider.data.model.ResourceItem
import com.rolling.learningguider.data.model.Skill
import com.rolling.learningguider.ui.HiddenItem
import com.rolling.learningguider.ui.UiState
import com.rolling.learningguider.ui.common.BodyText
import com.rolling.learningguider.ui.common.Dot
import com.rolling.learningguider.ui.common.InfoLabel
import com.rolling.learningguider.ui.common.StarRow
import com.rolling.learningguider.ui.common.parseHexColor
import com.rolling.learningguider.ui.common.pressable
import com.rolling.learningguider.ui.common.statusColor
import com.rolling.learningguider.ui.common.catColor
import com.rolling.learningguider.ui.common.levelColor

/* ============== 技能字典：按分类分组 + 吸顶 + 搜索 ============== */

@Composable
fun SkillListPage(ui: UiState, query: String, onSkillClick: (String) -> Unit, onRestore: (String) -> Unit) {
    val e = ui.effective
    val q = query.trim()
    val cats = Skill.CATEGORIES + e.skills.values.map { it.cat }.filter { it !in Skill.CATEGORIES }.distinct()
    LazyColumn(Modifier.fillMaxSize()) {
        cats.forEach { cat ->
            val list = e.skills.entries.filter { it.value.cat == cat }
            val filtered = if (q.isEmpty()) list else list.filter { (_, s) ->
                s.name.contains(q, true) || s.content.contains(q, true) || s.key.contains(q, true)
            }
            if (filtered.isEmpty()) return@forEach
            stickyHeader(key = "cat-$cat") {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Dot(catColor(cat), 10.dp)
                        Text(
                            cat,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp).weight(1f),
                        )
                        InfoLabel("${filtered.size} 项")
                    }
                }
            }
            items(filtered, key = { it.key }) { (id, s) -> SkillRow(id, s, ui, onSkillClick) }
        }
        if (q.isNotEmpty() && e.skills.values.none { it.name.contains(q, true) || it.content.contains(q, true) || it.key.contains(q, true) }) {
            item { EmptyHint("没有匹配「$q」的技能") }
        }
        item { HiddenRestoreRow(ui.hiddenSkills, "技能", onRestore) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SkillRow(id: String, s: Skill, ui: UiState, onClick: (String) -> Unit) {
    val st = ui.skillDone[id]?.st ?: "none"
    Row(
        Modifier
            .fillMaxWidth()
            .pressable { onClick(id) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(if (st == "none") 10.dp else 14.dp)
                    .background(statusColor(st), CircleShape),
            )
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(s.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "难度 ${"★".repeat(s.diff)}${"☆".repeat(5 - s.diff)} · 前置 ${s.pre.size} 项",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (st == "learning") InfoLabel("学习中")
        else if (st == "done") Text("已学", style = MaterialTheme.typography.labelMedium, color = statusColor(st))
    }
}

/* ============== 岗位字典 ============== */

@Composable
fun JobListPage(ui: UiState, query: String, onJobClick: (String) -> Unit, onRestore: (String) -> Unit) {
    val q = query.trim()
    val jobs = ui.effective.jobs.values
        .filter { q.isEmpty() || it.name.contains(q, true) || it.work.contains(q, true) || it.review.contains(q, true) }
    LazyColumn(Modifier.fillMaxSize()) {
        items(jobs.toList(), key = { it.id }) { j -> JobCard(j, onJobClick) }
        if (jobs.isEmpty()) item { EmptyHint("没有匹配「$q」的岗位") }
        item { HiddenRestoreRow(ui.hiddenJobs, "岗位", onRestore) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun JobCard(j: Job, onClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .pressable { onClick(j.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(j.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(color = levelColor(j.level).copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
                    Text(
                        j.level,
                        color = levelColor(j.level),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(j.salary, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                InfoLabel("需求")
                StarRow(j.demand, starSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }
            BodyText(j.work, Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/* ============== 学习路径 ============== */

@Composable
fun PathListPage(ui: UiState, query: String, onPathClick: (String) -> Unit, onSkillClick: (String) -> Unit, onRestore: (String) -> Unit) {
    val q = query.trim()
    val paths = ui.effective.paths.filter { q.isEmpty() || it.name.contains(q, true) }
    LazyColumn(Modifier.fillMaxSize()) {
        items(paths, key = { it.id }) { p -> PathCard(p, ui, onPathClick, onSkillClick) }
        if (paths.isEmpty()) item { EmptyHint("没有匹配「$q」的路线") }
        item { HiddenRestoreRow(ui.hiddenPaths, "路线", onRestore) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PathCard(p: LearnPath, ui: UiState, onPathClick: (String) -> Unit, onSkillClick: (String) -> Unit) {
    val done = p.skills.count { (ui.skillDone[it]?.st ?: "none") == "done" }
    val learning = p.skills.count { (ui.skillDone[it]?.st ?: "none") == "learning" }
    val job = ui.effective.jobs[p.job]
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .pressable { onPathClick(p.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(parseHexColor(p.color), 10.dp)
                Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp).weight(1f))
                InfoLabel(p.period)
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                ThreeColorBarSafe(done, learning, p.skills.size, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                InfoLabel("$done/${p.skills.size}")
            }
            Row(
                Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                p.skills.forEach { sid ->
                    val s = ui.effective.skills[sid] ?: return@forEach
                    val st = ui.skillDone[sid]?.st ?: "none"
                    Surface(
                        color = statusColor(st).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(50),
                        onClick = { onSkillClick(sid) },
                    ) {
                        Text(
                            s.name,
                            color = statusColor(st),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            if (!job?.projects.isNullOrEmpty()) {
                InfoLabel("练手：${job!!.projects.joinToString(" / ")}", Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun ThreeColorBarSafe(done: Int, learning: Int, total: Int, modifier: Modifier) {
    com.rolling.learningguider.ui.common.ThreeColorBar(done, learning, total, modifier)
}

/* ============== 学习资源 ============== */

@Composable
fun ResourceListPage(ui: UiState, query: String, onDeleteResource: (String) -> Unit) {
    val q = query.trim()
    LazyColumn(Modifier.fillMaxSize()) {
        ui.effective.resources.forEach { (cat, category) ->
            val filtered = category.items.filter { q.isEmpty() || it.n.contains(q, true) || it.d.contains(q, true) }
            if (filtered.isEmpty()) return@forEach
            item(key = "res-$cat") {
                Text(
                    "${category.title}（${filtered.size}）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(filtered, key = { "$cat-${it.id.ifEmpty { it.n }}" }) { r -> ResourceRow(r, onDeleteResource) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ResourceRow(r: ResourceItem, onDeleteResource: (String) -> Unit) {
    var confirmDelete = remember { androidx.compose.runtime.mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(r.n, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                r.d,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (r.id.isNotBlank()) {
            Text(
                "删除",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .pressable { confirmDelete.value = true }
                    .padding(8.dp),
            )
            if (confirmDelete.value) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirmDelete.value = false },
                    title = { Text("删除自定义资源") },
                    text = { Text("确定删除「${r.n}」？此操作不可恢复。") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { confirmDelete.value = false; onDeleteResource(r.id) }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete.value = false }) { Text("取消") } },
                )
            }
        }
    }
}

/* ============== 已隐藏可恢复 ============== */

@Composable
fun HiddenRestoreRow(hidden: List<HiddenItem>, label: String, onRestore: (String) -> Unit = {}) {
    if (hidden.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        InfoLabel("已隐藏 ${hidden.size} 个$label（点按恢复）")
        Row(
            Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            hidden.forEach { h ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50),
                    onClick = { onRestore(h.id) },
                ) {
                    Text(
                        "${h.name} ↺",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}
