local M = {}

M.C = {}

local order = { "white", "orange", "magenta", "lightBlue", "yellow", "lime", "pink", "gray",
    "lightGray", "cyan", "purple", "blue", "brown", "green", "red", "black" }

M.themes = {
    modern = {
        white = 0xDDDDE0, gray = 0x8E8E96, lightGray = 0x2A2A2D, cyan = 0x202022, brown = 0x19191B, black = 0x30323C,
        blue = 0xE8A33D, lightBlue = 0xB4C2FF, yellow = 0xE8A33D, lime = 0x46C46A, green = 0x46C46A, red = 0xE05A5A,
        orange = 0xD98A4A, magenta = 0xB79CFF, pink = 0xE38AB0, purple = 0x8E7CF0,
    },
    console = {
        white = 0x41FF7A, gray = 0x22B04E, lightGray = 0x040A06, cyan = 0x020503, brown = 0x08160C, black = 0x0B3319,
        blue = 0x41FF7A, lightBlue = 0x9CFFB8, yellow = 0xFFB23A, lime = 0x41FF7A, green = 0x22B04E, red = 0xFF5A4E,
        orange = 0xFFB23A, magenta = 0x9C8CFF, pink = 0x5FE3FF, purple = 0x9C8CFF,
    },
}

M.names = { "modern", "console" }

function M.apply(name)
    if not M.themes[name] then name = "modern" end
    local C = M.C
    C.desk, C.face, C.panel, C.light = colors.cyan, colors.lightGray, colors.brown, colors.black
    C.text, C.dim, C.titleText = colors.white, colors.gray, colors.white
    C.title, C.titleOff, C.barBg = colors.brown, colors.brown, colors.black
    C.accent, C.accent2 = colors.blue, colors.lightBlue
    C.sel, C.selText = colors.blue, colors.brown
    C.ok, C.warn, C.alarm, C.close = colors.lime, colors.yellow, colors.red, colors.red
    C.bar = colors.green
    C.task, C.taskActive = C.panel, C.light
    M.name = name
    M.edge = name == "console" and 0x136B30 or 0x38383C
    local out = {}
    for i, n in ipairs(order) do out[i] = M.themes[name][n] end
    return out
end

M.palette = M.apply

M.apply("modern")

return M
