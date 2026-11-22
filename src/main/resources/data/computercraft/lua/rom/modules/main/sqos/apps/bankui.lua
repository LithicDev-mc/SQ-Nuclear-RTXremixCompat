local theme = require("sqos.theme")
local ui = require("sqos.ui")
local text = require("sqos.apps.bank_text")
local errlog = require("sqos.errlog")
local C = theme.C
local T, money = text.T, text.money

local M = {}

local STORE = "/.sqos/bank_store"
local store

function M.data(user, area)
    if not store then
        local ok, t = pcall(function()
            local h = fs.open(STORE, "r")
            local d = textutils.unserialise(h.readAll() or "")
            h.close()
            return d
        end)
        store = ok and type(t) == "table" and t or {}
    end
    user = tostring(user or "?")
    store[user] = store[user] or {}
    store[user][area] = store[user][area] or {}
    return store[user][area]
end

function M.saveData()
    pcall(function()
        local h = fs.open(STORE, "w")
        h.write(textutils.serialise(store or {}))
        h.close()
    end)
end

function M.stamp(day, time)
    day, time = day or (os.day and os.day() or 0), time or (os.time and os.time() or 0)
    return string.format("T%d %02d:%02d", day, math.floor(time), math.floor(time % 1 * 60))
end

local function findBank()
    return peripheral.find and peripheral.find("sqc_bank") or nil
end

local function seenText()
    local types = {}
    for _, n in ipairs(peripheral.getNames()) do
        local t = peripheral.getType(n)
        types[#types + 1] = tostring(t)
    end
    if #types == 0 then return T("seen_none") end
    return T("seen", table.concat(types, ", "))
end

local function statusText(s)
    return T("st_" .. tostring(s))
end

local FONT = {
    ["0"] = { "111", "101", "101", "101", "111" }, ["1"] = { "010", "110", "010", "010", "111" },
    ["2"] = { "111", "001", "111", "100", "111" }, ["3"] = { "111", "001", "111", "001", "111" },
    ["4"] = { "101", "101", "111", "001", "001" }, ["5"] = { "111", "100", "111", "001", "111" },
    ["6"] = { "111", "100", "111", "101", "111" }, ["7"] = { "111", "001", "010", "010", "010" },
    ["8"] = { "111", "101", "111", "101", "111" }, ["9"] = { "111", "101", "111", "001", "111" },
}

local function bigWidth(digits)
    return #digits * 7 - 1
end

local function drawBig(x, y, digits, color)
    for i = 1, #digits do
        local g = FONT[digits:sub(i, i)]
        if g then
            for row = 1, 5 do
                for col = 1, 3 do
                    if g[row]:sub(col, col) == "1" then
                        ui.fill(term, x + (i - 1) * 7 + (col - 1) * 2, y + row - 1, 2, 1, color)
                    end
                end
            end
        end
    end
end

local function drawChart(x, y, w, hgt, vals, empty)
    if #vals == 0 then
        ui.put(term, x, y + math.floor(hgt / 2), empty or "-", C.dim, C.panel)
        return
    end
    local lo, hi = vals[1], vals[1]
    for _, v in ipairs(vals) do
        if v < lo then lo = v end
        if v > hi then hi = v end
    end
    local hiS, loS = tostring(hi), tostring(lo)
    local lw = math.max(#hiS, #loS)
    ui.put(term, x, y, hiS, C.dim, C.panel)
    ui.put(term, x, y + hgt - 1, loS, C.dim, C.panel)
    local cx, cols = x + lw + 1, w - lw - 1
    if cols < 2 then return end
    local first = #vals > cols and (#vals - cols + 1) or 1
    local span = hi - lo
    for i = first, #vals do
        local v = vals[i]
        local level = span == 0 and math.ceil(hgt / 2) or (1 + math.floor((v - lo) / span * (hgt - 1) + 0.5))
        ui.fill(term, cx + (i - first), y + hgt - level, 1, level, v >= vals[first] and C.ok or C.alarm)
    end
end

local STYLE = {
    primary = function() return C.selText, C.sel end,
    good = function() return C.selText, C.ok end,
    danger = function() return C.selText, C.alarm end,
}

local function styleColors(style)
    local f = STYLE[style]
    if f then return f() end
    return C.text, C.light
end

local function markerColor(status, overdue)
    if overdue then return C.alarm end
    if status == "open" then return C.warn end
    if status == "active" then return C.accent end
    if status == "done" then return C.ok end
    return C.dim
end

local WAIT_TRIES = 44

local function listApp(def)
    local function body(ctx)
        local tr = def.T or T
        local tab, sel, top = 1, nil, 1
        local sb, grab
        local rows, head, hero = {}, {}, nil
        local msg, msgKind = "", nil
        local S = {}
        local A = { S = S }
        local hits = ui.hits()
        local refresh = os.startTimer(0.2)
        local dlg, menuOpen, waiting, info
        local pendingJump
        local draw
        local title = def.title()

        local function reload()
            local p = findBank()
            if not p then
                rows, head, hero, S.st = {}, { { T("no_bank"), C.warn }, seenText() }, nil, nil
                return
            end
            local okS, st = pcall(p.status)
            S.st = okS and type(st) == "table" and st or nil
            if def.poll and S.st and S.st.loggedIn then pcall(def.poll, p, S, A) end
            local ok, r, hd, hr = pcall(def.tabs[tab].load, p, S)
            if ok then
                rows, head, hero = r or {}, hd or {}, hr
            else
                rows, head, hero = {}, { { tostring(r), C.alarm } }, nil
            end
            if sel and sel > #rows then sel = #rows > 0 and #rows or nil end
            if top > #rows then top = 1 end
        end

        local function setMsg(t, kind)
            t = tostring(t)
            msg, msgKind = t, kind
            if kind == "err" then
                errlog.write(title, t)
                local w = term.getSize()
                if #t > w - 6 or t:find("\n", 1, true) then
                    info = { title = T("error_title"), text = t }
                    msg = T("error_title") .. " (" .. errlog.PATH .. ")"
                end
            end
        end

        local function switchTab(i)
            if i < 1 then i = #def.tabs elseif i > #def.tabs then i = 1 end
            tab, sel, top, msg, msgKind = i, nil, 1, "", nil
            S.page = 1
            reload()
        end

        local function applyJump()
            if pendingJump then
                local i = pendingJump
                pendingJump = nil
                switchTab(i)
            end
        end

        local function finish(ok, res)
            waiting = nil
            if ok then setMsg(type(res) == "string" and res or T("done"), "ok") else setMsg(res, "err") end
            applyJump()
            reload()
        end

        local function cancelWait()
            waiting = nil
            setMsg(T("cancel_prompt"), nil)
        end

        local function needsConfirm(err)
            return tostring(err):find("confirmation required", 1, true) ~= nil
        end

        local function run(fn, opts)
            local p = findBank()
            if not p then setMsg(T("no_bank"), "err") return end
            local ok, res = pcall(fn, p)
            if not ok and opts and opts.confirm and needsConfirm(res) then
                waiting = { f = fn, tries = 0, timer = os.startTimer(0.5) }
                return
            end
            finish(ok, res)
        end

        A.run = run
        A.notify = function(t, kind, ttl) if ctx and ctx.notify then ctx.notify(t, kind, ttl or title) end end
        A.msg = setMsg
        A.reload = reload
        A.jump = function(i) pendingJump = i end

        local function openDialog(d)
            d.focus = 1
            d.values = {}
            for i, f in ipairs(d.fields or {}) do d.values[i] = tostring(f.default or "") end
            d.err = nil
            dlg = d
            menuOpen = nil
        end
        A.dialog = openDialog

        local function submitDialog()
            local d = dlg
            local out = {}
            for i, f in ipairs(d.fields) do
                local v = d.values[i]
                if v == "" and not f.optional then
                    d.err = f.label .. " ?"
                    d.focus = i
                    return
                end
                if f.kind == "number" then
                    local n = tonumber(v)
                    if v ~= "" and (not n or n ~= math.floor(n)) then
                        d.err = T("bad_number")
                        d.focus = i
                        return
                    end
                    out[f.id] = n
                else
                    out[f.id] = v
                end
            end
            dlg = nil
            local okc, err = pcall(d.ok, out)
            if not okc then setMsg(err, "err") end
        end

        local function currentActions()
            local t = def.tabs[tab]
            local out = {}
            local bl = type(t.buttons) == "function" and t.buttons(S) or t.buttons
            for _, b in ipairs(bl or {}) do out[#out + 1] = { b[1], function() b[2](A) end, b[3] } end
            if sel and rows[sel] and t.actions then
                local row = rows[sel]
                for _, a in ipairs(t.actions(row, S)) do
                    out[#out + 1] = { a[1], function() a[2](A, row) end, a[3] or "primary", row = true }
                end
            end
            return out
        end

        local function defaultAction()
            for _, a in ipairs(currentActions()) do
                if a.row and a[3] ~= "danger" then
                    a[2]()
                    applyJump()
                    return
                end
            end
        end

        local function drawMenuBar(w)
            ui.fill(term, 1, 1, w, 1, C.panel)
            local x = 1
            for i, m in ipairs(def.menus or {}) do
                local label = " " .. tr(m.label) .. " "
                local open = menuOpen and menuOpen.index == i
                ui.put(term, x, 1, label, open and C.selText or C.text, open and C.sel or C.panel)
                hits:add(x, 1, #label, 1, "menu", i)
                m.x = x
                x = x + #label
            end
            local st = S.st
            local who = st and st.loggedIn and tostring(st.user) or (st and T("not_logged") or "")
            local rx = w - #who
            if #who > 0 then ui.put(term, rx, 1, who, (st and st.loggedIn) and C.text or C.dim, C.panel) end
            ui.put(term, math.max(x, rx - 2), 1, "o", (st and st.online) and C.ok or C.alarm, C.panel)
        end

        local function drawTabs(w)
            ui.fill(term, 1, 2, w, 1, C.face)
            local x = 2
            for i, t in ipairs(def.tabs) do
                local label = tr(t.key)
                local fg, bg
                if i == tab then fg, bg = C.selText, C.sel else fg, bg = C.text, C.light end
                if x + #label + 2 <= w + 1 then
                    x = x + hits:button(term, x, 2, label, "tab", fg, bg, i) + 1
                end
            end
        end

        local function drawHero(y, w, h)
            local cw = w - 2
            if hero.chart then
                local ch = math.max(3, math.min(8, (h - 2 - 3 + 1) - 6))
                ui.fill(term, 2, y, cw, ch + 2, C.panel)
                ui.put(term, 3, y, ui.clip(hero.label, cw - 2), C.text, C.panel)
                drawChart(3, y + 1, cw - 2, ch, hero.chart.values or {}, hero.chart.empty)
                ui.put(term, 3, y + 1 + ch, ui.clip(hero.sub or "", cw - 2), C.dim, C.panel)
                return y + ch + 3
            end
            local big = h >= 17 and hero.digits and bigWidth(hero.digits) <= cw - 2
            local n = big and 7 or 3
            ui.fill(term, 2, y, cw, n, C.panel)
            ui.put(term, 3, y, ui.clip(hero.label, cw - 2), C.dim, C.panel)
            if big then
                drawBig(3, y + 1, hero.digits, C.accent2)
                ui.put(term, 3, y + 6, ui.clip(hero.sub or "", cw - 2), C.dim, C.panel)
            else
                ui.put(term, 3, y + 1, ui.clip(hero.text or hero.digits, cw - 2), C.accent2, C.panel)
                ui.put(term, 3, y + 2, ui.clip(hero.sub or "", cw - 2), C.dim, C.panel)
            end
            return y + n + 1
        end

        local function drawRow(y, x, w, r, isSel, idx)
            local bg = isSel and C.sel or C.face
            local fg = isSel and C.selText or (r.color or C.text)
            ui.fill(term, x, y, w, 1, bg)
            ui.put(term, x, y, r.marker and "o" or " ", r.marker or bg, bg)
            local amountStr = ""
            if r.amount then amountStr = ((r.sign or 0) > 0 and "+" or ((r.sign or 0) < 0 and "-" or "")) .. money(r.amount) end
            local right = (#amountStr > 0) and (#amountStr + 2) or 0
            ui.put(term, x + 2, y, ui.fit(r.text, math.max(1, w - 3 - right - 1)), fg, bg)
            if #amountStr > 0 then
                local col = C.text
                if isSel then col = C.selText
                elseif r.amountColor then col = r.amountColor
                elseif (r.sign or 0) > 0 then col = C.ok
                elseif (r.sign or 0) < 0 then col = C.alarm end
                ui.put(term, x + w - #amountStr - 1, y, amountStr, col, bg)
            end
        end

        local function drawDetail(x, y, w, hgt)
            ui.fill(term, x, y, w, hgt, C.face)
            ui.card(term, x + 1, y, w - 1, hgt, T("detail_text"))
            x = x + 1
            w = w - 1
            local row = sel and rows[sel]
            if not row then
                for i, part in ipairs(errlog.wrap(T("select_hint"), w - 2)) do
                    if i + 1 > hgt then break end
                    ui.put(term, x + 1, y + i, part, C.dim, C.panel)
                end
                return
            end
            local yy = y + 1
            for _, line in ipairs(row.detail or { { T("detail_text"), row.text } }) do
                if yy >= y + hgt then break end
                local label, value = tostring(line[1]) .. ":", tostring(line[2])
                if #label + 1 + #value <= w - 2 then
                    ui.put(term, x + 1, yy, label, C.dim, C.panel)
                    ui.put(term, x + 2 + #label, yy, value, line[3] or C.text, C.panel)
                    yy = yy + 1
                else
                    ui.put(term, x + 1, yy, ui.clip(label, w - 2), C.dim, C.panel)
                    yy = yy + 1
                    for _, part in ipairs(errlog.wrap(value, math.max(8, w - 3))) do
                        if yy >= y + hgt then break end
                        ui.put(term, x + 2, yy, part, line[3] or C.text, C.panel)
                        yy = yy + 1
                    end
                end
            end
        end

        local function drawContent(w, h)
            local y = 3
            if hero then y = drawHero(y, w, h) end
            for _, entry in ipairs(head) do
                local line, color = entry, C.dim
                if type(entry) == "table" then line, color = entry[1], entry[2] or C.dim end
                for _, part in ipairs(errlog.wrap(line, w - 2)) do
                    if y >= h - 2 then break end
                    ui.put(term, 2, y, ui.fit(part, w - 2), color, C.face)
                    y = y + 1
                end
            end
            local listTop, listBottom = y, h - 2
            local avail = listBottom - listTop + 1
            if avail < 1 then return end
            local t = def.tabs[tab]
            local wide = w >= 46 and not t.noDetail and #rows > 0
            local lw = wide and math.floor(w * 0.58) or w
            if #rows == 0 and not hero and #head == 0 then ui.put(term, 2, listTop, T("none"), C.dim, C.face) end
            for i = 0, avail - 1 do
                local idx = top + i
                local r = rows[idx]
                if not r then break end
                drawRow(listTop + i, 1, lw - 1, r, idx == sel, idx)
                hits:add(1, listTop + i, lw - 1, 1, "row", idx)
            end
            sb = nil
            if #rows > avail and avail > 1 then
                sb = { x = lw, y = listTop, h = avail }
                ui.fill(term, lw, listTop, 1, avail, C.panel)
                local th = math.max(1, math.floor(avail * avail / #rows))
                local tp = listTop + math.floor((avail - th) * (top - 1) / math.max(1, #rows - avail))
                ui.fill(term, lw, tp, 1, th, C.dim)
            end
            if wide then drawDetail(lw + 1, listTop, w - lw, avail) end
        end

        local function drawToolbar(w, h)
            ui.fill(term, 1, h - 1, w, 1, C.panel)
            local bx = 2
            for i, a in ipairs(currentActions()) do
                if bx + #a[1] + 2 <= w then
                    local fg, bg = styleColors(a[3])
                    bx = bx + hits:button(term, bx, h - 1, a[1], "act", fg, bg, i) + 1
                end
            end
        end

        local function drawStatus(w, h)
            ui.fill(term, 1, h, w, 1, C.face)
            local col = C.dim
            if msgKind == "err" then col = C.alarm elseif msgKind == "ok" then col = C.ok end
            local right = ""
            if S.st and S.st.balance then right = T("balance") .. " " .. money(S.st.balance) end
            ui.put(term, 2, h, ui.clip(msg, math.max(0, w - #right - 3)), col, C.face)
            if #right > 0 then ui.put(term, w - #right, h, right, C.dim, C.face) end
        end

        local function drawMenuDropdown(w)
            if not menuOpen then return end
            local m = def.menus[menuOpen.index]
            local iw = 0
            for _, it in ipairs(m.items) do
                if it ~= "-" then iw = math.max(iw, #tr(it[1])) end
            end
            iw = iw + 2
            local x = math.min(m.x or 1, math.max(1, w - iw + 1))
            ui.fill(term, x, 2, iw, #m.items, C.panel)
            for i, it in ipairs(m.items) do
                if it == "-" then
                    ui.put(term, x, 1 + i, ("-"):rep(iw), C.dim, C.panel)
                else
                    ui.put(term, x, 1 + i, " " .. ui.fit(tr(it[1]), iw - 1), C.text, C.light)
                    hits:add(x, 1 + i, iw, 1, "menuitem", i)
                end
            end
        end

        local function drawModals(w, h)
            if info then
                local dw = math.min(w - 2, 50)
                local lines = errlog.wrap(info.text, dw - 4)
                local hint = errlog.wrap(T("log_hint", errlog.PATH), dw - 4)
                local maxLines = math.max(1, h - 8 - #hint)
                local n = math.min(#lines, maxLines)
                local x, y = math.floor((w - dw) / 2) + 1, math.max(2, math.floor((h - (n + #hint + 5)) / 2))
                ui.fill(term, x, y, dw, n + #hint + 5, C.panel)
                ui.put(term, x, y, ui.fit(" " .. info.title, dw), C.selText, C.alarm)
                for i = 1, n do ui.put(term, x + 2, y + 1 + i, lines[i], C.text, C.panel) end
                if #lines > n then ui.put(term, x + dw - 5, y + n + 1, "...", C.dim, C.panel) end
                for i, part in ipairs(hint) do ui.put(term, x + 2, y + n + 1 + i, part, C.dim, C.panel) end
                hits:button(term, x + dw - 8, y + n + #hint + 3, T("ok"), "info_ok", C.selText, C.ok)
                if ctx and ctx.open then hits:button(term, x + 2, y + n + #hint + 3, T("open_log"), "info_log", C.text, C.light) end
                return
            end
            if waiting then
                local dw = math.min(w - 2, 46)
                local head = errlog.wrap(T("confirm_wait", math.max(0, math.ceil((WAIT_TRIES - waiting.tries) / 2))), dw - 4)
                local hint = errlog.wrap(T("confirm_hint"), dw - 4)
                local n = #head + #hint
                local x, y = math.floor((w - dw) / 2) + 1, math.max(2, math.floor((h - (n + 5)) / 2))
                ui.fill(term, x, y, dw, n + 5, C.panel)
                ui.put(term, x, y, ui.fit(" " .. T("confirm_title"), dw), C.selText, C.sel)
                for i, part in ipairs(head) do ui.put(term, x + 2, y + 1 + i, part, C.warn, C.panel) end
                for i, part in ipairs(hint) do ui.put(term, x + 2, y + 1 + #head + i, part, C.dim, C.panel) end
                hits:button(term, x + dw - 14, y + n + 3, T("btn_cancel"), "wait_cancel", C.selText, C.alarm)
                return
            end
            if not dlg then return end
            local nf = #dlg.fields
            local dw = math.min(w - 2, 44)
            local dh = nf + 5
            local x, y = math.floor((w - dw) / 2) + 1, math.max(2, math.floor((h - dh) / 2))
            ui.fill(term, x, y, dw, dh, C.panel)
            ui.put(term, x, y, ui.fit(" " .. dlg.title, dw), C.selText, C.sel)
            local lw = 0
            for _, f in ipairs(dlg.fields) do lw = math.max(lw, #f.label) end
            for i, f in ipairs(dlg.fields) do
                local fy = y + 1 + i
                ui.put(term, x + 1, fy, ui.fit(f.label, lw), C.dim, C.panel)
                local ix, iw = x + 2 + lw, dw - lw - 3
                local focus = dlg.focus == i
                local val = dlg.values[i]
                local shown = val
                if #val > iw - 1 then shown = val:sub(#val - iw + 2) end
                ui.fill(term, ix, fy, iw, 1, focus and C.light or C.face)
                ui.put(term, ix, fy, " " .. shown .. (focus and "_" or ""), C.text, focus and C.light or C.face)
                hits:add(ix, fy, iw, 1, "field", i)
            end
            if dlg.err then ui.put(term, x + 1, y + nf + 2, ui.clip(dlg.err, dw - 2), C.alarm, C.panel) end
            local by = y + nf + 3
            hits:button(term, x + dw - 24, by, T("ok"), "dlg_ok", C.selText, C.sel)
            hits:button(term, x + dw - 12, by, T("btn_cancel"), "dlg_cancel", C.text, C.light)
        end

        function draw()
            local w, h = term.getSize()
            hits:clear()
            ui.fill(term, 1, 1, w, h, C.face)
            if dlg or waiting or info then
                local real = hits
                hits = ui.hits()
                drawMenuBar(w)
                drawTabs(w)
                drawContent(w, h)
                drawToolbar(w, h)
                drawStatus(w, h)
                hits = real
            else
                drawMenuBar(w)
                drawTabs(w)
                drawContent(w, h)
                drawToolbar(w, h)
                drawStatus(w, h)
                drawMenuDropdown(w)
            end
            drawModals(w, h)
        end

        local function dialogKey(k)
            local nf = #dlg.fields
            if k == keys.enter then
                if dlg.focus < nf then dlg.focus = dlg.focus + 1 else submitDialog() end
            elseif k == keys.tab or k == keys.down then
                dlg.focus = dlg.focus % nf + 1
            elseif k == keys.up then
                dlg.focus = (dlg.focus - 2) % nf + 1
            elseif k == keys.backspace then
                dlg.values[dlg.focus] = dlg.values[dlg.focus]:sub(1, -2)
                dlg.err = nil
            elseif k == keys.escape then
                dlg = nil
            end
        end

        local function dialogChar(c)
            local f = dlg.fields[dlg.focus]
            if f.kind == "number" and not c:match("^%d$") then return end
            local v = dlg.values[dlg.focus]
            if #v < (f.max or 40) then dlg.values[dlg.focus] = v .. c end
            dlg.err = nil
        end

        local function runAction(i)
            local a = currentActions()[i]
            if a then a[2]() end
            applyJump()
        end

        local function menuPick(i)
            local m = def.menus[menuOpen.index]
            local it = m.items[i]
            menuOpen = nil
            if it and it ~= "-" then
                local ok, err = pcall(it[2], A)
                if not ok then setMsg(err, "err") end
                applyJump()
            end
        end

        local function openLog()
            info = nil
            if ctx and ctx.open then pcall(ctx.open, "editor", { path = "/" .. errlog.PATH }) end
        end

        while true do
            draw()
            local ev = { os.pullEvent() }
            local e = ev[1]
            if e == "timer" then
                if waiting and ev[2] == waiting.timer then
                    local p = findBank()
                    if not p then
                        finish(false, T("no_bank"))
                    else
                        local ok, res = pcall(waiting.f, p)
                        if ok then
                            finish(true, res)
                        elseif needsConfirm(res) and waiting.tries < WAIT_TRIES then
                            waiting.tries = waiting.tries + 1
                            waiting.timer = os.startTimer(0.5)
                        else
                            finish(false, needsConfirm(res) and T("confirm_timeout") or res)
                        end
                    end
                elseif ev[2] == refresh then
                    if not dlg and not waiting then reload() end
                    refresh = os.startTimer(3)
                end
            elseif e == "mouse_click" then
                local r = hits:at(ev[3], ev[4])
                if info then
                    if r and r.id == "info_ok" then info = nil
                    elseif r and r.id == "info_log" then openLog() end
                elseif waiting then
                    if r and r.id == "wait_cancel" then cancelWait() end
                elseif dlg then
                    if r and r.id == "field" then dlg.focus = r.data
                    elseif r and r.id == "dlg_ok" then submitDialog()
                    elseif r and r.id == "dlg_cancel" then dlg = nil end
                elseif menuOpen then
                    if r and r.id == "menuitem" then menuPick(r.data)
                    elseif r and r.id == "menu" then
                        menuOpen = (menuOpen.index ~= r.data) and { index = r.data } or nil
                    else menuOpen = nil end
                elseif sb and ev[3] == sb.x and ev[4] >= sb.y and ev[4] < sb.y + sb.h then
                    grab = "bar"
                    top = math.max(1, math.min(#rows - sb.h + 1, 1 + math.floor((ev[4] - sb.y) / (sb.h - 1) * (#rows - sb.h) + 0.5)))
                elseif r then
                    if r.id == "row" then grab = { y = ev[4], top = top } end
                    if r.id == "menu" then menuOpen = { index = r.data }
                    elseif r.id == "tab" then switchTab(r.data)
                    elseif r.id == "row" then
                        if sel == r.data and ev[2] == 1 then defaultAction() else sel = r.data end
                    elseif r.id == "act" then runAction(r.data) end
                end
            elseif e == "char" then
                if dlg and not waiting and not info then dialogChar(ev[2]) end
            elseif e == "paste" then
                if dlg and not waiting and not info then
                    for c in tostring(ev[2]):gmatch(".") do dialogChar(c) end
                end
            elseif e == "mouse_drag" and grab and not dlg and (grab ~= "bar" or sb) then
                if grab == "bar" then
                    top = math.max(1, math.min(#rows - sb.h + 1, 1 + math.floor((ev[4] - sb.y) / (sb.h - 1) * (#rows - sb.h) + 0.5)))
                else top = math.max(1, math.min(#rows, grab.top - (ev[4] - grab.y))) end
            elseif e == "mouse_up" then grab = nil
            elseif e == "mouse_scroll" then
                if not dlg and not waiting and not info then top = math.max(1, math.min(math.max(1, #rows), top + ev[2] * 2)) end
            elseif e == "key" then
                local k = ev[2]
                if info then
                    if k == keys.enter or k == keys.escape then info = nil end
                elseif waiting then
                    if k == keys.escape then cancelWait() end
                elseif dlg then
                    dialogKey(k)
                elseif menuOpen then
                    if k == keys.escape then menuOpen = nil end
                else
                    if k == keys.up and #rows > 0 then sel = math.max(1, (sel or 2) - 1)
                    elseif k == keys.down and #rows > 0 then sel = math.min(#rows, (sel or 0) + 1)
                    elseif k == keys.left then switchTab(tab - 1)
                    elseif k == keys.right or k == keys.tab then switchTab(tab + 1)
                    elseif k == keys.f5 then reload()
                    elseif k == keys.enter and sel then defaultAction() end
                    if sel then
                        local _, h = term.getSize()
                        local avail = math.max(1, h - 6)
                        if sel < top then top = sel elseif sel > top + avail - 1 then top = sel - avail + 1 end
                    end
                end
            elseif e == "peripheral" or e == "peripheral_detach" then
                if not waiting then reload() end
            end
        end
    end

    local function crashScreen(ctx, err)
        errlog.write(def.title(), err)
        local hitsC = ui.hits()
        local timerC = os.startTimer(0)
        while true do
            local w, h = term.getSize()
            hitsC:clear()
            ui.fill(term, 1, 1, w, h, C.face)
            ui.fill(term, 1, 1, w, 1, C.alarm)
            ui.put(term, 2, 1, ui.clip(T("crash_title"), w - 2), C.selText, C.alarm)
            local lines = errlog.wrap(tostring(err), w - 2)
            for i = 1, math.min(#lines, h - 5) do ui.put(term, 2, 2 + i, lines[i], C.text, C.face) end
            local hintLines = errlog.wrap(T("log_hint", errlog.PATH), w - 2)
            for i, part in ipairs(hintLines) do ui.put(term, 2, h - 1 - #hintLines + i - 1, part, C.dim, C.face) end
            ui.fill(term, 1, h, w, 1, C.panel)
            local bx = 2
            bx = bx + hitsC:button(term, bx, h, T("restart"), "restart", C.selText, C.ok) + 1
            if ctx and ctx.open then hitsC:button(term, bx, h, T("open_log"), "log", C.text, C.light) end
            local ev = { os.pullEvent() }
            if ev[1] == "mouse_click" then
                local r = hitsC:at(ev[3], ev[4])
                if r and r.id == "restart" then return true end
                if r and r.id == "log" and ctx and ctx.open then pcall(ctx.open, "editor", { path = "/" .. errlog.PATH }) end
            elseif ev[1] == "key" and (ev[2] == keys.enter or ev[2] == keys.r) then
                return true
            end
        end
    end

    return function(ctx)
        while true do
            local ok, err = pcall(body, ctx)
            if ok then return end
            if tostring(err):find("Terminated", 1, true) then error(err, 0) end
            if not crashScreen(ctx, err) then return end
        end
    end
end

M.findBank = findBank
M.seenText = seenText
M.statusText = statusText
M.markerColor = markerColor
M.listApp = listApp
M.drawBig = drawBig
M.bigWidth = bigWidth
M.drawChart = drawChart

return M
