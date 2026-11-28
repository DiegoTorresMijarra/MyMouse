package dev.mymouse

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import dev.mymouse.core.update.BannerKind
import dev.mymouse.core.update.UpdateBanner
import dev.mymouse.core.update.UpdatePolicy

/** Variante `github`: avisos de versión nueva desde las releases de GitHub y modo quiosco (propietario del dispositivo). */
object Flavor {
    const val NAME = "github"
    val updates: UpdateFeature = GithubUpdates
    val kiosk: KioskFeature = GithubKiosk
}

private object GithubUpdates : UpdateFeature {
    override fun versionText(context: Context) = Updater.localVersionText(context)

    override fun onPcVersion(version: String?) = Updater.setPcVersion(version)

    override fun onShown(activity: Activity, listener: () -> Unit) {
        Updater.addListener(listener)
        Updater.start(activity)
        Updater.onForeground(activity) // si había un APK esperando (permiso o app en segundo plano), se abre el instalador
    }

    override fun onHidden(activity: Activity, listener: () -> Unit) {
        Updater.removeListener(listener)
        Updater.onBackground(activity)
    }

    /** El texto de un aviso, en el idioma del móvil (el núcleo solo da los datos). */
    fun bannerText(context: Context, banner: UpdateBanner): String = when (banner.kind) {
        BannerKind.NEW_VERSION -> context.getString(R.string.upd_banner_new, banner.version.display)
        BannerKind.PC_NEWER -> context.getString(R.string.upd_banner_pc_newer, banner.version.display, banner.mineVersion?.display.orEmpty())
        BannerKind.PC_OLDER -> context.getString(R.string.upd_banner_pc_older, banner.version.display)
    }

    // ---- Píldora y hoja de novedades (docs/spec-v5.1-actualizaciones.md §5 y §6) ----

    /** La píldora de actualización, o null si no hay nada que avisar. Solo existe en modo ventana (en modo bloqueado la barra no se ve). */
    override fun pill(activity: Activity, notice: (String) -> Unit): View? {
        val density = activity.resources.displayMetrics.density
        val download = Updater.download
        val (label, onClick) = when {
            download != null && download.failed -> activity.getString(R.string.upd_pill_error) to { showUpdateError(activity, download.message) }
            download != null -> activity.getString(R.string.upd_pill_downloading, (download.progress * 100).toInt()) to
                { notice(activity.getString(R.string.upd_notice_downloading)) }
            else -> {
                val banner = Updater.banners(activity).firstOrNull() ?: return null
                val text = when (banner.kind) {
                    BannerKind.NEW_VERSION -> activity.getString(R.string.upd_pill_update_to, banner.release!!.version.display)
                    BannerKind.PC_NEWER -> banner.release?.let { activity.getString(R.string.upd_pill_update_to, it.version.display) }
                        ?: activity.getString(R.string.upd_pill_pc_newer)
                    BannerKind.PC_OLDER -> activity.getString(R.string.upd_pill_pc_older)
                }
                text to { showUpdateSheet(activity) }
            }
        }
        return GlassTheme.IconButton(activity, GlassTheme.IconButton.Kind.UPDATE, label = label, highlight = true).apply {
            contentDescription = label
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (44 * density).toInt()).apply { marginStart = (8 * density).toInt() }
        }
    }

    /** La hoja de novedades: el texto de la release (recortado) y "Descargar e instalar" / "Omitir"; sin botones si solo informa. */
    private fun showUpdateSheet(activity: Activity) {
        val banners = Updater.banners(activity)
        val primary = banners.firstOrNull() ?: return
        val message = StringBuilder()
        message.append(activity.getString(R.string.upd_sheet_you_have, Updater.localVersionText(activity)))
        primary.release?.notes?.let { UpdatePolicy.trimNotes(it).takeIf { notes -> notes.isNotEmpty() }?.let { notes -> message.append("\n\n").append(notes) } }
        banners.drop(1).forEach { message.append("\n\n").append(bannerText(activity, it)) }
        val builder = AlertDialog.Builder(activity).setTitle(bannerText(activity, primary)).setMessage(message.toString())
        val release = primary.release
        if (primary.canInstall && release != null) {
            builder.setPositiveButton(R.string.upd_download_install) { _, _ -> Updater.downloadAndInstall(activity, release) }
            if (primary.canSkip) builder.setNegativeButton(R.string.upd_skip) { _, _ -> Updater.skipOffered(activity) }
            builder.setNeutralButton(R.string.upd_later, null)
        } else {
            builder.setPositiveButton(R.string.upd_understood, null)
        }
        builder.show()
    }

    private fun showUpdateError(activity: Activity, message: String?) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.upd_error_title)
            .setMessage(message ?: activity.getString(R.string.upd_error_retry))
            .setPositiveButton(R.string.ok) { _, _ -> Updater.clearFailure() }
            .setOnCancelListener { Updater.clearFailure() }
            .show()
    }

    // ---- Tarjeta de Ajustes (docs/spec-v5.1-actualizaciones.md §5) ----

    override fun settingsSection(activity: SettingsActivity): UpdatesSection = object : UpdatesSection {
        private var status: TextView? = null
        private var install: View? = null
        private val listener: () -> Unit = { refresh() }
        private val density = activity.resources.displayMetrics.density

        override fun build() {
            activity.section(activity.getString(R.string.upd_settings_title))
            activity.list.addView(activity.label().apply { text = "MyMouse ${Updater.localVersionText(activity)}" })
            status = GlassTheme.note(activity, "").apply { setPadding(0, (4 * density).toInt(), 0, (8 * density).toInt()) }
            activity.list.addView(status)
            activity.list.addView(GlassTheme.button(activity, activity.getString(R.string.upd_check_now)) {
                status?.text = activity.getString(R.string.upd_checking)
                Updater.checkNow(activity, manual = true) { refresh() }
            })
            install = GlassTheme.button(activity, activity.getString(R.string.upd_download_install), accent = true) {
                Updater.banners(activity).firstOrNull { it.canInstall }?.release?.let { Updater.downloadAndInstall(activity, it) }
            }
            activity.list.addView(install)
            activity.list.addView(activity.switch(activity.getString(R.string.upd_auto_check), Updater.autoCheck(activity)) { Updater.setAutoCheck(activity, it) })
            activity.list.addView(GlassTheme.note(activity, activity.getString(R.string.upd_settings_note)))
            refresh()
        }

        override fun onShown() {
            Updater.addListener(listener)
            Updater.onForeground(activity) // si había un APK esperando, se abre el instalador desde aquí
            refresh()
        }

        override fun onHidden() {
            Updater.removeListener(listener)
            Updater.onBackground(activity)
        }

        private fun refresh() {
            val text = status ?: return
            val banners = Updater.banners(activity)
            val download = Updater.download
            val installable = banners.firstOrNull { it.canInstall }
            text.text = when {
                download != null && download.failed -> download.message ?: activity.getString(R.string.upd_status_failed)
                download != null -> activity.getString(R.string.upd_status_downloading, (download.progress * 100).toInt())
                Updater.isDev(activity) -> activity.getString(R.string.upd_status_dev)
                banners.isNotEmpty() -> banners.joinToString("\n") { bannerText(activity, it) }
                Updater.lastOutcome == Updater.Outcome.UP_TO_DATE -> activity.getString(R.string.upd_status_up_to_date)
                Updater.lastOutcome == Updater.Outcome.SKIPPED -> activity.getString(R.string.upd_status_skipped)
                else -> activity.getString(R.string.upd_status_unchecked)
            }
            install?.visibility = if (installable != null && download == null) View.VISIBLE else View.GONE
        }
    }
}

private object GithubKiosk : KioskFeature {
    override fun applyPolicies(context: Context) = AdminReceiver.applyKioskPolicies(context)

    override fun barButton(activity: MainActivity): View? {
        if (!AdminReceiver.isDeviceOwner(activity)) return null
        val density = activity.resources.displayMetrics.density
        return GlassTheme.button(activity, activity.getString(R.string.kiosk_release_button)) { confirmRelease(activity) }.apply {
            (layoutParams as LinearLayout.LayoutParams).apply { topMargin = 0; marginStart = (8 * density).toInt(); marginEnd = 0 }
        }
    }

    private fun confirmRelease(activity: MainActivity) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.kiosk_release_title)
            .setMessage(R.string.kiosk_release_message)
            .setPositiveButton(R.string.kiosk_release_confirm) { _, _ ->
                AdminReceiver.releaseDeviceOwner(activity)
                activity.recreate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
