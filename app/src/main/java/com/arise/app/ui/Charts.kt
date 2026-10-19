package com.arise.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** Single-series charts: one hue, thin marks, recessive grid, tap a mark for its value. */

@Composable
private fun ChartFrame(title: String, subtitle: String, readout: String?, content: @Composable () -> Unit) {
    Panel {
        SysLabel(title)
        Text(subtitle, color = C.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            readout ?: "Tap the chart to see values",
            color = if (readout != null) C.Text else C.Muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

private fun DrawScope.label(m: TextMeasurer, text: String, x: Float, y: Float, center: Boolean = true) {
    val tl = m.measure(text, TextStyle(color = C.Muted, fontSize = 10.sp))
    val w = tl.size.width.toFloat()
    val left = if (center) (x - w / 2f).coerceIn(0f, (size.width - w).coerceAtLeast(0f)) else x
    drawText(tl, topLeft = Offset(left, y))
}

private fun DrawScope.grid(plotH: Float) {
    for (g in 0..2) {
        val y = plotH * g / 2f
        drawLine(C.Grid, Offset(0f, y), Offset(size.width, y), 1f)
    }
    drawLine(C.Axis, Offset(0f, plotH), Offset(size.width, plotH), 1f)
}

private fun DrawScope.targetLine(y: Float) {
    drawLine(
        C.Gold.copy(alpha = 0.8f), Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
    )
}

private fun DrawScope.xLabels(m: TextMeasurer, labels: List<String>, slot: Float, y: Float) {
    if (labels.isEmpty()) return
    listOf(0, labels.size / 2, labels.lastIndex).distinct().forEach { i ->
        label(m, labels[i], i * slot + slot / 2f, y)
    }
}

@Composable
fun BarChart(
    title: String,
    subtitle: String,
    labels: List<String>,
    values: List<Float?>,
    format: (Float) -> String,
    target: Float? = null,
) {
    var sel by remember(values) { mutableStateOf<Int?>(null) }
    val m = rememberTextMeasurer()
    val readout = sel?.let { i -> "${labels[i]} · ${values[i]?.let(format) ?: "no data"}" }
    ChartFrame(title, subtitle, readout) {
        val top = maxOf(values.filterNotNull().maxOrNull() ?: 0f, target ?: 0f).let { if (it <= 0f) 1f else it * 1.15f }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .pointerInput(values) {
                    detectTapGestures { o ->
                        if (values.isNotEmpty()) {
                            val slot = size.width.toFloat() / values.size
                            sel = (o.x / slot).toInt().coerceIn(0, values.lastIndex)
                        }
                    }
                },
        ) {
            if (values.isEmpty()) return@Canvas
            val axisH = 18.dp.toPx()
            val plotH = size.height - axisH
            grid(plotH)
            label(m, format(top), 0f, 2f, center = false)
            val slot = size.width / values.size
            val bw = minOf(slot * 0.6f, 16.dp.toPx())
            val r = 4.dp.toPx()
            values.forEachIndexed { i, v ->
                if (v != null && v > 0f) {
                    val h = plotH * (v / top)
                    val left = i * slot + (slot - bw) / 2f
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                Rect(left, plotH - h, left + bw, plotH),
                                topLeft = CornerRadius(r), topRight = CornerRadius(r),
                                bottomLeft = CornerRadius.Zero, bottomRight = CornerRadius.Zero,
                            ),
                        )
                    }
                    drawPath(path, if (sel == null || sel == i) C.Accent else C.Accent.copy(alpha = 0.45f))
                }
            }
            target?.let { targetLine(plotH * (1f - it / top)) }
            xLabels(m, labels, slot, plotH + 4.dp.toPx())
        }
    }
}

@Composable
fun LineChart(
    title: String,
    subtitle: String,
    labels: List<String>,
    values: List<Float?>,
    format: (Float) -> String,
    target: Float? = null,
    invertY: Boolean = false,
) {
    var sel by remember(values) { mutableStateOf<Int?>(null) }
    val m = rememberTextMeasurer()
    val readout = sel?.let { i -> "${labels[i]} · ${values[i]?.let(format) ?: "no data"}" }
    val present = values.filterNotNull() + listOfNotNull(target)
    ChartFrame(title, subtitle, readout) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .pointerInput(values) {
                    detectTapGestures { o ->
                        if (values.isNotEmpty()) {
                            val slot = size.width.toFloat() / values.size
                            val raw = (o.x / slot).toInt().coerceIn(0, values.lastIndex)
                            // snap to the closest point that has data
                            sel = values.indices.filter { values[it] != null }.minByOrNull { abs(it - raw) } ?: raw
                        }
                    }
                },
        ) {
            if (values.isEmpty() || present.isEmpty()) return@Canvas
            val axisH = 18.dp.toPx()
            val padTop = 14.dp.toPx()
            val plotH = size.height - axisH
            var lo = present.min()
            var hi = present.max()
            if (hi - lo < 1f) { lo -= 1f; hi += 1f }
            val span = hi - lo
            lo -= span * 0.1f
            hi += span * 0.1f
            fun y(v: Float): Float {
                val f = (v - lo) / (hi - lo)
                return padTop + (plotH - padTop) * (if (invertY) f else 1f - f)
            }
            grid(plotH)
            label(m, format(if (invertY) lo else hi), 0f, 2f, center = false)
            target?.let { targetLine(y(it)) }
            val slot = size.width / values.size
            val pts = values.mapIndexedNotNull { i, v -> v?.let { i to Offset(i * slot + slot / 2f, y(it)) } }
            if (pts.size > 1) {
                val path = Path().apply {
                    moveTo(pts[0].second.x, pts[0].second.y)
                    pts.drop(1).forEach { lineTo(it.second.x, it.second.y) }
                }
                drawPath(path, C.Accent, style = Stroke(width = 2.dp.toPx()))
            }
            val rad = 4.dp.toPx()
            pts.forEach { (i, p) ->
                drawCircle(C.Panel, rad + 2.dp.toPx(), p)
                drawCircle(if (sel == null || sel == i) C.Accent else C.Accent.copy(alpha = 0.5f), rad, p)
            }
            xLabels(m, labels, slot, plotH + 4.dp.toPx())
        }
    }
}

/** Weeks as columns, Sunday→Saturday as rows. One hue, dim → bright with completion. */
@Composable
fun Heatmap(
    title: String,
    subtitle: String,
    weeks: Int,
    ratio: (week: Int, day: Int) -> Float?,
    describe: (week: Int, day: Int) -> String,
) {
    var sel by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val readout = sel?.let { (w, d) -> describe(w, d) }
    ChartFrame(title, subtitle, readout) {
        val gap = 2.dp
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .pointerInput(weeks) {
                    detectTapGestures { o ->
                        val cell = minOf(size.width.toFloat() / weeks, size.height.toFloat() / 7f)
                        val w = (o.x / cell).toInt()
                        val d = (o.y / cell).toInt()
                        if (w in 0 until weeks && d in 0..6) sel = w to d
                    }
                },
        ) {
            val cell = minOf(size.width / weeks, size.height / 7f)
            val g = gap.toPx()
            for (w in 0 until weeks) for (d in 0..6) {
                val r = ratio(w, d)
                val color: Color = when {
                    r == null -> C.Grid.copy(alpha = 0.35f)
                    r <= 0f -> C.Grid
                    else -> lerp(C.SeqLow, C.Accent, r.coerceIn(0f, 1f))
                }
                drawRoundRect(
                    color,
                    topLeft = Offset(w * cell + g / 2, d * cell + g / 2),
                    size = androidx.compose.ui.geometry.Size(cell - g, cell - g),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
                if (sel == w to d) {
                    drawRoundRect(
                        C.Text,
                        topLeft = Offset(w * cell + g / 2, d * cell + g / 2),
                        size = androidx.compose.ui.geometry.Size(cell - g, cell - g),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                        style = Stroke(1.5.dp.toPx()),
                    )
                }
            }
        }
    }
}
