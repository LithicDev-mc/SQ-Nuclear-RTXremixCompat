local theme = require("sqos.theme")
local text = require("sqos.apps.boerse_text")
local ui = require("sqos.apps.bankui")
local C = theme.C
local T, money = text.T, text.money

local M = {}

local function change(permille)
    local v = permille / 10
    return string.format("%s%.1f%%", v > 0 and "+" or "", v)
end

M.run = ui.listApp({ T = T, title = function() return T("title_ticker") end, tabs = {
    { key = "tab_ticker",
      load = function(p)
          local rows = {}
          for _, g in ipairs(p.exchangeTicker()) do
              local up = g.last > 0 and g.changePermille > 0
              local down = g.last > 0 and g.changePermille < 0
              local info = g.last > 0 and change(g.changePermille) or T("no_trade_short")
              rows[#rows + 1] = {
                  text = string.format("%-14s", g.name:sub(1, 14)) .. " " .. info,
                  amount = g.last > 0 and g.last or nil, sign = 0,
                  amountColor = up and C.ok or (down and C.alarm or C.text),
                  marker = up and C.ok or (down and C.alarm or C.dim),
                  detail = {
                      { T("detail_item"), g.name },
                      { T("last"), g.last > 0 and money(g.last) or T("no_trade"), up and C.ok or (down and C.alarm or nil) },
                      { T("change24"), g.last > 0 and change(g.changePermille) or "-" },
                      { T("high") .. " / " .. T("low"), g.last > 0 and (money(g.high) .. " / " .. money(g.low)) or "-" },
                      { T("volume"), money(g.volume) },
                  },
              }
          end
          return rows, {}
      end },
} })

return M
