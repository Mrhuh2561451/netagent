package com.hhxy.huazi.memory.store;

import com.hhxy.huazi.memory.model.CommitResult;
import com.hhxy.huazi.memory.model.MemoryScope;
import com.hhxy.huazi.memory.model.MemorySnapshot;

import java.util.Optional;

/** 以完整快照为并发写入单位；实现须区分会话不存在、格式损坏和基础设施不可用。 */
public interface MemoryStore {
    /** initialized 仅表示本次调用创建了会话，不表示已有会话的内容是否为空。 */
    record LoadResult(MemorySnapshot snapshot, boolean initialized) { }

    /** 不存在时返回空且不创建会话；读取不续期，格式异常或不可用不能伪装成空结果。 */
    Optional<MemorySnapshot> load(MemoryScope scope);

    /** 原子创建或返回已有快照；只有实际创建才设置保留期，不覆盖并发创建的会话。 */
    LoadResult loadOrCreate(MemoryScope scope);

    /** replacement 须含完整轮次和新版本；原子核验 expectedRevision 与提交身份，不复活已过期会话。 */
    CommitResult compareAndSet(MemoryScope scope, String expectedRevision, MemorySnapshot replacement);

    /** 以新版本空快照清空或创建会话；并发变化时不覆盖新内容，结果未知时不能声称清空成功。 */
    CommitResult clear(MemoryScope scope);
}
