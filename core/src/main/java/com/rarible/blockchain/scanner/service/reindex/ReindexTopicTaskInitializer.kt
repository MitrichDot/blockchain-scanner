package com.rarible.blockchain.scanner.service.reindex

import com.rarible.blockchain.scanner.subscriber.LogEventSubscriber
import com.rarible.core.task.TaskService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@ExperimentalCoroutinesApi
@Component
class ReindexTopicTaskInitializer(
    private val taskService: TaskService,
    private val descriptors: List<LogEventSubscriber<*, *, *>>,
    @Value("\${ethereumBlockReindexEnabled:true}") private val reindexEnabled: Boolean
) {
    @Scheduled(initialDelayString = "\${taskInitializerDelay:60000}", fixedDelay = Long.MAX_VALUE)
    fun initialize() {
        if (reindexEnabled) {
            descriptors
                .map { it.topic }
                .distinct()
                .forEach {
                    taskService.runTask(ReindexTopicTaskHandler.TOPIC, it)
                }
        }
    }
}