local theme = require("sqos.theme")
local text = require("sqos.apps.boerse_text")
local ui = require("sqos.apps.bankui")
local C = theme.C
local T, money = text.T, text.money

local M = {}

local CONFIRM = { confirm = true }

local function change(permille)
    local v = permille / 10
    return string.format("%s%.1f%%", v > 0 and "+" or "", v)
end

local function rateRows(p)
    local rows = {}
    for _, g in ipairs(p.exchangeGoods()) do
        local up = g.last > 0 and g.changePermille > 0
        local down = g.last > 0 and g.changePermille < 0
        local label = string.format("%-14s", g.name:sub(1, 14))
        local bid, ask = g.bid > 0 and tostring(g.bid) or "-", g.ask > 0 and tostring(g.ask) or "-"
        local info = g.last > 0 and change(g.changePermille) or T("no_trade_short")
        rows[#rows + 1] = {
            text = label .. " " .. info, item = g.item, name = g.name, amount = g.last > 0 and g.last or nil, sign = 0,
            amountColor = up and C.ok or (down and C.alarm or C.text),
            marker = up and C.ok or (down and C.alarm or C.dim),
            detail = {
                { T("detail_item"), g.name },
                { T("last"), g.last > 0 and money(g.last) or T("no_trade"), up and C.ok or (down and C.alarm or nil) },
                { T("change24"), g.last > 0 and change(g.changePermille) or "-" },
                { T("high") .. " / " .. T("low"), g.last > 0 and (money(g.high) .. " / " .. money(g.low)) or "-" },
                { T("volume"), money(g.volume) },
                { T("bid_long") .. " / " .. T("ask_long"), bid .. " / " .. ask },
                { T("stock"), money(g.stock) },
            },
        }
    end
    return rows
end

local function tradeView(p, S)
    if not S.good then return {}, { { T("pick_good"), C.warn } }, nil end
    local b = p.exchangeBook(S.good)
    local rows = {}
    for i = #b.asks, 1, -1 do
        local a = b.asks[i]
        rows[#rows + 1] = { text = T("row_ask", a.qty), amount = a.price, sign = 0, amountColor = C.alarm, marker = C.alarm,
            detail = { { T("side_sell"), T("row_ask", a.qty) }, { T("detail_price"), money(a.price), C.alarm } } }
    end
    for _, a in ipairs(b.bids) do
        rows[#rows + 1] = { text = T("row_bid", a.qty), amount = a.price, sign = 0, amountColor = C.ok, marker = C.ok,
            detail = { { T("side_buy"), T("row_bid", a.qty) }, { T("detail_price"), money(a.price), C.ok } } }
    end
    local head = b.last > 0 and (T("last") .. " " .. money(b.last)) or T("no_trade")
    local hero = {
        label = b.name .. "   " .. head .. (b.last > 0 and ("   " .. change(b.changePermille)) or ""),
        chart = { values = b.history, empty = T("chart_empty") },
        sub = T("day") .. ": " .. T("high") .. " " .. money(b.high) .. "  " .. T("low") .. " " .. money(b.low) .. "  "
            .. T("volume") .. " " .. money(b.volume) .. "   " .. T("stock") .. " " .. money(b.stock),
    }
    return rows, {}, hero
end

local function alarmsOf(S)
    local D = ui.data(S.st and S.st.user, "exchange")
    D.alarms = D.alarms or {}
    return D.alarms
end

local function pollAlarms(p, S, A)
    local al = alarmsOf(S)
    if #al == 0 then return end
    local ok, goods = pcall(p.exchangeGoods)
    if not ok then return end
    local last = {}
    for _, g in ipairs(goods) do last[g.item] = g.last end
    for i = #al, 1, -1 do
        local a, v = al[i], last[al[i].item]
        if v and v > 0 and ((a.above and v >= a.above) or (a.below and v <= a.below)) then
            A.notify(T(a.above and v >= a.above and "alarm_above" or "alarm_below", a.name, money(v)), "warn")
            table.remove(al, i)
            ui.saveData()
        end
    end
end

local function dAlarm(A, row)
    A.dialog({ title = T("alarm") .. ": " .. row.name, fields = {
        { id = "above", label = T("ask_above"), kind = "number", max = 10, optional = true },
        { id = "below", label = T("ask_below"), kind = "number", max = 10, optional = true } },
        ok = function(v)
            if not v.above and not v.below then A.msg(T("alarm_need"), "err") return end
            local al = alarmsOf(A.S)
            al[#al + 1] = { item = row.item, name = row.name, above = v.above, below = v.below }
            ui.saveData()
            A.msg(T("alarm_set"), "ok")
        end })
end

local function alarmRows(p, S)
    local rows = {}
    for i, a in ipairs(alarmsOf(S)) do
        local cond = (a.above and (T("above") .. " " .. money(a.above)) or "") .. (a.above and a.below and "  " or "") .. (a.below and (T("below") .. " " .. money(a.below)) or "")
        rows[#rows + 1] = { text = a.name .. "  " .. cond, idx = i, marker = C.warn,
            detail = { { T("detail_item"), a.name }, { T("above"), a.above and money(a.above) or "-" }, { T("below"), a.below and money(a.below) or "-" } } }
    end
    return rows, rows[1] and {} or { { T("alarm_empty"), C.dim } }
end

local function dOrder(side)
    return function(A)
        if not A.S.good then
            A.msg(T("pick_good"), "err")
            return
        end
        local good = A.S.good
        A.dialog({ title = (side == "buy" and T("buy") or T("sell")), fields = {
            { id = "price", label = T("ask_price"), kind = "number", max = 10 },
            { id = "qty", label = T("ask_qty"), kind = "number", max = 5 } },
            ok = function(v)
                A.run(function(p)
                    local r = p.exchangePlace(side, good, v.price, v.qty)
                    return T("placed", r.id, r.filled, v.qty, r.remaining)
                end, CONFIRM)
            end })
    end
end

local function doDeposit(A)
    A.run(function(p)
        local ok, n = pcall(p.exchangeDeposit)
        if not ok then
            if tostring(n):find("no tradable goods", 1, true) then
                local why = tostring(n):match("%((.*)%)%s*$")
                return T("nothing_to_deposit") .. (why and (" " .. why) or "")
            end
            error(n, 0)
        end
        return T("deposited", n)
    end)
end

local function dWithdraw(A, row)
    A.dialog({ title = T("withdraw"), fields = { { id = "qty", label = T("ask_qty"), kind = "number", max = 4,
        default = row and tostring(math.min(row.count, 1152)) or "" } },
        ok = function(v)
            A.run(function(p)
                p.exchangeWithdraw(row.item, v.qty)
                return T("withdrawn", v.qty)
            end, CONFIRM)
        end })
end

M.run = ui.listApp({ T = T, title = function() return T("title") end,
    menus = {
        { label = "menu_trade", items = { { "buy", dOrder("buy") }, { "sell", dOrder("sell") } } },
        { label = "menu_storage", items = { { "deposit", doDeposit } } },
    },
    poll = pollAlarms,
    tabs = {
        { key = "tab_rates", load = function(p) return rateRows(p), { { T("fee_note", 2), C.dim } } end,
          actions = function()
              return { { T("trade"), function(A, r)
                  A.S.good = r.item
                  A.jump(2)
              end, "primary" }, { T("alarm"), dAlarm } }
          end },
        { key = "tab_trade", load = tradeView,
          buttons = { { T("buy"), dOrder("buy"), "good" }, { T("sell"), dOrder("sell"), "danger" } } },
        { key = "tab_orders",
          load = function(p)
              local rows = {}
              for _, o in ipairs(p.exchangeOrders()) do
                  local buy = o.side == "buy"
                  rows[#rows + 1] = {
                      text = (buy and T("side_buy") or T("side_sell")) .. " #" .. o.id .. "  " .. o.qty .. "/" .. o.original .. "x " .. o.name,
                      amount = o.price, sign = 0, amountColor = buy and C.ok or C.alarm, marker = buy and C.ok or C.alarm, id = o.id,
                      detail = {
                          { T("detail_number"), "#" .. o.id },
                          { T("detail_item"), o.name },
                          { T("side"), buy and T("side_buy") or T("side_sell"), buy and C.ok or C.alarm },
                          { T("detail_price"), money(o.price) },
                          { T("qty_open"), o.qty .. " / " .. o.original },
                      },
                  }
              end
              return rows, {}
          end,
          actions = function() return { { T("cancel"), function(A, r) A.run(function(p) p.exchangeCancel(r.id) end) end, "danger" } } end },
        { key = "tab_stock",
          load = function(p)
              local rows = {}
              for _, s in ipairs(p.exchangeStock()) do
                  rows[#rows + 1] = { text = money(s.count) .. "x " .. s.name, item = s.item, count = s.count, marker = C.ok,
                      detail = { { T("detail_item"), s.name }, { T("detail_id"), s.item }, { T("stock"), money(s.count) } } }
              end
              return rows, { { rows[1] and T("stock_head") or T("none_stock"), C.dim } }
          end,
          buttons = { { T("deposit"), doDeposit, "good" } },
          actions = function() return { { T("withdraw"), dWithdraw, "primary" } } end },
        { key = "tab_alarms", load = alarmRows,
          actions = function() return { { T("delete"), function(A, r) table.remove(alarmsOf(A.S), r.idx) ui.saveData() A.reload() end, "danger" } } end },
    } })

return M
