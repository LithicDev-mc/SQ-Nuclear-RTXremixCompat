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
    local lastBase, lastCursor
    local winPrev, graphPrev, graphGeomPrev = {}, {}, {}

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
        lastBase, winPrev, graphPrev, graphGeomPrev, lastCursor = nil, {}, {}, {}, nil
    end

    local function signature(sc)
        local p = { sc.W, sc.H, g.palVersion or 0, theme.name, sc.clock, sc.date, sc.alarms, sc.saver and "S" or "",
            sc.status and sc.status.left or "", sc.hint or "", sc.user or "", sc.keys and (sc.keys.text .. sc.keys.view.label) or "" }
        for _, w in ipairs(sc.wins) do
            p[#p + 1] = table.concat({ w.id, w.x, w.y, w.w, w.h, w.title, w.active and 1 or 0, w.max and 1 or 0, w.grab and 1 or 0 }, ",")
        end
        local tb = sc.taskbar
        p[#p + 1] = tb.start.active and "A" or "a"
        for _, t in ipairs(tb.items) do p[#p + 1] = table.concat({ t.y, t.label, t.active and 1 or 0, t.min and 1 or 0, #t.wins, t.badge or 0 }, ",") end
        for _, t in ipairs(sc.tray.items) do p[#p + 1] = t.id .. t.x .. t.text .. (t.on and "!" or "") end
        if sc.menu then
            p[#p + 1] = "menu" .. sc.menu.x .. "," .. sc.menu.y .. "," .. sc.menu.search.text
            for _, t in ipairs(sc.menu.tiles) do p[#p + 1] = t.id end
        end
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
        return table.concat(p, "|")
    end

    local function text(cmds, x, y, s, color, size)
        if s ~= "" then cmds[#cmds + 1] = { "text", x, y, s, color, size or g.font } end
    end

    local function clipText(s, cells)
        s = tostring(s)
        if #s > cells then return s:sub(1, math.max(0, cells)) end
        return s
    end

    local function badge(cmds, rx, y, n, pal)
        local s = tostring(n)
        local w = #s * g.tw + 8
        gfx.rrect(cmds, rx - w, y, w, 10, 5, pal.close)
        text(cmds, rx - w + 4, y + 1, s, pal.text)
        return w
    end

    local function pill(cmds, x, y, w, h, label, pal, on, danger, bg)
        if pal.console then label = "[" .. label .. "]" end
        if not on then gfx.rrect(cmds, x, y, w, h, 3, danger and pal.close or pal.edge) end
        gfx.rrect(cmds, x + (on and 0 or 1), y + (on and 0 or 1), w - (on and 0 or 2), h - (on and 0 or 2), on and 3 or 2, on and pal.accent or (bg or pal.light))
        cmds[#cmds + 1] = { "ctext", x, y, w, h, label, on and pal.selText or (danger and pal.close or pal.text), g.font }
    end

    local function cardBox(cmds, x, y, wp, hp, pal)
        if not pal.console then gfx.shadow(cmds, x, y, wp, hp, 1.6) end
        gfx.rrect(cmds, x - 1, y - 1, wp + 2, hp + 2, 5, pal.console and pal.text or pal.edge)
        gfx.rrect(cmds, x, y, wp, hp, 4, pal.panel)
    end

    local function cardTitle(cmds, x, y, s, pal)
        s = s:upper()
        text(cmds, x, y, pal.console and ("[ " .. s .. " ]") or s, pal.accent)
    end

    local function drawTray(cmds, sc, pal)
        local tr = sc.tray
        local cy = Y(tr.y)
        for _, it in ipairs(tr.items) do
            local x, w = X(it.x), it.w * g.cw
            if it.id == "clock" then
                if it.on then gfx.rrect(cmds, x, cy + 1, w, g.ch - 2, 3, pal.accent) end
                cmds[#cmds + 1] = { "ctext", x, cy, w, g.ch, it.text, it.on and pal.selText or pal.text, g.font }
            elseif it.id == "date" then
                cmds[#cmds + 1] = { "ctext", x, cy, w, g.ch, it.text, pal.dim, g.font }
            elseif it.id == "bell" then
                if it.on then gfx.rrect(cmds, x, cy + 1, w, g.ch - 2, 3, pal.accent) end
                icons.sys(cmds, "bell", x + 2, cy + 1, g.ch - 2, it.on and pal.selText or (it.count > 0 and pal.text or pal.dim))
                if it.count > 0 then badge(cmds, x + w, cy - 1, it.count, pal) end
            elseif it.id == "alarm" then
                gfx.rrect(cmds, x, cy + 1, w, g.ch - 2, 3, pal.close)
                cmds[#cmds + 1] = { "ctext", x, cy, w, g.ch, "! " .. it.text, pal.selText, g.font }
            end
        end
    end

    local function drawStatus(cmds, sc, pal)
        local y, h, W = Y(1), g.ch, g.cols * g.cw
        cmds[#cmds + 1] = { "fill", 1, 1, W + 2 * g.ox, y + h - 1, pal.panel }
        cmds[#cmds + 1] = { "fill", 1, y + h - 1, W + 2 * g.ox, 1, pal.edge }
        text(cmds, X(2), y + 1, "SQ Con", pal.accent)
        local sf = sc.status.search
        local limit = sf and sf.x or (sc.tray.items[1] and sc.tray.items[1].x or sc.W)
        text(cmds, X(9), y + 1, clipText(sc.status.left, math.max(0, limit - 10)), pal.dim)
        if sf then
            local fx, fw = X(sf.x), sf.w * g.cw
            gfx.rrect(cmds, fx, y + 1, fw, h - 3, 3, pal.edge)
            gfx.rrect(cmds, fx + 1, y + 2, fw - 2, h - 5, 2, pal.desk)
            icons.sys(cmds, "search", fx + 4, y + 2, h - 5, pal.dim)
            text(cmds, fx + h + 2, y + 1, clipText(pal.console and ("> " .. sf.text) or sf.text, sf.w - 4), pal.dim)
        end
        drawTray(cmds, sc, pal)
    end

    local function titleButton(cmds, c, y, kind, pal, fg)
        local bx = X(c) + floor(3 * g.cw / 2) - 4
        local by = y + floor((g.ch - 8) / 2)
        if kind == "close" then
            icons.sys(cmds, "close", bx - 1, by - 1, 10, fg)
        elseif kind == "max" then
            gfx.frame(cmds, bx + 1, by + 1, 6, 6, fg)
        else
            cmds[#cmds + 1] = { "fill", bx + 1, by + 6, 6, 1, fg }
        end
    end

    local function winFrame(cmds, w, pal)
        local x, y, wp, hp = X(w.x), Y(w.y), w.w * g.cw, w.h * g.ch
        local edge = w.grab and pal.accent or ((pal.console and w.active) and pal.dim or pal.edge)
        gfx.rrect(cmds, x - 1, y, wp + 2, hp + 1, 5, edge)
        gfx.rrect(cmds, x, y + 1, wp, hp - 1, 4, pal.face)
        local band = w.active and pal.light or pal.panel
        gfx.rrect(cmds, x, y, wp, g.ch, 4, band)
        cmds[#cmds + 1] = { "fill", x, y + g.ch - 5, wp, 5, band }
        cmds[#cmds + 1] = { "fill", x, y + g.ch - 1, wp, 1, (w.active or w.grab) and pal.accent or edge }
        local fg = w.active and pal.text or pal.dim
        local c, m, n = w.buttons[1], w.buttons[2], w.buttons[3]
        local right = w.x + w.w
        if c > 0 then titleButton(cmds, c, y, "close", pal, w.active and pal.close or pal.dim) right = c end
        if m > 0 then titleButton(cmds, m, y, "max", pal, fg) right = math.min(right, m) end
        if n > 0 then titleButton(cmds, n, y, "min", pal, fg) right = math.min(right, n) end
        local title = pal.console and ("[ " .. w.title:upper() .. " ]") or w.title
        text(cmds, x + 6, y + 1, clipText(title, floor((X(right) - x - 10) / g.tw)), fg)
    end

    local function drawRail(cmds, sc, pal)
        local tb = sc.taskbar
        local x, y, wp = X(1), Y(tb.y), tb.w * g.cw
        cmds[#cmds + 1] = { "fill", 1, y, x - 1 + wp, tb.h * g.ch + g.oy + g.ch, pal.panel }
        cmds[#cmds + 1] = { "fill", x + wp - 2, y, 1, tb.h * g.ch, pal.edge }
        local s = tb.start
        local sy, sh = Y(s.y), s.h * g.ch
        if s.active then gfx.rrect(cmds, x + 3, sy + 2, wp - 8, sh - 4, 4, pal.light) end
        local cx, cy = x + floor((wp - 2) / 2), sy + floor(sh / 2)
        for _, d in ipairs({ { -6, -6 }, { 1, -6 }, { -6, 1 }, { 1, 1 } }) do gfx.rrect(cmds, cx + d[1], cy + d[2], 5, 5, 1, pal.accent) end
        for _, t in ipairs(tb.items) do
            local ty, th = Y(t.y), t.h * g.ch
            local running = #t.wins > 0
            if t.active then
                gfx.rrect(cmds, x + 3, ty + 1, wp - 8, th - 2, 4, pal.light)
                cmds[#cmds + 1] = { "fill", x, ty + 3, 2, th - 6, pal.accent }
            elseif running then
                cmds[#cmds + 1] = { "fill", x, ty + floor(th / 2) - 2, 2, 4, t.min and pal.dim or pal.text }
            end
            local size = math.min(16, th - 4)
            local ix = x + floor((wp - 2 - size) / 2)
            local fg = t.active and pal.accent or ((running and not t.min) and pal.text or pal.dim)
            icons.draw(cmds, t.app and t.app.id or "", t.icon, ix, ty + floor((th - size) / 2), size, col, fg, t.active and pal.light or pal.panel)
            if t.badge then badge(cmds, x + wp - 3, ty, math.min(99, t.badge), pal) end
        end
    end

    local function drawKeys(cmds, sc, pal)
        local kb = sc.keys
        if not kb then return end
        local y, W = Y(kb.y), g.cols * g.cw
        cmds[#cmds + 1] = { "fill", 1, y, W + 2 * g.ox, g.ch + g.oy + g.ch, pal.panel }
        cmds[#cmds + 1] = { "fill", 1, y, W + 2 * g.ox, 1, pal.edge }
        text(cmds, X(2), y + 2, clipText(kb.text, math.max(0, kb.view.x - 3)), pal.dim)
        pill(cmds, X(kb.view.x), y + 2, kb.view.w * g.cw, g.ch - 3, kb.view.label, pal)
    end

    local function drawMenu(cmds, sc, pal)
        local m = sc.menu
        if not m then return end
        local x, y, wp, hp = X(m.x), Y(m.y), m.w * g.cw, m.h * g.ch
        cardBox(cmds, x, y, wp, hp, pal)
        cardTitle(cmds, x + 8, y + 1, m.title, pal)
        local sf = m.search
        local fx, fy, fw = X(sf.x), Y(sf.y), sf.w * g.cw
        gfx.rrect(cmds, fx, fy, fw, g.ch, 3, sf.text ~= "" and pal.accent or pal.edge)
        gfx.rrect(cmds, fx + 1, fy + 1, fw - 2, g.ch - 2, 2, pal.desk)
        icons.sys(cmds, "search", fx + 5, fy + 1, g.ch - 2, pal.dim)
        local shown = sf.text ~= "" and (sf.text .. "_") or sf.hint
        text(cmds, fx + g.ch + 6, fy + 1, clipText(shown, floor((fw - g.ch - 10) / g.tw)), sf.text ~= "" and pal.text or pal.dim)
        if m.empty then text(cmds, x + 10, Y(m.y + 3) + 2, m.empty, pal.dim) end
        for _, t in ipairs(m.tiles) do
            local tx, ty, tw, th = X(t.x), Y(t.y), t.w * g.cw, t.h * g.ch
            local first = t.first and sf.text ~= ""
            gfx.rrect(cmds, tx, ty, tw, th, 4, first and pal.accent or pal.edge)
            gfx.rrect(cmds, tx + 1, ty + 1, tw - 2, th - 2, 3, pal.light)
            local size = math.min(16, th - 4)
            icons.draw(cmds, t.glyph or "", t.icon, tx + 5, ty + floor((th - size) / 2), size, col, pal.accent, pal.light)
            text(cmds, tx + size + 10, ty + floor((th - 8) / 2), clipText(t.label, floor((tw - size - 12) / g.tw)), pal.text)
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
        return pal.accent2
    end

    local function drawToasts(cmds, sc, pal)
        for _, t in ipairs(sc.toasts) do
            local x, y, wp, hp = X(t.x), Y(t.y), t.w * g.cw, t.h * g.ch
            cardBox(cmds, x, y, wp, hp, pal)
            gfx.rrect(cmds, x, y + 3, 3, hp - 6, 1, kindColor(t.kind, pal))
            local cells = floor((wp - 14) / g.cw)
            text(cmds, x + 9, y + 2, clipText(t.title or "SQ Con", cells), pal.dim)
            text(cmds, x + 9, y + 11, clipText(t.text, cells), pal.text)
        end
    end

    local function drawQuick(cmds, sc, pal)
        local q = sc.quick
        if not q then return end
        local x, y, wp, hp = X(q.x), Y(q.y), q.w * g.cw, q.h * g.ch
        cardBox(cmds, x, y, wp, hp, pal)
        cardTitle(cmds, x + 8, y + 1, q.title, pal)
        for _, r in ipairs(q.rows) do
            if r.label and r.label ~= "" then text(cmds, X(q.x + 1), Y(r.y) + 1, r.label, pal.dim) end
            for _, b in ipairs(r.buttons or {}) do
                pill(cmds, X(b.x), Y(b.y) + 1, b.w * g.cw, g.ch - 2, b.label, pal, b.on, b.danger)
            end
        end
    end

    local function drawCenter(cmds, sc, pal)
        local nc = sc.center
        if not nc then return end
        local x, y, wp, hp = X(nc.x), Y(nc.y), nc.w * g.cw, nc.h * g.ch
        cardBox(cmds, x, y, wp, hp, pal)
        cardTitle(cmds, x + 8, y + 1, nc.title, pal)
        if nc.empty then text(cmds, x + 8, y + g.ch + 4, nc.empty, pal.dim) end
        for _, e in ipairs(nc.entries) do
            local ex, ey, ew, eh = X(e.x), Y(e.y), e.w * g.cw, e.h * g.ch
            gfx.rrect(cmds, ex, ey, ew, eh, 3, pal.light)
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
        cardBox(cmds, x, y, wp, hp, pal)
        for _, it in ipairs(s.items) do
            local ix, iy, iw, ih = X(it.x), Y(it.y), it.w * g.cw, it.h * g.ch
            if it.sel then gfx.rrect(cmds, ix, iy, iw, ih, 4, pal.light) end
            icons.draw(cmds, it.app and it.app.id or "", it.icon, ix + floor((iw - 24) / 2), iy + floor((ih - 24) / 2), 24, col,
                it.sel and pal.accent or pal.text, it.sel and pal.light or pal.panel)
        end
        cmds[#cmds + 1] = { "ctext", x, y + hp - g.ch - 4, wp, g.ch, s.label or "", pal.text, g.font }
    end

    local function drawContext(cmds, sc, pal)
        local c = sc.ctx
        if not c then return end
        local x, y, wp, hp = X(c.x), Y(c.y) - 2, c.w * g.cw, c.h * g.ch + 4
        cardBox(cmds, x, y, wp, hp, pal)
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
        gfx.rrect(cmds, x, y, wp, g.ch + 2, 3, pal.accent)
        cmds[#cmds + 1] = { "ctext", x, y, wp, g.ch + 2, s, pal.selText, g.font }
    end

    local function drawOverlays(cmds, sc, pal)
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
        if inBox(sc.menu, cx, cy) or inBox(sc.quick, cx, cy) or inBox(sc.center, cx, cy) or inBox(sc.switcher, cx, cy) then return true end
        for _, t in ipairs(sc.toasts) do
            if inBox(t, cx, cy) then return true end
        end
        local c = sc.ctx
        if c and cx >= c.x - 0 and cx < c.x + c.w and cy >= c.y - 1 and cy <= c.y + c.h then return true end
        if sc.hint and cy == sc.bottom then return true end
        return false
    end

    local function drawGraphs(cmds, sc, w, full, pal)
        local prev = graphPrev[w.id] or {}
        local over = sc.menu or sc.quick or sc.center or sc.switcher or sc.ctx or sc.toasts[1]
        local now = {}
        for i, gr in ipairs(w.graphs or {}) do
            now[i] = prev[i]
            if full or (gr.key ~= prev[i] and not over) then
                now[i] = gr.key
                local x, y, wp, hp = X(w.x + gr.x - 1), Y(w.y + gr.y), gr.w * g.cw, gr.h * g.ch
                cmds[#cmds + 1] = { "fill", x, y, wp, hp, pal.panel }
                for k = 0, 2 do cmds[#cmds + 1] = { "fill", x, y + 1 + floor(k * (hp - 3) / 2), wp, 1, pal.light } end
                local vals = gr.vals
                local lo, hi = vals[1], vals[1]
                for _, v in ipairs(vals) do lo, hi = math.min(lo, v), math.max(hi, v) end
                local c = col(gr.color)
                local lx, ly
                for k, v in ipairs(vals) do
                    local px = x + 1 + (#vals > 1 and (k - 1) * (wp - 4) / (#vals - 1) or 0)
                    local py = y + hp - 3 - (hi > lo and (v - lo) / (hi - lo) or 0.5) * (hp - 5)
                    if lx then cmds[#cmds + 1] = { "line", lx, ly, px, py, c, 2 } end
                    lx, ly = px, py
                end
                if lx then cmds[#cmds + 1] = { "disc", lx, ly, 2, c } end
            end
        end
        graphPrev[w.id] = now
    end

    local function graphGeom(w)
        local p = {}
        for _, gr in ipairs(w.graphs or {}) do p[#p + 1] = gr.x .. "," .. gr.y .. "," .. gr.w .. "," .. gr.h end
        return table.concat(p, ";")
    end

    local function paintCells(cmds, sc, wi, w, full, pal, forced)
        local canvas = w.canvas
        if not canvas.getLine then return end
        local geom = graphGeom(w)
        if not full and geom ~= (graphGeomPrev[w.id] or "") then winPrev[w.id], graphPrev[w.id] = nil, nil end
        graphGeomPrev[w.id] = geom
        local _, ch = canvas.getSize()
        local prev = winPrev[w.id]
        if not prev or full then prev = {} winPrev[w.id] = prev end
        local faceHex = ui.hex(C.face)
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
                    local dirty = full
                    if not dirty then
                        if fr and fr[cx] then dirty = true
                        elseif rowPrev then
                            dirty = rowPrev[1]:sub(x, x) ~= ch0 or rowPrev[2]:sub(x, x) ~= fc0 or rowPrev[3]:sub(x, x) ~= bc0
                        else dirty = true end
                    end
                    if dirty and not full and covered(sc, wi, cx, cy) then dirty = false end
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
                        gfx.rrect(cmds, X(w.x + x - 1), Y(cy) + 1, (x2 - x + 1) * g.cw, g.ch - 2, 3, g.palette[tonumber(bc0, 16) + 1])
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
                            local nd = full
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
                        if bc0 ~= faceHex and x2 > x and not gfx.flat then
                            local ub = r > 1 and select(3, canvas.getLine(r - 1)) or ""
                            local db = r < ch and select(3, canvas.getLine(r + 1)) or ""
                            local px0, py0, rad = X(cx), Y(cy), 4
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
    end

    local function palette()
        local pal = {
            desk = col(C.desk), face = col(C.face), panel = col(C.panel), light = col(C.light), text = col(C.text),
            dim = col(C.dim), accent = col(C.accent), accent2 = col(C.accent2), close = col(C.close),
            selText = col(C.selText), edge = theme.edge, console = theme.name == "console",
        }
        gfx.flat = pal.console
        return pal
    end

    function self.present(sc)
        local pal = palette()
        local base = signature(sc)
        local cmds = {}
        local cur = sc.cursor
        local full = lastBase ~= base
        lastBase = base
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
        if full then
            winPrev = {}
            cmds[#cmds + 1] = { "clear", pal.desk }
            drawStatus(cmds, sc, pal)
            drawRail(cmds, sc, pal)
            drawKeys(cmds, sc, pal)
            for wi, w in ipairs(sc.wins) do
                winFrame(cmds, w, pal)
                paintCells(cmds, sc, wi, w, true, pal)
                drawGraphs(cmds, sc, w, true, pal)
            end
            drawOverlays(cmds, sc, pal)
        else
            local forced
            if lastCursor and (not cur or cur.x ~= lastCursor.x or cur.y ~= lastCursor.y) then
                forced = { [lastCursor.y] = { [lastCursor.x] = true } }
            end
            for wi, w in ipairs(sc.wins) do
                paintCells(cmds, sc, wi, w, false, pal, forced)
                drawGraphs(cmds, sc, w, false, pal)
            end
        end
        local same = cur and lastCursor and cur.x == lastCursor.x and cur.y == lastCursor.y
        if cur and not (same and #cmds == 0) then
            cmds[#cmds + 1] = { "fill", X(cur.x), Y(cur.y) + g.ch - 2, g.cw, 2, col(cur.color or colors.white) }
        end
        lastCursor = cur and { x = cur.x, y = cur.y } or nil
        flush(cmds)
    end

    return self
end

return M
