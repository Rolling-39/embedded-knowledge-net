package com.rolling.learningguider.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rolling.learningguider.ui.AppViewModel
import com.rolling.learningguider.ui.UiState
import com.rolling.learningguider.ui.icons.AppIcons
import kotlinx.coroutines.launch

/** 知识库：技能 / 岗位 / 路径 / 资源 四分页 + 搜索 + 详情弹层 */
@Composable
fun LibraryScreen(vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableStateOf(0) }

    // 弹层状态：当前打开的技能 / 岗位 / 路径
    var sheetSkill by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetJob by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetPath by rememberSaveable { mutableStateOf<String?>(null) }
    // 表单状态：null=关闭 / ""=新增 / 其他=编辑对应 id
    var formSkill by rememberSaveable { mutableStateOf<String?>(null) }
    var formJob by rememberSaveable { mutableStateOf<String?>(null) }
    var formPath by rememberSaveable { mutableStateOf<String?>(null) }
    var formRes by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = page) {
            listOf("技能", "岗位", "路径", "资源").forEachIndexed { i, title ->
                Tab(
                    selected = page == i,
                    onClick = { page = i },
                    text = { Text(title) },
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("搜索${listOf("技能", "岗位", "路径", "资源").getOrElse(page) { "技能" }}…", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(AppIcons.Close, contentDescription = "清空") }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
        )
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                (fadeIn(tween(200)) + slideInHorizontally(tween(220)) { if (targetState > initialState) it / 12 else -it / 12 })
                    .togetherWith(fadeOut(tween(150)))
            },
            label = "libPages",
            modifier = Modifier.fillMaxSize(),
        ) { p ->
            when (p) {
                0 -> SkillListPage(ui, query, onSkillClick = { sheetSkill = it }, onRestore = vm::restoreRecord, onAdd = { formSkill = "" }, onQuickStatus = vm::cycleSkillStatus)
                1 -> JobListPage(ui, query, onJobClick = { sheetJob = it }, onRestore = vm::restoreRecord, onAdd = { formJob = "" })
                2 -> PathListPage(ui, query, onPathClick = { sheetPath = it }, onSkillClick = { sheetSkill = it }, onRestore = vm::restoreRecord, onAdd = { formPath = "" })
                else -> ResourceListPage(ui, query, onDeleteResource = { vm.deleteResource(it) }, onAdd = { formRes = true })
            }
        }
    }

    sheetSkill?.let { id ->
        if (ui.effective.skills.containsKey(id)) {
            SkillDetailSheet(
                skillId = id,
                ui = ui,
                onOpenSkill = { sheetSkill = it; sheetJob = null; sheetPath = null },
                onDelete = { vm.deleteRecord("skill", id); sheetSkill = null },
                onDismiss = { sheetSkill = null },
                onSetStatus = { st -> vm.setSkillStatus(id, st) },
                onNoteChange = { vm.setSkillNote(id, it) },
                onStartDate = { vm.setSkillStartDate(id, it) },
                onGoalDate = { vm.setSkillGoalDate(id, it) },
                onEdit = { formSkill = id },
            )
        } else sheetSkill = null
    }
    sheetJob?.let { id ->
        if (ui.effective.jobs.containsKey(id)) {
            JobDetailSheet(
                jobId = id,
                ui = ui,
                onOpenSkill = { sheetSkill = it; sheetJob = null },
                onDelete = { vm.deleteRecord("job", id); sheetJob = null },
                onDismiss = { sheetJob = null },
                onSetStatus = { st -> vm.setJobStatus(id, st) },
                onNoteChange = { vm.setJobNote(id, it) },
                onEdit = { formJob = id },
            )
        } else sheetJob = null
    }
    sheetPath?.let { id ->
        val path = ui.effective.paths.firstOrNull { it.id == id }
        if (path != null) {
            PathDetailSheet(
                path = path,
                ui = ui,
                onOpenSkill = { sheetSkill = it; sheetPath = null },
                onDismiss = { sheetPath = null },
                onEdit = { formPath = id },
                onDelete = { vm.deleteRecord("path", id); sheetPath = null },
            )
        } else sheetPath = null
    }

    // ===== 表单挂载 =====
    if (formSkill != null) {
        val initial = formSkill?.takeIf { it.isNotBlank() }?.let { ui.effective.skills[it] }
        SkillFormSheet(
            initial = initial,
            ui = ui,
            onSave = { vm.saveSkill(formSkill?.takeIf { it.isNotBlank() }, it); formSkill = null; scope.launch { snackbar.showSnackbar("技能已保存") } },
            onDismiss = { formSkill = null },
        )
    }
    if (formJob != null) {
        val initial = formJob?.takeIf { it.isNotBlank() }?.let { ui.effective.jobs[it] }
        JobFormSheet(
            initial = initial,
            onSave = { vm.saveJob(formJob?.takeIf { it.isNotBlank() }, it); formJob = null; scope.launch { snackbar.showSnackbar("岗位已保存") } },
            onDismiss = { formJob = null },
        )
    }
    if (formPath != null) {
        val initial = formPath?.takeIf { it.isNotBlank() }?.let { pid -> ui.effective.paths.firstOrNull { it.id == pid } }
        PathFormSheet(
            initial = initial,
            ui = ui,
            onSave = { vm.savePath(formPath?.takeIf { it.isNotBlank() }, it); formPath = null; scope.launch { snackbar.showSnackbar("路线已保存") } },
            onDismiss = { formPath = null },
        )
    }
    if (formRes) {
        ResourceFormSheet(
            initial = null,
            onSave = { vm.addResource(it); formRes = false; scope.launch { snackbar.showSnackbar("资源已添加") } },
            onDismiss = { formRes = false },
        )
    }
    SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

