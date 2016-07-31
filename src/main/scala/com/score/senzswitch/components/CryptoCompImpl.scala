package com.score.senzswitch.components

import java.security._
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}

import com.score.senzswitch.config.Configuration
import com.score.senzswitch.protocols.{Senz, SenzType, SwitchKey}
import sun.misc.{BASE64Decoder, BASE64Encoder}

/**
 * Created by eranga on 7/31/16.
 */
trait CryptoCompImpl extends CryptoComp {

  this: KeyStoreComp with Configuration =>

  val crypto = new CryptoImpl()

  class CryptoImpl extends Crypto {

    override def initKeys() = {
      // only save if keys not exists in db
      keyStore.findSwitchKey match {
        case SwitchKey(Some(pubKey), Some(privateKye)) =>
          // already existing keys
        case _ =>
          // no keys
          // generate public private key pair
          val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
          keyPairGenerator.initialize(1024, new SecureRandom)
          val keyPair: KeyPair = keyPairGenerator.generateKeyPair

          // save key
          val pubKey = new BASE64Encoder().encode(keyPair.getPublic.getEncoded)
          val privateKey = new BASE64Encoder().encode(keyPair.getPrivate.getEncoded)
          keyStore.saveSwitchKey(SwitchKey(Some(pubKey), Some(privateKey)))
      }
    }

    override def sing(payload: String) = {
      // find private key
      val switchKey = keyStore.findSwitchKey
      val keyFactory: KeyFactory = KeyFactory.getInstance("RSA")
      val privateKeySpec: PKCS8EncodedKeySpec = new PKCS8EncodedKeySpec(new BASE64Decoder().decodeBuffer(switchKey.privateKey.get))
      val privateKey: PrivateKey = keyFactory.generatePrivate(privateKeySpec)

      // sign the payload
      val signature: Signature = Signature.getInstance("SHA256withRSA")
      signature.initSign(privateKey)
      signature.update(payload.replaceAll(" ", "").getBytes)

      // signature as Base64 encoded string
      val encodedSignature = new BASE64Encoder().encode(signature.sign).replaceAll("\n", "").replaceAll("\r", "")
      s"$payload $encodedSignature"
    }

    override def verify(payload: String, senz: Senz) = {
      def getSenzieKey(senz: Senz): Array[Byte] = {
        senz match {
          case Senz(SenzType.SHARE, _, `switchName`, attr, _) =>
            new BASE64Decoder().decodeBuffer(attr.get("#pubkey").get)
          case _ =>
            // get public key of senzie
            new BASE64Decoder().decodeBuffer(keyStore.findSenzieKey(senz.sender).get.key)
        }
      }

      // signed payload(with out signature)
      val signedPayload = payload.replace(senz.signature.get, "").trim

      // get public key of senzie
      val keyFactory: KeyFactory = KeyFactory.getInstance("RSA")
      val publicKeySpec: X509EncodedKeySpec = new X509EncodedKeySpec(getSenzieKey(senz))
      val publicKey: PublicKey = keyFactory.generatePublic(publicKeySpec)

      // verify signature
      val signature = Signature.getInstance("SHA256withRSA")
      signature.initVerify(publicKey)
      signature.update(signedPayload.getBytes)

      // decode(BASE64) signed payload and verify signature
      signature.verify(new BASE64Decoder().decodeBuffer(senz.signature.get))
    }

    override def decrypt() = {

    }

    override def encrypt() = {

    }

  }

}