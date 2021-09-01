package com.rarible.blockchainscanner.flow

import com.nftco.flow.sdk.crypto.Crypto
import org.testcontainers.shaded.org.apache.commons.lang.RandomStringUtils
import java.util.*

fun main() {
    val pair = Crypto.generateKeyPair()
    println(RandomStringUtils.random(16, "0123456789ABCDEF").lowercase(Locale.ENGLISH))
    println(pair.private.hex)
    println(pair.public.hex)
}
