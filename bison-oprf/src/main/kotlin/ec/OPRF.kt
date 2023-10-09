package ec

interface OPRF {
    /**
     * Generates a cryptographically secure, uniformly random, blinding value
     * @return The serialized blinding value
     */
    fun GenerateBlind() : ByteArray

    /**
     * Blinds an arbitrary input using the specified blinding value, then returns the blinded input
     * @param input Arbitrary byte string
     * @param blind Serialized blinding value obtained from [GenerateBlind]
     * @return The blinded input in serialized form, ready for transmission to the server
     */
    fun Blind(input: ByteArray, blind: ByteArray): ByteArray

    /**
     * Evaluates the blinded input and user-specific server secret to obtain the blinded result value
     * @param serverUserSecret Arbitrary byte string
     * @param blindedInput Serialized blinded input, as obtained from [Blind]
     */
    fun BlindEvaluate(userSecret: ByteArray, blindedElement: ByteArray): ByteArray

    /**
     * Finalizes the evaluation, removing the blinding from the result
     * @param input The original unblinded input
     * @param blind The blinding value used, in serialized form (as returned by [GenerateBlind])
     * @param blindedResult The blinded result obtained from the server (as obtained from [BlindEvaluate])
     */
    fun Finalize(input: ByteArray, blind: ByteArray, blindedResult: ByteArray): ByteArray
}
