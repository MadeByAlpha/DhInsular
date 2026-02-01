package com.oasisfeng.island.dhizuku_server

import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.rosan.dhizuku.aidl.IDhizukuClient
import com.rosan.dhizuku.server_api.DhizukuService


class IslandDhizukuService(context: Context, admin: ComponentName?, client: IDhizukuClient?) :
    DhizukuService(context, admin, client) {

    override fun getVersionName(): String = "$versionCode"

    override fun checkCallingPermission(function: String?, callingUid: Int, callingPid: Int): Boolean {
        Log.wtf("Test", "Test")
        return true
    }

}
