package com.smartfridge.service;

import com.smartfridge.config.FirmwareProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/** HMAC-SHA256 over "version:sha256" so the MCU can authenticate OTA commands with its shared secret. */
@Component
@RequiredArgsConstructor
public class FirmwareSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private final FirmwareProperties properties;

    public String sign(String version, String checksumSha256) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(properties.getSigningSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal((version + ":" + checksumSha256).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to compute firmware signature", ex);
        }
    }
}
