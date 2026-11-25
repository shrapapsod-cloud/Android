package app.ioshome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Фон-фото: чёткая картинка + сильно размытая маленькая копия для «стекла». */
class WallImages(val sharp: ImageBitmap, val blurred: ImageBitmap?)

val LocalWall = compositionLocalOf<WallImages?> { null }
val LocalScreen = compositionLocalOf { IntSize.Zero }

object Wall {
    fun file(ctx: Context) = File(ctx.filesDir, "wall.jpg")

    /** Скопировать выбранное фото к себе (уменьшив до размера экрана). */
    fun save(ctx: Context, uri: Uri): Boolean = runCatching {
        val dm = ctx.resources.displayMetrics
        val sw = dm.widthPixels.toFloat(); val sh = dm.heightPixels.toFloat()
        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
        val bmp = ImageDecoder.decodeBitmap(src) { d, info, _ ->
            val w = info.size.width; val h = info.size.height
            val k = minOf(1f, max(sw / w, sh / h))
            d.setTargetSize((w * k).roundToInt().coerceAtLeast(1), (h * k).roundToInt().coerceAtLeast(1))
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val tmp = File(ctx.filesDir, "wall.tmp")
        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        tmp.renameTo(file(ctx))
    }.getOrDefault(false)

    fun load(ctx: Context, level: Int): WallImages? {
        val f = file(ctx)
        if (!f.exists()) return null
        val b = BitmapFactory.decodeFile(f.path) ?: return null
        val bl = if (level > 0) blur(b, if (level == 1) 4 else 8).asImageBitmap() else null
        return WallImages(b.asImageBitmap(), bl)
    }

    /** Уменьшить до 120 px и трижды размыть — при растяжении получается мягкое iOS-размытие. */
    private fun blur(src: Bitmap, r: Int): Bitmap {
        val w = 120
        val h = (src.height * w / src.width).coerceAtLeast(1)
        val s = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
        val a = IntArray(w * h); val t = IntArray(w * h)
        s.getPixels(a, 0, w, 0, 0, w, h)
        repeat(3) { box(a, t, w, h, r, true); box(t, a, w, h, r, false) }
        s.setPixels(a, 0, w, 0, 0, w, h)
        return s
    }

    private fun box(inp: IntArray, out: IntArray, w: Int, h: Int, r: Int, horiz: Boolean) {
        val n = if (horiz) w else h
        val lines = if (horiz) h else w
        val div = 2 * r + 1
        for (l in 0 until lines) {
            fun idx(i: Int): Int { val c = i.coerceIn(0, n - 1); return if (horiz) l * w + c else c * w + l }
            var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) { val p = inp[idx(i)]; sr += p shr 16 and 255; sg += p shr 8 and 255; sb += p and 255 }
            for (i in 0 until n) {
                out[idx(i)] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val pa = inp[idx(i + r + 1)]; val pr = inp[idx(i - r)]
                sr += (pa shr 16 and 255) - (pr shr 16 and 255)
                sg += (pa shr 8 and 255) - (pr shr 8 and 255)
                sb += (pa and 255) - (pr and 255)
            }
        }
    }
}

/**
 * «Стекло» как в iOS: под элементом рисуется размытый кусок фона ровно того места экрана,
 * где элемент находится, плюс светлая вуаль. Без своего фото — просто полупрозрачная вуаль.
 */
fun Modifier.glass(
    shape: Shape,
    tint: Color = Color.White.copy(alpha = 0.2f),
    plain: Color = Color.White.copy(alpha = 0.28f),
): Modifier = composed {
    val wall = LocalWall.current
    val screen = LocalScreen.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    val bl = wall?.blurred
    this.clip(shape)
        .onGloballyPositioned { pos = it.positionInWindow() }
        .drawBehind {
            if (bl != null && screen.width > 0) {
                val k = max(screen.width / bl.width.toFloat(), screen.height / bl.height.toFloat())
                val dw = bl.width * k; val dh = bl.height * k
                drawImage(
                    bl,
                    dstOffset = IntOffset(((screen.width - dw) / 2 - pos.x).roundToInt(), ((screen.height - dh) / 2 - pos.y).roundToInt()),
                    dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                    filterQuality = FilterQuality.High,
                )
                drawRect(tint)
            } else drawRect(plain)
        }
}
