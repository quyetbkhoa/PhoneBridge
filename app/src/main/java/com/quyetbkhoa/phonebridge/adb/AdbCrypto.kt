package com.quyetbkhoa.phonebridge.adb

import android.content.Context
import android.util.Base64
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec

class AdbCrypto(private val context: Context) {
    @Volatile
    private var cachedKeyPair: KeyPair? = null

    @Synchronized
    fun getOrCreateKeyPair(): KeyPair {
        cachedKeyPair?.let { return it }
        val directory = File(context.filesDir, "adb").apply { mkdirs() }
        val keyFile = File(directory, "adbkey.pk8")
        val pair = if (keyFile.exists()) loadKeyPair(keyFile) else generateKeyPair().also {
            val temporary = File(directory, "adbkey.pk8.tmp")
            temporary.delete()
            temporary.writeBytes(it.private.encoded)
            check(temporary.renameTo(keyFile)) { "Unable to persist ADB private key" }
        }
        cachedKeyPair = pair
        return pair
    }

    fun signToken(token: ByteArray): ByteArray {
        return AndroidAdbRsa.signToken(getOrCreateKeyPair().private, token)
    }

    fun publicKeyPayload(): ByteArray {
        val publicKey = getOrCreateKeyPair().public as RSAPublicKey
        val encoded = AndroidAdbPublicKey.encode(publicKey)
        val base64 = Base64.encodeToString(encoded, Base64.NO_WRAP)
        return "$base64 phonebridge@android\u0000".toByteArray(Charsets.UTF_8)
    }

    private fun generateKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").run {
        initialize(2048)
        generateKeyPair()
    }

    private fun loadKeyPair(file: File): KeyPair {
        val factory = KeyFactory.getInstance("RSA")
        val privateKey: PrivateKey = factory.generatePrivate(PKCS8EncodedKeySpec(file.readBytes()))
        val rsaPrivate = privateKey as RSAPrivateCrtKey
        val publicKey = factory.generatePublic(RSAPublicKeySpec(rsaPrivate.modulus, rsaPrivate.publicExponent))
        return KeyPair(publicKey, privateKey)
    }

}

object AndroidAdbRsa {
    fun signToken(privateKey: PrivateKey, token: ByteArray): ByteArray {
        require(token.size == 20) { "ADB AUTH token must be a SHA-1 digest" }
        val signer = Signature.getInstance("NONEwithRSA")
        signer.initSign(privateKey)
        signer.update(SHA1_DIGEST_INFO_PREFIX)
        signer.update(token)
        return signer.sign()
    }

    private val SHA1_DIGEST_INFO_PREFIX = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02,
            0x1a, 0x05, 0x00, 0x04, 0x14
    )
}

object AndroidAdbPublicKey {
    const val ENCODED_SIZE = 524
    private const val MODULUS_BYTES = 256

    fun encode(key: RSAPublicKey): ByteArray {
        require(key.modulus.bitLength() <= 2048) { "ADB requires an RSA-2048 key" }
        val two32 = BigInteger.ONE.shiftLeft(32)
        val n0inv = two32.subtract(key.modulus.mod(two32).modInverse(two32)).mod(two32)
        val rr = BigInteger.ONE.shiftLeft(2048).modPow(BigInteger.valueOf(2), key.modulus)
        return ByteBuffer.allocate(ENCODED_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(64)
            .putInt(n0inv.toLong().toInt())
            .put(toFixedLittleEndian(key.modulus))
            .put(toFixedLittleEndian(rr))
            .putInt(key.publicExponent.toInt())
            .array()
    }

    private fun toFixedLittleEndian(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        val unsigned = if (raw.size > MODULUS_BYTES) raw.copyOfRange(raw.size - MODULUS_BYTES, raw.size) else raw
        val bigEndian = ByteArray(MODULUS_BYTES)
        unsigned.copyInto(bigEndian, MODULUS_BYTES - unsigned.size)
        return bigEndian.reversedArray()
    }
}
