package app.ioshome

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Плавные «непрерывные» углы как в iOS (continuous corners), а не обычное скругление дугой.
 * Кривые — та же аппроксимация, что использует UIKit (UIBezierPath roundedRect в iOS 7+).
 * radius задаётся в dp, либо fraction — доля от меньшей стороны (для иконок).
 */
class ContinuousShape(private val radius: Dp? = null, private val fraction: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = radius?.let { with(density) { it.toPx() } } ?: (size.minDimension * fraction)
        return Outline.Generic(continuousPath(size.width, size.height, r))
    }
}

/** Иконка приложения: радиус 22.37% стороны, как у iOS. */
val IconShape = ContinuousShape(fraction = 0.2237f)
/** Виджет: радиус ~13% стороны, как у виджетов iOS 14. */
val WidgetShape = ContinuousShape(fraction = 0.13f)
/** Круг / капсула — там, где в iOS настоящий круг (счётчики, «−», точки, переключатели). */
val Capsule = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50)

private const val K = 1.52866483f

fun continuousPath(w: Float, h: Float, rIn: Float): Path {
    val r = min(rIn, min(w, h) / 2f / K)
    val p = Path()
    // (a, b): a — расстояние от вертикального края, b — от горизонтального, в долях r
    p.corner(true) { a, b -> Offset(w - a * r, b * r) }      // правый верхний
    p.corner(false) { a, b -> Offset(w - b * r, h - a * r) } // правый нижний
    p.corner(false) { a, b -> Offset(a * r, h - b * r) }     // левый нижний
    p.corner(false) { a, b -> Offset(b * r, a * r) }         // левый верхний
    p.close()
    return p
}

private inline fun Path.corner(first: Boolean, f: (Float, Float) -> Offset) {
    val s = f(K, 0f)
    if (first) moveTo(s.x, s.y) else lineTo(s.x, s.y)
    cubic(f(1.08849323f, 0f), f(0.86840689f, 0.02208522f), f(0.66993427f, 0.06549600f))
    line(f(0.63149399f, 0.07491100f))
    cubic(f(0.37282392f, 0.16905899f), f(0.16905899f, 0.37282392f), f(0.07491100f, 0.63149399f))
    line(f(0.06549600f, 0.66993427f))
    cubic(f(0.02208522f, 0.86840689f), f(0f, 1.08849323f), f(0f, K))
}

private fun Path.cubic(a: Offset, b: Offset, c: Offset) = cubicTo(a.x, a.y, b.x, b.y, c.x, c.y)
private fun Path.line(a: Offset) = lineTo(a.x, a.y)
