local theme = require("sqos.theme")
local C = theme.C

local M = {}

local hexOf = {}
for i = 0, 15 do hexOf[2 ^ i] = ("0123456789abcdef"):sub(i + 1, i + 1) end
M.hexOf = hexOf

function M.hex(c)
    return hexOf[c] or "0"
end

function M.hexLine(s, def)
    return (s:gsub("[^0-9a-f]", def))
end

function M.fit(s, len)
    s = tostring(s)
    if #s > len then
        if len > 2 then return s:sub(1, len - 1) .. "~" end
        return s:sub(1, len)
    end
    return s .. (" "):rep(len - #s)
end

function M.clip(s, len)
    s = tostring(s)
    if #s > len then return s:sub(1, math.max(0, len)) end
    return s
end

function M.center(s, len)
    s = M.clip(s, len)
    local l = math.floor((len - #s) / 2)
    return (" "):rep(l) .. s .. (" "):rep(len - #s - l)
end

function M.put(t, x, y, s, fg, bg)
    t.setCursorPos(x, y)
    if fg then t.setTextColor(fg) end
    if bg then t.setBackgroundColor(bg) end
    t.write(s)
end

function M.fill(t, x, y, w, h, bg)
    if w <= 0 or h <= 0 then return end
    t.setBackgroundColor(bg)
    local row = (" "):rep(w)
    for yy = y, y + h - 1 do
        t.setCursorPos(x, yy)
        t.write(row)
    end
end

function M.button(t, x, y, label, fg, bg)
    local s = " " .. label .. " "
    M.put(t, x, y, s, fg or C.text, bg or C.light)
    return #s
end

function M.bar(t, x, y, w, frac, color, bg)
    if w <= 0 then return end
    frac = math.max(0, math.min(1, frac or 0))
    local n = math.floor(frac * w + 0.5)
    t.setCursorPos(x, y)
    if n > 0 then
        t.setBackgroundColor(color or C.bar)
        t.write((" "):rep(n))
    end
    if w - n > 0 then
        t.setBackgroundColor(bg or C.barBg)
        t.write((" "):rep(w - n))
    end
end

function M.num(v, unit)
    if type(v) ~= "number" then return tostring(v) end
    local a = math.abs(v)
    local s
    if a >= 1e9 then s = string.format("%.1fG", v / 1e9)
    elseif a >= 1e6 then s = string.format("%.1fM", v / 1e6)
    elseif a >= 1e4 then s = string.format("%.1fk", v / 1e3)
    elseif v == math.floor(v) then s = string.format("%d", v)
    else s = string.format("%.2f", v) end
    if unit and unit ~= "" then s = s .. " " .. unit end
    return s
end

function M.wrap(text, width)
    local out = {}
    text = tostring(text)
    if width < 2 then return { text } end
    for para in (text .. "\n"):gmatch("(.-)\n") do
        while #para > width do
            local cut = width
            local sp = para:sub(1, width + 1):match("^.*() ")
            if sp and sp > width / 2 then cut = sp - 1 end
            out[#out + 1] = para:sub(1, cut)
            para = para:sub(cut + 1):gsub("^ +", "")
        end
        out[#out + 1] = para
    end
    return out
end

function M.para(t, x, y, w, text, fg, bg, maxRows)
    for i, l in ipairs(M.wrap(text, w)) do
        if maxRows and i > maxRows then break end
        M.put(t, x, y, M.fit(l, w), fg or C.text, bg or C.face)
        y = y + 1
    end
    return y
end

function M.header(t, w, title, right)
    M.fill(t, 1, 1, w, 1, C.panel)
    M.put(t, 2, 1, M.clip(title, w - 2), C.text, C.panel)
    if right and right ~= "" then
        right = M.clip(right, math.max(0, w - #title - 4))
        M.put(t, w - #right, 1, right, C.dim, C.panel)
    end
end

function M.card(t, x, y, w, h, title)
    M.fill(t, x, y, w, h, C.panel)
    if title then M.put(t, x + 1, y, M.clip(title, w - 2), C.dim, C.panel) end
end

function M.field(t, x, y, w, value, active, hint)
    local shown = value ~= "" and value or (hint or "")
    local fg = value ~= "" and C.text or C.dim
    if active then shown = shown .. "_" end
    M.put(t, x, y, M.fit(" " .. shown:sub(-(w - 2)), w), fg, active and C.light or C.panel)
end

function M.valueRow(t, x, y, w, label, valueText, frac, color)
    M.put(t, x, y, M.fit(label, w), C.dim, C.face)
    local vs = tostring(valueText)
    M.put(t, x, y + 1, M.fit(vs, w), C.text, C.face)
    if frac and w - #vs - 1 >= 4 then
        M.bar(t, x + #vs + 1, y + 1, w - #vs - 1, frac, color or C.bar, C.barBg)
    end
    return y + 2
end

function M.empty(t, x, y, w, h, text)
    local lines = M.wrap(text, math.max(8, w - 4))
    local y0 = y + math.max(0, math.floor((h - #lines) / 2))
    for i, l in ipairs(lines) do
        if y0 + i - 1 < y + h then M.put(t, x, y0 + i - 1, M.center(l, w), C.dim, C.face) end
    end
end

local Hits = {}
Hits.__index = Hits

function M.hits()
    return setmetatable({ list = {} }, Hits)
end

function Hits:clear()
    self.list = {}
end

function Hits:add(x, y, w, h, id, data)
    self.list[#self.list + 1] = { x = x, y = y, w = w, h = h, id = id, data = data }
end

function Hits:at(px, py)
    for i = #self.list, 1, -1 do
        local r = self.list[i]
        if px >= r.x and px < r.x + r.w and py >= r.y and py < r.y + r.h then return r end
    end
    return nil
end

function Hits:button(t, x, y, label, id, fg, bg, data)
    local w = M.button(t, x, y, label, fg, bg)
    self:add(x, y, w, 1, id, data)
    return w
end

function Hits:tabs(t, x, y, tabs, active)
    for _, tb in ipairs(tabs) do
        local on = tb.id == active or (tb.data ~= nil and tb.data == active)
        x = x + self:button(t, x, y, tb.label, tb.id, on and C.selText or C.text, on and C.sel or C.light, tb.data) + 1
    end
    return x
end

function Hits:toggle(t, x, y, w, label, on, id, data)
    local pillW = 4
    M.put(t, x, y, M.fit(label, math.max(0, w - pillW - 1)), C.text, C.face)
    M.put(t, x + w - pillW, y, on and (" " .. require("sqos.lang").t("se_on") .. " "):sub(1, 4) or " -- ", on and C.selText or C.dim, on and C.sel or C.light)
    self:add(x, y, w, 1, id, data)
end

function Hits:scrollbar(t, x, y1, y2, top, total, rows, idUp, idDown)
    if y2 - y1 < 2 then return end
    M.put(t, x, y1, "^", C.text, C.light)
    M.put(t, x, y2, "v", C.text, C.light)
    self:add(x, y1, 1, 1, idUp)
    self:add(x, y2, 1, 1, idDown)
    local track = y2 - y1 - 1
    if track > 0 then
        M.fill(t, x, y1 + 1, 1, track, C.panel)
        if total > rows then
            local size = math.max(1, math.floor(track * rows / total))
            local pos = math.floor((track - size) * (top - 1) / math.max(1, total - rows) + 0.5)
            M.fill(t, x, y1 + 1 + pos, 1, size, C.dim)
        end
    end
end

return M
