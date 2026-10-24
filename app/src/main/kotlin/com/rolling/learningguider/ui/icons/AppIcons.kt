package com.rolling.learningguider.ui.icons

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 应用内矢量图标 —— path 数据直接取自网页版 embed-kb.html 的 ICONS，
 * 保证两端视觉语言一致。描边风格（stroke 2 / round）与网页版相同。
 */
object AppIcons {

    private fun build(name: String, builder: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(builder).build()

    private val stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black)

    private fun ImageVector.Builder.stroked(builder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) {
        path(
            stroke = stroke,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = builder,
        )
    }

    /** 知识网：三节点连线图（网页版 graph 图标） */
    val Net: ImageVector by lazy {
        build("Net") {
            stroked {
                circle(12f, 5f, 2f); circle(5f, 18f, 2f); circle(19f, 18f, 2f)
                moveTo(12f, 7f); verticalLineTo(10f)
                moveTo(7f, 16f); lineTo(12f, 10f)
                moveTo(17f, 16f); lineTo(12f, 10f)
            }
        }
    }

    /** 知识库：书本（网页版 book 图标） */
    val Library: ImageVector by lazy {
        build("Library") {
            stroked {
                moveTo(4f, 5f)
                curveTo(4f, 3.9f, 4.9f, 3f, 6f, 3f)
                horizontalLineTo(18f)
                curveTo(19.1f, 3f, 20f, 3.9f, 20f, 5f)
                verticalLineTo(19f)
                lineTo(17f, 17f)
                lineTo(14f, 19f)
                lineTo(11f, 17f)
                lineTo(8f, 19f)
                verticalLineTo(5f)
                close()
            }
        }
    }

    /** 进度：柱状图（网页版 chart 图标） */
    val Progress: ImageVector by lazy {
        build("Progress") {
            stroked {
                moveTo(4f, 20f); verticalLineTo(10f)
                moveTo(9f, 20f); verticalLineTo(4f)
                moveTo(14f, 20f); verticalLineTo(13f)
                moveTo(19f, 20f); verticalLineTo(8f)
            }
        }
    }

    /** 我的：人形（Material person path） */
    val Me: ImageVector by lazy {
        build("Me") {
            path(fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black)) {
                moveTo(12f, 12f)
                curveTo(14.21f, 12f, 16f, 10.21f, 16f, 8f)
                curveTo(16f, 5.79f, 14.21f, 4f, 12f, 4f)
                curveTo(9.79f, 4f, 8f, 5.79f, 8f, 8f)
                curveTo(8f, 10.21f, 9.79f, 12f, 12f, 12f)
                close()
                moveTo(12f, 14f)
                curveTo(9.33f, 14f, 4f, 15.34f, 4f, 18f)
                verticalLineTo(20f)
                horizontalLineTo(20f)
                verticalLineTo(18f)
                curveTo(20f, 15.34f, 14.67f, 14f, 12f, 14f)
                close()
            }
        }
    }


    /** 搜索（Material search path，填充） */
    val Search: ImageVector by lazy {
        build("Search") {
            path(fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black)) {
                moveTo(15.5f, 14f)
                horizontalLineTo(14.71f)
                lineTo(14.43f, 13.73f)
                curveTo(15.41f, 12.59f, 16f, 11.11f, 16f, 9.5f)
                curveTo(16f, 5.91f, 13.09f, 3f, 9.5f, 3f)
                reflectiveCurveTo(3f, 5.91f, 3f, 9.5f)
                reflectiveCurveTo(5.91f, 16f, 9.5f, 16f)
                curveTo(11.11f, 16f, 12.59f, 15.41f, 13.73f, 14.43f)
                lineTo(14f, 14.71f)
                verticalLineTo(15.5f)
                lineTo(19f, 20.49f)
                lineTo(20.49f, 19f)
                close()
                moveTo(9.5f, 14f)
                curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f)
                reflectiveCurveTo(7.01f, 5f, 9.5f, 5f)
                reflectiveCurveTo(14f, 7.01f, 14f, 9.5f)
                reflectiveCurveTo(11.99f, 14f, 9.5f, 14f)
                close()
            }
        }
    }

    /** 关闭（Material close path，填充） */
    val Close: ImageVector by lazy {
        build("Close") {
            path(fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black)) {
                moveTo(19f, 6.41f)
                lineTo(17.59f, 5f)
                lineTo(12f, 10.59f)
                lineTo(6.41f, 5f)
                lineTo(5f, 6.41f)
                lineTo(10.59f, 12f)
                lineTo(5f, 17.59f)
                lineTo(6.41f, 19f)
                lineTo(12f, 13.41f)
                lineTo(17.59f, 19f)
                lineTo(19f, 17.59f)
                lineTo(13.41f, 12f)
                close()
            }
        }
    }

    /** 用三次贝塞尔在 PathBuilder 里画圆（k ≈ 0.5523） */
    private fun androidx.compose.ui.graphics.vector.PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        val k = 0.5523f * r
        moveTo(cx + r, cy)
        curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
        curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
        curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
        curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
        close()
    }
}
