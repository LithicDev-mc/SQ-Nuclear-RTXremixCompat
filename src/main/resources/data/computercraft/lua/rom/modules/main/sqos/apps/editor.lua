local theme = require("sqos.theme")
local ui = require("sqos.ui")
local C = theme.C

local M = {}

local function readLines(path)
    local lines = {}
    if path and fs.exists(path) and not fs.isDir(path) then
        local h = fs.open(path, "r")
        if h then
            for line in (h.readAll() or ""):gmatch("([^\n]*)\n?") do lines[#lines + 1] = line end
            h.close()
            if #lines > 1 and lines[#lines] == "" then lines[#lines] = nil end
        end
    end
    if #lines == 0 then lines[1] = "" end
    return lines
end

function M.run(ctx)
    local T = ctx.T
    local path = ctx.args.path
    local lines = readLines(path)
    local cx, cy, top, left = 1, 1, 1, 0
    local mod, status, ctrl = false, "", false
    local hits = ui.hits()
    local readonly = path and fs.isReadOnly and fs.exists(path) and fs.isReadOnly(path)

    local function title()
        ctx.setTitle(T("app_editor") .. (path and (" - " .. fs.getName(path)) or "") .. (mod and " *" or ""))
    end

    local function dims()
        local w, h = term.getSize()
        return w, h, h - 2
    end

    local function gutter()
        local w = term.getSize()
        return w >= 30 and (#tostring(#lines) + 1) or 0
    end

    local function clampCursor()
        if cy < 1 then cy = 1 end
        if cy > #lines then cy = #lines end
        if cx < 1 then cx = 1 end
        if cx > #lines[cy] + 1 then cx = #lines[cy] + 1 end
        local w, _, rows = dims()
        w = w - gutter()
        if cy < top then top = cy end
        if cy > top + rows - 1 then top = cy - rows + 1 end
        if cx - 1 < left then left = cx - 1 end
        if cx - 1 >= left + w then left = cx - w end
    end

    local function save()
        if readonly then status = T("ed_readonly") return end
        if not path then
            local w, h = term.getSize()
            ui.fill(term, 1, h, w, 1, C.face)
            ui.put(term, 1, h, T("ed_path") .. " ", C.text, C.face)
            term.setBackgroundColor(C.light)
            term.setCursorPos(#T("ed_path") + 2, h)
            local ok, res = pcall(read, nil, nil, nil, "/")
            if not ok or not res or res == "" or res == "/" then return end
            path = res
        end
        local ok, err = pcall(function()
            local dir = fs.getDir(path)
            if dir ~= "" and not fs.exists(dir) then fs.makeDir(dir) end
            local h = fs.open(path, "w")
            h.write(table.concat(lines, "\n") .. "\n")
            h.close()
        end)
        if ok then mod = false status = T("ed_saved") else status = tostring(err) end
        title()
    end

    local function draw()
        local w, h, rows = dims()
        local gw = gutter()
        hits:clear()
        ui.fill(term, 1, 1, w, 1, C.panel)
        local x = 1
        x = x + hits:button(term, x, 1, T("save"), "save", mod and C.selText or C.text, mod and C.sel or C.light) + 1
        local left2 = w - 9
        if left2 > x then
            hits:button(term, w - 8, 1, "PgUp", "pgup")
            hits:button(term, w - 3, 1, "Dn", "pgdn")
            ui.put(term, x, 1, ui.clip(path or T("ed_new"), left2 - x), C.dim, C.panel)
        end
        for i = 0, rows - 1 do
            local n = top + i
            local ln = lines[n]
            local y = 2 + i
            if gw > 0 then
                ui.put(term, 1, y, ln and ui.fit(tostring(n), gw - 1) .. " " or (" "):rep(gw), n == cy and C.text or C.dim, C.panel)
            end
            term.setBackgroundColor(n == cy and C.light or C.face)
            term.setTextColor(C.text)
            term.setCursorPos(gw + 1, y)
            local tw = w - gw
            if ln then
                local seg = ln:sub(left + 1, left + tw)
                term.write(seg .. (" "):rep(tw - #seg))
            else
                term.write((" "):rep(tw))
            end
        end
        ui.fill(term, 1, h, w, 1, C.panel)
        local s = T("ed_line", cy, cx) .. (mod and (" " .. T("ed_modified")) or "") .. (status ~= "" and ("  " .. status) or "")
        ui.put(term, 2, h, ui.clip(s, w - 1), C.dim, C.panel)
        term.setCursorPos(gw + cx - left, cy - top + 2)
        term.setTextColor(C.text)
        term.setCursorBlink(true)
    end

    local function insert(s)
        local l = lines[cy]
        lines[cy] = l:sub(1, cx - 1) .. s .. l:sub(cx)
        cx = cx + #s
        mod = true
    end

    title()
    while true do
        clampCursor()
        draw()
        local ev = { os.pullEvent() }
        local e = ev[1]
        local _, h, rows = dims()
        if e == "char" then
            insert(ev[2])
            status = ""
        elseif e == "paste" then
            for part, nl in ev[2]:gmatch("([^\n]*)(\n?)") do
                insert(part)
                if nl ~= "" then
                    local l = lines[cy]
                    table.insert(lines, cy + 1, l:sub(cx))
                    lines[cy] = l:sub(1, cx - 1)
                    cy, cx = cy + 1, 1
                end
            end
        elseif e == "key" then
            local k = ev[2]
            if k == keys.leftCtrl or k == keys.rightCtrl then ctrl = true
            elseif ctrl and k == keys.s then save()
            elseif k == keys.left then
                if cx > 1 then cx = cx - 1 elseif cy > 1 then cy = cy - 1 cx = #lines[cy] + 1 end
            elseif k == keys.right then
                if cx <= #lines[cy] then cx = cx + 1 elseif cy < #lines then cy = cy + 1 cx = 1 end
            elseif k == keys.up then cy = cy - 1
            elseif k == keys.down then cy = cy + 1
            elseif k == keys.home then cx = 1
            elseif k == keys["end"] then cx = #lines[cy] + 1
            elseif k == keys.pageUp then cy = cy - rows top = top - rows
            elseif k == keys.pageDown then cy = cy + rows top = top + rows
            elseif k == keys.enter then
                local l = lines[cy]
                local indent = l:match("^%s*")
                table.insert(lines, cy + 1, indent .. l:sub(cx))
                lines[cy] = l:sub(1, cx - 1)
                cy, cx = cy + 1, #indent + 1
                mod = true
            elseif k == keys.backspace then
                if cx > 1 then
                    local l = lines[cy]
                    lines[cy] = l:sub(1, cx - 2) .. l:sub(cx)
                    cx = cx - 1
                    mod = true
                elseif cy > 1 then
                    local prev = lines[cy - 1]
                    lines[cy - 1] = prev .. lines[cy]
                    table.remove(lines, cy)
                    cy, cx = cy - 1, #prev + 1
                    mod = true
                end
            elseif k == keys.delete then
                local l = lines[cy]
                if cx <= #l then lines[cy] = l:sub(1, cx - 1) .. l:sub(cx + 1) mod = true
                elseif cy < #lines then lines[cy] = l .. lines[cy + 1] table.remove(lines, cy + 1) mod = true end
            elseif k == keys.tab then insert("  ") end
            status = ""
        elseif e == "key_up" then
            if ev[2] == keys.leftCtrl or ev[2] == keys.rightCtrl then ctrl = false end
        elseif e == "mouse_click" then
            local r = hits:at(ev[3], ev[4])
            if r then
                if r.id == "save" then save()
                elseif r.id == "pgup" then cy = cy - rows top = top - rows
                elseif r.id == "pgdn" then cy = cy + rows top = top + rows end
            elseif ev[4] >= 2 and ev[4] <= h - 1 then
                cy = top + ev[4] - 2
                if cy > #lines then cy = #lines end
                cx = left + ev[3] - gutter()
                if cx > #lines[cy] + 1 then cx = #lines[cy] + 1 end
                if cx < 1 then cx = 1 end
            end
        elseif e == "mouse_scroll" then
            top = math.max(1, math.min(#lines, top + ev[2] * 3))
            if cy < top then cy = top end
            if cy > top + rows - 1 then cy = top + rows - 1 end
        end
        if top < 1 then top = 1 end
        title()
    end
end

return M
