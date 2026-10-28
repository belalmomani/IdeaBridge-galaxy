package com.ideabridge.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ideabridge.app.R
import com.ideabridge.app.data.IdeaEntity
import com.ideabridge.app.data.RelationEntity
import com.ideabridge.app.engine.GalaxyLayout
import com.ideabridge.app.engine.LinkSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * عرض الكواكب: كل ملاحظة كوكب متوهج، والروابط المحفوظة خطوط ثابتة، والاقتراحات خطوط متقطعة خافتة.
 * سحب بإصبع للتحريك، وقرص بإصبعين للتكبير، ولمسة على كوكب لفتح ملاحظته.
 */
@Composable
fun GalaxyGraph(
    notes: List<IdeaEntity>,
    relations: List<RelationEntity>,
    suggestions: List<LinkSuggestion>,
    showSuggestions: Boolean,
    color: Color,
    onOpenNote: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (notes.isEmpty()) {
        Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.graph_empty), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val density = LocalDensity.current.density
    val textMeasurer = rememberTextMeasurer()
    val now = remember { System.currentTimeMillis() }
    val ids = remember(notes) { notes.map { it.id } }
    val edges = remember(relations) { relations.map { it.firstIdeaId to it.secondIdeaId } }
    val degree = remember(relations) {
        val map = HashMap<Long, Int>()
        relations.forEach {
            map[it.firstIdeaId] = (map[it.firstIdeaId] ?: 0) + 1
            map[it.secondIdeaId] = (map[it.secondIdeaId] ?: 0) + 1
        }
        map
    }

    // التخطيط يُحسب خارج الخيط الرئيسي حتى لا تتجمد الواجهة مع كثرة الملاحظات
    val layout by produceState<Map<Long, GalaxyLayout.Point>>(emptyMap(), ids, edges) {
        value = withContext(Dispatchers.Default) {
            GalaxyLayout.compute(ids, edges, iterations = if (ids.size > 400) 60 else 150)
        }
    }

    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    var panX by rememberSaveable { mutableFloatStateOf(0f) }
    var panY by rememberSaveable { mutableFloatStateOf(0f) }

    fun project(p: GalaxyLayout.Point, w: Float, h: Float): Offset {
        val r = min(w, h) / 2f * 0.8f
        return Offset(w / 2f + panX + p.x * r * scale, h / 2f + panY + p.y * r * scale)
    }

    fun radiusPx(n: IdeaEntity): Float {
        val len = n.title.length + (n.description?.length ?: 0)
        return GalaxyLayout.planetRadius(len, degree[n.id] ?: 0) * density * scale.coerceIn(0.7f, 1.6f)
    }

    Box(
        modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 4f)
                    panX += pan.x
                    panY += pan.y
                }
            }
            .pointerInput(notes, layout) {
                detectTapGestures { tap ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    var bestId: Long? = null
                    var bestDistance = Float.MAX_VALUE
                    for (n in notes) {
                        val p = layout[n.id] ?: continue
                        val d = (project(p, w, h) - tap).getDistance()
                        if (d <= radiusPx(n) + 24f * density && d < bestDistance) {
                            bestId = n.id
                            bestDistance = d
                        }
                    }
                    bestId?.let(onOpenNote)
                }
            }
    ) {
        val labelStyle = TextStyle(color = Color.White, fontSize = 12.sp)
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (showSuggestions) {
                for (s in suggestions) {
                    val a = layout[s.firstId] ?: continue
                    val b = layout[s.secondId] ?: continue
                    drawLine(
                        color = color.copy(alpha = 0.45f),
                        start = project(a, w, h), end = project(b, w, h),
                        strokeWidth = 2f * density,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f * density, 10f * density)),
                    )
                }
            }
            for (r in relations) {
                val a = layout[r.firstIdeaId] ?: continue
                val b = layout[r.secondIdeaId] ?: continue
                drawLine(
                    color = Color.White.copy(alpha = 0.6f),
                    start = project(a, w, h), end = project(b, w, h),
                    strokeWidth = 2f * density,
                )
            }
            val showLabels = notes.size <= 120 || scale >= 1.5f
            for (n in notes) {
                val p = layout[n.id] ?: continue
                val c = project(p, w, h)
                val r = radiusPx(n)
                val recent = now - n.updatedAt < 86_400_000L // الملاحظة الحديثة تتوهج أكثر
                val haloRadius = r * (if (recent) 3.2f else 2.4f)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(color.copy(alpha = if (recent) 0.55f else 0.30f), Color.Transparent),
                        center = c, radius = haloRadius,
                    ),
                    radius = haloRadius, center = c,
                )
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color.White, color), center = c, radius = r),
                    radius = r, center = c,
                )
                if (showLabels) {
                    val label = textMeasurer.measure(
                        text = n.title,
                        style = labelStyle,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        constraints = Constraints(maxWidth = (110f * density).toInt()),
                    )
                    drawText(label, topLeft = Offset(c.x - label.size.width / 2f, c.y + r + 4f * density))
                }
            }
        }
        Text(
            stringResource(R.string.graph_hint),
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
