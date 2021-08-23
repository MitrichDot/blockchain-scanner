package com.rarible.blockchain.scanner.flow

import org.bouncycastle.util.encoders.Hex
import com.nftco.flow.sdk.AsyncFlowAccessApi
import com.nftco.flow.sdk.Flow
import com.nftco.flow.sdk.Flow.DEFAULT_CHAIN_ID
import com.nftco.flow.sdk.FlowChainId
import com.nftco.flow.sdk.FlowId

object FlowAccessApiClientManager {

    data class Spork(val from: Long, val to: Long = Long.MAX_VALUE, val nodeUrl: String, val port: Int = 9000)

    val sporks = mutableMapOf(
        FlowChainId.TESTNET to listOf(
            Spork(from = 39272449, nodeUrl = "access.devnet.nodes.onflow.org"),
        ),

        FlowChainId.MAINNET to listOf(
            Spork(from = 7601063L, to = 8742958L, nodeUrl = "access-001.mainnet1.nodes.onflow.org"),
            Spork(from = 8742959L, to = 9737132L, nodeUrl = "access-001.mainnet2.nodes.onflow.org"),
            Spork(from = 9737133L, to = 9992019L, nodeUrl = "access-001.mainnet3.nodes.onflow.org"),
            Spork(from = 9992020L, to = 12020336L, nodeUrl = "access-001.mainnet4.nodes.onflow.org"),
            Spork(from = 12020337L, to = 12609236L, nodeUrl = "access-001.mainnet5.nodes.onflow.org"),
            Spork(from = 12609237L, to = 13404173L, nodeUrl = "access-001.mainnet6.nodes.onflow.org"),
            Spork(from = 13404174L, to = 13950741L, nodeUrl = "access-001.mainnet7.nodes.onflow.org"),
            Spork(from = 13950742L, to = 14892103L, nodeUrl = "access-001.mainnet8.nodes.onflow.org"),
            Spork(from = 14892104L, to = 15791890L, nodeUrl = "access-001.mainnet9.nodes.onflow.org"),
            Spork(from = 15791891L, to = 16755601L, nodeUrl = "access-001.mainnet10.nodes.onflow.org"),
            Spork(from = 16755602L, nodeUrl = "access.mainnet.nodes.onflow.org"),
        )
    )

    fun async(blockHeight: Long, chainId: FlowChainId = DEFAULT_CHAIN_ID): AsyncFlowAccessApi {
        val spork = sporkByBlockHeight(blockHeight, chainId)
        return Flow.newAsyncAccessApi(host = spork.nodeUrl, port = spork.port)
    }
    fun async(range: LongRange, chainId: FlowChainId = DEFAULT_CHAIN_ID): AsyncFlowAccessApi {
        val spork = sporkByBlockHeightRange(range, chainId)
        return Flow.newAsyncAccessApi(host = spork.nodeUrl, port = spork.port)
    }



    /**
     * This is bottle-neck!!!
     */
    fun async(hash: String, chainId: FlowChainId = DEFAULT_CHAIN_ID): AsyncFlowAccessApi {
        val blockHeight = sporks[chainId]!!.mapNotNull { Flow.newAsyncAccessApi(it.nodeUrl, it.port).getBlockHeaderById(FlowId(hash)).join() }.first().height
        val spork = sporkByBlockHeight(blockHeight, chainId)
        return Flow.newAsyncAccessApi(host = spork.nodeUrl, port = spork.port)
    }

    fun asyncByTxHAsh(transactionHash: String, chainId: FlowChainId): AsyncFlowAccessApi {
        val blockId = sporks[chainId]!!.mapNotNull { Flow.newAsyncAccessApi(it.nodeUrl, it.port).getTransactionById(FlowId(transactionHash)).join() }.first().referenceBlockId
        return async(Hex.toHexString(blockId.bytes), chainId)
    }

    fun asyncForCurrentSpork(chainId: FlowChainId): AsyncFlowAccessApi {
        val spork = sporks[chainId]!!.last()
        return Flow.newAsyncAccessApi(host = spork.nodeUrl, port = spork.port)
    }

    private fun sporkByBlockHeight(blockHeight: Long, chainId: FlowChainId): Spork =
        sporks[chainId]?.find { it.from <= blockHeight && blockHeight <= it.to }
            ?: throw IllegalArgumentException("Not found spork for blockHeight = $blockHeight in ${chainId.id}")

    private fun sporkByBlockHeightRange(range: LongRange, chainId: FlowChainId): Spork =
        sporks[chainId]?.find { it.from <= range.first && range.last <= it.to }
            ?: throw IllegalArgumentException("Not found spork for block range = $range in ${chainId.id}")

}
