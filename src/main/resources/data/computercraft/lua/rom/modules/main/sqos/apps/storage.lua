local theme = require("sqos.theme")
local ui = require("sqos.ui")
local lang = require("sqos.lang")
local C = theme.C

local M = {}

local S = {
    de = {
        total = "Gesamt", racks = "Racks", search = "Suche", clear = "x", items = "Sorten", rackTab = "Racks",
        byName = "A-Z", byCount = "Menge", none = "Nichts gefunden", empty = "Speicher ist leer",
        key = "Schluessel", count = "Anzahl", reserved = "reserviert", available = "frei", incomplete = "Netz unvollstaendig",
        online = "online", offline = "ohne Strom", prio = "Prio", drives = "Platten", noRacks = "Keine Racks im Netz",
        typesFull = "Sorten", more = "und %d weitere", error = "Fehler: %s", types = "Sorten",
    },
    en = {
        total = "Total", racks = "Racks", search = "Search", clear = "x", items = "Items", rackTab = "Racks",
        byName = "A-Z", byCount = "Count", none = "Nothing found", empty = "Storage is empty",
        key = "Key", count = "Count", reserved = "reserved", available = "free", incomplete = "Network incomplete",
        online = "online", offline = "no power", prio = "Prio", drives = "Drives", noRacks = "No racks on the network",
        typesFull = "Types", more = "and %d more", error = "Error: %s", types = "Types",
    },
}

local function tr(key, ...)
    local s = (S[lang.code] or S.de)[key] or S.de[key] or key
    if select("#", ...) > 0 then return s:format(...) end
    return s
end

local MAX_SORTS = 2000

local function findStorage()
    if not peripheral.find then return nil end
    return peripheral.find("sqc_storage")
end

local function fetchItems(p, query)
    if query ~= "" then return p.find(query, 500) end
    local out, offset = {}, 0
    while #out < MAX_SORTS do
        local page = p.list(offset, 500)
        for _, r in ipairs(page) do out[#out + 1] = r end
        if #page < 500 then break end
        offset = offset + 500
    end
    return out
end

function M.run(ctx)
    local p = findStorage()
    if not p then return require("sqos.apps.plant").runStorage(ctx) end
    local T = ctx.T
    local info, racks, items = nil, {}, {}
    local err
    local query, tab, sortMode = "", "items", "name"
    local top, selected = 1, nil
    local grab
    local hits = ui.hits()
    local timer = os.startTimer(0.2)
    local loaded = false

    local function refresh()
        local ok, e = pcall(function()
            info = p.getInfo()
            racks = p.getRacks()
            items = fetchItems(p, query)
        end)
        err = (not ok) and tostring(e) or nil
        loaded = true
        if sortMode == "count" then
            table.sort(items, function(a, b)
                if a.count ~= b.count then return a.count > b.count end
                return tostring(a.label) < tostring(b.label)
            end)
        end
    end

    local function listRows()
        local _, h = term.getSize()
        return math.max(1, h - 4)
    end

    local function draw()
        local w, h = term.getSize()
        hits:clear()
        ui.fill(term, 1, 1, w, h, C.face)
        if err then
            ui.para(term, 1, 2, w, tr("error", err), C.alarm, C.face)
            return
        end
        if not info then return end
        ui.fill(term, 1, 1, w, 2, C.panel)
        local used, cap = info.used or 0, info.capacity or 0
        local label = tr("total") .. " " .. ui.num(used) .. "/" .. ui.num(cap)
        ui.put(term, 2, 1, ui.clip(label, w - 2), C.text, C.panel)
        local bx = #label + 3
        if bx + 6 <= w then
            local frac = cap > 0 and used / cap or 0
            ui.bar(term, bx, 1, math.min(24, w - bx), frac, frac > 0.9 and C.warn or C.bar, C.barBg)
        end
        local status = tr("racks") .. " " .. (info.onlineRacks or 0) .. "/" .. (info.racks or 0)
            .. "   " .. tr("types") .. " " .. (info.types or 0)
        if info.complete == false then status = status .. "   " .. tr("incomplete") end
        ui.put(term, 2, 2, ui.clip(status, w - 2), info.complete == false and C.warn or C.dim, C.panel)
        local x = hits:tabs(term, 1, 3, { { label = tr("items"), id = "tab_items", data = "items" }, { label = tr("rackTab"), id = "tab_racks", data = "racks" } }, tab)
        if tab == "items" then
            local sortW = math.max(#tr("byName"), #tr("byCount")) + 2
            local fw = w - x - sortW - (query ~= "" and 4 or 0)
            if fw >= 8 then
                ui.field(term, x, 3, fw, query, not ctx.touch, tr("search"))
                hits:add(x, 3, fw, 1, "search")
                x = x + fw + 1
            end
            if query ~= "" then x = x + hits:button(term, x, 3, tr("clear"), "clear", C.text, C.light) + 1 end
            hits:button(term, w - sortW + 1, 3, sortMode == "name" and tr("byName") or tr("byCount"), "sort")
        end
        local rows = listRows()
        local listW = w - 1
        if tab == "items" then
            if #items == 0 and loaded then ui.empty(term, 1, 5, w, rows, query ~= "" and tr("none") or tr("empty")) end
            if top > #items then top = math.max(1, #items) end
            for i = 0, rows - 1 do
                local r = items[top + i]
                if not r then break end
                local y = 5 + i
                local isSel = r.key == selected
                local bg, fg = isSel and C.sel or C.face, isSel and C.selText or C.text
                local cnt = ui.num(r.count)
                ui.put(term, 1, y, " " .. ui.fit(r.label, math.max(1, listW - #cnt - 3)) .. " " .. cnt .. " ", fg, bg)
                hits:add(1, y, listW, 1, "item", r.key)
            end
            local sel
            for _, r in ipairs(items) do if r.key == selected then sel = r break end end
            if sel then
                local d = " " .. sel.key .. "  " .. tr("count") .. " " .. sel.count
                if (sel.reserved or 0) > 0 then d = d .. "  " .. tr("reserved") .. " " .. sel.reserved end
                ui.put(term, 1, h, ui.fit(d, w), C.text, C.panel)
            end
            hits:scrollbar(term, w, 5, 4 + rows, top, #items, rows, "up", "down")
        else
            if #racks == 0 and loaded then ui.empty(term, 1, 5, w, rows, tr("noRacks")) end
            local per = 3
            local shownRacks = math.floor(rows / per)
            for i = 0, shownRacks - 1 do
                local r = racks[top + i]
                if not r then break end
                local y = 5 + i * per
                ui.card(term, 1, y, listW - 1, 2)
                local head = "#" .. r.index .. "  " .. (r.online and tr("online") or tr("offline"))
                    .. "   " .. tr("prio") .. " " .. r.priority .. "   " .. tr("drives") .. " " .. r.drives
                ui.put(term, 2, y, ui.fit(head, listW - 3), r.online and C.text or C.alarm, C.panel)
                local stat = ui.num(r.used) .. "/" .. ui.num(r.capacity)
                ui.put(term, 2, y + 1, stat, C.dim, C.panel)
                local frac = r.capacity > 0 and r.used / r.capacity or 0
                ui.bar(term, #stat + 3, y + 1, math.max(0, math.min(24, listW - #stat - 4)), frac, frac > 0.9 and C.warn or C.bar, C.barBg)
            end
            hits:scrollbar(term, w, 5, 4 + rows, top, #racks, shownRacks, "up", "down")
        end
    end

    while true do
        draw()
        local ev = { os.pullEvent() }
        local e = ev[1]
        if e == "timer" and ev[2] == timer then
            refresh()
            timer = os.startTimer(5)
        elseif e == "mouse_click" then
            local r = hits:at(ev[3], ev[4])
            if r then
                if r.id == "tab_items" then tab = "items" top = 1
                elseif r.id == "tab_racks" then tab = "racks" top = 1
                elseif r.id == "clear" then query = "" top = 1 refresh()
                elseif r.id == "sort" then sortMode = sortMode == "name" and "count" or "name" refresh()
                elseif r.id == "item" then selected = r.data grab = { y = ev[4], top = top }
                elseif r.id == "up" then top = math.max(1, top - listRows())
                elseif r.id == "down" then top = top + listRows() end
            end
        elseif e == "mouse_drag" and grab then
            top = math.max(1, grab.top - (ev[4] - grab.y))
        elseif e == "mouse_up" then grab = nil
        elseif e == "mouse_scroll" then
            top = math.max(1, top + ev[2] * 3)
        elseif e == "char" and tab == "items" then
            query = query .. ev[2]
            top = 1
            refresh()
        elseif e == "key" and ev[2] == keys.backspace and tab == "items" and #query > 0 then
            query = query:sub(1, -2)
            top = 1
            refresh()
        elseif e == "peripheral" or e == "peripheral_detach" then
            p = findStorage()
            if not p then return require("sqos.apps.plant").runStorage(ctx) end
        end
    end
end

return M
