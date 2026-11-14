local M = {}
local unpack = table.unpack or unpack

local PIXEL_TYPES = { sqc_image_monitor = "sqc" }
M.PIXEL_TYPES = PIXEL_TYPES

local function textDriver(target, name, isMonitor, opts)
    local drv = {
        kind = isMonitor and "monitor" or "terminal",
        target = target, name = name, touchOnly = isMonitor and true or false,
    }
    local prev = {}
    local saved
    local blinking = false

    if isMonitor and opts.scale and target.setTextScale then pcall(target.setTextScale, opts.scale) end

    function drv.size()
        return target.getSize()
    end

    function drv.setPalette(rgbs)
        if not target.setPaletteColor then return end
        if not saved then
            saved = {}
            for i = 0, 15 do saved[i] = { target.getPaletteColor(2 ^ i) } end
        end
        for i = 0, 15 do target.setPaletteColor(2 ^ i, rgbs[i + 1]) end
    end

    function drv.present(rows, cursor)
        for y, r in ipairs(rows) do
            local key = r.t .. "\0" .. r.f .. "\0" .. r.b
            if prev[y] ~= key then
                prev[y] = key
                target.setCursorPos(1, y)
                target.blit(r.t, r.f, r.b)
            end
        end
        if cursor then
            target.setCursorPos(cursor.x, cursor.y)
            target.setTextColor(cursor.color or colors.white)
            target.setCursorBlink(true)
            blinking = true
        elseif blinking then
            target.setCursorBlink(false)
            blinking = false
        end
    end

    function drv.invalidate()
        prev = {}
    end

    function drv.restore()
        if saved then
            for i = 0, 15 do target.setPaletteColor(2 ^ i, unpack(saved[i])) end
        end
        target.setCursorBlink(false)
        target.setBackgroundColor(colors.black)
        target.setTextColor(colors.white)
        target.clear()
        target.setCursorPos(1, 1)
    end

    function drv.event(ev)
        local e = ev[1]
        if isMonitor then
            if e == "monitor_touch" and ev[2] == name then return { "click", 1, ev[3], ev[4], touch = true } end
            if e == "monitor_resize" and ev[2] == name then return { "resize" } end
        else
            if e == "mouse_click" then return { "click", ev[2], ev[3], ev[4] } end
            if e == "mouse_up" then return { "up", ev[2], ev[3], ev[4] } end
            if e == "mouse_drag" then return { "drag", ev[2], ev[3], ev[4] } end
            if e == "mouse_scroll" then return { "scroll", ev[2], ev[3], ev[4] } end
            if e == "term_resize" then return { "resize" } end
        end
        return nil
    end

    return drv
end

local function pixelDriver(mon, name, opts, prefix)
    local px = require(opts.con and "sqos.con_px" or "sqos.px")
    local g = { tw = 6, cw = opts.cellW or 7, ch = opts.cellH or 12, font = opts.fontSize or 10, ox = 0, oy = 0, cols = 1, rows = 1,
        palVersion = 0, palette = {} }
    local ppb = opts.density or 128
    local drv = { kind = "pixel", target = mon, name = name, touchOnly = true }
    for i = 1, 16 do g.palette[i] = 0 end
    local view

    local function layout(initial)
        if initial then
            local bw, bh = mon.getSize()
            local d = ppb
            local set = false
            while d >= 32 and not set do
                set = pcall(mon.setResolution, bw * d, bh * d)
                if not set then d = math.floor(d / 2) end
            end
        end
        local rw, rh = mon.getResolution()
        if not opts.cellW then
            local big = rw >= 1100
            g.cw, g.ch, g.tw, g.font = big and 9 or 7, big and 18 or 12, big and 8 or 6, big and 15 or 10
        end
        g.cols, g.rows = math.max(1, math.floor(rw / g.cw)), math.max(1, math.floor(rh / g.ch))
        g.ox, g.oy = math.floor((rw - g.cols * g.cw) / 2), math.floor((rh - g.rows * g.ch) / 2)
        if view then view.invalidate() end
    end
    layout(true)
    view = px.new(mon, g)

    function drv.size()
        return g.cols, g.rows
    end

    function drv.setPalette(rgbs)
        for i = 1, 16 do g.palette[i] = rgbs[i] end
        g.palVersion = g.palVersion + 1
        view.invalidate()
    end

    function drv.invalidate()
        view.invalidate()
    end

    function drv.presentScene(sc)
        view.present(sc)
    end

    function drv.animate(kind, w)
        view.animate(kind, w)
    end

    function drv.restore()
        pcall(mon.clear, 0)
    end

    local function cell(px, py)
        local cx = math.floor((px - 1 - g.ox) / g.cw) + 1
        local cy = math.floor((py - 1 - g.oy) / g.ch) + 1
        if cx >= 1 and cx <= g.cols and cy >= 1 and cy <= g.rows then return cx, cy end
    end

    function drv.event(ev)
        local e = ev[1]
        if ev[2] ~= name or type(e) ~= "string" or e:sub(1, #prefix + 1) ~= prefix .. "_" then return nil end
        local kind = e:sub(#prefix + 2)
        if kind == "resize" then
            layout()
            return { "resize" }
        end
        if kind == "touch" or kind == "up" or kind == "drag" or kind == "scroll" then
            local cx, cy = cell(ev[3], ev[4])
            if not cx then
                if kind == "touch" or kind == "scroll" then return nil end
                cx = math.max(1, math.min(g.cols, math.floor((ev[3] - 1 - g.ox) / g.cw) + 1))
                cy = math.max(1, math.min(g.rows, math.floor((ev[4] - 1 - g.oy) / g.ch) + 1))
            end
            if kind == "touch" then
                local btn = (type(ev[6]) == "number") and ev[6] or nil
                return { "click", btn or 1, cx, cy, touch = (btn == nil) }
            elseif kind == "up" then return { "up", ev[5] or 1, cx, cy }
            elseif kind == "drag" then return { "drag", ev[5] or 1, cx, cy }
            else return { "scroll", ev[5] or 1, cx, cy } end
        end
        if kind == "key" then return { "rewrite", { "key", ev[3], ev[4] == true, n = 3 } } end
        if kind == "key_up" then return { "rewrite", { "key_up", ev[3], n = 2 } } end
        if kind == "char" then return { "rewrite", { "char", ev[3], n = 2 } } end
        return nil
    end

    return drv
end

function M.open(spec, opts)
    opts = opts or {}
    if spec == nil or spec == "term" then
        return textDriver(term.current(), nil, false, opts)
    end
    local name = spec
    if spec == "auto" then
        name = nil
        for _, n in ipairs(peripheral.getNames()) do
            if PIXEL_TYPES[peripheral.getType(n)] then name = n break end
        end
        if not name then
            for _, n in ipairs(peripheral.getNames()) do
                if peripheral.getType(n) == "monitor" then name = n break end
            end
        end
        if not name then return nil, "no monitor found" end
    end
    if not peripheral.isPresent(name) then return nil, "no peripheral '" .. tostring(name) .. "'" end
    local kind = peripheral.getType(name)
    local p = peripheral.wrap(name)
    if PIXEL_TYPES[kind] then return pixelDriver(p, name, opts, PIXEL_TYPES[kind]) end
    if kind == "monitor" then return textDriver(p, name, true, opts) end
    return nil, "'" .. name .. "' is a " .. tostring(kind) .. ", not a monitor"
end

return M
