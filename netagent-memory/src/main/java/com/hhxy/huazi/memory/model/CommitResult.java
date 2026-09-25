package com.hhxy.huazi.memory.model;

public record CommitResult(Status status, String revision) {
    // OUTCOME_UNKNOWN 不代表失败，不可盲目重试。
    public enum Status { COMMITTED, ALREADY_COMMITTED, CONFLICT, SESSION_EXPIRED, OUTCOME_UNKNOWN }

    public CommitResult {
        if (status == null) {
            throw new MemoryValidationException("提交状态不能为空");
        }
        if (status == Status.COMMITTED || status == Status.ALREADY_COMMITTED) {
            MemorySnapshot.requireRevision(revision);
        } else if (revision != null) {
            throw new MemoryValidationException("只有已确认的提交才能携带版本");
        }
    }

    public static CommitResult of(Status status) {
        return new CommitResult(status, null);
    }
}
