package com.oasisfeng.island

import android.app.SearchManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import com.oasisfeng.island.console.apps.AppListFragment
import com.oasisfeng.island.setup.SetupActivity
import com.oasisfeng.island.util.CallerAwareActivity
import com.oasisfeng.island.util.DeviceAdmins
import com.oasisfeng.island.util.DevicePolicies
import com.oasisfeng.island.util.Modules
import com.oasisfeng.island.util.Users
import kr.alpha93.lyra.android.compat.getExtra


class MainActivity : FragmentActivity() {

    companion object {
        private const val TAG = "Island.Main"
    }

    private var mIsDeviceOwner = false

    private fun startSetupWizard() {
        startActivity(Intent(this, SetupActivity::class.java))
        finish()
    }

    private fun startMainUi(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_main)
        if (savedInstanceState != null) return

        val fragment = AppListFragment()
        if (Intent.ACTION_SEARCH == intent.action) {
            fragment.arguments = Bundle().apply {
                putString(SearchManager.QUERY, intent.getStringExtra(SearchManager.QUERY))
                intent.getExtra<UserHandle>(Intent.EXTRA_USER)?.also { putParcelable(Intent.EXTRA_USER, it) }
            }
        }

        supportFragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val policies = DevicePolicies(this)

        if (!Users.isParentProfile()) {
            if (!policies.isProfileOwner) {
                startSetupWizard() // Running in non-primary user or profile not managed by Island.
                // TODO: Proper handling?

                return
            }

            // Should generally not run in profile, unless the managed profile provision is interrupted or manual provision is not complete.
            onCreateInProfile()
            finish()
            return
        }

        val caller = CallerAwareActivity.getCallingPackage(this)
        if (Modules.MODULE_ENGINE == caller) Users.refreshUsers(this) // Possibly started by IslandProvisioning, refresh user state as a profile or its owner may be changed.

        mIsDeviceOwner = policies.isProfileOrDeviceOwnerOnCallingUser
        if (mIsDeviceOwner) {
            startMainUi(savedInstanceState) // As a device owner, always show the main UI.
            return
        }

        val user = Users.profile
        if (user == null) {
            // Nothing setup yet
            Log.i(TAG, "Profile not setup yet")
            startSetupWizard()
            return
        }

        val launcherApps = getSystemService<LauncherApps>()
        launcherApps?.getActivityList(packageName, user)?.takeIf { it.isNotEmpty() }?.let { insularActivities ->
            if (insularActivities.first().componentName.className != MainActivity::class.java.name) return@let

            try {
                launcherApps.startMainActivity(insularActivities.first().componentName, user, null, null)
            } catch (_: RuntimeException) {
                //`$`().logAndReport(TAG, "Error starting self in profile " + user.toId(), e)
                startSetupWizard()
            }
            finish()
            return
        }

        startMainUi(savedInstanceState)
    }

    private fun onCreateInProfile() {
        val policies = DevicePolicies(this)
        if (!policies(DevicePolicyManager::isAdminActive)) {
            //`$`().event("inactive_device_admin").send()
            startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(
                        DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                        DeviceAdmins.getComponentName(this)
                    )
                    .putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        getString(R.string.dialog_reactivate_message)
                    )
            )
            return
        }
        val resolves = packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_USER_INITIALIZE).setPackage(
                Modules.MODULE_ENGINE
            ), 0
        )
        val resolve = resolves
            .firstOrNull { it.activityInfo.name.startsWith("com.oasisfeng.island.provision") }
        if (resolve != null) {
            Log.w(TAG, "Manual provisioning is pending, resume it now.")
            //`$`().event("profile_post_provision_pending").send()
            val receiver = resolve.activityInfo
            sendBroadcast(Intent().setClassName(receiver.packageName, receiver.name))
        } else {    // Receiver disabled but launcher entrance is left enabled. The best bet is just disabling the launcher entrance. No provisioning attempt any more.
            Log.w(
                TAG,
                "Manual provisioning is finished, but launcher activity is still left enabled. Disable it now."
            )
            //`$`().event("profile_post_provision_activity_leftover").send()
            packageManager.setComponentEnabledSetting(
                ComponentName(this, javaClass),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

}
