local current_time = tonumber(ARGV[1])
local window_size = tonumber(ARGV[2])
local max_requests = tonumber(ARGV[3])
local request_id = ARGV[4]

local window_start = current_time - window_size

redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', window_start)

local current_count = redis.call('ZCARD', KEYS[1])

if current_count < max_requests then
    local member = current_time .. ":" .. request_id
    redis.call('ZADD', KEYS[1], current_time, member)

    local expiry_seconds = math.ceil(window_size / 1000) + 10
    redis.call('EXPIRE', KEYS[1], expiry_seconds)

    return current_count + 1
else
    return current_count
end