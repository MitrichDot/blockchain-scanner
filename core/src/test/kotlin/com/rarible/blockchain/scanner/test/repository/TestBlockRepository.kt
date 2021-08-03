package com.rarible.blockchain.scanner.test.repository

import com.rarible.blockchain.scanner.framework.model.Block
import com.rarible.blockchain.scanner.test.model.TestBlock
import com.rarible.core.mongo.repository.AbstractMongoRepository
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoOperations
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.findOne
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class TestBlockRepository(
    mongo: ReactiveMongoOperations
) : AbstractMongoRepository<TestBlock, Long>(mongo, TestBlock::class.java) {

    fun findByStatus(status: Block.Status): Flux<TestBlock> =
        mongo.find(Query(TestBlock::status isEqualTo status))

    fun getLastBlock(): Mono<Long> {
        return mongo.find(Query().with(Sort.by(Sort.Direction.DESC, "_id")).limit(1), TestBlock::class.java)
            .next()
            .map { it.id }
    }

    fun updateBlockStatus(number: Long, status: Block.Status): Mono<Void> {
        return mongo.updateFirst(
            Query(TestBlock::id isEqualTo number),
            Update().set(TestBlock::status.name, status),
            TestBlock::class.java
        ).then()
    }

    fun findFirstByIdAsc(): Mono<TestBlock> =
        mongo.findOne(Query().with(Sort.by(Sort.Direction.ASC, TestBlock::id.name)))

    fun findFirstByIdDesc(): Mono<TestBlock> =
        mongo.findOne(Query().with(Sort.by(Sort.Direction.DESC, TestBlock::id.name)))
}