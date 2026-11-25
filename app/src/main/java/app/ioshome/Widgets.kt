package app.ioshome

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.AlarmClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

val IosRed = Color(0xFFFF3B30)
val IosOrange = Color(0xFFFF9500)
val IosYellow = Color(0xFFFFCC00)
val IosGray = Color(0xFF8E8E93)

@Composable
private fun now(stepMs: Long): Calendar = produceState(Calendar.getInstance()) {
    while (true) {
        value = Calendar.getInstance()
        delay(stepMs - System.currentTimeMillis() % stepMs)
    }
}.value

/** Содержимое виджета (без подписи). Нажатие обрабатывает вызывающий. */
@Composable
fun WidgetBody(type: WType, w: Dp, h: Dp, notes: Note) {
    val m = Modifier.size(w, h).clip(WidgetShape)
    when (type) {
        WType.CLOCK -> ClockWidget(m)
        WType.CALENDAR -> CalendarWidget(m)
        WType.NOTES -> NotesWidget(m, notes)
        WType.BATTERY -> BatteryWidget(m)
    }
}

@Composable
private fun ClockWidget(m: Modifier) {
    val t = now(1000)
    Canvas(m.background(Color.Black)) {
        val r = min(size.width, size.height) / 2f * 0.9f
        val c = center
        drawCircle(Color.White, r, c)
        // деления
        for (i in 0 until 60) {
            val a = Math.toRadians(i * 6.0)
            val big = i % 5 == 0
            val r1 = r * if (big) 0.88f else 0.92f
            drawLine(
                if (big) Color.Black else IosGray,
                Offset(c.x + r1 * sin(a).toFloat(), c.y - r1 * cos(a).toFloat()),
                Offset(c.x + r * 0.96f * sin(a).toFloat(), c.y - r * 0.96f * cos(a).toFloat()),
                strokeWidth = if (big) r * 0.025f else r * 0.012f,
            )
        }
        // цифры
        val p = android.graphics.Paint().apply {
            isAntiAlias = true; color = Color.Black.toArgb(); textAlign = android.graphics.Paint.Align.CENTER
            textSize = r * 0.24f; typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        }
        for (h in 1..12) {
            val a = Math.toRadians(h * 30.0)
            val rr = r * 0.70f
            drawContext.canvas.nativeCanvas.drawText(
                "$h", c.x + rr * sin(a).toFloat(), c.y - rr * cos(a).toFloat() + p.textSize * 0.35f, p,
            )
        }
        val sec = t.get(Calendar.SECOND)
        val min = t.get(Calendar.MINUTE) + sec / 60f
        val hr = t.get(Calendar.HOUR) + min / 60f
        rotate(hr * 30f, c) { drawLine(Color.Black, c, Offset(c.x, c.y - r * 0.5f), r * 0.07f, StrokeCap.Round) }
        rotate(min * 6f, c) { drawLine(Color.Black, c, Offset(c.x, c.y - r * 0.78f), r * 0.05f, StrokeCap.Round) }
        rotate(sec * 6f, c) { drawLine(IosOrange, Offset(c.x, c.y + r * 0.15f), Offset(c.x, c.y - r * 0.85f), r * 0.018f, StrokeCap.Round) }
        drawCircle(Color.Black, r * 0.05f, c)
        drawCircle(IosOrange, r * 0.03f, c)
    }
}

@Composable
private fun CalendarWidget(m: Modifier) {
    val t = now(60_000)
    val ru = Locale("ru")
    val dow = SimpleDateFormat("EEEE", ru).format(t.time).uppercase(ru)
    val month = SimpleDateFormat("LLLL", ru).format(t.time).replaceFirstChar { it.titlecase(ru) }
    Column(m.background(Color.White).padding(14.dp)) {
        BasicText(dow, style = TextStyle(color = IosRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
        BasicText("${t.get(Calendar.DAY_OF_MONTH)}", style = TextStyle(color = Color.Black, fontSize = 44.sp, fontWeight = FontWeight.Light))
        Spacer(Modifier.weight(1f))
        BasicText(month, style = TextStyle(color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        BasicText("Нет событий сегодня", style = TextStyle(color = IosGray, fontSize = 12.sp), maxLines = 1)
    }
}

@Composable
private fun NotesWidget(m: Modifier, note: Note) {
    Column(m.background(Color.White)) {
        Box(Modifier.fillMaxWidth().height(36.dp).background(IosYellow).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
            BasicText("Заметки", style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
        BasicText(
            note.title, Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp),
            style = TextStyle(color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        BasicText(
            note.text, Modifier.padding(horizontal = 14.dp, vertical = 2.dp).weight(1f),
            style = TextStyle(color = IosGray, fontSize = 13.sp),
            overflow = TextOverflow.Ellipsis,
        )
        BasicText(
            SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date()), Modifier.padding(start = 14.dp, bottom = 10.dp),
            style = TextStyle(color = IosGray, fontSize = 11.sp),
        )
    }
}

@Composable
private fun BatteryWidget(m: Modifier) {
    val ctx = LocalContext.current
    val t = now(30_000)
    val i = remember(t) { ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
    val lvl = i?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) } ?: 0
    val plugged = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    val col = when { plugged -> Color(0xFF34C759); lvl <= 20 -> IosRed; else -> Color(0xFF34C759) }
    Box(m.background(Color(0xFF1C1C1E)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(22.dp)) {
            val w = size.minDimension * 0.1f
            drawCircle(Color.White.copy(alpha = 0.15f), style = Stroke(w))
            drawArc(col, -90f, 360f * lvl / 100f, false, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText("$lvl%", style = TextStyle(color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold))
            if (plugged) BasicText("заряжается", style = TextStyle(color = IosGray, fontSize = 11.sp))
        }
    }
}

/** Куда ведёт нажатие на виджет. */
fun widgetIntent(type: WType): Intent? = when (type) {
    WType.CLOCK -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
    WType.CALENDAR -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
    WType.BATTERY -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
    WType.NOTES -> null
}?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

