/*
 * Open TimeLimit Copyright <C> 2019 Jonas Lochmann
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.timelimit.android.integration.platform.android

import android.annotation.TargetApi
import android.app.ActivityManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.PowerManager
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.data.model.App
import io.timelimit.android.integration.platform.*
import io.timelimit.android.integration.platform.android.foregroundapp.ForegroundAppHelper
import io.timelimit.android.ui.lock.LockActivity


class AndroidIntegration(context: Context): PlatformIntegration(maximumProtectionLevel) {
    companion object {
        private const val LOG_TAG = "AndroidIntegration"

        val maximumProtectionLevel: ProtectionLevel

        init {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                maximumProtectionLevel = ProtectionLevel.DeviceOwner
            } else {
                maximumProtectionLevel = ProtectionLevel.PasswordDeviceAdmin
            }
        }
    }

    private val context = context.applicationContext
    private val policyManager = this.context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val foregroundAppHelper = ForegroundAppHelper.with(this.context)
    private val powerManager = this.context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val activityManager = this.context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val notificationManager = this.context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val deviceAdmin = ComponentName(context.applicationContext, AdminReceiver::class.java)

    init {
        AppsChangeListener.registerBroadcastReceiver(this.context, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                installedAppsChangeListener?.run()
            }
        })
    }

    override fun getLocalApps(): Collection<App> {
        return AndroidIntegrationApps.getLocalApps(context)
    }

    override fun getLocalAppTitle(packageName: String): String? {
        return AndroidIntegrationApps.getAppTitle(packageName, context)
    }

    override fun getAppIcon(packageName: String): Drawable? {
        return AndroidIntegrationApps.getAppIcon(packageName, context)
    }

    override fun getCurrentProtectionLevel(): ProtectionLevel {
        return AdminStatus.getAdminStatus(context, policyManager)
    }

    override fun getForegroundAppPackageName(): String? {
        return foregroundAppHelper.getForegroundAppPackage()
    }

    override fun getForegroundAppPermissionStatus(): RuntimePermissionStatus {
        return foregroundAppHelper.getPermissionStatus()
    }

    override fun showOverlayMessage(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    override fun getDrawOverOtherAppsPermissionStatus(): RuntimePermissionStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(context)) {
                return RuntimePermissionStatus.Granted
            } else {
                return RuntimePermissionStatus.NotGranted
            }
        } else {
            return RuntimePermissionStatus.NotRequired
        }
    }

    override fun getNotificationAccessPermissionStatus(): NewPermissionStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (activityManager.isLowRamDevice) {
                return NewPermissionStatus.NotSupported
            } else if (NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) {
                return NewPermissionStatus.Granted
            } else {
                return NewPermissionStatus.NotGranted
            }
        } else {
            return NewPermissionStatus.NotSupported
        }
    }

    override fun trySetLockScreenPassword(password: String): Boolean {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "set password")
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            try {
                if (password.isBlank()) {
                    return policyManager.resetPassword("", 0)
                } else if (policyManager.resetPassword(password, DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY)) {
                    policyManager.lockNow()

                    return true
                }
            } catch (ex: SecurityException) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "error setting password", ex)
                }
            }
        }

        return false
    }

    private var lastAppStatusMessage: AppStatusMessage? = null

    override fun setAppStatusMessage(message: AppStatusMessage?) {
        if (lastAppStatusMessage != message) {
            lastAppStatusMessage = message

            BackgroundService.setStatusMessage(message, context)
        }
    }

    override fun showAppLockScreen(currentPackageName: String) {
        LockActivity.start(context, currentPackageName)
    }

    override fun isScreenOn(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            return powerManager.isInteractive
        } else {
            return powerManager.isScreenOn
        }
    }

    override fun setShowNotificationToRevokeTemporarilyAllowedApps(show: Boolean) {
        if (show) {
            NotificationChannels.createAppStatusChannel(notificationManager, context)

            val actionIntent = PendingIntent.getService(
                    context,
                    PendingIntentIds.REVOKE_TEMPORARILY_ALLOWED,
                    BackgroundService.prepareRevokeTemporarilyAllowed(context),
                    PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, NotificationChannels.APP_STATUS)
                    .setSmallIcon(R.drawable.ic_stat_check)
                    .setContentTitle(context.getString(R.string.background_logic_temporarily_allowed_title))
                    .setContentText(context.getString(R.string.background_logic_temporarily_allowed_text))
                    .setContentIntent(actionIntent)
                    .setWhen(0)
                    .setShowWhen(false)
                    .setSound(null)
                    .setOnlyAlertOnce(true)
                    .setLocalOnly(true)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()

            notificationManager.notify(NotificationIds.REVOKE_TEMPORARILY_ALLOWED_APPS, notification)
        } else {
            notificationManager.cancel(NotificationIds.REVOKE_TEMPORARILY_ALLOWED_APPS)
        }
    }

    override fun disableDeviceAdmin() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (policyManager.isDeviceOwnerApp(context.packageName)) {
                setEnableSystemLockdown(false)
                policyManager.clearDeviceOwnerApp(context.packageName)
            }
        }

        policyManager.removeActiveAdmin(deviceAdmin)
    }

    @TargetApi(Build.VERSION_CODES.N)
    override fun setSuspendedApps(packageNames: List<String>, suspend: Boolean): List<String> {
        if (
                (getCurrentProtectionLevel() == ProtectionLevel.DeviceOwner) &&
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        ) {
            val failedApps = policyManager.setPackagesSuspended(
                    deviceAdmin,
                    packageNames.toTypedArray(),
                    suspend
            )

            return packageNames.filterNot { failedApps.contains(it) }
        } else {
            return emptyList()
        }
    }

    override fun setEnableSystemLockdown(enableLockdown: Boolean): Boolean {
        return if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                policyManager.isDeviceOwnerApp(context.packageName)
        ) {
            if (enableLockdown) {
                // disable problematic features
                policyManager.addUserRestriction(deviceAdmin, UserManager.DISALLOW_ADD_USER)
                policyManager.addUserRestriction(deviceAdmin, UserManager.DISALLOW_FACTORY_RESET)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    policyManager.addUserRestriction(deviceAdmin, UserManager.DISALLOW_SAFE_BOOT)
                }
            } else /* disable lockdown */ {
                // enable problematic features
                policyManager.clearUserRestriction(deviceAdmin, UserManager.DISALLOW_ADD_USER)
                policyManager.clearUserRestriction(deviceAdmin, UserManager.DISALLOW_FACTORY_RESET)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    policyManager.clearUserRestriction(deviceAdmin, UserManager.DISALLOW_SAFE_BOOT)
                }

                enableSystemApps()
                stopSuspendingForAllApps()
            }

            true
        } else {
            false
        }
    }

    private fun enableSystemApps() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return
        }

        // disabled system apps (all apps - enabled apps)
        val allApps = context.packageManager.getInstalledApplications(
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1)
                    PackageManager.GET_UNINSTALLED_PACKAGES
                else
                    PackageManager.MATCH_UNINSTALLED_PACKAGES
        )
        val enabledAppsPackages = context.packageManager.getInstalledApplications(0).map { it.packageName }.toSet()

        allApps
                .asSequence()
                .filterNot { enabledAppsPackages.contains(it.packageName) }
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
                .map { it.packageName }
                .forEach { policyManager.enableSystemApp(deviceAdmin, it) }
    }

    override fun stopSuspendingForAllApps() {
        setSuspendedApps(context.packageManager.getInstalledApplications(0).map { it.packageName }, false)
    }

    override fun setLockTaskPackages(packageNames: List<String>): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (policyManager.isDeviceOwnerApp(context.packageName)) {
                policyManager.setLockTaskPackages(deviceAdmin, packageNames.toTypedArray())

                true
            } else {
                false
            }
        } else {
            false
        }
    }
}
