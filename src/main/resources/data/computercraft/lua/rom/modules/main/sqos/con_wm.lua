local theme = require("sqos.theme")
local ui = require("sqos.ui")
local lang = require("sqos.lang")
local viewText = require("sqos.con_view")
local config = require("sqos.config")
local T = lang.t
local C = theme.C
local unpack = table.unpack or unpack

local WM = {}
WM.__index = WM

local nullTerm = setmetatable({
    getSize = function() return 51, 19 end,
    isColor = function() return true end,
    isColour = function() return true end,
    getPaletteColour = function() return 0, 0, 0 end,
    getPaletteColor = function() return 0, 0, 0 end,
    getTextColor = function() return colors.white end,
    getBackgroundColor = function() return colors.black end,
}, { __index = function() return function() end end })

local PASS_FOCUS = { key = true, key_up = true, char = true, paste = true }
local NO_BROADCAST = { mouse_click = true, mouse_up = true, mouse_drag = true, mouse_scroll = true, monitor_touch = true,
    monitor_resize = true, term_resize = true, sqc_touch = true, sqc_resize = true, sqc_move = true, sqc_up = true,
    sqc_drag = true, sqc_scroll = true, sqc_key = true, sqc_key_up = true, sqc_char = true, terminate = true }
local QUEUED = { mouse_click = true, mouse_up = true, mouse_scroll = true, key = true, char = true, paste = true, timer = true }
local MIN_W, MIN_H = 14, 4
local TOAST_SECONDS = 5
local HISTORY_MAX = 20

function WM.new(drv, cfg, apps)
    local self = setmetatable({}, WM)
    self.drv, self.cfg, self.apps = drv, cfg, apps
    self.con = true
    self.wins = {}
    self.nextId = 1
    self.running = true
    self.tbHits, self.menuHits, self.toastHits = ui.hits(), ui.hits(), ui.hits()
    self.ctxHits, self.trayHits, self.panelHits = ui.hits(), ui.hits(), ui.hits()
    self.byId = {}
    for _, a in ipairs(apps) do self.byId[a.id] = a end
    self.alt, self.ctrl = false, false
    self.alarms = 0
    self.toasts, self.history, self.unread = {}, {}, 0
    self.search = ""
    self.used = 0
    self.exitAction = nil
    self:layout()
    return self
end

function WM:layout()
    self.W, self.H = self.drv.size()
    self.compact = self.W < 40 or self.H < 14
    self.pixel = self.drv.kind == "pixel"
    self.sb = 1
    self.tb = self.compact and 0 or 1
    self.rail = self.compact and 3 or (self.pixel and 5 or 4)
    self.top = 2
    self.bottom = self.H - self.tb
    self.ax, self.aw = self.rail + 1, self.W - self.rail
    self:retile()
    self.drv.invalidate()
end

function WM:setGeometry(w, x, y, ww, hh)
    ww, hh = math.max(MIN_W, ww), math.max(MIN_H, hh)
    local resized = ww ~= w.w or hh ~= w.h
    w.x, w.y, w.w, w.h = x, y, ww, hh
    if resized then
        w.canvas.reposition(1, 1, ww, hh - 1)
        self:sendEvent(w, { "term_resize", n = 1 })
    end
end

function WM:visible()
    local t = {}
    for _, w in ipairs(self.wins) do
        if w.state == "max" and (not self.lock or w == self.lock) then return { w } end
        if w.state ~= "min" and (not self.lock or w == self.lock) then t[#t + 1] = w end
    end
    table.sort(t, function(a, b) return a.slot < b.slot end)
    return t
end

function WM:maxTiles()
    if self.aw < 60 then return 1 end
    if self.aw < 90 or self.bottom - self.top < 20 then return 2 end
    return 3
end

function WM:retile()
    local ts = self:visible()
    local maxN = self:maxTiles()
    while #ts > maxN do
        local oi
        for i, w in ipairs(ts) do
            if w ~= self.focus and (not oi or (w.used or 0) < (ts[oi].used or 0)) then oi = i end
        end
        if not oi then break end
        ts[oi].state = "min"
        table.remove(ts, oi)
    end
    local x0, y0, aw, ah = self.ax, self.top, self.aw, self.bottom - self.top + 1
    local n = #ts
    if n == 1 then
        self:setGeometry(ts[1], x0, y0, aw, ah)
    elseif n >= 2 then
        local lw = math.floor(aw * 0.6)
        self:setGeometry(ts[1], x0, y0, lw - 1, ah)
        if n == 2 then
            self:setGeometry(ts[2], x0 + lw, y0, aw - lw, ah)
        else
            local h1 = math.floor(ah * 0.55)
            self:setGeometry(ts[2], x0 + lw, y0, aw - lw, h1)
            self:setGeometry(ts[3], x0 + lw, y0 + h1, aw - lw, ah - h1)
        end
    end
end

local function titleButtons(w)
    local r = w.x + w.w
    if w.w < 22 then return r - 3, 0, 0 end
    return r - 3, r - 6, r - 9
end

function WM:visibleTop()
    local best
    for _, w in ipairs(self.wins) do
        if w.state ~= "min" and (not best or (w.used or 0) > (best.used or 0)) then best = w end
    end
    return best
end

function WM:focusWin(w)
    if w then
        if w.state == "min" then w.state = "normal" end
        for _, x in ipairs(self.wins) do
            if x ~= w and x.state == "max" then x.state = "normal" end
        end
        self.used = self.used + 1
        w.used = self.used
    end
    self.focus = w
end

function WM:closeWin(w)
    for i, x in ipairs(self.wins) do
        if x == w then table.remove(self.wins, i) break end
    end
    w.closed = true
    if w.app and w.app.id == "plant" then self.alarms = 0 end
    if self.focus == w then self.focus = self:visibleTop() end
    if self.capture == w then self.capture = nil end
    if self.drag and self.drag.win == w then self.drag = nil end
    if self.grab and self.grab.win == w then self.grab = nil end
end

function WM:toggleMax(w)
    w.state = w.state == "max" and "normal" or "max"
end

function WM:minimize(w)
    w.state = "min"
    if self.focus == w then self.focus = self:visibleTop() end
end

function WM:swap(a, b)
    if a and b and a ~= b then a.slot, b.slot = b.slot, a.slot end
end

function WM:moveTile(w, dir)
    local ts = self:visible()
    for i, x in ipairs(ts) do
        if x == w then self:swap(w, ts[i + dir]) return end
    end
end

function WM:openWindow(spec)
    local ww, hh = math.min(spec.w or 40, self.aw), math.min(spec.h or 14, self.bottom - self.top + 1)
    local w = { graphs = {},
        id = self.nextId, slot = self.nextId, title = spec.title or "?", x = self.ax, y = self.top, w = ww, h = hh,
        state = spec.maximize and "max" or "normal", app = spec.app, args = spec.args or {},
    }
    self.nextId = self.nextId + 1
    w.canvas = window.create(nullTerm, 1, 1, ww, hh - 1, false)
    w.canvas.setBackgroundColor(C.face)
    w.canvas.setTextColor(C.text)
    w.canvas.clear()
    local getLine = w.canvas.getLine
    w.canvas.getLine = function(y)
        local t, f, b = getLine(y)
        if t then f, b = ui.hexLine(f, "0"), ui.hexLine(b, "f") end
        return t, f, b
    end
    self.wins[#self.wins + 1] = w
    self:focusWin(w)
    self:retile()
    local ctx = self:makeContext(w)
    w.ctx = ctx
    w.co = coroutine.create(function() spec.run(ctx) end)
    self:resume(w, nil)
    return w
end

function WM:makeContext(w)
    local wm = self
    return {
        wm = self, win = w, args = w.args, cfg = self.cfg, T = T, touch = self.drv.touchOnly,
        setTitle = function(s) w.title = tostring(s) end,
        close = function() w.closing = true end,
        open = function(id, args) return wm:openApp(id, args) end,
        size = function() return w.canvas.getSize() end,
        quit = function(action) wm.exitAction = action wm.running = false end,
        unlock = function() if wm.lock == w then wm.lock = nil end w.closing = true end,
        apply = function() wm:applyConfig() end,
        notify = function(text, kind, title) wm:notify(text, kind, title or w.title, w.app and w.app.id) end,
        keys = function(text) w.keys = text end,
        graph = function(x, y, gw, gh, vals, color)
            if not x then w.graphs = {} return end
            if not wm.pixel then return false end
            w.graphs[#w.graphs + 1] = { x = x, y = y, w = gw, h = gh, vals = vals, color = color, key = table.concat(vals, ",") }
            return true
        end,
        setAlarms = function(n) wm.alarms = n or 0 end,
        badge = function(n) w.badge = (tonumber(n) or 0) > 0 and n or nil end,
    }
end

function WM:openApp(id, args)
    local app = self.byId[id]
    if not app then return nil end
    local title = app.titleKey and T(app.titleKey) or app.name
    return self:openWindow({ title = (args and args.title) or title, w = app.w, h = app.h, run = app.run, app = app, args = args })
end

function WM:showApp(id)
    for _, w in ipairs(self.wins) do
        if w.app and w.app.id == id and not w.closed then self:focusWin(w) return w end
    end
    return self:openApp(id)
end

function WM:notify(text, kind, title, app)
    local t = { text = tostring(text), kind = kind or "info", title = title, app = app, id = (self.toastId or 0) + 1, at = os.clock(),
        clock = self:clockText() }
    self.toastId = t.id
    table.insert(self.toasts, 1, t)
    while #self.toasts > 4 do table.remove(self.toasts) end
    table.insert(self.history, 1, t)
    while #self.history > HISTORY_MAX do table.remove(self.history) end
    if self.panel ~= "center" then self.unread = self.unread + 1 end
end

function WM:crash(w, err)
    w.dead = true
    err = tostring(err)
    if err:find("Terminated", 1, true) then
        w.closing = true
        return
    end
    local c = w.canvas
    c.setBackgroundColor(C.face)
    c.setTextColor(C.alarm)
    local _, h = c.getSize()
    c.setCursorPos(1, math.min(h, select(2, c.getCursorPos()) + 1))
    c.write(T("crashed", err):sub(1, 200))
    w.title = w.title .. " [!]"
end

function WM:resume(w, ev)
    if w.dead or w.closed or not w.co then return end
    if coroutine.status(w.co) ~= "suspended" then
        w.queue = w.queue or {}
        if ev then w.queue[#w.queue + 1] = ev end
        return
    end
    if ev and w.filter and ev[1] ~= w.filter and ev[1] ~= "terminate" then
        if QUEUED[ev[1]] then
            w.queue = w.queue or {}
            if #w.queue < (ev[1] == "timer" and 16 or 32) then w.queue[#w.queue + 1] = ev end
        end
        return
    end
    local old = term.redirect(w.canvas)
    local ok, res
    if ev then ok, res = coroutine.resume(w.co, unpack(ev, 1, ev.n or #ev))
    else ok, res = coroutine.resume(w.co) end
    term.redirect(old)
    if not ok then
        self:crash(w, res)
    elseif coroutine.status(w.co) == "dead" then
        w.dead = true
        w.closing = true
    else
        w.filter = res
    end
    if w.closing then self:closeWin(w) return end
    local q = w.queue
    if q and #q > 0 and not w.draining and (not w.filter or w.filter == q[1][1]) then
        w.draining = true
        while #q > 0 and not w.closed and not w.dead and (not w.filter or w.filter == q[1][1]) do
            self:resume(w, table.remove(q, 1))
        end
        w.draining = false
    end
end

function WM:sendEvent(w, ev)
    self:resume(w, ev)
end

function WM:sendMouse(w, name, a, x, y)
    self:resume(w, { name, a, x - w.x + 1, y - w.y, n = 4 })
end

function WM:clockText()
    if self.cfg.clock == "real" then return os.date("%H:%M") end
    local t = os.time()
    local h = math.floor(t)
    local m = math.floor((t - h) * 60)
    return string.format("%02d:%02d", h, m)
end

function WM:dateText()
    if self.cfg.clock == "real" then return os.date("%d.%m.%Y") end
    return T("day", os.day and os.day() or 1)
end

local function appLabel(a)
    return a.titleKey and T(a.titleKey) or a.name
end

function WM:isPinned(id)
    for _, p in ipairs(self.cfg.conPins or {}) do if p == id then return true end end
    return false
end

function WM:dockItems()
    local items, byApp = {}, {}
    local function entry(app, win)
        local key = app and app.id or ("w" .. win.id)
        local it = byApp[key]
        if not it then
            it = { app = app, wins = {}, pinned = app and self:isPinned(app.id) or false }
            byApp[key] = it
            items[#items + 1] = it
        end
        return it
    end
    for _, id in ipairs(self.cfg.conPins or {}) do
        local a = self.byId[id]
        if a and a.menu then entry(a) end
    end
    local sorted = {}
    for _, w in ipairs(self.wins) do if not self.lock or w == self.lock then sorted[#sorted + 1] = w end end
    table.sort(sorted, function(a, b) return a.id < b.id end)
    for _, w in ipairs(sorted) do
        local it = entry(w.app, w)
        it.wins[#it.wins + 1] = w
    end
    for _, it in ipairs(items) do
        local badge, allMin = 0, #it.wins > 0
        for _, w in ipairs(it.wins) do
            badge = badge + (tonumber(w.badge) or 0)
            if w.state ~= "min" then allMin = false end
            if w == self.focus and w.state ~= "min" then it.active = true end
        end
        it.badge = badge > 0 and badge or nil
        it.min = allMin
        it.label = it.app and appLabel(it.app) or (it.wins[1] and it.wins[1].title) or "?"
        it.icon = it.app and it.app.icon or { "?", colors.gray }
    end
    return items
end

local function wrapButtons(list, x, y, w, hits, gap)
    gap = gap or 1
    local rows, row, cx = {}, nil, x
    for _, b in ipairs(list) do
        local bw = b.w or (#b.label + 2)
        if row and cx + bw - 1 > x + w - 1 then
            cx = x
            y = y + 1
            row = nil
        end
        if not row then row = { y = y, buttons = {} } rows[#rows + 1] = row end
        b.x, b.y, b.w = cx, y, bw
        row.buttons[#row.buttons + 1] = b
        if hits then hits:add(cx, y, bw, 1, b.id, b.data) end
        cx = cx + bw + gap
    end
    return rows, y + (row and 1 or 0)
end

function WM:menuEntries()
    local q = self.search:lower()
    local out = {}
    local function add(id, label, icon, glyph)
        if q == "" or label:lower():find(q, 1, true) then
            out[#out + 1] = { id = id, label = label, icon = icon, glyph = glyph }
            return true
        end
    end
    for _, a in ipairs(self.apps) do
        if a.menu then add("app:" .. a.id, appLabel(a), a.icon, a.id) end
    end
    if q ~= "" then
        local gear = { "*", colors.gray }
        add("view", T("cmd_view"), gear, "settings")
        add("closeall", T("ctx_closeall"), gear, "settings")
        add("lock", T("lock"), gear, "login")
        add("reboot", T("reboot"), gear, "settings")
        add("quit", T("quit"), gear, "settings")
        for _, n in ipairs(self.plantNames or {}) do add("plant:" .. n, n, { "K", colors.red }, "plant") end
        local ok, list = pcall(fs.list, "/")
        if not self.byId.editor then list = {} end
        local n = 0
        for _, f in ipairs(ok and list or {}) do
            if n < 6 and not fs.isDir("/" .. f) and add("file:/" .. f, f, { "E", colors.blue }, "editor") then n = n + 1 end
        end
        local first, rest = {}, {}
        for _, e in ipairs(out) do
            local t = e.label:lower():sub(1, #q) == q and first or rest
            t[#t + 1] = e
        end
        for _, e in ipairs(rest) do first[#first + 1] = e end
        out = first
    end
    return out
end

function WM:buildScene()
    local W, H = self.W, self.H
    local sc = { W = W, H = H, top = self.top, bottom = self.bottom, sb = self.sb, tb = self.tb, rail = self.rail,
        pixel = self.pixel, compact = self.compact, wins = {}, toasts = {} }
    local now = os.clock()
    for i = #self.toasts, 1, -1 do
        if now - self.toasts[i].at > TOAST_SECONDS then table.remove(self.toasts, i) end
    end
    sc.clock, sc.date, sc.alarms = self:clockText(), self:dateText(), self.alarms
    sc.user = self.user
    self.tbHits:clear() self.menuHits:clear() self.toastHits:clear()
    self.ctxHits:clear() self.trayHits:clear() self.panelHits:clear()
    if self.saver then
        sc.saver = true
        sc.taskbar, sc.tray = { x = 1, y = self.top, w = self.rail, h = 0, items = {}, start = { x = 1, y = self.top, w = 0, h = 0 } }, { y = 1, items = {} }
        return sc
    end

    local vis = self:visible()
    local held = (self.grab or self.drag or {}).win
    for _, w in ipairs(vis) do
        sc.wins[#sc.wins + 1] = {
            ref = w, id = w.id, x = w.x, y = w.y, w = w.w, h = w.h, title = w.title, active = (w == self.focus),
            max = (w.state == "max"), canvas = w.canvas, graphs = w.graphs, app = w.app, icon = w.app and w.app.icon,
            grab = held == w or nil, buttons = { titleButtons(w) },
        }
    end
    if self.grab then sc.hint = T("move_hint") end

    local f = self.focus
    sc.status = { left = f and f.title or "" }
    local trayY = 1
    local tray = { y = trayY, items = {} }
    local tx = W
    local function trayItem(id, text, w, extra)
        tx = tx - w
        local it = { id = id, x = tx, w = w, text = text }
        for k, v in pairs(extra or {}) do it[k] = v end
        table.insert(tray.items, 1, it)
        self.trayHits:add(tx, trayY, w, 1, id)
        tx = tx - 1
    end
    trayItem("clock", sc.clock, #sc.clock + 2, { on = self.panel == "quick" })
    if W >= 50 then trayItem("date", sc.date, #sc.date + 1) end
    local n = self.unread > 0 and tostring(self.unread) or ""
    trayItem("bell", n, 3 + #n, { count = self.unread, on = self.panel == "center" })
    if sc.alarms > 0 then trayItem("alarm", tostring(sc.alarms), 3 + #tostring(sc.alarms), { count = sc.alarms }) end
    tray.w = W - tx
    sc.tray = tray
    if W >= 80 then
        local sw = math.min(36, W - 2 * (tray.w + 2))
        local s = { x = math.floor((W - sw) / 2) + 1, w = sw, text = T("search_bar") }
        sc.status.search = s
        self.trayHits:add(s.x, 1, sw, 1, "search")
    end

    local ih = self.pixel and 2 or 1
    local tb = { x = 1, y = self.top, w = self.rail, h = self.bottom - self.top + 1, ih = ih, items = {} }
    tb.start = { x = 1, y = self.top, w = self.rail, h = ih, active = self.menuOpen }
    self.tbHits:add(1, self.top, self.rail, ih, "start")
    local y = self.top + ih + (self.pixel and 0 or 1)
    for _, it in ipairs(self:dockItems()) do
        if y + ih - 1 > self.bottom then break end
        it.x, it.y, it.w, it.h = 1, y, self.rail, ih
        tb.items[#tb.items + 1] = it
        self.tbHits:add(1, y, self.rail, ih, "dock", it)
        y = y + ih
    end
    sc.taskbar = tb

    if self.tb > 0 then
        local label = T("view_" .. theme.name)
        sc.keys = { y = H, text = (f and f.state ~= "min" and (f.keys or (f.app and f.app.keys and T(f.app.keys)))) or T("keys_default"),
            view = { x = W - #label - 1, w = #label + 2, label = label } }
        self.tbHits:add(sc.keys.view.x, H, sc.keys.view.w, 1, "view")
    end

    self.home = #vis == 0 and not self.lock
    if self.menuOpen or self.home then
        local list = self:menuEntries()
        local tileW, tileH = self.pixel and 18 or 15, self.pixel and 2 or 1
        local cols = math.max(1, math.min(4, math.floor((self.aw - 3) / (tileW + 1))))
        if cols == 1 and self.aw - 2 < tileW then tileW = math.max(8, self.aw - 2) end
        local pw = cols * (tileW + 1) + 1
        local rows = math.max(1, math.ceil(#list / cols))
        local ph = 3 + rows * (tileH + 1) + 2
        local px = self.ax + math.max(0, math.floor((self.aw - pw) / 2))
        local py = self.top + ((self.compact or self.bottom - self.top < ph + 2) and 0 or 1)
        local menu = { x = px, y = py, w = pw, h = math.min(ph, self.bottom - py + 1), tiles = {}, home = self.home,
            title = T(self.search ~= "" and "pal_title" or "programs"),
            search = { x = px + 1, y = py + 1, w = pw - 2, text = self.search, hint = T("search_hint") },
            empty = #list == 0 and T("no_match") or nil }
        self.menuHits:add(menu.search.x, menu.search.y, menu.search.w, 1, "search")
        for i, e in ipairs(list) do
            local col, row = (i - 1) % cols, math.floor((i - 1) / cols)
            local t = { x = px + 1 + col * (tileW + 1), y = py + 3 + row * (tileH + 1), w = tileW, h = tileH,
                id = e.id, label = e.label, icon = e.icon, glyph = e.glyph, first = i == 1 }
            if t.y + tileH - 1 < py + menu.h - 2 then
                menu.tiles[#menu.tiles + 1] = t
                self.menuHits:add(t.x, t.y, t.w, t.h, t.id)
            end
        end
        local by = py + menu.h - 2
        local b1w, b2w, b3w = #T("reboot") + 2, #T("quit") + 2, #T("lock") + 2
        menu.user = { x = px + 1, y = by, text = self.user or T("user_guest") }
        menu.buttons = {
            { x = px + pw - 3 - b2w - b1w - b3w, y = by, w = b3w, h = 1, id = "lock", label = T("lock") },
            { x = px + pw - 2 - b2w - b1w, y = by, w = b1w, h = 1, id = "reboot", label = T("reboot") },
            { x = px + pw - 1 - b2w, y = by, w = b2w, h = 1, id = "quit", label = T("quit"), danger = true },
        }
        menu.user.w = math.max(0, menu.buttons[1].x - menu.user.x - 1)
        for _, b in ipairs(menu.buttons) do self.menuHits:add(b.x, b.y, b.w, b.h, b.id) end
        sc.menu = menu
    end

    if self.panel == "quick" then
        local pw = math.min(W - 2, 34)
        local px = W - pw
        local cfg = self.cfg
        local rows, y = {}, 0
        local function section(label, list)
            rows[#rows + 1] = { y = y, label = label }
            y = y + 1
            local r
            r, y = wrapButtons(list, 0, y, pw - 2)
            for _, row in ipairs(r) do rows[#rows + 1] = row end
        end
        local th = {}
        for _, n in ipairs(theme.names) do th[#th + 1] = { label = T("view_" .. n), id = "theme", data = n, on = theme.name == n } end
        section(T("se_theme"), th)
        section("", {
            { label = T("qs_sound"), id = "sound", on = cfg.sound ~= false },
        })
        section("", {
            { label = T("ctx_settings"), id = "settings" },
            { label = T("lock"), id = "lock" },
            { label = T("reboot"), id = "reboot" },
            { label = T("quit"), id = "quit", danger = true },
        })
        local ph = y + 2
        local py = self.top
        for _, r in ipairs(rows) do
            r.y = r.y + py + 1
            for _, b in ipairs(r.buttons or {}) do
                b.x, b.y = b.x + px + 1, r.y
                self.panelHits:add(b.x, b.y, b.w, 1, b.id, b.data)
            end
        end
        sc.quick = { x = px, y = py, w = pw, h = math.min(ph, self.bottom - py + 1), title = T("qs_title"), rows = rows }
    elseif self.panel == "center" then
        local pw = math.min(W - 2, 36)
        local px = W - pw
        local eh = self.pixel and 2 or 1
        local py = self.top
        local maxH = self.bottom - py + 1
        local entries = {}
        local y = py + 1
        for _, t in ipairs(self.history) do
            if y + eh - 1 > py + maxH - 3 then break end
            local e = { x = px + 1, y = y, w = pw - 2, h = eh, text = t.text, title = t.title or "SQ Con", kind = t.kind, clock = t.clock, id = t.id }
            entries[#entries + 1] = e
            self.panelHits:add(e.x, e.y, e.w, eh, "entry", t.id)
            y = y + eh + (self.pixel and 1 or 0)
        end
        local ph = math.max(4, y - py + 2)
        local center = { x = px, y = py, w = pw, h = math.min(ph, maxH), title = T("nc_title"), entries = entries,
            empty = #entries == 0 and T("nc_empty") or nil }
        if #entries > 0 then
            local lbl = T("nc_clear")
            center.clear = { x = px + pw - #lbl - 3, y = py + center.h - 2, w = #lbl + 2, label = lbl }
            self.panelHits:add(center.clear.x, center.clear.y, center.clear.w, 1, "clear")
        end
        sc.center = center
    end

    if self.switcher then
        local list = self.switcher.list
        local itemW = self.pixel and 6 or 5
        local n = math.min(#list, math.max(1, math.floor((W - 2) / (itemW + 1))))
        local sw = n * (itemW + 1) + 1
        local sh = self.pixel and 6 or 4
        local sx = math.floor((W - sw) / 2) + 1
        local sy = math.max(self.top, math.floor((self.top + self.bottom - sh) / 2))
        local s = { x = sx, y = sy, w = sw, h = sh, items = {}, title = T("sw_title") }
        for i = 1, n do
            local w = list[i]
            local it = { x = sx + 1 + (i - 1) * (itemW + 1), y = sy + 1, w = itemW, h = sh - 3, icon = w.app and w.app.icon or { "?", colors.gray },
                app = w.app, title = w.title, sel = i == self.switcher.idx, win = w }
            s.items[#s.items + 1] = it
            self.panelHits:add(it.x, it.y, it.w, it.h, "switch", w)
            if it.sel then s.label = w.title end
        end
        sc.switcher = s
    end

    local ty = self.top
    local th = self.pixel and 2 or 1
    for _, t in ipairs(self.panel == "center" and {} or self.toasts) do
        local tw2 = math.min(W - 2, math.max(16, math.min(36, #t.text + 6)))
        local entry = { x = W - tw2, y = ty, w = tw2, h = th, text = t.text, kind = t.kind, title = t.title, id = t.id }
        if ty + th - 1 <= self.bottom then
            sc.toasts[#sc.toasts + 1] = entry
            self.toastHits:add(entry.x, entry.y, entry.w, entry.h, t.id)
        end
        ty = ty + th + 1
    end

    local cm = self.ctxMenu
    if cm then
        local iw = 0
        for _, it in ipairs(cm.items) do iw = math.max(iw, #it.label) end
        local cw, chh = iw + 4, #cm.items
        local cx = math.max(1, math.min(cm.x, W - cw + 1))
        local cy = math.max(self.top, math.min(cm.y, self.bottom - chh + 1))
        local entry = { x = cx, y = cy, w = cw, h = chh, items = {} }
        for i, it in ipairs(cm.items) do
            local row = { y = cy + i - 1, label = it.label, id = it.id, danger = it.danger }
            entry.items[i] = row
            self.ctxHits:add(cx, row.y, cw, 1, i)
        end
        sc.ctx = entry
    end

    local w = self.focus
    if w and w.state ~= "min" and not self.menuOpen and not self.panel and not self.switcher and w.canvas.getCursorBlink() then
        local cx, cy = w.canvas.getCursorPos()
        local cw, ch = w.canvas.getSize()
        if cx >= 1 and cx <= cw and cy >= 1 and cy <= ch then
            sc.cursor = { x = w.x + cx - 1, y = w.y + cy, color = w.canvas.getTextColor() }
        end
    end
    return sc
end

function WM:render()
    self:retile()
    local sc = self:buildScene()
    self.scene = sc
    if self.drv.presentScene then
        self.drv.presentScene(sc)
    else
        local rows = viewText.compose(sc)
        self.drv.present(rows, sc.cursor)
    end
end

function WM:openContext(x, y, items)
    self.menuOpen, self.panel = false, nil
    self.ctxMenu = { x = x, y = y, items = items }
end

function WM:windowItems(w)
    local items = {}
    if w.state == "min" then
        items[#items + 1] = { label = T("ctx_restore"), id = "restore", data = w }
    else
        items[#items + 1] = { label = w.state == "max" and T("ctx_restore") or T("ctx_max"), id = "max", data = w }
        local ts = self:visible()
        if #ts > 1 and ts[1] ~= w then items[#items + 1] = { label = T("ctx_main"), id = "main", data = w } end
        items[#items + 1] = { label = T("ctx_min"), id = "min", data = w }
    end
    items[#items + 1] = { label = T("ctx_close"), id = "close", data = w, danger = true }
    return items
end

function WM:togglePin(id)
    local pins = self.cfg.conPins or {}
    for i, p in ipairs(pins) do
        if p == id then table.remove(pins, i) self.cfg.conPins = pins config.save(self.cfg) return end
    end
    pins[#pins + 1] = id
    self.cfg.conPins = pins
    config.save(self.cfg)
end

function WM:toggleView()
    self.cfg.theme = theme.name == "console" and "modern" or "console"
    config.save(self.cfg)
    self:applyConfig()
end

function WM:ctxAction(item)
    local id, d = item.id, item.data
    if id == "max" then self:focusWin(d) self:toggleMax(d)
    elseif id == "restore" then self:focusWin(d)
    elseif id == "main" then self:swap(d, self:visible()[1]) self:focusWin(d)
    elseif id == "min" then self:minimize(d)
    elseif id == "close" then self:closeWin(d)
    elseif id == "closeall" then
        for _, w in ipairs({ unpack(d.wins) }) do self:closeWin(w) end
    elseif id == "pin" then self:togglePin(d)
    elseif id == "settings" then self:showApp("settings")
    elseif id == "view" then self:toggleView()
    elseif id == "refresh" then self.drv.invalidate()
    end
end

function WM:contextAt(x, y)
    local hit = self.tbHits:at(x, y)
    if hit then
        if hit.id ~= "dock" then return false end
        local it = hit.data
        local items = {}
        if #it.wins == 1 then items = self:windowItems(it.wins[1])
        elseif #it.wins > 1 then items[#items + 1] = { label = T("ctx_closeall"), id = "closeall", data = it, danger = true } end
        if it.app and it.app.menu then
            table.insert(items, 1, { label = T(it.pinned and "ctx_unpin" or "ctx_pin"), id = "pin", data = it.app.id })
        end
        self:openContext(x, y, items)
        return true
    end
    local w = self:winAt(x, y)
    if w then
        if y ~= w.y then return false end
        self:focusWin(w)
        self:openContext(x, y, self:windowItems(w))
        return true
    end
    if y < self.top or y > self.bottom then return false end
    self:openContext(x, y, {
        { label = T("ctx_settings"), id = "settings" },
        { label = T("cmd_view"), id = "view" },
        { label = T("ctx_refresh"), id = "refresh" },
    })
    return true
end

function WM:applyConfig()
    lang.set(self.cfg.lang or "en")
    self.drv.setPalette(theme.apply(self.cfg.theme))
    self.drv.invalidate()
end

function WM:winAt(x, y)
    for _, w in ipairs(self:visible()) do
        if x >= w.x and x < w.x + w.w and y >= w.y and y < w.y + w.h then return w end
    end
end

function WM:lockScreen()
    local app = self.byId.login
    if not app or self.lock then return end
    self.menuOpen, self.panel, self.switcher = false, nil, nil
    self.lock = self:openWindow({ title = T("app_login"), run = app.run, app = app, args = { lock = true }, maximize = true })
end

function WM:menuAction(id)
    if id == "quit" then
        self.running = false
    elseif id == "lock" then
        self:lockScreen()
    elseif id == "reboot" then
        self.exitAction = "reboot"
        self.running = false
    elseif id == "view" then
        self:toggleView()
    elseif id == "closeall" then
        for _, w in ipairs({ unpack(self.wins) }) do self:closeWin(w) end
    elseif id:sub(1, 4) == "app:" then
        local app = self.byId[id:sub(5)]
        if app and app.multi then self:openApp(app.id) else self:showApp(id:sub(5)) end
    elseif id:sub(1, 5) == "file:" then
        self:openApp("editor", { path = id:sub(6) })
    elseif id:sub(1, 6) == "plant:" then
        self.plantSelect = id:sub(7)
        local w = self:showApp("plant")
        if w then self:sendEvent(w, { "sqos_select", n = 1 }) end
    end
end

function WM:dockClick(it)
    if #it.wins == 0 then
        if it.app then self:openApp(it.app.id) end
        return
    end
    if #it.wins == 1 then
        local w = it.wins[1]
        if w.state == "min" or w ~= self.focus then self:focusWin(w) else self:minimize(w) end
        return
    end
    local nextIdx = 1
    for i, w in ipairs(it.wins) do if w == self.focus then nextIdx = i % #it.wins + 1 end end
    self:focusWin(it.wins[nextIdx])
end

function WM:panelAction(id, data)
    local cfg = self.cfg
    if id == "theme" then cfg.theme = data
    elseif id == "sound" then cfg.sound = cfg.sound == false
    elseif id == "settings" then self.panel = nil self:showApp("settings") return
    elseif id == "lock" then self.panel = nil self:lockScreen() return
    elseif id == "reboot" then self.exitAction = "reboot" self.running = false return
    elseif id == "quit" then self.running = false return
    elseif id == "clear" then self.history, self.unread = {}, 0 return
    elseif id == "entry" then
        for i, t in ipairs(self.history) do
            if t.id == data then
                table.remove(self.history, i)
                if t.app then self.panel = nil self:showApp(t.app) end
                break
            end
        end
        return
    elseif id == "switch" then self.switcher = nil self:focusWin(data) return
    else return end
    config.save(cfg)
    if id == "theme" then self:applyConfig() end
end

function WM:onClick(btn, x, y, touch)
    local now = os.clock()
    if self.ctxMenu then
        local hit = self.ctxHits:at(x, y)
        local cm = self.ctxMenu
        self.ctxMenu = nil
        if hit and btn == 1 then self:ctxAction(cm.items[hit.id]) end
        return
    end
    if self.switcher then
        local hit = self.panelHits:at(x, y)
        self.switcher = nil
        if hit and hit.id == "switch" then self:focusWin(hit.data) end
        return
    end
    if btn == 2 and not self.grab and not self.menuOpen and not self.panel and self:contextAt(x, y) then return end
    local th = self.toastHits:at(x, y)
    if th then
        for i, t in ipairs(self.toasts) do
            if t.id == th.id then table.remove(self.toasts, i) break end
        end
        return
    end
    if self.grab then
        local g = self.grab
        self.grab = nil
        if btn == 1 then self:swap(g.win, self:winAt(x, y)) end
        return
    end
    local tr = self.trayHits:at(x, y)
    if tr then
        local was = self.menuOpen
        self.menuOpen = false
        if tr.id == "search" then self.panel, self.menuOpen, self.search = nil, not was, ""
        elseif tr.id == "clock" or tr.id == "date" then self.panel = self.panel ~= "quick" and "quick" or nil
        elseif tr.id == "bell" then
            self.panel = self.panel ~= "center" and "center" or nil
            self.unread = 0
        elseif tr.id == "alarm" then self.panel = nil self:showApp("plant") end
        return
    end
    if self.panel then
        local hit = self.panelHits:at(x, y)
        local p = self.panel
        local sc = self.scene
        local box = p == "quick" and sc.quick or sc.center
        if hit then self:panelAction(hit.id, hit.data)
        elseif not (box and x >= box.x and x < box.x + box.w and y >= box.y and y < box.y + box.h) then self.panel = nil end
        return
    end
    if self.menuOpen or self.home then
        local hit = self.menuHits:at(x, y)
        if hit then
            if hit.id ~= "search" then
                self.menuOpen, self.search = false, ""
                self:menuAction(hit.id)
            end
            return
        end
        if self.menuOpen then
            self.menuOpen = false
            return
        end
    end
    local hit = self.tbHits:at(x, y)
    if hit then
        if hit.id == "start" then self.menuOpen, self.search = true, ""
        elseif hit.id == "view" then self:toggleView()
        else self:dockClick(hit.data) end
        return
    end
    local w = self:winAt(x, y)
    if not w then return end
    self:focusWin(w)
    if y == w.y then
        local c, m, n = titleButtons(w)
        local dbl = self.lastTitle == w and now - self.lastTitleTime < 0.6
        self.lastTitle, self.lastTitleTime = w, now
        if c > 0 and x >= c and x < c + 3 then self:closeWin(w)
        elseif m > 0 and x >= m and x < m + 3 then self:toggleMax(w)
        elseif n > 0 and x >= n and x < n + 3 then self:minimize(w)
        elseif dbl then
            self.lastTitle = nil
            self:toggleMax(w)
        elseif #self:visible() > 1 then
            if touch then self.grab = { win = w } else self.drag = { win = w } end
        end
        return
    end
    self.capture = w
    self:sendMouse(w, "mouse_click", btn, x, y)
    if touch then
        self:sendMouse(w, "mouse_up", btn, x, y)
        self.capture = nil
    end
end

function WM:onDrag(btn, x, y)
    if self.drag then return end
    if self.capture then self:sendMouse(self.capture, "mouse_drag", btn, x, y) end
end

function WM:onUp(btn, x, y)
    if self.drag then
        local d = self.drag
        self.drag = nil
        self:swap(d.win, self:winAt(x, y))
        return
    end
    if self.capture then
        local w = self.capture
        self.capture = nil
        self:sendMouse(w, "mouse_up", btn, x, y)
    end
end

function WM:onScroll(dir, x, y)
    local w = self:winAt(x, y)
    if w then self:sendMouse(w, "mouse_scroll", dir, x, y) end
end

function WM:cycle()
    if not self.switcher then
        local list = {}
        for _, w in ipairs(self.wins) do list[#list + 1] = w end
        if #list < 2 then return end
        table.sort(list, function(a, b) return (a.used or 0) > (b.used or 0) end)
        self.switcher = { list = list, idx = 1 }
    end
    local s = self.switcher
    s.idx = s.idx % #s.list + 1
end

function WM:handle(ev)
    local name = ev[1]
    local e = self.drv.event(ev)
    if e and e[1] == "rewrite" then
        ev = e[2]
        name = ev[1]
        e = nil
    end
    if self.lock and (self.lock.dead or self.lock.closed) then self.lock = nil end
    if (e and e[1] ~= "resize") or name == "key" or name == "char" or name == "paste" then
        self.lastInput = os.clock()
        if self.saver then
            self.saver = nil
            self.drv.invalidate()
            return
        end
    end
    if self.lock and e and e[1] ~= "resize" then
        local l = self.lock
        if e[2] ~= 1 or e[3] < l.x or e[3] >= l.x + l.w or e[4] <= l.y or e[4] >= l.y + l.h then return end
    end
    if e then
        local kind = e[1]
        if kind == "click" then
            local touch = e.touch
            if touch == nil then touch = self.drv.touchOnly end
            self:onClick(e[2], e[3], e[4], touch)
        elseif kind == "up" then self:onUp(e[2], e[3], e[4])
        elseif kind == "drag" then self:onDrag(e[2], e[3], e[4])
        elseif kind == "scroll" then self:onScroll(e[2], e[3], e[4])
        elseif kind == "resize" then self:layout() end
        return
    end
    if NO_BROADCAST[name] and name ~= "terminate" then return end
    if name == "terminate" then
        if self.lock then return end
        local f = self.focus
        if f and not f.dead then self:resume(f, ev)
        elseif f then self:closeWin(f)
        else self.running = false end
        return
    end
    if name == "key" or name == "key_up" then
        local code = ev[2]
        if code == keys.leftCtrl or code == keys.rightCtrl then self.ctrl = (name == "key") end
        if name == "key" and self.ctrl and code == keys.space and not self.lock then
            self.menuOpen, self.search, self.panel, self.ctxMenu = not self.menuOpen, "", nil, nil
            return
        end
        if name == "key" and code == keys.escape and (self.menuOpen or self.panel or self.ctxMenu or self.grab) then
            self.menuOpen, self.panel, self.ctxMenu, self.grab = false, nil, nil, nil
            return
        end
    end
    local isAlt = name == "key" and (ev[2] == keys.leftAlt or ev[2] == keys.rightAlt)
    if (self.menuOpen or (self.home and not self.panel)) and not self.alt and not isAlt and (name == "char" or name == "key") then
        if name == "char" then
            if not (self.ctrl and ev[2] == " ") then self.search = (self.search .. ev[2]):sub(1, 20) end
        elseif ev[2] == keys.backspace then self.search = self.search:sub(1, -2)
        elseif ev[2] == keys.enter then
            local m = self.scene and self.scene.menu
            self.menuOpen, self.search = false, ""
            if m and m.tiles[1] then self:menuAction(m.tiles[1].id) end
        end
        return
    end
    if name == "key" or name == "key_up" then
        local code = ev[2]
        if code == keys.leftAlt or code == keys.rightAlt then
            self.alt = (name == "key")
            if not self.alt and self.switcher then
                local s = self.switcher
                self.switcher = nil
                self:focusWin(s.list[s.idx])
                return
            end
        end
        if name == "key" and self.alt and not self.lock then
            local f = self.focus
            if code == keys.tab then self:cycle() return end
            if code == keys.f4 and f then self:closeWin(f) return end
            if f and f.state ~= "min" then
                if code == keys.left then self:moveTile(f, -1) return end
                if code == keys.right then self:moveTile(f, 1) return end
                if code == keys.up or code == keys.enter then self:toggleMax(f) return end
                if code == keys.down then self:minimize(f) return end
            end
            local digits = { keys.one, keys.two, keys.three, keys.four, keys.five, keys.six, keys.seven, keys.eight, keys.nine }
            for i, k in ipairs(digits) do
                if code == k then
                    local it = self.scene and self.scene.taskbar.items[i]
                    if it then self:dockClick(it) end
                    return
                end
            end
        end
    end
    if PASS_FOCUS[name] then
        if self.focus and self.focus.state ~= "min" then self:resume(self.focus, ev) end
        return
    end
    if name == "timer" and ev[2] == self.clockTimer then
        self.clockTimer = os.startTimer(1)
        local mins = tonumber(self.cfg.saver) or 0
        if mins > 0 and not self.saver and os.clock() - (self.lastInput or 0) > mins * 60 then
            self.saver = true
            self.menuOpen, self.panel, self.switcher, self.ctxMenu = false, nil, nil, nil
            self.drv.invalidate()
        end
    end
    local snapshot = {}
    for i, w in ipairs(self.wins) do snapshot[i] = w end
    for _, w in ipairs(snapshot) do
        if not w.closed then self:resume(w, ev) end
    end
end

function WM:run()
    self.clockTimer = os.startTimer(1)
    self.lastInput = os.clock()
    self:applyConfig()
    if not self.cfg.helpSeen and self.byId.help then
        self.cfg.helpSeen = true
        config.save(self.cfg)
        self:openApp("help")
    end
    if self.lockOnStart then
        local d = require("sqos.account").load()
        if #d.order > 0 or not d.skipped then self:lockScreen() end
    end
    self:render()
    local flushPending = false
    local held = self.drv.hold and self.drv.hold(coroutine.running())
    while self.running do
        local ev = held and held() or table.pack(os.pullEventRaw())
        if ev[1] == "sqos_flush" then
            flushPending = false
            self:render()
        else
            self:handle(ev)
            if self.running then
                if (self.drag or self.grab) and not flushPending then
                    flushPending = true
                    os.queueEvent("sqos_flush")
                elseif not (self.drag or self.grab) then
                    self:render()
                end
            end
        end
    end
    return self.exitAction
end

return WM
