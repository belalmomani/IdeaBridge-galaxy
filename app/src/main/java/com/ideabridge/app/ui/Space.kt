package com.ideabridge.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

/** لون المجرة = لون كوكبها الحقيقي (colorIndex المخزّن يشير إلى قائمة الكواكب). */
fun galaxyColor(index: Int): Color = planetStyle(index).main

/** خلفية فضاء داكنة بنجوم ثابتة (بذرة ثابتة فلا تتغير بين الرسمات). */
@Composable
fun SpaceBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val stars = remember {
        val r = Random(7)
        List(70) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
    }
    Box(
        modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF070B18), Color(0xFF0E1530), Color(0xFF1A1440)))
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            stars.forEach { (x, y, s) ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.25f + 0.5f * s),
                    radius = 0.8f + 1.8f * s,
                    center = Offset(x * size.width, y * size.height),
                )
            }
        }
        content()
    }
}
