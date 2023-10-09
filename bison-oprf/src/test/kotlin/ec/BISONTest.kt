package ec

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Base64

class BISONTest {
    @Test
    fun fullProcess() {
        // end user
        val blind = BISON.GenerateBlind()
        val spId = "this is a test case".toByteArray()
        val blindedSpId = BISON.Blind(spId, blind)

        // issuer
        val userId = "Johnny Generic".toByteArray()
        val blindedPseudonym = BISON.BlindEvaluate(userId, blindedSpId)

        // service provider
        assertArrayEquals(blindedSpId, BISON.Blind(spId, blind))
        val userPseudonym =
            Base64.getUrlEncoder().withoutPadding().encodeToString(BISON.Finalize(spId, blind, blindedPseudonym))
        assertEquals(
            "kB63HMxPE7ahMVE9iValBfsNlvZGDFFeJycBZWDJMHIZ_XSFLfQgZuAGHzOWqfVy4mpiIfn6Mp3ihpuzLPvSNw",
            userPseudonym)
    }
}
