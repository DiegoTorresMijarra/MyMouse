package dev.mymouse

import android.app.Activity
import android.content.Context
import android.view.View

/** Variante `play`: sin autoactualizador ni modo quiosco. */
object Flavor {
    const val NAME = "play"
    val updates: UpdateFeature = PlayUpdates
    val kiosk: KioskFeature = NoKiosk
}

private object PlayUpdates : UpdateFeature {
    override fun versionText(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0-dev"
    } catch (e: Exception) {
        "0.0-dev"
    }

    override fun onPcVersion(version: String?) = Unit
    override fun onShown(activity: Activity, listener: () -> Unit) = Unit
    override fun onHidden(activity: Activity, listener: () -> Unit) = Unit
    override fun pill(activity: Activity, notice: (String) -> Unit): View? = null

    /** Solo la versión instalada: las actualizaciones las gestiona Play. */
    override fun settingsSection(activity: SettingsActivity): UpdatesSection = object : UpdatesSection {
        override fun build() {
            activity.section(activity.getString(R.string.settings_about))
            activity.list.addView(activity.label().apply { text = "MyMouse ${versionText(activity)}" })
        }

        override fun onShown() = Unit
        override fun onHidden() = Unit
    }
}

private object NoKiosk : KioskFeature {
    override fun applyPolicies(context: Context) = Unit
    override fun barButton(activity: MainActivity): View? = null
}
