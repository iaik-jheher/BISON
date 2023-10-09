package ec

import cafe.cryptography.curve25519.RistrettoElement
import cafe.cryptography.curve25519.Scalar

object BISON : OPRFImpl<RistrettoElement, Scalar>(QuickDirtyRistretto()) {
}