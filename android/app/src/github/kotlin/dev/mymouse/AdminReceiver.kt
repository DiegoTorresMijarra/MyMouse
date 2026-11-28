package dev.mymouse

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.BatteryManager
import android.provider.Settings

/**
 * Receptor de administración. Solo tiene efecto si se registra la app como propietaria del dispositivo:
 *   adb shell dpm set-device-owner dev.mymouse/.AdminReceiver
 * Ver docs/spec-v0.md §2.1.1 (modo bloqueado completo).
 */
class AdminReceiver : DeviceAdminReceiver() {

    companion object {
        fun component(context: Context) = ComponentName(context, AdminReceiver::class.java)

        fun isDeviceOwner(context: Context): Boolean =
            context.getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(context.packageName)

        /** Permite el bloqueo sin confirmación, sin pantalla de bloqueo y con la pantalla encendida mientras carga. */
        fun applyKioskPolicies(context: Context) {
            if (!isDeviceOwner(context)) return
            val dpm = context.getSystemService(DevicePolicyManager::class.java)
            val admin = component(context)
            dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
            dpm.setKeyguardDisabled(admin, true)
            val plugged = BatteryManager.BATTERY_PLUGGED_AC or BatteryManager.BATTERY_PLUGGED_USB or
                BatteryManager.BATTERY_PLUGGED_WIRELESS
            dpm.setGlobalSetting(admin, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, plugged.toString())
        }

        /** Deshace todo lo anterior y deja de ser propietaria: el móvil vuelve a ser normal. */
        @Suppress("DEPRECATION") // clearDeviceOwnerApp: la alternativa moderna exige más permisos.
        fun releaseDeviceOwner(context: Context) {
            if (!isDeviceOwner(context)) return
            val dpm = context.getSystemService(DevicePolicyManager::class.java)
            val admin = component(context)
            dpm.setLockTaskPackages(admin, emptyArray())
            dpm.setKeyguardDisabled(admin, false)
            dpm.setGlobalSetting(admin, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, "0")
            dpm.clearDeviceOwnerApp(context.packageName)
        }
    }
}
