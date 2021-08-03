package com.rarible.blockchain.scanner.framework.model

import com.rarible.core.common.Identifiable

/**
 * Basic data class for blockchain block data to be stored in Mongo
 */
interface Block : Identifiable<Long> {

    override val id: Long
    val hash: String
    val timestamp: Long
    val status: Status

    enum class Status {
        PENDING,
        SUCCESS,
        ERROR
    }
}