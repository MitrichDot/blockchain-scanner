package com.rarible.blockchain.scanner.flow

import com.rarible.blockchain.scanner.flow.subscriber.AllFlowEventsSubscriber
import com.rarible.blockchain.scanner.flow.subscriber.FlowLogEventSubscriber
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ObsoleteCoroutinesApi
@FlowPreview
@Configuration
@EnableAutoConfiguration
@EnableFlowBlockchainScanner
@ExperimentalCoroutinesApi
class TestConfig {

    @Bean
    fun allEventsSubscriber(): FlowLogEventSubscriber = AllFlowEventsSubscriber()
}