package app.ioshome

/** Элемент рабочего стола: приложение (1×1) или виджет (2×2). */
sealed interface Item {
    val key: String
    val span: Int
}

data class AppItem(val component: String) : Item {
    override val key get() = "a:$component"
    override val span get() = 1
}

data class WidgetItem(val type: WType, val id: Long) : Item {
    override val key get() = "w:${type.name}:$id"
    override val span get() = 2
}

enum class WType(val title: String) {
    CLOCK("Часы"), CALENDAR("Календарь"), NOTES("Заметки"), BATTERY("Аккумулятор")
}

fun parseItem(s: String): Item? = when {
    s.startsWith("a:") -> AppItem(s.substring(2))
    s.startsWith("w:") -> s.split(":").let { p ->
        runCatching { WidgetItem(WType.valueOf(p[1]), p[2].toLong()) }.getOrNull()
    }
    else -> null
}

/** Заметка в виджете: заголовок + текст. */
data class Note(val title: String, val text: String)

/** «Пустое место» при перетаскивании: остальные значки расступаются вокруг него. */
const val GAP = "__gap__"
fun gapFor(i: Item): Item = if (i.span == 1) AppItem(GAP) else WidgetItem(WType.CLOCK, -1L)
val Item.isGap: Boolean get() = (this is AppItem && component == GAP) || (this is WidgetItem && id == -1L)

const val COLS = 4
const val ROWS = 6
const val DOCK_MAX = 4

data class Placed(val item: Item, val col: Int, val row: Int)

/**
 * Раскладка как в iOS: элементы идут по порядку слева направо, сверху вниз,
 * виджет 2×2 встаёт в первое место, где помещается. Что не влезло — во втором списке.
 */
fun pack(items: List<Item>): Pair<List<Placed>, List<Item>> {
    val busy = Array(ROWS) { BooleanArray(COLS) }
    val placed = ArrayList<Placed>()
    val overflow = ArrayList<Item>()
    for (it in items) {
        val s = it.span
        var done = false
        loop@ for (r in 0..ROWS - s) for (c in 0..COLS - s) {
            var free = true
            for (dr in 0 until s) for (dc in 0 until s) if (busy[r + dr][c + dc]) free = false
            if (free) {
                for (dr in 0 until s) for (dc in 0 until s) busy[r + dr][c + dc] = true
                placed += Placed(it, c, r); done = true; break@loop
            }
        }
        if (!done) overflow += it
    }
    return placed to overflow
}

/** Переполнение переносится на следующие страницы, пустые страницы убираются. */
fun normalize(pages: List<List<Item>>): List<List<Item>> {
    val out = ArrayList<List<Item>>()
    var carry: List<Item> = emptyList()
    var i = 0
    while (i < pages.size || carry.isNotEmpty()) {
        val src = carry + (pages.getOrNull(i) ?: emptyList())
        val (placed, over) = pack(src)
        val keys = placed.map { it.item.key }.toSet()
        out += src.filter { it.key in keys }
        carry = over
        i++
    }
    val res = out.filter { it.isNotEmpty() }
    return res.ifEmpty { listOf(emptyList()) }
}
