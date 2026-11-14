local theme = require("sqos.theme")
local ui = require("sqos.ui")
local C = theme.C

local M = {}

local DIGITS = {
    ["0"] = { "111", "101", "101", "101", "111" }, ["1"] = { "010", "110", "010", "010", "111" },
    ["2"] = { "111", "001", "111", "100", "111" }, ["3"] = { "111", "001", "111", "001", "111" },
    ["4"] = { "101", "101", "111", "001", "001" }, ["5"] = { "111", "100", "111", "001", "111" },
    ["6"] = { "111", "100", "111", "101", "111" }, ["7"] = { "111", "001", "010", "010", "010" },
    ["8"] = { "111", "101", "111", "101", "111" }, ["9"] = { "111", "101", "111", "001", "111" },
    [":"] = { "0", "1", "0", "1", "0" },
}

local function newGrid(W, H, fg, bg)
    local g = { W = W, H = H, t = {}, f = {}, b = {} }
    for y = 1, H do
        local t, f, b = {}, {}, {}
        for x = 1, W do t[x], f[x], b[x] = " ", ui.hex(fg), ui.hex(bg) end
        g.t[y], g.f[y], g.b[y] = t, f, b
    end
    return g
end

local function put(g, x, y, s, fg, bg)
    if y < 1 or y > g.H then return end
    local f, b = ui.hex(fg), bg and ui.hex(bg)
    for i = 1, #s do
        local xx = x + i - 1
        if xx >= 1 and xx <= g.W then
            g.t[y][xx] = s:sub(i, i)
            g.f[y][xx] = f
            if b then g.b[y][xx] = b end
        end
    end
end

local function fill(g, x, y, w, h, bg, fg)
    local b = ui.hex(bg)
    for yy = y, y + h - 1 do
        if yy >= 1 and yy <= g.H then
            for xx = x, x + w - 1 do
                if xx >= 1 and xx <= g.W then
                    g.t[yy][xx] = " "
                    g.b[yy][xx] = b
                    if fg then g.f[yy][xx] = ui.hex(fg) end
                end
            end
        end
    end
end

local function blitLine(g, x, y, t, f, b)
    if y < 1 or y > g.H then return end
    for i = 1, #t do
        local xx = x + i - 1
        if xx >= 1 and xx <= g.W then
            g.t[y][xx], g.f[y][xx], g.b[y][xx] = t:sub(i, i), f:sub(i, i), b:sub(i, i)
        end
    end
end

local function kindColor(kind)
    if kind == "ok" then return C.ok end
    if kind == "warn" then return C.warn end
    if kind == "alarm" then return C.alarm end
    return C.accent2
end

local function chip(g, x, y, w, h, icon, bg)
    put(g, x + math.floor((w - 1) / 2), y + math.floor((h - 1) / 2), icon and icon[1] or "?", C.accent, bg)
end

local function console()
    return theme.name == "console"
end

local function button(g, b, bg)
    local back = b.on and C.sel or (b.danger and C.alarm or (bg or C.light))
    local fg = b.on and C.selText or C.text
    local label = console() and ("[" .. ui.clip(b.label, b.w - 2) .. "]") or (" " .. b.label)
    put(g, b.x, b.y, ui.fit(label, b.w), fg, back)
end

local function bigClock(g, x, y, text, pw)
    local cx = x
    for i = 1, #text do
        local d = DIGITS[text:sub(i, i)]
        if d then
            for r = 1, 5 do
                for c = 1, #d[r] do
                    if d[r]:sub(c, c) == "1" then
                        fill(g, cx + (c - 1) * pw, y + r - 1, pw, 1, C.text)
                    end
                end
            end
            cx = cx + #d[1] * pw + pw
        end
    end
end

local function drawWindow(g, w)
    local active = w.active
    local tbg = w.grab and C.accent or (active and C.light or C.panel)
    local tfg = w.grab and C.selText or (active and C.titleText or C.dim)
    fill(g, w.x, w.y, w.w, 1, tbg)
    local c, m, n = w.buttons[1], w.buttons[2], w.buttons[3]
    local right = w.x + w.w
    if n > 0 then put(g, n, w.y, " _ ", tfg, tbg) right = n end
    if m > 0 then put(g, m, w.y, w.max and " = " or " o ", tfg, tbg) right = math.min(right, m) end
    if c > 0 then put(g, c, w.y, " x ", active and C.alarm or tfg, tbg) right = math.min(right, c) end
    if active and not w.grab then put(g, w.x, w.y, " ", C.text, C.accent) end
    local title = console() and ("[ " .. w.title:upper() .. " ]") or w.title
    put(g, w.x + 2, w.y, ui.clip(title, math.max(0, right - w.x - 3)), tfg, tbg)
    local canvas = w.canvas
    if canvas.getLine then
        local _, ch = canvas.getSize()
        for r = 1, ch do
            local t, f, b = canvas.getLine(r)
            blitLine(g, w.x, w.y + r, t, f, b)
        end
    end
end

local function card(g, x, y, w, h, title)
    fill(g, x, y, w, h, C.panel)
    if title then put(g, x + 1, y, ui.clip(title, w - 2), C.dim, C.panel) end
end

function M.compose(sc)
    local W, H = sc.W, sc.H
    local g = newGrid(W, H, C.text, C.desk)

    if sc.saver then
        local pw = W >= 50 and 2 or 1
        local cw = 0
        for i = 1, #sc.clock do cw = cw + #DIGITS[sc.clock:sub(i, i)][1] * pw + pw end
        local x, y = math.floor((W - cw) / 2) + 1, math.floor((H - 7) / 2) + 1
        bigClock(g, x, y, sc.clock, pw)
        put(g, math.floor((W - #sc.date) / 2) + 1, y + 6, sc.date, C.dim, C.desk)
        local rows = {}
        for yy = 1, H do rows[yy] = { t = table.concat(g.t[yy]), f = table.concat(g.f[yy]), b = table.concat(g.b[yy]) } end
        return rows
    end

    fill(g, 1, 1, W, 1, C.panel)
    put(g, 2, 1, "SQ Con", C.accent, C.panel)
    local sf = sc.status.search
    local limit = sf and sf.x or (sc.tray.items[1] and sc.tray.items[1].x or W)
    put(g, 9, 1, ui.clip(sc.status.left, math.max(0, limit - 10)), C.dim, C.panel)
    if sf then put(g, sf.x, 1, ui.fit(" " .. sf.text, sf.w), C.dim, C.light) end

    for _, w in ipairs(sc.wins) do drawWindow(g, w) end

    local tb = sc.taskbar
    fill(g, 1, tb.y, tb.w, tb.h, C.panel)
    local s = tb.start
    put(g, s.x, s.y, ui.center(tb.w >= 4 and "SQ" or "S", s.w), C.selText, s.active and C.accent2 or C.accent)
    for _, it in ipairs(tb.items) do
        local running = #it.wins > 0
        local bg = it.active and C.light or C.panel
        local fg = it.active and C.accent or ((running and not it.min) and C.text or C.dim)
        put(g, it.x, it.y, ui.center(it.icon[1], it.w), fg, bg)
        if running then put(g, it.x, it.y, it.active and " " or ".", C.dim, it.active and C.accent or bg) end
        if it.badge then put(g, it.x + it.w - 1, it.y, tostring(math.min(9, it.badge)), C.text, C.alarm) end
    end

    local kb = sc.keys
    if kb then
        fill(g, 1, kb.y, W, 1, C.panel)
        put(g, 2, kb.y, ui.clip(kb.text, math.max(0, kb.view.x - 3)), C.dim, C.panel)
        put(g, kb.view.x, kb.y, " " .. kb.view.label, C.text, C.panel)
    end

    local tr = sc.tray
    for _, it in ipairs(tr.items) do
        local y = tr.y
        if it.id == "clock" then put(g, it.x, y, " " .. it.text .. " ", it.on and C.selText or C.text, it.on and C.accent or C.panel)
        elseif it.id == "date" then put(g, it.x, y, it.text .. " ", C.dim, C.panel)
        elseif it.id == "bell" then
            local on = it.count > 0
            put(g, it.x, y, " " .. (on and "*" or "o") .. it.text .. " ", it.on and C.selText or (on and C.warn or C.dim), it.on and C.accent or C.panel)
        elseif it.id == "alarm" then put(g, it.x, y, " !" .. it.text .. " ", C.text, C.alarm) end
    end

    for _, t in ipairs(sc.toasts) do
        local col = kindColor(t.kind)
        fill(g, t.x, t.y, t.w, t.h, C.panel)
        fill(g, t.x, t.y, 1, t.h, col)
        local label = (t.title and (t.title .. ": ") or "") .. t.text
        put(g, t.x + 2, t.y, ui.clip(label, t.w - 3), C.text, C.panel)
    end

    local menu = sc.menu
    if menu then
        card(g, menu.x, menu.y, menu.w, menu.h, menu.title)
        local sf = menu.search
        local shown = sf.text ~= "" and sf.text or sf.hint
        put(g, sf.x, sf.y, ui.fit(" " .. shown .. (sf.text ~= "" and "_" or ""), sf.w), sf.text ~= "" and C.text or C.dim, C.light)
        if menu.empty then put(g, menu.x + 2, menu.y + 3, ui.clip(menu.empty, menu.w - 3), C.dim, C.panel) end
        for _, t in ipairs(menu.tiles) do
            fill(g, t.x, t.y, t.w, t.h, C.light)
            chip(g, t.x, t.y, 3, t.h, t.icon, C.light)
            put(g, t.x + 4, t.y + math.floor((t.h - 1) / 2), ui.clip(t.label, t.w - 5), C.text, C.light)
        end
        if menu.user then put(g, menu.user.x, menu.user.y, ui.clip("@ " .. menu.user.text, menu.user.w), C.dim, C.panel) end
        for _, b in ipairs(menu.buttons or {}) do button(g, b) end
    end

    local q = sc.quick
    if q then
        card(g, q.x, q.y, q.w, q.h, q.title)
        for _, r in ipairs(q.rows) do
            if r.label and r.label ~= "" then put(g, q.x + 1, r.y, ui.clip(r.label, q.w - 2), C.dim, C.panel) end
            for _, b in ipairs(r.buttons or {}) do button(g, b) end
        end
    end

    local nc = sc.center
    if nc then
        card(g, nc.x, nc.y, nc.w, nc.h, nc.title)
        if nc.empty then put(g, nc.x + 1, nc.y + 1, ui.clip(nc.empty, nc.w - 2), C.dim, C.panel) end
        for _, e in ipairs(nc.entries) do
            fill(g, e.x, e.y, e.w, 1, C.light)
            fill(g, e.x, e.y, 1, 1, kindColor(e.kind))
            put(g, e.x + 2, e.y, ui.clip(e.title .. ": " .. e.text, e.w - 3), C.text, C.light)
        end
        if nc.clear then button(g, { x = nc.clear.x, y = nc.clear.y, w = nc.clear.w, label = nc.clear.label }) end
    end

    local sw = sc.switcher
    if sw then
        card(g, sw.x, sw.y, sw.w, sw.h)
        for _, it in ipairs(sw.items) do
            if it.sel then fill(g, it.x, it.y, it.w, it.h, C.light) end
            chip(g, it.x + 1, it.y, it.w - 2, 1, it.icon, it.sel and C.light or C.panel)
        end
        put(g, sw.x + 1, sw.y + sw.h - 1, ui.center(ui.clip(sw.label or "", sw.w - 2), sw.w - 2), C.text, C.panel)
    end

    local cx = sc.ctx
    if cx then
        fill(g, cx.x, cx.y, cx.w, cx.h, C.panel)
        for _, it in ipairs(cx.items) do
            put(g, cx.x + 1, it.y, ui.clip(it.label, cx.w - 2), it.danger and C.alarm or C.text, C.panel)
        end
    end

    if sc.hint then
        local y = math.max(1, sc.bottom)
        local text = " " .. sc.hint .. " "
        put(g, math.floor((W - #text) / 2) + 1, y, ui.clip(text, W), C.selText, C.accent)
    end

    local rows = {}
    for y = 1, H do
        rows[y] = { t = table.concat(g.t[y]), f = table.concat(g.f[y]), b = table.concat(g.b[y]) }
    end
    return rows
end

return M
