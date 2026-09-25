package com.hhxy.huazi.memory.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

// 随机版本防止会话清空重建后旧请求误写。
public record MemorySnapshot(int schemaVersion, String revision, List<MemoryTurn> turns,
                             Instant updatedAt, String lastCommitId, String lastCommitDigest) {
    public static final int SCHEMA_VERSION = 1;

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

    public static void requireRevision(String revision) {
        try {
            if (revision == null || !UUID.fromString(revision).toString().equals(revision)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ignored) {
            throw new MemoryValidationException("记忆版本必须是规范格式的 UUID");
        }
    }

    public static MemorySnapshot empty(String revision, Instant now) {
        return new MemorySnapshot(SCHEMA_VERSION, revision, List.of(), now, "", "");
    }
}
