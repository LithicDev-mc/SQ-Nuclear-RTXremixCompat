local theme = require("sqos.theme")
local ui = require("sqos.ui")
local C = theme.C

local M = {}

local function listDir(path)
    local ok, names = pcall(fs.list, path)
    if not ok then return {} end
    local out = {}
    for _, n in ipairs(names) do
        local full = fs.combine(path, n)
        out[#out + 1] = { name = n, dir = fs.isDir(full), full = full }
    end
    table.sort(out, function(a, b)
        if a.dir ~= b.dir then return a.dir end
        return a.name:lower() < b.name:lower()
    end)
    return out
end

function M.run(ctx)
    local T = ctx.T
    local path = ctx.args.path or ""
    local entries = listDir(path)
    local sel, top = nil, 1
    local msg, hits = "", ui.hits()
    local lastClick, lastTime = nil, 0
    local confirm = false

    local function reload()
        entries = listDir(path)
        if sel and sel > #entries then sel = #entries > 0 and #entries or nil end
        if top > math.max(1, #entries) then top = 1 end
    end

    local function listRows()
        local _, h = term.getSize()
        return math.max(1, h - 3)
    end

    local function ensureVisible()
        local rows = listRows()
        if sel then
            if sel < top then top = sel end
            if sel > top + rows - 1 then top = sel - rows + 1 end
        end
    end

    local function prompt(label, default)
        local w, h = term.getSize()
        ui.fill(term, 1, h, w, 1, C.face)
        ui.put(term, 1, h, ui.clip(label, w - 2) .. " ", C.text, C.face)
        term.setBackgroundColor(C.light)
        term.setTextColor(C.text)
        local x = #label + 2
        term.setCursorPos(x, h)
        term.write((" "):rep(math.max(0, w - x + 1)))
        term.setCursorPos(x, h)
        local ok, res = pcall(read, nil, nil, nil, default)
        return ok and res or nil
    end

    local function openEntry(e, forceEdit)
        if e.dir then
            path = e.full
            sel, top = nil, 1
            reload()
        elseif not forceEdit and e.name:match("%.lua$") then
            ctx.open("runner", { cmd = "/" .. e.full, title = e.name })
        else
            ctx.open("editor", { path = "/" .. e.full })
        end
    end

    local function draw()
        local w, h = term.getSize()
        hits:clear()
        ui.fill(term, 1, 1, w, h, C.face)
        ui.fill(term, 1, 1, w, 2, C.panel)
        local x = 1
        local short = w < 40
        x = x + hits:button(term, x, 1, short and "^" or T("up"), "up") + 1
        x = x + hits:button(term, x, 1, short and "+" or T("new_folder"), "mkdir") + 1
        if sel then
            x = x + hits:button(term, x, 1, short and "R" or T("rename"), "ren") + 1
            x = x + hits:button(term, x, 1, short and "x" or T("delete"), "del", C.selText, C.alarm) + 1
        end
        local p = "/" .. path
        ui.put(term, 2, 2, ui.fit(p:gsub("/", " / "):gsub("^ ", "") .. (msg ~= "" and ("   " .. msg) or ""), w - 2), C.dim, C.panel)
        local rows = listRows()
        local listW = w - 2
        if #entries == 0 then ui.empty(term, 1, 3, w, rows, T("ex_empty")) end
        for i = 0, rows - 1 do
            local idx = top + i
            local e = entries[idx]
            if not e then break end
            local y = 3 + i
            local isSel = idx == sel
            local bg, fg = isSel and C.sel or C.face, isSel and C.selText or C.text
            local mark = e.dir and "#" or (e.name:match("%.lua$") and ">" or "-")
            ui.put(term, 1, y, " " .. mark .. " " .. ui.fit(e.name, listW - 4), fg, bg)
            if not isSel then ui.put(term, 2, y, mark, e.dir and C.warn or (mark == ">" and C.ok or C.dim), bg) end
            hits:add(1, y, listW, 1, "item", idx)
        end
        hits:scrollbar(term, w, 3, 2 + rows, top, #entries, rows, "pgup", "pgdn")
        ui.fill(term, 1, h, w, 1, C.face)
        if confirm and sel then
            ui.put(term, 1, h, T("ex_confirm_del"), C.alarm, C.face)
            local cx = #T("ex_confirm_del") + 2
            cx = cx + hits:button(term, cx, h, T("yes"), "yes", C.selText, C.alarm) + 1
            hits:button(term, cx, h, T("no"), "no")
        elseif sel then
            local e = entries[sel]
            local cx = 1
            cx = cx + hits:button(term, cx, h, T("open"), "open", C.selText, C.sel) + 1
            if not e.dir then
                cx = cx + hits:button(term, cx, h, T("run"), "run") + 1
                hits:button(term, cx, h, T("edit"), "edit")
            end
        end
    end

    local function act(id, data)
        msg = ""
        if id == "up" then
            if path ~= "" then
                path = fs.getDir(path)
                sel, top = nil, 1
                reload()
            end
        elseif id == "item" then
            local now = os.clock()
            if sel == data and lastClick == data and now - lastTime < 2 then
                openEntry(entries[data])
                lastClick = nil
            else
                sel = data
                lastClick, lastTime = data, now
            end
            confirm = false
        elseif id == "open" then
            if sel then openEntry(entries[sel]) end
        elseif id == "run" then
            if sel then ctx.open("runner", { cmd = "/" .. entries[sel].full, title = entries[sel].name }) end
        elseif id == "edit" then
            if sel then openEntry(entries[sel], true) end
        elseif id == "mkdir" then
            local n = prompt(T("ex_name"), "")
            if n and n ~= "" then
                local full = fs.combine(path, n)
                if fs.exists(full) then msg = T("ex_exists")
                else
                    local ok, err = pcall(fs.makeDir, full)
                    if not ok then msg = T("ex_failed", tostring(err)) end
                end
                reload()
            end
        elseif id == "ren" then
            if sel then
                local e = entries[sel]
                local n = prompt(T("ex_name"), e.name)
                if n and n ~= "" and n ~= e.name then
                    local ok, err = pcall(fs.move, e.full, fs.combine(path, n))
                    if not ok then msg = T("ex_failed", tostring(err)) end
                    reload()
                end
            end
        elseif id == "del" then
            confirm = true
        elseif id == "yes" then
            local ok, err = pcall(fs.delete, entries[sel].full)
            if not ok then msg = T("ex_failed", tostring(err)) end
            confirm = false
            sel = nil
            reload()
        elseif id == "no" then
            confirm = false
        elseif id == "pgup" then
            top = math.max(1, top - listRows())
        elseif id == "pgdn" then
            top = math.max(1, math.min(#entries, top + listRows()))
        end
    end

    while true do
        ensureVisible()
        draw()
        local ev = { os.pullEvent() }
        local e = ev[1]
        if e == "mouse_click" then
            local r = hits:at(ev[3], ev[4])
            if r then act(r.id, r.data) end
        elseif e == "mouse_scroll" then
            top = math.max(1, math.min(math.max(1, #entries), top + ev[2] * 3))
        elseif e == "key" then
            local k = ev[2]
            if k == keys.up and #entries > 0 then sel = math.max(1, (sel or 2) - 1)
            elseif k == keys.down and #entries > 0 then sel = math.min(#entries, (sel or 0) + 1)
            elseif k == keys.enter and sel then openEntry(entries[sel])
            elseif k == keys.backspace then act("up")
            elseif k == keys.delete and sel then confirm = true
            elseif k == keys.f5 then reload() end
        elseif e == "term_resize" then
            ensureVisible()
        end
    end
end

return M
