package app.ioshome

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val Bg = Color(0xFFF2F2F7)
private val SepColor = Color(0xFFC6C6C8)
private val Sub = Color(0xFF8E8E93)

/** Полноценное окно «Настройки» в стиле iOS. Жест «назад» системы просто закрывает его. */
class SettingsActivity : ComponentActivity() {
    private var resumes by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { SettingsScreen(this, resumes) }
    }

    override fun onResume() { super.onResume(); resumes++ }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    fun start(i: Intent) { runCatching { startActivity(i) } }

    fun isDefaultHome() = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)

    @Suppress("DEPRECATION")
    fun askDefaultHome() {
        val rm = getSystemService(RoleManager::class.java)
        runCatching { startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 1) }
            .onFailure { start(Intent(Settings.ACTION_HOME_SETTINGS)) }
    }

    /** Выбор рабочего стола в системе — там можно вернуть MIUI. */
    fun openHomeChooser() {
        runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
            .onFailure { start(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
    }

    fun badgesAllowed() = getSystemService(android.app.NotificationManager::class.java)
        .isNotificationListenerAccessGranted(ComponentName(this, BadgeService::class.java))

    fun findPacks() = start(
        Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=icon%20pack")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

@Composable
private fun SettingsScreen(act: SettingsActivity, resumes: Int) {
    val ctx = LocalContext.current
    val p = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    var scale by remember { mutableIntStateOf(p.iconScale) }
    var labels by remember { mutableStateOf(!p.hideLabels) }
    var blur by remember { mutableIntStateOf(p.blur) }
    var pack by remember { mutableStateOf(p.iconPack) }
    var wallVer by remember { mutableIntStateOf(0) }
    var hasWall by remember { mutableStateOf(Wall.file(ctx).exists()) }
    var resetDone by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf("main") }

    val packs by produceState(emptyList<PackInfo>(), resumes) { value = withContext(Dispatchers.IO) { IconPacks.list(ctx) } }
    val isDefault = remember(resumes) { act.isDefaultHome() }
    val badges = remember(resumes) { act.badgesAllowed() }
    val wallThumb by produceState<ImageBitmap?>(null, wallVer, hasWall) {
        value = if (!hasWall) null else withContext(Dispatchers.IO) { Wall.load(ctx, 0)?.sharp }
    }
    val preview by produceState<ImageBitmap?>(null, pack) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val app = ctx.packageManager.getLaunchIntentForPackage("com.android.settings")?.component
                    ?: return@runCatching null
                val pm = ctx.packageManager
                val dr = if (pack.isNotEmpty()) IconPack(ctx, pack).drawable(app, ctx.resources.displayMetrics.densityDpi) else null
                (dr ?: pm.getActivityIcon(app)).toBitmap(192, 192).asImageBitmap()
            }.getOrNull()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { Wall.save(ctx, uri) }
            if (ok) { hasWall = true; wallVer++ }
        }
    }

    // Подстраница «Набор значков» — как вложенный экран в iOS, назад жестом
    if (page == "packs") {
        androidx.activity.compose.BackHandler { page = "main" }
        Page("Набор значков", back = "Настройки", onBack = { page = "main" }) {
            Group(footer = "Подходят любые наборы для Nova / ADW / Apex Launcher из Play Маркета. Значки без своей картинки в наборе остаются обычными.") {
                CheckRow("Стандартный", pack.isEmpty()) { pack = ""; p.iconPack = "" }
                for (pk in packs) { Sep(); CheckRow(pk.label, pack == pk.pkg) { pack = pk.pkg; p.iconPack = pk.pkg } }
            }
            Group { NavRow("Найти наборы в Play Маркете", color = IosBlue, chevron = false) { act.findPacks() } }
        }
        return
    }

    Page("Настройки") {
        Group(header = "Обои", footer = "Фото используется для фона и размытия «стекла» под доком, папками и меню.") {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp, 120.dp).clip(ContinuousShape(10.dp)).background(Color(0xFFD1D1D6))) {
                    wallThumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                Spacer(Modifier.width(16.dp))
                BasicText(if (hasWall) "Своё фото" else "Системные обои", style = TextStyle(fontSize = 17.sp))
            }
            Sep()
            NavRow("Выбрать фото", color = IosBlue, chevron = false) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            if (hasWall) {
                Sep()
                NavRow("Вернуть системные обои", color = IosRed, chevron = false) { Wall.file(ctx).delete(); hasWall = false }
            }
        }

        Group(header = "Значки") {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val size by animateDpAsState(BaseIcon * (scale / 100f), spring(0.7f, 500f), label = "pv")
                Box(Modifier.size(BaseIcon * (Prefs.MAX_SCALE / 100f)), contentAlignment = Alignment.Center) {
                    val m = Modifier.size(size).clip(IconShape)
                    preview?.let { Image(it, null, m) } ?: Box(m.background(IosBlue))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        BasicText("Размер значков", Modifier.weight(1f), style = TextStyle(fontSize = 17.sp))
                        BasicText("$scale%", style = TextStyle(fontSize = 17.sp, color = Sub))
                    }
                    Spacer(Modifier.height(6.dp))
                    val span = (Prefs.MAX_SCALE - Prefs.MIN_SCALE).toFloat()
                    IosSlider((scale - Prefs.MIN_SCALE) / span, {
                        val v = (Prefs.MIN_SCALE + it * span).roundToInt() / 5 * 5
                        if (v != scale) { scale = v; p.iconScale = v }
                    })
                }
            }
            Sep()
            SwitchRow("Подписи под значками", labels) { labels = it; p.hideLabels = !it }
            Sep()
            NavRow("Набор значков", value = IconPacks.label(ctx, pack)) { page = "packs" }
        }

        Group(header = "Размытие", footer = "Работает, когда выбрано своё фото.") {
            listOf("Выкл.", "Среднее", "Сильное").forEachIndexed { i, t ->
                if (i > 0) Sep()
                CheckRow(t, blur == i) { blur = i; p.blur = i }
            }
        }

        Group(header = "Рабочий стол") {
            NavRow("Сделать рабочим столом по умолчанию", value = if (isDefault) "Да" else "Нет") { act.askDefaultHome() }
            Sep()
            NavRow("Вернуть рабочий стол MIUI") { act.openHomeChooser() }
            Sep()
            NavRow("Счётчики на значках", value = if (badges) "Вкл." else "Выкл.") {
                act.start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        Group(footer = "Иконки и виджеты встанут как при первом запуске.") {
            NavRow(if (resetDone) "Раскладка будет сброшена" else "Сбросить раскладку экрана «Домой»", color = IosRed, chevron = false) {
                p.requestReset(); resetDone = true
            }
        }
        BasicText("iOS Home 3.0", Modifier.fillMaxWidth().padding(16.dp), style = TextStyle(color = Sub, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
    }
}

/* ---------- Элементы списка iOS ---------- */

@Composable
private fun Page(title: String, back: String? = null, onBack: () -> Unit = {}, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Bg).statusBarsPadding().verticalScroll(rememberScrollState())
            .navigationBarsPadding().padding(bottom = 24.dp),
    ) {
        if (back != null) {
            BasicText(
                "‹ $back", Modifier.clickable(onClick = onBack).padding(horizontal = 12.dp, vertical = 10.dp),
                style = TextStyle(color = IosBlue, fontSize = 17.sp),
            )
        } else Spacer(Modifier.height(20.dp))
        BasicText(title, Modifier.padding(horizontal = 16.dp), style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun Group(header: String? = null, footer: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 22.dp)) {
        if (header != null) BasicText(
            header.uppercase(), Modifier.padding(start = 32.dp, bottom = 6.dp),
            style = TextStyle(color = Sub, fontSize = 13.sp),
        )
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(ContinuousShape(10.dp)).background(Color.White)) { content() }
        if (footer != null) BasicText(
            footer, Modifier.padding(start = 32.dp, end = 32.dp, top = 6.dp),
            style = TextStyle(color = Sub, fontSize = 13.sp),
        )
    }
}

@Composable
private fun Sep() = Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(SepColor))

@Composable
private fun RowBase(onClick: (() -> Unit)?, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun NavRow(text: String, value: String? = null, color: Color = Color.Black, chevron: Boolean = true, onClick: () -> Unit) =
    RowBase(onClick) {
        BasicText(text, Modifier.weight(1f), style = TextStyle(color = color, fontSize = 17.sp))
        if (value != null) BasicText(value, style = TextStyle(color = Sub, fontSize = 17.sp), maxLines = 1)
        if (chevron) BasicText("›", style = TextStyle(color = Color(0xFFC4C4C7), fontSize = 22.sp))
    }

@Composable
private fun SwitchRow(text: String, on: Boolean, onChange: (Boolean) -> Unit) = RowBase({ onChange(!on) }) {
    BasicText(text, Modifier.weight(1f), style = TextStyle(fontSize = 17.sp))
    IosSwitch(on)
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onClick: () -> Unit) = RowBase(onClick) {
    BasicText(text, Modifier.weight(1f), style = TextStyle(fontSize = 17.sp))
    if (checked) BasicText("✓", style = TextStyle(color = IosBlue, fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
}

