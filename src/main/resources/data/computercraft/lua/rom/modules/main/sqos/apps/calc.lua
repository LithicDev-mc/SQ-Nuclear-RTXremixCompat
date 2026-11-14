local theme = require("sqos.theme")
local ui = require("sqos.ui")
local C = theme.C

local M = {}

local KEYS = {
    { "C", "<", "(", ")" },
    { "7", "8", "9", "/" },
    { "4", "5", "6", "*" },
    { "1", "2", "3", "-" },
    { "0", ".", "=", "+" },
}

local function evaluate(expr)
    if expr == "" or not expr:match("^[%d%.%+%-%*/%%%(%)%s%^]+$") then return nil end
    local f = (loadstring or load)("return " .. expr, "calc")
    if not f then return nil end
    if setfenv then setfenv(f, {}) end
    local ok, v = pcall(f)
    if not ok or type(v) ~= "number" or v ~= v or v == math.huge or v == -math.huge then return nil end
    if v == math.floor(v) and math.abs(v) < 1e15 then return string.format("%d", v) end
    return (string.format("%.10g", v))
end

function M.run(ctx)
    local T = ctx.T
    local hits = ui.hits()
    local expr, result, err = "", "", false

    local function press(k)
        err = false
        if k == "C" then expr, result = "", ""
        elseif k == "<" then expr = expr:sub(1, -2)
        elseif k == "=" then
            local r = evaluate(expr)
            if r then result, expr = r, r else err = true result = T("ca_error") end
        else
            if result ~= "" and expr == result and k:match("%d") then expr = "" end
            expr = (expr .. k):sub(1, 60)
            result = ""
        end
    end

    local function draw()
        local w, h = term.getSize()
        hits:clear()
        ui.fill(term, 1, 1, w, h, C.face)
        ui.fill(term, 1, 1, w, 2, C.panel)
        local shown = expr ~= "" and expr or "0"
        ui.put(term, 2, 1, ui.clip(shown:sub(-(w - 2)), w - 2), C.dim, C.panel)
        local big = result ~= "" and result or shown
        ui.put(term, math.max(2, w - #big), 2, ui.clip(big, w - 2), err and C.alarm or C.text, C.panel)
        local rows, cols = #KEYS, 4
        local bw = math.max(3, math.floor((w - (cols + 1)) / cols))
        local bh = math.max(1, math.floor((h - 3) / rows))
        local x0 = math.floor((w - (cols * bw + cols - 1)) / 2) + 1
        for r, line in ipairs(KEYS) do
            for c, k in ipairs(line) do
                local x, y = x0 + (c - 1) * (bw + 1), 3 + (r - 1) * (bh + (bh > 1 and 0 or 0))
                local y2 = 3 + (r - 1) * bh
                local op = not k:match("[%d%.]")
                local bg = k == "=" and C.sel or (k == "C" and C.alarm or (op and C.panel or C.light))
                local fg = (k == "=" or k == "C") and C.selText or C.text
                ui.fill(term, x, y2, bw, bh, bg)
                ui.put(term, x + math.floor((bw - 1) / 2), y2 + math.floor((bh - 1) / 2), k, fg, bg)
                hits:add(x, y2, bw, bh, "key", k)
            end
        end
    end

    while true do
        draw()
        local ev = { os.pullEvent() }
        local e = ev[1]
        if e == "mouse_click" then
            local r = hits:at(ev[3], ev[4])
            if r then press(r.data) end
        elseif e == "char" then
            local c = ev[2]
            if c == "=" then press("=") elseif c:match("[%d%.%+%-%*/%%%(%)%^]") then press(c) end
        elseif e == "key" then
            if ev[2] == keys.enter or ev[2] == keys.numPadEnter then press("=")
            elseif ev[2] == keys.backspace then press("<")
            elseif ev[2] == keys.delete then press("C") end
        end
    end
end

return M
