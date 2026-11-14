local M = {}

local floor = math.floor

function M.split(c)
    return floor(c / 65536) % 256, floor(c / 256) % 256, c % 256
end

function M.join(r, g, b)
    r, g, b = math.max(0, math.min(255, floor(r + 0.5))), math.max(0, math.min(255, floor(g + 0.5))), math.max(0, math.min(255, floor(b + 0.5)))
    return r * 65536 + g * 256 + b
end

function M.mix(a, b, t)
    local r1, g1, b1 = M.split(a)
    local r2, g2, b2 = M.split(b)
    return M.join(r1 + (r2 - r1) * t, g1 + (g2 - g1) * t, b1 + (b2 - b1) * t)
end

function M.lum(c)
    local r, g, b = M.split(c)
    return (0.299 * r + 0.587 * g + 0.114 * b) / 255
end

function M.rrect(cmds, x, y, w, h, r, color)
    if w <= 0 or h <= 0 then return end
    r = M.flat and 0 or math.max(0, math.min(r, floor(w / 2), floor(h / 2)))
    if r <= 1 then
        cmds[#cmds + 1] = { "fill", x, y, w, h, color }
        return
    end
    cmds[#cmds + 1] = { "fill", x + r, y, w - 2 * r, h, color }
    cmds[#cmds + 1] = { "fill", x, y + r, r, h - 2 * r, color }
    cmds[#cmds + 1] = { "fill", x + w - r, y + r, r, h - 2 * r, color }
    local cx1, cx2 = x + r - 0.5, x + w - r - 0.5
    local cy1, cy2 = y + r - 0.5, y + h - r - 0.5
    cmds[#cmds + 1] = { "disc", cx1, cy1, r, color }
    cmds[#cmds + 1] = { "disc", cx2, cy1, r, color }
    cmds[#cmds + 1] = { "disc", cx1, cy2, r, color }
    cmds[#cmds + 1] = { "disc", cx2, cy2, r, color }
end

function M.shadow(cmds, x, y, w, h, strength)
    strength = strength or 1
    for i = 1, 3 do
        local g = 4 - i
        cmds[#cmds + 1] = { "afill", x - g + 1, y - g + 3, w + 2 * g - 2, h + 2 * g - 2, 0x000000, 0.10 * strength }
    end
end

function M.frame(cmds, x, y, w, h, color)
    cmds[#cmds + 1] = { "fill", x, y, w, 1, color }
    cmds[#cmds + 1] = { "fill", x, y + h - 1, w, 1, color }
    cmds[#cmds + 1] = { "fill", x, y + 1, 1, h - 2, color }
    cmds[#cmds + 1] = { "fill", x + w - 1, y + 1, 1, h - 2, color }
end

return M
