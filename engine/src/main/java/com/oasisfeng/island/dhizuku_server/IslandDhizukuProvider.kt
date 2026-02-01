package com.oasisfeng.island.dhizuku_server

import com.oasisfeng.island.util.DeviceAdmins
import com.rosan.dhizuku.aidl.IDhizukuClient
import com.rosan.dhizuku.server_api.DhizukuProvider
import com.rosan.dhizuku.server_api.DhizukuService


class IslandDhizukuProvider : DhizukuProvider() {
    override fun onCreateService(p0: IDhizukuClient?): DhizukuService =
        IslandDhizukuService(context!!, DeviceAdmins.getComponentName(context!!), p0)
}
