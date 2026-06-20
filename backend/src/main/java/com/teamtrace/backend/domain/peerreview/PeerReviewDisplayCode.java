package com.teamtrace.backend.domain.peerreview;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 互评展示代号：对同一 (taskId, groupId, userId) 稳定、非顺序字母表（避免 A/B/C/D 易推测），
 * 不暴露真实用户 ID。密钥须使用服务端机密（如 {@code jwt.secret}）注入后传入。
 */
public final class PeerReviewDisplayCode {

    private static final char[] ALPHANUM =
            "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz".toCharArray();

    private PeerReviewDisplayCode() {}

    /**
     * @param appSecret 与 JWT 等同级机密，勿硬编码
     */
    public static String forMember(long taskId, long groupId, long userId, String appSecret) {
        if (appSecret == null || appSecret.isBlank()) {
            throw new IllegalArgumentException("appSecret must not be blank");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = "peer-alias:v1:" + taskId + ":" + groupId + ":" + userId;
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return toCode(raw, 6);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String toCode(byte[] raw, int len) {
        StringBuilder sb = new StringBuilder(len);
        int x = 0;
        for (int i = 0; i < len; i++) {
            x = (x + (raw[i] & 0xff)) % ALPHANUM.length;
            sb.append(ALPHANUM[x]);
        }
        return sb.toString();
    }
}
