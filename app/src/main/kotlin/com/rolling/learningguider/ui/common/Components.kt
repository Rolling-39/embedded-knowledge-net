package com.rolling.learningguider.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ============== 颜色（与网页版一致的分类/层级色系） ============== */

private val CAT_COLORS = mapOf(
    "语言基础" to Color(0xFF64748B),
    "硬件" to Color(0xFF2563EB),
    "系统" to Color(0xFF0EA5E9),
    "协议" to Color(0xFF8B5CF6),
    "AI/EDA" to Color(0xFF14B8A6),
)

@Composable
fun catColor(cat: String): Color = CAT_COLORS[cat] ?: MaterialTheme.colorScheme.primary

@Composable
fun levelColor(level: String): Color = when (level) {
    "基础" -> Color(0xFF10B981)
    "高阶" -> Color(0xFFEF4444)
    else -> Color(0xFFF59E0B)
}

val StatusDone = Color(0xFF10B981)
val StatusLearning = Color(0xFFF59E0B)
val StatusNone = Color(0xFF94A3B8)

@Composable
fun statusColor(st: String): Color = when (st) {
    "done" -> StatusDone
    "learning" -> StatusLearning
    else -> StatusNone
}

/* ============== 基础组件 ============== */

/** 卡片按压反馈（缩放 + ripple），手机端对应网页版的悬停/按压动效 */
fun Modifier.pressable(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = 600f),
        label = "pressScale",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(vertical = 8.dp),
    )
}

@Composable
fun InfoLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = modifier, maxLines = maxLines, overflow = overflow)
}

/** "#10b981" → Color；非法值回退主色 */
fun parseHexColor(hex: String, fallback: Color = Color(0xFF2563EB)): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex.trim()))
}.getOrDefault(fallback)

/** 小圆点（路径色/状态色） */
@Composable
fun Dot(color: Color, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(color, CircleShape),
    )
}

/** 小标签 chip */
@Composable
fun TagChip(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
    filled: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val bg = if (filled) color else color.copy(alpha = 0.12f)
    val fg = if (filled) Color.White else color
    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.pressable(onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = text, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** 星级行（难度/市场需求），文本星避免彩色 emoji */
@Composable
fun StarRow(count: Int, total: Int = 5, color: Color = Color(0xFFF59E0B), starSize: TextUnit = 14.sp) {
    Row {
        for (i in 1..total) {
            Text(
                text = "★",
                color = if (i <= count) color else MaterialTheme.colorScheme.outline,
                fontSize = starSize,
            )
        }
    }
}

/* ============== 进度可视化 ============== */

/** 环形进度（带生长动画与数字滚动） */
@Composable
fun ProgressRing(
    percent: Int,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    ringColor: Color = MaterialTheme.colorScheme.primary,
) {
    val target = percent.coerceIn(0, 100).toFloat()
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = if (animate) tween(900) else snap(),
        label = "ring",
    )
    Box(modifier = modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            drawArc(color = ringColor.copy(alpha = 0.15f), startAngle = 0f, sweepAngle = 360f, useCenter = false, style = stroke)
            drawArc(color = ringColor, startAngle = -90f, sweepAngle = 3.6f * animated, useCenter = false, style = stroke)
        }
        Text("${(animated + 0.5f).toInt()}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

/** 三色进度条：已学(绿)/学习中(橙)/未学(灰) */
@Composable
fun ThreeColorBar(done: Int, learning: Int, total: Int, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val remaining = (total - done - learning).coerceAtLeast(0)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(4.dp)),
    ) {
        if (done + learning + remaining == 0) {
            Box(Modifier.fillMaxWidth().fillMaxHeight().background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
        } else {
            if (done > 0) Box(Modifier.weight(done.toFloat()).fillMaxHeight().background(StatusDone))
            if (learning > 0) Box(Modifier.weight(learning.toFloat()).fillMaxHeight().background(StatusLearning))
            if (remaining > 0) Box(Modifier.weight(remaining.toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
        }
    }
}

/** 分类进度行：色条 + 名称 + N/M */
@Composable
fun CategoryProgressRow(cat: String, done: Int, learning: Int, total: Int) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(catColor(cat), 10.dp)
            Text(
                cat, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
            )
            Text("$done / $total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ThreeColorBar(done, learning, total, Modifier.padding(top = 6.dp))
    }
}
