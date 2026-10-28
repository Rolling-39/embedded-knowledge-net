@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rolling.learningguider.ui.net

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rolling.learningguider.data.model.EffectiveData
import com.rolling.learningguider.data.model.JobState
import com.rolling.learningguider.data.model.SkillDone
import com.rolling.learningguider.ui.AppViewModel
import com.rolling.learningguider.ui.UiState
import com.rolling.learningguider.ui.common.Dot
import com.rolling.learningguider.ui.common.parseHexColor
import com.rolling.learningguider.ui.icons.AppIcons
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * ============================================================================
 * 知识网 · 手机端单泳道纵向路线
 * ----------------------------------------------------------------------------
 * 坐标系：内容单位 = dp @ scale1（构建时统一乘 density），
 *         因此 scale = 1 时 14sp 文字上屏就是 14sp —— 不再被二次放大。
 * 布局：一条学习路径 = 一条竖直泳道；左侧轨道 + 右侧文字块，
 *       文字块宽度受泳道约束，超长省略，杜绝跨泳道重叠。
 * 渲染：静态层（节点/文字/轨道）+ 动效层（流光/光晕/涟漪）双层 Canvas，
 *       静态层只在相机或数据变化时重绘，动效层每帧只画极少图元。
 * 相机：手势结束 120ms 后自动吸附到最近泳道 + 缩放档位（0.5 / 1.0 / 1.7），
 *       带速度惯性，越界回弹。
 * ============================================================================
 */

private object Spec {
    const val LANE_W = 300f      // 泳道宽（dp）
    const val LANE_GAP = 26f
    const val PAD_L = 16f
    const val RAIL_X = 44f       // 轨道相对泳道左边
    const val NODE_R = 15f
    const val LABEL_X = 72f      // 文字块相对泳道左边
    const val HEADER_H = 24f     // 泳道顶部留白
    const val ROW_H = 72f        // 节点行距
    const val JOB_W = 232f
    const val JOB_H = 66f
    const val PAD_BOTTOM = 44f   // 内容底部留白
    const val MIN_SCALE = 0.5f
    const val MAX_SCALE = 2.2f
}

/** dp → px 的几何换算器（内容单位即 px@scale1） */
private class Geo(val d: Float) {
    val laneW = Spec.LANE_W * d
    val laneGap = Spec.LANE_GAP * d
    val laneStride = laneW + laneGap
    val padL = Spec.PAD_L * d
    val railDX = Spec.RAIL_X * d
    val nodeR = Spec.NODE_R * d
    val labelDX = Spec.LABEL_X * d
    val labelMaxW = (Spec.LANE_W - Spec.LABEL_X - 14f) * d
    val headerH = Spec.HEADER_H * d
    val rowH = Spec.ROW_H * d
    val jobW = Spec.JOB_W * d
    val jobH = Spec.JOB_H * d
    val padBottom = Spec.PAD_BOTTOM * d

    fun laneLeft(i: Int) = padL + i * laneStride
    fun laneCenter(i: Int) = laneLeft(i) + laneW / 2f
}

/* ============== 渲染模型（预计算，绘制循环里不再查表 / 拼接字符串） ============== */

private class NodeText(
    val one: TextLayoutResult,
    val two: TextLayoutResult,
    val meta: TextLayoutResult,
)

private class NetNode(
    val skillId: String,
    val lane: Int,
    val order: Int,
    val cx: Float,
    val cy: Float,
    val fill: Color,
    val text: NodeText,
    val st: String,
)

private class NetJob(
    val jobId: String,
    val cx: Float,
    val cy: Float,
    val fill: Color,
    val title: TextLayoutResult,
)

private class NetLane(
    val pathId: String,
    val index: Int,
    val left: Float,
    val railX: Float,
    val color: Color,
    val nodes: List<NetNode>,
    val job: NetJob?,
    val total: Int,
    val done: Int,
    val learning: Int,
    val railTop: Float,
    val railBottom: Float,
    /** 本泳道内容底部（岗位卡下沿 + 留白），纵向滚动按泳道各自收口 */
    val contentBottom: Float,
)

private class NetModel(
    val lanes: List<NetLane>,
    val bySkill: Map<String, List<NetNode>>,
    val contentW: Float,
    val contentH: Float,
) {
    companion object {
        val Empty = NetModel(emptyList(), emptyMap(), 0f, 0f)
    }
}

private fun buildModel(
    e: EffectiveData,
    skillDone: Map<String, SkillDone>,
    geo: Geo,
    texts: Map<String, NodeText>,
    jobTexts: Map<String, TextLayoutResult>,
): NetModel {
    if (e.paths.isEmpty()) return NetModel.Empty
    val lanes = ArrayList<NetLane>(e.paths.size)
    val bySkill = HashMap<String, MutableList<NetNode>>()
    var maxRight = 0f
    var maxBottom = 0f

    e.paths.forEachIndexed { i, p ->
        val left = geo.laneLeft(i)
        val railX = left + geo.railDX
        val nodes = ArrayList<NetNode>(p.skills.size)
        var done = 0
        var learning = 0
        p.skills.forEachIndexed { j, sid ->
            val s = e.skills[sid]
            val t = texts[sid]
            if (s != null && t != null) {
                val cy = geo.headerH + geo.rowH * 0.5f + j * geo.rowH
                val st = skillDone[sid]?.st ?: "none"
                if (st == "done") done++ else if (st == "learning") learning++
                val n = NetNode(sid, i, j, railX, cy, catColorOf(s.cat), t, st)
                nodes += n
                bySkill.getOrPut(sid) { ArrayList(2) } += n
            }
        }
        val lastCy = nodes.lastOrNull()?.cy ?: (geo.headerH + geo.rowH * 0.5f)
        val jobCy = lastCy + geo.rowH * 0.5f + 34f * geo.d + geo.jobH / 2f
        val job = e.jobs[p.job]?.let { jb ->
            jobTexts[p.job]?.let { tl ->
                NetJob(p.job, left + geo.laneW / 2f, jobCy, parseHexColor(jb.color, catColorOf("硬件")), tl)
            }
        }
        val railTop = if (nodes.isEmpty()) geo.headerH else nodes.first().cy - geo.rowH * 0.42f
        val railBottom = if (nodes.isEmpty()) railTop else nodes.last().cy

        val laneBottom = jobCy + geo.jobH / 2f + geo.padBottom
        lanes += NetLane(p.id, i, left, railX, parseHexColor(p.color), nodes, job, nodes.size, done, learning, railTop, railBottom, laneBottom)
        maxRight = maxOf(maxRight, left + geo.laneW)
        maxBottom = maxOf(maxBottom, jobCy + geo.jobH / 2f)
    }
    return NetModel(
        lanes = lanes,
        bySkill = bySkill,
        contentW = maxRight + geo.padL,
        contentH = maxBottom + geo.padBottom,
    )
}

/* ============== 知识网主屏 ============== */

@Composable
fun NetScreen(vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var focusSkill by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetSkill by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetJob by rememberSaveable { mutableStateOf<String?>(null) }
    var skillFormId by rememberSaveable { mutableStateOf<String?>(null) }
    var laneInView by rememberSaveable { mutableStateOf(0) }
    var mode by rememberSaveable { mutableStateOf("route") }   // route 路线 / graph 网图
    var catFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var jumpLane by remember { mutableIntStateOf(0) }
    var jumpTick by remember { mutableIntStateOf(0) }
    var resetTick by remember { mutableIntStateOf(0) }
    val chipState = rememberLazyListState()
    val context = LocalContext.current

    val systemMotionOff = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val motionOn = !ui.reduceMotion && !systemMotionOff

    val paths = ui.effective.paths
    val currentLane = laneInView.coerceIn(0, maxOf(0, paths.size - 1))
    val currentPath = paths.getOrNull(currentLane)

    fun jumpTo(lane: Int) {
        jumpLane = lane
        jumpTick++
    }

    Column(Modifier.fillMaxSize()) {
        // ---- 模式切换 + 路径/分类 ----
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeToggle(mode = mode, onChange = { mode = it })
            LazyRow(
                state = chipState,
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 8.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (mode == "route") {
                    items(paths.size) { i ->
                        val p = paths[i]
                        FilterChipCompact(
                            selected = i == currentLane,
                            color = parseHexColor(p.color),
                            label = p.name,
                            onClick = {
                                focusSkill = null
                                jumpTo(i)
                            },
                        )
                    }
                } else {
                    item {
                        FilterChipCompact(
                            selected = catFilter == null,
                            color = MaterialTheme.colorScheme.primary,
                            label = "全部",
                            onClick = { catFilter = null },
                        )
                    }
                    items(com.rolling.learningguider.data.model.Skill.CATEGORIES.size) { i ->
                        val cat = com.rolling.learningguider.data.model.Skill.CATEGORIES[i]
                        FilterChipCompact(
                            selected = catFilter == cat,
                            color = graphCatColorPublic(cat),
                            label = cat,
                            onClick = { catFilter = if (catFilter == cat) null else cat },
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 2.dp),
            placeholder = { Text("搜索技能", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(AppIcons.Close, contentDescription = "清空", modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = MaterialTheme.shapes.extraLarge,
        )

        // ---- HUD：路线显示路径信息，网图显示规模与提示 ----
        if (mode == "graph") {
            GraphHud(
                skillCount = ui.effective.skills.size,
                jobCount = ui.effective.jobs.size,
                pathCount = paths.size,
                onReset = { resetTick++ },
            )
        } else PathHud(
            name = currentPath?.name ?: "暂未加载",
            period = currentPath?.period.orEmpty(),
            color = currentPath?.let { parseHexColor(it.color) } ?: MaterialTheme.colorScheme.primary,
            done = currentPath?.let { p -> p.skills.count { ui.skillDone[it]?.st == "done" } } ?: 0,
            learning = currentPath?.let { p -> p.skills.count { ui.skillDone[it]?.st == "learning" } } ?: 0,
            total = currentPath?.skills?.size ?: 0,
            onReset = { resetTick++ },
        )

        if (mode == "route" && ui.showNetHint && focusSkill == null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f))
                    .padding(start = 14.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "点节点看详情 · 长按快标 · 左右滑动换路径",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.dismissNetHint() }) { Text("知道了", style = MaterialTheme.typography.labelSmall) }
            }
        }
        if (mode == "route" && focusSkill != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .padding(start = 14.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "聚焦：${ui.effective.skills[focusSkill]?.name ?: ""}（紫=前置 · 青=被依赖）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { focusSkill = null }) { Text("取消", style = MaterialTheme.typography.labelSmall) }
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (mode == "graph") {
                GraphCanvas(
                    ui = ui,
                    query = query,
                    catFilter = catFilter,
                    resetTick = resetTick,
                    onSkillTap = { sheetSkill = it },
                    onJobOpen = { pathIndex ->
                        if (pathIndex in paths.indices) {
                            mode = "route"
                            focusSkill = null
                            jumpTo(pathIndex)
                        }
                    },
                    onSkillLongPress = vm::cycleSkillStatus,
                )
            } else NetCanvas(
                ui = ui,
                focusSkillId = focusSkill,
                query = query,
                motionOn = motionOn,
                jumpLane = jumpLane,
                jumpTick = jumpTick,
                resetTick = resetTick,
                onLaneChanged = { laneInView = it },
                onSkillTap = { sheetSkill = it },
                onJobTap = { sheetJob = it },
                onSkillLongPress = vm::cycleSkillStatus,
            )

        }

        // 底部：路线=覆盖率总览条；网图=图例
        if (mode == "graph") {
            GraphLegend()
        } else {
            OverviewBar(
                paths = paths,
                doneOf = { p -> p.skills.count { ui.skillDone[it]?.st == "done" } },
                learningOf = { p -> p.skills.count { ui.skillDone[it]?.st == "learning" } },
                current = currentLane,
                onPick = { jumpTo(it); focusSkill = null },
            )
        }
    }

    // 切换路径时把 chip 滚进视野
    LaunchedEffect(currentLane) {
        if (paths.isNotEmpty()) runCatching { chipState.animateScrollToItem(currentLane) }
    }

    sheetSkill?.let { id ->
        if (ui.effective.skills.containsKey(id)) {
            com.rolling.learningguider.ui.library.SkillDetailSheet(
                skillId = id,
                ui = ui,
                onOpenSkill = { sheetSkill = it },
                onDelete = { vm.deleteRecord("skill", id); sheetSkill = null },
                onDismiss = { sheetSkill = null },
                onSetStatus = { st -> vm.setSkillStatus(id, st) },
                onNoteChange = { vm.setSkillNote(id, it) },
                onStartDate = { vm.setSkillStartDate(id, it) },
                onGoalDate = { vm.setSkillGoalDate(id, it) },
                onFocusNet = { focusSkill = id; sheetSkill = null },
                onEdit = { skillFormId = id; sheetSkill = null },
            )
        } else sheetSkill = null
    }
    sheetJob?.let { id ->
        if (ui.effective.jobs.containsKey(id)) {
            com.rolling.learningguider.ui.library.JobDetailSheet(
                jobId = id,
                ui = ui,
                onOpenSkill = { sheetSkill = it; sheetJob = null },
                onDelete = { vm.deleteRecord("job", id); sheetJob = null },
                onDismiss = { sheetJob = null },
                onSetStatus = { st -> vm.setJobStatus(id, st) },
                onNoteChange = { vm.setJobNote(id, it) },
                onEdit = null,
            )
        } else sheetJob = null
    }
    if (skillFormId != null) {
        val initial = skillFormId?.takeIf { it.isNotBlank() }?.let { ui.effective.skills[it] }
        com.rolling.learningguider.ui.library.SkillFormSheet(
            initial = initial,
            ui = ui,
            onSave = { vm.saveSkill(skillFormId?.takeIf { it.isNotBlank() }, it); skillFormId = null },
            onDismiss = { skillFormId = null },
        )
    }
}

@Composable
private fun FilterChipCompact(
    selected: Boolean,
    color: Color,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) color.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        ),
        modifier = Modifier.height(34.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(color, 7.dp)
            Text(
                label,
                Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PathHud(
    name: String,
    period: String,
    color: Color,
    done: Int,
    learning: Int,
    total: Int,
    onReset: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(period.ifBlank { "周期未标注" })
                    append(" · ")
                    append("已完成 $done")
                    if (learning > 0) append(" · 学习中 $learning")
                    append(" / 共 $total")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Canvas(Modifier.width(46.dp).height(6.dp)) {
            val r = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(color = color.copy(alpha = 0.18f), cornerRadius = r)
            val f = if (total == 0) 0f else (done + learning * 0.5f) / total
            if (f > 0f) {
                drawRoundRect(color = color, size = Size(size.width * f, size.height), cornerRadius = r)
            }
        }
        TextButton(onClick = onReset, modifier = Modifier.height(36.dp)) {
            Text("复位", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OverviewBar(
    paths: List<com.rolling.learningguider.data.model.LearnPath>,
    doneOf: (com.rolling.learningguider.data.model.LearnPath) -> Int,
    learningOf: (com.rolling.learningguider.data.model.LearnPath) -> Int,
    current: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (paths.size < 2) return
    val hairline = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    Column(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Canvas(Modifier.fillMaxWidth().height(1.dp)) {
            drawRect(hairline)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            paths.forEachIndexed { i, p ->
                val c = parseHexColor(p.color)
                val total = p.skills.size.coerceAtLeast(1)
                val frac = ((doneOf(p) + learningOf(p) * 0.5f) / total).coerceIn(0f, 1f)
                val isCur = i == current
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onPick(i) },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val trackTop = h * 0.16f
                        drawRoundRect(
                            color = c.copy(alpha = if (isCur) 0.20f else 0.12f),
                            topLeft = Offset(0f, trackTop),
                            size = Size(w, h - trackTop),
                            cornerRadius = CornerRadius(w / 2f, w / 2f),
                        )
                        val fillH = (h - trackTop) * (0.18f + 0.82f * frac)
                        drawRoundRect(
                            color = c.copy(alpha = if (isCur) 1f else 0.62f),
                            topLeft = Offset(0f, h - fillH),
                            size = Size(w, fillH),
                            cornerRadius = CornerRadius(w / 2f, w / 2f),
                        )

                    }
                }
            }
        }
    }
}

/* ============== 画布 ============== */

@Composable
private fun NetCanvas(
    ui: UiState,
    focusSkillId: String?,
    query: String,
    motionOn: Boolean,
    jumpLane: Int,
    jumpTick: Int,
    resetTick: Int,
    onLaneChanged: (Int) -> Unit,
    onSkillTap: (String) -> Unit,
    onJobTap: (String) -> Unit,
    onSkillLongPress: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val geo = remember(density) { Geo(density) }
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val nameStyle = remember { TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp) }
    val metaStyle = remember { TextStyle(fontSize = 11.sp, lineHeight = 14.sp) }
    val numStyle = remember { TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    val jobStyle = remember { TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold) }
    val badgeStyle = remember { TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold) }

    // ---- 文字测量（数据或泳道宽度变化时才重算）----
    val texts = remember(ui.effective.skills, geo.labelMaxW) {
        val maxW = geo.labelMaxW.toInt().coerceAtLeast(1)
        val c = Constraints(minWidth = 0, maxWidth = maxW)
        ui.effective.skills.mapValues { (_, s) ->
            NodeText(
                one = measurer.measure(AnnotatedString(s.name), nameStyle, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = c),
                two = measurer.measure(AnnotatedString(s.name), nameStyle, overflow = TextOverflow.Ellipsis, maxLines = 2, constraints = c),
                meta = measurer.measure(
                    AnnotatedString(s.cat + " · " + "★".repeat(s.diff.coerceIn(1, 5))),
                    metaStyle, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = c,
                ),
            )
        }
    }
    val jobTexts = remember(ui.effective.jobs, jobStyle) {
        ui.effective.jobs.mapValues { (_, j) -> measurer.measure(AnnotatedString(j.name), jobStyle, maxLines = 1) }
    }
    val numLayouts = remember(numStyle) {
        (1..40).map { measurer.measure(AnnotatedString(it.toString()), numStyle) }
    }
    val badgeLayout = remember(badgeStyle) { measurer.measure(AnnotatedString("✓"), badgeStyle) }
    val jobCaption = remember { measurer.measure(AnnotatedString("目标岗位"), TextStyle(fontSize = 10.sp, letterSpacing = 1.5.sp)) }

    val model = remember(ui.effective, ui.skillDone, geo, texts, jobTexts) {
        buildModel(ui.effective, ui.skillDone, geo, texts, jobTexts)
    }

    // ---- 相机 ----
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var fitted by remember { mutableStateOf(false) }
    var camJob by remember { mutableStateOf<Job?>(null) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var intro by remember { mutableFloatStateOf(0f) }
    var rippleAt by remember { mutableStateOf<Offset?>(null) }
    val ripple = remember { Animatable(0f) }

    val scheme = MaterialTheme.colorScheme
    val colors = remember(scheme.background, scheme.surface, scheme.outline, scheme.onSurface, scheme.onSurfaceVariant) {
        DrawColors(
            bg = scheme.background,
            surface = scheme.surface,
            outline = scheme.outline,
            onSurface = scheme.onSurface,
            onSurfaceVariant = scheme.onSurfaceVariant,
        )
    }

    val laneStride = geo.laneStride
    val laneCount = model.lanes.size
    val currentLane by remember(laneStride, laneCount) {
        derivedStateOf {
            if (canvasSize.width == 0 || model.lanes.isEmpty()) 0
            else {
                val contentX = (canvasSize.width / 2f - offset.x) / scale
                (((contentX - geo.padL - geo.laneW / 2f) / laneStride).roundToInt())
                    .coerceIn(0, model.lanes.size - 1)
            }
        }
    }
    LaunchedEffect(currentLane) { onLaneChanged(currentLane) }

    /** 当前视野中央那条泳道（用于纵向界线与 HUD） */
    fun laneAtX(viewX: Float, sc: Float): Int {
        if (model.lanes.isEmpty()) return 0
        val contentX = (canvasSize.width / 2f - viewX) / sc
        return ((contentX - geo.padL - geo.laneW / 2f) / geo.laneStride)
            .roundToInt().coerceIn(0, model.lanes.size - 1)
    }

    fun clampOffset(o: Offset, sc: Float): Offset {
        if (canvasSize.width == 0) return o
        val margin = 26f * density
        val maxX = geo.padL + margin
        val minX = canvasSize.width - model.contentW * sc - margin
        val x = if (minX > maxX) (canvasSize.width - model.contentW * sc) / 2f else o.x.coerceIn(minX, maxX)
        // 纵向按「当前泳道自身内容高度」收口：短路径不会滑出大片空白
        val laneH = model.lanes.getOrNull(laneAtX(x, sc))?.contentBottom ?: model.contentH
        val maxY = margin
        val minY = canvasSize.height - laneH * sc - margin
        val y = if (minY > maxY) (canvasSize.height - laneH * sc) / 2f else o.y.coerceIn(minY, maxY)
        return Offset(x, y)
    }

    /** 越界橡皮筋：超出边界只跟手 30%，松手回弹 */
    fun softClamp(o: Offset, sc: Float): Offset {
        val hard = clampOffset(o, sc)
        if (hard == o) return o
        return Offset(hard.x + (o.x - hard.x) * 0.3f, hard.y + (o.y - hard.y) * 0.3f)
    }

    /** 只负责跑相机动画，不取消任何 Job（避免「settle 取消自己」导致动画不启动） */
    fun launchCamera(targetScale: Float, targetOffset: Offset, spec: AnimationSpec<Float>) {
        camJob?.cancel()
        val s0 = scale
        val o0 = offset
        val tScale = targetScale
        val tOffset = clampOffset(targetOffset, tScale)
        camJob = scope.launch {
            val t = Animatable(0f)
            t.animateTo(1f, spec) {
                scale = s0 + (tScale - s0) * t.value
                offset = o0 + (tOffset - o0) * t.value
            }
            scale = tScale
            offset = tOffset
        }
    }

    fun animateCamera(
        targetScale: Float,
        targetOffset: Offset,
        spec: AnimationSpec<Float> = spring(0.9f, Spring.StiffnessMediumLow),
    ) {
        settleJob?.cancel()
        launchCamera(targetScale, targetOffset, spec)
    }

    fun centerOnLane(lane: Int, sc: Float): Offset {
        if (canvasSize.width == 0) return offset
        val x = canvasSize.width / 2f - geo.laneCenter(lane) * sc
        return clampOffset(Offset(x, offset.y), sc)
    }

    /** 手势结束：按速度惯性预测 + 吸附到最近泳道与缩放档位 */
    fun settle(vel: Offset) {
        if (canvasSize.width == 0) return
        settleJob?.cancel()
        settleJob = scope.launch {
            delay(110)
            val targetScale = nearestDetent(scale)
            val predictedX = offset.x + vel.x * 0.14f
            val predictedY = offset.y + vel.y * 0.14f
            val centerContentX = (canvasSize.width / 2f - predictedX) / scale
            val idx = ((centerContentX - geo.padL - geo.laneW / 2f) / laneStride)
                .roundToInt().coerceIn(0, maxOf(0, model.lanes.size - 1))
            // 保持屏幕中心的内容点不动做缩放
            val centerContentY = (canvasSize.height / 2f - predictedY) / scale
            val nx = canvasSize.width / 2f - geo.laneCenter(idx) * targetScale
            val ny = canvasSize.height / 2f - centerContentY * targetScale
            launchCamera(targetScale, clampOffset(Offset(nx, ny), targetScale), spring(0.9f, Spring.StiffnessMediumLow))
        }
    }

    // 首次布局：贴合第一条泳道
    LaunchedEffect(fitted, canvasSize) {
        if (!fitted && canvasSize.width > 0 && laneCount > 0) {
            fitted = true
            scale = 1f
            offset = Offset(canvasSize.width / 2f - geo.laneCenter(0), 0f)
        }
    }

    // 缩放复位 / 泳道跳转
    LaunchedEffect(resetTick) {
        if (resetTick > 0 && canvasSize.width > 0 && laneCount > 0) {
            animateCamera(1f, Offset(canvasSize.width / 2f - geo.laneCenter(0), 0f))
        }
    }
    LaunchedEffect(jumpTick) {
        if (jumpTick > 0 && canvasSize.width > 0 && laneCount > 0) {
            val idx = jumpLane.coerceIn(0, laneCount - 1)
            val sc = if (scale < 0.95f) 1f else scale
            animateCamera(sc, centerOnLane(idx, sc))
        }
    }

    // 搜索：防抖后定位第一个匹配节点
    LaunchedEffect(query, fitted, model) {
        val q = query.trim().lowercase()
        if (q.isBlank() || canvasSize.width == 0) return@LaunchedEffect
        delay(220)
        val hit = model.bySkill.entries.firstOrNull { (id, _) ->
            val s = ui.effective.skills[id] ?: return@firstOrNull false
            s.name.lowercase().contains(q) || s.cat.lowercase().contains(q)
        }?.value?.firstOrNull() ?: return@LaunchedEffect
        val sc = if (scale < 0.95f) 1f else scale
        animateCamera(
            sc,
            clampOffset(
                Offset(canvasSize.width / 2f - hit.cx, canvasSize.height / 2f - hit.cy),
                sc,
            ),
        )
    }

    // 聚焦：把焦点节点移到画面中心
    LaunchedEffect(focusSkillId) {
        val id = focusSkillId ?: return@LaunchedEffect
        if (canvasSize.width == 0) return@LaunchedEffect
        val n = model.bySkill[id]?.firstOrNull() ?: return@LaunchedEffect
        val sc = if (scale < 1f) 1f else scale
        animateCamera(
            sc,
            clampOffset(Offset(canvasSize.width / 2f - n.cx, canvasSize.height / 2f - n.cy), sc),
        )
    }

    // 入场：一次性阶梯浮现（尊重系统/应用内「移除动画」）
    LaunchedEffect(motionOn, laneCount) {
        if (!motionOn || laneCount == 0) {
            intro = 1f
            return@LaunchedEffect
        }
        intro = 0f
        Animatable(0f).animateTo(1f, tween(780, easing = FastOutSlowInEasing)) { intro = value }
    }

    // ---- 动效驱动 ----
    val pulse: State<Float> = if (motionOn) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
            label = "pulseA",
        )
    } else remember { mutableFloatStateOf(0f) }

    val flow: State<Float> = if (motionOn) {
        rememberInfiniteTransition(label = "flow").animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(3200, easing = LinearEasing)),
            label = "flowA",
        )
    } else remember { mutableFloatStateOf(0f) }

    val q = query.trim().lowercase()
    val focused = focusSkillId?.takeIf { ui.effective.skills.containsKey(it) }
    val focusPre = remember(focused, ui.effective.skills) {
        focused?.let { ui.effective.skills[it]?.pre ?: emptyList() } ?: emptyList()
    }
    val focusDep = remember(focused, ui.effective.skills) {
        if (focused == null) emptyList()
        else ui.effective.skills.entries.filter { focused in it.value.pre }.map { it.key }
    }

    fun nodeAlpha(n: NetNode): Float {
        val s = ui.effective.skills[n.skillId] ?: return 1f
        return when {
            q.isNotEmpty() && !(s.name.lowercase().contains(q) || s.cat.lowercase().contains(q)) -> 0.16f
            focused != null && !(n.skillId == focused || n.skillId in focusPre || n.skillId in focusDep) -> 0.14f
            laneCount > 1 && n.lane != currentLane -> if (scale < 0.85f) 0.62f else 0.34f
            else -> 1f
        }
    }

    // ---------------- 静态层 ----------------
    Canvas(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { canvasSize = it }
            .pointerInput(model, geo) {
                detectTapGestures(
                    onTap = { p ->
                        hitTest(model, p, offset, scale, density)?.let { hit ->
                            rippleAt = Offset(hit.cx, hit.cy)
                            scope.launch { ripple.snapTo(0f); ripple.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
                            if (hit.isJob) onJobTap(hit.id) else onSkillTap(hit.id)
                        }
                    },
                    onLongPress = { p ->
                        hitTest(model, p, offset, scale, density)?.let { hit ->
                            if (!hit.isJob) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                rippleAt = Offset(hit.cx, hit.cy)
                                scope.launch { ripple.snapTo(0f); ripple.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
                                onSkillLongPress(hit.id)
                            }
                        }
                    },
                    onDoubleTap = { p ->
                        val target = when {
                            scale < 0.8f -> 1f
                            scale > 1.3f -> Spec.MIN_SCALE
                            else -> 1.7f
                        }
                        val c = p - offset
                        animateCamera(target, Offset(p.x - c.x * (target / scale), p.y - c.y * (target / scale)).let { clampOffset(it, target) })
                    },
                )
            }
            .pointerInput(model, geo) {
                var vx = 0f
                var vy = 0f
                var lastT = 0L
                detectTransformGestures { centroid, pan, zoom, _ ->
                    camJob?.cancel()
                    val now = System.nanoTime()
                    if (lastT != 0L) {
                        val dt = ((now - lastT) / 1e9f).coerceIn(0.004f, 0.12f)
                        vx = vx * 0.55f + (pan.x / dt) * 0.45f
                        vy = vy * 0.55f + (pan.y / dt) * 0.45f
                    }
                    lastT = now
                    val ns = (scale * zoom).coerceIn(Spec.MIN_SCALE, Spec.MAX_SCALE)
                    val c = centroid - offset
                    offset = softClamp(centroid - c * (ns / scale) + pan, ns)
                    scale = ns
                    settle(Offset(vx, vy))
                }
            },
    ) {
        if (model.lanes.isEmpty()) return@Canvas
        val s = scale
        val o = offset
        val visL = -o.x / s
        val visT = -o.y / s
        val visR = (size.width - o.x) / s
        val visB = (size.height - o.y) / s
        val lod = if (s < 0.7f) 0 else if (s < 0.95f) 1 else 2
        val introP = intro

        withTransform({ translate(o.x, o.y); scale(s, s, Offset.Zero) }) {

            // ---- 聚焦关系连线（画在节点之下）----
            if (focused != null) {
                val target = model.bySkill[focused]?.firstOrNull()
                if (target != null) {
                    drawFocusLinks(model, target.cx, target.cy, focusPre, focusDep, 2f * density)
                }
            }

            model.lanes.forEach { lane ->
                if (lane.left + geo.laneW < visL - 60f || lane.left > visR + 60f) return@forEach

                val dimmed = if (s < 0.85f) 0.62f else 0.5f
                val laneBase = if (q.isEmpty() && focused == null && laneCount > 1 && lane.index != currentLane) dimmed else 1f
                val railAlpha = laneBase * 0.9f

                // 轨道：底槽 + 进度段
                drawLine(
                    color = lane.color.copy(alpha = 0.26f * railAlpha),
                    start = Offset(lane.railX, lane.railTop),
                    end = Offset(lane.railX, lane.railBottom),
                    strokeWidth = 3.4f * density, cap = StrokeCap.Round,
                )
                if (lane.total > 0) {
                    val f = ((lane.done + lane.learning * 0.5f) / lane.total).coerceIn(0f, 1f)
                    if (f > 0f) {
                        drawLine(
                            color = lane.color.copy(alpha = railAlpha),
                            start = Offset(lane.railX, lane.railTop),
                            end = Offset(lane.railX, lane.railTop + (lane.railBottom - lane.railTop) * f),
                            strokeWidth = 3.2f * density, cap = StrokeCap.Round,
                        )
                    }
                }

                // 轨道 → 岗位卡
                lane.job?.let { job ->
                    drawLine(
                        color = lane.color.copy(alpha = 0.5f * railAlpha),
                        start = Offset(lane.railX, lane.railBottom),
                        end = Offset(job.cx, job.cy - geo.jobH / 2f),
                        strokeWidth = 2f * density, cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f * density, 7f * density)),
                    )
                }

                // 节点（纵向剪裁：只画视野内的，单屏通常 6~8 个而非整条泳道）
                lane.nodes.forEach { n ->
                    if (n.cy < visT - geo.rowH || n.cy > visB + geo.rowH) return@forEach
                    val na = nodeAlpha(n) * introScale(introP, n.order)
                    if (na <= 0.01f) return@forEach
                    val c = Offset(n.cx, n.cy)
                    val r = geo.nodeR * (0.9f + 0.1f * introScale(introP, n.order))

                    // 与轨道分离的底色圈
                    drawCircle(colors.bg, radius = r + 2.5f * density, center = c, alpha = na)
                    drawCircle(n.fill, radius = r, center = c, alpha = na)

                    val statusC = statusColorOf(n.st)
                    val ringC = if (n.st == "none") n.fill.copy(alpha = 0.35f) else statusC
                    val ringW = if (n.st == "none") 1.6f * density else 3f * density
                    drawCircle(ringC, radius = r, center = c, alpha = na, style = Stroke(ringW))

                    val nl = numLayouts.getOrNull(n.order)
                    if (nl != null) {
                        drawText(nl, color = Color.White, topLeft = Offset(n.cx - nl.size.width / 2f, n.cy - nl.size.height / 2f), alpha = na)
                    }

                    // 已学角标
                    if (n.st == "done") {
                        val bx = n.cx + r * 0.78f
                        val by = n.cy - r * 0.78f
                        drawCircle(colors.bg, radius = 7f * density, center = Offset(bx, by), alpha = na)
                        drawCircle(statusC, radius = 5.6f * density, center = Offset(bx, by), alpha = na)
                        drawText(
                            badgeLayout, color = Color.White,
                            topLeft = Offset(bx - badgeLayout.size.width / 2f, by - badgeLayout.size.height / 2f),
                            alpha = na,
                        )
                    }

                    // 文字块（受泳道宽度约束，绝不越界）
                    if (lod > 0) {
                        val textL = if (lod >= 2) n.text.two else n.text.one
                        val metaH = if (lod >= 2) n.text.meta.size.height + 2f else 0f
                        val blockH = textL.size.height + metaH
                        val top = n.cy - blockH / 2f
                        drawText(
                            textL, color = colors.onSurface,
                            topLeft = Offset(lane.left + geo.labelDX, top),
                            alpha = na,
                        )
                        if (lod >= 2) {
                            drawText(
                                n.text.meta, color = colors.onSurfaceVariant,
                                topLeft = Offset(lane.left + geo.labelDX, top + textL.size.height + 2f),
                                alpha = na * 0.85f,
                            )
                        }
                    }
                }

                // 岗位卡
                lane.job?.let { job ->
                    val ja = laneBase
                    val tl = Offset(job.cx - geo.jobW / 2f, job.cy - geo.jobH / 2f)
                    drawRoundRect(colors.bg, topLeft = tl, size = Size(geo.jobW + 6f, geo.jobH + 6f), cornerRadius = CornerRadius(19f * density), alpha = ja)
                    drawRoundRect(job.fill, topLeft = tl, size = Size(geo.jobW, geo.jobH), cornerRadius = CornerRadius(18f * density), alpha = ja)
                    drawRoundRect(
                        Color.White.copy(alpha = 0.9f), topLeft = tl, size = Size(geo.jobW, geo.jobH),
                        cornerRadius = CornerRadius(18f * density), alpha = ja, style = Stroke(1.6f * density),
                    )
                    drawText(
                        jobCaption, color = Color.White,
                        topLeft = Offset(job.cx - jobCaption.size.width / 2f, job.cy - geo.jobH * 0.30f),
                        alpha = ja * 0.72f,
                    )
                    drawText(
                        job.title, color = Color.White,
                        topLeft = Offset(job.cx - job.title.size.width / 2f, job.cy - geo.jobH * 0.02f),
                        alpha = ja,
                    )
                }
            }
        }
    }

    // ---------------- 动效层（每帧只画少量图元） ----------------
    if (motionOn) {
        Canvas(modifier.fillMaxSize().clipToBounds()) {
            if (model.lanes.isEmpty()) return@Canvas
            val s = scale
            val o = offset
            val p = pulse.value
            val f = flow.value
            val visL = -o.x / s
            val visT = -o.y / s
            val visR = (size.width - o.x) / s
            val visB = (size.height - o.y) / s

            withTransform({ translate(o.x, o.y); scale(s, s, Offset.Zero) }) {
                // 学习中的节点：柔和呼吸光晕
                val lane = model.lanes.getOrNull(currentLane) ?: return@withTransform
                lane.nodes.forEach { n ->
                    if (n.st != "learning") return@forEach
                    if (n.cy < visT - 40f || n.cy > visB + 40f) return@forEach
                    val a = (0.17f - 0.12f * p)
                    drawCircle(statusColorOf("learning"), radius = geo.nodeR + (4f + 8f * p) * density, center = Offset(n.cx, n.cy), alpha = a)
                    drawCircle(statusColorOf("learning"), radius = geo.nodeR + 2f * density, center = Offset(n.cx, n.cy), alpha = 0.09f + 0.05f * p)
                }

                // 当前泳道：沿轨道下行的高光点（替代虚线流动，更像“进度在推进”）
                if (lane.nodes.isNotEmpty()) {
                    val top = lane.railTop
                    val bottom = lane.job?.let { it.cy - geo.jobH / 2f } ?: lane.railBottom
                    val y = top + (bottom - top) * f
                    val c = Offset(lane.railX, y)
                    drawCircle(lane.color, radius = 13f * density, center = c, alpha = 0.07f)
                    drawCircle(lane.color, radius = 8f * density, center = c, alpha = 0.12f)
                    drawCircle(lane.color, radius = 4.6f * density, center = c, alpha = 0.85f)
                }

                // 点击涟漪
                val rp = ripple.value
                rippleAt?.let { c ->
                    if (rp > 0f && rp < 1f) {
                        drawCircle(
                            color = colors.onSurface,
                            radius = geo.nodeR + 34f * density * rp,
                            center = c,
                            alpha = (1f - rp) * 0.28f,
                            style = Stroke(2f * density),
                        )
                    }
                }
            }
        }
    }
}

/* ============== 绘制辅助 ============== */

private class DrawColors(
    val bg: Color,
    val surface: Color,
    val outline: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
)

private class Hit(val id: String, val isJob: Boolean, val cx: Float, val cy: Float)

private fun hitTest(model: NetModel, p: Offset, offset: Offset, scale: Float, density: Float): Hit? {
    val c = (p - offset) / scale
    model.lanes.forEach { lane ->
        lane.job?.let { job ->
            if (abs(c.x - job.cx) < 130f && abs(c.y - job.cy) < 46f) return Hit(job.jobId, true, job.cx, job.cy)
        }
    }
    var best: Hit? = null
    var bestD = Float.MAX_VALUE
    // 24dp 屏幕半径折算成内容单位，缩放后仍可点中
    val limit = (24f * density / scale.coerceAtLeast(0.2f)).coerceAtLeast(40f)
    model.lanes.forEach { lane ->
        lane.nodes.forEach { n ->
            val d = (c - Offset(n.cx, n.cy)).getDistance()
            if (d < limit && d < bestD) {
                bestD = d
                best = Hit(n.skillId, false, n.cx, n.cy)
            }
        }
    }
    return best
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFocusLinks(
    model: NetModel,
    tx: Float,
    ty: Float,
    pre: List<String>,
    dep: List<String>,
    strokeW: Float,
) {
    fun curve(fromX: Float, fromY: Float, toX: Float, toY: Float, color: Color) {
        val path = Path()
        path.moveTo(fromX, fromY)
        val midY = (fromY + toY) / 2f
        path.cubicTo(fromX, midY, toX, midY, toX, toY)
        drawPath(path, color = color, style = Stroke(width = strokeW, cap = StrokeCap.Round))
    }
    pre.forEach { id ->
        model.bySkill[id]?.firstOrNull()?.let { n ->
            if (n.cx != tx || n.cy != ty) curve(n.cx, n.cy, tx, ty, Color(0xFF6366F1).copy(alpha = 0.45f))
        }
    }
    dep.forEach { id ->
        model.bySkill[id]?.firstOrNull()?.let { n ->
            if (n.cx != tx || n.cy != ty) curve(tx, ty, n.cx, n.cy, Color(0xFF0EA5E9).copy(alpha = 0.45f))
        }
    }
}

/** 入场阶梯：按节点在泳道内的序号依次浮现 */
private fun introScale(p: Float, order: Int): Float {
    if (p >= 1f) return 1f
    val local = ((p * 1.6f) - order * 0.045f).coerceIn(0f, 1f)
    return FastOutSlowInEasing.transform(local)
}

private fun nearestDetent(scale: Float): Float {
    val detents = floatArrayOf(Spec.MIN_SCALE, 1f, 1.7f)
    var best = detents[0]
    var bestD = Float.MAX_VALUE
    detents.forEach { d ->
        val diff = abs(d - scale)
        if (diff < bestD) {
            bestD = diff
            best = d
        }
    }
    return best
}

private fun catColorOf(cat: String): Color = when (cat) {
    "语言基础" -> Color(0xFF64748B)
    "硬件" -> Color(0xFF2563EB)
    "系统" -> Color(0xFF0EA5E9)
    "协议" -> Color(0xFF8B5CF6)
    "AI/EDA" -> Color(0xFF14B8A6)
    else -> Color(0xFF64748B)
}

private fun statusColorOf(st: String): Color = when (st) {
    "done" -> Color(0xFF10B981)
    "learning" -> Color(0xFFF59E0B)
    else -> Color(0xFF94A3B8)
}
