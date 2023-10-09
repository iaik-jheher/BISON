package ec

import java.lang.IllegalArgumentException
import java.math.BigInteger

fun I2OSP(v: BigInteger, sz: Int) : ByteArray {
    if (sz < 1)
        throw IllegalArgumentException("sz")
    if (v.signum() == -1 || v.bitLength() > sz * Byte.SIZE_BITS)
        throw IllegalArgumentException("v")

    val signed = v.toByteArray()
    return if (signed.size == sz)
        signed
    else if (signed.size < sz)
        ByteArray(sz-signed.size) + signed
    else
        signed.copyOfRange(1, signed.size)
}
fun I2OSP(v: Int, sz: Int) = I2OSP(BigInteger.valueOf(v.toLong()), sz)

open class OPRFImpl<PointType, IntegerType> (internal val G: OPRFCipherSpec<PointType, IntegerType>) : OPRF {

    override fun GenerateBlind() : ByteArray = G.SerializeScalar(G.RandomScalar())

    override fun Blind(input: ByteArray, blind: ByteArray): ByteArray {
        val unblindedInput = G.HashToGroup(input)
        if (unblindedInput == G.identity)
            throw IllegalArgumentException("input")
        val blindingValue = G.DeserializeScalar(blind) ?: throw IllegalArgumentException("blind")
        val blindedInput = G.multiply(unblindedInput, blindingValue)
        return G.SerializeElement(blindedInput)
    }

    override fun BlindEvaluate(userSecret: ByteArray, blindedElement: ByteArray): ByteArray {
        val blindedInput = G.DeserializeElement(blindedElement) ?: throw IllegalArgumentException("blindedElement")
        val blindedOutput = G.multiply(blindedInput, G.HashToScalar(userSecret))
        return G.SerializeElement(blindedOutput)
    }

    override fun Finalize(input: ByteArray, blind: ByteArray, blindedResult: ByteArray): ByteArray {
        val blindedOutput = G.DeserializeElement(blindedResult) ?: throw IllegalArgumentException("blindedResult")
        val blindElm = G.DeserializeScalar(blind) ?: throw IllegalArgumentException("blind")
        val unblindedOutput = G.multiply(blindedOutput, G.ScalarInverse(blindElm))
        val unblindedElement = G.SerializeElement(unblindedOutput)
        return G.Hash(I2OSP(input.size, 2) + input +
                        I2OSP(unblindedElement.size, 2) + unblindedElement +
                        "Finalize".toByteArray())
    }
}
