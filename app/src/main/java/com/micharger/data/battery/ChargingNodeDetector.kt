package com.micharger.data.battery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ChargingNodes(
    val inputSuspendNode: String?,   // 写 1 暂停充电 / 0 恢复（优先）
    val enableNode: String?,         // 写 1 开启 / 0 关闭（降级方案）
    val currentMaxNode: String?,     // 限流 mA
)

class ChargingNodeDetector(private val reader: SysFsReader) {

    suspend fun detect(): ChargingNodes = withContext(Dispatchers.IO) {
        val base = "/sys/class/power_supply/battery"
        ChargingNodes(
            inputSuspendNode = detectNode("$base/input_suspend"),
            enableNode = detectNode("$base/battery_charging_enabled")
                ?: detectNode("$base/charging_enabled"),
            currentMaxNode = detectNode("$base/constant_charge_current_max"),
        )
    }

    /** 用「读出当前值并原样写回」验证节点可写（原样写回无副作用） */
    private fun detectNode(path: String): String? {
        val current = reader.read(path)?.toIntOrNull() ?: return null
        return if (reader.write(path, current.toString())) path else null
    }
}
