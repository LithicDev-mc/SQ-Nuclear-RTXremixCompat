local gfx = require("sqos.gfx")

local M = {}
local WHITE = 0xFFFFFF

local glyphs = {}

function glyphs.plant(f, base)
    f.tri(12, 3, 5, 11.5, 10.5, 11.5, WHITE)
    f.tri(9.5, 8.5, 15, 8.5, 8, 17.5, WHITE)
    f.tri(9.5, 8.5, 10.5, 11.5, 12.5, 8.5, WHITE)
    f.tri(10.5, 11.5, 15, 8.5, 9.5, 8.5, WHITE)
end

function glyphs.storage(f, base)
    for i = 0, 2 do
        f.rr(4, 4 + i * 5, 12, 3, 1, WHITE)
        f.rr(13, 4.8 + i * 5, 1.4, 1.4, 0, base)
    end
end

function glyphs.explorer(f, base)
    f.rr(3, 4.5, 6, 3, 1, WHITE)
    f.rr(3, 6, 14, 10, 2, WHITE)
    f.rect(3, 9, 14, 1, base)
end

function glyphs.editor(f, base)
    f.rr(5, 3, 10, 14, 2, WHITE)
    f.rect(7, 6, 6, 1, base)
    f.rect(7, 9, 6, 1, base)
    f.rect(7, 12, 4, 1, base)
end

function glyphs.shell(f, base)
    f.rr(2.5, 4, 15, 12, 2, 0x10131A)
    f.line(5.5, 7.5, 8.5, 10, WHITE, 1.5)
    f.line(8.5, 10, 5.5, 12.5, WHITE, 1.5)
    f.rect(10.5, 12, 4, 1.5, WHITE)
end

function glyphs.settings(f, base)
    f.rect(9, 2, 2, 16, WHITE)
    f.rect(2, 9, 16, 2, WHITE)
    f.line(4.5, 4.5, 15.5, 15.5, WHITE, 2)
    f.line(15.5, 4.5, 4.5, 15.5, WHITE, 2)
    f.disc(10, 10, 6, WHITE)
    f.disc(10, 10, 3, base)
end

function glyphs.chat(f, base)
    f.rr(3, 4, 14, 10, 3, WHITE)
    f.tri(6, 13, 6, 18, 11, 13, WHITE)
    f.rect(6, 7, 8, 1, base)
    f.rect(6, 10, 5, 1, base)
end

function glyphs.bank(f, base)
    f.disc(10, 10, 7, WHITE)
    f.disc(10, 10, 5, base)
    f.text("$", 10, 10, WHITE)
end

function glyphs.auction(f, base)
    f.line(5, 5.5, 10.5, 11, WHITE, 4)
    f.line(3.5, 8.5, 8.5, 3.5, WHITE, 4)
    f.line(10, 10, 16, 16, WHITE, 2.5)
    f.rr(3, 16.5, 9, 2, 1, WHITE)
end

function glyphs.exchange(f, base)
    f.line(3, 15, 7.5, 10.5, WHITE, 2)
    f.line(7.5, 10.5, 10.5, 13, WHITE, 2)
    f.line(10.5, 13, 16.5, 5.5, WHITE, 2)
    f.tri(17.5, 4, 17.5, 9.5, 12.5, 4, WHITE)
end

function glyphs.ticker(f, base)
    f.rr(3.5, 11, 3, 6, 1, WHITE)
    f.rr(8.5, 7, 3, 10, 1, WHITE)
    f.rr(13.5, 3.5, 3, 13.5, 1, WHITE)
end

function glyphs.login(f, base)
    f.disc(10, 7, 3.5, WHITE)
    f.rr(4, 11.5, 12, 6, 3, WHITE)
    f.rect(4, 15, 12, 3, base)
    f.rect(4, 15, 12, 1, WHITE)
end

function glyphs.runner(f, base)
    f.tri(6, 4, 6, 16, 16, 10, WHITE)
end

function glyphs.system(f, base)
    f.rr(3, 4, 14, 10, 2, WHITE)
    f.rect(5, 6, 10, 6, base)
    f.rect(7, 15.5, 6, 1.5, WHITE)
end

function glyphs.notes(f, base)
    f.rr(4, 3, 12, 14, 2, WHITE)
    f.tri(16, 13, 16, 17, 12, 17, base)
    f.rect(6.5, 6.5, 7, 1, base)
    f.rect(6.5, 9.5, 7, 1, base)
    f.rect(6.5, 12.5, 4, 1, base)
end

function glyphs.calc(f, base)
    f.rr(4.5, 2.5, 11, 15, 2, WHITE)
    f.rect(6.5, 4.5, 7, 3, base)
    for r = 0, 1 do
        for c = 0, 2 do f.rect(6.5 + c * 2.6, 9.5 + r * 3, 1.6, 1.6, base) end
    end
end

function M.draw(cmds, id, icon, x, y, s, col, fg, bg)
    local base = bg or (icon and col(icon[2])) or 0x3B82F6
    local k = s / 20
    local white = WHITE
    WHITE = fg or white
    if not fg then
        gfx.rrect(cmds, x, y, s, s, math.max(2, math.floor(s * 0.3)), base)
        if s >= 8 then
            cmds[#cmds + 1] = { "afill", x + math.floor(s * 0.2), y + 1, s - 2 * math.floor(s * 0.2), 1, WHITE, 0.25 }
        end
    end
    local f = {}
    function f.rect(a, b, w, h, c) cmds[#cmds + 1] = { "fill", math.floor(x + a * k + 0.5), math.floor(y + b * k + 0.5), math.max(1, math.floor(w * k + 0.5)), math.max(1, math.floor(h * k + 0.5)), c } end
    function f.rr(a, b, w, h, r, c) gfx.rrect(cmds, math.floor(x + a * k + 0.5), math.floor(y + b * k + 0.5), math.max(1, math.floor(w * k + 0.5)), math.max(1, math.floor(h * k + 0.5)), math.floor(r * k), c) end
    function f.disc(a, b, r, c) cmds[#cmds + 1] = { "disc", x + a * k - 0.5, y + b * k - 0.5, r * k, c } end
    function f.line(a, b, c2, d, c, w) cmds[#cmds + 1] = { "line", x + a * k - 0.5, y + b * k - 0.5, x + c2 * k - 0.5, y + d * k - 0.5, c, math.max(1, math.floor(w * k + 0.5)) } end
    function f.tri(a, b, c2, d, e, g, c) cmds[#cmds + 1] = { "tri", x + a * k - 0.5, y + b * k - 0.5, x + c2 * k - 0.5, y + d * k - 0.5, x + e * k - 0.5, y + g * k - 0.5, c } end
    function f.text(t, a, b, c)
        local sz = math.max(6, math.floor(11 * k))
        cmds[#cmds + 1] = { "ctext", math.floor(x + (a - 8) * k), math.floor(y + (b - 6) * k), math.floor(16 * k), math.floor(12 * k), t, c, sz }
    end
    if s >= 14 and glyphs[id] then
        glyphs[id](f, base)
    else
        local letter = icon and icon[1] or "?"
        cmds[#cmds + 1] = { "ctext", x, y, s, s, letter, WHITE, s >= 14 and 12 or 8 }
    end
    WHITE = white
end

function M.sys(cmds, kind, x, y, s, color)
    local k = s / 10
    local function P(a, b) return x + a * k, y + b * k end
    if kind == "bell" then
        local cx, cy = P(5, 4.5)
        cmds[#cmds + 1] = { "disc", cx, cy, 3.2 * k, color }
        cmds[#cmds + 1] = { "fill", math.floor(x + 1.5 * k), math.floor(y + 4 * k), math.max(1, math.floor(7 * k)), math.max(1, math.floor(3.5 * k)), color }
        cmds[#cmds + 1] = { "disc", x + 5 * k, y + 8.6 * k, 1.1 * k, color }
    elseif kind == "search" then
        cmds[#cmds + 1] = { "disc", x + 4.2 * k, y + 4.2 * k, 3.2 * k, color }
        cmds[#cmds + 1] = { "disc", x + 4.2 * k, y + 4.2 * k, 2 * k, 0 }
        cmds[#cmds + 1] = { "line", x + 6.5 * k, y + 6.5 * k, x + 9 * k, y + 9 * k, color, math.max(1, math.floor(1.6 * k)) }
    elseif kind == "user" then
        cmds[#cmds + 1] = { "disc", x + 5 * k, y + 3.3 * k, 2.2 * k, color }
        gfx.rrect(cmds, math.floor(x + 1.5 * k), math.floor(y + 6 * k), math.max(1, math.floor(7 * k)), math.max(1, math.floor(4 * k)), math.floor(2 * k), color)
    elseif kind == "lock" then
        gfx.rrect(cmds, math.floor(x + 2 * k), math.floor(y + 4.5 * k), math.max(1, math.floor(6 * k)), math.max(1, math.floor(5 * k)), math.floor(k), color)
        cmds[#cmds + 1] = { "arc", x + 5 * k, y + 4.5 * k, 2.6 * k, 1.4 * k, -1.6, 1.6, color }
    elseif kind == "power" then
        cmds[#cmds + 1] = { "arc", x + 5 * k, y + 5.5 * k, 4 * k, 2.6 * k, 0.6, 5.7, color }
        cmds[#cmds + 1] = { "fill", math.floor(x + 4.3 * k), math.floor(y + 0.8 * k), math.max(1, math.floor(1.4 * k)), math.max(1, math.floor(4.5 * k)), color }
    elseif kind == "reboot" then
        cmds[#cmds + 1] = { "arc", x + 5 * k, y + 5 * k, 4 * k, 2.6 * k, 0.9, 6.0, color }
        cmds[#cmds + 1] = { "tri", x + 5 * k, y + 0.3 * k, x + 8.5 * k, y + 2.2 * k, x + 5.2 * k, y + 3.8 * k, color }
    elseif kind == "pin" then
        cmds[#cmds + 1] = { "disc", x + 5 * k, y + 3.5 * k, 2.5 * k, color }
        cmds[#cmds + 1] = { "tri", x + 3 * k, y + 5 * k, x + 7 * k, y + 5 * k, x + 5 * k, y + 9.5 * k, color }
    elseif kind == "check" then
        cmds[#cmds + 1] = { "line", x + 2 * k, y + 5.5 * k, x + 4.3 * k, y + 8 * k, color, math.max(1, math.floor(1.6 * k)) }
        cmds[#cmds + 1] = { "line", x + 4.3 * k, y + 8 * k, x + 8.5 * k, y + 2.5 * k, color, math.max(1, math.floor(1.6 * k)) }
    elseif kind == "close" then
        cmds[#cmds + 1] = { "line", x + 2.5 * k, y + 2.5 * k, x + 7.5 * k, y + 7.5 * k, color, math.max(1, math.floor(1.4 * k)) }
        cmds[#cmds + 1] = { "line", x + 7.5 * k, y + 2.5 * k, x + 2.5 * k, y + 7.5 * k, color, math.max(1, math.floor(1.4 * k)) }
    end
end

return M
