local function app(id, titleKey, icon, mod, fn, extra)
    local a = { id = id, titleKey = titleKey, icon = icon }
    a.run = function(ctx) return require("sqos.apps." .. mod)[fn](ctx) end
    for k, v in pairs(extra or {}) do a[k] = v end
    return a
end

return {
    app("plant", "app_plant", { "K", colors.red }, "plant", "run", { os = "con", keys = "pl_keys", menu = true, w = 56, h = 17 }),
    app("storage", "app_storage", { "S", colors.green }, "storage", "run", { os = "con", keys = "pl_keys", menu = true, w = 50, h = 15 }),
    app("chat", "app_chat", { "C", colors.lime }, "chat", "run", { keys = "kb_chat", desktop = true, menu = true, w = 54, h = 16 }),
    app("explorer", "app_explorer", { "D", colors.orange }, "explorer", "run", { keys = "kb_explorer", desktop = true, menu = true, w = 40, h = 15 }),
    app("editor", "app_editor", { "E", colors.blue }, "editor", "run", { keys = "kb_editor", desktop = true, menu = true, multi = true, w = 46, h = 16 }),
    app("shell", "app_shell", { ">", colors.black }, "shell", "run", { os = "both", desktop = true, menu = true, multi = true, w = 44, h = 14 }),
    app("login", "app_login", { "L", colors.cyan }, "login", "run", { desktop = true, menu = true, w = 40, h = 16 }),
    app("notes", "app_notes", { "N", colors.yellow }, "notes", "run", { desktop = true, menu = true, w = 40, h = 14 }),
    app("calc", "app_calc", { "=", colors.gray }, "calc", "run", { keys = "kb_calc", desktop = true, menu = true, w = 22, h = 14 }),
    app("system", "app_system", { "i", colors.lightBlue }, "system", "run", { os = "both", desktop = true, menu = true, w = 46, h = 16 }),
    app("help", "app_help", { "?", colors.lightBlue }, "help", "run", { os = "both", desktop = true, menu = true, w = 50, h = 17 }),
    app("settings", "app_settings", { "*", colors.gray }, "settings", "run", { os = "both", desktop = true, menu = true, w = 44, h = 18 }),
    app("bank", nil, { "B", colors.yellow }, "bank", "run", { keys = "kb_bank", name = "Bank", desktop = true, menu = true, w = 56, h = 16 }),
    app("auction", "app_auction", { "A", colors.magenta }, "bank", "runAuction", { keys = "kb_bank", desktop = true, menu = true, w = 56, h = 16 }),
    app("exchange", "app_exchange", { "$", colors.lime }, "boerse", "run", { keys = "kb_bank", desktop = true, menu = true, w = 56, h = 18 }),
    app("ticker", "app_ticker", { "T", colors.cyan }, "tafel", "run", { desktop = true, menu = true, w = 56, h = 16 }),
    app("runner", "app_runner", { "P", colors.purple }, "runner", "run", { w = 44, h = 14 }),
}
