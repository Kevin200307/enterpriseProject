package lk.ac.mrt.swiftdrop.identity_service.config;

import com.nimbusds.jose.jwk.RSAKey;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Provider interface for RS256 cryptographic keys used in JWT signing and public JWKS exposure.
 */
public interface RsaKeyProvider {

    /**
     * Returns the RSA public key.
     *
     * @return the RSAPublicKey
     */
    RSAPublicKey getPublicKey();

    /**
     * Returns the RSA private key used for signing tokens.
     *
     * @return the RSAPrivateKey
     */
    RSAPrivateKey getPrivateKey();

    /**
     * Returns the public/private key pair.
     *
     * @return the KeyPair
     */
    KeyPair getKeyPair();

    /**
     * Returns the key identifier (kid) matching the JWKS specification.
     *
     * @return the unique kid string
     */
    String getKeyId();

    /**
     * Converts the key pair into a Nimbus RSAKey object for cryptographic operations and JWKS serialization.
     *
     * @return the Nimbus RSAKey representation
     */
    RSAKey toRsaJwk();
}
