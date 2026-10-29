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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.ideabridge.app.data.GalaxyRow
import com.ideabridge.app.data.IdeaEntity
import com.ideabridge.app.data.RelationEntity
import com.ideabridge.app.engine.GalaxyLayout
import com.ideabridge.app.engine.LinkSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

/** عقدة في الرسم (كوكب ملاحظة أو مجرة). radius بوحدة نسبية تُضرب في الكثافة والتكبير. */
data class GraphNode(
    val id: Long,
    val label: String,
    val radius: Float,
    val glow: Boolean,
    val color: Color,
)

/**
 * رسم عام للكواكب والخطوط: سحب بإصبع للتحريك، وقرص للتكبير، ولمسة على عقدة لفتحها.
 * solidEdges خطوط ثابتة (روابط محفوظة)، وdashedEdges خطوط متقطعة (اقتراحات نصية).
 */
@Composable
fun PlanetGraph(
    nodes: List<GraphNode>,
    solidEdges: List<Pair<Long, Long>>,
    dashedEdges: List<Pair<Long, Long>>,
    layoutEdges: List<Pair<Long, Long>>,
    dashedColor: Color,
    emptyText: String,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (nodes.isEmpty()) {
        Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            Text(emptyText, style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val density = LocalDensity.current.density
    val textMeasurer = rememberTextMeasurer()
    val ids = remember(nodes) { nodes.map { it.id } }

    // التخطيط يُحسب خارج الخيط الرئيسي حتى لا تتجمد الواجهة مع كثرة العقد
    val layout by produceState<Map<Long, GalaxyLayout.Point>>(emptyMap(), ids, layoutEdges) {
        value = withContext(Dispatchers.Default) {
            GalaxyLayout.compute(ids, layoutEdges, iterations = if (ids.size > 400) 60 else 150)
        }
    }

    var scale by rememberSaveable { mutableStateOf(1f) }
    var panX by rememberSaveable { mutableStateOf(0f) }
    var panY by rememberSaveable { mutableStateOf(0f) }

    fun project(p: GalaxyLayout.Point, w: Float, h: Float): Offset {
        val r = min(w, h) / 2f * 0.8f
        return Offset(w / 2f + panX + p.x * r * scale, h / 2f + panY + p.y * r * scale)
    }

    fun radiusPx(n: GraphNode): Float = n.radius * density * scale.coerceIn(0.7f, 1.6f)

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
            .pointerInput(nodes, layout) {
                detectTapGestures { tap ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    var bestId: Long? = null
                    var bestDistance = Float.MAX_VALUE
                    for (n in nodes) {
                        val p = layout[n.id] ?: continue
                        val d = (project(p, w, h) - tap).getDistance()
                        if (d <= radiusPx(n) + 24f * density && d < bestDistance) {
                            bestId = n.id
                            bestDistance = d
                        }
                    }
                    bestId?.let(onOpen)
                }
            }
    ) {
        val labelStyle = TextStyle(color = Color.White, fontSize = 12.sp)
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            for ((a, b) in dashedEdges) {
                val pa = layout[a] ?: continue
                val pb = layout[b] ?: continue
                drawLine(
                    color = dashedColor.copy(alpha = 0.55f),
                    start = project(pa, w, h), end = project(pb, w, h),
                    strokeWidth = 2f * density,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f * density, 10f * density)),
                )
            }
            for ((a, b) in solidEdges) {
                val pa = layout[a] ?: continue
                val pb = layout[b] ?: continue
                drawLine(
                    color = Color.White.copy(alpha = 0.6f),
                    start = project(pa, w, h), end = project(pb, w, h),
                    strokeWidth = 2f * density,
                )
            }
            val showLabels = nodes.size <= 120 || scale >= 1.5f
            for (n in nodes) {
                val p = layout[n.id] ?: continue
                val c = project(p, w, h)
                val r = radiusPx(n)
                val haloRadius = r * (if (n.glow) 3.2f else 2.4f)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(n.color.copy(alpha = if (n.glow) 0.55f else 0.30f), Color.Transparent),
                        center = c, radius = haloRadius,
                    ),
                    radius = haloRadius, center = c,
                )
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color.White, n.color), center = c, radius = r),
                    radius = r, center = c,
                )
                if (showLabels) {
                    val label = textMeasurer.measure(
                        text = n.label,
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

/** كواكب ملاحظات مجرة: الروابط المحفوظة ثابتة، والاقتراحات متقطعة، والملاحظة المعدّلة خلال 24 ساعة تتوهج. */
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
    val now = remember { System.currentTimeMillis() }
    val solid = remember(relations) { relations.map { it.firstIdeaId to it.secondIdeaId } }
    val nodes = remember(notes, relations, color) {
        val degree = HashMap<Long, Int>()
        relations.forEach {
            degree[it.firstIdeaId] = (degree[it.firstIdeaId] ?: 0) + 1
            degree[it.secondIdeaId] = (degree[it.secondIdeaId] ?: 0) + 1
        }
        notes.map {
            val len = it.title.length + (it.description?.length ?: 0)
            GraphNode(it.id, it.title, GalaxyLayout.planetRadius(len, degree[it.id] ?: 0), now - it.updatedAt < 86_400_000L, color)
        }
    }
    val dashed = remember(suggestions, showSuggestions) {
        if (showSuggestions) suggestions.map { it.firstId to it.secondId } else emptyList()
    }
    PlanetGraph(
        nodes = nodes, solidEdges = solid, dashedEdges = dashed, layoutEdges = solid,
        dashedColor = color, emptyText = stringResource(R.string.graph_empty),
        onOpen = onOpenNote, modifier = modifier,
    )
}

/** خريطة المجرات: كل مجرة كوكب بلونها، والخطوط المتقطعة روابط نصية بين المجرات المتقاربة. */
@Composable
fun GalaxyMapGraph(
    galaxies: List<GalaxyRow>,
    links: List<LinkSuggestion>,
    onOpenGalaxy: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val edges = remember(links) { links.map { it.firstId to it.secondId } }
    val nodes = remember(galaxies, links) {
        val degree = HashMap<Long, Int>()
        links.forEach {
            degree[it.firstId] = (degree[it.firstId] ?: 0) + 1
            degree[it.secondId] = (degree[it.secondId] ?: 0) + 1
        }
        galaxies.map {
            GraphNode(it.id, it.name, GalaxyLayout.planetRadius(it.noteCount * 150, degree[it.id] ?: 0), false, galaxyColor(it.colorIndex))
        }
    }
    PlanetGraph(
        nodes = nodes, solidEdges = emptyList(), dashedEdges = edges, layoutEdges = edges,
        dashedColor = MaterialTheme.colorScheme.primary, emptyText = stringResource(R.string.galaxies_empty_title),
        onOpen = onOpenGalaxy, modifier = modifier,
    )
}
