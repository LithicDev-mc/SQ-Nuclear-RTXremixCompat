local M = { path = "/.sqos/account" }

local B = bit32
if not B then
    local function op(f)
        return function(a, b, ...)
            if b == nil then return a end
            local r, p = 0, 1
            for _ = 1, 32 do
                if f(a % 2, b % 2) then r = r + p end
                a, b, p = math.floor(a / 2), math.floor(b / 2), p * 2
            end
            return op(f)(r, ...)
        end
    end
    B = {
        band = op(function(x, y) return x == 1 and y == 1 end),
        bxor = op(function(x, y) return x ~= y end),
        bnot = function(a) return 4294967295 - a % 4294967296 end,
        rshift = function(a, n) return math.floor(a % 4294967296 / 2 ^ n) end,
        rrotate = function(a, n)
            a = a % 4294967296
            return math.floor(a / 2 ^ n) + (a % 2 ^ n) * 2 ^ (32 - n)
        end,
    }
end
local band, bxor, bnot, rshift, rrotate = B.band, B.bxor, B.bnot, B.rshift, B.rrotate
local K = {
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
}

local function be32(n)
    return string.char(band(rshift(n, 24), 255), band(rshift(n, 16), 255), band(rshift(n, 8), 255), band(n, 255))
end

function M.sha256(msg)
    local h = { 0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19 }
    local bits = #msg * 8
    msg = msg .. "\128" .. ("\0"):rep((55 - #msg) % 64) .. be32(math.floor(bits / 2 ^ 32)) .. be32(bits % 2 ^ 32)
    local w = {}
    for chunk = 1, #msg, 64 do
        for i = 0, 15 do
            local a, b, c, d = msg:byte(chunk + i * 4, chunk + i * 4 + 3)
            w[i] = a * 16777216 + b * 65536 + c * 256 + d
        end
        for i = 16, 63 do
            local x, y = w[i - 15], w[i - 2]
            local s0 = bxor(rrotate(x, 7), rrotate(x, 18), rshift(x, 3))
            local s1 = bxor(rrotate(y, 17), rrotate(y, 19), rshift(y, 10))
            w[i] = band(w[i - 16] + s0 + w[i - 7] + s1, 0xffffffff)
        end
        local a, b, c, d, e, f, g, hh = h[1], h[2], h[3], h[4], h[5], h[6], h[7], h[8]
        for i = 0, 63 do
            local t1 = hh + bxor(rrotate(e, 6), rrotate(e, 11), rrotate(e, 25)) + bxor(band(e, f), band(bnot(e), g)) + K[i + 1] + w[i]
            local t2 = bxor(rrotate(a, 2), rrotate(a, 13), rrotate(a, 22)) + bxor(band(a, b), band(a, c), band(b, c))
            hh, g, f, e, d, c, b, a = g, f, e, band(d + t1, 0xffffffff), c, b, a, band(t1 + t2, 0xffffffff)
        end
        h[1], h[2], h[3], h[4] = band(h[1] + a, 0xffffffff), band(h[2] + b, 0xffffffff), band(h[3] + c, 0xffffffff), band(h[4] + d, 0xffffffff)
        h[5], h[6], h[7], h[8] = band(h[5] + e, 0xffffffff), band(h[6] + f, 0xffffffff), band(h[7] + g, 0xffffffff), band(h[8] + hh, 0xffffffff)
    end
    local out = {}
    for i = 1, 8 do out[i] = string.format("%08x", h[i]) end
    return table.concat(out)
end

function M.load()
    local d = { users = {}, order = {}, skipped = false }
    local ok, data = pcall(function()
        if not fs.exists(M.path) then return nil end
        local h = fs.open(M.path, "r")
        local t = textutils.unserialise(h.readAll() or "")
        h.close()
        return t
    end)
    if ok and type(data) == "table" and type(data.users) == "table" then
        d.users, d.skipped = data.users, data.skipped == true
        for _, n in ipairs(data.order or {}) do
            if d.users[n] then d.order[#d.order + 1] = n end
        end
    end
    return d
end

function M.save(d)
    return pcall(function()
        local h = fs.open(M.path, "w")
        h.write(textutils.serialise({ users = d.users, order = d.order, skipped = d.skipped }))
        h.close()
    end)
end

local function newSalt()
    local s = {}
    for i = 1, 16 do s[i] = string.format("%x", math.random(0, 15)) end
    return table.concat(s) .. tostring(os.epoch and os.epoch("utc") or os.clock and os.clock() or 0)
end

function M.set(d, name, pw)
    local salt = newSalt()
    if not d.users[name] then d.order[#d.order + 1] = name end
    d.users[name] = { salt = salt, hash = M.sha256(salt .. pw) }
    return M.save(d)
end

function M.check(d, name, pw)
    local u = d.users[name]
    return u ~= nil and M.sha256(u.salt .. pw) == u.hash
end

return M
