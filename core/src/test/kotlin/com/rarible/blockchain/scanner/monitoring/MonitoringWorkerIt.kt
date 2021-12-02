package com.rarible.blockchain.scanner.monitoring

import com.rarible.blockchain.scanner.test.configuration.AbstractIntegrationTest
import com.rarible.blockchain.scanner.test.configuration.IntegrationTest
import com.rarible.blockchain.scanner.test.data.randomOriginalBlock
import com.rarible.core.test.wait.BlockingWait
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@FlowPreview
@ExperimentalCoroutinesApi
@IntegrationTest
class MonitoringWorkerIt : AbstractIntegrationTest() {

    @Autowired
    lateinit var blockMonitor: BlockMonitor

    @Autowired
    lateinit var registry: MeterRegistry

    @Test
    fun `update block metrics`() = runBlocking {
        saveBlock(randomOriginalBlock())
        saveBlock(randomOriginalBlock())
        saveBlock(randomOriginalBlock())
        saveBlock(randomOriginalBlock())

        BlockingWait.waitAssert {
            assertTrue(blockMonitor.getBlockDelay()!!.toLong() > 0)
            assertTrue(registry.find("blockchain.scanner.block.delay").gauge()!!.value().toLong() > 0)
        }
    }


}
