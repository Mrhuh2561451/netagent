package com.hhxy.huazi.memory.store;

import com.hhxy.huazi.memory.model.CommitResult;
import com.hhxy.huazi.memory.model.MemoryScope;
import com.hhxy.huazi.memory.model.MemorySnapshot;

import java.util.Optional;

public interface MemoryStore {
    record LoadResult(MemorySnapshot snapshot, boolean initialized) { }

    /** 读取不续期；不存在时不创建会话，以免核验操作改变会话生命周期。 */
    Optional<MemorySnapshot> load(MemoryScope scope);

    /** 初始化须与存在性检查原子执行，避免覆盖并发创建的会话。 */
    LoadResult loadOrCreate(MemoryScope scope);

    /** 原子核验版本和提交身份，拒绝覆盖并发更新，也不复活已过期会话。 */
    CommitResult compareAndSet(MemoryScope scope, String expectedRevision, MemorySnapshot replacement);

    /** 用新版本的空快照替代删除键，使持有旧版本的提交失效。 */
    CommitResult clear(MemoryScope scope);
}
