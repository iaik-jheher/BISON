package ec

import cafe.cryptography.curve25519.Scalar
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

class QuickDirtyRistrettoTest {
    val G = QuickDirtyRistretto()

    @Test
    fun invertTest() {
        val scalar = G.RandomScalar()
        val inverse = scalar.invert()
        assertEquals(Scalar.ONE, scalar.multiply(inverse))
    }

    @Test
    fun blindingTest() {
        val randomPoint = G.HashToGroup(Random.nextBytes(64))
        val randomSecret = G.HashToScalar(Random.nextBytes(64))
        val blind = G.RandomScalar()
        val inverseBlind = blind.invert()

        val blindedPoint = randomPoint.multiply(blind)
        val blindedResult = blindedPoint.multiply(randomSecret)
        val unblindedResult = blindedResult.multiply(inverseBlind)

        val directResult = randomPoint.multiply(randomSecret)

        assertEquals(directResult, unblindedResult)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun interoperabilityTestWithJavascript() {
        val expectedPoint = Base64.UrlSafe.decode("9KJmTIiX8WwoveswNdCbJNwl3otjJdRwHRq1gxTXghU")
        val actualPoint = G.HashToGroup("example.org".toByteArray())
        assertArrayEquals(expectedPoint, G.SerializeElement(actualPoint))
    }

}

