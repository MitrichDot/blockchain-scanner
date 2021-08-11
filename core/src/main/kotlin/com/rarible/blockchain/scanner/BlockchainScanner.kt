package com.rarible.blockchain.scanner

import com.rarible.blockchain.scanner.configuration.BlockchainScannerProperties
import com.rarible.blockchain.scanner.data.BlockEvent
import com.rarible.blockchain.scanner.framework.client.BlockchainBlock
import com.rarible.blockchain.scanner.framework.client.BlockchainClient
import com.rarible.blockchain.scanner.framework.client.BlockchainLog
import com.rarible.blockchain.scanner.framework.mapper.BlockMapper
import com.rarible.blockchain.scanner.framework.mapper.LogMapper
import com.rarible.blockchain.scanner.framework.model.Block
import com.rarible.blockchain.scanner.framework.model.Descriptor
import com.rarible.blockchain.scanner.framework.model.Log
import com.rarible.blockchain.scanner.framework.model.LogRecord
import com.rarible.blockchain.scanner.framework.service.BlockService
import com.rarible.blockchain.scanner.framework.service.LogService
import com.rarible.blockchain.scanner.framework.service.PendingLogService
import com.rarible.blockchain.scanner.pending.DefaultPendingBlockChecker
import com.rarible.blockchain.scanner.pending.DefaultPendingLogChecker
import com.rarible.blockchain.scanner.pending.PendingBlockChecker
import com.rarible.blockchain.scanner.pending.PendingLogChecker
import com.rarible.blockchain.scanner.reconciliation.ReconciliationExecutor
import com.rarible.blockchain.scanner.reconciliation.ReconciliationService
import com.rarible.blockchain.scanner.subscriber.LogEventListener
import com.rarible.blockchain.scanner.subscriber.LogEventSubscriber
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import java.time.Duration

@FlowPreview
@ExperimentalCoroutinesApi
open class BlockchainScanner<BB : BlockchainBlock, BL : BlockchainLog, B : Block, L : Log, R : LogRecord<L, *>, D : Descriptor>(
    blockchainClient: BlockchainClient<BB, BL, D>,
    subscribers: List<LogEventSubscriber<BB, BL, L, R, D>>,
    blockMapper: BlockMapper<BB, B>,
    blockService: BlockService<B>,
    logMapper: LogMapper<BB, BL, L>,
    logService: LogService<L, R, D>,
    pendingLogService: PendingLogService<BB, L, R, D>,
    logEventListeners: List<LogEventListener<L, R>>,
    properties: BlockchainScannerProperties

) : PendingLogChecker, BlockListener, PendingBlockChecker, ReconciliationExecutor {

    private val blockScanner = BlockScanner(
        blockchainClient,
        blockMapper,
        blockService,
        properties
    )

    private val logEventPublisher = LogEventPublisher(
        logEventListeners,
        properties
    )

    private val blockListener = BlockEventListener(
        blockchainClient,
        subscribers,
        blockService,
        logMapper,
        logService,
        pendingLogService,
        logEventPublisher
    )

    private val pendingLogChecker = DefaultPendingLogChecker(
        blockchainClient,
        logService,
        subscribers.map { it.getDescriptor() },
        blockListener,
        logEventListeners
    )

    private val pendingBlockChecker = DefaultPendingBlockChecker(
        blockchainClient,
        blockService,
        blockListener
    )

    private val reconciliationService = ReconciliationService(
        blockchainClient,
        subscribers,
        logMapper,
        logService,
        logEventPublisher,
        properties
    )

    private val descriptorIds = subscribers.map { it.getDescriptor().id }.toSet()

    suspend fun scan() {
        blockScanner.scan(blockListener)
    }

    override suspend fun onBlockEvent(event: BlockEvent) {
        return blockListener.onBlockEvent(event)
    }

    override fun checkPendingLogs() {
        pendingLogChecker.checkPendingLogs()
    }

    override fun checkPendingBlocks(pendingBlockAgeToCheck: Duration) {
        pendingBlockChecker.checkPendingBlocks(pendingBlockAgeToCheck)
    }

    override fun reconcile(descriptorId: String?, from: Long): Flow<LongRange> {
        return reconciliationService.reindex(descriptorId, from)
    }

    override fun getDescriptorIds(): Set<String> {
        return descriptorIds
    }
}