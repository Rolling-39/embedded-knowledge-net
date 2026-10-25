@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rolling.learningguider.ui.net

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.util.lerp as lerpF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rolling.learningguider.data.model.EffectiveData
import com.rolling.learningguider.ui.AppViewModel
import com.rolling.learningguider.ui.UiState
import com.rolling.learningguider.ui.common.Dot
import com.rolling.learningguider.ui.common.parseHexColor
import com.rolling.learningguider.ui.icons.AppIcons
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 知识网（手机端纵向泳道布局）：
 * 每条学习路径 = 一条竖直泳道，技能按学习顺序自上而下串联（粗彩色连线 + 步骤序号），
 * 泳道底部是目标岗位色块。默认视野约两条泳道，左右平移切换路径、双指缩放；
 * 所有文字在内容坐标系绘制，天然随缩放等比缩放。
 */
object NetLayout {
    const val LANE_W = 240f
    const val HEADER_H = 120f
    const val ROW_H = 108f
    const val NODE_R = 22f
    const val MARGIN = 50f
    const val JOB_W = 210f
    const val JOB_H = 62f

    data class SkillNode(val skillId: String, val lane: Int, val order: Int, val x: Float, val y: Float)
    data class JobNode(val jobId: String, val lane: Int, val x: Float, val y: Float)

    data class Layout(
        val skillNodes: List<SkillNode>,
        val jobNodes: List<JobNode>,
        val laneXs: List<Float>,
        val W: Float,
        val H: Float,
    )

    fun build(e: EffectiveData): Layout {
        val paths = e.paths
        if (paths.isEmpty()) return Layout(emptyList(), emptyList(), emptyList(), 800f, 800f)
        val laneXs = paths.indices.map { MARGIN + it * LANE_W + LANE_W / 2 }
        val nodes = mutableListOf<SkillNode>()
        var maxLen = 0
        paths.forEachIndexed { i, p ->
            p.skills.forEachIndexed { j, sid ->
                nodes += SkillNode(sid, i, j, laneXs[i], HEADER_H + j * ROW_H)
            }
            maxLen = maxOf(maxLen, p.skills.size)
        }
        val jobY = HEADER_H + maxLen * ROW_H + JOB_H / 2 + 30f
        val jobs = paths.mapIndexed { i, p -> JobNode(p.job, i, laneXs[i], jobY) }
        val w = MARGIN * 2 + paths.size * LANE_W
        val h = jobY + JOB_H / 2 + MARGIN
        return Layout(nodes, jobs, laneXs, w, maxOf(h, 700f))
    }
}

/* ============== 知识网主屏 ============== */

@Composable
fun NetScreen(vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    var activePath by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var focusSkill by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetSkill by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetJob by rememberSaveable { mutableStateOf<String?>(null) }
    var skillFormId by rememberSaveable { mutableStateOf<String?>(null) }
    var resetTick by rememberSaveable { mutableStateOf(0) }
    val context = LocalContext.current

    val systemMotionOff = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val motionOn = !ui.reduceMotion && !systemMotionOff

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = activePath.isEmpty(),
                onClick = { activePath = ""; focusSkill = null },
                label = { Text("全部") },
            )
            ui.effective.paths.forEach { p ->
                FilterChip(
                    selected = activePath == p.id,
                    onClick = { activePath = if (activePath == p.id) "" else p.id; focusSkill = null },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Dot(parseHexColor(p.color), 8.dp)
                            Text(p.name, Modifier.padding(start = 6.dp))
                        }
                    },
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; focusSkill = null },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("搜索技能，匹配节点高亮…", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(AppIcons.Close, contentDescription = "清空") }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
        )
        if (ui.showNetHint && focusSkill == null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f))
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "点节点看详情 · 长按快标状态 · 双指缩放/拖动 · 双击放大",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.TextButton(onClick = { vm.dismissNetHint() }) { Text("知道了") }
            }
        }
        if (focusSkill != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    "聚焦：${ui.effective.skills[focusSkill]?.name ?: ""}（紫=前置 · 青=被依赖）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Box(Modifier.fillMaxSize().padding(top = 4.dp)) {
            NetCanvas(
                ui = ui,
                activePathId = activePath,
                focusSkillId = focusSkill,
                query = query,
                motionOn = motionOn,
                onSkillTap = { sheetSkill = it },
                onJobTap = { sheetJob = it },
                onSkillLongPress = vm::cycleSkillStatus,
                resetTick = resetTick,
            )
            androidx.compose.material3.Surface(
                onClick = { resetTick++ },
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                shadowElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Text(
                    "复位",
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
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

/* ============== 画布（单 Canvas 自绘：手势只触发重绘不重组） ============== */

@Composable
fun NetCanvas(
    ui: UiState,
    activePathId: String,
    focusSkillId: String?,
    query: String,
    motionOn: Boolean,
    onSkillTap: (String) -> Unit,
    onJobTap: (String) -> Unit,
    onSkillLongPress: (String) -> Unit,
    resetTick: Int = 0,
    modifier: Modifier = Modifier,
) {
    val layout = remember(ui.effective) { NetLayout.build(ui.effective) }
    var scale by remember { mutableFloatStateOf(2f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var fitted by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    var animJob by remember { mutableStateOf<Job?>(null) }

    fun defaultView(size: IntSize): Pair<Float, Offset> {
        val sc = (size.width / (NetLayout.LANE_W * 2.05f)).coerceIn(0.5f, 2.6f)
        val x = if (layout.laneXs.isNotEmpty()) size.width / 2f - layout.laneXs[0] * sc else 0f
        return sc to Offset(x, 24f)
    }

    /** 视图平滑动画（chip/搜索定位、复位、双击缩放共用） */
    fun animateViewTo(targetScale: Float, targetOffset: Offset) {
        animJob?.cancel()
        animJob = scope.launch {
            val s0 = scale
            val o0 = offset
            val t = Animatable(0f)
            try {
                t.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) {
                    val f = t.value
                    scale = lerpF(s0, targetScale, f)
                    offset = lerp(o0, targetOffset, f)
                }
            } finally {
                scale = targetScale
                offset = targetOffset
            }
        }
    }

    val labelBg = MaterialTheme.colorScheme.background
    val labelBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val nameColor = MaterialTheme.colorScheme.onSurface

    val pulseState: State<Float> = if (motionOn) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            0.35f, 1f,
            infiniteRepeatable(tween(1300), RepeatMode.Reverse),
            label = "pulseA",
        )
    } else {
        remember { androidx.compose.runtime.mutableStateOf(1f) }
    }
    val dashState: State<Float> = if (motionOn) {
        rememberInfiniteTransition(label = "dash").animateFloat(
            0f, 88f,
            infiniteRepeatable(tween(1300, easing = LinearEasing)),
            label = "dashA",
        )
    } else {
        remember { androidx.compose.runtime.mutableStateOf(0f) }
    }

    val textMeasurer = rememberTextMeasurer()
    val nameStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val headerStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val subStyle = TextStyle(fontSize = 12.sp)
    val numStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val nameLayouts = remember(ui.effective) {
        ui.effective.skills.mapValues { (_, s) -> textMeasurer.measure(AnnotatedString(s.name), nameStyle) }
    }
    val headerLayouts = remember(ui.effective) {
        ui.effective.paths.associate { p -> p.id to textMeasurer.measure(AnnotatedString(p.name), headerStyle) }
    }
    val periodLayouts = remember(ui.effective) {
        ui.effective.paths.associate { p -> p.id to textMeasurer.measure(AnnotatedString(p.period), subStyle) }
    }
    val jobNameLayouts = remember(ui.effective) {
        ui.effective.jobs.mapValues { (_, j) ->
            textMeasurer.measure(AnnotatedString(j.name), TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold))
        }
    }
    val glyphCache = remember { HashMap<String, TextLayoutResult>() }
    fun glyph(text: String): TextLayoutResult = glyphCache.getOrPut(text) {
        textMeasurer.measure(AnnotatedString(text), numStyle)
    }

    fun laneOf(pathId: String): Int = ui.effective.paths.indexOfFirst { it.id == pathId }

    // 复位视图（外层按钮触发）
    LaunchedEffect(resetTick) {
        if (resetTick > 0 && canvasSize != IntSize.Zero) {
            val (sc, of) = defaultView(canvasSize)
            animateViewTo(sc, of)
        }
    }

    // 选中路径 → 自动把该泳道平移到屏幕中央
    LaunchedEffect(activePathId, fitted) {
        if (!fitted || activePathId.isEmpty()) return@LaunchedEffect
        val lane = laneOf(activePathId)
        if (lane in layout.laneXs.indices) {
            animateViewTo(scale, Offset(canvasSize.width / 2f - layout.laneXs[lane] * scale, offset.y))
        }
    }
    // 搜索 → 居中第一个匹配节点
    LaunchedEffect(query, fitted) {
        kotlinx.coroutines.delay(250) // 防抖：打字过程不逐字跳动
        val q2 = query.trim().lowercase()
        if (q2.isBlank() || canvasSize == IntSize.Zero) return@LaunchedEffect
        val node = layout.skillNodes.firstOrNull {
            ui.effective.skills[it.skillId]?.name?.lowercase()?.contains(q2) == true
        } ?: return@LaunchedEffect
        animateViewTo(
            scale,
            Offset(canvasSize.width / 2f - node.x * scale, canvasSize.height / 2f - node.y * scale),
        )
    }

    fun hitTest(p: Offset): Pair<String, Boolean>? { // id, isJob
        val c = (p - offset) / scale
        layout.jobNodes.forEach { jn ->
            val r = Rect(
                jn.x - NetLayout.JOB_W / 2, jn.y - NetLayout.JOB_H / 2,
                jn.x + NetLayout.JOB_W / 2, jn.y + NetLayout.JOB_H / 2,
            )
            if (r.inflate(8f).contains(c)) return jn.jobId to true
        }
        layout.skillNodes.forEach { n ->
            if ((c - Offset(n.x, n.y)).getDistance() <= 36f) return n.skillId to false
        }
        return null
    }

    val q = query.trim().lowercase()
    fun matches(skillId: String): Boolean {
        if (q.isEmpty()) return true
        val s = ui.effective.skills[skillId] ?: return false
        return s.name.lowercase().contains(q) || s.cat.lowercase().contains(q)
    }

    val activeLane = if (activePathId.isEmpty()) -1 else laneOf(activePathId)
    val focusOn = focusSkillId != null && ui.effective.skills.containsKey(focusSkillId)
    val focusPre = if (focusOn) (ui.effective.skills[focusSkillId]?.pre ?: emptyList()) else emptyList()
    val focusDep = if (focusOn) {
        ui.effective.skills.entries.filter { focusSkillId in it.value.pre }.map { it.key }
    } else emptyList()

    fun nodeAlpha(skillId: String, lane: Int): Float = when {
        q.isNotEmpty() && !matches(skillId) -> 0.1f
        focusOn && !(skillId == focusSkillId || focusPre.contains(skillId) || focusDep.contains(skillId)) -> 0.1f
        activeLane >= 0 && lane != activeLane -> 0.12f
        else -> 1f
    }

    val dashPaint = remember { Paint() }

    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged { size: IntSize ->
                canvasSize = size
                if (!fitted && size.width > 0 && layout.laneXs.isNotEmpty()) {
                    fitted = true
                    val (sc, of) = defaultView(size)
                    scale = sc
                    offset = of
                }
            }
            .pointerInput(layout) {
                detectTapGestures(
                    onTap = { p -> hitTest(p)?.let { (id, isJob) -> if (isJob) onJobTap(id) else onSkillTap(id) } },
                    onLongPress = { p -> hitTest(p)?.let { (id, isJob) -> if (!isJob) onSkillLongPress(id) } },
                    onDoubleTap = { p ->
                        val target = if (scale > 1.7f) 1f else 2.3f
                        animateViewTo(target, p - (p - offset) * (target / scale))
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    animJob?.cancel()
                    val newScale = (scale * zoom).coerceIn(0.4f, 3.5f)
                    offset = centroid - (centroid - offset) * (newScale / scale)
                    scale = newScale
                    offset += pan
                }
            },
    ) {
        val pulse = pulseState.value
        val dash = dashState.value
        if (layout.laneXs.isEmpty()) return@Canvas

        fun drawDashedLine(start: Offset, end: Offset, color: Color, strokeWidth: Float, phase: Float) {
            dashPaint.color = color
            dashPaint.alpha = 1f
            dashPaint.style = PaintingStyle.Stroke
            dashPaint.strokeWidth = strokeWidth
            dashPaint.pathEffect = PathEffect.dashPathEffect(floatArrayOf(26f, 18f), phase)
            dashPaint.strokeCap = StrokeCap.Round
            drawContext.canvas.drawLine(start, end, dashPaint)
        }

        withTransform({ translate(offset.x, offset.y); this.scale(scale, scale, Offset.Zero) }) {

            // ---- 泳道表头：路径名 + 周期 ----
            ui.effective.paths.forEachIndexed { lane, p ->
                val laneAlpha = if (activeLane >= 0 && lane != activeLane) 0.15f else 1f
                val x = layout.laneXs[lane]
                val color = parseHexColor(p.color)
                headerLayouts[p.id]?.let {
                    drawText(it, color = color, topLeft = Offset(x - it.size.width / 2f, 34f), alpha = laneAlpha)
                }
                periodLayouts[p.id]?.let {
                    drawText(it, color = labelBorder, topLeft = Offset(x - it.size.width / 2f, 60f), alpha = laneAlpha * 0.9f)
                }
            }

            // ---- 泳道连线（粗、明显）----
            ui.effective.paths.forEachIndexed { lane, p ->
                val selected = lane == activeLane
                val laneAlpha = when {
                    selected -> 1f
                    activeLane >= 0 -> 0.1f
                    else -> 0.8f
                }
                val color = parseHexColor(p.color)
                val strokeW = if (selected) 9f else 6f
                for (j in 0 until p.skills.size - 1) {
                    val y1 = NetLayout.HEADER_H + j * NetLayout.ROW_H
                    val y2 = NetLayout.HEADER_H + (j + 1) * NetLayout.ROW_H
                    val x = layout.laneXs[lane]
                    if (selected && motionOn) {
                        drawDashedLine(Offset(x, y1), Offset(x, y2), color, strokeW, dash)
                    } else {
                        drawLine(color, Offset(x, y1), Offset(x, y2), strokeWidth = strokeW, alpha = laneAlpha)
                    }
                }
                // 末节点 → 岗位色块
                if (p.skills.isNotEmpty()) {
                    val lastY = NetLayout.HEADER_H + (p.skills.size - 1) * NetLayout.ROW_H
                    val jobNode = layout.jobNodes.firstOrNull { it.lane == lane }
                    if (jobNode != null) {
                        drawLine(
                            color, Offset(layout.laneXs[lane], lastY),
                            Offset(jobNode.x, jobNode.y - NetLayout.JOB_H / 2),
                            strokeWidth = strokeW, alpha = laneAlpha,
                        )
                    }
                }
            }

            // ---- 技能节点 + 名称标签（标签带底色，避免被连线穿过/重叠）----
            layout.skillNodes.forEach { n ->
                val s = ui.effective.skills[n.skillId] ?: return@forEach
                val st = ui.skillDone[n.skillId]?.st ?: "none"
                val alpha = nodeAlpha(n.skillId, n.lane)
                val fill = catColorFor(s.cat)
                if (st != "none" && motionOn) {
                    drawCircle(statusColorFor(st).copy(alpha = 0.3f * pulse), radius = 30f, center = Offset(n.x, n.y))
                }
                nameLayouts[n.skillId]?.let { tl ->
                    val pillW = tl.size.width + 18f
                    val pillH = tl.size.height + 8f
                    val pillTop = n.y + NetLayout.NODE_R + 6f
                    drawRoundRect(
                        color = labelBg,
                        topLeft = Offset(n.x - pillW / 2f, pillTop),
                        size = Size(pillW, pillH),
                        cornerRadius = CornerRadius(8f, 8f),
                        alpha = alpha,
                    )
                    drawRoundRect(
                        color = labelBorder,
                        topLeft = Offset(n.x - pillW / 2f, pillTop),
                        size = Size(pillW, pillH),
                        cornerRadius = CornerRadius(8f, 8f),
                        alpha = alpha,
                        style = Stroke(1f),
                    )
                    drawText(tl, color = nameColor, topLeft = Offset(n.x - tl.size.width / 2f, pillTop + 4f), alpha = alpha)
                }
                drawCircle(fill, radius = NetLayout.NODE_R, center = Offset(n.x, n.y), alpha = alpha)
                drawText(glyph("${n.order + 1}"), color = Color.White, topLeft = Offset(n.x - 7f, n.y - 9f), alpha = alpha)
                val ring = when {
                    focusOn && n.skillId == focusSkillId -> Color(0xFF6366F1) to 4.5f
                    focusOn && focusPre.contains(n.skillId) -> Color(0xFF6366F1) to 3.5f
                    focusOn && focusDep.contains(n.skillId) -> Color(0xFF0EA5E9) to 3.5f
                    st == "learning" -> statusColorFor("learning") to 4.5f
                    st == "done" -> statusColorFor("done") to 4.5f
                    else -> Color.White to 3f
                }
                drawCircle(ring.first, radius = NetLayout.NODE_R, center = Offset(n.x, n.y), alpha = alpha, style = Stroke(ring.second))
                if (st == "done") {
                    drawText(glyph("✓"), color = Color(0xFF10B981), topLeft = Offset(n.x + 8f, n.y + 6f), alpha = alpha)
                }
            }

            // ---- 岗位节点 ----
            layout.jobNodes.forEach { jn ->
                val j = ui.effective.jobs[jn.jobId] ?: return@forEach
                val st = ui.jobState[jn.jobId]?.st ?: "none"
                val laneAlpha = when {
                    q.isNotEmpty() && !j.name.lowercase().contains(q) -> 0.12f
                    activeLane >= 0 && jn.lane != activeLane -> 0.12f
                    focusOn -> 0.25f
                    else -> 1f
                }
                val fill = parseHexColor(j.color, catColorFor("硬件"))
                val topLeft = Offset(jn.x - NetLayout.JOB_W / 2, jn.y - NetLayout.JOB_H / 2)
                drawRoundRect(fill, topLeft = topLeft, size = Size(NetLayout.JOB_W, NetLayout.JOB_H), cornerRadius = CornerRadius(14f, 14f), alpha = laneAlpha)
                val strokeC = when (st) {
                    "learning" -> Color(0xFFF59E0B)
                    "done" -> Color(0xFF10B981)
                    else -> Color.White
                }
                drawRoundRect(strokeC, topLeft = topLeft, size = Size(NetLayout.JOB_W, NetLayout.JOB_H), cornerRadius = CornerRadius(14f, 14f), alpha = laneAlpha, style = Stroke(if (st == "none") 3f else 5f))
                jobNameLayouts[jn.jobId]?.let {
                    drawText(it, color = Color.White, topLeft = Offset(jn.x - it.size.width / 2f, jn.y - it.size.height / 2f), alpha = laneAlpha)
                }
            }
        }
    }
}

private fun catColorFor(cat: String): Color = when (cat) {
    "语言基础" -> Color(0xFF64748B)
    "硬件" -> Color(0xFF2563EB)
    "系统" -> Color(0xFF0EA5E9)
    "协议" -> Color(0xFF8B5CF6)
    "AI/EDA" -> Color(0xFF14B8A6)
    else -> Color(0xFF64748B)
}

private fun statusColorFor(st: String): Color = when (st) {
    "done" -> Color(0xFF10B981)
    "learning" -> Color(0xFFF59E0B)
    else -> Color(0xFF94A3B8)
}
