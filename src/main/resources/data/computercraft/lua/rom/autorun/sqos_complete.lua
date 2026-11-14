local completion = require "cc.shell.completion"
for _, name in ipairs({ "sqcon" }) do
    shell.setCompletionFunction("rom/programs/" .. name .. ".lua", completion.build(
        { completion.choice, { "-m ", "-s ", "-d " } },
        { completion.peripheral }
    ))
end
