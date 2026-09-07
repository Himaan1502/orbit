package com.orbit.auth.signingkey;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the persistent RSA signing keys in {@code auth.signing_keys} (ADR-011 §3).
 *
 * <p>Restarts never rotate the key — the active key is loaded from the database.
 * Rotation is <em>overlapping</em>: a retired key's public half stays in the
 * published JWKS until every token it could have signed has expired
 * ({@code overlap} = the refresh-token TTL), so a rotation does not invalidate
 * outstanding tokens. Rotation is operator-triggered
 * ({@code AUTH_ROTATE_SIGNING_KEY=true} then restart) and also callable directly
 * ({@link #rotate()}); the in-memory JWK set is refreshed live, no downtime.
 */
@Component
public class SigningKeyManager {

    private static final Logger log = LoggerFactory.getLogger(SigningKeyManager.class);

    private final SigningKeyRepository repository;
    private final KeySealer sealer;
    private final TransactionTemplate tx;
    private final boolean rotateOnStartup;
    private final Duration overlap;

    /** Current published set (active + still-valid retired keys), refreshed on rotate. */
    private final AtomicReference<JWKSet> jwkSet = new AtomicReference<>(new JWKSet());
    private volatile String activeKeyId;

    public SigningKeyManager(
            SigningKeyRepository repository,
            KeySealer sealer,
            PlatformTransactionManager txManager,
            @Value("${orbit.auth.rotate-signing-key:false}") boolean rotateOnStartup,
            @Value("${orbit.auth.signing-key-overlap:P7D}") Duration overlap) {
        this.repository = repository;
        this.sealer = sealer;
        this.tx = new TransactionTemplate(txManager);
        this.rotateOnStartup = rotateOnStartup;
        this.overlap = overlap;
    }

    @PostConstruct
    void init() {
        reload();
        if (rotateOnStartup) {
            log.warn("AUTH_ROTATE_SIGNING_KEY is set — rotating the signing key now. "
                    + "Unset it before the next restart to avoid rotating again.");
            rotate();
        }
    }

    /** The kid of the key that signs new tokens. */
    public String activeKeyId() {
        return activeKeyId;
    }

    /** The live JWK set — active key plus retired keys still within the overlap window. */
    public JWKSet currentJwkSet() {
        return jwkSet.get();
    }

    /** Retire the active key and promote a fresh one. Old tokens keep validating during the overlap. */
    public void rotate() {
        tx.executeWithoutResult(status -> {
            // Flush the retire UPDATE before inserting the new active row — the
            // `one active key` partial unique index is checked per statement.
            repository.findByActiveTrue().ifPresent(current -> {
                current.retire();
                repository.saveAndFlush(current);
            });
            repository.save(newKey());
        });
        reload();
        log.info("Signing key rotated. New active kid={}, {} key(s) published.",
                activeKeyId, currentJwkSet().getKeys().size());
    }

    /** Rebuild the in-memory JWK set from the database; create the first key if the table is empty. */
    public void reload() {
        Instant cutoff = Instant.now().minus(overlap);
        List<RSAKey> keys = tx.execute(status -> {
            repository.findByActiveFalseAndRotatedAtBefore(cutoff).forEach(repository::delete);

            List<SigningKey> stored =
                    repository.findByActiveTrueOrRotatedAtAfterOrderByActiveDescCreatedAtDesc(cutoff);
            if (stored.stream().noneMatch(SigningKey::isActive)) {
                stored = new ArrayList<>(stored);
                stored.add(0, repository.save(newKey()));
            }
            return stored.stream().map(this::toRsaKey).toList();
        });

        this.activeKeyId = repository.findByActiveTrue().map(SigningKey::getKeyId)
                .orElse(keys.isEmpty() ? null : keys.get(0).getKeyID());
        this.jwkSet.set(new JWKSet(new ArrayList<>(keys)));
        log.info("Signing keys loaded: active kid={}, {} published", activeKeyId, keys.size());
    }

    private SigningKey newKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) pair.getPrivate();

            String kid = java.util.UUID.randomUUID().toString();
            String publicB64 = Base64.getEncoder()
                    .encodeToString(new X509EncodedKeySpec(publicKey.getEncoded()).getEncoded());
            String sealedPrivate = sealer.seal(new PKCS8EncodedKeySpec(privateKey.getEncoded()).getEncoded());
            return new SigningKey(kid, publicB64, sealedPrivate);
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
