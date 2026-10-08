package com.foldpod.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap

internal data class ClassicStatus(
    val appIcon: ImageBitmap? = null,
    val sourceName: String = "",
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
)

internal val LocalClassicStatus = staticCompositionLocalOf { ClassicStatus() }

@Composable
internal fun rememberClassicStatus(packageName: String?, sourceName: String): ClassicStatus {
    val context = LocalContext.current
    val appIcon = remember(context, packageName) {
        packageName?.let { name ->
            runCatching { context.packageManager.getApplicationIcon(name).toBitmap(48, 48).asImageBitmap() }.getOrNull()
        }
    }
    var batteryPercent by remember { mutableStateOf<Int?>(null) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        fun update(intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            batteryPercent = if (level >= 0 && scale > 0) ((level.toLong() * 100) / scale).toInt().coerceIn(0, 100) else null
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = update(intent)
        }
        update(ContextCompat.registerReceiver(context, receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    return ClassicStatus(appIcon, sourceName, batteryPercent, charging)
}

@Composable
internal fun ClassicStatusIndicators() {
    val status = LocalClassicStatus.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        status.appIcon?.let { icon ->
            Image(icon, contentDescription = status.sourceName.ifBlank { "Current player" }, modifier = Modifier.size(19.dp))
        }
        status.batteryPercent?.let { percent ->
            val tint = when {
                status.charging -> Color(0xFF287E3D)
                percent <= 15 -> Color(0xFFB3261E)
                else -> Color(0xFF444444)
            }
            Text("$percent%", fontSize = 11.sp, lineHeight = 14.sp, color = Color(0xFF444444), maxLines = 1)
            Canvas(Modifier.size(25.dp, 14.dp).semantics {
                contentDescription = "Battery $percent percent${if (status.charging) ", charging" else ""}"
            }) {
                val bodyWidth = size.width * 0.88f
                val stroke = 1.dp.toPx()
                val inset = stroke * 2f
                drawRoundRect(tint, Offset(stroke / 2, stroke / 2),
                    Size(bodyWidth - stroke, size.height - stroke), CornerRadius(stroke), style = Stroke(stroke))
                drawRect(tint, Offset(bodyWidth, size.height * 0.3f), Size(size.width - bodyWidth, size.height * 0.4f))
                val fillWidth = (bodyWidth - inset * 2).coerceAtLeast(0f) * percent / 100f
                if (fillWidth > 0f) drawRect(tint, Offset(inset, inset), Size(fillWidth, (size.height - inset * 2).coerceAtLeast(0f)))
            }
        }
    }
}
