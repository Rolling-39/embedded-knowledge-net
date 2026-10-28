@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rolling.learningguider.ui.net

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import com.rolling.learningguider.data.model.SkillDone
import com.rolling.learningguider.ui.UiState
import com.rolling.learningguider.ui.common.parseHexColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment

/*
 * ============================================================================
 * 知识网 · 关系图（真正的“网”）
 * ----------------------------------------------------------------------------
 * 节点：81 个技能 + 16 个岗位（同一技能在多条路径里只出现一次 → 天然交叉成网）
 * 边：  ① 前置关系 skill → pre        （紫，最强语义）
 *      ② 同路径相邻 skill[i] → [i+1] （路径色，最淡，构成学习流）
 *      ③ 路径末节点 → 目标岗位        （路径色，稍强）
 * 布局：确定性力导向（Fruchterman-Reingold，固定初始黄金角螺旋，无随机数）
 *       斥力 O(n²) + 边弹簧 + 向心引力，180 次迭代，97 节点约 20ms，只算一次。
 * 交互：单指拖动 / 双指缩放 / 双击放大 / 点击技能开详情 / 点击岗位进入对应路线
 * ============================================================================
 */

private object GSpec {
    const val MIN_SCALE = 0.18f
    const val MAX_SCALE = 2.4f
    const val SPACING = 112f      // dp，节点平均间距
    const val PADDING = 56f       // dp，画布四周留白
    const val ITERATIONS = 180
}

@androidx.compose.runtime.Immutable
private class GNode(
    val id: String,
    val isJob: Boolean,
    val name: String,
    val cat: String,
    val x: Float,
    val y: Float,
    val r: Float,
    val color: Color,
    val degree: Int,
    val pathIndex: Int,
    val st: String,
    val label: TextLayoutResult,
)

private class GEdge(val a: Int, val b: Int, val kind: Int, val color: Color)

private class GraphModel(
    val nodes: List<GNode>,
    val edges: List<GEdge>,
    val w: Float,
    val h: Float,
) {
    companion object {
        val Empty = GraphModel(emptyList(), emptyList(), 0f, 0f)
    }
}

/* ============== 建图 + 布局 ============== */

private fun buildGraph(
    e: EffectiveData,
    skillDone: Map<String, SkillDone>,
    density: Float,
    skillLabels: Map<String, TextLayoutResult>,
    jobLabels: Map<String, TextLayoutResult>,
): GraphModel {
    if (e.skills.isEmpty() && e.paths.isEmpty()) return GraphModel.Empty

    // --- 索引：技能 + 岗位 ---
    val skillIds = e.skills.keys.toList()
    val jobIds = e.paths.mapNotNull { p -> p.job.takeIf { e.jobs.containsKey(it) } }.distinct()
    val skillIndex = HashMap<String, Int>(skillIds.size * 2)
    skillIds.forEachIndexed { i, id -> skillIndex[id] = i }
    val jobIndex = HashMap<String, Int>(jobIds.size * 2)
    jobIds.forEachIndexed { i, id -> jobIndex[id] = skillIds.size + i }

    val jobPath = HashMap<String, Int>()
    e.paths.forEachIndexed { pi, p -> if (!jobPath.containsKey(p.job)) jobPath[p.job] = pi }

    val n = skillIds.size + jobIds.size
    if (n == 0) return GraphModel.Empty

    // --- 边 ---
    val edges = ArrayList<GEdge>(n * 3)
    val deg = IntArray(n)
    val seen = HashSet<Long>()

    fun key(a: Int, b: Int, kind: Int): Long {
        val lo = min(a, b).toLong()
        val hi = max(a, b).toLong()
        return (kind.toLong() shl 56) or (lo shl 24) or hi
    }

    fun addEdge(a: Int, b: Int, kind: Int, color: Color) {
        if (a == b || a < 0 || b < 0) return
        if (!seen.add(key(a, b, kind))) return
        edges += GEdge(a, b, kind, color)
        deg[a]++
        deg[b]++
    }

    val preColor = Color(0xFF8B5CF6)
    skillIds.forEachIndexed { i, id ->
        e.skills[id]?.pre?.forEach { pre ->
            val pi = skillIndex[pre] ?: return@forEach
            addEdge(i, pi, 0, preColor)
        }
    }
    e.paths.forEach { p ->
        val c = parseHexColor(p.color)
        for (k in 0 until p.skills.size - 1) {
            val a = skillIndex[p.skills[k]] ?: continue
            val b = skillIndex[p.skills[k + 1]] ?: continue
            addEdge(a, b, 1, c)
        }
        val last = p.skills.lastOrNull()?.let { skillIndex[it] }
        val jb = jobIndex[p.job]
        if (last != null && jb != null) addEdge(last, jb, 2, c)
    }

    // --- 力导向布局（确定性） ---
    val side = sqrt(n.toFloat()) * GSpec.SPACING * density
    val pos = forceLayout(n, edges.map { it.a to it.b }, side, GSpec.ITERATIONS)

    // --- 包围盒归一化 ---
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (i in 0 until n) {
        minX = min(minX, pos[2 * i]); maxX = max(maxX, pos[2 * i])
        minY = min(minY, pos[2 * i + 1]); maxY = max(maxY, pos[2 * i + 1])
    }
    val pad = GSpec.PADDING * density
    val dx = pad - minX
    val dy = pad - minY
    for (i in 0 until n) {
        pos[2 * i] += dx
        pos[2 * i + 1] += dy
    }
    val w = (maxX - minX) + pad * 2
    val h = (maxY - minY) + pad * 2

    // --- 节点 ---
    val nodes = ArrayList<GNode>(n)
    skillIds.forEachIndexed { i, id ->
        val s = e.skills[id] ?: return@forEachIndexed
        val d = deg[i]
        val r = (5.5f + min(d, 7) * 1.15f) * density
        nodes += GNode(
            id = id,
            isJob = false,
            name = s.name,
            cat = s.cat,
            x = pos[2 * i],
            y = pos[2 * i + 1],
            r = r,
            color = graphCatColor(s.cat),
            degree = d,
            pathIndex = -1,
            st = skillDone[id]?.st ?: "none",
            label = skillLabels[id] ?: skillLabels.values.first(),
        )
    }
    jobIds.forEachIndexed { j, id ->
        val idx = skillIds.size + j
        val jb = e.jobs[id]
        nodes += GNode(
            id = id,
            isJob = true,
            name = jb?.name ?: id,
            cat = "",
            x = pos[2 * idx],
            y = pos[2 * idx + 1],
            r = 13f * density,
            color = parseHexColor(jb?.color ?: "", graphCatColor("硬件")),
            degree = deg[idx],
            pathIndex = jobPath[id] ?: -1,
            st = "none",
            label = jobLabels[id] ?: jobLabels.values.first(),
        )
    }
    return GraphModel(nodes, edges, w, h)
}

/** Fruchterman-Reingold（确定性：初始位置为黄金角螺旋，无随机） */
private fun forceLayout(n: Int, edges: List<Pair<Int, Int>>, side: Float, iterations: Int): FloatArray {
    val pos = FloatArray(n * 2)
    val c = side / 2f
    for (i in 0 until n) {
        val a = i * 2.3999632f
        val rad = side * 0.44f * sqrt((i + 0.5f) / n)
        pos[2 * i] = c + rad * cos(a)
        pos[2 * i + 1] = c + rad * sin(a)
    }
    val k = side / sqrt(n.toFloat())
    val disp = FloatArray(n * 2)
    var t = side * 0.12f
    repeat(iterations) {
        java.util.Arrays.fill(disp, 0f)
        for (i in 0 until n) {
            val ix = pos[2 * i]
            val iy = pos[2 * i + 1]
            for (j in i + 1 until n) {
                var ddx = ix - pos[2 * j]
                var ddy = iy - pos[2 * j + 1]
                var d2 = ddx * ddx + ddy * ddy
                if (d2 < 1f) {
                    ddx = 1f + (i % 7) * 0.3f
                    ddy = 1f + (j % 5) * 0.3f
                    d2 = ddx * ddx + ddy * ddy
                }
                val d = sqrt(d2)
                val f = k * k / d
                val ux = ddx / d * f
                val uy = ddy / d * f
                disp[2 * i] += ux; disp[2 * i + 1] += uy
                disp[2 * j] -= ux; disp[2 * j + 1] -= uy
            }
        }
        edges.forEach { (a, b) ->
            val ddx = pos[2 * a] - pos[2 * b]
            val ddy = pos[2 * a + 1] - pos[2 * b + 1]
            val d = max(sqrt(ddx * ddx + ddy * ddy), 1f)
            val f = d * d / k
            val ux = ddx / d * f
            val uy = ddy / d * f
            disp[2 * a] -= ux; disp[2 * a + 1] -= uy
            disp[2 * b] += ux; disp[2 * b + 1] += uy
        }
        for (i in 0 until n) {
            val ddx = disp[2 * i]
            val ddy = disp[2 * i + 1]
            val dl = sqrt(ddx * ddx + ddy * ddy)
            if (dl > 0.0001f) {
                val step = min(dl, t)
                pos[2 * i] += ddx / dl * step
                pos[2 * i + 1] += ddy / dl * step
            }
            pos[2 * i] += (c - pos[2 * i]) * 0.008f
            pos[2 * i + 1] += (c - pos[2 * i + 1]) * 0.008f
        }
        t *= 0.976f
    }
    return pos
}

/* ============== 画布 ============== */

@Composable
fun GraphCanvas(
    ui: UiState,
    query: String,
    catFilter: String?,
    resetTick: Int,
    onSkillTap: (String) -> Unit,
    onJobOpen: (Int) -> Unit,
    onSkillLongPress: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val measurer = rememberTextMeasurer()

    val skillStyle = remember { TextStyle(fontSize = 10.sp, lineHeight = 13.sp) }
    val jobStyle = remember { TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp) }

    val skillLabels = remember(ui.effective.skills) {
        val c = Constraints(minWidth = 0, maxWidth = (78 * density).toInt())
        ui.effective.skills.mapValues { (_, s) ->
            measurer.measure(AnnotatedString(s.name), skillStyle, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = c)
        }
    }
    val jobLabels = remember(ui.effective.jobs) {
        val c = Constraints(minWidth = 0, maxWidth = (110 * density).toInt())
        ui.effective.jobs.mapValues { (_, j) ->
            measurer.measure(AnnotatedString(j.name), jobStyle, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = c)
        }
    }

    val model = remember(ui.effective, ui.skillDone) {
        buildGraph(ui.effective, ui.skillDone, density, skillLabels, jobLabels)
    }

    var scale by remember { mutableFloatStateOf(0.4f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var fitted by remember { mutableStateOf(false) }
    var camJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    val bg = androidx.compose.material3.MaterialTheme.colorScheme.background
    val onSurface = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant

    fun fitScale(size: IntSize): Float {
        if (model.w <= 0f || size.width == 0) return 1f
        return min(size.width / model.w, size.height / model.h).coerceIn(GSpec.MIN_SCALE, 1.2f)
    }

    fun fitOffset(size: IntSize, sc: Float): Offset =
        Offset((size.width - model.w * sc) / 2f, (size.height - model.h * sc) / 2f)

    fun clampOffset(o: Offset, sc: Float): Offset {
        if (canvasSize.width == 0) return o
        val margin = 60f * density
        val minX = canvasSize.width - model.w * sc - margin
        val minY = canvasSize.height - model.h * sc - margin
        val x = if (minX > margin) o.x else o.x.coerceIn(minX, margin)
        val y = if (minY > margin) o.y else o.y.coerceIn(minY, margin)
        return Offset(x, y)
    }

    fun animateTo(targetScale: Float, targetOffset: Offset) {
        camJob?.cancel()
        val s0 = scale
        val o0 = offset
        camJob = scope.launch {
            val t = Animatable(0f)
            t.animateTo(1f, spring(0.88f, Spring.StiffnessMediumLow)) {
                scale = s0 + (targetScale - s0) * t.value
                offset = o0 + (targetOffset - o0) * t.value
            }
            scale = targetScale
            offset = targetOffset
        }
    }

    LaunchedEffect(canvasSize, model) {
        if (!fitted && canvasSize.width > 0 && model.nodes.isNotEmpty()) {
            fitted = true
            val sc = fitScale(canvasSize)
            scale = sc
            offset = fitOffset(canvasSize, sc)
        }
    }
    LaunchedEffect(resetTick) {
        if (resetTick > 0 && canvasSize.width > 0 && model.nodes.isNotEmpty()) {
            val sc = fitScale(canvasSize)
            animateTo(sc, fitOffset(canvasSize, sc))
        }
    }

    // 搜索定位
    LaunchedEffect(query, model) {
        val q = query.trim().lowercase()
        if (q.isBlank() || canvasSize.width == 0) return@LaunchedEffect
        val hit = model.nodes.firstOrNull { !it.isJob && it.name.lowercase().contains(q) } ?: return@LaunchedEffect
        val sc = max(scale, 1f)
        animateTo(
            sc,
            clampOffset(
                Offset(canvasSize.width / 2f - hit.x * sc, canvasSize.height / 2f - hit.y * sc),
                sc,
            ),
        )
    }

    Canvas(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { canvasSize = it }
            .pointerInput(model, density) {
                detectTapGestures(
                    onTap = { p ->
                        val hit = hitTestGraph(model, p, offset, scale, density) ?: return@detectTapGestures
                        if (hit.isJob) {
                            if (hit.pathIndex >= 0) onJobOpen(hit.pathIndex)
                        } else {
                            onSkillTap(hit.id)
                        }
                    },
                    onLongPress = { p ->
                        val hit = hitTestGraph(model, p, offset, scale, density) ?: return@detectTapGestures
                        if (!hit.isJob) onSkillLongPress(hit.id)
                    },
                    onDoubleTap = { p ->
                        val target = if (scale > fitScale(canvasSize) * 2.2f) fitScale(canvasSize)
                        else min(max(scale * 2.1f, 0.9f), GSpec.MAX_SCALE)
                        val c = p - offset
                        animateTo(target, clampOffset(Offset(p.x - c.x * (target / scale), p.y - c.y * (target / scale)), target))
                    },
                )
            }
            .pointerInput(model) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    camJob?.cancel()
                    val ns = (scale * zoom).coerceIn(GSpec.MIN_SCALE, GSpec.MAX_SCALE)
                    val c = centroid - offset
                    offset = clampOffset(centroid - c * (ns / scale) + pan, ns)
                    scale = ns
                }
            },
    ) {
        if (model.nodes.isEmpty()) return@Canvas
        val s = scale
        val o = offset
        val visL = -o.x / s
        val visT = -o.y / s
        val visR = (size.width - o.x) / s
        val visB = (size.height - o.y) / s
        val q = query.trim().lowercase()
        val d = density

        withTransform({ translate(o.x, o.y); scale(s, s, Offset.Zero) }) {

            // ---- 边 ----
            model.edges.forEach { e ->
                val a = model.nodes[e.a]
                val b = model.nodes[e.b]
                if (a.x < visL - 80 && b.x < visL - 80) return@forEach
                if (a.x > visR + 80 && b.x > visR + 80) return@forEach
                if (a.y < visT - 80 && b.y < visT - 80) return@forEach
                if (a.y > visB + 80 && b.y > visB + 80) return@forEach

                val dim = edgeDim(a, b, q, catFilter)
                if (dim <= 0.02f) return@forEach
                val alpha = when (e.kind) {
                    0 -> 0.42f
                    1 -> 0.20f
                    else -> 0.62f
                } * dim
                val base = when (e.kind) {
                    0 -> 1.2f
                    1 -> 1f
                    else -> 1.6f
                } * d
                // 缩小后线会细到亚像素，这里做下限补偿（保证屏幕上至少 ~1px）
                val width = max(base, 1.05f * d / max(s, 0.2f))
                drawLine(
                    color = e.color.copy(alpha = alpha),
                    start = Offset(a.x, a.y),
                    end = Offset(b.x, b.y),
                    strokeWidth = width,
                    cap = StrokeCap.Round,
                )
            }

            // ---- 节点 + 标签 ----
            model.nodes.forEach { nd ->
                if (nd.x < visL - 120 || nd.x > visR + 120 || nd.y < visT - 120 || nd.y > visB + 120) return@forEach
                val a = nodeDim(nd, q, catFilter)
                if (a <= 0.02f) return@forEach
                val c = Offset(nd.x, nd.y)

                if (nd.isJob) {
                    // 岗位：圆角方块 + 白描边，视觉上与技能区分
                    val hw = nd.r * 1.5f
                    val hh = nd.r * 0.86f
                    drawCircle(bg, radius = nd.r * 1.2f, center = c, alpha = a)
                    drawRoundRect(
                        color = nd.color,
                        topLeft = Offset(nd.x - hw, nd.y - hh),
                        size = Size(hw * 2f, hh * 2f),
                        cornerRadius = CornerRadius(hh * 0.55f, hh * 0.55f),
                        alpha = a,
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.92f),
                        topLeft = Offset(nd.x - hw, nd.y - hh),
                        size = Size(hw * 2f, hh * 2f),
                        cornerRadius = CornerRadius(hh * 0.55f, hh * 0.55f),
                        alpha = a,
                        style = Stroke(1.6f * d),
                    )

                } else {
                    drawCircle(bg, radius = nd.r + 2.2f * d, center = c, alpha = a)
                    drawCircle(nd.color, radius = nd.r, center = c, alpha = a)
                    if (nd.st != "none") {
                        drawCircle(
                            color = if (nd.st == "done") Color(0xFF10B981) else Color(0xFFF59E0B),
                            radius = nd.r,
                            center = c,
                            alpha = a,
                            style = Stroke(2.6f * d),
                        )
                    }
                }
            }
        }

        // ---- 名称层：屏幕空间绘制（字号恒定 10/11sp，不随缩放变形）----
        // 岗位优先、技能按连接度数排序，逐个贪心避让：位置挤不下的自动让位，
        // 于是「缩小只显示大节点，放大逐步显出更多名字」，任何缩放级别都可读。
        val placed = ArrayList<androidx.compose.ui.geometry.Rect>(96)
        val cands = ArrayList<GNode>(96)
        model.nodes.forEach { nd ->
            val sx = nd.x * s + o.x
            val sy = nd.y * s + o.y
            if (sx < -140f || sx > size.width + 140f || sy < -50f || sy > size.height + 50f) return@forEach
            if (nodeDim(nd, q, catFilter) < 0.5f) return@forEach
            cands += nd
        }
        cands.sortWith(compareByDescending<GNode> { if (it.isJob) 1_000_000 + it.degree else it.degree })
        cands.forEach { nd ->
            val sx = nd.x * s + o.x
            val sy = nd.y * s + o.y
            val lw = nd.label.size.width.toFloat()
            val lh = nd.label.size.height.toFloat()
            val top = sy + nd.r * s + 2f * d
            // 水平钳制在画布内，避免长名称被屏幕边缘裁掉
            val cx = (sx - lw / 2f).coerceIn(4f * d, (size.width - lw - 4f * d).coerceAtLeast(4f * d))
            val rect = androidx.compose.ui.geometry.Rect(cx - 3f * d, top - 1.5f * d, cx + lw + 3f * d, top + lh + 1.5f * d)
            if (placed.any { it.overlaps(rect) }) return@forEach
            placed += rect
            drawRoundRect(
                color = bg.copy(alpha = if (nd.isJob) 0.82f else 0.66f),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                cornerRadius = CornerRadius(3f * d, 3f * d),
            )
            drawText(nd.label, color = if (nd.isJob) onSurface else onSurfaceVariant, topLeft = Offset(cx, top))
        }
    }
}

private fun nodeDim(n: GNode, q: String, catFilter: String?): Float {
    if (n.isJob) return 1f
    if (q.isNotEmpty() && !n.name.lowercase().contains(q)) return 0.13f
    if (catFilter != null && n.cat != catFilter) return 0.13f
    return 1f
}

private fun edgeDim(a: GNode, b: GNode, q: String, catFilter: String?): Float {
    val ja = a.isJob
    val jb = b.isJob
    if (q.isNotEmpty()) {
        val ok = (!ja && a.name.lowercase().contains(q)) || (!jb && b.name.lowercase().contains(q))
        if (!ok) return 0.12f
    }
    if (catFilter != null) {
        val ok = (ja || a.cat == catFilter) || (jb || b.cat == catFilter)
        if (!ok) return 0.1f
    }
    return 1f
}

private class GHit(val id: String, val isJob: Boolean, val pathIndex: Int)

private fun hitTestGraph(model: GraphModel, p: Offset, offset: Offset, scale: Float, density: Float): GHit? {
    val c = (p - offset) / scale
    var best: GHit? = null
    var bestScore = Float.MAX_VALUE
    model.nodes.forEach { n ->
        // 命中半径按「屏幕 24dp」折算回内容坐标：缩小后节点只剩几像素，但仍要能点中
        val reach = max(n.r * 1.4f, 24f * density / scale.coerceAtLeast(0.05f))
        val dx = abs(c.x - n.x)
        val dy = abs(c.y - n.y)
        // 圆点区
        var hitDist = Float.MAX_VALUE
        if (dx < reach && dy < reach) hitDist = sqrt(dx * dx + dy * dy)
        // 名称标签区（画在节点下方，按屏幕字号换算回内容坐标 → 点字也能命中）
        if (hitDist == Float.MAX_VALUE) {
            val lw = n.label.size.width / scale
            val lh = n.label.size.height / scale
            val top = n.y + n.r
            if (c.x > n.x - lw / 2f - 6f && c.x < n.x + lw / 2f + 6f && c.y > top && c.y < top + lh + 6f) {
                hitDist = (c.y - top) * 0.6f
            }
        }
        if (hitDist < Float.MAX_VALUE) {
            val score = hitDist - (if (n.isJob) 8f * density else 0f)
            if (score < bestScore) {
                bestScore = score
                best = GHit(n.id, n.isJob, n.pathIndex)
            }
        }
    }
    return best
}

private fun graphCatColor(cat: String): Color = when (cat) {
    "语言基础" -> Color(0xFF64748B)
    "硬件" -> Color(0xFF2563EB)
    "系统" -> Color(0xFF0EA5E9)
    "协议" -> Color(0xFF8B5CF6)
    "AI/EDA" -> Color(0xFF14B8A6)
    else -> Color(0xFF64748B)
}

/* ============== 模式切换 / HUD / 图例 ============== */

/** 分类色（供 chips 使用） */
internal fun graphCatColorPublic(cat: String): Color = graphCatColor(cat)

/** 路线 / 网图 切换胶囊 */
@Composable
fun ModeToggle(mode: String, onChange: (String) -> Unit) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
        color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .padding(start = 12.dp)
            .height(34.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.padding(3.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            ModeSegment("路线", mode == "route") { onChange("route") }
            ModeSegment("网图", mode == "graph") { onChange("graph") }
        }
    }
}

@Composable
private fun ModeSegment(text: String, selected: Boolean, onClick: () -> Unit) {
    val cs = androidx.compose.material3.MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxHeight()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
            .background(if (selected) cs.primary else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = if (selected) cs.onPrimary else cs.onSurfaceVariant,
        )
    }
}

/** 网图模式 HUD：规模 + 操作提示 + 复位 */
@Composable
fun GraphHud(skillCount: Int, jobCount: Int, pathCount: Int, onReset: () -> Unit) {
    val cs = androidx.compose.material3.MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            androidx.compose.material3.Text(
                "关系网 · $skillCount 技能 / $jobCount 岗位 / $pathCount 路线",
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = cs.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            androidx.compose.material3.Text(
                "点技能看详情 · 点岗位进入路线 · 双指缩放",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
            )
        }
        androidx.compose.material3.TextButton(onClick = onReset, modifier = Modifier.height(36.dp)) {
            androidx.compose.material3.Text(
                "全览",
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
        }
    }
}

/** 网图底部图例 */
@Composable
fun GraphLegend() {
    val cs = androidx.compose.material3.MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(cs.surface)
            .padding(horizontal = 12.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        listOf("语言基础", "硬件", "系统", "协议", "AI/EDA").forEach { cat ->
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .size(7.dp)
                        .background(graphCatColor(cat), androidx.compose.foundation.shape.CircleShape),
                )
                androidx.compose.material3.Text(
                    cat,
                    Modifier.padding(start = 4.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
