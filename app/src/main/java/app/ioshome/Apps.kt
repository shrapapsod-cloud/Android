package app.ioshome

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class AppInfo(val key: String, val pkg: String, val label: String, val info: LauncherActivityInfo)

data class PackInfo(val pkg: String, val label: String)

/** Наборы значков из Play Маркета (формат Nova / ADW / Apex — его поддерживают почти все). */
object IconPacks {
    private val ACTIONS = listOf(
        "org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.teslacoilsw.launcher.THEME",
        "com.anddoes.launcher.THEME", "com.gau.go.launcherex.theme", "com.fede.launcher.THEME_ICONPACK",
    )

    fun list(ctx: Context): List<PackInfo> {
        val pm = ctx.packageManager
        return ACTIONS.flatMap { a ->
            runCatching { pm.queryIntentActivities(Intent(a), PackageManager.MATCH_ALL) }.getOrDefault(emptyList())
        }.map { it.activityInfo.packageName }.distinct().map { p ->
            PackInfo(p, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() }.getOrDefault(p))
        }.sortedBy { it.label.lowercase() }
    }

    fun label(ctx: Context, pkg: String): String =
        if (pkg.isEmpty()) "Стандартный" else runCatching {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault("Стандартный")
}

/** Разобранный appfilter.xml набора: компонент приложения -> имя картинки. */
class IconPack(ctx: Context, private val pkg: String) {
    private val res: Resources = ctx.packageManager.getResourcesForApplication(pkg)
    private val map = HashMap<String, String>()
    private val byPkg = HashMap<String, String>()

    init {
        val id = res.getIdentifier("appfilter", "xml", pkg)
        val p: XmlPullParser = if (id != 0) res.getXml(id) else XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(ctx.createPackageContext(pkg, 0).assets.open("appfilter.xml"), "UTF-8")
        }
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.name == "item") {
                val comp = p.getAttributeValue(null, "component")
                val dr = p.getAttributeValue(null, "drawable")
                if (comp != null && dr != null && comp.startsWith("ComponentInfo{")) {
                    val k = comp.removePrefix("ComponentInfo{").removeSuffix("}").trim()
                    map.putIfAbsent(k, dr)
                    byPkg.putIfAbsent(k.substringBefore('/'), dr)
                }
            }
            ev = p.next()
        }
    }

    fun drawable(cn: ComponentName, dpi: Int): Drawable? {
        val name = map[cn.flattenToString()] ?: map[cn.flattenToShortString()] ?: byPkg[cn.packageName] ?: return null
        val rid = res.getIdentifier(name, "drawable", pkg).takeIf { it != 0 }
            ?: res.getIdentifier(name, "mipmap", pkg).takeIf { it != 0 } ?: return null
        return runCatching { res.getDrawableForDensity(rid, dpi, null) }.getOrNull()
    }
}

/** Иконки: рисуются квадратом «под обрез», форму squircle даёт clip в Compose. */
object IconCache {
    private const val PX = 256
    private val cache = LruCache<String, ImageBitmap>(300)
    /** Меняется при смене набора значков — иконки перерисовываются. */
    var ver by mutableIntStateOf(0); private set
    @Volatile private var packName = ""
    private var pack: IconPack? = null
    private var packLoaded = true

    fun setPack(name: String) {
        if (name == packName) return
        synchronized(this) { packName = name; pack = null; packLoaded = name.isEmpty() }
        cache.evictAll()
        ver++
    }

    private fun pack(ctx: Context): IconPack? = synchronized(this) {
        if (!packLoaded) {
            packLoaded = true
            pack = runCatching { IconPack(ctx, packName) }.getOrNull()
        }
        pack
    }

    fun peek(key: String): ImageBitmap? = cache.get(key)

    fun load(ctx: Context, app: AppInfo): ImageBitmap {
        cache.get(app.key)?.let { return it }
        val dpi = ctx.resources.displayMetrics.densityDpi
        val d = pack(ctx)?.drawable(app.info.componentName, dpi)
            ?: runCatching { app.info.getIcon(dpi) }.getOrNull()
            ?: ctx.packageManager.defaultActivityIcon
        val bmp = render(d, PX).asImageBitmap()
        cache.put(app.key, bmp)
        return bmp
    }

    fun clear() = cache.evictAll()

    private fun render(d: Drawable, s: Int): Bitmap {
        val out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        if (d is AdaptiveIconDrawable) {
            // Слои адаптивной иконки 108dp, видимая часть 72dp → растягиваем слой на 150%.
            val o = s / 4
            d.background?.let { it.setBounds(-o, -o, s + o, s + o); it.draw(c) }
            d.foreground?.let { it.setBounds(-o, -o, s + o, s + o); it.draw(c) }
            return out
        }
        // Старая иконка / иконка из набора: рисуем пробно; если углы прозрачные (своя форма) —
        // квадрат растягиваем, круг кладём на белый фон.
        val probe = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, s, s); d.draw(Canvas(probe))
        val cornersEmpty = Color.alpha(probe.getPixel(2, 2)) < 128 && Color.alpha(probe.getPixel(s - 3, s - 3)) < 128
        val edgesFull = Color.alpha(probe.getPixel(s / 2, 1)) > 200 && Color.alpha(probe.getPixel(1, s / 2)) > 200
        if (cornersEmpty && edgesFull) {
            val e = (s * 0.07f).toInt()
            c.drawColor(probe.getPixel(s / 2, 3))
            d.setBounds(-e, -e, s + e, s + e); d.draw(c)
        } else if (cornersEmpty) {
            c.drawColor(Color.WHITE)
            val e = (s * 0.12f).toInt()
            d.setBounds(e, e, s - e, s - e); d.draw(c)
        } else {
            c.drawBitmap(probe, 0f, 0f, null)
        }
        probe.recycle()
        return out
    }
}
