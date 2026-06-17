package nl.live.tafelbord;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.UUID;

public class CashMainActivity extends WeatherMainActivity {
    static final String K_CASH_ENTRIES = "cash_entries";
    static final String K_CASH_ADMIN = "cash_admin";
    String cashDate = todayCash();
    String cashPeriod = "Dag";

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
    }

    @Override void render() {
        root = col();
        root.setBackgroundColor(BG);
        root.setPadding(dp(14), dp(14), dp(14), 0);
        root.addView(header());
        if ("map".equals(screen)) root.addView(zoneTabs());
        content = col();
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        if (!"staff".equals(screen) && !"email".equals(screen) && !"tasks".equals(screen)
                && !"weather".equals(screen) && !"cash".equals(screen)) root.addView(addReservationBar(), full());
        root.addView(nav());
        if ("cash".equals(screen)) cashScreen();
        else if ("weather".equals(screen)) weatherScreen();
        else if ("tasks".equals(screen)) tasksScreen();
        else if ("email".equals(screen)) emailScreen();
        else if ("res".equals(screen)) resScreen();
        else if ("planner".equals(screen)) planner();
        else if ("dash".equals(screen)) dash();
        else if ("phone".equals(screen)) phone();
        else if ("staff".equals(screen)) staff();
        else if ("more".equals(screen)) more();
        else map();
        setContentView(root);
    }

    @Override void more() {
        LinearLayout p = panel();
        p.addView(txt("Meer", 25, INK, true));
        Button cash = btn("Kassa / Dagafsluiting", BLACK, Color.WHITE);
        cash.setOnClickListener(v -> { screen = "cash"; cashDate = todayCash(); render(); });
        Button weather = btn("Terrasweer", weatherColor(), Color.WHITE);
        weather.setOnClickListener(v -> { screen = "weather"; render(); fetchWeatherAsync(false); });
        Button tasksBtn = btn("Taken", ORANGE, Color.WHITE);
        tasksBtn.setOnClickListener(v -> { screen = "tasks"; taskTab = "Opening"; render(); });
        Button mailBtn = btn("Mail Sync", GREEN, Color.WHITE);
        mailBtn.setOnClickListener(v -> { screen = "email"; render(); });
        Button phoneBtn = btn("Telefoonmodus", BLUE, Color.WHITE);
        phoneBtn.setOnClickListener(v -> { screen = "phone"; render(); });
        Button staffBtn = btn("Personeel vandaag", BLUE, Color.WHITE);
        staffBtn.setOnClickListener(v -> { screen = "staff"; render(); });
        Button mode = btn(edit ? "Service modus aanzetten" : "Bewerk modus aanzetten", edit ? ORANGE : BLACK, Color.WHITE);
        mode.setOnClickListener(v -> { edit = !edit; selected.clear(); render(); });
        p.addView(cash, full());
        p.addView(weather, full());
        p.addView(tasksBtn, full());
        p.addView(mailBtn, full());
        p.addView(phoneBtn, full());
        p.addView(staffBtn, full());
        p.addView(mode, full());
        content.addView(p, full());
    }

    @Override void dash() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = col();
        scroll.addView(list);
        LinearLayout p = panel();
        p.addView(txt("Dashboard", 25, INK, true));
        p.addView(metric("Vrije plaatsen", freeSeats(), GREEN));
        p.addView(metric("Vrije tafels", freeTables(), GREEN));
        p.addView(metric("Aanwezige gasten", statusGuests("Aanwezig"), BLUE));
        p.addView(metric("Aankomst komende 30 minuten", arrivalNext30(), RED));
        p.addView(metric("Controle nodig", controlNeededCount(), ORANGE));
        p.addView(riskView(dayAdvice("19:00", 0, "Alles", "")));
        list.addView(p, full());
        list.addView(cashTile(), full());
        list.addView(weatherTile(), full());
        list.addView(wixMailTile(), full());
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    void cashScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = col();
        scroll.addView(list);
        list.addView(cashTile(), full());
        list.addView(periodTabs(), full());
        list.addView(cashForm(), full());
        list.addView(cashOverview(), full());
        list.addView(cashHistory(), full());
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    LinearLayout cashTile() {
        CashEntry today = entryFor(todayCash());
        CashCalc calc = calculate(today);
        LinearLayout p = panel();
        p.setBackground(bg(Color.WHITE, calc.color, 1, 18));
        p.addView(txt("Kassa / Dagafsluiting", 22, INK, true));
        p.addView(txt("Vandaag: " + todayCash(), 15, MUTED, false));
        p.addView(metric("Omzet", Math.round(today.revenue), BLACK));
        p.addView(metric("Pin", Math.round(today.pin), BLUE));
        p.addView(metric("Fooi", Math.round(today.tip), GREEN));
        p.addView(metric("Kasverschil", Math.round(calc.cashDifference), calc.color));
        p.addView(txt(calc.message, 16, calc.color, true));
        Button open = btn("Open Kassa", BLACK, Color.WHITE);
        open.setOnClickListener(v -> { screen = "cash"; cashDate = todayCash(); render(); });
        p.addView(open, full());
        return p;
    }

    LinearLayout periodTabs() {
        LinearLayout tabs = row();
        tabs.setBackground(bg(Color.WHITE, LINE, 1, 18));
        tabs.addView(periodButton("Dag"), w());
        tabs.addView(periodButton("Week"), w());
        tabs.addView(periodButton("Maand"), w());
        tabs.addView(periodButton("Jaar"), w());
        return tabs;
    }

    Button periodButton(String label) {
        boolean on = cashPeriod.equals(label);
        Button b = btn(label, on ? BLACK : Color.TRANSPARENT, on ? Color.WHITE : INK);
        b.setMinHeight(dp(56));
        b.setOnClickListener(v -> { cashPeriod = label; render(); });
        return b;
    }

    LinearLayout cashForm() {
        CashEntry entry = entryFor(cashDate);
        CashCalc calc = calculate(entry);
        LinearLayout p = panel();
        p.addView(txt("Dagelijkse kassa-invoer", 24, INK, true));
        p.addView(txt(isAdmin() ? "Beheermodus: oude dagen aanpassen toegestaan" : "Medewerker: alleen vandaag aanpassen", 14, isAdmin() ? GREEN : MUTED, true));
        EditText date = field("Datum", entry.date.isEmpty() ? todayCash() : entry.date);
        EditText revenue = moneyField("Omzet totaal", entry.revenue);
        EditText pin = moneyField("Dagtotaal pin", entry.pin);
        EditText drawer = moneyField("Cash in kassa", entry.cashInDrawer);
        EditText start = moneyField("Beginsaldo kassa", entry.startBalance <= 0 ? 250f : entry.startBalance);
        EditText tip = moneyField("Fooi", entry.tip);
        EditText safe = moneyField("Bedrag naar kluis", entry.safeDrop);
        EditText correction = moneyField("Correcties / kasverschil", entry.correction);
        EditText notes = field("Notities", entry.notes);
        p.addView(date);
        p.addView(revenue);
        p.addView(pin);
        p.addView(drawer);
        p.addView(start);
        p.addView(tip);
        p.addView(safe);
        p.addView(correction);
        p.addView(notes);
        p.addView(txt("Controle: omzet - pin = verwacht cashbedrag", 16, INK, true));
        p.addView(txt("Verwacht cash: " + money(calc.expectedCash), 16, BLUE, true));
        p.addView(txt("Advies naar kluis: " + money(calc.suggestedSafeDrop), 16, ORANGE, true));
        p.addView(txt("Eindsaldo na afstorten: " + money(calc.endBalance), 16, calc.endBalanceOk ? GREEN : RED, true));
        p.addView(txt(calc.message, 17, calc.color, true));
        LinearLayout actions = row();
        Button save = btn("Opslaan", GREEN, Color.WHITE);
        save.setOnClickListener(v -> saveCashFromFields(date, revenue, pin, drawer, start, tip, safe, correction, notes));
        Button admin = btn(isAdmin() ? "Beheer uit" : "Beheer aan", isAdmin() ? ORANGE : BLUE, Color.WHITE);
        admin.setOnClickListener(v -> { prefs.edit().putBoolean(K_CASH_ADMIN, !isAdmin()).apply(); render(); });
        actions.addView(save, w());
        actions.addView(admin, w());
        p.addView(actions);
        p.addView(txt("Export naar Excel/PDF/boekhouder wordt voorbereid op basis van deze opgeslagen dagdata.", 14, MUTED, false));
        return p;
    }

    EditText moneyField(String hint, float value) {
        EditText e = field(hint, value == 0 ? "" : euroInput(value));
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        return e;
    }

    void saveCashFromFields(EditText date, EditText revenue, EditText pin, EditText drawer, EditText start,
                            EditText tip, EditText safe, EditText correction, EditText notes) {
        String d = val(date, todayCash());
        if (!isAdmin() && !todayCash().equals(d)) {
            Toast.makeText(this, "Alleen beheerder mag oude kassagegevens aanpassen", Toast.LENGTH_LONG).show();
            return;
        }
        CashEntry e = entryFor(d);
        e.date = d;
        e.revenue = moneyValue(revenue.getText().toString());
        e.pin = moneyValue(pin.getText().toString());
        e.cashInDrawer = moneyValue(drawer.getText().toString());
        e.startBalance = moneyValue(start.getText().toString());
        if (e.startBalance <= 0) e.startBalance = 250f;
        e.tip = moneyValue(tip.getText().toString());
        e.safeDrop = moneyValue(safe.getText().toString());
        e.correction = moneyValue(correction.getText().toString());
        e.notes = val(notes, "");
        e.updatedAt = LocalDate.now().toString();
        saveEntry(e);
        cashDate = d;
        Toast.makeText(this, "Dagafsluiting opgeslagen", Toast.LENGTH_SHORT).show();
        render();
    }

    LinearLayout cashOverview() {
        CashSummary s = summaryFor(cashPeriod);
        LinearLayout p = panel();
        p.addView(txt("Overzicht " + cashPeriod.toLowerCase(Locale.ROOT), 24, INK, true));
        p.addView(metricMoney("Totale omzet", s.revenue, BLACK));
        p.addView(metricMoney("Totale pinomzet", s.pin, BLUE));
        p.addView(metricMoney("Totale cashomzet", s.cashRevenue, GREEN));
        p.addView(metricMoney("Totale fooi", s.tip, GREEN));
        p.addView(metricMoney("Totaal naar kluis", s.safeDrop, ORANGE));
        p.addView(metricMoney("Kasverschillen", s.cashDifference, Math.abs(s.cashDifference) < 1 ? GREEN : RED));
        p.addView(metricMoney("Gemiddelde omzet per dag", s.days == 0 ? 0 : s.revenue / s.days, BLUE));
        p.addView(txt("Deze omzetdata is klaar voor latere koppeling met personeelskosten, inkoopkosten en dagresultaat.", 14, MUTED, false));
        return p;
    }

    LinearLayout cashHistory() {
        LinearLayout p = panel();
        p.addView(txt("Terugzoeken", 22, INK, true));
        ArrayList<CashEntry> entries = entries();
        Collections.sort(entries, (a, b) -> b.date.compareTo(a.date));
        int shown = 0;
        for (CashEntry e : entries) {
            CashCalc c = calculate(e);
            p.addView(txt(e.date + " - omzet " + money(e.revenue) + " - pin " + money(e.pin) + " - kluis " + money(e.safeDrop), 15, INK, true));
            p.addView(txt(c.message, 14, c.color, true));
            shown++;
            if (shown >= 30) break;
        }
        if (shown == 0) p.addView(txt("Nog geen dagafsluitingen opgeslagen.", 15, MUTED, false));
        return p;
    }

    LinearLayout metricMoney(String label, float value, int color) {
        LinearLayout r = row();
        r.addView(txt(label, 16, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(txt(money(value), 22, color, true));
        return r;
    }

    CashCalc calculate(CashEntry e) {
        CashCalc c = new CashCalc();
        float start = e.startBalance <= 0 ? 250f : e.startBalance;
        c.expectedCash = e.revenue - e.pin;
        c.actualCashRevenue = e.cashInDrawer - start - e.tip - e.correction;
        c.cashDifference = c.actualCashRevenue - c.expectedCash;
        c.expectedDrawerBeforeDrop = start + c.expectedCash + e.tip + e.correction;
        c.suggestedSafeDrop = Math.max(0, e.cashInDrawer - start);
        c.endBalance = e.cashInDrawer - e.safeDrop;
        c.endBalanceOk = Math.abs(c.endBalance - start) < 1.0f;
        if (Math.abs(c.cashDifference) < 1.0f && c.endBalanceOk) {
            c.color = GREEN;
            c.message = "Klopt: omzet - pin sluit aan en beginsaldo blijft behouden.";
        } else if (Math.abs(c.cashDifference) < 5.0f || c.endBalanceOk) {
            c.color = ORANGE;
            c.message = "Controleer: klein verschil of afstorting/beginsaldo nakijken.";
        } else {
            c.color = RED;
            c.message = "Controleer: kasverschil of beginsaldo klopt niet.";
        }
        return c;
    }

    CashSummary summaryFor(String period) {
        CashSummary s = new CashSummary();
        for (CashEntry e : entries()) {
            if (!inPeriod(e.date, period)) continue;
            CashCalc c = calculate(e);
            s.days++;
            s.revenue += e.revenue;
            s.pin += e.pin;
            s.cashRevenue += c.expectedCash;
            s.tip += e.tip;
            s.safeDrop += e.safeDrop;
            s.cashDifference += c.cashDifference;
        }
        return s;
    }

    boolean inPeriod(String date, String period) {
        try {
            LocalDate d = LocalDate.parse(date);
            LocalDate ref = LocalDate.parse(cashDate == null || cashDate.isEmpty() ? todayCash() : cashDate);
            if ("Dag".equals(period)) return d.equals(ref);
            if ("Week".equals(period)) {
                LocalDate start = ref.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate end = ref.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
                return !d.isBefore(start) && !d.isAfter(end);
            }
            if ("Maand".equals(period)) return d.getYear() == ref.getYear() && d.getMonthValue() == ref.getMonthValue();
            if ("Jaar".equals(period)) return d.getYear() == ref.getYear();
        } catch (Exception ignored) {
        }
        return false;
    }

    CashEntry entryFor(String date) {
        for (CashEntry e : entries()) if (e.date.equals(date)) return e;
        CashEntry e = new CashEntry();
        e.date = date;
        e.startBalance = 250f;
        return e;
    }

    void saveEntry(CashEntry entry) {
        ArrayList<CashEntry> list = entries();
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).date.equals(entry.date)) list.remove(i);
        list.add(entry);
        JSONArray arr = new JSONArray();
        for (CashEntry e : list) arr.put(e.json());
        prefs.edit().putString(K_CASH_ENTRIES, arr.toString()).apply();
    }

    ArrayList<CashEntry> entries() {
        ArrayList<CashEntry> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(K_CASH_ENTRIES, "[]"));
            for (int i = 0; i < arr.length(); i++) list.add(CashEntry.from(arr.getJSONObject(i)));
        } catch (Exception ignored) {
        }
        return list;
    }

    boolean isAdmin() {
        return prefs.getBoolean(K_CASH_ADMIN, false);
    }

    String todayCash() {
        return LocalDate.now().toString();
    }

    String money(float value) {
        return String.format(Locale.forLanguageTag("nl-NL"), "€ %.2f", value);
    }

    String euroInput(float value) {
        return String.format(Locale.forLanguageTag("nl-NL"), "%.2f", value);
    }

    float moneyValue(String raw) {
        try {
            String s = raw == null ? "" : raw.trim().replace("€", "").replace(" ", "");
            if (s.contains(",") && s.lastIndexOf(',') > s.lastIndexOf('.')) s = s.replace(".", "").replace(',', '.');
            return s.isEmpty() ? 0f : Float.parseFloat(s);
        } catch (Exception ignored) {
            return 0f;
        }
    }

    static class CashEntry {
        String id = UUID.randomUUID().toString();
        String date = "";
        float revenue;
        float pin;
        float cashInDrawer;
        float startBalance = 250f;
        float tip;
        float safeDrop;
        float correction;
        String notes = "";
        String updatedAt = "";

        JSONObject json() {
            JSONObject j = new JSONObject();
            try {
                j.put("id", id); j.put("date", date); j.put("revenue", revenue); j.put("pin", pin);
                j.put("cashInDrawer", cashInDrawer); j.put("startBalance", startBalance); j.put("tip", tip);
                j.put("safeDrop", safeDrop); j.put("correction", correction); j.put("notes", notes); j.put("updatedAt", updatedAt);
            } catch (Exception ignored) {}
            return j;
        }

        static CashEntry from(JSONObject j) {
            CashEntry e = new CashEntry();
            e.id = j.optString("id", UUID.randomUUID().toString());
            e.date = j.optString("date", "");
            e.revenue = (float) j.optDouble("revenue", 0);
            e.pin = (float) j.optDouble("pin", 0);
            e.cashInDrawer = (float) j.optDouble("cashInDrawer", 0);
            e.startBalance = (float) j.optDouble("startBalance", 250);
            e.tip = (float) j.optDouble("tip", 0);
            e.safeDrop = (float) j.optDouble("safeDrop", 0);
            e.correction = (float) j.optDouble("correction", 0);
            e.notes = j.optString("notes", "");
            e.updatedAt = j.optString("updatedAt", "");
            return e;
        }
    }

    static class CashCalc {
        float expectedCash;
        float actualCashRevenue;
        float expectedDrawerBeforeDrop;
        float suggestedSafeDrop;
        float endBalance;
        float cashDifference;
        boolean endBalanceOk;
        int color = ORANGE;
        String message = "Vul de dagafsluiting in.";
    }

    static class CashSummary {
        int days;
        float revenue;
        float pin;
        float cashRevenue;
        float tip;
        float safeDrop;
        float cashDifference;
    }
}
