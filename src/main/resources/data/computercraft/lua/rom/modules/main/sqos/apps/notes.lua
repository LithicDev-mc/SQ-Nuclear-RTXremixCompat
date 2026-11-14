local theme = require("sqos.theme")
local ui = require("sqos.ui")
local C = theme.C

local M = {}
local DIR = "/.sqos/notes"

local function load()
    local out = {}
    if not fs.exists(DIR) then return out end
    for _, n in ipairs(fs.list(DIR)) do
        local path = fs.combine(DIR, n)
        if not fs.isDir(path) then
            local h = fs.open(path, "r")
            local text = h and (h.readAll() or "") or ""
            if h then h.close() end
            local first = text:match("^[^\n]*") or ""
            out[#out + 1] = { path = "/" .. path, name = n, title = first ~= "" and first or nil, lines = select(2, text:gsub("\n", "")) + 1 }
        end
    end
    table.sort(out, function(a, b) return a.name > b.name end)
    return out
end

function M.run(ctx)
    local T = ctx.T
    local hits = ui.hits()
    local notes, sel, top, confirm = load(), nil, 1, false

    local function draw()
        local w, h = term.getSize()
        hits:clear()
        ui.fill(term, 1, 1, w, h, C.face)
        ui.fill(term, 1, 1, w, 1, C.panel)
        local x = 1 + hits:button(term, 1, 1, "+ " .. T("nt_new"), "new", C.selText, C.sel) + 1
        if sel then
            x = x + hits:button(term, x, 1, T("edit"), "edit") + 1
            x = x + hits:button(term, x, 1, T("delete"), "del", C.selText, C.alarm) + 1
        end
        local rows = h - 1
        if #notes == 0 then ui.empty(term, 1, 2, w, rows, T("nt_empty")) end
        if top > #notes then top = math.max(1, #notes) end
        for i = 0, rows - 1 do
            local n = notes[top + i]
            if not n then break end
            local y = 2 + i
            local isSel = n.path == sel
            local bg, fg = isSel and C.sel or C.face, isSel and C.selText or C.text
            local meta = n.lines .. " Z"
            ui.put(term, 1, y, " " .. ui.fit(n.title or T("nt_untitled"), math.max(1, w - #meta - 4)) .. " " .. meta .. " ", n.title and fg or C.dim, bg)
            hits:add(1, y, w - 1, 1, "item", n.path)
        end
        hits:scrollbar(term, w, 2, h, top, #notes, rows, "up", "down")
        if confirm and sel then
            ui.fill(term, 1, h, w, 1, C.face)
            ui.put(term, 1, h, T("ex_confirm_del"), C.alarm, C.face)
            local cx = #T("ex_confirm_del") + 2
            cx = cx + hits:button(term, cx, h, T("yes"), "yes", C.selText, C.alarm) + 1
            hits:button(term, cx, h, T("no"), "no")
        end
    end

    local last, lastTime = nil, 0
    while true do
        draw()
        local ev = { os.pullEvent() }
        local e = ev[1]
        if e == "mouse_click" then
            local r = hits:at(ev[3], ev[4])
            if r then
                if r.id == "new" then
                    if not fs.exists(DIR) then fs.makeDir(DIR) end
                    local name = string.format("%d-%04d.txt", os.day and os.day() or 0, math.floor((os.time and os.time() or 0) * 100))
                    local path = fs.combine(DIR, name)
                    local n = 0
                    while fs.exists(path) do n = n + 1 path = fs.combine(DIR, name:gsub("%.txt$", "") .. "-" .. n .. ".txt") end
                    local hnd = fs.open(path, "w")
                    if hnd then hnd.write("") hnd.close() end
                    sel = "/" .. path
                    ctx.open("editor", { path = sel })
                elseif r.id == "item" then
                    local now = os.clock()
                    if sel == r.data and last == r.data and now - lastTime < 1.5 then ctx.open("editor", { path = sel }) last = nil
                    else sel = r.data last, lastTime = r.data, now end
                    confirm = false
                elseif r.id == "edit" then ctx.open("editor", { path = sel })
                elseif r.id == "del" then confirm = true
                elseif r.id == "yes" then pcall(fs.delete, sel) sel, confirm = nil, false
                elseif r.id == "no" then confirm = false
                elseif r.id == "up" then top = math.max(1, top - 5)
                elseif r.id == "down" then top = top + 5 end
            end
            notes = load()
        elseif e == "mouse_scroll" then top = math.max(1, top + ev[2] * 3)
        elseif e == "key" and ev[2] == keys.f5 then notes = load()
        elseif e == "term_resize" then notes = load()
        end
    end
end

return M
