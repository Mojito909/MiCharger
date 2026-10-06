package com.micharger.data.battery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChargingController(
    private val reader: SysFsReader,
    private val detector: ChargingNodeDetector,
) {

    var nodes: ChargingNodes? = null
        private set
    private var originalCurrent: Int? = null

    /** 是否已检测到可用的控制节点（用于判断是否需要重新初始化） */
    val canControl: Boolean
        get() = nodes?.let { it.inputSuspendNode != null || it.enableNode != null } == true

    suspend fun initialize(): ChargingNodes? = withContext(Dispatchers.IO) {
        nodes = detector.detect()
        nodes?.currentMaxNode
            ?.let { reader.read(it)?.toIntOrNull() }
            ?.let { originalCurrent = it }
        nodes
    }

    /** 是否处于暂停充电状态；null 表示无法判断 */
    suspend fun isSuspended(): Boolean? = withContext(Dispatchers.IO) {
        val n = nodes ?: return@withContext null
        when {
            n.inputSuspendNode != null -> reader.read(n.inputSuspendNode)?.toIntOrNull() == 1
            n.enableNode != null -> reader.read(n.enableNode)?.toIntOrNull() == 0
            else -> null
        }
    }

    suspend fun pause(): Boolean = withContext(Dispatchers.IO) {
        val n = nodes ?: return@withContext false
        val result = when {
            n.inputSuspendNode != null -> reader.write(n.inputSuspendNode, "1")
            n.enableNode != null -> reader.write(n.enableNode, "0")
            else -> false
        }
        result
    }

    suspend fun resume(): Boolean = withContext(Dispatchers.IO) {
        val n = nodes ?: return@withContext false
        when {
            n.inputSuspendNode != null -> reader.write(n.inputSuspendNode, "0")
            n.enableNode != null -> reader.write(n.enableNode, "1")
            else -> false
        }
    }

    /** 恢复初始化时记录的原始限流值，撤销历史遗留的电流写入 */
    suspend fun clearCurrentLimit(): Boolean = withContext(Dispatchers.IO) {
        val path = nodes?.currentMaxNode ?: return@withContext false
        val original = originalCurrent ?: return@withContext false
        reader.write(path, original.toString())
    }
}
