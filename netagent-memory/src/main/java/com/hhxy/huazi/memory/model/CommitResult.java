package com.hhxy.huazi.memory.model;

/** 提交结果仅在确认写入或确认已提交时携带 revision；结果未知不能当作失败自动重放业务操作。 */
public record CommitResult(Status status, String revision) {
    // COMMITTED、ALREADY_COMMITTED 携带已确认版本；CONFLICT、SESSION_EXPIRED 没有确认版本，OUTCOME_UNKNOWN 也不得携带版本。
    // OUTCOME_UNKNOWN 不代表失败，不可盲目重试。
    public enum Status { COMMITTED, ALREADY_COMMITTED, CONFLICT, SESSION_EXPIRED, OUTCOME_UNKNOWN }

    // 强制状态与版本成对约束，避免把未确认写入的版本交给后续乐观并发操作。
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

    // 仅用于无版本结果；传入 COMMITTED 或 ALREADY_COMMITTED 会因缺少确认版本而构造失败。
    public static CommitResult of(Status status) {
        return new CommitResult(status, null);
    }
}
