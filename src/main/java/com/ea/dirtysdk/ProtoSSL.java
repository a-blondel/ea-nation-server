package com.ea.dirtysdk;

import com.ea.utils.Props;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.ea.utils.ByteUtils.MD5_CIPHER_SIGNATURE;
import static com.ea.utils.ByteUtils.findBytePattern;

@RequiredArgsConstructor
@Component
public class ProtoSSL {

    private final Props props;

    private static final String RDN_REGEX = ",(?=\\s[A-Z]+=)";
    private static final Map<String, ASN1ObjectIdentifier> RDN_NAME_TO_BC_STYLE = Map.of(
            "C", BCStyle.C,
            "ST", BCStyle.ST,
            "L", BCStyle.L,
            "O", BCStyle.O,
            "OU", BCStyle.OU,
            "CN", BCStyle.CN
    );

    private final ConcurrentHashMap<String, Pair<KeyPair, Certificate>> certCache = new ConcurrentHashMap<>();

    public Pair<KeyPair, Certificate> getEaCert(String subject, String issuer) throws Exception {
        String cacheKey = subject + "|" + issuer;
        if (certCache.containsKey(cacheKey)) {
            return certCache.get(cacheKey);
        }

        Pair<KeyPair, Certificate> creds = generateVulnerableCert(subject, issuer);
        certCache.put(cacheKey, creds);

        return creds;
    }

    private Pair<KeyPair, Certificate> generateVulnerableCert(String subject, String issuer) throws Exception {
        KeyPair cKeyPair = generateKeyPair();
        Certificate cCertificate = generateCertificate(subject, cKeyPair, cKeyPair.getPrivate(), issuer);
        Certificate patchedCCertificate = patchCertificateSignaturePattern(cCertificate);
        return Pair.of(cKeyPair, patchedCCertificate);
    }

    private KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024, new SecureRandom());
        return keyPairGenerator.generateKeyPair();
    }

    private Certificate generateCertificate(String subjectName, KeyPair subjectKeyPair, PrivateKey issuerPrivKey, String issuer) throws Exception {
        X500Name subjectDn = buildX500Name(subjectName);
        X500Name issuerDn = issuer == null ? subjectDn : buildX500Name(issuer);

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

        // The game doesn't perform any validation on the certificate's validity period
        // It's safer to set the validity period to a date in the past to avoid the 2050 problem :
        // X509 certificate validity period is in UTCTime format until 2049, then starting from 2050 it's in GeneralizedTime format
        // Which isn't handled by the game
        Date validity = formatter.parse("2011-08-11");

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuerDn,
                BigInteger.valueOf(new SecureRandom().nextInt()),
                validity,
                validity,
                subjectDn,
                subjectKeyPair.getPublic());

        ContentSigner signer = new JcaContentSignerBuilder(props.getSslCertificateCipherAlgorithm()).setProvider(new BouncyCastleProvider()).build(issuerPrivKey);

        X509Certificate certificate = new JcaX509CertificateConverter().setProvider(new BouncyCastleProvider()).getCertificate(certBuilder.build(signer));
        return certificate;
    }

    private X500Name buildX500Name(String dn) {
        X500NameBuilder builder = new X500NameBuilder(BCStyle.INSTANCE);
        String[] rdns = dn.split(RDN_REGEX, -1);
        for (String rdn : rdns) {
            String[] rdnPair = rdn.split("=", 2);
            String rdnName = rdnPair[0].trim().toUpperCase();
            String rdnValue = rdnPair[1].trim();
            if (RDN_NAME_TO_BC_STYLE.containsKey(rdnName)) {
                builder.addRDN(RDN_NAME_TO_BC_STYLE.get(rdnName), new DERPrintableString(rdnValue));
            }
        }
        return builder.build();
    }

    private static X509Certificate patchCertificateSignaturePattern(Certificate cCertificate) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        byte[] certDer = cCertificate.getEncoded();

        int signature1Offset = findBytePattern(certDer, MD5_CIPHER_SIGNATURE, 0);
        int signature2Offset = findBytePattern(certDer, MD5_CIPHER_SIGNATURE, signature1Offset + MD5_CIPHER_SIGNATURE.length);

        if (signature1Offset == -1 || signature2Offset == -1) {
            throw new RuntimeException("Failed to find valid signature for patching!");
        }

        // Patch the second signature to TLS_NULL_WITH_NULL_NULL
        certDer[signature2Offset + 8] = 0x01;

        X509CertificateHolder holder = new X509CertificateHolder(certDer);
        JcaX509CertificateConverter certConverter = new JcaX509CertificateConverter().setProvider(new BouncyCastleProvider());
        return certConverter.getCertificate(holder);
    }

}
