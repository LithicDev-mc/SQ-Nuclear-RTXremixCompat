local args = { ... }

local function usage()
    print("SQ-OS")
    print("  sqos                 on this terminal")
    print("  sqos -m              on the first image monitor or monitor")
    print("  sqos -m <name>       on a specific monitor (e.g. monitor_0)")
    print("  sqos -s <0.5-5>      text size (normal monitors)")
    print("  sqos -d <32-512>     pixels per block (image monitor)")
    print("  sqcon                SQ Con, the control room (same switches)")
end

local spec, scale, density, con
local i = 1
while i <= #args do
    local a = args[i]
    if a == "-m" then
        local nxt = args[i + 1]
        if nxt and nxt:sub(1, 1) ~= "-" then spec = nxt i = i + 1 else spec = "auto" end
    elseif a == "-con" then con = true
    elseif a == "-s" then scale = tonumber(args[i + 1]) i = i + 1
    elseif a == "-d" then density = tonumber(args[i + 1]) i = i + 1
    else usage() return end
    i = i + 1
end

if not con then printError("No such program") return end

if not term.isColor() then
    printError("SQ-OS needs an Advanced Computer (colour).")
    return
end

local sys = require("sqos.sys")
sys.shell, sys.multishell = shell, multishell

local config = require("sqos.config")
local lang = require("sqos.lang")
local backend = require("sqos.backend")
local WM = require(con and "sqos.con_wm" or "sqos.wm")
local name = con and "SQ Con" or "SQ-OS"

local cfg = config.load()
lang.con = con
lang.set(cfg.lang)
if scale then cfg.scale = scale end
if density then cfg.density = density end

local drv, err = backend.open(spec, { scale = cfg.scale, density = cfg.density, con = con })
if not drv then
    printError(err)
    return
end

local apps = {}
for _, a in ipairs(require("sqos.apps")) do
    if a.os == "both" or (a.os == "con") == (con == true) then apps[#apps + 1] = a end
end
local wm = WM.new(drv, cfg, apps)
wm.lockOnStart = true
if drv.kind ~= "terminal" then
    term.clear()
    term.setCursorPos(1, 1)
    print(name .. " runs on " .. tostring(drv.name) .. " (" .. drv.kind .. ").")
    print("Keyboard: goes to the active window.")
    print("Quit: Start > Quit.")
end

local ok, res = pcall(wm.run, wm)
drv.restore()
if drv.kind ~= "terminal" then
    term.clear()
    term.setCursorPos(1, 1)
end
if not ok then
    printError(tostring(res))
elseif res == "reboot" then
    os.reboot()
end
