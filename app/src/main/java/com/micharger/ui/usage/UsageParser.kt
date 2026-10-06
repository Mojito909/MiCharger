package com.micharger.ui.usage

object UsageParser {

    data class UidPower(val uid: Int, val mah: Double)

    private val regex = Regex("""^\s*Uid (u0a(\d+)|(\d+)):\s*([\d.]+)""", RegexOption.MULTILINE)

    /** 解析 dumpsys batterystats --charged 的 Estimated power use 段落 */
    fun parse(output: String): List<UidPower> =
        regex.findAll(output).mapNotNull { m ->
            val uid = m.groupValues[2].toIntOrNull()?.let { it + 10000 }
                ?: m.groupValues[3].toIntOrNull()
            val mah = m.groupValues[4].toDoubleOrNull()
            if (uid != null && mah != null && mah > 0) UidPower(uid, mah) else null
        }.sortedByDescending { it.mah }.take(20).toList()
}
