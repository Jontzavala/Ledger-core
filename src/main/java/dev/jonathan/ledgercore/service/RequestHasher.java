package dev.jonathan.ledgercore.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class RequestHasher {
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    public static String hashRequest(String description, List<LegRequest> legs) {
        StringBuilder sb = new StringBuilder();
        sb.append(description);
        for (LegRequest leg : legs) {
            sb.append("|").append(leg.accountId()).append(":").append(leg.amount());
        }
        return sha256Hex(sb.toString());
    }
}
