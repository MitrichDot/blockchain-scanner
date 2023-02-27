package com.rarible.blockchain.scanner.task

import com.rarible.blockchain.scanner.BlockchainScannerManager
import com.rarible.blockchain.scanner.framework.client.BlockchainBlock
import com.rarible.blockchain.scanner.framework.client.BlockchainLog
import com.rarible.blockchain.scanner.framework.model.Descriptor
import com.rarible.blockchain.scanner.framework.model.LogRecord
import com.rarible.blockchain.scanner.reindex.ReindexParam
import com.rarible.blockchain.scanner.reindex.SubscriberFilter
import com.rarible.core.task.TaskHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.slf4j.Logger
import org.slf4j.LoggerFactory

// Implement in blockchains if needed
abstract class BlockReindexTaskHandler<BB : BlockchainBlock, BL : BlockchainLog, R : LogRecord, D : Descriptor, P : ReindexParam>(
    manager: BlockchainScannerManager<BB, BL, R, D>
) : TaskHandler<Long> {

    protected val logger: Logger = LoggerFactory.getLogger(javaClass)

    override val type = "BLOCK_SCANNER_REINDEX_TASK"

    private val enabled = manager.properties.task.reindex.enabled
    private val monitor = manager.reindexMonitor

    override suspend fun isAbleToRun(param: String): Boolean {
        return param.isNotBlank() && enabled
    }

    private val reindexer = manager.blockReindexer
    private val planner = manager.blockScanPlanner

    override fun runLongTask(from: Long?, param: String): Flow<Long> {
        val taskParam = getParam(param)
        val filter = getFilter(taskParam)

        return flow {
            val (reindexRanges, baseBlock, planFrom, planTo) = planner.getPlan(taskParam.range, from)
            val blocks = reindexer.reindex(
                baseBlock,
                reindexRanges,
                filter
            ).onEach {
                monitor.onReindex(
                    name = taskParam.name,
                    from = taskParam.range.from,
                    to = taskParam.range.to,
                    state = getTaskProgress(planFrom, planTo, it.id)
                )
            }
            emitAll(blocks)
        }.map {
            logger.info("Re-index finished up to block $it")
            it.id
        }
    }

    private fun getTaskProgress(from: Long, to: Long, position: Long): Double {
        if (position == 0L || from > to) return 0.toDouble()
        return (to.toDouble() - from.toDouble()) / position.toDouble()
    }

    abstract fun getFilter(param: P): SubscriberFilter<BB, BL, R, D>

    abstract fun getParam(param: String): P

}