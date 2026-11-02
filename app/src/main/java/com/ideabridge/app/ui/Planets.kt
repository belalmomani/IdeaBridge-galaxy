package com.ideabridge.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ideabridge.app.R

enum class PlanetKind { EARTH, MARS, JUPITER, SATURN, VENUS, NEPTUNE, URANUS, MERCURY }

/** كوكب بألوانه الحقيقية: light للضوء، main اللون الأساسي، dark للظل. */
class PlanetStyle(val kind: PlanetKind, val nameRes: Int, val light: Color, val main: Color, val dark: Color)

/** ترتيب هذه القائمة ثابت لأن colorIndex المخزّن في المجرة يشير إليه. وعددها = IdeaRules.GALAXY_STYLE_COUNT. */
val Planets: List<PlanetStyle> = listOf(
    PlanetStyle(PlanetKind.EARTH, R.string.planet_earth, Color(0xFF6FB1F2), Color(0xFF2B6CB0), Color(0xFF0B2A52)),
    PlanetStyle(PlanetKind.MARS, R.string.planet_mars, Color(0xFFE88A5E), Color(0xFFC1440E), Color(0xFF5E1D08)),
    PlanetStyle(PlanetKind.JUPITER, R.string.planet_jupiter, Color(0xFFEBDDBF), Color(0xFFC99B6D), Color(0xFF6E4B2E)),
    PlanetStyle(PlanetKind.SATURN, R.string.planet_saturn, Color(0xFFF0E0B0), Color(0xFFD8BD7C), Color(0xFF7A6638)),
    PlanetStyle(PlanetKind.VENUS, R.string.planet_venus, Color(0xFFF4E3BF), Color(0xFFE0B76F), Color(0xFF8A6428)),
    PlanetStyle(PlanetKind.NEPTUNE, R.string.planet_neptune, Color(0xFF8FB0FF), Color(0xFF3E66F0), Color(0xFF14267A)),
    PlanetStyle(PlanetKind.URANUS, R.string.planet_uranus, Color(0xFFD6F5F7), Color(0xFF8FD6DD), Color(0xFF3F8591)),
    PlanetStyle(PlanetKind.MERCURY, R.string.planet_mercury, Color(0xFFC4BFB9), Color(0xFF9C9690), Color(0xFF4D4945)),
)

fun planetStyle(index: Int): PlanetStyle = Planets[index.mod(Planets.size)]

private val RingColor = Color(0xFFD9C79A)

/** يرسم كوكبًا كاملًا: كرة مظللة، تفاصيل سطحه الحقيقية (إن كان كبيرًا بما يكفي)، وحلقات زحل. */
fun DrawScope.drawPlanet(style: PlanetStyle, c: Offset, r: Float, detailed: Boolean) {
    val hasRing = style.kind == PlanetKind.SATURN
    val ringTopLeft = Offset(c.x - r * 1.9f, c.y - r * 0.5f)
    val ringSize = Size(r * 3.8f, r * 1.0f)
    val ringStroke = Stroke(width = r * 0.28f)

    if (hasRing) { // النصف الخلفي من الحلقة خلف الكوكب
        rotate(-18f, c) { drawOval(color = RingColor.copy(alpha = 0.85f), topLeft = ringTopLeft, size = ringSize, style = ringStroke) }
    }
    val circle = Path().apply { addOval(Rect(c, r)) }
    clipPath(circle) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(style.light, style.main, style.dark),
                center = Offset(c.x - r * 0.35f, c.y - r * 0.35f), radius = r * 1.7f,
            ),
            radius = r, center = c,
        )
        if (detailed) drawSurface(style.kind, c, r)
        drawCircle( // ظل الجهة البعيدة عن الضوء
            brush = Brush.radialGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.40f)),
                center = Offset(c.x - r * 0.3f, c.y - r * 0.3f), radius = r * 1.5f,
            ),
            radius = r, center = c,
        )
    }
    if (hasRing) { // النصف الأمامي من الحلقة أمام الكوكب
        rotate(-18f, c) {
            drawArc(
                color = RingColor, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                topLeft = ringTopLeft, size = ringSize, style = ringStroke,
            )
        }
    }
}

private fun DrawScope.blob(c: Offset, r: Float, dx: Float, dy: Float, w: Float, h: Float, color: Color) {
    drawOval(color = color, topLeft = Offset(c.x + dx * r, c.y + dy * r), size = Size(w * r, h * r))
}

private fun DrawScope.drawSurface(kind: PlanetKind, c: Offset, r: Float) {
    when (kind) {
        PlanetKind.EARTH -> {
            val land = Color(0xFF4C9A55).copy(alpha = 0.9f)
            blob(c, r, -0.55f, -0.25f, 0.7f, 0.5f, land)
            blob(c, r, 0.05f, 0.10f, 0.55f, 0.55f, land)
            blob(c, r, -0.20f, -0.75f, 0.4f, 0.25f, land)
            val cloud = Color.White.copy(alpha = 0.55f)
            blob(c, r, -0.70f, 0.35f, 1.0f, 0.14f, cloud)
            blob(c, r, -0.10f, -0.35f, 0.9f, 0.12f, cloud)
            val ice = Color.White.copy(alpha = 0.8f)
            blob(c, r, -0.40f, -1.0f, 0.8f, 0.22f, ice)
            blob(c, r, -0.35f, 0.80f, 0.7f, 0.20f, ice)
        }
        PlanetKind.MARS -> {
            val patch = Color(0xFF7A2A0E).copy(alpha = 0.6f)
            blob(c, r, -0.50f, -0.10f, 0.6f, 0.35f, patch)
            blob(c, r, 0.10f, 0.30f, 0.5f, 0.30f, patch)
            blob(c, r, -0.20f, 0.55f, 0.4f, 0.20f, patch)
            blob(c, r, -0.35f, -1.0f, 0.7f, 0.25f, Color.White.copy(alpha = 0.85f))
        }
        PlanetKind.JUPITER -> {
            val bands = listOf(0xFFB98558, 0xFFE9DCC0, 0xFFA8693F, 0xFFEBD9B8, 0xFFB5764A, 0xFFE3CFA9, 0xFF9C5E3A)
            val h = 2f * r / bands.size
            bands.forEachIndexed { i, argb ->
                drawRect(
                    color = Color(argb).copy(alpha = 0.75f),
                    topLeft = Offset(c.x - r, c.y - r + i * h), size = Size(2f * r, h),
                )
            }
            blob(c, r, 0.15f, 0.20f, 0.5f, 0.28f, Color(0xFFB5482A).copy(alpha = 0.85f)) // البقعة الحمراء
        }
        PlanetKind.SATURN -> {
            val bands = listOf(0xFFC9AE6A, 0xFFEBD9A3, 0xFFBFA45E, 0xFFE4D09A)
            val h = 2f * r / bands.size
            bands.forEachIndexed { i, argb ->
                drawRect(
                    color = Color(argb).copy(alpha = 0.5f),
                    topLeft = Offset(c.x - r, c.y - r + i * h), size = Size(2f * r, h),
                )
            }
        }
        PlanetKind.VENUS -> {
            val swirl = Color(0xFFC48B3E).copy(alpha = 0.35f)
            blob(c, r, -1.0f, -0.50f, 2.0f, 0.30f, swirl)
            blob(c, r, -0.9f, -0.05f, 1.9f, 0.25f, swirl)
            blob(c, r, -1.0f, 0.35f, 2.0f, 0.30f, swirl)
            blob(c, r, -0.9f, -0.28f, 1.8f, 0.15f, Color(0xFFFFF1D0).copy(alpha = 0.35f))
        }
        PlanetKind.NEPTUNE -> {
            val band = Color(0xFF9DB8FF).copy(alpha = 0.5f)
            blob(c, r, -1.0f, -0.40f, 2.0f, 0.18f, band)
            blob(c, r, -1.0f, 0.25f, 2.0f, 0.14f, band)
            blob(c, r, 0.0f, 0.0f, 0.45f, 0.25f, Color(0xFF0F1C66).copy(alpha = 0.6f))
        }
        PlanetKind.URANUS -> blob(c, r, -1.0f, -0.20f, 2.0f, 0.20f, Color(0xFFBDEDF0).copy(alpha = 0.5f))
        PlanetKind.MERCURY -> {
            val crater = Color(0xFF5E5A56).copy(alpha = 0.55f)
            blob(c, r, -0.50f, -0.30f, 0.30f, 0.30f, crater)
            blob(c, r, 0.20f, -0.60f, 0.22f, 0.22f, crater)
            blob(c, r, 0.10f, 0.10f, 0.40f, 0.40f, crater)
            blob(c, r, -0.60f, 0.35f, 0.25f, 0.25f, crater)
            blob(c, r, 0.35f, 0.50f, 0.20f, 0.20f, crater)
        }
    }
}

/** كوكب بحجم ثابت للبطاقات والمنتقي. زحل أصغر قليلًا ليتسع لحلقاته داخل المربع نفسه. */
@Composable
fun PlanetOrb(style: PlanetStyle, diameter: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(diameter)) {
        val r = this.size.minDimension / 2f * (if (style.kind == PlanetKind.SATURN) 0.52f else 0.92f)
        drawPlanet(style, center, r, detailed = true)
    }
}

/** منتقي الكوكب: صفان من أربعة كواكب، والمختار أكبر، واسمه تحتها. */
@Composable
fun PlanetPicker(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Planets.chunked(4).forEachIndexed { row, items ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { i, style ->
                    val index = row * 4 + i
                    PlanetOrb(style, if (index == selected) 54.dp else 42.dp, Modifier.clickable { onSelect(index) })
                }
            }
        }
        Text(stringResource(planetStyle(selected).nameRes), style = MaterialTheme.typography.labelLarge)
    }
}
