local theme = require("sqos.theme")
local ui = require("sqos.ui")
local C = theme.C

local M = {}

local OPENS = { sqc_network = "plant", sqc_machine = "plant", sqc_reactor = "plant", sqc_storage = "storage", sqc_bank = "bank",
    sqc_image_monitor = nil }

local function devices()
    local out = {}
    for _, n in ipairs(peripheral.getNames()) do out[#out + 1] = { name = n, type = tostring(peripheral.getType(n)) } end
    table.sort(out, function(a, b) return a.name < b.name end)
    return out
end

local function fmtTime(sec)
    sec = math.floor(sec)
    local h, m = math.floor(sec / 3600), math.floor(sec / 60) % 60
    if h > 0 then return string.format("%dh %02dmin", h, m) end
    return string.format("%dmin %02ds", m, sec % 60)
end

function M.run(ctx)
    local T = ctx.T
    local hits = ui.hits()
    local label = os.getComputerLabel and os.getComputerLabel() or ""
    local editing, top = false, 1
    local timer = os.startTimer(1)
    local msg = ""

    local function draw()
        local w, h = term.getSize()
        hits:clear()
        ui.fill(term, 1, 1, w, h, C.face)
        local id = os.getComputerID and os.getComputerID() or "?"
        ui.header(term, w, T("app_system"), "ID " .. tostring(id))
        local y = 3
        ui.put(term, 2, y, T("sy_label"), C.dim, C.face)
        local fw = math.max(8, math.min(24, w - #T("sy_set") - 6))
        ui.field(term, 2, y + 1, fw, label, editing, T("sy_noname"))
        hits:add(2, y + 1, fw, 1, "edit")
        hits:button(term, 3 + fw, y + 1, T("sy_set"), "set", C.selText, C.sel)
        y = y + 3
        local free = fs.getFreeSpace and fs.getFreeSpace("/") or nil
        local cap = fs.getCapacity and fs.getCapacity("/") or nil
        local colW = math.floor((w - 3) / 2)
        ui.put(term, 2, y, ui.fit(T("sy_uptime"), colW), C.dim, C.face)
        ui.put(term, 2, y + 1, ui.fit(fmtTime(os.clock()), colW), C.text, C.face)
        if free then
            ui.put(term, 3 + colW, y, ui.fit(T("sy_disk"), colW), C.dim, C.face)
            local txt = ui.num(free) .. " " .. T("sy_free")
            ui.put(term, 3 + colW, y + 1, ui.fit(txt, colW), C.text, C.face)
            if cap and cap > 0 and colW - #txt - 1 >= 4 then
                local frac = 1 - free / cap
                ui.bar(term, 4 + colW + #txt, y + 1, colW - #txt - 1, frac, frac > 0.9 and C.warn or C.bar, C.barBg)
            end
        end
        y = y + 3
        local list = devices()
        ui.put(term, 2, y, ui.fit(T("sy_devices") .. " (" .. #list .. ")", w - 2), C.dim, C.face)
        y = y + 1
        local rows = h - y + 1
        if #list == 0 then ui.put(term, 2, y, T("sy_none"), C.dim, C.face) end
        if top > #list then top = math.max(1, #list) end
        for i = 0, rows - 1 do
            local d = list[top + i]
            if not d then break end
            local app = OPENS[d.type]
            if app and not ctx.wm.byId[app] then app = nil end
            local line = " " .. ui.fit(d.name, math.max(6, math.floor((w - 4) / 2))) .. " " .. d.type
            ui.put(term, 1, y + i, ui.fit(line, w - 1), app and C.text or C.dim, C.face)
            if app then
                ui.put(term, w - #T("open") - 2, y + i, " " .. T("open") .. " ", C.selText, C.sel)
                hits:add(1, y + i, w, 1, "open", app)
            end
        end
        hits:scrollbar(term, w, y, h, top, #list, rows, "up", "down")
        if msg ~= "" then ui.put(term, 2, h, ui.clip(msg, w - 2), C.dim, C.face) end
    end

    while true do
        draw()
        local ev = { os.pullEvent() }
        local e = ev[1]
        if e == "timer" and ev[2] == timer then timer = os.startTimer(1)
        elseif e == "mouse_click" then
            local r = hits:at(ev[3], ev[4])
            editing = r ~= nil and r.id == "edit"
            if r and r.id == "set" then
                pcall(os.setComputerLabel, label ~= "" and label or nil)
                msg = T("se_applied")
            elseif r and r.id == "open" then ctx.open(r.data)
            elseif r and r.id == "up" then top = math.max(1, top - 3)
            elseif r and r.id == "down" then top = top + 3 end
        elseif e == "char" and editing then label = (label .. ev[2]):sub(1, 32)
        elseif e == "key" and editing then
            if ev[2] == keys.backspace then label = label:sub(1, -2)
            elseif ev[2] == keys.enter then pcall(os.setComputerLabel, label ~= "" and label or nil) msg = T("se_applied") editing = false end
        elseif e == "mouse_scroll" then top = math.max(1, top + ev[2])
        end
    end
end

return M
