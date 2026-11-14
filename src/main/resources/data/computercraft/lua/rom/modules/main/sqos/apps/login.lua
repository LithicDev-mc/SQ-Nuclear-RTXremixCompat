local theme = require("sqos.theme")
local ui = require("sqos.ui")
local account = require("sqos.account")
local C = theme.C

local M = {}

function M.run(ctx)
    local T = ctx.T
    local wm = ctx.wm
    local locked = ctx.args and ctx.args.lock
    local d = account.load()
    local mode
    local fields, active = {}, 1
    local msg, pick = "", d.order[1]
    local hits = ui.hits()

    local function form(kind, list)
        mode, fields, active, msg = kind, {}, 1, ""
        for _, f in ipairs(list) do fields[#fields + 1] = { label = T(f[1]), secret = f[2], value = "" } end
    end
    local function start()
        if locked and #d.order > 0 then form("login", { { "lg_pass", true } })
        elseif locked or #d.order == 0 then form("create", { { "lg_user" }, { "lg_pass", true }, { "lg_pass2", true } })
        else mode = nil end
    end
    start()

    local function done(name)
        wm.user = name
        if locked then ctx.unlock() else mode = nil msg = T("lg_done") end
    end

    local function submit()
        local v = {}
        for i, f in ipairs(fields) do v[i] = f.value end
        if mode == "login" then
            if account.check(d, pick, v[1]) then return done(pick) end
            msg, fields[1].value = T("lg_bad"), ""
        elseif mode == "create" then
            if v[1] == "" or v[2] == "" then msg = T("lg_empty")
            elseif v[2] ~= v[3] then msg = T("lg_diff")
            elseif d.users[v[1]] then msg = T("lg_exists")
            else
                account.set(d, v[1], v[2])
                if locked then return done(v[1]) end
                wm.user = v[1]
                mode, msg = nil, T("lg_done")
            end
        elseif mode == "change" then
            if not account.check(d, wm.user, v[1]) then msg = T("lg_bad")
            elseif v[2] == "" or v[2] ~= v[3] then msg = T("lg_diff")
            else
                account.set(d, wm.user, v[2])
                mode, msg = nil, T("lg_done")
            end
        end
    end

    local function draw()
        local w, h = term.getSize()
        hits:clear()
        ui.fill(term, 1, 1, w, h, C.face)
        local y = 2
        local title = locked and (mode == "create" and T("lg_first") or T("lg_welcome")) or T("app_login")
        if locked then
            local clock, date = wm.clockText and wm:clockText() or "", wm.dateText and wm:dateText() or ""
            ui.put(term, math.floor((w - #clock) / 2) + 1, y, clock, C.text, C.face)
            ui.put(term, math.floor((w - #date) / 2) + 1, y + 1, date, C.dim, C.face)
            y = y + 3
        end
        ui.put(term, 2, y, ui.clip(title, w - 2), C.accent2, C.face)
        y = y + 2
        if not mode then
            local who = wm.user and (T("lg_as") .. " " .. wm.user) or T("lg_none")
            ui.put(term, 2, y, ui.clip(who, w - 2), C.text, C.face)
            y = y + 2
            local x = 2
            x = x + hits:button(term, x, y, T("lg_lock"), "lock") + 1
            y = y + 2
            x = 2
            x = x + hits:button(term, x, y, T("lg_create"), "create") + 1
            if wm.user and d.users[wm.user] then hits:button(term, x, y, T("lg_change"), "change") end
        else
            if mode == "login" then
                local x = 2
                for _, n in ipairs(d.order) do
                    local on = n == pick
                    if x + #n + 2 > w then x = 2 y = y + 1 end
                    x = x + hits:button(term, x, y, n, "pick", on and C.selText or C.text, on and C.sel or C.light, n) + 1
                end
                y = y + 2
            end
            for i, f in ipairs(fields) do
                ui.put(term, 2, y, ui.clip(f.label, w - 2), C.dim, C.face)
                local shown = f.secret and ("*"):rep(#f.value) or f.value
                local fw = math.max(4, w - 3)
                ui.field(term, 2, y + 1, fw, shown, i == active, "")
                hits:add(2, y + 1, fw, 1, "field", i)
                y = y + 3
            end
            local x = 2
            x = x + hits:button(term, x, y, T(mode == "login" and "lg_login" or "ok"), "submit", C.selText, C.sel) + 1
            if mode == "create" and locked and #d.order == 0 then
                x = x + hits:button(term, x, y, T("lg_skip"), "skip") + 1
            elseif not locked then
                hits:button(term, x, y, T("cancel"), "cancel")
            end
        end
        if msg ~= "" and y + 2 <= h then ui.put(term, 2, h - 1, ui.clip(msg, w - 2), C.warn, C.face) end
    end

    while true do
        draw()
        local ev = { os.pullEvent() }
        if ev[1] == "mouse_click" then
            local r = hits:at(ev[3], ev[4])
            if r then
                if r.id == "field" then active = r.data
                elseif r.id == "pick" then pick = r.data
                elseif r.id == "submit" then submit()
                elseif r.id == "lock" then wm:lockScreen() ctx.close()
                elseif r.id == "create" then form("create", { { "lg_user" }, { "lg_pass", true }, { "lg_pass2", true } })
                elseif r.id == "change" then form("change", { { "lg_old", true }, { "lg_new", true }, { "lg_pass2", true } })
                elseif r.id == "cancel" then mode, msg = nil, ""
                elseif r.id == "skip" then
                    d.skipped = true
                    account.save(d)
                    done(nil)
                end
            end
        elseif mode and ev[1] == "char" then
            fields[active].value = fields[active].value .. ev[2]
        elseif mode and ev[1] == "key" then
            local k = ev[2]
            if k == keys.backspace then fields[active].value = fields[active].value:sub(1, -2)
            elseif k == keys.tab then active = active % #fields + 1
            elseif k == keys.enter then
                if active < #fields then active = active + 1 else submit() end
            end
        end
    end
end

return M
