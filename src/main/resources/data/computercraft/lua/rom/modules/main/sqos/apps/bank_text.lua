local lang = require("sqos.lang")

local de = {
    app_bank = "Bank", app_auction = "Auktion",
    no_bank = "Kein Bank-Terminal am Computer (Peripheral sqc_bank). Den Computer direkt an ein Bank-Terminal stellen, oder an ein Test-Modem, das neben einem Terminal steht.",
    seen = "Am Computer: %s", seen_none = "Am Computer ist nichts angeschlossen.",
    offline_no_server = "Kein Bank-Server in der geladenen Welt. Einen Bank-Server aufstellen.",
    offline_terminal_no_modem = "Das Terminal hat kein Test-Modem daneben und steht nicht direkt am Server. Ein Modem neben das Terminal stellen.",
    offline_server_no_modem = "Neben dem Bank-Server fehlt ein Test-Modem. Eins direkt an den Server stellen.",
    done = "Erledigt", cancel_prompt = "Abgebrochen", bad_number = "Keine gueltige Zahl",
    refresh = "Neu laden",
    tab_account = "Konto", tab_requests = "Anfragen", tab_loans = "Kredite",
    offline = "Kein Bank-Server erreichbar (Modem neben Terminal und Server).",
    nobody = "Niemand angemeldet. Rechtsklick auf das Terminal.",
    user = "Spieler", balance = "Kontostand", proceeds = "Erloes wartet", waiting = "Items warten",
    deposit = "Einzahlen", withdraw = "Auszahlen", send = "Senden", request = "Anfordern", logout = "Abmelden",
    ask_amount = "Betrag:", ask_name = "Spieler:", ask_note = "Text:", ask_interest = "Zins %:", ask_days = "Tage:",
    deposited = "Eingezahlt: %s", incoming = "an dich", outgoing = "von dir",
    accept = "Annehmen", decline = "Ablehnen", cancel = "Zurueck ziehen", repay = "Zurueckzahlen", new_loan = "Neu",
    from = "von", to = "an", lent = "verliehen", borrowed = "geliehen", pay_back = "zurueck", overdue = "ueberfaellig",
    days_left = "%d Tage", st_open = "offen", st_active = "laufend", st_done = "erledigt", st_declined = "abgelehnt", st_cancelled = "zurueckgezogen",
    none = "(nichts)",
    ok = "OK", btn_cancel = "Abbrechen",
    menu_account = "Konto", menu_payment = "Zahlung", menu_loan = "Kredit", menu_market = "Markt",
    error_title = "Fehler", crash_title = "Fehler in der App", restart = "Neu starten", open_log = "Log im Editor",
    log_hint = "Voller Text in %s (Editor-App oder: edit sqos_error.log)",
    select_hint = "Waehle links einen Eintrag.", detail_text = "Eintrag",
    confirm_title = "Bestaetigung noetig", confirm_hint = "Lies den Text in der Aktionsleiste und klicke mit Rechtsklick auf das Bank-Terminal. Esc bricht ab.",
    confirm_timeout = "Nicht bestaetigt (Zeit abgelaufen)",
    detail_number = "Nummer", detail_amount = "Betrag", detail_note = "Text", detail_status = "Status", detail_item = "Ware",
    detail_price = "Preis", detail_fee = "Gebuehr", detail_slot = "Platz", detail_id = "Kennung",
    lender = "Verleiher", borrower = "Kreditnehmer", repay_amount = "Rueckzahlung", you = "du",
    confirm_wait = "Bestaetigen: Rechtsklick aufs Terminal (%ds)",
    title_bank = "BANK", title_auction = "AUKTION", not_logged = "nicht angemeldet",
    tab_market = "Markt", tab_mine = "Meine", tab_sell = "Verkaufen", tab_pickup = "Abholen",
    search = "Suchen", ask_search = "Suche:", prev = "<", next = ">", buy = "Kaufen", sell = "Einstellen", ask_price = "Preis:",
    page = "Seite %d/%d", fee = "Gebuehr %d%%", pickup_head = "Erloes: %s", claim = "Abholen",
    claimed = "Erloes %s, Items %d, wartend %d", container_empty = "Behaelter ist leer. Items ins Terminal legen.",
    seller = "von", coins_skip = "Muenzen sind nicht verkaeuflich",
    tab_log = "Auszug", tab_orders = "Auftraege", tab_watch = "Merkliste", menu_orders = "Dauerauftrag",
    log_in = "Eingang", log_out = "Ausgang", log_empty = "Noch keine Buchungen. Der Auszug fuehrt jede Aenderung des Kontostands, die diese App sieht.",
    log_clear = "Leeren", filter_all = "Alle", filter_in = "Eingang", filter_out = "Ausgang", detail_time = "Zeit", delete = "Loeschen",
    so_new = "Neu", so_title = "Dauerauftrag", ask_every = "Alle ... Tage:", so_every = "alle %d Tage", so_next = "naechster T%d", so_next_label = "Naechster", so_interval = "Abstand",
    so_due_short = "faellig", so_due = "Dauerauftrag faellig: %s an %s (Bank, Reiter Auftraege)", so_run = "Jetzt ueberweisen",
    so_empty = "Keine Dauerauftraege. Neu antippen: Spieler, Betrag, alle wie viele Tage. Faellige Auftraege melden sich, ueberwiesen wird mit Jetzt ueberweisen und Rechtsklick am Terminal.",
    watch = "Merken", unwatch = "Vergessen", watch_hint = "Gemerkte Angebote melden sich, wenn sie verkauft oder zurueckgezogen werden.",
    watch_empty = "Nichts gemerkt. Im Markt ein Angebot waehlen und Merken antippen.",
    watch_gone = "Angebot #%s (%s) ist weg: verkauft oder zurueckgezogen", undercut = "Unterboten: %s bietet %s fuer %s an",
}

local en = {
    app_bank = "Bank", app_auction = "Auctions",
    no_bank = "No bank terminal at the computer (peripheral sqc_bank). Put the computer right next to a bank terminal, or next to a test modem that touches a terminal.",
    seen = "At the computer: %s", seen_none = "Nothing is attached to the computer.",
    offline_no_server = "No bank server in the loaded world. Place a bank server.",
    offline_terminal_no_modem = "The terminal has no test modem next to it and does not touch the server. Put a modem next to the terminal.",
    offline_server_no_modem = "The bank server has no test modem next to it. Put one right against the server.",
    done = "Done", cancel_prompt = "Cancelled", bad_number = "Not a valid number",
    refresh = "Refresh",
    tab_account = "Account", tab_requests = "Requests", tab_loans = "Loans",
    offline = "No bank server reachable (modem next to terminal and server).",
    nobody = "Nobody logged in. Right-click the terminal.",
    user = "Player", balance = "Balance", proceeds = "Proceeds waiting", waiting = "Items waiting",
    deposit = "Deposit", withdraw = "Withdraw", send = "Send", request = "Request", logout = "Log out",
    ask_amount = "Amount:", ask_name = "Player:", ask_note = "Note:", ask_interest = "Interest %:", ask_days = "Days:",
    deposited = "Deposited: %s", incoming = "to you", outgoing = "from you",
    accept = "Accept", decline = "Decline", cancel = "Withdraw", repay = "Repay", new_loan = "New",
    from = "from", to = "to", lent = "lent", borrowed = "borrowed", pay_back = "repay", overdue = "overdue",
    days_left = "%d days", st_open = "open", st_active = "active", st_done = "done", st_declined = "declined", st_cancelled = "withdrawn",
    none = "(nothing)",
    ok = "OK", btn_cancel = "Cancel",
    menu_account = "Account", menu_payment = "Payment", menu_loan = "Loan", menu_market = "Market",
    error_title = "Error", crash_title = "Error in the app", restart = "Restart", open_log = "Log in editor",
    log_hint = "Full text in %s (Editor app or: edit sqos_error.log)",
    select_hint = "Pick an entry on the left.", detail_text = "Entry",
    confirm_title = "Confirmation needed", confirm_hint = "Read the text in the action bar and right-click the bank terminal. Esc cancels.",
    confirm_timeout = "Not confirmed (time ran out)",
    detail_number = "Number", detail_amount = "Amount", detail_note = "Note", detail_status = "Status", detail_item = "Good",
    detail_price = "Price", detail_fee = "Fee", detail_slot = "Slot", detail_id = "ID",
    lender = "Lender", borrower = "Borrower", repay_amount = "Repayment", you = "you",
    confirm_wait = "Confirm: right-click the terminal (%ds)",
    title_bank = "BANK", title_auction = "AUCTIONS", not_logged = "not logged in",
    tab_market = "Market", tab_mine = "Mine", tab_sell = "Sell", tab_pickup = "Pickup",
    search = "Search", ask_search = "Search:", prev = "<", next = ">", buy = "Buy", sell = "List", ask_price = "Price:",
    page = "Page %d/%d", fee = "Fee %d%%", pickup_head = "Proceeds: %s", claim = "Claim",
    claimed = "Proceeds %s, items %d, waiting %d", container_empty = "Container is empty. Put items into the terminal.",
    seller = "by", coins_skip = "Coins cannot be sold",
    tab_log = "Statement", tab_orders = "Orders", tab_watch = "Watch list", menu_orders = "Standing order",
    log_in = "Incoming", log_out = "Outgoing", log_empty = "No entries yet. The statement records every balance change this app sees.",
    log_clear = "Clear", filter_all = "All", filter_in = "Incoming", filter_out = "Outgoing", detail_time = "Time", delete = "Delete",
    so_new = "New", so_title = "Standing order", ask_every = "Every ... days:", so_every = "every %d days", so_next = "next D%d", so_next_label = "Next", so_interval = "Interval",
    so_due_short = "due", so_due = "Standing order due: %s to %s (Bank, Orders tab)", so_run = "Transfer now",
    so_empty = "No standing orders. Tap New: player, amount, every how many days. Due orders notify you, transfer with Transfer now and a right-click on the terminal.",
    watch = "Watch", unwatch = "Forget", watch_hint = "Watched offers notify you when they are sold or withdrawn.",
    watch_empty = "Nothing watched. Pick an offer in the market and tap Watch.",
    watch_gone = "Offer #%s (%s) is gone: sold or withdrawn", undercut = "Undercut: %s offers %s for %s",
}

local tables = { de = de, en = en }

local M = {}

function M.T(key, ...)
    local s = (tables[lang.code] or de)[key] or de[key] or key
    if select("#", ...) > 0 then
        local ok, r = pcall(string.format, s, ...)
        if ok then return r end
    end
    return s
end

function M.money(n)
    n = math.floor(tonumber(n) or 0)
    local s = tostring(math.abs(n))
    local out = s:reverse():gsub("(%d%d%d)", "%1."):reverse()
    if out:sub(1, 1) == "." then out = out:sub(2) end
    return (n < 0 and "-" or "") .. out
end

return M
