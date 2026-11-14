local theme = require("sqos.theme")
local ui = require("sqos.ui")
local lang = require("sqos.lang")
local viewText = require("sqos.view_text")
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
    monitor_resize = true, term_resize = true, sqc_touch = true, sqc_resize = true, terminate = true }
local QUEUED = { mouse_click = true, mouse_up = true, mouse_scroll = true, key = true, char = true, paste = true }
local MIN_W, MIN_H = 14, 4
local TOAST_SECONDS = 5
local HISTORY_MAX = 20

function WM.new(drv, cfg, apps)
    local self = setmetatable({}, WM)
    self.drv, self.cfg, self.apps = drv, cfg, apps
    self.wins = {}
    self.nextId = 1
    self.running = true
    self.iconHits, self.tbHits, self.menuHits, self.toastHits = ui.hits(), ui.hits(), ui.hits(), ui.hits()
    self.ctxHits, self.trayHits, self.panelHits = ui.hits(), ui.hits(), ui.hits()
    self.byId = {}
    for _, a in ipairs(apps) do self.byId[a.id] = a end
    self.cascade = 0
    self.alt = false
    self.alarms = 0
    self.toasts, self.history, self.unread = {}, {}, 0
    self.search = ""
    self.exitAction = nil
    self:layout()
    return self
end

function WM:layout()
    self.W, self.H = self.drv.size()
    self.compact = self.W < 40 or self.H < 14
    self.pixel = self.drv.kind == "pixel"
    self.sb = self.compact and 0 or 1
    self.tb = self.pixel and 2 or 1
    self.top = self.sb + 1
    self.bottom = self.H - self.tb
    for _, w in ipairs(self.wins) do
        if w.state == "max" then self:setGeometry(w, 1, self.top, self.W, self.bottom - self.top + 1)
        elseif w.snapped then self:snap(w, w.snapped)
        else self:clampWin(w) end
    end
    self.drv.invalidate()
end

function WM:clampWin(w)
    local areaH = self.bottom - self.top + 1
    if w.w > self.W then w.w = self.W end
    if w.h > areaH then w.h = areaH end
    if w.x + w.w - 1 < 4 then w.x = 4 - w.w + 1 end
    if w.x > self.W - 3 then w.x = self.W - 3 end
    if w.y < self.top then w.y = self.top end
    if w.y > self.bottom then w.y = self.bottom end
end

function WM:setGeometry(w, x, y, ww, hh)
    local areaH = self.bottom - self.top + 1
    ww, hh = math.max(MIN_W, math.min(ww, self.W)), math.max(MIN_H, math.min(hh, areaH))
    local resized = ww ~= w.w or hh ~= w.h
    w.x, w.y, w.w, w.h = x, y, ww, hh
    if resized then
        w.canvas.reposition(1, 1, ww, hh - 1)
        self:sendEvent(w, { "term_resize", n = 1 })
    end
end

local function titleButtons(w)
    local r = w.x + w.w
    if w.w < 22 then return r - 3, 0, 0 end
    if w.fixed then return r - 3, 0, r - 6 end
    return r - 3, r - 6, r - 9
end

function WM:visibleTop()
    for i = #self.wins, 1, -1 do
        if self.wins[i].state ~= "min" then return self.wins[i] end
    end
end

function WM:focusWin(w)
    if w and w.state == "min" then w.state = w.restoreState or "normal" end
    for i, x in ipairs(self.wins) do
        if x == w then table.remove(self.wins, i) break end
    end
    if w then self.wins[#self.wins + 1] = w end
    self.focus = w
end

function WM:closeWin(w)
    for i, x in ipairs(self.wins) do
        if x == w then table.remove(self.wins, i) break end
    end
    if not w.closed and self.drv.animate and self.cfg.anim then self.drv.animate("close", w) end
    w.closed = true
    if w.app and w.app.id == "plant" then self.alarms = 0 end
    if self.focus == w then self.focus = self:visibleTop() end
    if self.capture == w then self.capture = nil end
    if self.drag and self.drag.win == w then self.drag = nil end
    if self.grab and self.grab.win == w then self.grab = nil end
end

function WM:toggleMax(w)
    if w.fixed then return end
    if w.state == "max" or w.snapped then
        w.state, w.snapped = "normal", nil
        local g = w.prev
        self:setGeometry(w, g.x, g.y, g.w, g.h)
    else
        w.prev = { x = w.x, y = w.y, w = w.w, h = w.h }
        w.state = "max"
        self:setGeometry(w, 1, self.top, self.W, self.bottom - self.top + 1)
    end
end

function WM:snap(w, side)
    if w.fixed then return end
    if w.state ~= "max" and not w.snapped then w.prev = { x = w.x, y = w.y, w = w.w, h = w.h } end
    local areaH = self.bottom - self.top + 1
    local half = math.floor(self.W / 2)
    w.state, w.snapped = "normal", side
    if side == "left" then self:setGeometry(w, 1, self.top, half, areaH)
    else self:setGeometry(w, half + 1, self.top, self.W - half, areaH) end
end

function WM:minimize(w)
    w.restoreState = w.state
    w.state = "min"
    if self.drv.animate and self.cfg.anim then self.drv.animate("close", w) end
    if self.focus == w then self.focus = self:visibleTop() end
end

function WM:openWindow(spec)
    local W = self.W
    local areaH = self.bottom - self.top + 1
    local ww = math.min(spec.w or 40, W)
    local hh = math.min(spec.h or 14, areaH)
    self.cascade = (self.cascade + 1) % 6
    local x = math.max(1, math.min(W - ww + 1, 2 + self.cascade * 2))
    local y = math.max(self.top, math.min(self.bottom - hh + 1, self.top + self.cascade))
    local w = {
        id = self.nextId, title = spec.title or "?", x = x, y = y, w = ww, h = hh, state = "normal",
        app = spec.app, fixed = spec.fixed, args = spec.args or {},
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
    if (self.compact or spec.maximize) and not spec.fixed then
        w.prev = { x = x, y = y, w = ww, h = hh }
        w.state = "max"
        self:setGeometry(w, 1, self.top, W, self.bottom - self.top + 1)
    end
    self:focusWin(w)
    if self.drv.animate and self.cfg.anim then self.drv.animate("open", w) end
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
        notify = function(text, kind, title) wm:notify(text, kind, title or w.title) end,
        setAlarms = function(n) wm.alarms = n or 0 end,
        badge = function(n) w.badge = (tonumber(n) or 0) > 0 and n or nil end,
    }
end

function WM:openApp(id, args)
    local app = self.byId[id]
    if not app then return nil end
    local title = app.titleKey and T(app.titleKey) or app.name
    return self:openWindow({
        title = (args and args.title) or title, w = app.w, h = app.h, run = app.run, app = app, args = args,
        fixed = app.fixed, maximize = app.maximize,
    })
end

function WM:notify(text, kind, title)
    local t = { text = tostring(text), kind = kind or "info", title = title, id = (self.toastId or 0) + 1, at = os.clock(),
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
    if ev and w.filter and ev[1] ~= w.filter and ev[1] ~= "terminate" then
        if QUEUED[ev[1]] then
            w.queue = w.queue or {}
            if #w.queue < 32 then w.queue[#w.queue + 1] = ev end
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

function WM:iconApps()
    local out = {}
    for _, a in ipairs(self.apps) do if a.desktop then out[#out + 1] = a end end
    return out
end

local function appLabel(a)
    return a.titleKey and T(a.titleKey) or a.name
end

function WM:appOfWindow(w)
    return w.app
end

function WM:isPinned(id)
    for _, p in ipairs(self.cfg.pins or {}) do if p == id then return true end end
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
    for _, id in ipairs(self.cfg.pins or {}) do
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

function WM:buildScene()
    local W, H = self.W, self.H
    local sc = { W = W, H = H, top = self.top, bottom = self.bottom, sb = self.sb, tb = self.tb,
        pixel = self.pixel, compact = self.compact, wins = {}, icons = {}, toasts = {}, wall = self.cfg.wall }
    local now = os.clock()
    for i = #self.toasts, 1, -1 do
        if now - self.toasts[i].at > TOAST_SECONDS then table.remove(self.toasts, i) end
    end
    sc.clock, sc.date, sc.alarms = self:clockText(), self:dateText(), self.alarms
    sc.user = self.user
    if self.saver then
        sc.saver = true
        sc.taskbar, sc.tray = { y = H, h = self.tb, items = {}, start = { x = 1, w = 0 }, x0 = 1, x1 = 1 }, { y = 1, items = {} }
        self.iconHits:clear() self.tbHits:clear() self.menuHits:clear() self.toastHits:clear()
        self.ctxHits:clear() self.trayHits:clear() self.panelHits:clear()
        return sc
    end

    if not self.compact and W >= 40 and self.bottom - self.top >= 12 then
        sc.widget = { x = 3, y = self.top + 1 }
    end
    self.iconHits:clear()
    self.ipos, self.iconXY = self.ipos or {}, {}
    local iw, ih = self.compact and 8 or 10, self.pixel and 5 or 4
    local icH = self.pixel and 4 or 3
    local y0 = self.top + (sc.widget and 8 or 1)
    local perCol = math.max(1, math.floor((self.bottom - y0 + 1) / ih))
    for i, a in ipairs(self.lock and {} or self:iconApps()) do
        local col, row = math.floor((i - 1) / perCol), (i - 1) % perCol
        local x, y = 2 + col * iw, y0 + row * ih
        if self.ipos[a.id] then x, y = self.ipos[a.id][1], self.ipos[a.id][2] end
        self.iconXY[a.id] = { x, y }
        if x + 5 <= W and y + icH - 1 <= self.bottom then
            local ic = {
                id = a.id, x = x, y = y, w = iw - 1, h = icH, label = appLabel(a), icon = a.icon, sel = (self.selIcon == a.id),
            }
            sc.icons[#sc.icons + 1] = ic
            self.iconHits:add(x, y, iw - 1, icH, a.id)
        end
    end

    for _, w in ipairs(self.wins) do
        if w.state ~= "min" and (not self.lock or w == self.lock) then
            local grab = self.grab and self.grab.win == w
            sc.wins[#sc.wins + 1] = {
                ref = w, id = w.id, x = w.x, y = w.y, w = w.w, h = w.h, title = w.title, active = (w == self.focus),
                max = (w.state == "max"), snapped = w.snapped, fixed = w.fixed, canvas = w.canvas, app = w.app,
                icon = w.app and w.app.icon, grab = grab and self.grab.mode or nil,
                dragging = (self.drag and self.drag.win == w and self.drag.mode == "move") or nil,
                buttons = { titleButtons(w) },
            }
        end
    end
    if self.grab then
        sc.hint = self.grab.mode == "move" and T("move_hint") or T("resize_hint")
    end

    if self.sb > 0 then
        local f = self.focus
        sc.status = { left = f and f.title or "" }
    end

    self.trayHits:clear()
    local trayY = self.sb > 0 and 1 or (H - self.tb + 1)
    local tray = { y = trayY, items = {}, inDock = self.sb == 0 }
    local tx = W
    local function trayItem(id, text, w, extra)
        tx = tx - w
        local it = { id = id, x = tx, w = w, text = text }
        for k, v in pairs(extra or {}) do it[k] = v end
        table.insert(tray.items, 1, it)
        self.trayHits:add(tx, trayY, w, self.tb > 1 and tray.inDock and self.tb or 1, id)
        tx = tx - 1
    end
    if self.sb > 0 or W >= 26 then
        trayItem("clock", sc.clock, #sc.clock + 2, { on = self.panel == "quick" })
        if self.sb > 0 and W >= 50 then trayItem("date", sc.date, #sc.date + 1) end
        local n = self.unread > 0 and tostring(self.unread) or ""
        trayItem("bell", n, 3 + #n, { count = self.unread, on = self.panel == "center" })
        if sc.alarms > 0 then trayItem("alarm", tostring(sc.alarms), 3 + #tostring(sc.alarms), { count = sc.alarms }) end
    end
    tray.w = W - tx
    sc.tray = tray

    self.tbHits:clear()
    local tb = { y = H - self.tb + 1, h = self.tb, items = {} }
    local items = self:dockItems()
    local startW = self.pixel and 4 or 4
    local itemW = self.pixel and 4 or 3
    local avail = W - (tray.inDock and tray.w + 1 or 0) - 2
    local maxN = math.max(0, math.floor((avail - startW - 1) / (itemW + 1)))
    local shown = {}
    for _, it in ipairs(items) do if #shown < maxN then shown[#shown + 1] = it end end
    local total = startW + 1 + #shown * (itemW + 1) - 1
    local x = math.max(2, math.floor((avail - total) / 2) + 2)
    tb.start = { x = x, w = startW, active = self.menuOpen }
    self.tbHits:add(x, tb.y, startW, self.tb, "start")
    x = x + startW + 1
    for _, it in ipairs(shown) do
        it.x, it.w = x, itemW
        tb.items[#tb.items + 1] = it
        self.tbHits:add(x, tb.y, itemW, self.tb, "dock", it)
        x = x + itemW + 1
    end
    tb.x0, tb.x1 = tb.start.x, x - 2
    sc.taskbar = tb

    self.menuHits:clear()
    if self.menuOpen then
        local apps, q = {}, self.search:lower()
        for _, a in ipairs(self.apps) do
            if a.menu and (q == "" or appLabel(a):lower():find(q, 1, true)) then apps[#apps + 1] = a end
        end
        local tileW, tileH = self.pixel and 16 or 14, self.pixel and 3 or 2
        local cols = math.max(1, math.min(3, math.floor((W - 1) / (tileW + 1))))
        if cols == 1 and W - 2 < tileW then tileW = math.max(8, W - 2) end
        local pw = cols * (tileW + 1) + 1
        local rows = math.max(1, math.ceil(#apps / cols))
        local ph = 3 + rows * (tileH + 1) + 2
        local px = math.max(1, math.min(W - pw + 1, tb.start.x - 1))
        local py = math.max(self.top, self.bottom - ph + 1 - (self.pixel and 1 or 0))
        local menu = { x = px, y = py, w = pw, h = math.min(ph, self.bottom - self.top + 1), tiles = {}, title = T("programs"),
            search = { x = px + 1, y = py + 1, w = pw - 2, text = self.search, hint = T("search_hint") },
            empty = #apps == 0 and T("no_match") or nil }
        self.menuHits:add(menu.search.x, menu.search.y, menu.search.w, 1, "search")
        for i, a in ipairs(apps) do
            local col, row = (i - 1) % cols, math.floor((i - 1) / cols)
            local t = { x = px + 1 + col * (tileW + 1), y = py + 3 + row * (tileH + 1), w = tileW, h = tileH,
                id = "app:" .. a.id, label = appLabel(a), icon = a.icon, app = a, first = i == 1 }
            if t.y + tileH - 1 < py + menu.h - 1 then
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

    self.panelHits:clear()
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
            { label = T("qs_anim"), id = "anim", on = cfg.anim },
            { label = T("qs_sound"), id = "sound", on = cfg.sound ~= false },
            { label = T("qs_tap"), id = "tap", on = cfg.singleTap },
        })
        section("", {
            { label = T("ctx_settings"), id = "settings" },
            { label = T("lock"), id = "lock" },
            { label = T("reboot"), id = "reboot" },
            { label = T("quit"), id = "quit", danger = true },
        })
        local ph = y + 2
        local py = math.max(self.top, self.bottom - ph + 1)
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
        local py = self.sb > 0 and self.top or self.top
        local maxH = self.bottom - py + 1
        local entries = {}
        local y = py + 1
        for _, t in ipairs(self.history) do
            if y + eh - 1 > py + maxH - 3 then break end
            local e = { x = px + 1, y = y, w = pw - 2, h = eh, text = t.text, title = t.title or "SQ-OS", kind = t.kind, clock = t.clock, id = t.id }
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

    self.toastHits:clear()
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

    self.ctxHits:clear()
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
        if not w.fixed then
            items[#items + 1] = { label = (w.state == "max" or w.snapped) and T("ctx_restore") or T("ctx_max"), id = "max", data = w }
            if self.W >= 40 then
                items[#items + 1] = { label = T("ctx_left"), id = "left", data = w }
                items[#items + 1] = { label = T("ctx_right"), id = "right", data = w }
            end
        end
        items[#items + 1] = { label = T("ctx_min"), id = "min", data = w }
    end
    items[#items + 1] = { label = T("ctx_close"), id = "close", data = w, danger = true }
    return items
end

local function nextOf(list, v)
    for i, x in ipairs(list) do
        if x == v then return list[i % #list + 1] end
    end
    return list[1]
end

function WM:togglePin(id)
    local pins = self.cfg.pins or {}
    for i, p in ipairs(pins) do
        if p == id then table.remove(pins, i) self.cfg.pins = pins config.save(self.cfg) return end
    end
    pins[#pins + 1] = id
    self.cfg.pins = pins
    config.save(self.cfg)
end

function WM:ctxAction(item)
    local id, d = item.id, item.data
    if id == "open" then self:openApp(d)
    elseif id == "max" then self:focusWin(d) self:toggleMax(d)
    elseif id == "restore" then
        if d.state == "min" then self:focusWin(d) else self:toggleMax(d) end
    elseif id == "left" or id == "right" then self:focusWin(d) self:snap(d, id)
    elseif id == "min" then self:minimize(d)
    elseif id == "close" then self:closeWin(d)
    elseif id == "closeall" then
        for _, w in ipairs({ unpack(d.wins) }) do self:closeWin(w) end
    elseif id == "pin" then self:togglePin(d)
    elseif id == "settings" then self:openApp("settings")
    elseif id == "theme" then
        self.cfg.theme = nextOf(theme.names, theme.name)
        config.save(self.cfg)
        self:applyConfig()
    elseif id == "minall" then
        for _, w in ipairs({ unpack(self.wins) }) do if w.state ~= "min" then self:minimize(w) end end
    elseif id == "refresh" then self.drv.invalidate()
    end
end

function WM:contextAt(x, y)
    if y > self.bottom then
        local hit = self.tbHits:at(x, y)
        if hit and hit.id == "dock" then
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
        return false
    end
    local w = self:winAt(x, y)
    if w then
        if y == w.y then
            self:focusWin(w)
            self:openContext(x, y, self:windowItems(w))
            return true
        end
        return false
    end
    local hit = self.iconHits:at(x, y)
    if hit then
        self.selIcon = hit.id
        self:openContext(x, y, {
            { label = T("ctx_open"), id = "open", data = hit.id },
            { label = T(self:isPinned(hit.id) and "ctx_unpin" or "ctx_pin"), id = "pin", data = hit.id },
        })
        return true
    end
    self:openContext(x, y, {
        { label = T("ctx_settings"), id = "settings" },
        { label = T("ctx_theme"), id = "theme" },
        { label = T("ctx_minall"), id = "minall" },
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
    for i = #self.wins, 1, -1 do
        local w = self.wins[i]
        if w.state ~= "min" and x >= w.x and x < w.x + w.w and y >= w.y and y < w.y + w.h then return w end
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
    elseif type(id) == "string" and id:sub(1, 4) == "app:" then
        self:openApp(id:sub(5))
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
    elseif id == "anim" then cfg.anim = not cfg.anim
    elseif id == "sound" then cfg.sound = cfg.sound == false
    elseif id == "tap" then cfg.singleTap = not cfg.singleTap
    elseif id == "settings" then self.panel = nil self:openApp("settings") return
    elseif id == "lock" then self.panel = nil self:lockScreen() return
    elseif id == "reboot" then self.exitAction = "reboot" self.running = false return
    elseif id == "quit" then self.running = false return
    elseif id == "clear" then self.history, self.unread = {}, 0 return
    elseif id == "entry" then
        for i, t in ipairs(self.history) do if t.id == data then table.remove(self.history, i) break end end
        return
    elseif id == "switch" then self.switcher = nil self:focusWin(data) return
    else return end
    config.save(cfg)
    if id == "theme" then self:applyConfig() end
end

function WM:placeGrab(g, x, y)
    local w = g.win
    if g.mode == "move" then
        w.x, w.y = x - g.ox, y - g.oy
        w.snapped = nil
        self:clampWin(w)
    else
        self:setGeometry(w, w.x, w.y, x - w.x + 1, y - w.y + 1)
    end
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
        if btn == 1 and not (g.mode == "move" and y == g.win.y and x >= g.win.x and x < g.win.x + g.win.w) then
            self:placeGrab(g, x, y)
        end
        return
    end
    local tr = self.trayHits:at(x, y)
    if tr then
        self.menuOpen = false
        if tr.id == "clock" or tr.id == "date" then self.panel = self.panel ~= "quick" and "quick" or nil
        elseif tr.id == "bell" then
            self.panel = self.panel ~= "center" and "center" or nil
            self.unread = 0
        elseif tr.id == "alarm" then self.panel = nil self:openApp("plant") end
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
    if self.menuOpen then
        local hit = self.menuHits:at(x, y)
        local m = self.scene and self.scene.menu
        if hit and hit.id == "search" then return end
        if hit or not (m and x >= m.x and x < m.x + m.w and y >= m.y and y < m.y + m.h) then self.menuOpen = false end
        if hit then self:menuAction(hit.id) end
        return
    end
    if y > self.bottom then
        local hit = self.tbHits:at(x, y)
        if hit then
            if hit.id == "start" then
                self.menuOpen, self.search = true, ""
            else
                self:dockClick(hit.data)
            end
        end
        return
    end
    local w = self:winAt(x, y)
    if w then
        self:focusWin(w)
        local ly = y - w.y
        if ly == 0 then
            local c, m, n = titleButtons(w)
            local dbl = self.lastTitle == w and now - self.lastTitleTime < 0.6
            self.lastTitle, self.lastTitleTime = w, now
            if c > 0 and x >= c and x < c + 3 then self:closeWin(w)
            elseif m > 0 and x >= m and x < m + 3 then self:toggleMax(w)
            elseif n > 0 and x >= n and x < n + 3 then self:minimize(w)
            elseif not w.fixed and dbl then
                self.lastTitle = nil
                self:toggleMax(w)
            elseif w.state ~= "max" then
                if touch then
                    self.grab = { win = w, mode = "move", ox = x - w.x, oy = 0 }
                else
                    self.drag = { win = w, mode = "move", ox = x - w.x, oy = 0 }
                end
            end
            return
        end
        if w.state ~= "max" and not w.fixed and x == w.x + w.w - 1 and y == w.y + w.h - 1 then
            if touch then self.grab = { win = w, mode = "resize" }
            else self.drag = { win = w, mode = "resize" } end
            return
        end
        self.capture = w
        self:sendMouse(w, "mouse_click", btn, x, y)
        if touch then
            self:sendMouse(w, "mouse_up", btn, x, y)
            self.capture = nil
        end
        return
    end
    local hit = self.iconHits:at(x, y)
    if hit then
        local dbl = self.lastIcon == hit.id and (now - (self.lastIconTime or 0)) < 0.6
        local xy = self.iconXY[hit.id]
        self.selIcon = hit.id
        self.lastIcon, self.lastIconTime = hit.id, now
        if dbl or touch then
            self.selIcon = nil
            self.lastIcon = nil
            self:openApp(hit.id)
        else
            self.idrag = { id = hit.id, ox = x - xy[1], oy = y - xy[2], x = x, y = y, open = self.cfg.singleTap }
        end
        return
    end
    self.selIcon = nil
end

function WM:onDrag(btn, x, y)
    local ic = self.idrag
    if ic then
        if x ~= ic.x or y ~= ic.y then
            ic.moved = true
            self.ipos[ic.id] = { math.max(1, math.min(self.W - 5, x - ic.ox)), math.max(self.top, math.min(self.bottom - 3, y - ic.oy)) }
        end
        return
    end
    local d = self.drag
    if d then
        local w = d.win
        if d.mode == "move" then
            w.x, w.y = x - d.ox, y
            w.snapped = nil
            d.x, d.y = x, y
            self:clampWin(w)
        else
            self:setGeometry(w, w.x, w.y, x - w.x + 1, y - w.y + 1)
        end
        return
    end
    if self.capture then self:sendMouse(self.capture, "mouse_drag", btn, x, y) end
end

function WM:onUp(btn, x, y)
    local ic = self.idrag
    if ic then
        self.idrag = nil
        if ic.open and not ic.moved then self.selIcon, self.lastIcon = nil, nil self:openApp(ic.id) end
        return
    end
    if self.drag then
        local d = self.drag
        self.drag = nil
        if d.mode == "move" and d.x and not d.win.fixed and self.W >= 40 then
            if x <= 1 then self:snap(d.win, "left")
            elseif x >= self.W then self:snap(d.win, "right")
            elseif y <= self.top and d.win.state ~= "max" then self:toggleMax(d.win) end
        end
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
        for i = #self.wins, 1, -1 do list[#list + 1] = self.wins[i] end
        if #list < 2 then return end
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
    if self.menuOpen and (name == "char" or name == "key") then
        if name == "char" then self.search = (self.search .. ev[2]):sub(1, 20)
        elseif ev[2] == keys.backspace then self.search = self.search:sub(1, -2)
        elseif ev[2] == keys.enter then
            local m = self.scene and self.scene.menu
            self.menuOpen = false
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
                if code == keys.left then self:snap(f, "left") return end
                if code == keys.right then self:snap(f, "right") return end
                if code == keys.up then self:toggleMax(f) return end
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
    while self.running do
        local ev = table.pack(os.pullEventRaw())
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
