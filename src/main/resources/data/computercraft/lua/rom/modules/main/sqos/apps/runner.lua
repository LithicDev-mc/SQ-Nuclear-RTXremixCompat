local sys = require("sqos.sys")

local M = {}

function M.run(ctx)
    local cmd = ctx.args.cmd
    local sh = sys.shell
    term.setBackgroundColor(colors.black)
    term.setTextColor(colors.white)
    term.clear()
    term.setCursorPos(1, 1)
    if sh and cmd then
        local ok, err = pcall(sh.run, cmd)
        if not ok and not tostring(err):find("Terminated", 1, true) then
            term.setTextColor(colors.red)
            print(tostring(err))
        end
    else
        print("no shell")
    end
    term.setTextColor(colors.yellow)
    print()
    print(ctx.T("press_key"))
    while true do
        local e = os.pullEvent()
        if e == "key" or e == "mouse_click" then break end
    end
end

return M
