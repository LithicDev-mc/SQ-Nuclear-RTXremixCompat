local theme = require("sqos.theme")
local ui = require("sqos.ui")
local C = theme.C

local M = {}

function M.run(ctx)
    local T = ctx.T
    local hits = ui.hits()
    local section, top = "start", 1

    local function draw()
        local w, h = term.getSize()
        hits:clear()
        ui.fill(term, 1, 1, w, h, C.face)
        local sections = {
            { label = T("hp_start"), id = "sec", data = "start" }, { label = T("hp_gestures"), id = "sec", data = "gestures" },
            { label = T("hp_keys"), id = "sec", data = "keys" }, { label = T("hp_apps"), id = "sec", data = "apps" },
        }
        local x0, y = 1, 1
        if w >= 44 then
            local nw = 14
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
        local cw = w - x0
        local lines = {}
        if section == "apps" then
            for _, a in ipairs(ctx.wm.apps) do
                if a.menu then
                    lines[#lines + 1] = { a.titleKey and T(a.titleKey) or a.name, C.text, a.icon }
                    for _, l in ipairs(ui.wrap(T("hp_" .. a.id), cw - 2)) do lines[#lines + 1] = { "  " .. l, C.dim } end
                end
            end
        else
            for _, l in ipairs(ui.wrap(T("hp_" .. section .. "_text"), cw)) do lines[#lines + 1] = { l, C.text } end
        end
        local rows = h - y + 1
        top = math.max(1, math.min(top, math.max(1, #lines - rows + 1)))
        for i = 0, rows - 1 do
            local l = lines[top + i]
            if not l then break end
            if l[3] then ui.put(term, x0, y + i, l[3][1], l[3][2], C.face) ui.put(term, x0 + 2, y + i, l[1], l[2], C.face)
            else ui.put(term, x0, y + i, l[1], l[2], C.face) end
        end
        hits:scrollbar(term, w, y, h, top, #lines, rows, "up", "down")
    end

    while true do
        draw()
        local ev = { os.pullEvent() }
        if ev[1] == "mouse_click" then
            local r = hits:at(ev[3], ev[4])
            if r and r.id == "sec" then section, top = r.data, 1
            elseif r and r.id == "up" then top = math.max(1, top - 3)
            elseif r and r.id == "down" then top = top + 3 end
        elseif ev[1] == "mouse_scroll" then top = math.max(1, top + ev[2] * 2)
        elseif ev[1] == "key" and ev[2] == keys.escape then ctx.close()
        end
    end
end

return M
