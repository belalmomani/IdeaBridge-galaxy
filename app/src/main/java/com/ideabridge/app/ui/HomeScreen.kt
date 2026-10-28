package com.ideabridge.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ideabridge.app.R

/** مساحة اختيارية محجوزة لإعلان مستقبلي. مخفية افتراضيًا، ولا يوجد أي SDK أو معرّف إعلان. */
@Composable
fun AdSlot(modifier: Modifier = Modifier, visible: Boolean = false) {
    if (!visible) return
    Surface(
        modifier = modifier.fillMaxWidth().height(60.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.ad_slot_placeholder), style = MaterialTheme.typography.labelMedium)
        }
    }
}
