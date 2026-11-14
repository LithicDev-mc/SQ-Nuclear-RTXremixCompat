local theme = require("sqos.theme")
local ui = require("sqos.ui")
local lang = require("sqos.lang")
local config = require("sqos.config")
local C = theme.C

local M = {}

function M.run(ctx)
    local T = ctx.T
    local cfg = ctx.cfg
    local hits = ui.hits()
    local msg, section = "", "look"

    local function draw()
        local w, h = term.getSize()
        hits:clear()
        ui.fill(term, 1, 1, w, h, C.face)
        local sections = {
            { label = T("se_look"), id = "sec", data = "look" }, { label = T("se_behave"), id = "sec", data = "behave" },
            { label = T("se_pins"), id = "sec", data = "dock" }, { label = T("se_about"), id = "sec", data = "about" },
        }
        local x0, y = 1, 1
        local wide = w >= 44
        if wide then
            local nw = 12
            ui.fill(term, 1, 1, nw, h, C.panel)
            for i, s in ipairs(sections) do
                local on = s.data == section
                ui.put(term, 1, i + 1, ui.fit(" " .. s.label, nw), on and C.selText or C.text, on and C.sel or C.panel)
                hits:add(1, i + 1, nw, 1, s.id, s.data)
            end
            x0, y = nw + 2, 2
        else
            hits:tabs(term, 1, 1, sections, section)
            y = 3
        end
        local cw = w - x0 + 1
        local function row(label, options)
            if label ~= "" then ui.put(term, x0, y, ui.clip(label, cw), C.dim, C.face) y = y + 1 end
            local x = x0
            for _, o in ipairs(options) do
                local on = o.active
                if x + #o.label + 2 > w + 1 then x = x0 y = y + 1 end
                x = x + hits:button(term, x, y, o.label, o.id, on and C.selText or C.text, on and C.sel or C.light, o.data) + 1
            end
            y = y + (h >= 20 and 2 or 1)
        end
        if section == "look" then
            local th = {}
            for _, n in ipairs(theme.names) do th[#th + 1] = { label = T("view_" .. n), id = "theme", data = n, active = theme.name == n } end
            row(T("se_theme"), th)
            if not ctx.wm.con then
                local walls = {}
                for _, n in ipairs({ "plain", "grid" }) do
                    walls[#walls + 1] = { label = T("wall_" .. n), id = "wall", data = n, active = (cfg.wall == "grid") == (n == "grid") }
                end
                row(T("se_wall"), walls)
                row(T("se_anim"), {
                    { label = T("se_on"), id = "anim", data = true, active = cfg.anim },
                    { label = T("se_off"), id = "anim", data = false, active = not cfg.anim },
                })
            end
            local sv = { { label = T("saver_off"), id = "saver", data = 0, active = (cfg.saver or 0) == 0 } }
            for _, m in ipairs({ 1, 5, 10, 30 }) do sv[#sv + 1] = { label = T("saver_min", m), id = "saver", data = m, active = cfg.saver == m } end
            row(T("se_saver"), sv)
        elseif section == "behave" then
            row(T("se_lang"), {
                { label = "Deutsch", id = "lang", data = "de", active = cfg.lang == "de" },
                { label = "English", id = "lang", data = "en", active = cfg.lang == "en" },
            })
            row(T("se_clock"), {
                { label = T("se_clock_game"), id = "clock", data = "game", active = cfg.clock == "game" },
                { label = T("se_clock_real"), id = "clock", data = "real", active = cfg.clock == "real" },
            })
            if not ctx.wm.con then
                row(T("se_single"), {
                    { label = T("yes"), id = "tap", data = true, active = cfg.singleTap },
                    { label = T("no"), id = "tap", data = false, active = not cfg.singleTap },
                })
            end
            row(T("se_sound"), {
                { label = T("se_on"), id = "sound", data = true, active = cfg.sound ~= false },
                { label = T("se_off"), id = "sound", data = false, active = cfg.sound == false },
            })
        elseif section == "dock" then
            ui.put(term, x0, y, ui.clip(T("se_pin_hint"), cw), C.dim, C.face)
            y = y + 1
            for _, a in ipairs(ctx.wm.apps) do
                if a.menu and y < h then
                    local name = a.titleKey and T(a.titleKey) or a.name
                    hits:toggle(term, x0, y, math.min(cw, 30), name, ctx.wm:isPinned(a.id), "pin", a.id)
                    y = y + 1
                end
            end
        else
            y = ui.para(term, x0, y, cw, T("ab_text"), C.text, C.face) + 1
            y = ui.para(term, x0, y, cw, T("se_keys"), C.dim, C.face) + 1
            local id = os.getComputerID and os.getComputerID() or "?"
            local label = os.getComputerLabel and os.getComputerLabel() or nil
            ui.put(term, x0, y, ui.clip("ID " .. tostring(id) .. (label and ("  " .. label) or ""), cw), C.dim, C.face)
        end
        if msg ~= "" then ui.put(term, x0, h, ui.clip(msg, cw), C.dim, C.face) end
    end

    while true do
        draw()
        local ev = { os.pullEvent() }
        if ev[1] == "mouse_click" then
            local r = hits:at(ev[3], ev[4])
            if r then
                if r.id == "sec" then section = r.data msg = ""
                else
                    if r.id == "theme" then cfg.theme = r.data
                    elseif r.id == "wall" then cfg.wall = r.data
                    elseif r.id == "anim" then cfg.anim = r.data
                    elseif r.id == "lang" then cfg.lang = r.data
                    elseif r.id == "clock" then cfg.clock = r.data
                    elseif r.id == "tap" then cfg.singleTap = r.data
                    elseif r.id == "sound" then cfg.sound = r.data
                    elseif r.id == "saver" then cfg.saver = r.data
                    elseif r.id == "pin" then ctx.wm:togglePin(r.data) end
                    config.save(cfg)
                    ctx.apply()
                    T = lang.t
                    msg = T("se_applied")
                end
            end
        end
    end
end

return M
