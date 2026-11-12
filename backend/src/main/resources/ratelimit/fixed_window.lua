-- Fixed-window rate-limit counter. Redis runs a script as ONE indivisible step, so no other client
-- can run between the INCR and the PEXPIRE below: concurrent requests cannot bypass the limit.
--   KEYS[1] = counter key
--   ARGV[1] = window length in milliseconds
-- Returns { count_in_window, milliseconds_until_the_window_ends }
local count = redis.call('INCR', KEYS[1])
local ttl = redis.call('PTTL', KEYS[1])
if count == 1 or ttl < 0 then
    -- first hit of a new window (or a key that somehow has no expiry): (re)start the timer.
    -- Later hits never extend the window, so a blocked client cannot keep itself blocked forever.
    redis.call('PEXPIRE', KEYS[1], ARGV[1])
    ttl = tonumber(ARGV[1])
end
return { count, ttl }
