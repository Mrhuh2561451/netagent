-- 操作名、参数顺序和返回状态必须与 Java 存储层保持一致，避免协议解析错位。
local op = ARGV[1]
local expected = ARGV[2]
local revision = ARGV[3]
local payload = ARGV[4]
local updated = ARGV[5]
local commitId = ARGV[6]
local digest = ARGV[7]
local ttl = tonumber(ARGV[8])
local maxBytes = tonumber(ARGV[9])
local maxTurns = tonumber(ARGV[10])
local maxMessages = tonumber(ARGV[11])
local maxTurnBytes = tonumber(ARGV[12])

local function nonempty(value)
    return type(value) == 'string' and #value > 0
end

local function uuid(value)
    return type(value) == 'string' and #value == 36
        and value:match('^[0-9a-f%-]+$') ~= nil
        and value:sub(9, 9) == '-' and value:sub(14, 14) == '-'
        and value:sub(19, 19) == '-' and value:sub(24, 24) == '-'
end

local function array(value)
    if type(value) ~= 'table' then return false end
    local count = 0
    for key, _ in pairs(value) do
        if type(key) ~= 'number' or key < 1 or key % 1 ~= 0 then return false end
        count = count + 1
    end
    return count == #value
end

local function timestamp(value)
    if type(value) ~= 'string' then return false end
    local year, month, day, hour, minute, second, fraction =
        value:match('^(%d%d%d%d)%-(%d%d)%-(%d%d)T(%d%d):(%d%d):(%d%d)(.*)Z$')
    if not year or (fraction ~= '' and not fraction:match('^%.%d+$')) or #fraction > 10 then return false end
    year, month, day = tonumber(year), tonumber(month), tonumber(day)
    if month < 1 or month > 12 or tonumber(hour) > 23 or tonumber(minute) > 59 or tonumber(second) > 59 then
        return false
    end
    local days = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31}
    if year % 4 == 0 and (year % 100 ~= 0 or year % 400 == 0) then days[2] = 29 end
    return day >= 1 and day <= days[month]
end

local function fields(value, names)
    if type(value) ~= 'table' then return false end
    local count = 0
    for name, _ in pairs(value) do
        if not names[name] then return false end
        count = count + 1
    end
    local required = 0
    for name in pairs(names) do required = required + 1 end
    return count == required
end

local function validTurn(turn)
    if not fields(turn, {requestId=true, createdAt=true, messages=true})
        or not nonempty(turn.requestId) or not timestamp(turn.createdAt)
        or not array(turn.messages) or #turn.messages < 2 or #turn.messages > maxMessages then
        return false
    end
    -- 工具调用必须逐一闭合，不能在结果未齐时继续助手输出或提交半轮对话。
    local ids, calls, pending = {}, {}, {}
    for i, message in ipairs(turn.messages) do
        if not fields(message, {messageId=true, role=true, text=true, toolCallId=true, toolCalls=true})
            or not nonempty(message.messageId) or ids[message.messageId]
            or type(message.text) ~= 'string' or not array(message.toolCalls) then return false end
        ids[message.messageId] = true
        local role = message.role
        if i == 1 and role ~= 'USER' then return false end
        if i > 1 and role == 'USER' then return false end
        if i == #turn.messages and (role ~= 'ASSISTANT' or #message.toolCalls ~= 0) then return false end
        if role == 'TOOL' then
            if #message.toolCalls ~= 0 or not nonempty(message.toolCallId)
                or not pending[message.toolCallId] then return false end
            pending[message.toolCallId] = nil
        elseif role == 'USER' or role == 'ASSISTANT' then
            if next(pending) ~= nil or (message.toolCallId ~= nil and message.toolCallId ~= cjson.null) then
                return false
            end
            if role == 'USER' and #message.toolCalls ~= 0 then return false end
            if i > 1 and i < #turn.messages and #message.toolCalls == 0 then return false end
            for _, call in ipairs(message.toolCalls) do
                if not fields(call, {id=true, name=true, arguments=true}) or not nonempty(call.id) or calls[call.id]
                    or not nonempty(call.name) or type(call.arguments) ~= 'string'
                    or not call.arguments:match('^%s*{') then return false end
                local ok, arguments = pcall(cjson.decode, call.arguments)
                if not ok or type(arguments) ~= 'table' then return false end
                calls[call.id], pending[call.id] = true, true
            end
        else
            return false
        end
    end
    return next(pending) == nil
end

-- 按原始 JSON 的字节范围计算单轮大小，避免重新编码造成限额判断偏差。
local function boundedTurns(raw)
    local depth, start, quoted, escaped = 0, 0, false, false
    for i = 1, #raw do
        local character = raw:sub(i, i)
        if quoted then
            if escaped then escaped = false
            elseif character == '\\' then escaped = true
            elseif character == '"' then quoted = false end
        elseif character == '"' then quoted = true
        elseif character == '{' or character == '[' then
            if depth == 1 then start = i end
            depth = depth + 1
        elseif character == '}' or character == ']' then
            depth = depth - 1
            if depth == 1 and i - start + 1 > maxTurnBytes then return false end
        end
    end
    return true
end

local function quote(value)
    return cjson.encode(value):gsub('\\/', '/')
end

local function validState(schema, rev, raw, time, id, hash)
    if schema ~= '1' or not uuid(rev) or type(raw) ~= 'string' or #raw > maxBytes
        or not raw:match('^%s*%[') or not timestamp(time)
        or type(id) ~= 'string' or type(hash) ~= 'string' then return nil end
    -- 快照限额包含元数据，按 Java 序列化格式重建外层结构后再核对总字节数。
    local envelope = '{"lastCommitDigest":' .. quote(hash) .. ',"lastCommitId":' .. quote(id)
        .. ',"revision":' .. quote(rev) .. ',"schemaVersion":1,"turns":' .. raw
        .. ',"updatedAt":' .. quote(time) .. '}'
    if #envelope > maxBytes or not boundedTurns(raw) then return nil end
    local ok, turns = pcall(cjson.decode, raw)
    if not ok or not array(turns) or #turns > maxTurns then return nil end
    local requests, messageCount = {}, 0
    for _, turn in ipairs(turns) do
        if not validTurn(turn) or requests[turn.requestId] then return nil end
        requests[turn.requestId] = true
        messageCount = messageCount + #turn.messages
    end
    -- cjson 将空数组与空对象都解码为表，需结合原始 JSON 检查数组字段。
    local _, callArrays = raw:gsub('"toolCalls"%s*:%s*%[', '')
    local _, messageArrays = raw:gsub('"messages"%s*:%s*%[', '')
    if callArrays ~= messageCount or messageArrays ~= #turns then return nil end
    if #turns == 0 then
        if id ~= '' or hash ~= '' then return nil end
    elseif id ~= turns[#turns].requestId or #hash ~= 64 or not hash:match('^[0-9a-f]+$') then
        return nil
    end
    return turns
end

local function same(left, right)
    if type(left) ~= type(right) then return false end
    if type(left) ~= 'table' then return left == right end
    for key, value in pairs(left) do
        if not same(value, right[key]) then return false end
    end
    for key, _ in pairs(right) do
        if left[key] == nil then return false end
    end
    return true
end

if #KEYS ~= 1 or #ARGV ~= 12 or not ttl or ttl <= 0 or ttl % 1 ~= 0 or ttl > 9007199254740991
    or not maxBytes or maxBytes <= 0 or not maxTurns or maxTurns < 1
    or not maxMessages or maxMessages < 2 or not maxTurnBytes or maxTurnBytes < 1 then
    return {'INVALID'}
end
if op ~= 'load' and op ~= 'init' and op ~= 'cas' and op ~= 'clear' then return {'INVALID'} end
local replacement
if op ~= 'load' then
    replacement = validState('1', revision, payload, updated, commitId, digest)
    if not replacement then return {'INVALID'} end
    if op ~= 'cas' and #replacement ~= 0 then return {'INVALID'} end
    if op == 'cas' and (#replacement == 0 or not uuid(expected) or revision == expected) then
        return {'INVALID'}
    end
    if op == 'clear' and expected ~= '' and (not uuid(expected) or revision == expected) then
        return {'INVALID'}
    end
end
local exists = redis.call('EXISTS', KEYS[1]) == 1
local current, turns
-- 已存状态格式异常时只报告错误，不能覆盖或删除可能由较新版本写入的数据。
if exists then
    if redis.call('TYPE', KEYS[1]).ok ~= 'hash' or redis.call('HLEN', KEYS[1]) ~= 6 then
        return {'FORMAT'}
    end
    current = redis.call('HMGET', KEYS[1], 'schemaVersion', 'revision', 'payload', 'updatedAt',
        'lastCommitId', 'lastCommitDigest')
    turns = validState(current[1], current[2], current[3], current[4], current[5], current[6])
    if not turns or redis.call('PTTL', KEYS[1]) < 0 then return {'FORMAT'} end
end
local function state(initialized)
    return {'STATE', initialized, current[1], current[2], current[3], current[4], current[5], current[6]}
end
-- 仅实际写入时设置保留期，读取和重复提交都不为会话续期。
local function writeState()
    redis.call('HSET', KEYS[1], 'schemaVersion', '1', 'revision', revision, 'payload', payload,
        'updatedAt', updated, 'lastCommitId', commitId, 'lastCommitDigest', digest)
    redis.call('PEXPIRE', KEYS[1], ttl)
end
if op == 'load' then
    if not exists then return {'MISSING'} end
    return state('0')
end
if op == 'init' then
    if exists then return state('0') end
    writeState()
    current = {'1', revision, payload, updated, commitId, digest}
    return state('1')
end
if op == 'clear' then
    if exists and current[2] == revision then return {'COMMITTED', revision} end
    -- 预读后版本发生变化时不再清空，避免抹掉刚提交的新内容。
    if (exists and current[2] ~= expected) or (not exists and expected ~= '') then
        return {'OUTCOME_UNKNOWN'}
    end
    writeState()
    return {'COMMITTED', revision}
end
-- 迟到的提交不能通过写入重新创建已过期会话。
if not exists then return {'SESSION_EXPIRED'} end
local newTurn = replacement[#replacement]
-- 去重优先于版本比较，并同时核对摘要和内容，识别响应丢失后的同内容重试。
if current[5] == commitId then
    if current[6] ~= digest or not same(turns[#turns], newTurn) then return {'IDENTITY_MISMATCH'} end
    return {'ALREADY_COMMITTED', current[2]}
end
for _, turn in ipairs(turns) do
    if turn.requestId == commitId then
        if not same(turn, newTurn) then return {'IDENTITY_MISMATCH'} end
        -- 较早的请求已不再对应最近提交，不能把当前版本当作它的提交版本。
        return {'OUTCOME_UNKNOWN'}
    end
end
if current[2] ~= expected then return {'CONFLICT'} end
writeState()
return {'COMMITTED', revision}
