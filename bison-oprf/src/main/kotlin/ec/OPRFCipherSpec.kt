package ec

import java.math.BigInteger

interface OPRFCipherSpec<PointType, IntegerType> {

    val identity : PointType
    fun Hash(x: ByteArray): ByteArray
    fun HashToGroup(x: ByteArray) : PointType
    fun HashToScalar(x: ByteArray) : IntegerType
    fun RandomScalar() : IntegerType
    fun ScalarInverse(s: IntegerType): IntegerType

    fun SerializeElement(a: PointType): ByteArray
    fun DeserializeElement(buf: ByteArray): PointType?

    fun SerializeScalar(s: IntegerType): ByteArray
    fun DeserializeScalar(buf: ByteArray): IntegerType?

    fun multiply(a: PointType, s: IntegerType): PointType
}