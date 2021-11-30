package com.rarible.blockchain.scanner

import com.rarible.blockchain.scanner.event.log.LogEventHandler
import com.rarible.blockchain.scanner.framework.data.FullBlock
import com.rarible.blockchain.scanner.framework.data.NewBlockEvent
import com.rarible.blockchain.scanner.framework.data.RevertedBlockEvent
import com.rarible.blockchain.scanner.framework.data.Source
import com.rarible.blockchain.scanner.framework.model.Log
import com.rarible.blockchain.scanner.test.client.TestBlockchainBlock
import com.rarible.blockchain.scanner.test.client.TestBlockchainLog
import com.rarible.blockchain.scanner.test.configuration.AbstractIntegrationTest
import com.rarible.blockchain.scanner.test.configuration.IntegrationTest
import com.rarible.blockchain.scanner.test.data.assertRecordAndLogEquals
import com.rarible.blockchain.scanner.test.data.randomBlockHash
import com.rarible.blockchain.scanner.test.data.randomBlockchainBlock
import com.rarible.blockchain.scanner.test.data.randomBlockchainLog
import com.rarible.blockchain.scanner.test.data.randomTestLogRecord
import com.rarible.blockchain.scanner.test.data.testDescriptor1
import com.rarible.blockchain.scanner.test.model.TestCustomLogRecord
import com.rarible.blockchain.scanner.test.model.TestDescriptor
import com.rarible.blockchain.scanner.test.model.TestLog
import com.rarible.blockchain.scanner.test.model.TestLogRecord
import com.rarible.blockchain.scanner.test.subscriber.TestLogEventSubscriber
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
@FlowPreview
@IntegrationTest
internal class LogEventHandlerIt : AbstractIntegrationTest() {

    private val descriptor = testDescriptor1()
    private val collection = descriptor.collection
    private val topic = descriptor.topic

    @Test
    fun `handle logs - log saved with minor index`() = runBlocking {
        val handler = createHandler(TestLogEventSubscriber(descriptor, 2))
        val block = randomBlockchainBlock()
        val log = randomBlockchainLog(block, topic)

        val savedLogs = handler.handleLogs(FullBlock(block, listOf(log))).toCollection(mutableListOf())

        assertEquals(2, savedLogs.size)

        val savedLog1 = findLog(collection, savedLogs[0].id)!!
        val savedLog2 = findLog(collection, savedLogs[1].id)!!

        // These event logs are based on same Blockchain log, so basic params should be the same
        assertTrue(savedLog1 is TestCustomLogRecord)
        assertTrue(savedLog1 is TestCustomLogRecord)
        assertRecordAndLogEquals(savedLog1, log.testOriginalLog, block.testOriginalBlock)
        assertRecordAndLogEquals(savedLog2, log.testOriginalLog, block.testOriginalBlock)
        assertEquals(0, savedLog1.log!!.index)
        assertEquals(0, savedLog2.log!!.index)

        // Minor index should be different because there are 2 custom data objects generated for single Blockchain Log
        assertEquals(0, savedLog1.log!!.minorLogIndex)
        assertEquals(1, savedLog2.log!!.minorLogIndex)
    }

    @Test
    fun `handle logs - logs saved with index and minor index`() = runBlocking {
        val handler = createHandler(TestLogEventSubscriber(testDescriptor1(), 3))
        val block = randomBlockchainBlock()
        val logs = listOf(randomBlockchainLog(block, topic), randomBlockchainLog(block, topic))

        val savedLogs = handler.handleLogs(FullBlock(block, logs)).toCollection(mutableListOf())

        assertEquals(6, savedLogs.size)

        val fromDb = savedLogs.map { findLog(collection, it.id)!! }
        val indices = fromDb.map { it.log!!.index }
        val minorIndices = fromDb.map { it.log!!.minorLogIndex }

        assertIterableEquals(listOf(0, 0, 0, 1, 1, 1), indices)
        assertIterableEquals(listOf(0, 1, 2, 0, 1, 2), minorIndices)
    }

    @Test
    fun `handle logs - nothing on empty flow`() = runBlocking {
        val handler = createHandler(TestLogEventSubscriber(descriptor, 3))
        val block = randomBlockchainBlock()

        val savedLogs = handler.handleLogs(FullBlock(block, emptyList())).toCollection(mutableListOf())
        assertEquals(0, savedLogs.size)
    }

    @Test
    fun `before handle block - new block`() = runBlocking {
        val handler = createHandler(TestLogEventSubscriber(descriptor))

        val block = randomBlockchainBlock()
        val log1 = randomTestLogRecord(topic, block.hash)
        val log2 = randomTestLogRecord(topic, block.hash, Log.Status.REVERTED)

        testLogRepository.saveAll(collection, log1, log2)

        val deleted = handler.beforeHandleBlock(NewBlockEvent(Source.BLOCKCHAIN, block.number, block.hash))
        assertEquals(1, deleted.size)

        // This log is still alive
        assertNotNull(findLog(collection, log1.id))

        // This log should be deleted since it's status is REVERTED
        assertNull(findLog(collection, log2.id))
        assertEquals(log2.id, deleted[0].id)
        assertEquals(Log.Status.REVERTED, deleted[0].log!!.status)
    }

    @Test
    fun `before handle block - reverted block event`() = runBlocking {
        val handler = createHandler(TestLogEventSubscriber(descriptor))

        val reverted = randomBlockchainBlock()
        val event = RevertedBlockEvent(Source.BLOCKCHAIN, reverted.number, reverted.hash)

        val log1 = randomTestLogRecord(topic, reverted.hash, Log.Status.REVERTED)
        val log2 = randomTestLogRecord(topic, reverted.hash, Log.Status.CONFIRMED)
        val log3 = randomTestLogRecord(topic, randomBlockHash(), Log.Status.REVERTED)

        testLogRepository.saveAll(collection, log2, log1, log3)

        val revertedLogs = handler.beforeHandleBlock(event).toCollection(mutableListOf())
        assertEquals(2, revertedLogs.size)

        val log1FromDb = findLog(collection, log1.id)
        val log2FromDb = findLog(collection, log2.id)!!
        val log4FromDb = findLog(collection, log3.id)!!

        // This log should be deleted since it's status is REVERTED, and it should be returned
        assertNull(log1FromDb)
        assertEquals(log1.id, revertedLogs[0].id)
        assertEquals(Log.Status.REVERTED, revertedLogs[0].log!!.status)

        // Ensure we got change event for reverted log1, it should follow after deleted one
        assertEquals(log2.id, revertedLogs[1].id)
        assertFalse(revertedLogs[1].log!!.visible)
        assertEquals(Log.Status.REVERTED, revertedLogs[1].log!!.status)

        // This log is still alive, and it's status changed to REVERTED
        assertNotNull(log2FromDb)
        assertEquals(Log.Status.REVERTED, log2FromDb.log!!.status)
        assertFalse(log2FromDb.log!!.visible)

        // This log should not be changed since it related to another block
        assertNotNull(log4FromDb)
        assertEquals(log3.log!!.status, log4FromDb.log!!.status)
    }

    private fun createHandler(
        subscriber: TestLogEventSubscriber
    ): LogEventHandler<TestBlockchainBlock, TestBlockchainLog, TestLog, TestLogRecord<*>, TestDescriptor> {
        return LogEventHandler(
            subscriber,
            testLogMapper,
            testLogService
        )
    }

}
