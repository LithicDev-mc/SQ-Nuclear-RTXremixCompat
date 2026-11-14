local args = { ... }

local mon = args[1] and peripheral.wrap(args[1]) or (peripheral.find and peripheral.find("sqc_image_monitor"))
if not mon or not mon.batch then
    printError("Kein Bildmonitor (sqc_image_monitor) gefunden.")
    printError("Benutzung: kurstafel [monitorname]")
    return
end
local bank = peripheral.find and peripheral.find("sqc_bank")
if not bank or not bank.exchangeTicker then
    printError("Kein Bank-Terminal (sqc_bank) am Computer. Computer neben ein Terminal oder ein Bank-Modem stellen.")
    return
end

local lang = "en"
pcall(function() lang = require("sqos.config").load().lang or "en" end)

local TXT = {
    de = { title = "BOERSE KURSE", good = "Ware", last = "Kurs", change = "Aend.", high = "Hoch", low = "Tief", chart = "Verlauf",
           none = "-", error = "Keine Verbindung: %s", page = "Seite %d/%d" },
    en = { title = "EXCHANGE PRICES", good = "Good", last = "Price", change = "Chg.", high = "High", low = "Low", chart = "Trend",
           none = "-", error = "No connection: %s", page = "Page %d/%d" },
}
local function T(key, ...)
    local s = (TXT[lang] or TXT.de)[key] or TXT.de[key] or key
    if select("#", ...) > 0 then return s:format(...) end
    return s
end

local BG, HEAD, ROWA, ROWB = 0x0D1016, 0x252934, 0x14171E, 0x1A1D26
local TEXT, DIM, UP, DOWN, ACCENT = 0xECEFF4, 0x8B93A3, 0x34D399, 0xF05252, 0x3B82F6

local function clamp(v, lo, hi) return math.max(lo, math.min(hi, v)) end

local function money(n)
    n = math.floor(tonumber(n) or 0)
    local s = tostring(n):reverse():gsub("(%d%d%d)", "%1."):reverse()
    if s:sub(1, 1) == "." then s = s:sub(2) end
    return s
end

local function changeText(permille)
    local v = permille / 10
    return string.format("%s%.1f%%", v > 0 and "+" or "", v)
end

local MIN_ROW = 14

local function build(rows, page, w, h)
    local cmds = {}
    local function add(...) cmds[#cmds + 1] = { ... } end
    add("clear", BG)
    local headH = clamp(math.floor(h * 0.13), 14, 48)
    add("fill", 1, 1, w, headH, HEAD)
    add("fill", 1, headH, w, 2, ACCENT)
    local tsize = clamp(headH - 8, 6, 40)
    add("text", 6, math.floor((headH - tsize) / 2) + 1, T("title"), TEXT, tsize)
    local colH = clamp(math.floor(headH * 0.65), 10, 30)
    local top = headH + 3
    local csize = clamp(colH - 4, 5, 20)
    local fr = { 0.25, 0.14, 0.13, 0.12, 0.12, 0.24 }
    local xs, x = {}, 4
    local inner = w - 8
    for i, f in ipairs(fr) do
        xs[i] = { x = x, w = math.floor(inner * f) }
        x = x + math.floor(inner * f)
    end
    local labels = { T("good"), T("last"), T("change"), T("high"), T("low"), T("chart") }
    for i, l in ipairs(labels) do
        if i == 1 then add("text", xs[i].x, top + 1, l, DIM, csize)
        else add("ctext", xs[i].x, top, xs[i].w, colH, l, DIM, csize) end
    end
    local listTop = top + colH + 1
    local avail = h - listTop - 2
    local perPage = math.max(1, math.floor(avail / MIN_ROW))
    local pages = math.max(1, math.ceil(#rows / perPage))
    page = ((page - 1) % pages) + 1
    local first = (page - 1) * perPage + 1
    local shown = math.min(perPage, #rows - first + 1)
    local rowH = clamp(math.floor(avail / math.max(1, math.min(perPage, #rows))), MIN_ROW, 40)
    local size = clamp(math.min(rowH - 5, math.floor(xs[1].w / (0.62 * 14))), 5, 32)
    for k = 0, shown - 1 do
        local r = rows[first + k]
        local y = listTop + k * rowH
        add("fill", 1, y, w, rowH, (k % 2 == 0) and ROWA or ROWB)
        local up = r.last > 0 and r.changePermille > 0
        local down = r.last > 0 and r.changePermille < 0
        local color = up and UP or (down and DOWN or TEXT)
        add("text", xs[1].x, y + math.floor((rowH - size) / 2), tostring(r.name):sub(1, 14), TEXT, size)
        add("ctext", xs[2].x, y, xs[2].w, rowH, r.last > 0 and money(r.last) or T("none"), color, size)
        add("ctext", xs[3].x, y, xs[3].w, rowH, r.last > 0 and changeText(r.changePermille) or T("none"), color, size)
        add("ctext", xs[4].x, y, xs[4].w, rowH, r.last > 0 and money(r.high) or T("none"), DIM, size)
        add("ctext", xs[5].x, y, xs[5].w, rowH, r.last > 0 and money(r.low) or T("none"), DIM, size)
        local hist = r.history or {}
        if #hist >= 2 then
            local lo, hi = hist[1], hist[1]
            for _, v in ipairs(hist) do
                if v < lo then lo = v end
                if v > hi then hi = v end
            end
            local cx, cw = xs[6].x + 4, xs[6].w - 8
            local cy, chh = y + 3, rowH - 6
            local span = math.max(1, hi - lo)
            local col = hist[#hist] >= hist[1] and UP or DOWN
            local prevX, prevY
            for i, v in ipairs(hist) do
                local px = cx + (i - 1) * cw / (#hist - 1)
                local py = cy + chh - (v - lo) / span * chh
                if prevX then add("line", prevX, prevY, px, py, col, 1) end
                prevX, prevY = px, py
            end
        else
            add("ctext", xs[6].x, y, xs[6].w, rowH, T("none"), DIM, size)
        end
    end
    if pages > 1 then
        add("ctext", w - 90, h - 12, 86, 10, T("page", page, pages), DIM, 6)
    end
    return cmds, pages
end

local errlog
pcall(function() errlog = require("sqos.errlog") end)

local lastError
local function errorScreen(msg)
    if errlog and msg ~= lastError then errlog.write("kurstafel", msg) end
    lastError = msg
    pcall(function()
        local w, h = mon.getResolution()
        local size = clamp(math.floor(h / 14), 6, 16)
        local perLine = math.max(10, math.floor((w - 12) / (size * 0.62)))
        local lines = errlog and errlog.wrap(T("error", tostring(msg)), perLine) or { T("error", tostring(msg):sub(1, perLine)) }
        local cmds = { { "clear", BG } }
        for i, line in ipairs(lines) do
            if 6 + i * (size + 3) > h then break end
            cmds[#cmds + 1] = { "text", 6, 6 + (i - 1) * (size + 3), line, DOWN, size }
        end
        mon.batch(cmds)
    end)
end

local page, rows = 1, nil
local refresh = os.startTimer(0)
local flip = os.startTimer(8)
local function draw()
    if not rows then return end
    local w, h = mon.getResolution()
    local ok, cmds, pages = pcall(build, rows, page, w, h)
    if ok then
        local ok2, err = pcall(mon.batch, cmds)
        if not ok2 then errorScreen(err) end
    else
        errorScreen(cmds)
    end
end

local function main()
  print("Kurstafel laeuft auf dem Bildmonitor. Strg+T beendet.")
  while true do
    local ev = { os.pullEvent() }
    if ev[1] == "timer" and ev[2] == refresh then
        local ok, res = pcall(bank.exchangeTicker)
        if ok then
            rows = res
            draw()
        else
            rows = nil
            errorScreen(res)
        end
        refresh = os.startTimer(5)
    elseif ev[1] == "timer" and ev[2] == flip then
        page = page + 1
        draw()
        flip = os.startTimer(8)
    elseif ev[1] == "sqc_resize" then
        draw()
    end
  end
end

local ok, err = pcall(main)
if not ok and not tostring(err):find("Terminated", 1, true) then
    if errlog then errlog.write("kurstafel", err) end
    printError(tostring(err))
    print("Der volle Text steht in sqos_error.log (edit sqos_error.log).")
elseif not ok then
    error(err, 0)
end
