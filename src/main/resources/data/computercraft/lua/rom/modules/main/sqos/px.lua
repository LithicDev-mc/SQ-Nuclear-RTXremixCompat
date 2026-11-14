local theme = require("sqos.theme")
local ui = require("sqos.ui")
local gfx = require("sqos.gfx")
local icons = require("sqos.icons")
local C = theme.C

local M = {}

local idx = {}
for i = 0, 15 do idx[2 ^ i] = i + 1 end
local floor = math.floor

function M.new(mon, g)
    local self = {}
    local lastBase, lastPos, lastCursor
    local winPrev = {}

    local function col(c) return g.palette[idx[c] or 1] end
    local function X(cx) return g.ox + (cx - 1) * g.cw + 1 end
    local function Y(cy) return g.oy + (cy - 1) * g.ch + 1 end

    local function flush(cmds)
        local i, n = 1, #cmds
        while i <= n do
            local chunk = {}
            for k = i, math.min(n, i + 1499) do chunk[#chunk + 1] = cmds[k] end
            mon.batch(chunk)
            i = i + 1500
        end
    end

    function self.invalidate()
        lastBase, lastPos, winPrev, lastCursor = nil, nil, {}, nil
    end

    local function signature(sc)
        local p = { sc.W, sc.H, g.palVersion or 0, theme.name, sc.clock, sc.date, sc.alarms, sc.wall, sc.saver and "S" or "",
            sc.status and sc.status.left or "", sc.hint or "", sc.user or "" }
        local pos = {}
        for _, w in ipairs(sc.wins) do
            p[#p + 1] = table.concat({ w.id, w.w, w.h, w.title, w.active and 1 or 0, w.max and 1 or 0, w.grab or "", w.dragging and 1 or 0 }, ",")
            pos[w.id] = w.x .. "," .. w.y
        end
        for _, ic in ipairs(sc.icons) do p[#p + 1] = ic.id .. ic.x .. "," .. ic.y .. (ic.sel and "s" or "") .. ic.label end
        local tb = sc.taskbar
        p[#p + 1] = tb.start.x .. (tb.start.active and "A" or "a")
        for _, t in ipairs(tb.items) do p[#p + 1] = table.concat({ t.x, t.label, t.active and 1 or 0, t.min and 1 or 0, #t.wins, t.badge or 0 }, ",") end
        for _, t in ipairs(sc.tray.items) do p[#p + 1] = t.id .. t.x .. t.text .. (t.on and "!" or "") end
        if sc.menu then p[#p + 1] = "menu" .. sc.menu.x .. "," .. sc.menu.y .. "," .. #sc.menu.tiles .. sc.menu.search.text end
        if sc.quick then
            p[#p + 1] = "quick" .. sc.quick.y
            for _, r in ipairs(sc.quick.rows) do for _, b in ipairs(r.buttons or {}) do p[#p + 1] = b.label .. (b.on and "+" or "-") end end
        end
        if sc.center then
            p[#p + 1] = "center" .. sc.center.h
            for _, e in ipairs(sc.center.entries) do p[#p + 1] = e.id .. e.text end
        end
        if sc.switcher then
            p[#p + 1] = "sw" .. #sc.switcher.items .. (sc.switcher.label or "")
            for _, it in ipairs(sc.switcher.items) do p[#p + 1] = it.sel and "S" or "s" end
        end
        if sc.ctx then
            p[#p + 1] = "ctx" .. sc.ctx.x .. "," .. sc.ctx.y .. "," .. sc.ctx.w .. "," .. #sc.ctx.items .. sc.ctx.items[1].label
        end
        for _, t in ipairs(sc.toasts) do p[#p + 1] = t.id .. t.text .. t.y end
        return table.concat(p, "|"), pos
    end

    local function text(cmds, x, y, s, color, size)
        if s ~= "" then cmds[#cmds + 1] = { "text", x, y, s, color, size or g.font } end
    end

    local function clipText(s, cells)
        s = tostring(s)
        if #s > cells then return s:sub(1, math.max(0, cells)) end
        return s
    end

    local function badge(cmds, rx, y, n, color)
        local s = tostring(n)
        local w = #s * g.tw + 8
        gfx.rrect(cmds, rx - w, y, w, 10, 5, color or col(C.alarm))
        text(cmds, rx - w + 4, y + 1, s, col(C.selText))
        return w
    end

    local function intersects(a, b)
        return a[1] < b[1] + b[3] and b[1] < a[1] + a[3] and a[2] < b[2] + b[4] and b[2] < a[2] + a[4]
    end

    local function pill(cmds, x, y, w, h, label, pal, on, danger, bg)
        local back = on and pal.accent or (danger and pal.close or (bg or pal.light))
        gfx.rrect(cmds, x, y, w, h, floor(h / 2), back)
        cmds[#cmds + 1] = { "ctext", x, y, w, h, label, (on or danger) and col(C.selText) or pal.text, g.font }
    end

    local function cardBox(cmds, x, y, wp, hp, pal, r)
        r = r or 8
        gfx.shadow(cmds, x, y, wp, hp, 1.6)
        gfx.rrect(cmds, x - 1, y - 1, wp + 2, hp + 2, r + 1, gfx.mix(pal.panel, pal.text, 0.14))
        gfx.rrect(cmds, x, y, wp, hp, r, pal.panel)
    end

    local function drawWallpaper(cmds, sc, pal, region)
        local W, H = g.cols * g.cw, g.rows * g.ch
        local top = pal.desk
        local style = sc.wall
        local bottom = top
        if not region then
            cmds[#cmds + 1] = { "clear", top }
            if style == "grid" then
                local gc = gfx.mix(bottom, pal.text, pal.dark and 0.08 or 0.12)
                for cx = 0, g.cols, 4 do cmds[#cmds + 1] = { "fill", g.ox + cx * g.cw + 1, g.oy + 1, 1, H, gc } end
                for cy = 0, g.rows, 2 do cmds[#cmds + 1] = { "fill", g.ox + 1, g.oy + cy * g.ch + 1, W, 1, gc } end
            end
            return
        end
        local x, y, w, h = region[1], region[2], region[3], region[4]
        cmds[#cmds + 1] = { "fill", x, y, w, h, top }
        if style == "grid" then
            local gc = gfx.mix(bottom, pal.text, pal.dark and 0.08 or 0.12)
            for cx = 0, g.cols, 4 do
                local lx = g.ox + cx * g.cw + 1
                if lx >= x and lx < x + w then cmds[#cmds + 1] = { "fill", lx, y, 1, h, gc } end
            end
            for cy = 0, g.rows, 2 do
                local ly = g.oy + cy * g.ch + 1
                if ly >= y and ly < y + h then cmds[#cmds + 1] = { "fill", x, ly, w, 1, gc } end
            end
        end
    end

    local function drawWidget(cmds, sc, pal, region)
        local w = sc.widget
        if not w then return end
        if region and not intersects({ X(w.x), Y(w.y), 24 * g.cw, 6 * g.ch }, region) then return end
        local x, y = X(w.x), Y(w.y)
        cmds[#cmds + 1] = { "text", x, y, sc.clock, pal.text, 32 }
        text(cmds, x + 2, y + 40, sc.date, pal.dim)
    end

    local function drawIcons(cmds, sc, pal, region)
        for _, ic in ipairs(sc.icons) do
            local x, y, w, h = X(ic.x), Y(ic.y), ic.w * g.cw, ic.h * g.ch
            if not region or intersects({ x, y, w, h }, region) then
                if ic.sel then
                    gfx.rrect(cmds, x, y, w, h, 8, gfx.mix(pal.accent, pal.text, 0.2))
                    gfx.rrect(cmds, x + 1, y + 1, w - 2, h - 2, 7, gfx.mix(pal.desk, pal.accent, 0.45))
                end
                icons.draw(cmds, ic.id, ic.icon, x + floor((w - 26) / 2), y + 2, 26, col)
                local label = clipText(ic.label, ic.w)
                text(cmds, x + floor((w - #label * g.tw) / 2) + 1, y + 31, label, 0x000000)
                text(cmds, x + floor((w - #label * g.tw) / 2), y + 30, label, pal.text)
            end
        end
    end

    local function drawTray(cmds, sc, pal)
        local tr = sc.tray
        local y0 = Y(tr.y)
        local h = (tr.inDock and sc.tb or 1) * g.ch
        for _, it in ipairs(tr.items) do
            local x, w = X(it.x), it.w * g.cw
            local cy = y0 + floor((h - g.ch) / 2)
            if it.id == "clock" then
                if it.on then gfx.rrect(cmds, x, cy + 1, w, g.ch - 2, floor((g.ch - 2) / 2), pal.accent) end
                cmds[#cmds + 1] = { "ctext", x, cy, w, g.ch, it.text, it.on and col(C.selText) or pal.text, g.font }
            elseif it.id == "date" then
                cmds[#cmds + 1] = { "ctext", x, cy, w, g.ch, it.text, pal.dim, g.font }
            elseif it.id == "bell" then
                if it.on then gfx.rrect(cmds, x, cy + 1, w, g.ch - 2, floor((g.ch - 2) / 2), pal.accent) end
                local s = g.ch - 2
                icons.sys(cmds, "bell", x + 2, cy + 1, s, it.on and col(C.selText) or (it.count > 0 and pal.text or pal.dim))
                if it.count > 0 then badge(cmds, x + w, cy - 1, it.count) end
            elseif it.id == "alarm" then
                gfx.rrect(cmds, x, cy + 1, w, g.ch - 2, floor((g.ch - 2) / 2), pal.close)
                cmds[#cmds + 1] = { "ctext", x, cy, w, g.ch, "! " .. it.text, col(C.selText), g.font }
            end
        end
    end

    local function drawStatus(cmds, sc, pal, region)
        if sc.sb <= 0 then return end
        if region and not intersects({ g.ox, Y(1), g.cols * g.cw, g.ch }, region) then return end
        local y, h = Y(1), g.ch
        cmds[#cmds + 1] = { "afill", g.ox + 1, y, g.cols * g.cw, h, pal.panel, 0.9 }
        cmds[#cmds + 1] = { "fill", g.ox + 1, y + h - 1, g.cols * g.cw, 1, gfx.mix(pal.panel, pal.text, 0.1) }
        cmds[#cmds + 1] = { "disc", X(2) + 2, y + 4.5, 2.5, pal.accent }
        text(cmds, X(2) + 8, y + 1, "SQ-OS", pal.accent2)
        local first = sc.tray.items[1]
        local limit = first and first.x or sc.W
        if sc.status and sc.status.left then
            text(cmds, X(2) + 8 + 6 * g.cw, y + 1, clipText(sc.status.left, math.max(0, limit - 10)), pal.dim)
        end
        drawTray(cmds, sc, pal)
    end

    local function titleButton(cmds, c, y, kind, pal, active, tcol)
        local bx = X(c) + floor(3 * g.cw / 2) - 4
        local by = y + floor((g.ch - 8) / 2)
        local fg = active and pal.titleText or pal.dim
        if kind == "close" then
            gfx.rrect(cmds, bx, by, 8, 8, 4, active and pal.close or gfx.mix(tcol, pal.text, 0.2))
            if active then icons.sys(cmds, "close", bx - 1, by - 1, 10, col(C.selText)) end
        else
            gfx.rrect(cmds, bx, by, 8, 8, 4, gfx.mix(tcol, pal.text, active and 0.25 or 0.12))
            if kind == "max" then
                cmds[#cmds + 1] = { "fill", bx + 2, by + 2, 4, 4, fg }
                cmds[#cmds + 1] = { "fill", bx + 3, by + 3, 2, 2, tcol }
            else
                cmds[#cmds + 1] = { "fill", bx + 2, by + 5, 4, 1, fg }
            end
        end
    end

    local function winFrame(cmds, w, pal)
        local x, y, wp, hp = X(w.x), Y(w.y), w.w * g.cw, w.h * g.ch
        gfx.shadow(cmds, x, y, wp, hp, w.active and 1.3 or 0.8)
        local border = w.grab and pal.accent or (w.active and gfx.mix(pal.face, pal.accent, 0.55) or gfx.mix(pal.face, pal.text, 0.14))
        gfx.rrect(cmds, x - 1, y - 1, wp + 2, hp + 2, 8, border)
        gfx.rrect(cmds, x, y, wp, hp, 7, pal.face)
        local tcol = w.grab and gfx.mix(pal.title, pal.accent, 0.5) or (w.active and pal.title or pal.titleOff)
        gfx.rrect(cmds, x, y, wp, g.ch + 4, 7, tcol)
        cmds[#cmds + 1] = { "fill", x, y + g.ch, wp, 4, pal.face }
        cmds[#cmds + 1] = { "fill", x + 3, y + g.ch + 3, wp - 6, 1, gfx.mix(pal.face, pal.text, 0.08) }
        if w.active then cmds[#cmds + 1] = { "fill", x + 3, y + g.ch + 3, wp - 6, 1, gfx.mix(pal.face, pal.accent, 0.35) } end
        local gc = w.active and pal.titleText or pal.dim
        local tx = x + 5
        local c, m, n = w.buttons[1], w.buttons[2], w.buttons[3]
        local right = w.x + w.w
        if c > 0 then titleButton(cmds, c, y, "close", pal, w.active, tcol) right = c end
        if m > 0 then titleButton(cmds, m, y, "max", pal, w.active, tcol) right = math.min(right, m) end
        if n > 0 then titleButton(cmds, n, y, "min", pal, w.active, tcol) right = math.min(right, n) end
        if w.icon then
            icons.draw(cmds, w.app and w.app.id or "", w.icon, tx, y + 1, 8, col)
            tx = tx + 12
        end
        text(cmds, tx, y + 1, clipText(w.title, floor((X(right) - tx - 4) / g.tw)), gc)
    end

    local function drawGrip(cmds, w, pal)
        if w.fixed or w.max then return end
        local x2, y2 = X(w.x + w.w), Y(w.y + w.h)
        for i = 1, 3 do
            cmds[#cmds + 1] = { "line", x2 - 2 - i * 3, y2 - 3, x2 - 3, y2 - 2 - i * 3, pal.dim, 1 }
        end
    end

    local function drawDock(cmds, sc, pal)
        local tb = sc.taskbar
        local y, h = Y(tb.y), tb.h * g.ch
        local bar = gfx.mix(pal.panel, pal.desk, pal.dark and 0.4 or 0)
        local x0, x1 = X(tb.x0) - 4, X(tb.x1 + 1) + 3
        gfx.rrect(cmds, x0 - 1, y + 1, x1 - x0 + 2, h - 2, 9, gfx.mix(bar, pal.text, 0.14))
        gfx.rrect(cmds, x0, y + 2, x1 - x0, h - 4, 8, bar)
        local s = tb.start
        local sx = X(s.x)
        if s.active then gfx.rrect(cmds, sx + 1, y + 2, s.w * g.cw - 2, h - 4, 5, pal.light) end
        local cx, cy = sx + floor(s.w * g.cw / 2), y + floor(h / 2)
        local q = { { -6, -6, pal.accent2 }, { 1, -6, pal.accent }, { -6, 1, pal.accent }, { 1, 1, pal.accent2 } }
        for _, d in ipairs(q) do gfx.rrect(cmds, cx + d[1], cy + d[2], 6, 6, 2, d[3]) end
        for _, t in ipairs(tb.items) do
            local tx = X(t.x)
            local tw = t.w * g.cw
            if t.active then gfx.rrect(cmds, tx + 1, y + 2, tw - 2, h - 4, 5, pal.light) end
            local app = t.app
            local ix = tx + floor((tw - 14) / 2)
            icons.draw(cmds, app and app.id or "", t.icon, ix, y + 3, 14, col)
            local n = #t.wins
            if t.active then
                gfx.rrect(cmds, tx + floor((tw - 10) / 2), y + h - 4, 10, 2, 1, pal.accent)
            elseif n > 0 then
                local k = math.min(3, n)
                local dx = tx + floor((tw - (k * 4 - 1)) / 2)
                for i = 1, k do cmds[#cmds + 1] = { "fill", dx + (i - 1) * 4, y + h - 4, 3, 2, t.min and gfx.mix(bar, pal.dim, 0.5) or pal.dim } end
            end
            if t.badge then badge(cmds, ix + 18, y + 1, math.min(99, t.badge)) end
        end
        if sc.tray.inDock then
            cmds[#cmds + 1] = { "afill", X(sc.tray.items[1] and sc.tray.items[1].x or sc.W) - 4, y + 2, sc.tray.w * g.cw + 6, h - 4, bar, 0.85 }
            drawTray(cmds, sc, pal)
        end
    end

    local function drawMenu(cmds, sc, pal)
        local m = sc.menu
        if not m then return end
        local x, y, wp, hp = X(m.x), Y(m.y), m.w * g.cw, m.h * g.ch
        cardBox(cmds, x, y, wp, hp, pal)
        text(cmds, x + 8, y + 1, m.title, pal.dim)
        local sf = m.search
        local fx, fy, fw = X(sf.x), Y(sf.y), sf.w * g.cw
        gfx.rrect(cmds, fx, fy, fw, g.ch, floor(g.ch / 2), pal.light)
        icons.sys(cmds, "search", fx + 5, fy + 1, g.ch - 2, pal.dim)
        local shown = sf.text ~= "" and (sf.text .. "_") or sf.hint
        text(cmds, fx + g.ch + 6, fy + 1, clipText(shown, floor((fw - g.ch - 10) / g.tw)), sf.text ~= "" and pal.text or pal.dim)
        if m.empty then text(cmds, x + 10, Y(m.y + 3) + 2, m.empty, pal.dim) end
        for _, t in ipairs(m.tiles) do
            local tx, ty, tw, th = X(t.x), Y(t.y), t.w * g.cw, t.h * g.ch
            gfx.rrect(cmds, tx, ty, tw, th, 8, gfx.mix(pal.light, pal.text, t.first and sf.text ~= "" and 0.3 or 0.1))
            gfx.rrect(cmds, tx + 1, ty + 1, tw - 2, th - 2, 7, pal.light)
            icons.draw(cmds, t.app.id, t.icon, tx + 5, ty + floor((th - 20) / 2), 20, col)
            text(cmds, tx + 30, ty + floor((th - 8) / 2), clipText(t.label, floor((tw - 32) / g.tw)), pal.text)
        end
        if m.user then
            local ux, uy = X(m.user.x), Y(m.user.y)
            icons.sys(cmds, "user", ux + 1, uy, g.ch, pal.dim)
            text(cmds, ux + g.ch + 4, uy + 1, clipText(m.user.text, math.max(0, m.user.w - 3)), pal.dim)
        end
        for _, b in ipairs(m.buttons or {}) do
            pill(cmds, X(b.x), Y(b.y), b.w * g.cw, b.h * g.ch, b.label, pal, false, b.danger)
        end
    end

    local function kindColor(kind, pal)
        if kind == "ok" then return col(C.ok) end
        if kind == "warn" then return col(C.warn) end
        if kind == "alarm" then return col(C.alarm) end
        return pal.accent
    end

    local function drawToasts(cmds, sc, pal)
        for _, t in ipairs(sc.toasts) do
            local x, y, wp, hp = X(t.x), Y(t.y), t.w * g.cw, t.h * g.ch
            gfx.shadow(cmds, x, y, wp, hp, 1.2)
            gfx.rrect(cmds, x - 1, y - 1, wp + 2, hp + 2, 9, gfx.mix(pal.panel, pal.text, 0.12))
            gfx.rrect(cmds, x, y, wp, hp, 8, pal.panel)
            gfx.rrect(cmds, x, y + 3, 3, hp - 6, 1, kindColor(t.kind, pal))
            local cells = floor((wp - 14) / g.cw)
            text(cmds, x + 9, y + 2, clipText(t.title or "SQ-OS", cells), pal.dim)
            text(cmds, x + 9, y + 11, clipText(t.text, cells), pal.text)
        end
    end

    local function drawQuick(cmds, sc, pal)
        local q = sc.quick
        if not q then return end
        local x, y, wp, hp = X(q.x), Y(q.y), q.w * g.cw, q.h * g.ch
        cardBox(cmds, x, y, wp, hp, pal)
        text(cmds, x + 8, y + 1, q.title, pal.dim)
        for _, r in ipairs(q.rows) do
            if r.label and r.label ~= "" then text(cmds, X(q.x + 1), Y(r.y) + 1, r.label, pal.dim) end
            for _, b in ipairs(r.buttons or {}) do
                local bx, by, bw = X(b.x), Y(b.y), b.w * g.cw
                if b.rgb then
                    local cx = bx + floor(bw / 2)
                    cmds[#cmds + 1] = { "disc", cx, by + g.ch / 2, g.ch / 2 - 1, b.rgb }
                    if b.on then icons.sys(cmds, "check", cx - 5, by + 1, 10, col(C.selText)) end
                else
                    pill(cmds, bx, by + 1, bw, g.ch - 2, b.label, pal, b.on, b.danger)
                end
            end
        end
    end

    local function drawCenter(cmds, sc, pal)
        local nc = sc.center
        if not nc then return end
        local x, y, wp, hp = X(nc.x), Y(nc.y), nc.w * g.cw, nc.h * g.ch
        cardBox(cmds, x, y, wp, hp, pal)
        text(cmds, x + 8, y + 1, nc.title, pal.dim)
        if nc.empty then text(cmds, x + 8, y + g.ch + 4, nc.empty, pal.dim) end
        for _, e in ipairs(nc.entries) do
            local ex, ey, ew, eh = X(e.x), Y(e.y), e.w * g.cw, e.h * g.ch
            gfx.rrect(cmds, ex, ey, ew, eh, 6, pal.light)
            gfx.rrect(cmds, ex + 2, ey + 3, 3, eh - 6, 1, kindColor(e.kind, pal))
            local cells = floor((ew - 14) / g.tw)
            text(cmds, ex + 9, ey + 2, clipText(e.title, cells - 6), pal.dim)
            if e.clock then text(cmds, ex + ew - #e.clock * g.tw - 4, ey + 2, e.clock, pal.dim) end
            text(cmds, ex + 9, ey + 11, clipText(e.text, cells), pal.text)
        end
        if nc.clear then pill(cmds, X(nc.clear.x), Y(nc.clear.y) + 1, nc.clear.w * g.cw, g.ch - 2, nc.clear.label, pal) end
    end

    local function drawSwitcher(cmds, sc, pal)
        local s = sc.switcher
        if not s then return end
        local x, y, wp, hp = X(s.x), Y(s.y), s.w * g.cw, s.h * g.ch
        cardBox(cmds, x, y, wp, hp, pal, 10)
        for _, it in ipairs(s.items) do
            local ix, iy, iw, ih = X(it.x), Y(it.y), it.w * g.cw, it.h * g.ch
            if it.sel then gfx.rrect(cmds, ix, iy, iw, ih, 8, gfx.mix(pal.light, pal.accent, 0.5)) end
            icons.draw(cmds, it.app and it.app.id or "", it.icon, ix + floor((iw - 24) / 2), iy + floor((ih - 24) / 2), 24, col)
        end
        cmds[#cmds + 1] = { "ctext", x, y + hp - g.ch - 4, wp, g.ch, s.label or "", pal.text, g.font }
    end

    local function drawContext(cmds, sc, pal)
        local c = sc.ctx
        if not c then return end
        local x, y, wp, hp = X(c.x), Y(c.y) - 2, c.w * g.cw, c.h * g.ch + 4
        gfx.shadow(cmds, x, y, wp, hp, 1.4)
        gfx.rrect(cmds, x - 1, y - 1, wp + 2, hp + 2, 6, gfx.mix(pal.panel, pal.text, 0.16))
        gfx.rrect(cmds, x, y, wp, hp, 5, pal.panel)
        for _, it in ipairs(c.items) do
            text(cmds, x + 8, Y(it.y) + 1, clipText(it.label, c.w - 2), it.danger and col(C.alarm) or pal.text)
        end
    end

    local function drawHint(cmds, sc, pal)
        if not sc.hint then return end
        local s = sc.hint
        local wp = #s * g.tw + 16
        local x = g.ox + floor((sc.W * g.cw - wp) / 2)
        local y = Y(sc.bottom) - 2
        gfx.rrect(cmds, x, y, wp, g.ch + 2, floor((g.ch + 2) / 2), pal.accent)
        cmds[#cmds + 1] = { "ctext", x, y, wp, g.ch + 2, s, col(C.selText), g.font }
    end

    local function drawOverlays(cmds, sc, pal)
        drawDock(cmds, sc, pal)
        drawToasts(cmds, sc, pal)
        drawMenu(cmds, sc, pal)
        drawQuick(cmds, sc, pal)
        drawCenter(cmds, sc, pal)
        drawSwitcher(cmds, sc, pal)
        drawContext(cmds, sc, pal)
        drawHint(cmds, sc, pal)
    end

    local function inBox(b, cx, cy)
        return b and cx >= b.x and cx < b.x + b.w and cy >= b.y and cy < b.y + b.h
    end

    local function covered(sc, wi, cx, cy)
        for i = wi + 1, #sc.wins do
            if inBox(sc.wins[i], cx, cy) then return true end
        end
        if inBox(sc.menu, cx, cy) or inBox(sc.quick, cx, cy) or inBox(sc.center, cx, cy) or inBox(sc.switcher, cx, cy) then return true end
        for _, t in ipairs(sc.toasts) do
            if inBox(t, cx, cy) then return true end
        end
        local c = sc.ctx
        if c and cx >= c.x - 0 and cx < c.x + c.w and cy >= c.y - 1 and cy <= c.y + c.h then return true end
        if sc.hint and cy == sc.bottom then return true end
        return false
    end

    local function paintCells(cmds, sc, wi, w, full, pal, forced, region)
        local canvas = w.canvas
        if not canvas.getLine then return end
        local _, ch = canvas.getSize()
        local prev = winPrev[w.id]
        if not prev or full then prev = {} winPrev[w.id] = prev end
        local faceHex = ui.hex(C.face)
        local function skip(cx, cy)
            return region and not (X(cx) < region[1] + region[3] and region[1] < X(cx) + g.cw and Y(cy) < region[2] + region[4] and region[2] < Y(cy) + g.ch)
        end
        local touchedGrip = false
        for r = 1, ch do
            local t, f, b = canvas.getLine(r)
            local rowPrev = prev[r]
            local same = rowPrev and rowPrev[1] == t and rowPrev[2] == f and rowPrev[3] == b
            local cy = w.y + r
            local fr = forced and forced[cy]
            if full or not same or fr then
                local cw = #t
                local x = 1
                while x <= cw do
                    local cx = w.x + x - 1
                    local ch0, fc0, bc0 = t:sub(x, x), f:sub(x, x), b:sub(x, x)
                    local dirty = full and not skip(cx, cy)
                    if not dirty then
                        if fr and fr[cx] then dirty = true
                        elseif rowPrev then
                            dirty = rowPrev[1]:sub(x, x) ~= ch0 or rowPrev[2]:sub(x, x) ~= fc0 or rowPrev[3]:sub(x, x) ~= bc0
                        else dirty = true end
                    end
                    if dirty and not full and covered(sc, wi, cx, cy) then dirty = false end
                    if dirty and r == ch and x == cw then touchedGrip = true end
                    local pill
                    if dirty and bc0 ~= faceHex then
                        local a2, z = x, x
                        while a2 > 1 and b:sub(a2 - 1, a2 - 1) == bc0 do a2 = a2 - 1 end
                        while z < cw and b:sub(z + 1, z + 1) == bc0 do z = z + 1 end
                        local _, _, ub = canvas.getLine(math.max(1, r - 1))
                        local _, _, db = canvas.getLine(math.min(ch, r + 1))
                        local single = z > a2 and not (r > 1 and ub:sub(a2, z):find(bc0, 1, true)) and not (r < ch and db:sub(a2, z):find(bc0, 1, true))
                        if single and not full then
                            for k = a2, z do if covered(sc, wi, w.x + k - 1, cy) then single = false break end end
                        end
                        if single then pill = { a2, z } x = a2 end
                    end
                    if pill then
                        local x2 = pill[2]
                        if not full then cmds[#cmds + 1] = { "fill", X(w.x + x - 1), Y(cy), (x2 - x + 1) * g.cw, g.ch, g.palette[tonumber(faceHex, 16) + 1] } end
                        gfx.rrect(cmds, X(w.x + x - 1), Y(cy) + 1, (x2 - x + 1) * g.cw, g.ch - 2, floor((g.ch - 2) / 2), g.palette[tonumber(bc0, 16) + 1])
                        for xx = x, x2 do
                            local c = t:sub(xx, xx)
                            if c ~= " " and c ~= "" then
                                local byte = c:byte()
                                if byte > 126 or byte < 32 then c = "?" end
                                cmds[#cmds + 1] = { "text", X(w.x + xx - 1), Y(cy) + 1, c, g.palette[tonumber(f:sub(xx, xx), 16) + 1], g.font }
                            end
                        end
                        x = x2 + 1
                    elseif dirty then
                        local x2 = x
                        while x2 < cw do
                            local nx = x2 + 1
                            local ncx = w.x + nx - 1
                            local nd = full and not skip(ncx, cy)
                            if not nd then
                                if fr and fr[ncx] then nd = true
                                elseif rowPrev then
                                    nd = rowPrev[1]:sub(nx, nx) ~= t:sub(nx, nx) or rowPrev[2]:sub(nx, nx) ~= f:sub(nx, nx) or rowPrev[3]:sub(nx, nx) ~= b:sub(nx, nx)
                                else nd = true end
                                if nd and covered(sc, wi, ncx, cy) then nd = false end
                            end
                            if not nd or b:sub(nx, nx) ~= bc0 then break end
                            x2 = nx
                        end
                        if not (full and bc0 == faceHex) then
                            cmds[#cmds + 1] = { "fill", X(cx), Y(cy), (x2 - x + 1) * g.cw, g.ch, g.palette[tonumber(bc0, 16) + 1] }
                        end
                        if bc0 ~= faceHex and x2 > x then
                            local ub = r > 1 and select(3, canvas.getLine(r - 1)) or ""
                            local db = r < ch and select(3, canvas.getLine(r + 1)) or ""
                            local px0, py0, rad = X(cx), Y(cy), 5
                            local fcol, ccol = g.palette[tonumber(faceHex, 16) + 1], g.palette[tonumber(bc0, 16) + 1]
                            local lo, ro = x == 1 or b:sub(x - 1, x - 1) ~= bc0, x2 == cw or b:sub(x2 + 1, x2 + 1) ~= bc0
                            local function corner(ax, ay)
                                cmds[#cmds + 1] = { "fill", ax, ay, rad, rad, fcol }
                                return ax, ay
                            end
                            local rx = px0 + (x2 - x + 1) * g.cw - rad
                            local by = py0 + g.ch - rad
                            if ub:sub(x, x) ~= bc0 and lo then corner(px0, py0) cmds[#cmds + 1] = { "disc", px0 + rad - 0.5, py0 + rad - 0.5, rad, ccol } end
                            if ub:sub(x2, x2) ~= bc0 and ro then corner(rx, py0) cmds[#cmds + 1] = { "disc", rx - 0.5 + 0, py0 + rad - 0.5, rad, ccol } end
                            if db:sub(x, x) ~= bc0 and lo then corner(px0, by) cmds[#cmds + 1] = { "disc", px0 + rad - 0.5, by - 0.5, rad, ccol } end
                            if db:sub(x2, x2) ~= bc0 and ro then corner(rx, by) cmds[#cmds + 1] = { "disc", rx - 0.5, by - 0.5, rad, ccol } end
                        end
                        for xx = x, x2 do
                            local c = t:sub(xx, xx)
                            if c ~= " " and c ~= "" then
                                local byte = c:byte()
                                if byte > 126 or byte < 32 then c = "?" end
                                cmds[#cmds + 1] = { "text", X(w.x + xx - 1), Y(cy) + 1, c, g.palette[tonumber(f:sub(xx, xx), 16) + 1], g.font }
                            end
                        end
                        x = x2 + 1
                    else
                        x = x + 1
                    end
                end
                prev[r] = { t, f, b }
            end
        end
        return touchedGrip
    end

    local function palette()
        local pal = {
            desk = col(C.desk), face = col(C.face), panel = col(C.panel), light = col(C.light), text = col(C.text),
            dim = col(C.dim), accent = col(C.accent), accent2 = col(C.accent2), title = col(C.title),
            titleOff = col(C.titleOff), titleText = col(C.titleText), close = col(C.close),
        }
        pal.dark = gfx.lum(pal.face) < 0.5
        gfx.flat = theme.name == "console"
        return pal
    end

    local function winRect(x, y, w, h)
        return { X(x) - 6, Y(y) - 6, w * g.cw + 12, h * g.ch + 14 }
    end

    local function clipTo(cmds, r)
        local out = {}
        for _, c in ipairs(cmds) do
            if c[1] == "afill" then
                local x1, y1 = math.max(c[2], r[1]), math.max(c[3], r[2])
                local x2, y2 = math.min(c[2] + c[4], r[1] + r[3]), math.min(c[3] + c[5], r[2] + r[4])
                if x2 > x1 and y2 > y1 then out[#out + 1] = { "afill", x1, y1, x2 - x1, y2 - y1, c[6], c[7] } end
            elseif c[1] == "glow" then
            else
                out[#out + 1] = c
            end
        end
        return out
    end

    local function movedWindows(sc, pos)
        local n, mover = 0, nil
        for _, w in ipairs(sc.wins) do
            if lastPos[w.id] ~= pos[w.id] then n = n + 1 mover = w end
        end
        return n, mover
    end

    local function fastMove(cmds, sc, pal, mover, prevPos)
        local ox, oy = prevPos[mover.id]:match("^(%-?%d+),(%-?%d+)$")
        local old = winRect(tonumber(ox), tonumber(oy), mover.w, mover.h)
        local new = winRect(mover.x, mover.y, mover.w, mover.h)
        local x1, y1 = math.min(old[1], new[1]), math.min(old[2], new[2])
        local x2, y2 = math.max(old[1] + old[3], new[1] + new[3]), math.max(old[2] + old[4], new[2] + new[4])
        local r = { math.max(1, x1), math.max(1, y1), x2 - math.max(1, x1), y2 - math.max(1, y1) }
        local sub = {}
        drawWallpaper(sub, sc, pal, r)
        drawWidget(sub, sc, pal, r)
        drawIcons(sub, sc, pal, r)
        drawStatus(sub, sc, pal, r)
        for wi, w in ipairs(sc.wins) do
            if intersects(winRect(w.x, w.y, w.w, w.h), r) then
                winFrame(sub, w, pal)
                if not w.dragging then paintCells(sub, sc, wi, w, true, pal, nil, r) end
                drawGrip(sub, w, pal)
            end
        end
        drawOverlays(sub, sc, pal)
        for _, c in ipairs(clipTo(sub, r)) do cmds[#cmds + 1] = c end
    end

    function self.present(sc)
        local pal = palette()
        local base, pos = signature(sc)
        local cmds = {}
        local cur = sc.cursor
        local mode = "full"
        local mover
        if lastBase == base and lastPos then
            local n, m = movedWindows(sc, pos)
            if n == 0 then mode = "diff" elseif n == 1 then mode = "move" mover = m end
        end
        local prevPos = lastPos
        lastBase, lastPos = base, pos
        local full = mode == "full"
        if sc.saver then
            if full then
                local W, H = g.cols * g.cw, g.rows * g.ch
                cmds[#cmds + 1] = { "clear", gfx.mix(pal.desk, 0x000000, 0.5) }
                cmds[#cmds + 1] = { "ctext", g.ox + 1, g.oy + floor(H / 2) - 40, W, 48, sc.clock, pal.text, 48 }
                cmds[#cmds + 1] = { "ctext", g.ox + 1, g.oy + floor(H / 2) + 14, W, g.ch, sc.date, pal.dim, g.font }
                flush(cmds)
            end
            lastCursor = nil
            return
        end
        if mode == "move" then
            winPrev = winPrev or {}
            fastMove(cmds, sc, pal, mover, prevPos)
        end
        if full then
            winPrev = {}
            drawWallpaper(cmds, sc, pal)
            drawWidget(cmds, sc, pal)
            drawIcons(cmds, sc, pal)
            drawStatus(cmds, sc, pal)
            for wi, w in ipairs(sc.wins) do
                winFrame(cmds, w, pal)
                if not w.dragging then paintCells(cmds, sc, wi, w, true, pal) end
                drawGrip(cmds, w, pal)
            end
            drawOverlays(cmds, sc, pal)
        else
            local forced
            if lastCursor and (not cur or cur.x ~= lastCursor.x or cur.y ~= lastCursor.y) then
                forced = { [lastCursor.y] = { [lastCursor.x] = true } }
            end
            for wi, w in ipairs(sc.wins) do
                if not w.dragging and paintCells(cmds, sc, wi, w, false, pal, forced) then drawGrip(cmds, w, pal) end
            end
        end
        local same = cur and lastCursor and cur.x == lastCursor.x and cur.y == lastCursor.y
        if cur and not (same and #cmds == 0) then
            cmds[#cmds + 1] = { "fill", X(cur.x), Y(cur.y) + g.ch - 2, g.cw, 2, col(cur.color or colors.white) }
        end
        lastCursor = cur and { x = cur.x, y = cur.y } or nil
        flush(cmds)
    end

    function self.animate(kind, w)
        local pal = palette()
        local x, y, wp, hp = X(w.x), Y(w.y), w.w * g.cw, w.h * g.ch
        local cx, cy = x + wp / 2, y + hp / 2
        for i = 1, 3 do
            local s = kind == "open" and (0.4 + 0.2 * i) or (1 - 0.2 * i)
            local fw, fh = floor(wp * s), floor(hp * s)
            local cmds = {}
            gfx.frame(cmds, floor(cx - fw / 2), floor(cy - fh / 2), fw, fh, pal.accent2)
            flush(cmds)
        end
    end

    return self
end

return M
