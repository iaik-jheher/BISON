package ec

import cafe.cryptography.curve25519.CompressedRistretto
import cafe.cryptography.curve25519.Constants
import cafe.cryptography.curve25519.RistrettoElement
import cafe.cryptography.curve25519.Scalar
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

private val ORDER = BigInteger(byteArrayOf(0xed.toByte(), 0xd3.toByte(), 0xf5.toByte(), 0x5c, 0x1a, 0x63, 0x12, 0x58,
0xd6.toByte(), 0x9c.toByte(), 0xf7.toByte(), 0xa2.toByte(), 0xde.toByte(), 0xf9.toByte(), 0xde.toByte(), 0x14,
0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x10).reversedArray())
fun Scalar.invert() : Scalar {
    val modInverse = BigInteger(this.toByteArray().reversedArray()).modInverse(ORDER)
    return Scalar.fromCanonicalBytes(I2OSP(modInverse, 32).reversedArray())
}


class QuickDirtyRistretto : OPRFCipherSpec<RistrettoElement, Scalar> {
    override val identity: RistrettoElement
        get() = RistrettoElement.IDENTITY

    override fun Hash(x: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-512").digest(x)
    }

    override fun HashToGroup(x: ByteArray): RistrettoElement {
        return RistrettoElement.fromUniformBytes(Hash("HashToGroup".toByteArray() + x))
    }

    // NB this is not uniform; none of this should be used in production, it's quick & dirty for demonstration purposes
    override fun HashToScalar(x: ByteArray): Scalar {
        return Scalar.fromBytesModOrderWide(Hash("HashToScalar".toByteArray() + x))
    }

    override fun RandomScalar(): Scalar {
        val CSPRNG = SecureRandom.getInstanceStrong()
        val x = ByteArray(32)
        while (true) {
            try {
                CSPRNG.nextBytes(x)
                return Scalar.fromCanonicalBytes(x)
            } catch (t: Throwable) {}
        }

    }

    override fun SerializeElement(a: RistrettoElement): ByteArray {
        return a.compress().toByteArray()
    }

    override fun DeserializeElement(buf: ByteArray): RistrettoElement? {
        return runCatching { CompressedRistretto(buf).decompress() }.getOrNull()
    }

    override fun multiply(a: RistrettoElement, s: Scalar) : RistrettoElement {
        return a.multiply(s)
    }

    override fun SerializeScalar(s: Scalar): ByteArray {
        return s.toByteArray()
    }

    override fun DeserializeScalar(buf: ByteArray): Scalar? {
        return runCatching { Scalar.fromCanonicalBytes(buf) }.getOrNull()
    }

    override fun ScalarInverse(s: Scalar): Scalar {
        return s.invert()
    }

}
