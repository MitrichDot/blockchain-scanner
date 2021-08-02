package com.rarible.blockchain.scanner.ethereum.client

import com.rarible.blockchain.scanner.data.BlockLogs
import com.rarible.blockchain.scanner.data.TransactionMeta
import com.rarible.blockchain.scanner.framework.client.BlockchainClient
import com.rarible.blockchain.scanner.subscriber.LogEventDescriptor
import io.daonomic.rpc.domain.Word
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.util.retry.RetryBackoffSpec
import scalether.core.EthPubSub
import scalether.core.MonoEthereum
import scalether.domain.Address
import scalether.domain.request.LogFilter
import scalether.domain.request.TopicFilter
import scalether.domain.response.Log
import scalether.util.Hex
import java.math.BigInteger
import java.time.Duration
import java.util.*

@Component
// TODO configure retry spec for all methods
class EthereumClient(
    private val ethereum: MonoEthereum,
    private val ethPubSub: EthPubSub,
    private val backoff: RetryBackoffSpec
) : BlockchainClient<EthereumBlockchainBlock, EthereumBlockchainLog> {

    private val logger: Logger = LoggerFactory.getLogger(EthereumClient::class.java)

    override fun listenNewBlocks(): Flow<EthereumBlockchainBlock> {
        return ethPubSub.newHeads()
            .map { EthereumBlockchainBlock(it) }
            .timeout(Duration.ofMinutes(5))
            .asFlow()
    }

    override suspend fun getBlock(hash: String): EthereumBlockchainBlock {
        return ethereum.ethGetBlockByHash(Word.apply(hash)).map {
            EthereumBlockchainBlock(it)
        }.awaitFirst()
    }

    override suspend fun getBlock(id: Long): EthereumBlockchainBlock {
        return ethereum.ethGetBlockByNumber(BigInteger.valueOf(id)).map {
            EthereumBlockchainBlock(it)
        }.awaitFirst()
    }

    override suspend fun getLastBlockNumber(): Long {
        return ethereum.ethBlockNumber().map { it.toLong() }.awaitFirst()
    }

    override suspend fun getTransactionMeta(transactionHash: String): Optional<TransactionMeta> {
        return ethereum.ethGetTransactionByHash(Word.apply(transactionHash)).map {
            if (it.isEmpty) {
                Optional.empty()
            } else {
                val tx = it.get()
                Optional.of(
                    TransactionMeta(
                        tx.hash().toString(),
                        tx.blockNumber().toLong(),
                        tx.blockHash().toString()
                    )
                )
            }
        }.awaitFirst()
    }

    override suspend fun getBlockEvents(
        block: EthereumBlockchainBlock,
        descriptor: LogEventDescriptor
    ): List<EthereumBlockchainLog> {
        val filter = LogFilter
            .apply(TopicFilter.simple(Word.apply(descriptor.topic))) // TODO ???
            .address(*descriptor.contracts.map { Address.apply(it) }.toTypedArray())
            .blockHash(block.ethBlock.hash())

        return ethereum.ethGetLogsJava(filter)
            .map { orderByTransaction(it).map { log -> EthereumBlockchainLog(log) } }
            .doOnError { logger.warn("Unable to get logs for block ${block.ethBlock.hash()}", it) }
            .retryWhen(backoff)
            .awaitFirst()
    }

    //todo помнишь, мы обсуждали, что нужно сделать, чтобы index события брался немного по другим параметрам?
    //todo (уникальный чтобы считался внутри транзакции, topic, address). это ты учел тут?
    override fun getBlockEvents(
        descriptor: LogEventDescriptor,
        range: LongRange
    ): Flow<BlockLogs<EthereumBlockchainLog>> {

        val addresses = descriptor.contracts.map { Address.apply(it) }
        val filter = LogFilter
            .apply(TopicFilter.simple(Word.apply(descriptor.topic))) // TODO ???
            .address(*addresses.toTypedArray())
        val finalFilter = filter.blocks(
            BigInteger.valueOf(range.first).encodeForFilter(),
            BigInteger.valueOf(range.last).encodeForFilter()
        )
        logger.info("loading logs $finalFilter range=$range")

        return ethereum.ethGetLogsJava(finalFilter)
            .doOnNext {
                logger.info("loaded ${it.size} logs for range $range")
            }.flatMapIterable { allLogs ->
                allLogs.groupBy { log ->
                    log.blockHash()
                }.entries.map { e ->
                    val orderedLogs = orderByTransaction(e.value)
                    BlockLogs(e.key.toString(), orderedLogs.map { EthereumBlockchainLog(it) })
                }
            }.doOnError {
                logger.warn("Unable to get Logs for descriptor [{}] from Block range {}", descriptor, range, it)
            }
            .retryWhen(backoff).asFlow()
    }

    private fun orderByTransaction(logs: List<Log>): List<Log> {
        return logs.groupBy {
            it.transactionHash()
        }.values.flatMap { logsInTransaction ->
            logsInTransaction.sortedBy { log ->
                log.logIndex()
            }
        }
    }
}

private fun BigInteger.encodeForFilter(): String {
    return "0x${Hex.to(this.toByteArray()).trimStart('0')}"
}
