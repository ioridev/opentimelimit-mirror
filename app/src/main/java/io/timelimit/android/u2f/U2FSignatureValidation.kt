/*
 * TimeLimit Copyright <C> 2019 - 2022 Jonas Lochmann
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.timelimit.android.u2f

import io.timelimit.android.u2f.protocol.U2FResponse
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.CryptoException
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECParameterSpec
import org.bouncycastle.jce.spec.ECPublicKeySpec
import java.security.KeyFactory
import java.security.Security
import java.security.Signature


object U2FSignatureValidation {
    init {
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
    }

    private val curve = SECNamedCurves.getByName("secp256r1")
    private val ecParamSpec = ECParameterSpec(curve.getCurve(), curve.getG(), curve.getN(),  curve.getH())

    // based on https://github.com/Yubico/java-u2flib-server/blob/dd44d3cdce4eeaeb517f2acd1fd520d5a42ce752/u2flib-server-core/src/main/java/com/yubico/u2f/crypto/BouncyCastleCrypto.java
    fun validate(
        applicationId: ByteArray,
        challenge: ByteArray,
        response: U2FResponse.Login,
        publicKey: ByteArray
    ): Boolean {
        try {
            val signedData =
                applicationId + byteArrayOf(
                    response.flags,
                    response.counter.shr(24).toUByte().toByte(),
                    response.counter.shr(16).toUByte().toByte(),
                    response.counter.shr(8).toUByte().toByte(),
                    response.counter.toUByte().toByte(),
                ) + challenge

            if (publicKey.size != 65 || publicKey[0] != 4.toByte()) return false

            val point = curve.getCurve().decodePoint(publicKey)

            val decodedPublicKey = KeyFactory.getInstance("EC", "BC").generatePublic(
                ECPublicKeySpec(point, ecParamSpec)
            )

            val verifier = Signature.getInstance("SHA256withECDSA", "BC")

            verifier.initVerify(decodedPublicKey)

            verifier.update(signedData)

            return verifier.verify(response.signature)
        } catch (ex: CryptoException) {
            return false
        } catch (ex: IllegalArgumentException) {
            return false
        }
    }
}