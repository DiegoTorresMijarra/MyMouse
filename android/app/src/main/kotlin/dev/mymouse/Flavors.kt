package dev.mymouse

import android.app.Activity
import android.content.Context
import android.view.View

/**
 * Lo que cambia entre las dos variantes de la app (docs/spec-v7-guia.md §1.2):
 * - `github`: se actualiza sola desde las releases de GitHub y tiene el modo quiosco (propietario del dispositivo).
 * - `play`: ninguna de las dos cosas (Play no las permite o las revisa con lupa); aquí todo está vacío.
 * Cada variante define `Flavor` en `src/github/` o `src/play/`; el código común solo conoce estas interfaces.
 */
interface UpdateFeature {
    /** `versionName` de esta app: `5.0` en una release, `0.0-dev.N` en las demás. */
    fun versionText(context: Context): String

    /** La versión de MyMouse del PC (`APP_VERSION`), o null al perder la conexión. Puede llamarse desde otro hilo. */
    fun onPcVersion(version: String?)

    /** La pantalla principal pasa a primer plano / a segundo plano. [listener] se llama cuando cambia algo que mostrar. */
    fun onShown(activity: Activity, listener: () -> Unit)
    fun onHidden(activity: Activity, listener: () -> Unit)

    /** La píldora de la barra con el aviso de versión nueva, o null si no hay nada que avisar. [notice] muestra un aviso sobre el trackpad. */
    fun pill(activity: Activity, notice: (String) -> Unit): View?

    /** La tarjeta de Ajustes con la versión y, en la variante `github`, las actualizaciones. */
    fun settingsSection(activity: SettingsActivity): UpdatesSection
}

/** La tarjeta de actualizaciones de Ajustes. */
interface UpdatesSection {
    fun build()
    fun onShown()
    fun onHidden()
}

/** El modo quiosco (propietario del dispositivo): solo en la variante `github`. */
interface KioskFeature {
    /** Aplica las políticas si la app es propietaria del dispositivo. */
    fun applyPolicies(context: Context)

    /** Un botón extra de la barra de la pantalla principal ("Quitar propietario"), o null. */
    fun barButton(activity: MainActivity): View?
}
