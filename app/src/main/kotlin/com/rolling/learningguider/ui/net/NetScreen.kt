package com.rolling.learningguider.ui.net

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
import com.rolling.learningguider.ui.common.catColor
import com.rolling.learningguider.ui.common.parseHexColor
import com.rolling.learningguider.ui.icons.AppIcons

/* ============== 布局算法（移植网页版 layoutNet/netH，坐标系一致） ============== */

object NetLayout {
    val CAT_X = linkedMapOf(
        "语言基础" to 90f, "硬件" to 320f, "系统" to 560f, "协议" to 800f, "AI/EDA" to 1040f,
    )
    const val JOB_X = 1260f
    const val ROW_H = 36f
    const val TOP = 46f
    const val JOB_H = 56f
    const val JOB_TOP = 50f
    const val W = 1380f
    const val NODE_R = 15f
    const val JOB_W = 148f
    const val JOB_HH = 40f

    fun build(e: EffectiveData): Layout {
        val pos = HashMap<String, Offset>()
        CAT_X.forEach { (cat, x) ->
            var i = 0
            e.skills.forEach { (id, s) ->
                if (s.cat == cat) {
                    pos[id] = Offset(x, TOP + i * ROW_H)
                    i++
                }
            }
        }
        val jobRects = LinkedHashMap<String, Rect>()
        e.jobs.entries.forEachIndexed { i, (id, _) ->
            val y = JOB_TOP + i * JOB_H
            jobRects[id] = Rect(JOB_X - JOB_W / 2, y - JOB_HH / 2, JOB_X + JOB_W / 2, y + JOB_HH / 2)
        }
        var h = 0f
        CAT_X.keys.forEach { cat ->
            val n = e.skills.values.count { it.cat == cat }
            if (n > 0) h = maxOf(h, TOP + (n - 1) * ROW_H + 32f)
        }
        h = maxOf(h, JOB_TOP + (e.jobs.size - 1).coerceAtLeast(0) * JOB_H + 40f)
        return Layout(pos, jobRects, W, maxOf(960f, h + 20f))
    }

    data class Layout(
        val pos: Map<String, Offset>,
        val jobRects: Map<String, Rect>,
        val W: Float,
        val H: Float,
    )
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
    val context = LocalContext.current

    // 系统级「移除动画」+ 应用内开关 → 双重尊重
    val systemMotionOff = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val motionOn = !ui.reduceMotion && !systemMotionOff

    Column(Modifier.fillMaxSize()) {
        // 路径筛选 chips
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
                label = { Text("全部路径") },
            )
            ui.effective.paths.forEach { p ->
                FilterChip(
                    selected = activePath == p.id,
                    onClick = { activePath = if (activePath == p.id) "" else p.id; focusSkill = null },
                    label = {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Dot(parseHexColor(p.color), 8.dp)
                            Text(p.name, Modifier.padding(start = 6.dp))
                        }
                    },
                )
            }
        }
        // 搜索
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
                onSkillLongPress = { id ->
                    val next = when (ui.skillDone[id]?.st ?: "none") {
                        "none" -> "learning"
                        "learning" -> "done"
                        else -> "none"
                    }
                    vm.setSkillStatus(id, next)
                },
            )
        }
    }

    sheetSkill?.let { id ->
        if (ui.effective.skills.containsKey(id)) {
            com.rolling.learningguider.ui.library.SkillDetailSheet(
                skillId = id,
                ui = ui,
                onOpenSkill = { sheetSkill = it },
                onDelete = { vm.deleteRecord("skill", id); sheetSkill = null },
                onDismiss = { sheetSkill = null; },
                onSetStatus = { st -> vm.setSkillStatus(id, st) },
                onNoteChange = { vm.setSkillNote(id, it) },
                onStartDate = { vm.setSkillStartDate(id, it) },
                onGoalDate = { vm.setSkillGoalDate(id, it) },
                onFocusNet = { focusSkill = id; sheetSkill = null },
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
            )
        } else sheetJob = null
    }
}

/* ============== 画布（单 Canvas 自绘：性能红线——手势只触发重绘不重组） ============== */

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
    modifier: Modifier = Modifier,
) {
    val layout = remember(ui.effective) { NetLayout.build(ui.effective) }
    var scale by remember { mutableFloatStateOf(0.6f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var fitted by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // 呼吸脉冲（学习中/已学节点光晕）
    val pulseState: State<Float> = if (motionOn) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            0.35f, 1f,
            infiniteRepeatable(tween(1300), RepeatMode.Reverse),
            label = "pulseA",
        )
    } else {
        remember { androidx.compose.runtime.mutableStateOf(1f) }
    }
    // 选中路径连线流动
    val dashState: State<Float> = if (motionOn) {
        rememberInfiniteTransition(label = "dash").animateFloat(
            0f, 60f,
            infiniteRepeatable(tween(1500, easing = LinearEasing)),
            label = "dashA",
        )
    } else {
        remember { androidx.compose.runtime.mutableStateOf(0f) }
    }

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold)
    val headerStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val nameLayouts = remember(ui.effective) {
        ui.effective.skills.mapValues { (_, s) ->
            textMeasurer.measure(AnnotatedString(s.name), labelStyle)
        }
    }
    val jobNameLayouts = remember(ui.effective) {
        ui.effective.jobs.mapValues { (_, j) ->
            textMeasurer.measure(AnnotatedString(j.name), TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold))
        }
    }
    val headerLayouts = remember(ui.effective) {
        NetLayout.CAT_X.keys.associateWith { cat ->
            textMeasurer.measure(AnnotatedString(cat), headerStyle)
        } + ("目标岗位" to textMeasurer.measure(AnnotatedString("目标岗位"), headerStyle))
    }
    val glyphCache = remember { HashMap<String, androidx.compose.ui.text.TextLayoutResult>() }
    val dashPaint = remember { Paint() }
    fun glyph(text: String): androidx.compose.ui.text.TextLayoutResult = glyphCache.getOrPut(text) {
        textMeasurer.measure(AnnotatedString(text), TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold))
    }

    // 搜索自动定位到第一个匹配节点（居中）
    LaunchedEffect(query) {
        val q2 = query.trim().lowercase()
        if (q2.isBlank() || canvasSize == IntSize.Zero) return@LaunchedEffect
        val first = ui.effective.skills.entries.firstOrNull { it.value.name.lowercase().contains(q2) } ?: return@LaunchedEffect
        val pos = layout.pos[first.key] ?: return@LaunchedEffect
        offset = Offset(canvasSize.width / 2f, canvasSize.height / 2f) - pos * scale
    }

    fun hitTest(p: Offset): Pair<String, Boolean>? { // id, isJob
        val c = (p - offset) / scale
        layout.jobRects.forEach { (id, r) -> if (r.inflate(6f).contains(c)) return id to true }
        ui.effective.skills.keys.forEach { id ->
            val pos = layout.pos[id] ?: return@forEach
            if ((c - pos).getDistance() <= 26f) return id to false
        }
        return null
    }

    val q = query.trim().lowercase()
    fun matches(id: String): Boolean {
        if (q.isEmpty()) return true
        val s = ui.effective.skills[id] ?: return false
        return s.name.lowercase().contains(q) || s.cat.lowercase().contains(q)
    }

    val activePath = ui.effective.paths.firstOrNull { it.id == activePathId }
    val focusOn = focusSkillId != null && ui.effective.skills.containsKey(focusSkillId)
    val focusPre = if (focusOn) (ui.effective.skills[focusSkillId]?.pre ?: emptyList()) else emptyList()
    val focusDep = if (focusOn) ui.effective.skills.entries.filter { focusSkillId in it.value.pre }.map { it.key } else emptyList()

    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged { size: IntSize ->
                canvasSize = size
                if (!fitted && size.width > 0) {
                    fitted = true
                    scale = (size.width / layout.W).coerceIn(0.35f, 1f)
                    offset = Offset(0f, (size.height - layout.H * scale) / 2f)
                }
            }
            .pointerInput(layout) {
                detectTapGestures(
                    onTap = { p -> hitTest(p)?.let { (id, isJob) -> if (isJob) onJobTap(id) else onSkillTap(id) } },
                    onLongPress = { p -> hitTest(p)?.let { (id, isJob) -> if (!isJob) onSkillLongPress(id) } },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(0.35f, 2.5f)
                    offset = centroid - (centroid - offset) * (newScale / scale)
                    scale = newScale
                    offset += pan
                }
            },
    ) {
        val pulse = pulseState.value
        val dash = dashState.value

        fun drawDashedPath(path: Path, color: Color, strokeWidth: Float, phase: Float, alpha: Float, pattern: FloatArray = floatArrayOf(20f, 14f)) {
            dashPaint.color = color
            dashPaint.alpha = alpha
            dashPaint.style = PaintingStyle.Stroke
            dashPaint.strokeWidth = strokeWidth
            dashPaint.pathEffect = PathEffect.dashPathEffect(pattern, phase)
            drawContext.canvas.drawPath(path, dashPaint)
        }
        fun drawDashedCircle(center: Offset, radius: Float, color: Color, strokeWidth: Float, alpha: Float) {
            dashPaint.color = color
            dashPaint.alpha = alpha
            dashPaint.style = PaintingStyle.Stroke
            dashPaint.strokeWidth = strokeWidth
            dashPaint.pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
            drawContext.canvas.drawCircle(center, radius, dashPaint)
        }

        withTransform({ translate(offset.x, offset.y); this.scale(scale, scale, Offset.Zero) }) {

            // ---- 连线 ----
            val paths = ui.effective.paths
            paths.forEach { p ->
                val selected = p.id == activePathId
                val dimmed = activePathId.isNotEmpty() && !selected
                val baseColor = parseHexColor(p.color)
                val lineColor = when {
                    dimmed -> Color(0xFFCBD5E1).copy(alpha = 0.4f)
                    selected -> baseColor
                    else -> baseColor.copy(alpha = 0.55f)
                }
                val strokeWidth = when { selected -> 4f; activePathId.isEmpty() -> 1.8f; else -> 1.2f }
                val lineAlpha = when { selected -> 1f; activePathId.isEmpty() -> 0.32f; else -> 0.15f }
                val nodes = p.skills + p.job
                for (i in 0 until nodes.size - 1) {
                    val a = layout.pos[nodes[i]] ?: continue
                    val b = layout.pos[nodes[i + 1]] ?: continue
                    val mx = (a.x + b.x) / 2
                    val path = Path().apply {
                        moveTo(a.x, a.y)
                        cubicTo(mx, a.y, mx, b.y, b.x, b.y)
                    }
                    if (selected && motionOn) {
                        drawDashedPath(path, baseColor, strokeWidth, dash, lineAlpha)
                    } else {
                        drawPath(path, lineColor, alpha = lineAlpha, style = Stroke(strokeWidth))
                    }
                }
            }

            // ---- 聚焦模式的前置连线（紫色虚线） ----
            if (focusOn) {
                val fp = layout.pos[focusSkillId!!] ?: Offset.Zero
                focusPre.forEach { preId ->
                    val pp = layout.pos[preId] ?: return@forEach
                    val mx = (pp.x + fp.x) / 2
                    val path = Path().apply {
                        moveTo(pp.x, pp.y)
                        cubicTo(mx, pp.y, mx, fp.y, fp.x, fp.y)
                    }
                    drawDashedPath(path, Color(0xFF6366F1), 2f, 0f, 0.8f, floatArrayOf(8f, 6f))
                }
            }

            // ---- 技能节点 ----
            ui.effective.skills.forEach { (id, s) ->
                val p = layout.pos[id] ?: return@forEach
                val st = ui.skillDone[id]?.st ?: "none"
                val inPath = activePath == null || activePath.skills.contains(id)
                val focusRelated = !focusOn || id == focusSkillId || focusPre.contains(id) || focusDep.contains(id)
                val alpha = when {
                    q.isNotEmpty() && !matches(id) -> 0.12f
                    focusOn && !focusRelated -> 0.12f
                    activePathId.isNotEmpty() && !inPath -> 0.12f
                    else -> 1f
                }
                if (alpha < 0.9f && q.isEmpty() && !focusOn) { /* 仍绘制，仅淡化 */ }
                val fill = catColorFor(s.cat)
                // 状态光晕（呼吸）
                if (st != "none" && motionOn) {
                    drawCircle(statusColorFor(st).copy(alpha = 0.35f * pulse), radius = 21f, center = p)
                }
                drawCircle(fill, radius = NetLayout.NODE_R, center = p, alpha = alpha)
                val stroke = when {
                    focusOn && id == focusSkillId -> Stroke(3.5f)
                    focusOn && focusPre.contains(id) -> Stroke(2.5f)
                    focusOn && focusDep.contains(id) -> Stroke(2.5f)
                    st == "learning" -> Stroke(3f)
                    st == "done" -> Stroke(3f)
                    else -> Stroke(2.2f)
                }
                val strokeColor = when {
                    focusOn && id == focusSkillId -> Color(0xFF6366F1)
                    focusOn && focusPre.contains(id) -> Color(0xFF6366F1)
                    focusOn && focusDep.contains(id) -> Color(0xFF0EA5E9)
                    st == "learning" -> statusColorFor("learning")
                    st == "done" -> statusColorFor("done")
                    else -> Color.White
                }
                val dashRing = focusOn && (focusPre.contains(id) || focusDep.contains(id))
                if (dashRing) {
                    drawDashedCircle(p, NetLayout.NODE_R, strokeColor, 2.5f, alpha)
                } else {
                    drawCircle(strokeColor, radius = NetLayout.NODE_R, center = p, alpha = alpha, style = stroke)
                }
                // 序号/难度
                val numText = if (activePath?.skills?.contains(id) == true) {
                    "${(activePath.skills.indexOf(id) + 1)}"
                } else "${s.diff}"
                drawText(glyph(numText), color = Color.White, topLeft = Offset(p.x - 5f, p.y - 8f), alpha = alpha)
                // 状态角标
                if (st == "done") {
                    drawText(glyph("✓"), color = Color(0xFF10B981), topLeft = Offset(p.x + 6f, p.y + 6f), alpha = alpha)
                } else if (st == "learning") {
                    drawCircle(Color(0xFFF59E0B), radius = 5f, center = Offset(p.x + 10f, p.y + 10f), alpha = alpha)
                    drawCircle(Color.White, radius = 5f, center = Offset(p.x + 10f, p.y + 10f), alpha = alpha, style = Stroke(1.6f))
                }
                // 名称
                nameLayouts[id]?.let {
                    drawText(it, color = Color(0xFF334155), topLeft = Offset(p.x + 22f, p.y - 8f), alpha = alpha)
                }
            }

            // ---- 岗位节点 ----
            ui.effective.jobs.forEach { (id, j) ->
                val r = layout.jobRects[id] ?: return@forEach
                val st = ui.jobState[id]?.st ?: "none"
                val inPath = activePathId.isEmpty() || activePath?.job == id
                val alpha = when {
                    q.isNotEmpty() && !(j.name.lowercase().contains(q)) -> 0.12f
                    focusOn -> 0.25f
                    activePathId.isNotEmpty() && !inPath -> 0.12f
                    else -> 1f
                }
                val fill = parseHexColor(j.color, catColorFor("硬件"))
                drawRoundRect(
                    color = fill,
                    topLeft = Offset(r.left, r.top),
                    size = androidx.compose.ui.geometry.Size(r.width, r.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                    alpha = alpha,
                )
                val strokeC = when (st) {
                    "learning" -> Color(0xFFF59E0B)
                    "done" -> Color(0xFF10B981)
                    else -> Color.White
                }
                drawRoundRect(
                    color = strokeC,
                    topLeft = Offset(r.left, r.top),
                    size = androidx.compose.ui.geometry.Size(r.width, r.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                    alpha = alpha,
                    style = Stroke(if (st == "none") 2.2f else 4f),
                )
                jobNameLayouts[id]?.let {
                    drawText(
                        it, color = Color.White,
                        topLeft = Offset(r.center.x - it.size.width / 2f, r.center.y - it.size.height / 2f),
                        alpha = alpha,
                    )
                }
                if (st == "done") {
                    drawText(glyph("✓"), color = Color(0xFF10B981), topLeft = Offset(r.right - 22f, r.top - 4f), alpha = alpha)
                }
            }

            // ---- 分类表头 ----
            headerLayouts.forEach { (title, tl) ->
                val x = if (title == "目标岗位") NetLayout.JOB_X else NetLayout.CAT_X[title] ?: return@forEach
                val color = if (title == "目标岗位") Color(0xFF475569) else catColorFor(title)
                drawText(tl, color = color, topLeft = Offset(x - tl.size.width / 2f, 10f))
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
