package com.hhxy.huazi.memory.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** schemaVersion 是持久化格式版本，revision 是乐观并发令牌；最后提交元数据只绑定当前窗口末轮，不代表完整提交历史。 */
// 随机版本防止会话清空重建后旧请求误写。
public record MemorySnapshot(int schemaVersion, String revision, List<MemoryTurn> turns,
                             Instant updatedAt, String lastCommitId, String lastCommitDigest) {
    public static final int SCHEMA_VERSION = 1;

    // 校验请求唯一性及元数据的格式、末轮关联；摘要是否匹配轮次内容由策略校验，此处不重新计算。
    public MemorySnapshot {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new MemoryValidationException("不支持的记忆数据结构版本");
        }
        requireRevision(revision);
        if (turns == null || turns.stream().anyMatch(java.util.Objects::isNull) || updatedAt == null
                || lastCommitId == null || lastCommitDigest == null) {
            throw new MemoryValidationException("快照字段不能为空");
        }
        turns = List.copyOf(turns);
        var ids = new HashSet<String>();
        for (MemoryTurn turn : turns) {
            if (!ids.add(turn.requestId())) {
                throw new MemoryValidationException("快照包含重复请求");
            }
        }
        if (turns.isEmpty()) {
            if (!lastCommitId.isEmpty() || !lastCommitDigest.isEmpty()) {
                throw new MemoryValidationException("空快照不能保留提交元数据");
            }
        } else if (!turns.get(turns.size() - 1).requestId().equals(lastCommitId)
                || !lastCommitDigest.matches("[0-9a-f]{64}")) {
            throw new MemoryValidationException("快照提交元数据无效");
        }
    }

    // 仅校验标准 UUID 字符串，不生成或修正版本；规范化后的文本必须与传入值完全一致。
    public static void requireRevision(String revision) {
        try {
            if (revision == null || !UUID.fromString(revision).toString().equals(revision)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ignored) {
            throw new MemoryValidationException("记忆版本必须是规范格式的 UUID");
        }
    }

    // 新 revision 必须由上层生成并传入，尤其清空重建时不能复用旧并发令牌；此工厂只构造空窗口。
    public static MemorySnapshot empty(String revision, Instant now) {
        return new MemorySnapshot(SCHEMA_VERSION, revision, List.of(), now, "", "");
    }
}
