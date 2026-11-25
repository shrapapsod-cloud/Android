package app.ioshome

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val IosBlue = Color(0xFF007AFF)
val IosGreen = Color(0xFF34C759)
/** Размер значка с учётом настройки «Размер значков». 60dp = как в iOS. */
val BaseIcon = 60.dp
val LocalIconSize = staticCompositionLocalOf { BaseIcon }

/** Переключатель iOS. */
@Composable
fun IosSwitch(on: Boolean, modifier: Modifier = Modifier) {
    val x by animateFloatAsState(if (on) 1f else 0f, spring(0.75f, 500f), label = "sw")
    Box(modifier.size(51.dp, 31.dp).clip(Capsule).background(lerp(Color(0xFFE9E9EA), IosGreen, x))) {
        Box(Modifier.offset(2.dp + 20.dp * x, 2.dp).size(27.dp).shadow(2.dp, Capsule).clip(Capsule).background(Color.White))
    }
}

/** Ползунок iOS, value 0..1. */
@Composable
fun IosSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val cb by rememberUpdatedState(onChange)
    BoxWithConstraints(
        modifier.fillMaxWidth().height(34.dp)
            .pointerInput(Unit) { detectTapGestures { cb((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { ch, _ -> ch.consume(); cb((ch.position.x / size.width).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val w: Dp = maxWidth
        Box(Modifier.fillMaxWidth().height(4.dp).clip(Capsule).background(Color(0xFFE3E3E8)))
        Box(Modifier.width(w * value).height(4.dp).clip(Capsule).background(IosBlue))
        Box(Modifier.offset((w - 28.dp) * value).size(28.dp).shadow(3.dp, Capsule).clip(Capsule).background(Color.White))
    }
}
