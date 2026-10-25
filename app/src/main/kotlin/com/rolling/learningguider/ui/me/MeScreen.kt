package com.rolling.learningguider.ui.me

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rolling.learningguider.ui.AppViewModel
import com.rolling.learningguider.ui.common.InfoLabel
import com.rolling.learningguider.ui.common.pressable
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 我的：主题 / 动画 / 数据互通（导入导出）/ 清空 / 关于 */
@Composable
fun MeScreen(vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmClear1 by remember { mutableStateOf(false) }
    var confirmClear2 by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let { scope.launch { snackbar.showSnackbar(vm.exportTo(context, it)) } }
    }
    val importOverwrite = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { scope.launch { snackbar.showSnackbar(vm.importFrom(context, it, merge = false)) } }
    }
    val importMerge = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { scope.launch { snackbar.showSnackbar(vm.importFrom(context, it, merge = true)) } }
    }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
        SectionCard("外观") {
            androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                listOf("" to "跟随系统", "light" to "浅色", "dark" to "深色").forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = ui.themeMode == mode,
                        onClick = { vm.setTheme(mode) },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index = i, count = 3),
                    ) { Text(label) }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("减少动画", fontWeight = FontWeight.Medium)
                    InfoLabel("关闭节点呼吸与连线流动（系统开「移除动画」时自动生效）")
                }
                Switch(checked = ui.reduceMotion, onCheckedChange = { vm.setReduceMotion(it) })
            }
        }

        SectionCard("数据互通（与网页版 JSON 备份互导）") {
            OutlinedButton(
                onClick = { exportLauncher.launch("嵌入式知识网备份_${LocalDate.now()}.json") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) { Text("导出备份（网页版可导入）") }
            OutlinedButton(
                onClick = { importOverwrite.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) { Text("导入恢复（覆盖本机数据）") }
            OutlinedButton(
                onClick = { importMerge.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) { Text("合并导入（保留本机已有，新增缺失）") }
            InfoLabel(
                "网页版右上角「导出 JSON」的文件可直接导入；本机导出的文件也可在网页版「导入恢复」中使用。",
                Modifier.padding(top = 6.dp),
            )
        }

        SectionCard("危险区") {
            TextButton(onClick = { confirmClear1 = true }, modifier = Modifier.fillMaxWidth()) {
                Text("清空全部数据", color = MaterialTheme.colorScheme.error)
            }
            InfoLabel("只清学习进度与定制，内置内容不受影响")
        }

        SectionCard("关于") {
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
            }
            InfoLabel("嵌入式知识网 v$version · 单机离线 · 数据存本机")
            InfoLabel("内置内容版本：${ui.effective.version.ifEmpty { "加载中…" }}（81 技能 / 16 路径 / 16 岗位）")
                InfoLabel("内容以网页版 Learning_Guider 为单一事实源，随 App 版本更新")
                Text(
                    "© Rolling",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (confirmClear1) {
        AlertDialog(
            onDismissRequest = { confirmClear1 = false },
            title = { Text("清空数据") },
            text = { Text("确定要清空所有学习进度与定制吗？建议先导出备份！") },
            confirmButton = { TextButton(onClick = { confirmClear1 = false; confirmClear2 = true }) { Text("继续", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmClear1 = false }) { Text("取消") } },
        )
    }
    if (confirmClear2) {
        AlertDialog(
            onDismissRequest = { confirmClear2 = false },
            title = { Text("再次确认") },
            text = { Text("所有进度与学习记录将被永久删除，确定继续？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear2 = false
                    vm.clearAll()
                    scope.launch { snackbar.showSnackbar("已清空全部数据") }
                }) { Text("确定清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear2 = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) { content() }
        }
    }
}

@Composable
private fun SettingRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) Text("●", color = MaterialTheme.colorScheme.primary)
    }
}
