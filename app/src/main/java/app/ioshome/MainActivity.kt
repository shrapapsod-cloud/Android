package app.ioshome

import android.app.ActivityOptions
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import android.content.pm.ShortcutInfo

class MainActivity : ComponentActivity() {
    lateinit var state: HomeState
    private val la by lazy { getSystemService(LauncherApps::class.java) }
    private val handler = Handler(Looper.getMainLooper())

    private val cb = object : LauncherApps.Callback() {
        private fun r() { handler.removeCallbacks(reload); handler.postDelayed(reload, 300) }
        override fun onPackageRemoved(p: String?, u: UserHandle?) = r()
        override fun onPackageAdded(p: String?, u: UserHandle?) = r()
        override fun onPackageChanged(p: String?, u: UserHandle?) = r()
        override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle?, b: Boolean) = r()
        override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle?, b: Boolean) = r()
    }
    private val reload = Runnable { state.reload() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            SystemBarStyle.dark(Color.TRANSPARENT),
            SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        state = HomeState(applicationContext)
        state.reload()
        la.registerCallback(cb, handler)
        setContent { HomeScreen(state, this) }
    }

    override fun onResume() {
        super.onResume()
        state.refreshSettings()
        state.tick++
    }

    override fun onDestroy() {
        la.unregisterCallback(cb)
        super.onDestroy()
    }

    /** Кнопка «Домой», когда мы уже на рабочем столе: закрыть всё и вернуться на первую страницу. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val wasHome = state.edit || state.drag != null || state.overlay != null || state.search
        state.edit = false; state.drag = null; state.overlay = null; state.search = false
        if (!wasHome) state.goPage = 0
    }

    fun launch(app: AppInfo, bounds: Rect?) {
        state.launched(app.key)
        val opts = bounds?.let {
            ActivityOptions.makeScaleUpAnimation(
                window.decorView, it.left.toInt(), it.top.toInt(), it.width.toInt(), it.height.toInt(),
            ).toBundle()
        }
        val r = bounds?.let { android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }
        runCatching { la.startMainActivity(app.info.componentName, app.info.user, r, opts) }
    }

    fun start(i: Intent?) { if (i != null) runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }

    fun appDetails(app: AppInfo) =
        runCatching { la.startAppDetailsActivity(app.info.componentName, app.info.user, null, null) }

    fun uninstall(app: AppInfo) = start(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")))

    /** Свайп вниз — шторка уведомлений. */
    fun expandShade() {
        runCatching {
            val sb = getSystemService("statusbar")!!
            sb.javaClass.getMethod("expandNotificationsPanel").invoke(sb)
        }
    }

    fun badgesAllowed(): Boolean =
        getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(ComponentName(this, BadgeService::class.java))

    fun openBadgeSettings() = start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    fun isDefaultHome(): Boolean = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)

    /** Отдельное полноэкранное окно настроек (с жестом «назад»). */
    fun openSettings() {
        val o = ActivityOptions.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out).toBundle()
        runCatching { startActivity(Intent(this, SettingsActivity::class.java), o) }
    }

    /** Быстрые действия приложения (как меню при долгом нажатии в iOS). Работает, когда iOS Home — по умолчанию. */
    fun shortcuts(app: AppInfo): List<ShortcutInfo> = runCatching {
        if (!la.hasShortcutHostPermission()) return emptyList()
        val q = LauncherApps.ShortcutQuery().setPackage(app.pkg).setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
        )
        (la.getShortcuts(q, app.info.user) ?: emptyList())
            .filter { it.isEnabled && (it.activity == null || it.activity == app.info.componentName) }
            .distinctBy { it.id }
            .sortedWith(compareBy({ !it.isDynamic }, { it.rank }))
            .take(4)
    }.getOrDefault(emptyList())

    fun shortcutIcon(si: ShortcutInfo): ImageBitmap? = runCatching {
        la.getShortcutIconDrawable(si, resources.displayMetrics.densityDpi)?.toBitmap(96, 96)?.asImageBitmap()
    }.getOrNull()

    fun startShortcut(si: ShortcutInfo) { runCatching { la.startShortcut(si, null, null) } }

    fun share(app: AppInfo) = start(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "${app.label}: https://play.google.com/store/apps/details?id=${app.pkg}"),
            null,
        ),
    )

    @Suppress("DEPRECATION")
    fun askDefaultHome() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
            runCatching { startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 1) }
                .onFailure { start(Intent(Settings.ACTION_HOME_SETTINGS)) }
        } else start(Intent(Settings.ACTION_HOME_SETTINGS))
    }
}
