@file:OptIn(ExperimentalLayoutApi::class)

package app.ioshome

import android.content.pm.ShortcutInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val LabelStyle = TextStyle(
    color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center,
    shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f), 3f),
)
private val Placeholder = Color.White.copy(alpha = 0.28f)
private val Sheet = Color(0xF2F2F2F7)
private val SheetGlass = Color(0xBFF2F2F7)

/* ======================= Корень ======================= */

@Composable
fun HomeScreen(s: HomeState, act: MainActivity) {
    val ctx = LocalContext.current
    val wall by produceState<WallImages?>(null, s.wallVer, s.blur) {
        value = withContext(Dispatchers.IO) { Wall.load(ctx, s.blur) }
    }
    var screen by remember { mutableStateOf(IntSize.Zero) }
    val icon = BaseIcon * (s.iconScale / 100f)
    CompositionLocalProvider(LocalWall provides wall, LocalScreen provides screen, LocalIconSize provides icon) {
        Box(Modifier.fillMaxSize().onSizeChanged { screen = it }) {
            wall?.let { Image(it.sharp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            HomeContent(s, act)
        }
    }
}

@Composable
private fun HomeContent(s: HomeState, act: MainActivity) {
    val scope = rememberCoroutineScope()
    val wall = LocalWall.current
    val haptic = LocalHapticFeedback.current
    val homePages = s.pages.size
    val extra = if (s.edit) 1 else 0           // пустая страница в режиме правки (как в iOS)
    val libIndex = 1 + homePages + extra       // 0 — «Сегодня», 1..N — экраны, последняя — Библиотека
    val pager = rememberPagerState(initialPage = 1) { libIndex + 1 }
    val onSide = (pager.currentPage == libIndex || pager.currentPage == 0) && s.drag == null
    val badges by Badges.counts.collectAsState()
    val defaultHome = remember(s.tick) { act.isDefaultHome() }

    // Свайп вниз: 0..1 — насколько вытянут поиск пальцем
    var pull by remember { mutableFloatStateOf(0f) }
    val searchA by animateFloatAsState(
        if (s.search) 1f else pull,
        if (pull > 0f && !s.search) spring(stiffness = 2000f) else spring(0.85f, 420f), label = "search",
    )
    val overlayA by animateFloatAsState(if (s.overlay != null) 1f else 0f, tween(220), label = "ov")
    val dim = maxOf(searchA, overlayA)

    // Возврат на рабочий стол — значки «прилетают», как в iOS
    val enter = remember { Animatable(1f) }
    LaunchedEffect(s.tick) {
        if (s.tick > 1) { enter.snapTo(1.12f); enter.animateTo(1f, spring(0.8f, 260f)) }
    }

    LaunchedEffect(Unit) { snapshotFlow { pager.currentPage }.collect { s.currentPage = it - 1 } }
    LaunchedEffect(s.goPage) {
        if (s.goPage >= 0) { pager.animateScrollToPage((s.goPage + 1).coerceAtMost(libIndex)); s.goPage = -1 }
    }
    // Пока тащишь — считаем, куда встанет; значки расступаются заранее
    LaunchedEffect(s.drag) {
        val d = s.drag ?: return@LaunchedEffect
        while (s.drag === d) {
            val t = s.target(d)
            if (t != s.hover) {
                s.hover = t
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            delay(50)
        }
    }
    // Перетаскивание к краю экрана листает страницы
    LaunchedEffect(s.drag) {
        val d = s.drag ?: return@LaunchedEffect
        while (s.drag === d) {
            val x = (d.start.center + d.offset).x
            val dir = when { x < s.gridRect.left + 24f -> -1; x > s.gridRect.right - 24f -> 1; else -> 0 }
            if (dir != 0) {
                delay(550)
                val x2 = (d.start.center + d.offset).x
                val still = if (dir < 0) x2 < s.gridRect.left + 24f else x2 > s.gridRect.right - 24f
                val target = pager.currentPage + dir
                if (s.drag === d && still && target in 1 until libIndex) pager.animateScrollToPage(target)
            }
            delay(60)
        }
    }

    BackHandler {
        when {
            s.search -> s.search = false
            s.overlay != null -> s.overlay = null
            s.edit -> s.edit = false
            pager.currentPage != 1 -> scope.launch { pager.animateScrollToPage(1) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Размытый фон, когда открыт поиск или меню
        if (dim > 0f) {
            wall?.blurred?.let { Image(it, null, Modifier.fillMaxSize().graphicsLayer { alpha = dim }, contentScale = ContentScale.Crop) }
        }
        Column(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    val k = (1f - 0.06f * dim) * enter.value
                    scaleX = k; scaleY = k
                    alpha = (1f - 0.5f * dim) * (1f - (enter.value - 1f) * 5f).coerceIn(0f, 1f)
                }
                .blur((18f * dim).dp)
                .statusBarsPadding().navigationBarsPadding(),
        ) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .pointerInput(s.edit, libIndex) {
                        // Свайп вниз от верхнего края — уведомления, из середины — поиск
                        val full = 260.dp.toPx()
                        var total = 0f
                        var fromTop = false
                        detectVerticalDragGestures(
                            onDragStart = { o -> total = 0f; fromTop = o.y < size.height * 0.12f },
                            onVerticalDrag = { ch, dy ->
                                total += dy
                                if (!fromTop && !s.edit && pager.currentPage in 1 until libIndex) {
                                    pull = (total / full).coerceIn(0f, 1f)
                                    ch.consume()
                                }
                            },
                            onDragEnd = {
                                if (fromTop) { if (total > 80f && !s.edit) act.expandShade() }
                                else if (pull > 0.3f) s.search = true
                                pull = 0f
                            },
                            onDragCancel = { pull = 0f },
                        )
                    }
                    .pointerInput(s.edit) {
                        detectTapGestures(
                            onTap = { if (s.edit) s.edit = false },
                            // Долгое нажатие по пустому месту — режим правки
                            onLongPress = {
                                if (!s.cellTouch && pager.currentPage in 1 until libIndex) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress); s.edit = true
                                }
                            },
                        )
                    },
                beyondViewportPageCount = 8,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pager,
                    snapAnimationSpec = spring(dampingRatio = 0.86f, stiffness = 380f),
                ),
                key = { it },
            ) { page ->
                when (page) {
                    0 -> TodayPage(s, act)
                    libIndex -> Library(s, act, badges)
                    else -> HomePage(s, act, page - 1, s.pages.getOrElse(page - 1) { emptyList() }, badges)
                }
            }

            val dockAlpha by animateFloatAsState(if (onSide) 0f else 1f, tween(200), label = "dock")
            Dots(libIndex + 1, pager.currentPage, Modifier.graphicsLayer { alpha = dockAlpha }) { i ->
                scope.launch { pager.animateScrollToPage(i) }
            }
            val badgesOk = remember(s.tick) { act.badgesAllowed() }
            when {
                s.edit -> {}
                !defaultHome -> Banner("Нажми, чтобы сделать iOS Home рабочим столом по умолчанию") { act.askDefaultHome() }
                !badgesOk -> Banner("Включить красные счётчики на иконках (доступ к уведомлениям)") { act.openBadgeSettings() }
            }
            Dock(s, act, badges, Modifier.graphicsLayer { alpha = dockAlpha })
        }

        // Кнопки режима правки («+» слева, «Готово» справа)
        AnimatedVisibility(s.edit && s.drag == null && s.overlay == null, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Pill("+") { s.overlay = Overlay.AddWidget }
                Pill("Настройки") { act.openSettings() }
                Pill("Готово") { s.edit = false }
            }
        }

        // Перетаскиваемый элемент рисуется поверх всего
        s.drag?.let { d -> DragGhost(s, d, badges) }

        if (searchA > 0.01f) Spotlight(s, act, searchA)

        OverlayHost(s, act)
    }
}

/** Слой меню и панелей. Тап мимо: сначала прячет клавиатуру, второй тап закрывает. */
@Composable
private fun OverlayHost(s: HomeState, act: MainActivity) {
    val ov = s.overlay
    val ime = WindowInsets.isImeVisible
    val focus = LocalFocusManager.current
    val kb = LocalSoftwareKeyboardController.current
    AnimatedVisibility(ov != null, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f))
                .clickable(remember { MutableInteractionSource() }, null) {
                    if (ime) { focus.clearFocus(); kb?.hide() } else s.overlay = null
                },
        ) {
            when (val o = ov ?: return@Box) {
                is Overlay.AppMenu -> AppMenu(s, act, o.app, o.bounds)
                is Overlay.WidgetMenu -> WidgetMenu(s, o.item, o.bounds)
                is Overlay.Folder -> Folder(s, act, o)
                Overlay.AddWidget -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AddWidget(s) }
                Overlay.EditNotes -> EditNotes(s)
            }
        }
    }
}

/* ======================= Сетка рабочего стола ======================= */

@Composable
private fun HomePage(s: HomeState, act: MainActivity, pageIdx: Int, items: List<Item>, badges: Map<String, Int>) {
    val icon = LocalIconSize.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Сетка iOS 14: поля 24dp, виджет 2×2 — ровно от левого края иконки до правого края соседней
        val inset = (24.dp - (icon - BaseIcon) / 2).coerceAtLeast(6.dp)
        val px = (maxWidth - inset * 2 - icon) / (COLS - 1)
        val labelH = if (s.hideLabels) 0.dp else 20.dp
        val py = minOf(px * 1.08f, (maxHeight - 12.dp - icon - labelH) / (ROWS - 1))
        val side = minOf(px + icon, py + icon)
        val shown = s.preview(pageIdx, items)
        Box(
            Modifier.padding(start = inset + icon / 2 - px / 2, top = 12.dp).size(px * COLS, py * ROWS)
                .onGloballyPositioned {
                    // соседние страницы пейджера тоже сообщают позицию — берём только видимую
                    val r = it.boundsInWindow()
                    if (r.width > it.size.width * 0.9f) s.gridRect = r
                },
        ) {
            for (p in pack(shown).first) {
                if (p.item.isGap) continue
                key(p.item.key) {
                    val wide = p.item.span > 1
                    // Значки плавно переезжают на новое место (и обратно), пока тащишь
                    val x by animateDpAsState(px * p.col + (if (wide) (px - side) / 2 else 0.dp), spring(0.78f, 420f), label = "x")
                    val y by animateDpAsState(py * p.row, spring(0.78f, 420f), label = "y")
                    Box(
                        Modifier.offset(x, y).size(if (wide) side else px, if (wide) maxOf(py * 2, side + labelH) else py),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Cell(s, act, p.item, side, px, badges, fromDock = false, page = pageIdx)
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(
    s: HomeState, act: MainActivity, item: Item, side: Dp, labelW: Dp, badges: Map<String, Int>, fromDock: Boolean, page: Int,
) {
    val app = (item as? AppItem)?.let { s.apps[it.component] }
    if (item is AppItem && app == null) return
    val haptic = LocalHapticFeedback.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val dragging = s.drag?.item?.key == item.key
    val wobble = jiggle(s.edit && !dragging, item.key)
    var pressed by remember { mutableStateOf(false) }
    // Если ячейка исчезла во время перетаскивания — всё равно положить элемент
    DisposableEffect(item.key) { onDispose { if (s.drag?.item?.key == item.key) s.drop() } }
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(0.6f, 900f), label = "press")

    Box(
        Modifier
            .graphicsLayer {
                alpha = if (dragging) 0f else 1f
                rotationZ = wobble; scaleX = scale; scaleY = scale
            }
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .pointerInput(item.key, app) {  // без s.edit в ключах: иначе жест обрывается при входе в режим правки
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true; s.cellTouch = true
                    val slop = viewConfiguration.touchSlop
                    val longMs = if (s.edit) 250L else viewConfiguration.longPressTimeoutMillis
                    // true = отпустил (тап), false = сдвинул (листает страницы), null = долгое нажатие
                    val res: Boolean? = withTimeoutOrNull(longMs) {
                        var r = false
                        while (true) {
                            val e = awaitPointerEvent()
                            val c = e.changes.firstOrNull { it.id == down.id }
                            if (c == null) { r = false; break }
                            if (!c.pressed) { c.consume(); r = true; break }
                            if ((c.position - down.position).getDistance() > slop || c.isConsumed) { r = false; break }
                        }
                        r
                    }
                    pressed = false
                    if (res != null) s.cellTouch = false
                    if (res == true) {
                        if (s.edit) {
                            if (item is WidgetItem && item.type == WType.NOTES) s.overlay = Overlay.EditNotes
                        } else when (item) {
                            is AppItem -> if (app != null) act.launch(app, bounds)
                            is WidgetItem -> if (item.type == WType.NOTES) s.overlay = Overlay.EditNotes
                                else act.start(widgetIntent(item.type))
                        }
                    }
                    if (res != null) return@awaitEachGesture

                    // Долгое нажатие: меню (или режим правки), дальше можно тащить
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (!s.edit) {
                        s.overlay = if (app != null) Overlay.AppMenu(app, bounds) else Overlay.WidgetMenu(item, bounds)
                    }
                    var moved = 0f
                    while (true) {
                        val e = awaitPointerEvent()
                        val c = e.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        val delta = c.positionChange()
                        c.consume()
                        moved += abs(delta.x) + abs(delta.y)
                        val dr = s.drag
                        if (dr == null && moved > 18f) {
                            s.overlay = null; s.edit = true
                            val d = Drag(item, fromDock, page, bounds).also { it.offset = c.position - down.position }
                            s.drag = d
                            s.hover = s.target(d)
                        } else if (dr != null) dr.offset += delta
                    }
                    s.cellTouch = false
                    if (s.drag != null) s.drop()
                }
            },
    ) {
        when (item) {
            is AppItem -> AppTile(app!!, badges[app.pkg] ?: 0, showLabel = !fromDock && !s.hideLabels, minus = s.edit && !dragging, labelW = labelW) {
                s.removeFromHome(item)
            }
            is WidgetItem -> WidgetTile(s, item, side, minus = s.edit && !dragging)
        }
    }
}

@Composable
private fun jiggle(on: Boolean, key: String): Float {
    if (!on) return 0f
    val t = rememberInfiniteTransition(label = "jiggle")
    val sign = if (key.hashCode() and 1 == 0) 1f else -1f
    val a by t.animateFloat(-1.6f * sign, 1.6f * sign, infiniteRepeatable(tween(125 + (key.hashCode() and 31)), RepeatMode.Reverse), label = "j")
    return a
}

@Composable
fun AppIcon(app: AppInfo, size: Dp = LocalIconSize.current, badge: Int = 0) {
    val ctx = LocalContext.current
    val ver = IconCache.ver
    val bmp: ImageBitmap? by produceState(IconCache.peek(app.key), app.key, ver) {
        value = IconCache.peek(app.key) ?: withContext(Dispatchers.Default) { IconCache.load(ctx, app) }
    }
    Box(Modifier.size(size)) {
        val m = Modifier.size(size).clip(IconShape)
        bmp?.let { Image(it, app.label, m) } ?: Box(m.background(Placeholder))
        if (badge > 0) Badge(badge, Modifier.align(Alignment.TopEnd).offset(8.dp, (-6).dp))
    }
}

@Composable
private fun Badge(n: Int, m: Modifier) {
    Box(
        m.height(22.dp).widthIn(min = 22.dp).clip(Capsule).background(IosRed).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(if (n > 99) "99+" else "$n", style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun AppTile(
    app: AppInfo, badge: Int, showLabel: Boolean, minus: Boolean, labelW: Dp = 80.dp, size: Dp = LocalIconSize.current,
    onMinus: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            AppIcon(app, size, badge = badge)
            MinusButton(minus, Modifier.align(Alignment.TopStart).offset((-7).dp, (-7).dp), onMinus)
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            BasicText(app.label, Modifier.width(labelW), style = LabelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WidgetTile(s: HomeState, w: WidgetItem, side: Dp, minus: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(Modifier.shadow(6.dp, WidgetShape, clip = false)) { WidgetBody(w.type, side, side, s.notes) }
            MinusButton(minus, Modifier.align(Alignment.TopStart).offset((-7).dp, (-7).dp)) { s.removeFromHome(w) }
        }
        if (!s.hideLabels) {
            Spacer(Modifier.height(5.dp))
            BasicText(w.type.title, style = LabelStyle, maxLines = 1)
        }
    }
}

@Composable
private fun MinusButton(visible: Boolean, m: Modifier, onClick: () -> Unit) {
    AnimatedVisibility(visible, m, enter = scaleIn(spring(0.6f, 600f)), exit = scaleOut()) {
        Box(
            Modifier.size(22.dp).glass(Capsule, Color(0xB3D1D1D6), Color(0xE6D1D1D6)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(10.dp, 2.dp).clip(Capsule).background(Color.Black))
        }
    }
}

@Composable
private fun DragGhost(s: HomeState, d: Drag, badges: Map<String, Int>) {
    val dens = LocalDensity.current
    val app = (d.item as? AppItem)?.let { s.apps[it.component] }
    val lift = remember { Animatable(1f) }
    LaunchedEffect(d) { lift.animateTo(1.12f, spring(0.55f, 500f)) }
    Box(
        Modifier
            .offset { IntOffset((d.start.left + d.offset.x).roundToInt(), (d.start.top + d.offset.y).roundToInt()) }
            .size(with(dens) { d.start.width.toDp() }, with(dens) { d.start.height.toDp() })
            .graphicsLayer { scaleX = lift.value; scaleY = lift.value; alpha = 0.95f },
        contentAlignment = Alignment.TopCenter,
    ) {
        when (val di = d.item) {
            is AppItem -> if (app != null) Box(Modifier.shadow(10.dp, IconShape, clip = false)) { AppIcon(app, badge = badges[app.pkg] ?: 0) }
            is WidgetItem -> {
                val side = with(dens) { minOf(d.start.width, d.start.height).toDp() }
                Box(Modifier.shadow(12.dp, WidgetShape, clip = false)) { WidgetBody(di.type, side, side, s.notes) }
            }
        }
    }
}

/* ======================= Док и точки ======================= */

@Composable
private fun Dock(s: HomeState, act: MainActivity, badges: Map<String, Int>, m: Modifier) {
    val icon = LocalIconSize.current
    BoxWithConstraints(
        m.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp).height(icon + 30.dp)
            .glass(ContinuousShape(32.dp))
            .onGloballyPositioned { s.dockRect = it.boundsInWindow() },
    ) {
        val slot = maxWidth / DOCK_MAX
        val list = s.dockPreview()
        val start = (maxWidth - slot * list.size) / 2
        list.forEachIndexed { i, it ->
            key(it.key) {
                // Значки в доке тоже расступаются и съезжаются плавно
                val x by animateDpAsState(start + slot * i, spring(0.78f, 420f), label = "dx")
                Box(Modifier.offset(x, 0.dp).size(slot, maxHeight), contentAlignment = Alignment.Center) {
                    if (!it.isGap) Cell(s, act, it, icon, slot, badges, fromDock = true, page = -1)
                }
            }
        }
    }
}

@Composable
private fun Dots(count: Int, current: Int, m: Modifier, onClick: (Int) -> Unit) {
    Row(m.fillMaxWidth().height(20.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val a by animateFloatAsState(if (i == current) 1f else 0.4f, tween(200), label = "dot")
            Box(
                Modifier.clickable(remember { MutableInteractionSource() }, null) { onClick(i) }.padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (i == count - 1) Box(Modifier.size(7.dp).clip(ContinuousShape(2.dp)).background(Color.White.copy(alpha = a)))
                else Box(Modifier.size(7.dp).clip(Capsule).background(Color.White.copy(alpha = a)))
            }
        }
    }
}

@Composable
private fun Banner(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .glass(ContinuousShape(16.dp), Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.9f))
            .clickable(onClick = onClick).padding(12.dp),
    ) {
        BasicText(text, style = TextStyle(color = Color(0xFF0A60D0), fontSize = 14.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    Box(
        Modifier.glass(Capsule, Color(0x99E5E5EA), Color(0xCCE5E5EA)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 5.dp),
    ) {
        BasicText(text, style = TextStyle(color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
    }
}

/* ======================= «Сегодня» (левая страница) ======================= */

@Composable
private fun TodayPage(s: HomeState, act: MainActivity) {
    val ru = Locale("ru")
    BoxWithConstraints(Modifier.fillMaxSize().glass(RectangleShape, Color.Black.copy(alpha = 0.12f), Color.Black.copy(alpha = 0.3f))) {
        val side = (maxWidth - 16.dp * 2 - 14.dp) / 2
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
            BasicText(
                SimpleDateFormat("EEEE, d MMMM", ru).format(Date()).replaceFirstChar { it.titlecase(ru) },
                style = TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().height(40.dp)
                    .glass(ContinuousShape(12.dp), Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.3f))
                    .clickable { s.search = true }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) { BasicText("⌕  Поиск", style = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 17.sp)) }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TodayWidget(s, act, WType.CALENDAR, side)
                TodayWidget(s, act, WType.BATTERY, side)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TodayWidget(s, act, WType.NOTES, side)
                TodayWidget(s, act, WType.CLOCK, side)
            }
            Spacer(Modifier.height(16.dp))
            val sugg = (s.recent.mapNotNull { s.apps[it] } + s.sortedApps).distinctBy { it.key }.take(8)
            Column(Modifier.fillMaxWidth().glass(ContinuousShape(22.dp), Color.White.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.3f)).padding(14.dp)) {
                BasicText("Предложения Siri", style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(10.dp))
                for (row in sugg.chunked(4)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        for (app in row) LaunchTile(s, act, app, 54.dp)
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun TodayWidget(s: HomeState, act: MainActivity, t: WType, side: Dp) {
    Box(
        Modifier.shadow(6.dp, WidgetShape, clip = false).clickable {
            if (t == WType.NOTES) s.overlay = Overlay.EditNotes else act.start(widgetIntent(t))
        },
    ) { WidgetBody(t, side, side, s.notes) }
}

/** Значок, который просто запускает приложение (Библиотека, поиск, «Сегодня», папки). */
@Composable
private fun LaunchTile(s: HomeState, act: MainActivity, app: AppInfo, size: Dp, label: Boolean = !s.hideLabels, labelW: Dp = 76.dp) {
    var b by remember { mutableStateOf(Rect.Zero) }
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val badges by Badges.counts.collectAsState()
    val sc by animateFloatAsState(if (pressed) 0.9f else 1f, spring(0.6f, 900f), label = "p")
    Box(
        Modifier.graphicsLayer { scaleX = sc; scaleY = sc }
            .onGloballyPositioned { b = it.boundsInWindow() }
            .pointerInput(app.key) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { s.search = false; s.overlay = null; act.launch(app, b) },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        s.search = false
                        s.overlay = Overlay.AppMenu(app, b)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AppTile(app, badges[app.pkg] ?: 0, showLabel = label, minus = false, labelW = labelW, size = size) {}
    }
}

/* ======================= Библиотека приложений ======================= */

@Composable
private fun Library(s: HomeState, act: MainActivity, badges: Map<String, Int>) {
    var q by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val cats = remember(s.apps, s.recent) { s.categories }
    val list = remember(s.apps, q) { s.sortedApps.filter { it.label.contains(q.trim(), ignoreCase = true) } }
    Column(
        Modifier.fillMaxSize()
            .glass(RectangleShape, Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.35f))
            .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } },
    ) {
        SearchField(q, { q = it }, "Библиотека приложений", Modifier.padding(16.dp))
        if (q.isNotBlank()) {
            // Поиск: алфавитный список, как в iOS
            LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                items(list, key = { it.key }) { app ->
                    var b by remember { mutableStateOf(Rect.Zero) }
                    Row(
                        Modifier.fillMaxWidth().onGloballyPositioned { b = it.boundsInWindow() }
                            .clickable { focus.clearFocus(); act.launch(app, b) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app, 40.dp, badges[app.pkg] ?: 0)
                        Spacer(Modifier.width(14.dp))
                        BasicText(app.label, style = TextStyle(color = Color.White, fontSize = 17.sp))
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                GridCells.Fixed(2), Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(cats, key = { it.first }) { (title, apps) -> LibraryFolder(s, act, title, apps) }
            }
        }
    }
}

/** Папка Библиотеки: 3 больших значка + 4 маленьких (нажатие на них открывает папку). */
@Composable
private fun LibraryFolder(s: HomeState, act: MainActivity, title: String, apps: List<AppInfo>) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .glass(ContinuousShape(22.dp), Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.2f))
                .padding(12.dp),
        ) {
            val cell = (maxWidth - 12.dp) / 2
            val big = apps.size <= 4
            (if (big) apps.take(4) else apps.take(3)).forEachIndexed { i, app ->
                Box(Modifier.offset((cell + 12.dp) * (i % 2), (cell + 12.dp) * (i / 2))) {
                    LaunchTile(s, act, app, cell, label = false)
                }
            }
            if (!big) {
                val small = (cell - 6.dp) / 2
                Box(
                    Modifier.offset(cell + 12.dp, cell + 12.dp).size(cell)
                        .clickable(remember { MutableInteractionSource() }, null) { s.overlay = Overlay.Folder(title, apps.map { it.key }) },
                ) {
                    apps.drop(3).take(4).forEachIndexed { i, app ->
                        Box(Modifier.offset((small + 6.dp) * (i % 2), (small + 6.dp) * (i / 2))) { AppIcon(app, small) }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        BasicText(title, style = LabelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Folder(s: HomeState, act: MainActivity, f: Overlay.Folder) {
    val apps = f.keys.mapNotNull { s.apps[it] }
    val pop = remember { Animatable(0.8f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(0.7f, 400f)) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 28.dp, vertical = 60.dp)
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(f.title, style = LabelStyle.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier.fillMaxWidth().glass(ContinuousShape(36.dp), Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.3f))
                .clickable(remember { MutableInteractionSource() }, null) {},
        ) {
            LazyVerticalGrid(
                GridCells.Fixed(3), Modifier.fillMaxWidth().heightIn(max = 520.dp),
                contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(apps, key = { it.key }) { app -> LaunchTile(s, act, app, LocalIconSize.current.coerceAtMost(72.dp)) }
            }
        }
    }
}

@Composable
private fun SearchField(q: String, onQ: (String) -> Unit, hint: String, m: Modifier = Modifier, focus: FocusRequester? = null) {
    Row(
        m.fillMaxWidth().height(40.dp)
            .glass(ContinuousShape(12.dp), Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.3f))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText("⌕", style = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 18.sp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (q.isEmpty()) BasicText(hint, style = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 17.sp))
            BasicTextField(
                q, onQ, if (focus != null) Modifier.fillMaxWidth().focusRequester(focus) else Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 17.sp), cursorBrush = SolidColor(Color.White),
            )
        }
        if (q.isNotEmpty()) Box(
            Modifier.size(18.dp).clip(Capsule).background(Color.White.copy(alpha = 0.45f)).clickable { onQ("") },
            contentAlignment = Alignment.Center,
        ) { BasicText("×", style = TextStyle(color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)) }
    }
}

/* ======================= Меню (контекстное меню iOS) ======================= */

@Composable
private fun MenuRow(text: String, color: Color = Color.Black, icon: String? = null, img: ImageBitmap? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text, Modifier.weight(1f), style = TextStyle(color = color, fontSize = 16.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (img != null) Image(img, null, Modifier.size(22.dp).clip(ContinuousShape(5.dp)))
        else if (icon != null) BasicText(icon, style = TextStyle(color = color, fontSize = 17.sp))
    }
}

@Composable
private fun Divider(thick: Boolean = false) =
    Box(Modifier.fillMaxWidth().height(if (thick) 7.dp else 0.5.dp).background(if (thick) Color(0x1A000000) else Color(0x33000000)))

@Composable
private fun Card(width: Dp = 250.dp, content: @Composable () -> Unit) {
    Column(
        Modifier.width(width).shadow(16.dp, ContinuousShape(14.dp), clip = false)
            .glass(ContinuousShape(14.dp), SheetGlass, Sheet)
            .clickable(remember { MutableInteractionSource() }, null) {},
    ) { content() }
}

/** Значок остаётся на своём месте, меню «вырастает» под ним (или над ним, если значок внизу). */
@Composable
private fun ContextMenu(bounds: Rect, preview: @Composable () -> Unit, menu: @Composable () -> Unit) {
    val dens = LocalDensity.current
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(0.62f, 420f)) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val l = with(dens) { bounds.left.toDp() }
        val t = with(dens) { bounds.top.toDp() }
        val b = with(dens) { bounds.bottom.toDp() }
        val below = (t + b) / 2 < maxHeight * 0.55f
        Box(Modifier.offset(l, t).graphicsLayer { val k = 1f + 0.06f * pop.value; scaleX = k; scaleY = k }) { preview() }
        val mx = l.coerceIn(16.dp, (maxWidth - 250.dp - 16.dp).coerceAtLeast(16.dp))
        val m = Modifier.graphicsLayer {
            scaleX = pop.value; scaleY = pop.value; alpha = ((pop.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
            transformOrigin = TransformOrigin(0.1f, if (below) 0f else 1f)
        }
        if (below) Box(Modifier.offset(mx, b + 12.dp).then(m)) { menu() }
        else Box(Modifier.align(Alignment.BottomStart).padding(bottom = (maxHeight - t + 12.dp).coerceAtLeast(0.dp)).offset(mx, 0.dp).then(m)) { menu() }
    }
}

@Composable
private fun AppMenu(s: HomeState, act: MainActivity, app: AppInfo, bounds: Rect) {
    val onHome = (s.pages.flatten() + s.dock).any { it is AppItem && it.component == app.key }
    val shortcuts by produceState<List<Pair<ShortcutInfo, ImageBitmap?>>>(emptyList(), app.key) {
        value = withContext(Dispatchers.IO) { act.shortcuts(app).map { it to act.shortcutIcon(it) } }
    }
    ContextMenu(bounds, { Box(Modifier.clickable { s.overlay = null; act.launch(app, bounds) }) { AppIcon(app) } }) {
        Card {
            // Быстрые действия самого приложения (как 3D Touch / Haptic Touch)
            for ((si, img) in shortcuts) {
                MenuRow((si.shortLabel ?: si.longLabel ?: "").toString(), img = img) { s.overlay = null; act.startShortcut(si) }
                Divider()
            }
            if (shortcuts.isNotEmpty()) Divider(thick = true)
            MenuRow("Изменить экран «Домой»", icon = "▦") { s.overlay = null; s.edit = true }
            Divider()
            MenuRow("Поделиться приложением", icon = "⇪") { s.overlay = null; act.share(app) }
            Divider()
            MenuRow("О приложении", icon = "ⓘ") { s.overlay = null; act.appDetails(app) }
            Divider()
            if (onHome) MenuRow("Удалить с экрана «Домой»", icon = "⊖") { s.overlay = null; s.removeFromHome(AppItem(app.key)) }
            else MenuRow("Добавить на экран «Домой»", icon = "⊕") { s.overlay = null; s.addToHome(app.key) }
            Divider()
            MenuRow("Удалить приложение", IosRed, icon = "🗑") { s.overlay = null; act.uninstall(app) }
        }
    }
}

@Composable
private fun WidgetMenu(s: HomeState, item: Item, bounds: Rect) {
    val w = item as? WidgetItem ?: return
    val side = with(LocalDensity.current) { minOf(bounds.width, bounds.height).toDp() }
    ContextMenu(bounds, { WidgetBody(w.type, side, side, s.notes) }) {
        Card {
            if (w.type == WType.NOTES) {
                MenuRow("Изменить заметку", icon = "✎") { s.overlay = Overlay.EditNotes }
                Divider()
            }
            MenuRow("Изменить экран «Домой»", icon = "▦") { s.overlay = null; s.edit = true }
            Divider()
            MenuRow("Удалить виджет", IosRed, icon = "⊖") { s.overlay = null; s.removeFromHome(w) }
        }
    }
}

@Composable
private fun AddWidget(s: HomeState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText("Добавить виджет", style = LabelStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (t in WType.entries.take(2)) WidgetChoice(s, t)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (t in WType.entries.drop(2)) WidgetChoice(s, t)
        }
    }
}

@Composable
private fun WidgetChoice(s: HomeState, t: WType) {
    Column(
        Modifier.clickable { s.overlay = null; s.addWidget(t) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.shadow(6.dp, WidgetShape, clip = false)) { WidgetBody(t, 140.dp, 140.dp, s.notes) }
        Spacer(Modifier.height(4.dp))
        BasicText(t.title, style = LabelStyle.copy(fontSize = 13.sp))
    }
}

/** Редактор заметки: заголовок + текст, сохраняется сразу.
 *  Тап мимо поля прячет клавиатуру, следующий тап мимо карточки — закрывает. */
@Composable
private fun EditNotes(s: HomeState) {
    var title by remember { mutableStateOf(s.notes.title) }
    var text by remember { mutableStateOf(s.notes.text) }
    val focus = LocalFocusManager.current
    val kb = LocalSoftwareKeyboardController.current
    fun save() = s.setNotes(Note(title.trim().ifBlank { "Заметка" }, text))
    Box(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 520.dp)
                .shadow(16.dp, ContinuousShape(16.dp), clip = false)
                .glass(ContinuousShape(16.dp), SheetGlass, Sheet)
                .clickable(remember { MutableInteractionSource() }, null) { focus.clearFocus(); kb?.hide() },
        ) {
            Row(
                Modifier.fillMaxWidth().background(IosYellow).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText("Заметки", Modifier.weight(1f), style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                BasicText(
                    "Готово", Modifier.clickable { save(); focus.clearFocus(); s.overlay = null },
                    style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                )
            }
            Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
                if (title.isEmpty()) BasicText("Заголовок", style = TextStyle(color = IosGray, fontSize = 22.sp, fontWeight = FontWeight.Bold))
                BasicTextField(
                    title, { title = it; save() }, Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = TextStyle(color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    cursorBrush = SolidColor(IosOrange),
                )
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(16.dp)) {
                if (text.isEmpty()) BasicText("Текст заметки", style = TextStyle(color = IosGray, fontSize = 17.sp))
                BasicTextField(
                    text, { text = it; save() }, Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = Color.Black, fontSize = 17.sp), cursorBrush = SolidColor(IosOrange),
                )
            }
        }
    }
}

/* ======================= Поиск (Spotlight) ======================= */

@Composable
private fun Spotlight(s: HomeState, act: MainActivity, a: Float) {
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val fm = LocalFocusManager.current
    val kb = LocalSoftwareKeyboardController.current
    val ime = WindowInsets.isImeVisible
    LaunchedEffect(s.search) {
        if (s.search) { delay(120); runCatching { focus.requestFocus() } } else q = ""
    }
    val list = remember(s.apps, s.recent, q) {
        if (q.isBlank()) (s.recent.mapNotNull { s.apps[it] } + s.sortedApps).distinctBy { it.key }.take(8)
        else s.sortedApps.filter { it.label.contains(q.trim(), ignoreCase = true) }
            .sortedBy { if (it.label.startsWith(q.trim(), ignoreCase = true)) 0 else 1 }
    }
    Column(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = a; translationY = (1f - a) * -60.dp.toPx() }
            .clickable(remember { MutableInteractionSource() }, null) {
                if (ime) { fm.clearFocus(); kb?.hide() } else s.search = false
            }
            .statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchField(q, { q = it }, "Поиск", Modifier.weight(1f), focus)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.clickable { s.search = false }) {
                BasicText("Отменить", style = TextStyle(color = Color.White, fontSize = 17.sp))
            }
        }
        Spacer(Modifier.height(18.dp))
        BasicText(
            if (q.isBlank()) "ПРЕДЛОЖЕНИЯ SIRI" else "ПРИЛОЖЕНИЯ",
            style = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().glass(ContinuousShape(16.dp), Color.White.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.3f))) {
            if (list.isEmpty()) {
                BasicText("Ничего не найдено", Modifier.padding(16.dp), style = TextStyle(color = Color.White, fontSize = 15.sp))
            } else LazyVerticalGrid(
                GridCells.Fixed(COLS), Modifier.fillMaxWidth().heightIn(max = 460.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(list, key = { it.key }) { app -> LaunchTile(s, act, app, LocalIconSize.current.coerceAtMost(64.dp)) }
            }
        }
    }
}
