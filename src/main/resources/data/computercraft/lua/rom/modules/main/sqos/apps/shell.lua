local sys = require("sqos.sys")

local M = {}

function M.run(ctx)
    term.setBackgroundColor(colors.black)
    term.setTextColor(colors.white)
    term.clear()
    term.setCursorPos(1, 1)
    local sh = sys.shell
    if sh then
        pcall(sh.run, "shell")
    else
        print("no shell")
        os.pullEvent("key")
    end
end

return M
