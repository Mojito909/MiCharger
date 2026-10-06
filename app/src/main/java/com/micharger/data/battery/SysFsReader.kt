package com.micharger.data.battery

import com.topjohnwu.superuser.Shell
import java.io.File

class SysFsReader {

    /** 读节点：先试直接读，失败走 shell cat（部分节点需 root 才可见内容） */
    fun read(path: String): String? {
        val file = File(path)
        if (file.canRead()) {
            runCatching { file.readText().trim() }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        val result = Shell.cmd("cat '$path'").exec()
        val line = result.out.firstOrNull()?.trim()
        return line?.takeIf { it.isNotEmpty() && !it.startsWith("cat:") && !it.contains("Permission denied") }
    }

    /** 写节点（需 root），返回是否成功 */
    fun write(path: String, value: String): Boolean =
        Shell.cmd("echo '$value' > '$path'").exec().isSuccess
}
