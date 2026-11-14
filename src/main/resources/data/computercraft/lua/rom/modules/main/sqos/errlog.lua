local M = {}

M.PATH = "sqos_error.log"
local MAX_BYTES = 20000

function M.wrap(msg, width)
    width = math.max(8, width or 40)
    local out = {}
    for para in (tostring(msg) .. "\n"):gmatch("(.-)\n") do
        para = para:gsub("\r", ""):gsub("\t", "    ")
        if para == "" then
            out[#out + 1] = ""
        else
            local line = ""
            for word in para:gmatch("%S+") do
                while #word > width do
                    if line ~= "" then
                        out[#out + 1] = line
                        line = ""
                    end
                    out[#out + 1] = word:sub(1, width)
                    word = word:sub(width + 1)
                end
                if line == "" then line = word
                elseif #line + 1 + #word <= width then line = line .. " " .. word
                else
                    out[#out + 1] = line
                    line = word
                end
            end
            if line ~= "" then out[#out + 1] = line end
        end
    end
    return out
end

function M.write(source, msg)
    pcall(function()
        local stamp = "day " .. tostring(os.day()) .. " " .. textutils.formatTime(os.time(), true)
        local entry = stamp .. " [" .. tostring(source) .. "] " .. tostring(msg) .. "\n"
        local old = ""
        if fs.exists(M.PATH) then
            local f = fs.open(M.PATH, "r")
            if f then
                old = f.readAll() or ""
                f.close()
            end
        end
        local all = old .. entry
        if #all > MAX_BYTES then all = all:sub(#all - MAX_BYTES + 1) end
        local f = fs.open(M.PATH, "w")
        if f then
            f.write(all)
            f.close()
        end
    end)
end

return M
