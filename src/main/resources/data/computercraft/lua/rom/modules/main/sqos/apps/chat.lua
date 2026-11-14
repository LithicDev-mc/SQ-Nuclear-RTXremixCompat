local theme = require("sqos.theme")
local ui = require("sqos.ui")
local C = theme.C

local M = {}

local PUB, MAX, FILE, POLL = "*", 100, "/.sqos/chat", 3
local store

local function loadAll()
    local ok, d = pcall(function()
        if not fs.exists(FILE) then return {} end
        local h = fs.open(FILE, "r")
        local t = textutils.unserialise(h.readAll() or "")
        h.close()
        return t
    end)
    return ok and type(d) == "table" and d or {}
end

local function saveAll()
    pcall(function()
        local h = fs.open(FILE, "w")
        h.write(textutils.serialise(store))
        h.close()
    end)
end

local function wrap(text, width)
    local out = {}
    text = tostring(text)
    if width < 2 then return { text } end
    while #text > width do
        local cut = width
        local sp = text:sub(1, width + 1):match("^.*() ")
        if sp and sp > width / 2 then cut = sp - 1 end
        out[#out + 1] = text:sub(1, cut)
        text = text:sub(cut + 1):gsub("^ +", "")
    end
    out[#out + 1] = text
    return out
end

function M.run(ctx)
    local T, wm = ctx.T, ctx.wm
    store = store or loadAll()
    local me, d, status, tid
    local cur, input, scroll, adding = PUB, "", 0, false
    local unread = {}
    local hits = ui.hits()

    local function ensure(key)
        if not d.logs[key] then
            d.logs[key] = {}
            table.insert(d.order, 2, key)
        end
        return d.logs[key]
    end

    local function add(key, from, text, mine, tm)
        local log = ensure(key)
        log[#log + 1] = { f = from, t = text, m = mine, tm = tm }
        while #log > MAX do table.remove(log, 1) end
        if key ~= PUB then
            for i, k in ipairs(d.order) do
                if k == key then table.remove(d.order, i) break end
            end
            table.insert(d.order, 2, key)
        end
    end

    local function mentions(text)
        return me and (" " .. text:lower() .. " "):find("%f[%w_]" .. me:lower():gsub("%W", "%%%0") .. "%f[^%w_]") ~= nil
    end

    local function stamp(tm)
        if type(tm) ~= "number" then return nil, "" end
        local t = tm + 6000
        return math.floor(t / 24000), string.format("%02d:%02d", math.floor(t % 24000 / 1000), math.floor(t % 1000 * 60 / 1000))
    end

    local function sync()
        local bank = peripheral.find and peripheral.find("sqc_bank")
        if not bank then status, me = "chat_nobank", nil return end
        local ok, st = pcall(bank.status)
        if not ok then status, me = "chat_nobank", nil return end
        if not st.online then status, me = "chat_offline", nil return end
        if not st.loggedIn then status, me = "chat_login", nil return end
        if st.user ~= me then
            me = st.user
            d = store[me]
            if type(d) ~= "table" or type(d.order) ~= "table" or type(d.logs) ~= "table" then
                d = { order = { PUB }, logs = { [PUB] = {} }, last = 0 }
                store[me] = d
            end
            cur, scroll, adding, unread = PUB, 0, false, {}
        end
        local ok2, list = pcall(bank.chatFetch, d.last or 0)
        if not ok2 then status = tostring(list) return end
        status = nil
        for _, m in ipairs(list) do
            local mine = m.from == me
            add(m.to == "" and PUB or (mine and m.to or m.from), m.from, m.text, mine, m.time)
            d.last = math.max(d.last or 0, m.id)
            if not mine then
                local key = m.to == "" and PUB or m.from
                if key ~= cur then unread[key] = (unread[key] or 0) + 1 else scroll = 0 end
                if mentions(m.text) then ctx.notify(m.text, "warn", "@" .. m.from)
                elseif wm.focus ~= ctx.win then ctx.notify(m.text, "info", m.from) end
                local total = 0
                for _, n in pairs(unread) do total = total + n end
                if ctx.badge then ctx.badge(total) end
            end
        end
        if #list > 0 then saveAll() end
    end

    local function title(key)
        return key == PUB and T("chat_public") or key
    end

    local function lines(width)
        local out, lastDay = {}, nil
        for _, m in ipairs(d and d.logs[cur] or {}) do
            local day, clock = stamp(m.tm)
            if day and day ~= lastDay then
                out[#out + 1] = { text = T("chat_day", day), sep = true }
                lastDay = day
            end
            local head = (cur == PUB and not m.m) and (m.f .. ": ") or ""
            local hi = not m.m and mentions(m.t)
            for i, l in ipairs(wrap(head .. m.t, width)) do
                out[#out + 1] = { text = l, mine = m.m, hi = hi, clock = i == 1 and clock or nil }
            end
        end
        return out
    end

    local function draw()
        local w, h = term.getSize()
        local lw = w >= 40 and 14 or 9
        local x0, rw = lw + 2, w - lw - 1
        hits:clear()
        ui.fill(term, 1, 1, w, h, C.face)
        ui.fill(term, 1, 1, lw, h, C.panel)
        ui.fill(term, lw + 1, 1, 1, h, C.light)
        ui.put(term, 1, 1, ui.fit(" " .. T("app_chat"), lw), C.dim, C.panel)
        for i, k in ipairs(d and d.order or {}) do
            local y = 1 + i
            if y >= h then break end
            local on = k == cur
            local n = unread[k]
            local label = ui.fit(" " .. ui.clip(title(k), lw - (n and 4 or 2)), lw - (n and #tostring(n) + 1 or 0))
            ui.put(term, 1, y, label, on and C.selText or C.text, on and C.sel or C.panel)
            if n then ui.put(term, lw - #tostring(n), y, tostring(n) .. " ", C.warn, on and C.sel or C.panel) end
            hits:add(1, y, lw, 1, "chat", k)
        end
        hits:button(term, 1, h, T("chat_new"), "new", C.selText, C.sel)
        local line = status and (status:sub(1, 5) == "chat_" and T(status) or status) or (me .. "  " .. T("chat_online"))
        ui.put(term, x0, 1, ui.fit(" " .. title(cur), rw), C.text, C.panel)
        local right = ui.clip(line, math.max(0, rw - #title(cur) - 3))
        ui.put(term, w - #right, 1, right, status and C.warn or (status == nil and C.ok or C.dim), C.panel)
        local ls = lines(rw - 12)
        local rows = h - 2
        scroll = math.min(scroll, math.max(0, #ls - rows))
        local first = math.max(1, #ls - rows + 1 - scroll)
        if status and status:sub(1, 5) == "chat_" then
            for i, l in ipairs(wrap(T("chat_help"), rw - 2)) do ui.put(term, x0 + 1, 2 + i, l, C.dim, C.face) end
        elseif #ls == 0 and cur == PUB then ui.put(term, x0 + 1, 3, ui.clip(T("chat_welcome"), rw - 2), C.dim, C.face) end
        for i = 0, rows - 1 do
            local l = ls[first + i]
            if l and l.sep then
                ui.put(term, x0 + math.floor((rw - #l.text) / 2), 2 + i, l.text, C.dim, C.face)
            elseif l then
                local s = " " .. l.text .. " "
                local bx = l.mine and (w - #s - 5) or (x0 + 6)
                ui.put(term, bx, 2 + i, s, l.mine and C.selText or C.text, l.mine and C.sel or (l.hi and C.warn or C.light))
                if l.clock then ui.put(term, l.mine and (w - 4) or x0, 2 + i, l.clock, C.dim, C.face) end
            end
        end
        local prompt = adding and T("chat_to") or ""
        local shown = prompt .. input
        local hint = adding and "" or (ctx.touch and T("chat_keyboard") or "> ")
        ui.field(term, x0, h, rw, shown, not ctx.touch, hint)
        if not ctx.touch then
            term.setCursorPos(math.min(w, x0 + 1 + #shown:sub(-(rw - 2))), h)
            term.setCursorBlink(true)
            term.setTextColor(C.text)
        end
    end

    local function send(text)
        if not me then return end
        if adding then
            adding = false
            text = text:sub(1, 16)
            for _, k in ipairs(d.order) do
                if k:lower() == text:lower() then text = k end
            end
            if text ~= "" and text ~= me and text ~= PUB then
                ensure(text)
                cur, scroll = text, 0
            end
        elseif text == "/clear" then
            d.logs[cur] = {}
            saveAll()
        else
            local bank = peripheral.find("sqc_bank")
            local ok, err = pcall(bank.chatSend, cur == PUB and "" or cur, text)
            if not ok then status = tostring(err) return end
            sync()
        end
    end

    sync()
    tid = os.startTimer(POLL)
    while true do
        draw()
        local ev = { os.pullEvent() }
        local e = ev[1]
        if e == "timer" and ev[2] == tid then
            sync()
            tid = os.startTimer(POLL)
        elseif e == "mouse_click" then
            local r = hits:at(ev[3], ev[4])
            if r and r.id == "chat" then
                cur, scroll, adding, unread[r.data] = r.data, 0, false, nil
                local total = 0
                for _, n in pairs(unread) do total = total + n end
                if ctx.badge then ctx.badge(total) end
            elseif r and r.id == "new" then adding, input = true, "" end
        elseif e == "char" then
            if #input < 200 then input = input .. ev[2] end
        elseif e == "paste" then
            input = (input .. tostring(ev[2]):gsub("[\r\n]", " ")):sub(1, 200)
        elseif e == "key" then
            local k = ev[2]
            if k == keys.enter then
                if input ~= "" then send(input) scroll = 0 else adding = false end
                input = ""
            elseif k == keys.backspace then input = input:sub(1, -2)
            elseif k == keys.pageUp or k == keys.up then scroll = scroll + 3
            elseif k == keys.pageDown or k == keys.down then scroll = math.max(0, scroll - 3) end
        elseif e == "mouse_scroll" then
            scroll = math.max(0, scroll - ev[2] * 2)
        end
    end
end

return M
