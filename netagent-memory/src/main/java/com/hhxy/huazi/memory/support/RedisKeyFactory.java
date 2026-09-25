package com.hhxy.huazi.memory.support;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RedisKeyFactory {
    private final String prefix;

    public RedisKeyFactory(String environment) {
        requireSegment(environment);
        this.prefix = "netagent:" + environment + ":v1:";
    }

    // 作用域只以摘要进入键名；花括号内的哈希标签用于 Redis 集群槽位定位。
    public String memoryKey(String tenantId, String userId, String agentId, String conversationId) {
        return prefix + "memory:{" + digest(tenantId, userId, agentId, conversationId) + "}:state";
    }

    public String cacheKey(String region, String tenantId, String userId, String businessId) {
        requireSegment(region);
        return prefix + "cache:" + region + ":{" + digest(tenantId, userId, businessId) + "}:value";
    }

    public String cacheLockKey(String region, String tenantId, String userId, String businessId) {
        requireSegment(region);
        return prefix + "lock:cache:" + region + ":{" + digest(tenantId, userId, businessId) + "}";
    }

    public static String digest(String... parts) {
        if (parts == null || parts.length == 0) {
            throw new IllegalArgumentException("键作用域不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                if (part == null || part.isBlank()) {
                    throw new IllegalArgumentException("键作用域不能包含空白标识符");
                }
                ByteBuffer bytes = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(part));
                // 为每段加入字节长度，避免不同分段的标识符拼接后得到相同摘要输入。
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.remaining()).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("键标识符必须包含有效的 Unicode 字符", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 摘要算法不可用", e);
        }
    }

    private static void requireSegment(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("Redis 命名空间片段无效");
        }
    }
}
