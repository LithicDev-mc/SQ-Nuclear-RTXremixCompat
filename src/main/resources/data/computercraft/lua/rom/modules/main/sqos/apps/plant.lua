local theme = require("sqos.theme")
local ui = require("sqos.ui")
local C = theme.C

local M = {}
local unpack = table.unpack or unpack

local STATE_COLOR = { ok = C.ok, warning = C.warn, alarm = C.alarm, off = C.dim }
local STATE_ORDER = { alarm = 1, warning = 2, ok = 3, off = 4 }
local STATE_WORD = { ok = "pl_st_ok", warning = "pl_st_warn", alarm = "pl_st_alarm", off = "pl_st_off" }

local function hasType(name, t)
    if peripheral.hasType then
        local ok, r = pcall(peripheral.hasType, name, t)
        return ok and r
    end
    return peripheral.getType(name) == t
end

local function isStorage(e)
    local t = (e.info and e.info.type or ""):lower()
    local n = (e.name or ""):lower()
    if t:find("rack") or t:find("storage") or t:find("drive") or n:find("rack") or n:find("speicher") then return true end
    local v = e.info and e.info.values or {}
    return v.storage_used ~= nil or v.storage_drives ~= nil
end

local function findSources()
    local out = {}
    for _, n in ipairs(peripheral.getNames()) do
        if hasType(n, "sqc_network") then out[#out + 1] = { kind = "net", name = n, p = peripheral.wrap(n) }
        elseif hasType(n, "sqc_machine") then out[#out + 1] = { kind = "machine", name = n, p = peripheral.wrap(n) } end
    end
    return out
end

local function infoKey(info, fallback)
    return table.concat({ tostring(info.type), tostring(info.name), tostring(info.x), tostring(info.y), tostring(info.z) }, "|")
end

local function readAll(sources)
    local jobs, results = {}, {}
    for si, s in ipairs(sources) do
        if s.kind == "machine" then
            jobs[#jobs + 1] = function()
                local ok, info = pcall(s.p.getInfo)
                if ok and type(info) == "table" then results[#results + 1] = { src = s, idx = nil, info = info } end
            end
        else
            local ok, n = pcall(s.p.count)
            if ok and type(n) == "number" then
                for i = 1, n do
                    jobs[#jobs + 1] = function()
                        local ok2, info = pcall(s.p.getInfo, i)
                        if ok2 and type(info) == "table" then results[#results + 1] = { src = s, idx = i, info = info } end
                    end
                end
            end
        end
    end
    local i = 1
    while i <= #jobs do
        local chunk = {}
        for k = i, math.min(#jobs, i + 15) do chunk[#chunk + 1] = jobs[k] end
        local live, ev = #chunk, { n = 0 }
        for k, job in ipairs(chunk) do chunk[k] = coroutine.create(job) end
        while live > 0 do
            for _, co in ipairs(chunk) do
                if coroutine.status(co) ~= "dead" then
                    coroutine.resume(co, unpack(ev, 1, ev.n))
                    if coroutine.status(co) == "dead" then live = live - 1 end
                end
            end
            if live > 0 then ev = table.pack(os.pullEvent("task_complete")) end
        end
        i = i + 16
    end
    local seen, list = {}, {}
    for _, r in ipairs(results) do
        local key = infoKey(r.info)
        if not seen[key] then
            seen[key] = true
            r.key = key
            r.name = tostring(r.info.name or r.info.type or "?")
            r.state = tostring(r.info.state or "off")
            list[#list + 1] = r
        end
    end
    table.sort(list, function(a, b)
        if a.name ~= b.name then return a.name < b.name end
        return a.key < b.key
    end)
    return list
end

local function sortedKeys(info)
    local keysList = {}
    for k in pairs(info.values or {}) do keysList[#keysList + 1] = k end
    local labels = info.labels or {}
    table.sort(keysList, function(a, b) return tostring(labels[a] or a) < tostring(labels[b] or b) end)
    return keysList
end

local function switchKey(info)
    for _, k in ipairs(info.settable or {}) do
        if k == "run" or k == "enable" then return k end
    end
end

local FAV = "/.sqos/plant_fav"

local function loadFav()
    local ok, t = pcall(function()
        local h = fs.open(FAV, "r")
        local d = textutils.unserialise(h.readAll() or "")
        h.close()
        return d
    end)
    return ok and type(t) == "table" and t or {}
end

local function saveFav(t)
    pcall(function()
        local h = fs.open(FAV, "w")
        h.write(textutils.serialise(t))
        h.close()
    end)
end

local function sparkLine(x, y, w, hgt, line, vals, col, bg)
    local lo, hi = vals[1], vals[1]
    for _, v in ipairs(vals) do lo, hi = math.min(lo, v), math.max(hi, v) end
    local lab = line == 1 and ui.num(hi) or (line == hgt and ui.num(lo) or "")
    local lw = 7
    ui.put(term, x, y, ui.fit(lab, lw), C.dim, bg or C.face)
    local cols = w - lw
    local first = math.max(1, #vals - cols + 1)
    for i = first, #vals do
        local level = hi == lo and math.ceil(hgt / 2) or (1 + math.floor((vals[i] - lo) / (hi - lo) * (hgt - 1) + 0.5))
        if level >= hgt + 1 - line then ui.put(term, x + lw + i - first, y, " ", C.text, col or C.bar) end
    end
end

local function setValue(entry, key, value)
    local s = entry.src
    if s.kind == "machine" then return pcall(s.p.set, key, value) end
    return pcall(s.p.set, entry.idx, key, value)
end

function M.make(mode)
    local storageMode = mode == "storage"
    return function(ctx)
        local T = ctx.T
        local entries, selKey = {}, nil
        local tab = "all"
        local listTop, detTop = 1, 1
        local detailOnly = false
        local err = nil
        local hits = ui.hits()
        local known = {}
        local fav, hist, query, selVal = loadFav(), {}, "", nil
        local log, lastState, listW, detMax = {}, {}, 24, 1
        local HIST = 40
        local speaker = peripheral.find and peripheral.find("speaker")
        local timer = os.startTimer(0.2)
        local loaded = false

        local function visible()
            local out = {}
            for _, e in ipairs(entries) do
                local keep = true
                if storageMode and not isStorage(e) then keep = false end
                if tab == "alarms" and not (e.state == "alarm" or e.state == "warning") then keep = false end
                if tab == "fav" and not fav[e.key] then keep = false end
                if query ~= "" and not (e.name:lower():find(query:lower(), 1, true) or tostring(e.info.type or ""):lower():find(query:lower(), 1, true)) then keep = false end
                if keep then out[#out + 1] = e end
            end
            table.sort(out, function(a, b)
                if (fav[a.key] or false) ~= (fav[b.key] or false) then return fav[a.key] ~= nil end
                if tab == "alarms" and STATE_ORDER[a.state] ~= STATE_ORDER[b.state] then return (STATE_ORDER[a.state] or 9) < (STATE_ORDER[b.state] or 9) end
                if a.name ~= b.name then return a.name < b.name end
                return a.key < b.key
            end)
            return out
        end

        local function selected()
            for _, e in ipairs(entries) do if e.key == selKey then return e end end
        end

        local function refresh()
            local ok, res = pcall(function() return readAll(findSources()) end)
            if ok then
                entries = res
                err = nil
                loaded = true
                for _, e in ipairs(entries) do
                    local hk = hist[e.key] or {}
                    hist[e.key] = hk
                    for k, v in pairs(e.info.values or {}) do
                        if type(v) == "number" then
                            local l = hk[k] or {}
                            hk[k] = l
                            l[#l + 1] = v
                            while #l > HIST do table.remove(l, 1) end
                        end
                    end
                end
                local names = {}
                for _, e in ipairs(entries) do
                    names[#names + 1] = e.name
                    if lastState[e.key] ~= e.state then
                        if lastState[e.key] or e.state == "alarm" or e.state == "warning" then
                            local l = log[e.key] or {}
                            log[e.key] = l
                            table.insert(l, 1, { textutils.formatTime(os.time(), true), e.state, tostring(e.info.stateText or "") })
                            l[9] = nil
                        end
                        lastState[e.key] = e.state
                    end
                end
                if not storageMode then ctx.wm.plantNames = names end
                local bad, count, first = false, 0, nil
                for _, e in ipairs(entries) do
                    local alarming = (e.state == "alarm")
                    if alarming then count = count + 1 end
                    if alarming and not known[e.key] then bad = true first = first or e end
                    known[e.key] = alarming or nil
                end
                if not storageMode and ctx.setAlarms then ctx.setAlarms(count) end
                if bad and not storageMode and ctx.notify then
                    ctx.notify(first.name or "?", "alarm", T("pl_alarm"))
                end
                if bad and ctx.cfg.sound ~= false and speaker and not storageMode then
                    pcall(speaker.playNote, "bell", 1, 18)
                end
            else
                err = tostring(res)
            end
        end

        local function counts()
            local c = { ok = 0, warning = 0, alarm = 0, off = 0, all = 0, fav = 0 }
            for _, e in ipairs(entries) do
                if not storageMode or isStorage(e) then
                    c[e.state] = (c[e.state] or 0) + 1
                    c.all = c.all + 1
                    if fav[e.key] then c.fav = c.fav + 1 end
                end
            end
            return c
        end

        local function draw()
            local w, h = term.getSize()
            hits:clear()
            if ctx.graph then ctx.graph() end
            ui.fill(term, 1, 1, w, h, C.face)
            ui.fill(term, 1, 1, w, 1, C.panel)
            local c = counts()
            local function tabLabel(key, n) return w >= 40 and (T(key) .. " " .. n) or T(key) end
            local tabs = { { label = tabLabel("pl_all", c.all), id = "tab", data = "all" } }
            if not storageMode then tabs[#tabs + 1] = { label = tabLabel("pl_alarms", c.alarm + c.warning), id = "tab", data = "alarms" } end
            if w >= 40 then tabs[#tabs + 1] = { label = tabLabel("pl_fav", c.fav), id = "tab", data = "fav" } end
            local x = hits:tabs(term, 1, 1, tabs, tab)
            if storageMode then
                local used, max = 0, 0
                for _, e in ipairs(entries) do
                    if isStorage(e) then
                        local v, m = e.info.values or {}, e.info.max or {}
                        used = used + (v.storage_used or 0)
                        max = max + (m.storage_used or 0)
                    end
                end
                local s = T("st_total") .. " " .. ui.num(used) .. "/" .. ui.num(max)
                if max > 0 and x + #s < w then ui.put(term, w - #s, 1, s, C.text, C.panel) end
            elseif speaker then
                local on = ctx.cfg.sound ~= false
                local s = T("pl_sound") .. (on and " +" or " -")
                if x + #s + 2 <= w then hits:button(term, w - #s - 1, 1, s, "sound", on and C.text or C.dim, C.panel) end
            end
            if err then
                ui.para(term, 1, 3, w, T("pl_error", err), C.alarm, C.face)
                return
            end
            if #entries == 0 and loaded then
                ui.empty(term, 1, 2, w, h - 1, storageMode and T("st_none") or T("pl_none"))
                return
            end
            local list = visible()
            local wide = w >= 46
            listW = wide and math.min(28, math.max(18, math.floor(w * 0.33))) or w
            local word = listW >= 24
            if wide and not selected() and list[1] then selKey = list[1].key end
            local showList = wide or not detailOnly
            local showDetail = wide or detailOnly
            local rows = h - 3
            if showList then
                ui.field(term, 1, 2, listW - 2, query, not ctx.touch, T("search_hint"))
                hits:add(1, 2, listW - 2, 1, "search")
                if listTop > #list then listTop = math.max(1, #list) end
                for i = 0, rows - 1 do
                    local e = list[listTop + i]
                    if not e then break end
                    local y = 4 + i
                    local bg = e.key == selKey and C.light or C.face
                    ui.put(term, 1, y, (" "):rep(listW - 2), C.text, bg)
                    ui.put(term, 2, y, word and ui.fit(T(STATE_WORD[e.state] or "pl_st_off"), 5) or "o", STATE_COLOR[e.state] or C.dim, bg)
                    ui.put(term, word and 8 or 4, y, ui.fit(e.name, listW - (word and 11 or 7)), e.state == "off" and C.dim or C.text, bg)
                    if fav[e.key] then ui.put(term, listW - 3, y, "*", C.accent, bg) end
                    hits:add(1, y, listW - 2, 1, "item", e.key)
                end
                if #list == 0 then ui.put(term, 2, 4, ui.clip(query ~= "" and T("no_match") or T("pl_no_alarm"), listW - 3), C.dim, C.face) end
                hits:scrollbar(term, listW - 1, 4, h, listTop, #list, rows, "lup", "ldown")
            end
            if not showDetail then return end
            local dx = wide and listW + 1 or 1
            local dw = w - dx + 1
            local e = selected()
            if not e then
                ui.empty(term, dx, 2, dw, h - 1, "<-")
                return
            end
            local y = 2
            if not wide then
                hits:button(term, dx, 2, T("back"), "back")
                y = 3
            end
            local info = e.info
            local values, maxs, units, labels = info.values or {}, info.max or {}, info.units or {}, info.labels or {}
            local sk = switchKey(info)
            local rx = dx + dw
            if sk then
                local on = values[sk] ~= 0
                rx = rx - #T("pl_off_btn") - 2
                hits:button(term, rx, y, T("pl_off_btn"), "switch", on and C.text or C.selText, on and C.light or C.alarm, { sk, 0 })
                rx = rx - #T("pl_on") - 3
                hits:button(term, rx, y, T("pl_on"), "switch", on and C.selText or C.text, on and C.sel or C.light, { sk, 1 })
            end
            rx = rx - 4
            ui.put(term, rx, y, " * ", fav[e.key] and C.accent or C.dim, C.light)
            hits:add(rx, y, 3, 1, "fav", e.key)
            local head = math.max(0, rx - dx - 2)
            ui.put(term, dx + 1, y, ui.clip(e.name, head), C.text, C.face)
            local st = T(STATE_WORD[e.state] or "pl_st_off") .. "  " .. tostring(info.stateText or "")
            if head - #e.name - 2 > 3 then ui.put(term, dx + #e.name + 3, y, ui.clip(st, head - #e.name - 2), STATE_COLOR[e.state] or C.dim, C.face) end
            local vy = y + 2
            local avail = h - vy + 1
            local hk = hist[e.key] or {}
            local body = {}
            local function row(fn) body[#body + 1] = fn end
            local dvw = dw - 3
            local x0 = dx + 1
            local cols = dvw >= 66 and 3 or (dvw >= 30 and 2 or 1)
            local cards, plain, cardGraph = {}, {}, {}
            for _, k in ipairs(sortedKeys(info)) do
                if k ~= sk then
                    local v, m = values[k], maxs[k]
                    if cols > 1 and type(v) == "number" and m and m > 0 then cards[#cards + 1] = k else plain[#plain + 1] = k end
                end
            end
            local cw = math.floor((dvw - (cols - 1)) / cols)
            local gh = h >= 34 and 6 or 3
            for i = 1, #cards, cols do
                for line = 1, 3 + gh do
                    row(function(yy)
                        for j = 0, cols - 1 do
                            local k = cards[i + j]
                            if k then
                                local cx = x0 + j * (cw + 1)
                                ui.fill(term, cx, yy, cw, 1, C.panel)
                                if line == 1 then
                                    ui.put(term, cx + 1, yy, ui.clip(tostring(labels[k] or k), cw - 2), C.dim, C.panel)
                                elseif line == 2 then
                                    ui.put(term, cx + 1, yy, ui.clip(ui.num(values[k], units[k]), cw - 2), C.text, C.panel)
                                elseif line == 3 then
                                    ui.bar(term, cx + 1, yy, cw - 2, values[k] / maxs[k], C.bar, C.barBg)
                                elseif hk[k] and #hk[k] > 1 then
                                    if line == 4 then cardGraph[k] = ctx.graph and ctx.graph(cx + 8, yy, cw - 9, math.min(gh, h - yy + 1), hk[k], C.accent2) end
                                    if cardGraph[k] then
                                        local lo, hi = hk[k][1], hk[k][1]
                                        for _, v in ipairs(hk[k]) do lo, hi = math.min(lo, v), math.max(hi, v) end
                                        if line == 4 or line == 3 + gh then ui.put(term, cx + 1, yy, ui.fit(ui.num(line == 4 and hi or lo), 7), C.dim, C.panel) end
                                    else
                                        sparkLine(cx + 1, yy, cw - 2, gh, line - 3, hk[k], C.accent2, C.panel)
                                    end
                                end
                            end
                        end
                    end)
                end
                row(function() end)
            end
            local labW = 0
            for _, k in ipairs(plain) do labW = math.max(labW, #tostring(labels[k] or k)) end
            for _, k in ipairs(plain) do
                local label = tostring(labels[k] or k)
                row(function(yy)
                    local v, m = values[k], maxs[k]
                    local vs = ui.num(v, units[k])
                    local lw = math.max(4, dvw - #vs - 1)
                    ui.put(term, x0, yy, ui.fit(label, lw), k == selVal and C.text or C.dim, C.face)
                    ui.put(term, x0 + lw, yy, " " .. vs, C.text, C.face)
                    if type(v) == "number" then hits:add(x0, yy, dvw, 1, "val", k) end
                    local bw = math.min(dvw - 12, lw - 1) - labW - 1
                    if m and m > 0 and type(v) == "number" and bw >= 4 then ui.bar(term, x0 + labW + 1, yy, bw, v / m, C.bar, C.barBg) end
                end)
                if k == selVal and hk[k] and #hk[k] > 1 then
                    for line = 1, 3 do row(function(yy) sparkLine(x0, yy, dvw, 3, line, hk[k], C.accent2) end) end
                end
            end
            local function section(key)
                if #body > 0 and #plain > 0 then row(function() end) end
                row(function(yy) ui.put(term, x0, yy, ui.clip(T(key):upper(), dvw), C.accent, C.face) end)
            end
            local numeric = {}
            if not storageMode then
                for _, k in ipairs(info.settable or {}) do
                    if k ~= "run" and k ~= "enable" and k ~= "scram" and k:sub(1, 5) ~= "dump." and k ~= "repair" then numeric[#numeric + 1] = k end
                end
            end
            if #numeric > 0 then section("pl_settable") end
            for _, k in ipairs(numeric) do
                local label = tostring(labels[k] or k)
                local function steps(yy, x2)
                    local v = tonumber(values[k]) or 0
                    local big = dvw >= 33
                    if big then x2 = x2 + hits:button(term, x2, yy, "-10", "step", C.text, C.light, { k, -10, v }) + 1 end
                    x2 = x2 + hits:button(term, x2, yy, "-1", "step", C.text, C.light, { k, -1, v }) + 1
                    ui.put(term, x2, yy, ui.center(ui.num(v, units[k]), 11), C.text, C.panel)
                    x2 = x2 + 12
                    x2 = x2 + hits:button(term, x2, yy, "+1", "step", C.text, C.light, { k, 1, v }) + 1
                    if big then hits:button(term, x2, yy, "+10", "step", C.text, C.light, { k, 10, v }) end
                end
                if dvw >= 50 then
                    row(function(yy)
                        ui.put(term, x0, yy, ui.fit(label, dvw - 35), C.text, C.face)
                        steps(yy, x0 + dvw - 33)
                    end)
                else
                    row(function(yy) ui.put(term, x0, yy, ui.fit(label, dvw), C.dim, C.face) end)
                    row(function(yy) steps(yy, x0) end)
                end
            end
            local lg = log[e.key]
            if lg then
                section("pl_log")
                for _, l in ipairs(lg) do
                    row(function(yy)
                        ui.put(term, x0, yy, l[1], C.dim, C.face)
                        ui.put(term, x0 + 6, yy, ui.fit(T(STATE_WORD[l[2]] or "pl_st_off"), 5), STATE_COLOR[l[2]] or C.dim, C.face)
                        ui.put(term, x0 + 12, yy, ui.clip(l[3], dvw - 12), C.text, C.face)
                    end)
                end
            end
            detMax = math.max(1, #body - avail + 1)
            if detTop > detMax then detTop = detMax end
            for i = 0, avail - 1 do
                local fn = body[detTop + i]
                if not fn then break end
                fn(vy + i)
            end
            if #body > avail then hits:scrollbar(term, w, vy, h, detTop, #body, avail, "dup", "ddown") end
        end

        local function move(d)
            local list = visible()
            local at = 0
            for i, e in ipairs(list) do if e.key == selKey then at = i end end
            local e = list[math.max(1, math.min(#list, at + d))]
            if not e then return end
            selKey, detTop, selVal = e.key, 1, nil
            local rows = select(2, term.getSize()) - 3
            for i, v in ipairs(list) do
                if v == e then listTop = math.max(math.min(listTop, i), i - rows + 1) end
            end
        end

        local function switch(e, key, value)
            if ((e.info.values or {})[key] ~= 0) ~= (value ~= 0) then
                setValue(e, key, value)
                refresh()
            end
        end

        local function act(id, data)
            if id == "tab" then tab = data listTop = 1
            elseif id == "item" then selKey = data detailOnly = true detTop = 1 selVal = nil
            elseif id == "fav" then fav[data] = (not fav[data]) or nil saveFav(fav)
            elseif id == "val" then selVal = selVal ~= data and data or nil
            elseif id == "back" then detailOnly = false
            elseif id == "lup" then listTop = math.max(1, listTop - 5)
            elseif id == "ldown" then listTop = math.min(math.max(1, #visible()), listTop + 5)
            elseif id == "dup" then detTop = math.max(1, detTop - 5)
            elseif id == "ddown" then detTop = math.min(detMax, detTop + 5)
            elseif id == "sound" then ctx.cfg.sound = ctx.cfg.sound == false require("sqos.config").save(ctx.cfg)
            elseif id == "switch" then
                local e = selected()
                if e then switch(e, data[1], data[2]) end
            elseif id == "step" then
                local e = selected()
                if e then
                    local key, delta, cur = data[1], data[2], data[3]
                    local nv = cur + delta
                    local mx = (e.info.max or {})[key]
                    if nv < 0 then nv = 0 end
                    if mx and mx > 0 and nv > mx then nv = mx end
                    local ok, res = setValue(e, key, nv)
                    if not ok then err = tostring(res) else err = nil end
                    refresh()
                end
            end
        end

        while true do
            local pick = not storageMode and ctx.wm.plantSelect
            if pick then
                for _, e in ipairs(entries) do
                    if e.name == pick then selKey, detailOnly, detTop, tab, query, ctx.wm.plantSelect = e.key, true, 1, "all", "", nil end
                end
            end
            draw()
            local ev = { os.pullEvent() }
            local e = ev[1]
            if e == "timer" and ev[2] == timer then
                refresh()
                timer = os.startTimer(2)
            elseif e == "mouse_click" then
                local r = hits:at(ev[3], ev[4])
                if r then act(r.id, r.data) end
            elseif e == "mouse_scroll" then
                if ev[3] < listW and select(1, term.getSize()) >= 46 then
                    listTop = math.max(1, listTop + ev[2] * 2)
                else
                    detTop = math.max(1, math.min(detMax, detTop + ev[2] * 2))
                end
            elseif e == "peripheral" or e == "peripheral_detach" then
                refresh()
            elseif e == "char" then query, listTop = (query .. ev[2]):sub(1, 20), 1
            elseif e == "key" then
                local k = ev[2]
                if k == keys.backspace then query, listTop = query:sub(1, -2), 1
                elseif k == keys.up then move(-1)
                elseif k == keys.down then move(1)
                elseif k == keys.pageUp then detTop = math.max(1, detTop - 5)
                elseif k == keys.pageDown then detTop = math.min(detMax, detTop + 5)
                elseif k == keys.enter then
                    local s = selected()
                    local sk = s and switchKey(s.info)
                    if sk then switch(s, sk, (s.info.values or {})[sk] ~= 0 and 0 or 1) end
                end
            end
        end
    end
end

M.run = M.make("plant")
M.runStorage = M.make("storage")

return M
