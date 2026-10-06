package com.micharger.util

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RootChecker {
    /** 无 root 设备不会弹授权，直接返回 false；有 root 首次调用会触发 Magisk 授权弹窗 */
    suspend fun check(): Boolean = withContext(Dispatchers.IO) {
        Shell.getShell()
        Shell.isAppGrantedRoot() == true
    }
}
