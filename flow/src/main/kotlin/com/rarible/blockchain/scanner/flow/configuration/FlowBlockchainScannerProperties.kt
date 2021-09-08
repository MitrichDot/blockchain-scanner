package com.rarible.blockchain.scanner.flow.configuration

import com.rarible.blockchain.scanner.configuration.*
import com.rarible.core.daemon.DaemonWorkerProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.time.Duration

@ConstructorBinding
@ConfigurationProperties(prefix = "blockchain.scanner.flow")
data class FlowBlockchainScannerProperties(
    override val blockchain: String = "flow",
    override val retryPolicy: RetryPolicyProperties = RetryPolicyProperties(),
    override val monitoring: MonitoringProperties = MonitoringProperties(
        worker = DaemonWorkerProperties(pollingPeriod = Duration.ofMillis(200L), errorDelay = Duration.ofSeconds(1L), backpressureSize = 100, buffer = false)
    ),
    override val job: JobProperties = JobProperties(reconciliation = ReconciliationJobProperties(batchSize = 1000L))
): BlockchainScannerProperties
