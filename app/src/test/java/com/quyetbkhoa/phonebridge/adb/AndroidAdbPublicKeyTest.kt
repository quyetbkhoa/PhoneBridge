package com.quyetbkhoa.phonebridge.adb

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidAdbPublicKeyTest {
    @Test
    fun `encodes AOSP 524-byte RSA public key format`() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val encoded = AndroidAdbPublicKey.encode(pair.public as RSAPublicKey)
        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(AndroidAdbPublicKey.ENCODED_SIZE, encoded.size)
        assertEquals(64, buffer.int)
        buffer.position(encoded.size - 4)
        assertEquals(65537, buffer.int)
    }

    @Test
    fun `AUTH signature is valid PKCS1 SHA1 signature of token digest`() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val message = "phonebridge-auth".toByteArray()
        val token = MessageDigest.getInstance("SHA-1").digest(message)
        val signature = AndroidAdbRsa.signToken(pair.private, token)
        val verifier = Signature.getInstance("SHA1withRSA").apply {
            initVerify(pair.public)
            update(message)
        }

        org.junit.Assert.assertTrue(verifier.verify(signature))
    }
}
