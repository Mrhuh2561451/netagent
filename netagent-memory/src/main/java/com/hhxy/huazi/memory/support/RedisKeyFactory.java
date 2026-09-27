package com.hhxy.huazi.memory.support;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 用环境前缀和作用域摘要隔离键空间；摘要隐藏标识原文，但不代替鉴权或加密。 */
public final class RedisKeyFactory {
    private final String prefix;

    /** 环境名由服务端确定；前缀中的 v1 是键命名版本，不是会话并发版本。 */
    public RedisKeyFactory(String environment) {
        requireSegment(environment);
        this.prefix = "netagent:" + environment + ":v1:";
    }

    /** 按租户、用户、智能体、会话隔离记忆；花括号中的摘要是 Redis 集群哈希标签。 */
    public String memoryKey(String tenantId, String userId, String agentId, String conversationId) {
        return prefix + "memory:{" + digest(tenantId, userId, agentId, conversationId) + "}:state";
    }

    /** 区域名保持可读，租户、用户及业务标识只以摘要进入键名，避免直接暴露身份。 */
    public String cacheKey(String region, String tenantId, String userId, String businessId) {
        requireSegment(region);
        return prefix + "cache:" + region + ":{" + digest(tenantId, userId, businessId) + "}:value";
    }

    /** 与对应缓存值使用相同作用域摘要和哈希标签，但独立前缀避免锁与值相互覆盖。 */
    public String cacheLockKey(String region, String tenantId, String userId, String businessId) {
        requireSegment(region);
        return prefix + "lock:cache:" + region + ":{" + digest(tenantId, userId, businessId) + "}";
    }

    /** 对有序且带长度分界的 UTF-8 输入计算 SHA-256；分段顺序属于键和内容摘要协议。 */
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

    /** 禁止命名空间片段包含冒号或花括号，防止改变键的分层结构及集群哈希标签。 */
    private static void requireSegment(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("Redis 命名空间片段无效");
        }
    }
}
