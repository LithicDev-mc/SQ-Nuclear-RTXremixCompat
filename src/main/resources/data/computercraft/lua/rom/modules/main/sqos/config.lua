local M = {}

M.path = "/.sqos/config"

M.defaults = {
    theme = "modern",
    anim = true,
    lang = "en",
    nick = "",
    clock = "game",
    singleTap = false,
    scale = 0.5,
    density = 128,
    wall = "plain",
    sound = true,
    saver = 0,
    helpSeen = false,
    pins = { "chat", "bank", "explorer", "notes", "settings" },
    conPins = { "plant", "storage", "system", "settings" },
}

function M.load()
    local cfg = {}
    for k, v in pairs(M.defaults) do cfg[k] = v end
    if fs.exists(M.path) then
        local h = fs.open(M.path, "r")
        if h then
            local data = textutils.unserialise(h.readAll() or "")
            h.close()
            if type(data) == "table" then
                for k, v in pairs(data) do
                    if M.defaults[k] ~= nil and type(v) == type(M.defaults[k]) then cfg[k] = v end
                end
            end
        end
    end
    return cfg
end

function M.save(cfg)
    local out = {}
    for k in pairs(M.defaults) do out[k] = cfg[k] end
    local ok = pcall(function()
        local h = fs.open(M.path, "w")
        h.write(textutils.serialise(out))
        h.close()
    end)
    return ok
end

return M
