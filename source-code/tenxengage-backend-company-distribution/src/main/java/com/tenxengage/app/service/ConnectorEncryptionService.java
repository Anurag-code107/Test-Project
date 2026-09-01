package com.tenxengage.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
public class ConnectorEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(ConnectorEncryptionService.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    /**
     * The development fallback. Present in this file, therefore known to anyone who can read the
     * repository — which is why it is refused outside the profiles that genuinely do not need a real key.
     */
    private static final String DEV_DEFAULT_KEY = "0123456789abcdef0123456789abcdef";

    /** Profiles that may run without a real key: no production data, no real money. */
    private static final Set<String> KEYLESS_PROFILES = Set.of("local", "localtest", "test");

    private final SecretKeySpec keySpec;
    private final ObjectMapper objectMapper;

    public ConnectorEncryptionService(
            @Value("${app.connector.encryption-key:0123456789abcdef0123456789abcdef}") String encryptionKey,
            ObjectMapper objectMapper,
            Environment environment) {

        // Refuse rather than warn. This key wraps the credentials that authorise paying a partner company's
        // sellers out of that company's wallet; a default key is not weaker encryption, it is none. No
        // active profile counts as "not local" on purpose — a deployment that forgot its profile is exactly
        // the one that also forgot its key.
        boolean keylessProfile = Arrays.stream(environment.getActiveProfiles()).anyMatch(KEYLESS_PROFILES::contains);
        if (!keylessProfile
                && (encryptionKey == null || encryptionKey.isBlank() || DEV_DEFAULT_KEY.equals(encryptionKey))) {
            throw new IllegalStateException(
                    "app.connector.encryption-key must be set to a real 16, 24, or 32 byte key outside local. "
                            + "Refusing to start with the development default.");
        }

        byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            log.warn("Connector encryption key is {} bytes; must be exactly 16, 24, or 32 bytes for AES. "
                    + "Padding to 32 bytes — configure a proper key in production.", keyBytes.length);
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            keyBytes = padded;
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
        this.objectMapper = objectMapper;
    }

    public String encrypt(Map<String, String> config) {
        try {
            String json = objectMapper.writeValueAsString(config);
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(json.getBytes());

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt connector config", e);
        }
    }

    public Map<String, String> decrypt(String encrypted) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] decrypted = cipher.doFinal(ciphertext);

            return objectMapper.readValue(decrypted, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to decrypt connector config", e);
            return Map.of();
        }
    }

    public Map<String, String> maskConfig(Map<String, String> config) {
        Map<String, String> masked = new HashMap<>();
        for (Map.Entry<String, String> entry : config.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            String keyLower = key.toLowerCase();
            if (keyLower.contains("secret") || keyLower.contains("password")
                    || keyLower.contains("key") || keyLower.contains("token")
                    || keyLower.contains("credential") || keyLower.contains("auth")) {
                masked.put(key, "••••••••");
            } else {
                masked.put(key, value);
            }
        }
        return masked;
    }
}
