local theme = require("sqos.theme")
local text = require("sqos.apps.bank_text")
local ui = require("sqos.apps.bankui")
local C = theme.C
local T, money = text.T, text.money
local statusText, markerColor, listApp = ui.statusText, ui.markerColor, ui.listApp

local M = {}

local CONFIRM = { confirm = true }

local function dWithdraw(A)
    A.dialog({ title = T("withdraw"), fields = { { id = "amount", label = T("ask_amount"), kind = "number", max = 13 } },
        ok = function(v) A.S.act = "withdraw" A.run(function(p) p.withdraw(v.amount) end, CONFIRM) end })
end

local function dSend(A)
    A.dialog({ title = T("send"), fields = {
        { id = "name", label = T("ask_name"), kind = "text", max = 16 },
        { id = "amount", label = T("ask_amount"), kind = "number", max = 13 } },
        ok = function(v) A.S.act = T("send") .. " " .. v.name A.run(function(p) p.transfer(v.name, v.amount) end, CONFIRM) end })
end

local function dRequest(A)
    A.dialog({ title = T("request"), fields = {
        { id = "name", label = T("ask_name"), kind = "text", max = 16 },
        { id = "amount", label = T("ask_amount"), kind = "number", max = 13 },
        { id = "note", label = T("ask_note"), kind = "text", optional = true, max = 40 } },
        ok = function(v) A.run(function(p) p.requestMoney(v.name, v.amount, v.note or "") end) end })
end

local function dLoan(A)
    A.dialog({ title = T("new_loan"), fields = {
        { id = "name", label = T("ask_name"), kind = "text", max = 16 },
        { id = "amount", label = T("ask_amount"), kind = "number", max = 13 },
        { id = "interest", label = T("ask_interest"), kind = "number", max = 3, default = "5" },
        { id = "days", label = T("ask_days"), kind = "number", max = 2, default = "7" } },
        ok = function(v) A.run(function(p) p.offerLoan(v.name, v.amount, v.interest, v.days) end, CONFIRM) end })
end

local function doDeposit(A)
    A.S.act = "deposit"
    A.run(function(p) return T("deposited", money(p.deposit())) end)
end

local LOG_MAX = 200

local function bankData(S)
    return ui.data(S.st and S.st.user, "bank")
end

local function pollBank(p, S, A)
    local D = bankData(S)
    D.log, D.orders = D.log or {}, D.orders or {}
    local bal = S.st.balance
    local dirty = false
    if bal and D.last and bal ~= D.last then
        local delta = bal - D.last
        local kind = S.act
        if type(kind) == "string" and T(kind) ~= kind then kind = T(kind) end
        table.insert(D.log, 1, { d = os.day(), t = os.time(), a = delta, k = kind or T(delta > 0 and "log_in" or "log_out") })
        while #D.log > LOG_MAX do table.remove(D.log) end
        dirty = true
    end
    if bal then D.last = bal S.act = nil end
    for _, o in ipairs(D.orders) do
        if os.day() >= (o.next or 0) and not o.told then
            o.told = true
            dirty = true
            A.notify(T("so_due", money(o.amount), o.name), "warn")
        end
    end
    if dirty then ui.saveData() end
end

local function logRows(p, S)
    local D = bankData(S)
    local rows, f = {}, S.filter or "all"
    for _, e in ipairs(D.log or {}) do
        if f == "all" or (f == "in" and e.a > 0) or (f == "out" and e.a < 0) then
            rows[#rows + 1] = { text = ui.stamp(e.d, e.t) .. "  " .. e.k, amount = math.abs(e.a), sign = e.a > 0 and 1 or -1,
                marker = e.a > 0 and C.ok or C.alarm,
                detail = { { T("detail_time"), ui.stamp(e.d, e.t) }, { T("detail_text"), e.k }, { T("detail_amount"), money(e.a), e.a > 0 and C.ok or C.alarm } } }
        end
    end
    return rows, rows[1] and {} or { { T("log_empty"), C.dim } }
end

local function filterButtons(S)
    local f = S.filter or "all"
    local function b(key, id) return { T(key), function(A) A.S.filter = id A.reload() end, f == id and "primary" or nil } end
    return { b("filter_all", "all"), b("filter_in", "in"), b("filter_out", "out"),
        { T("log_clear"), function(A) bankData(A.S).log = {} ui.saveData() A.reload() end, "danger" } }
end

local function dOrder(A)
    A.dialog({ title = T("so_new"), fields = {
        { id = "name", label = T("ask_name"), kind = "text", max = 16 },
        { id = "amount", label = T("ask_amount"), kind = "number", max = 13 },
        { id = "days", label = T("ask_every"), kind = "number", max = 3, default = "7" } },
        ok = function(v)
            if v.days < 1 then A.msg(T("bad_number"), "err") return end
            local D = bankData(A.S)
            D.orders = D.orders or {}
            D.orders[#D.orders + 1] = { name = v.name, amount = v.amount, days = v.days, next = os.day() + v.days }
            ui.saveData()
        end })
end

local function orderRows(p, S)
    local rows = {}
    for i, o in ipairs(bankData(S).orders or {}) do
        local due = os.day() >= (o.next or 0)
        rows[#rows + 1] = { text = o.name .. "  " .. T("so_every", o.days) .. "  " .. (due and T("so_due_short") or T("so_next", o.next)),
            amount = o.amount, sign = -1, marker = due and C.warn or C.accent, idx = i, order = o,
            detail = { { T("to"), o.name }, { T("detail_amount"), money(o.amount), C.alarm }, { T("so_interval"), T("so_every", o.days) },
                { T("so_next_label"), "T" .. tostring(o.next) .. (due and ("  " .. T("so_due_short")) or "") } } }
    end
    return rows, rows[1] and {} or { { T("so_empty"), C.dim } }
end

local function orderActions(row)
    return {
        { T("so_run"), function(A, r)
            local o = r.order
            A.S.act = T("so_title") .. " " .. o.name
            A.run(function(p)
                p.transfer(o.name, o.amount)
                o.next, o.told = os.day() + o.days, nil
                ui.saveData()
            end, CONFIRM)
        end, "good" },
        { T("delete"), function(A, r)
            table.remove(bankData(A.S).orders, r.idx)
            ui.saveData()
            A.reload()
        end, "danger" },
    }
end

local function doLogout(A)
    A.run(function(p) p.logout() end)
end

local function requestRows(p)
    local r = p.requests()
    local rows = {}
    local function add(q, incoming)
        local open = q.status == "open"
        rows[#rows + 1] = {
            text = "#" .. q.id .. " " .. (incoming and (T("from") .. " " .. q.from) or (T("to") .. " " .. q.to))
                .. (q.note ~= "" and ("  " .. q.note) or "") .. (open and "" or ("  " .. statusText(q.status))),
            amount = q.amount, sign = open and (incoming and -1 or 1) or 0,
            marker = markerColor(q.status), color = open and nil or C.dim,
            open = open, id = q.id, mine = not incoming,
            detail = {
                { T("detail_number"), "#" .. q.id },
                { T("from"), q.from },
                { T("to"), q.to },
                { T("detail_amount"), money(q.amount), incoming and C.alarm or C.ok },
                { T("detail_note"), q.note ~= "" and q.note or "-" },
                { T("detail_status"), statusText(q.status) },
            },
        }
    end
    for _, q in ipairs(r.incoming or {}) do add(q, true) end
    for _, q in ipairs(r.outgoing or {}) do add(q, false) end
    return rows
end

local function loanRows(p)
    local r = p.loans()
    local rows = {}
    local function add(l, mine)
        local who = mine and (T("lent") .. " " .. T("to") .. " " .. l.borrower) or (T("borrowed") .. " " .. T("from") .. " " .. l.lender)
        local s = "#" .. l.id .. " " .. who .. "  " .. l.interest .. "%  " .. statusText(l.status)
        if l.daysLeft then s = s .. "  " .. T("days_left", l.daysLeft) end
        if l.overdue then s = s .. "  " .. T("overdue") end
        local live = l.status == "open" or l.status == "active"
        rows[#rows + 1] = {
            text = s, amount = l.repay, sign = live and (mine and 1 or -1) or 0, marker = markerColor(l.status, l.overdue),
            color = live and nil or C.dim, status = l.status, id = l.id, mine = mine,
            detail = {
                { T("detail_number"), "#" .. l.id },
                { T("lender"), l.lender },
                { T("borrower"), l.borrower },
                { T("detail_amount"), money(l.amount) },
                { T("ask_interest"), l.interest .. "%" },
                { T("repay_amount"), money(l.repay), mine and C.ok or C.alarm },
                { T("ask_days"), tostring(l.days) .. (l.daysLeft and ("  (" .. T("days_left", l.daysLeft) .. ")") or "") },
                { T("detail_status"), statusText(l.status) .. (l.overdue and ("  " .. T("overdue")) or ""), l.overdue and C.alarm or nil },
            },
        }
    end
    for _, l in ipairs(r.lent or {}) do add(l, true) end
    for _, l in ipairs(r.borrowed or {}) do add(l, false) end
    return rows
end

M.run = listApp({ title = function() return T("title_bank") end,
    menus = {
        { label = "menu_account", items = { { "deposit", doDeposit }, { "withdraw", dWithdraw }, "-", { "logout", doLogout } } },
        { label = "menu_payment", items = { { "send", dSend }, { "request", dRequest } } },
        { label = "menu_loan", items = { { "new_loan", dLoan } } },
        { label = "menu_orders", items = { { "so_new", dOrder } } },
    },
    poll = pollBank,
    tabs = {
        { key = "tab_account", noDetail = true,
          load = function(p)
              local st = p.status()
              local head, hero = {}, nil
              if not st.online then head[#head + 1] = { T("offline_" .. tostring(st.link)), C.warn } end
              if not st.loggedIn then
                  head[#head + 1] = { T("nobody"), C.warn }
              elseif st.balance then
                  hero = { label = T("balance"), digits = tostring(math.floor(st.balance)), text = money(st.balance),
                      sub = T("proceeds") .. ": " .. money(st.proceeds or 0) .. "   " .. T("waiting") .. ": " .. (st.waiting or 0) }
              end
              return {}, head, hero
          end,
          buttons = {
              { T("deposit"), doDeposit, "good" },
              { T("withdraw"), dWithdraw },
              { T("send"), dSend, "primary" },
              { T("request"), dRequest },
              { T("logout"), doLogout, "danger" },
          } },
        { key = "tab_requests", load = requestRows,
          actions = function(row)
              if not row.open then return {} end
              if row.mine then return { { T("cancel"), function(A, r) A.run(function(p) p.cancelRequest(r.id) end) end, "danger" } } end
              return { { T("accept"), function(A, r) A.S.act = T("request") .. " #" .. r.id A.run(function(p) p.answerRequest(r.id, true) end, CONFIRM) end, "good" },
                       { T("decline"), function(A, r) A.run(function(p) p.answerRequest(r.id, false) end) end, "danger" } }
          end },
        { key = "tab_loans", load = loanRows,
          buttons = { { T("new_loan"), dLoan, "primary" } },
          actions = function(row)
              if row.status == "open" and row.mine then
                  return { { T("cancel"), function(A, r) A.run(function(p) p.cancelLoan(r.id) end) end, "danger" } }
              end
              if row.status == "open" then
                  return { { T("accept"), function(A, r) A.run(function(p) p.answerLoan(r.id, true) end, CONFIRM) end, "good" },
                           { T("decline"), function(A, r) A.run(function(p) p.answerLoan(r.id, false) end) end, "danger" } }
              end
              if row.status == "active" and not row.mine then
                  return { { T("repay"), function(A, r) A.S.act = "repay" A.run(function(p) p.repayLoan(r.id) end, CONFIRM) end, "primary" } }
              end
              return {}
          end },
        { key = "tab_log", load = logRows, buttons = filterButtons },
        { key = "tab_orders", load = orderRows, buttons = { { T("so_new"), dOrder, "primary" } }, actions = orderActions },
    } })

local function offerText(o)
    return "#" .. o.id .. "  " .. o.count .. "x " .. o.name .. "  " .. T("seller") .. " " .. o.seller
end

local function offerDetail(o)
    return {
        { T("detail_number"), "#" .. o.id },
        { T("detail_item"), o.count .. "x " .. o.name },
        { T("seller"), o.seller .. (o.own and ("  (" .. T("you") .. ")") or "") },
        { T("detail_price"), money(o.price), C.warn },
        { T("detail_fee"), T("fee", 5) },
    }
end

local function pollAuction(p, S, A)
    local D = ui.data(S.st.user, "auction")
    D.watch, D.told = D.watch or {}, D.told or {}
    S.tick = ((S.tick or 0) + 1) % 10
    if S.tick ~= 1 then return end
    local dirty = false
    for id, w in pairs(D.watch) do
        local ok, r = pcall(p.search, w.name, 1, 50)
        if ok then
            local found = false
            for _, o in ipairs(r.offers or {}) do if o.id == tonumber(id) then found = true end end
            if not found then
                A.notify(T("watch_gone", id, w.count .. "x " .. w.name), "info")
                D.watch[id] = nil
                dirty = true
            end
        end
    end
    local okM, mine = pcall(p.myOffers)
    for _, m in ipairs(okM and mine or {}) do
        local ok, r = pcall(p.search, m.name, 1, 50)
        for _, o in ipairs(ok and r.offers or {}) do
            local key = m.id .. ":" .. o.id
            if not o.own and o.item == m.item and o.price < m.price and not D.told[key] then
                D.told[key] = true
                dirty = true
                A.notify(T("undercut", m.count .. "x " .. m.name, o.seller, money(o.price)), "warn")
            end
        end
    end
    if dirty then ui.saveData() end
end

local function watchOf(S)
    local D = ui.data(S.st and S.st.user, "auction")
    D.watch = D.watch or {}
    return D.watch
end

local function toggleWatch(A, r)
    local w = watchOf(A.S)
    local k = tostring(r.id)
    if w[k] then w[k] = nil else w[k] = { name = r.name, count = r.count, price = r.price, seller = r.seller } end
    ui.saveData()
    A.reload()
end

local function dSearch(A)
    A.dialog({ title = T("search"), fields = { { id = "q", label = T("ask_search"), kind = "text", optional = true, max = 30,
        default = A.S.query or "" } },
        ok = function(v)
            A.S.query = v.q or ""
            A.S.page = 1
            A.reload()
        end })
end

local function doClaim(A)
    A.run(function(p)
        local c = p.claim()
        return T("claimed", money(c.money), c.moved, c.waiting)
    end, CONFIRM)
end

M.runAuction = listApp({ title = function() return T("title_auction") end,
    menus = {
        { label = "menu_market", items = { { "search", dSearch }, { "claim", doClaim } } },
    },
    poll = pollAuction,
    tabs = {
        { key = "tab_market",
          load = function(p, S)
              S.page = S.page or 1
              local r = p.search(S.query or "", S.page, 12)
              S.page, S.pages = r.page, r.pages
              local rows = {}
              for _, o in ipairs(r.offers) do
                  local watched = watchOf(S)[tostring(o.id)] ~= nil
                  rows[#rows + 1] = { text = (watched and "* " or "") .. offerText(o), id = o.id, own = o.own, amount = o.price, sign = 0, amountColor = C.warn,
                      marker = o.own and C.accent or C.warn, detail = offerDetail(o), name = o.name, count = o.count, price = o.price, seller = o.seller, watched = watched }
              end
              local head = { T("search") .. ": " .. ((S.query and S.query ~= "") and S.query or "*") .. "   " .. T("page", r.page, r.pages) .. "   " .. T("fee", 5) }
              return rows, head
          end,
          buttons = {
              { T("search"), dSearch, "primary" },
              { T("prev"), function(A)
                  A.S.page = math.max(1, (A.S.page or 1) - 1)
                  A.reload()
              end },
              { T("next"), function(A)
                  A.S.page = math.min(A.S.pages or 1, (A.S.page or 1) + 1)
                  A.reload()
              end },
          },
          actions = function(row)
              local w = { T(row.watched and "unwatch" or "watch"), toggleWatch }
              if row.own then return { w } end
              return { { T("buy"), function(A, r) A.S.act = "buy" A.run(function(p) p.buy(r.id) end, CONFIRM) end, "good" }, w }
          end },
        { key = "tab_watch",
          load = function(p, S)
              local rows = {}
              for id, w in pairs(watchOf(S)) do
                  rows[#rows + 1] = { text = "#" .. id .. "  " .. w.count .. "x " .. w.name .. "  " .. T("seller") .. " " .. w.seller, id = tonumber(id),
                      amount = w.price, sign = 0, amountColor = C.warn, marker = C.warn, name = w.name, count = w.count, price = w.price, seller = w.seller, watched = true,
                      detail = { { T("detail_number"), "#" .. id }, { T("detail_item"), w.count .. "x " .. w.name }, { T("seller"), w.seller }, { T("detail_price"), money(w.price), C.warn } } }
              end
              table.sort(rows, function(a, b) return a.id < b.id end)
              return rows, rows[1] and { { T("watch_hint"), C.dim } } or { { T("watch_empty"), C.dim } }
          end,
          actions = function()
              return { { T("buy"), function(A, r) A.S.act = "buy" A.run(function(p) p.buy(r.id) end, CONFIRM) end, "good" }, { T("unwatch"), toggleWatch } }
          end },
        { key = "tab_mine",
          load = function(p)
              local rows = {}
              for _, o in ipairs(p.myOffers()) do
                  rows[#rows + 1] = { text = offerText(o), id = o.id, amount = o.price, sign = 1, marker = C.accent, detail = offerDetail(o) }
              end
              return rows, {}
          end,
          actions = function() return { { T("cancel"), function(A, r) A.run(function(p) p.cancelOffer(r.id) end) end, "danger" } } end },
        { key = "tab_sell",
          load = function(p)
              local rows = {}
              for _, s in ipairs(p.inventory()) do
                  if s.coin == 0 then
                      rows[#rows + 1] = { text = "[" .. s.slot .. "]  " .. s.count .. "x " .. s.name, slot = s.slot, marker = C.dim,
                          detail = { { T("detail_slot"), tostring(s.slot) }, { T("detail_item"), s.count .. "x " .. s.name }, { T("detail_id"), s.item } } }
                  end
              end
              return rows, rows[1] and {} or { { T("container_empty"), C.warn } }
          end,
          actions = function() return { { T("sell"), function(A, r)
              A.dialog({ title = T("sell"), fields = { { id = "price", label = T("ask_price"), kind = "number", max = 13 } },
                  ok = function(v) A.run(function(p) p.listItem(r.slot, v.price) end, CONFIRM) end })
          end, "primary" } } end },
        { key = "tab_pickup", noDetail = true,
          load = function(p)
              local r = p.pending()
              local rows = {}
              for _, it in ipairs(r.items) do rows[#rows + 1] = { text = it.count .. "x " .. it.name, marker = C.ok } end
              local head = { { T("pickup_head", money(r.proceeds)), r.proceeds > 0 and C.ok or C.dim } }
              return rows, head
          end,
          buttons = { { T("claim"), doClaim, "good" } } },
    } })

return M
