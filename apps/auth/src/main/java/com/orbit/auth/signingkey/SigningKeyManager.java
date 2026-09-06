package com.orbit.auth.signingkey;

import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * Loads the persistent RSA signing key from {@code auth.signing_keys}, creating
 * one on first run. See ADR-011 §3. This replaces Milestone 0's ephemeral
 * {@code generateRsaKey()} — the key now survives restarts, so issued tokens
 * stay valid.
 */
@Component
public class SigningKeyManager {

    private static final Logger log = LoggerFactory.getLogger(SigningKeyManager.class);

    private final SigningKeyRepository repository;
    private final KeySealer sealer;
    private final TransactionTemplate tx;

    public SigningKeyManager(SigningKeyRepository repository, KeySealer sealer, PlatformTransactionManager txManager) {
        this.repository = repository;
        this.sealer = sealer;
        this.tx = new TransactionTemplate(txManager);
    }

    /** Returns the active signing key, generating and persisting one if none exists. */
    public RSAKey loadOrCreateActiveKey() {
        RSAKey key = tx.execute(status -> repository.findByActiveTrue()
                .map(this::toRsaKey)
                .orElseGet(this::generateAndStore));
        log.info("Auth server signing key ready (kid={}, persistent)", key.getKeyID());
        return key;
    }

    private RSAKey generateAndStore() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) pair.getPrivate();

            String kid = UUID.randomUUID().toString();
            String publicB64 = Base64.getEncoder()
                    .encodeToString(new X509EncodedKeySpec(publicKey.getEncoded()).getEncoded());
            String sealedPrivate = sealer.seal(new PKCS8EncodedKeySpec(privateKey.getEncoded()).getEncoded());

            repository.save(new SigningKey(kid, publicB64, sealedPrivate));
            log.warn("Generated a NEW persistent RSA signing key (kid={}). "
                    + "Existing tokens (if any) are invalidated by this one-time event.", kid);

            return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(kid).build();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to generate RSA signing key", ex);
        }
    }

    private RSAKey toRsaKey(SigningKey stored) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(stored.getPublicKey())));
            RSAPrivateKey privateKey = (RSAPrivateKey) factory.generatePrivate(
                    new PKCS8EncodedKeySpec(sealer.unseal(stored.getPrivateKey())));
            return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(stored.getKeyId()).build();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to load stored RSA signing key " + stored.getKeyId(), ex);
        }
    }
}
