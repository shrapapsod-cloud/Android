package app.ioshome

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.roundToInt

/** Что сейчас перетаскивается: элемент, откуда, исходный прямоугольник в окне и смещение пальца. */
class Drag(val item: Item, val fromDock: Boolean, val fromPage: Int, val start: Rect) {
    var offset by mutableStateOf(Offset.Zero)
}

sealed interface Overlay {
    /** Контекстное меню у значка (bounds — где значок на экране). */
    data class AppMenu(val app: AppInfo, val bounds: Rect) : Overlay
    /** Папка Библиотеки приложений. */
    data class Folder(val title: String, val keys: List<String>) : Overlay
    /** Контекстное меню у виджета. */
    data class WidgetMenu(val item: Item, val bounds: Rect) : Overlay
    data object AddWidget : Overlay
    data object EditNotes : Overlay
}

class HomeState(private val ctx: Context) {
    private val sp = ctx.getSharedPreferences("home", Context.MODE_PRIVATE)
    private val la = ctx.getSystemService(LauncherApps::class.java)

    var apps by mutableStateOf<Map<String, AppInfo>>(emptyMap()); private set
    var pages by mutableStateOf<List<List<Item>>>(listOf(emptyList())); private set
    var dock by mutableStateOf<List<AppItem>>(emptyList()); private set
    var hidden by mutableStateOf<Set<String>>(emptySet()); private set
    var notes by mutableStateOf(Note(sp.getString("noteTitle", null) ?: "Заметка", sp.getString("notes", null) ?: "Нажми, чтобы написать")); private set

    var edit by mutableStateOf(false)
    var drag by mutableStateOf<Drag?>(null)
    /** Открытая панель поверх: меню приложения, добавление виджета, редактор заметки. */
    var overlay by mutableStateOf<Overlay?>(null)
    /** Запрос на прокрутку пейджера к странице (-1 = нет). */
    var goPage by mutableIntStateOf(-1)
    /** Увеличивается при каждом возврате на рабочий стол (перепроверить разрешения). */
    var tick by mutableIntStateOf(0)

    /** Поиск (свайп вниз по рабочему столу, как Spotlight в iOS). */
    var search by mutableStateOf(false)
    /** Тоже для Библиотеки: категории приложений. */
    val categories: List<Pair<String, List<AppInfo>>> get() = libraryCategories(sortedApps, recent.mapNotNull { apps[it] })

    /** Палец сейчас на иконке/виджете (чтобы долгое нажатие по пустому месту их не перехватывало). */
    var cellTouch = false

    /** Недавно открытые (для «Недавних» в Библиотеке, как в iOS). */
    var recent by mutableStateOf((sp.getString("recent", "") ?: "").split(",").filter { it.isNotEmpty() }); private set
    fun launched(key: String) {
        recent = (listOf(key) + recent.filter { it != key }).take(8)
        sp.edit().putString("recent", recent.joinToString(",")).apply()
    }

    /** Куда встанет перетаскиваемый элемент сейчас: (страница или -1 = док, индекс). */
    var hover by mutableStateOf<Pair<Int, Int>?>(null)

    // Настройки (меняются в отдельном окне SettingsActivity, сюда подтягиваются в onResume)
    var hideLabels by mutableStateOf(false); private set
    var blur by mutableIntStateOf(1); private set
    /** Размер значков в процентах от iOS (60dp = 100%). */
    var iconScale by mutableIntStateOf(100); private set
    /** Меняется при смене фона — перезагрузить картинку. */
    var wallVer by mutableStateOf(0L); private set

    init { refreshSettings() }

    fun refreshSettings() {
        val p = Prefs(ctx)
        hideLabels = p.hideLabels
        blur = p.blur
        iconScale = p.iconScale
        IconCache.setPack(p.iconPack)
        if (apps.isNotEmpty() && p.consumeReset()) { defaultLayout(); sync() }
        val f = Wall.file(ctx)
        val v = if (f.exists()) f.lastModified() else 0L
        if (v != wallVer) wallVer = v
    }

    // Геометрия для расчёта места при отпускании
    var gridRect = Rect.Zero
    var dockRect = Rect.Zero
    var currentPage = 0

    val sortedApps: List<AppInfo> get() = apps.values.sortedBy { it.label.lowercase() }

    fun reload() {
        val list = la.getActivityList(null, Process.myUserHandle())
            .filter { it.componentName.packageName != ctx.packageName }
            .map { AppInfo(it.componentName.flattenToShortString(), it.componentName.packageName, it.label.toString(), it) }
        val first = apps.isEmpty() && !sp.contains("pages")
        apps = list.associateBy { it.key }
        IconCache.clear()
        if (first) defaultLayout() else load()
        sync()
    }

    private fun load() {
        pages = (sp.getString("pages", "") ?: "").split("|")
            .map { p -> p.split(",").mapNotNull(::parseItem) }
        dock = (sp.getString("dock", "") ?: "").split(",").mapNotNull(::parseItem).filterIsInstance<AppItem>()
        hidden = (sp.getString("hidden", "") ?: "").split(",").filter { it.isNotEmpty() }.toSet()
    }

    /** Убрать удалённые приложения, добавить новые в конец последней страницы. */
    private fun sync() {
        val ok: (Item) -> Boolean = { it !is AppItem || it.component in apps }
        val p = pages.map { it.filter(ok) }
        val d = dock.filter(ok)
        val placed = (p.flatten() + d).filterIsInstance<AppItem>().map { it.component }.toSet()
        val fresh = sortedApps.map { it.key }.filter { it !in placed && it !in hidden }.map { AppItem(it) }
        val withNew = if (fresh.isEmpty()) p else p.dropLast(1) + listOf((p.lastOrNull() ?: emptyList()) + fresh)
        pages = normalize(withNew)
        dock = d
        save()
    }

    /** Раскладка как на скриншоте: слева виджеты, справа приложения, внизу док. */
    private fun defaultLayout() {
        fun find(vararg pk: String): String? = pk.firstNotNullOfOrNull { p -> apps.values.firstOrNull { it.pkg == p }?.key }
        val dockApps = listOfNotNull(
            find("com.google.android.dialer", "com.android.contacts", "com.android.dialer"),
            find("com.miui.notes", "com.google.android.keep"),
            find("org.telegram.messenger", "com.radolyn.ayugram"),
            find("org.mozilla.firefox", "com.android.chrome", "com.savanna.browser"),
        ).distinct()
        val right = listOfNotNull(
            find("com.android.settings"),
            find("com.android.deskclock"),
            find("com.google.android.apps.messaging", "com.android.mms"),
            find("com.google.android.gm"),
            find("com.viber.voip"),
            find("com.radolyn.ayugram", "com.discord"),
            find("com.xiaomi.calendar", "com.google.android.calendar", "com.android.calendar"),
            find("com.zhiliaoapp.musically", "com.google.android.youtube"),
            find("com.discord", "com.openai.chatgpt"),
            find("com.anthropic.claude", "com.openai.chatgpt"),
            find("com.google.android.apps.photos", "com.miui.gallery"),
            find("com.android.camera"),
        ).distinct().filter { it !in dockApps }
        var n = 0L
        val w = listOf(WType.CLOCK, WType.CALENDAR, WType.NOTES).map { WidgetItem(it, n++) }
        val first = ArrayList<Item>()
        for (i in 0 until 3) {
            first += w[i]
            right.drop(i * 4).take(4).forEach { first += AppItem(it) }
        }
        val used = (dockApps + right).toSet()
        val rest = sortedApps.map { it.key }.filter { it !in used }.map { AppItem(it) }
        pages = normalize(listOf(first, rest))
        dock = dockApps.map { AppItem(it) }
        hidden = emptySet()
        sp.edit().putLong("wid", n).apply()
    }

    private fun save() {
        sp.edit()
            .putString("pages", pages.joinToString("|") { p -> p.joinToString(",") { it.key } })
            .putString("dock", dock.joinToString(",") { it.key })
            .putString("hidden", hidden.joinToString(","))
            .apply()
    }

    fun setNotes(n: Note) { notes = n; sp.edit().putString("noteTitle", n.title).putString("notes", n.text).apply() }

    fun addWidget(t: WType) {
        val id = sp.getLong("wid", 100L) + 1
        sp.edit().putLong("wid", id).apply()
        val pg = currentPage.coerceIn(0, pages.lastIndex)
        pages = normalize(pages.mapIndexed { i, p -> if (i == pg) listOf(WidgetItem(t, id)) + p else p })
        save()
    }

    /** «−» в режиме редактирования / «Убрать с экрана»: приложение остаётся в Библиотеке. */
    fun removeFromHome(item: Item) {
        pages = normalize(pages.map { p -> p.filter { it.key != item.key } })
        dock = dock.filter { it.key != item.key }
        if (item is AppItem) hidden = hidden + item.component
        save()
    }

    fun addToHome(component: String) {
        hidden = hidden - component
        if ((pages.flatten() + dock).any { it is AppItem && it.component == component }) { save(); return }
        pages = normalize(pages.dropLast(1) + listOf(pages.last() + AppItem(component)))
        save()
        goPage = pages.lastIndex
    }

    /** Куда встанет перетаскиваемый элемент: (-1, i) — в док, (страница, i) — в сетку, null — некуда. */
    fun target(d: Drag): Pair<Int, Int>? {
        val c = d.start.center + d.offset
        val item = d.item
        if (item is AppItem && dockRect.width > 0f && c.y >= dockRect.top - 8f) {
            val rest = dock.filter { it.key != item.key }
            if (!d.fromDock && rest.size >= DOCK_MAX) return null
            // значки дока стоят по центру с шагом в 1/4 ширины, как в iOS
            val slot = dockRect.width / DOCK_MAX
            val startX = dockRect.center.x - slot * (rest.size + 1) / 2f
            val idx = ((c.x - startX) / slot).toInt().coerceIn(0, rest.size)
            return -1 to idx
        }
        if (gridRect.width <= 0f) return null
        val pg = currentPage
        if (pg < 0 || pg > pages.size) return null // «Сегодня» или Библиотека
        val page = pages.getOrElse(pg) { emptyList() }.filter { it.key != item.key }
        val cw = gridRect.width / COLS
        val ch = gridRect.height / ROWS
        val s = item.span
        val col = ((c.x - gridRect.left) / cw - s / 2f).roundToInt().coerceIn(0, COLS - s)
        val row = ((c.y - gridRect.top) / ch - s / 2f).roundToInt().coerceIn(0, ROWS - s)
        val placed = pack(page).first
        val hit = placed.firstOrNull { p ->
            col in p.col until p.col + p.item.span && row in p.row until p.row + p.item.span
        } ?: placed.firstOrNull { p ->   // виджет «накрывает» несколько клеток — берём любую занятую
            s > 1 && p.col < col + s && col < p.col + p.item.span && p.row < row + s && row < p.row + p.item.span
        }
        val idx = if (hit != null) {
            val i = page.indexOfFirst { it.key == hit.item.key }
            val cx = gridRect.left + (hit.col + hit.item.span / 2f) * cw
            if (s == 1 && hit.item.span == 1 && c.x > cx) i + 1 else i
        } else page.size
        return pg to idx
    }

    /** Как сейчас выглядит страница с учётом перетаскивания: значки расступаются заранее. */
    fun preview(pageIdx: Int, items: List<Item>): List<Item> {
        val d = drag ?: return items
        val t = hover ?: return items
        if (t.first != pageIdx) return items
        val isSource = !d.fromDock && d.fromPage == pageIdx && items.any { it.key == d.item.key }
        val out = items.filter { it.key != d.item.key }.toMutableList()
        out.add(t.second.coerceIn(0, out.size), if (isSource) d.item else gapFor(d.item))
        return out
    }

    fun dockPreview(): List<Item> {
        val d = drag ?: return dock
        val t = hover
        if (t == null || t.first != -1) return dock
        val out = dock.filter { it.key != d.item.key }.toMutableList<Item>()
        out.add(t.second.coerceIn(0, out.size), if (d.fromDock) d.item else AppItem(GAP))
        return out
    }

    /** Отпускание перетаскиваемого элемента. */
    fun drop() {
        val d = drag ?: return
        val t = target(d)
        drag = null; hover = null
        if (t == null) return
        val item = d.item
        if (t.first == -1 && item is AppItem) {
            val rest = dock.filter { it.key != item.key }
            dock = rest.toMutableList().apply { add(t.second.coerceIn(0, size), item) }
            if (!d.fromDock) pages = normalize(pages.map { p -> p.filter { it.key != item.key } })
            save(); return
        }
        val pg = t.first
        val padded = pages.map { p -> p.filter { it.key != item.key } }.toMutableList()
        while (padded.size <= pg) padded += emptyList<Item>()
        padded[pg] = padded[pg].toMutableList().apply { add(t.second.coerceIn(0, size), item) }
        if (d.fromDock) dock = dock.filter { it.key != item.key }
        pages = normalize(padded)
        save()
        goPage = pages.indexOfFirst { p -> p.any { it.key == item.key } }
    }
}

/** Папки Библиотеки приложений, как в iOS 14: Предложения, Недавно добавленные и по категориям. */
fun libraryCategories(all: List<AppInfo>, recent: List<AppInfo>): List<Pair<String, List<AppInfo>>> {
    val out = ArrayList<Pair<String, List<AppInfo>>>()
    val sugg = (recent + all).distinctBy { it.key }.take(8)
    if (sugg.isNotEmpty()) out += "Предложения" to sugg
    out += "Недавно добавленные" to all.sortedByDescending { it.info.firstInstallTime }.take(8)
    val names = mapOf(
        android.content.pm.ApplicationInfo.CATEGORY_SOCIAL to "Социальные сети",
        android.content.pm.ApplicationInfo.CATEGORY_VIDEO to "Развлечения",
        android.content.pm.ApplicationInfo.CATEGORY_AUDIO to "Музыка",
        android.content.pm.ApplicationInfo.CATEGORY_IMAGE to "Фото и видео",
        android.content.pm.ApplicationInfo.CATEGORY_NEWS to "Новости",
        android.content.pm.ApplicationInfo.CATEGORY_MAPS to "Навигация",
        android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY to "Продуктивность",
        android.content.pm.ApplicationInfo.CATEGORY_GAME to "Игры",
    )
    val groups = all.groupBy { names[it.info.applicationInfo.category] ?: "Другие" }
    for (n in names.values.distinct() + "Другие") groups[n]?.let { if (it.isNotEmpty()) out += n to it }
    return out
}
