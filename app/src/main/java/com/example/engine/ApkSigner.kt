package com.example.engine

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ApkSigner {

    private var cachedKeyPair: KeyPair? = null

    @Synchronized
    private fun getOrCreateKeyPair(): KeyPair {
        cachedKeyPair?.let { return it }
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        cachedKeyPair = kp
        return kp
    }

    /**
     * Signs the in-memory APK entries and writes the complete signed APK to the output stream.
     */
    fun signApk(
        entries: Map<String, ByteArray>,
        outputZip: ZipOutputStream
    ) {
        val keyPair = getOrCreateKeyPair()
        val privateKey = keyPair.private
        val publicKey = keyPair.public

        // 1. Build MANIFEST.MF
        val manifestBuilder = StringBuilder()
        manifestBuilder.append("Manifest-Version: 1.0\r\n")
        manifestBuilder.append("Created-By: 1.0 (Android APK Builder Engine)\r\n\r\n")

        val entryDigests = mutableMapOf<String, String>()

        for ((name, data) in entries) {
            val digest = sha256Base64(data)
            entryDigests[name] = digest

            manifestBuilder.append("Name: $name\r\n")
            manifestBuilder.append("SHA-256-Digest: $digest\r\n\r\n")
        }

        val manifestBytes = manifestBuilder.toString().toByteArray(Charsets.UTF_8)
        val manifestDigest = sha256Base64(manifestBytes)

        // 2. Build CERT.SF
        val certSfBuilder = StringBuilder()
        certSfBuilder.append("Signature-Version: 1.0\r\n")
        certSfBuilder.append("Created-By: 1.0 (Android APK Builder Engine)\r\n")
        certSfBuilder.append("SHA-256-Digest-Manifest: $manifestDigest\r\n\r\n")

        for ((name, digest) in entryDigests) {
            certSfBuilder.append("Name: $name\r\n")
            certSfBuilder.append("SHA-256-Digest: $digest\r\n\r\n")
        }

        val certSfBytes = certSfBuilder.toString().toByteArray(Charsets.UTF_8)

        // 3. Sign CERT.SF with RSA (SHA256withRSA)
        val rsaSignature = Signature.getInstance("SHA256withRSA")
        rsaSignature.initSign(privateKey)
        rsaSignature.update(certSfBytes)
        val signatureBytes = rsaSignature.sign()

        // 4. Construct PKCS#7 block (CERT.RSA)
        val certRsaBytes = buildPkcs7Block(publicKey.encoded, signatureBytes, certSfBytes)

        // 5. Write all original entries into output APK ZIP
        for ((name, data) in entries) {
            val entry = ZipEntry(name)
            outputZip.putNextEntry(entry)
            outputZip.write(data)
            outputZip.closeEntry()
        }

        // 6. Write META-INF entries
        val manifestEntry = ZipEntry("META-INF/MANIFEST.MF")
        outputZip.putNextEntry(manifestEntry)
        outputZip.write(manifestBytes)
        outputZip.closeEntry()

        val sfEntry = ZipEntry("META-INF/CERT.SF")
        outputZip.putNextEntry(sfEntry)
        outputZip.write(certSfBytes)
        outputZip.closeEntry()

        val rsaEntry = ZipEntry("META-INF/CERT.RSA")
        outputZip.putNextEntry(rsaEntry)
        outputZip.write(certRsaBytes)
        outputZip.closeEntry()
    }

    private fun sha256Base64(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(data)
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    /**
     * Builds a minimal valid PKCS#7 SignedData structure in ASN.1 DER format.
     */
    private fun buildPkcs7Block(
        encodedPublicKey: ByteArray,
        signatureBytes: ByteArray,
        signedContent: ByteArray
    ): ByteArray {
        // ASN.1 DER tags:
        // SEQUENCE = 0x30, SET = 0x31, INTEGER = 0x02, OCTET STRING = 0x04, OID = 0x06
        // PKCS#7 SignedData OID: 1.2.840.113549.1.7.2
        val signedDataOid = byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x02)
        // Data OID: 1.2.840.113549.1.7.1
        val dataOid = byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x01)
        // SHA-256 OID: 2.16.840.1.101.3.4.2.1
        val sha256Oid = byteArrayOf(0x06, 0x09, 0x60, 0x86.toByte(), 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01)
        // rsaEncryption OID: 1.2.840.113549.1.1.1
        val rsaOid = byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x01)

        val digestAlgorithms = derSequence(listOf(derSequence(listOf(sha256Oid, byteArrayOf(0x05, 0x00))))) // NULL param
        val digestAlgorithmsSet = derSet(listOf(digestAlgorithms))

        val encapContentInfo = derSequence(listOf(dataOid))

        // SignerInfo
        val signerVersion = byteArrayOf(0x02, 0x01, 0x01) // version 1
        // IssuerAndSerialNumber
        val issuer = derSequence(listOf(derSet(listOf(derSequence(listOf(
            byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03), // commonName
            derUtf8String("Android APK Builder Testkey")
        ))))))
        val serialNumber = byteArrayOf(0x02, 0x01, 0x01)
        val issuerAndSerial = derSequence(listOf(issuer, serialNumber))

        val digestAlgorithmId = derSequence(listOf(sha256Oid, byteArrayOf(0x05, 0x00)))
        val digestEncryptionAlgorithmId = derSequence(listOf(rsaOid, byteArrayOf(0x05, 0x00)))
        val encryptedDigest = derOctetString(signatureBytes)

        val signerInfo = derSequence(listOf(
            signerVersion,
            issuerAndSerial,
            digestAlgorithmId,
            digestEncryptionAlgorithmId,
            encryptedDigest
        ))
        val signerInfos = derSet(listOf(signerInfo))

        // Certificates [0] IMPLICIT
        // Minimal self-signed certificate sequence or public key wrapper
        val cert = buildMinimalX509Cert(encodedPublicKey, issuer, serialNumber)
        val certificates = byteArrayOf(0xA0.toByte()) + derLength(cert.size) + cert

        // SignedData
        val signedData = derSequence(listOf(
            byteArrayOf(0x02, 0x01, 0x01), // version 1
            digestAlgorithmsSet,
            encapContentInfo,
            certificates,
            signerInfos
        ))

        // ContentInfo outer
        val contentInfo = derSequence(listOf(
            signedDataOid,
            byteArrayOf(0xA0.toByte()) + derLength(signedData.size) + signedData
        ))

        return contentInfo
    }

    private fun buildMinimalX509Cert(pubKeyEncoded: ByteArray, issuer: ByteArray, serial: ByteArray): ByteArray {
        val version = byteArrayOf(0xA0.toByte(), 0x03, 0x02, 0x01, 0x02) // v3
        val sigAlg = derSequence(listOf(
            byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B), // sha256WithRSAEncryption
            byteArrayOf(0x05, 0x00)
        ))
        val validity = derSequence(listOf(
            derUtcTime("240101000000Z"),
            derUtcTime("440101000000Z")
        ))
        val subject = issuer

        val tbsCertificate = derSequence(listOf(
            version,
            serial,
            sigAlg,
            issuer,
            validity,
            subject,
            pubKeyEncoded
        ))

        // Dummy self-signature block
        val sigValue = derBitString(ByteArray(256) { 0xAA.toByte() })

        return derSequence(listOf(
            tbsCertificate,
            sigAlg,
            sigValue
        ))
    }

    private fun derSequence(items: List<ByteArray>): ByteArray {
        val total = items.fold(ByteArray(0)) { acc, bytes -> acc + bytes }
        return byteArrayOf(0x30) + derLength(total.size) + total
    }

    private fun derSet(items: List<ByteArray>): ByteArray {
        val total = items.fold(ByteArray(0)) { acc, bytes -> acc + bytes }
        return byteArrayOf(0x31) + derLength(total.size) + total
    }

    private fun derOctetString(data: ByteArray): ByteArray {
        return byteArrayOf(0x04) + derLength(data.size) + data
    }

    private fun derUtf8String(str: String): ByteArray {
        val bytes = str.toByteArray(Charsets.UTF_8)
        return byteArrayOf(0x0C) + derLength(bytes.size) + bytes
    }

    private fun derUtcTime(timeStr: String): ByteArray {
        val bytes = timeStr.toByteArray(Charsets.US_ASCII)
        return byteArrayOf(0x17) + derLength(bytes.size) + bytes
    }

    private fun derBitString(data: ByteArray): ByteArray {
        return byteArrayOf(0x03) + derLength(data.size + 1) + byteArrayOf(0x00) + data
    }

    private fun derLength(len: Int): ByteArray {
        return when {
            len < 128 -> byteArrayOf(len.toByte())
            len < 256 -> byteArrayOf(0x81.toByte(), len.toByte())
            else -> byteArrayOf(0x82.toByte(), (len shr 8).toByte(), (len and 0xFF).toByte())
        }
    }
}
