local lang = require("sqos.lang")
local bank = require("sqos.apps.bank_text")

local de = {
    title = "BOERSE",
    tab_rates = "Kurse", tab_trade = "Handel", tab_orders = "Orders", tab_stock = "Lager",
    trade = "Handeln", buy = "Kaufen", sell = "Verkaufen", cancel = "Zurueck ziehen",
    deposit = "Einlagern", withdraw = "Auslagern",
    ask_price = "Limit je Stueck:", ask_qty = "Menge:",
    pick_good = "Waehle im Reiter Kurse eine Ware und tippe Handeln.",
    no_trade = "noch kein Handel", no_trade_short = "kein Handel", chart_empty = "Noch kein Handel",
    last = "Kurs", high = "Hoch", low = "Tief", volume = "Menge", stock = "Lager", day = "24h",
    bid = "K", ask = "V",
    side_buy = "KAUF", side_sell = "VERKAUF",
    row_ask = "Verkauf  %d Stueck", row_bid = "Kauf     %d Stueck",
    placed = "Order #%d: %d von %d sofort ausgefuehrt, im Buch: %d",
    nothing_to_deposit = "Nichts zum Einlagern: Der Behaelter enthaelt keine handelbaren Waren.",
    deposited = "%d Stueck eingelagert", withdrawn = "%d Stueck ausgelagert",
    stock_head = "Waren in den Behaelter am Terminal legen, dann Einlagern.",
    fee_note = "Gebuehr %d%% fuer den Verkaeufer",
    none_stock = "Lager ist leer",
    title_ticker = "KURSTAFEL", tab_ticker = "Kurse",
    menu_trade = "Handel", menu_storage = "Lager", change24 = "Aenderung 24h", bid_long = "Bestes Kaufgebot", ask_long = "bestes Verkaufsgebot",
    side = "Seite", qty_open = "Menge offen/gesamt",
    tab_alarms = "Alarme", alarm = "Alarm", ask_above = "Ueber:", ask_below = "Unter:", above = "ueber", below = "unter",
    alarm_need = "Mindestens eine Grenze angeben", alarm_set = "Kursalarm gesetzt", alarm_empty = "Keine Kursalarme. Im Reiter Kurse eine Ware waehlen und Alarm antippen. Gemeldet wird, solange die Boerse offen ist.",
    alarm_above = "%s ist auf %s gestiegen", alarm_below = "%s ist auf %s gefallen",
}

local en = {
    title = "EXCHANGE",
    tab_rates = "Prices", tab_trade = "Trade", tab_orders = "Orders", tab_stock = "Storage",
    trade = "Trade", buy = "Buy", sell = "Sell", cancel = "Withdraw",
    deposit = "Store", withdraw = "Take out",
    ask_price = "Limit per piece:", ask_qty = "Amount:",
    pick_good = "Pick a good in the Prices tab and tap Trade.",
    no_trade = "no trades yet", no_trade_short = "no trades", chart_empty = "No trades yet",
    last = "Price", high = "High", low = "Low", volume = "Volume", stock = "Storage", day = "24h",
    bid = "B", ask = "A",
    side_buy = "BUY", side_sell = "SELL",
    row_ask = "Sell     %d pieces", row_bid = "Buy      %d pieces",
    placed = "Order #%d: %d of %d filled at once, in the book: %d",
    nothing_to_deposit = "Nothing to store: the container holds no tradable goods.",
    deposited = "%d pieces stored", withdrawn = "%d pieces taken out",
    stock_head = "Put goods into the terminal container, then Store.",
    fee_note = "Fee %d%% paid by the seller",
    none_stock = "Storage is empty",
    title_ticker = "PRICE BOARD", tab_ticker = "Prices",
    menu_trade = "Trade", menu_storage = "Storage", change24 = "Change 24h", bid_long = "Best bid", ask_long = "best ask",
    side = "Side", qty_open = "Amount open/total",
    tab_alarms = "Alerts", alarm = "Alert", ask_above = "Above:", ask_below = "Below:", above = "above", below = "below",
    alarm_need = "Enter at least one limit", alarm_set = "Price alert set", alarm_empty = "No price alerts. Pick a good in the Prices tab and tap Alert. Alerts fire while the exchange is open.",
    alarm_above = "%s rose to %s", alarm_below = "%s fell to %s",
}

local tables = { de = de, en = en }

local M = {}

function M.T(key, ...)
    local s = (tables[lang.code] or de)[key] or de[key]
    if not s then return bank.T(key, ...) end
    if select("#", ...) > 0 then
        local ok, r = pcall(string.format, s, ...)
        if ok then return r end
    end
    return s
end

M.money = bank.money

return M
