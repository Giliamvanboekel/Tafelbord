package nl.live.tafelbord;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;

public class EmailMainActivity extends MainActivity {
    static final String K_EMAIL_CONNECTED = "email_connected";
    static final String K_EMAIL_ACCOUNT = "email_account";
    static final String K_EMAIL_LAST_SYNC = "email_last_sync";
    static final String K_EMAIL_PROCESSED = "email_processed_ids";
    static final String K_EMAIL_LOG = "email_log";
    static final String K_EMAIL_CREATED = "email_created";
    static final String K_EMAIL_CANCELLED = "email_cancelled";
    static final String K_EMAIL_REVIEW = "email_review";

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
        if (!"staff".equals(screen) && !"email".equals(screen)) root.addView(addReservationBar(), full());
        root.addView(nav());
        if ("email".equals(screen)) emailScreen();
        else if ("res".equals(screen)) resScreen();
        else if ("planner".equals(screen)) planner();
        else if ("dash".equals(screen)) dash();
        else if ("phone".equals(screen)) phone();
        else if ("staff".equals(screen)) staff();
        else if ("more".equals(screen)) more();
        else map();
        setContentView(root);
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
        p.addView(metric("Bijna klaar gasten", statusGuests("Bijna klaar"), ORANGE));
        p.addView(metric("Aankomst komende 30 minuten", arrivalNext30(), RED));
        p.addView(metric("Reserveringen vandaag", reservations.size(), BLACK));
        p.addView(metric("Zonder tafel", unassigned(), ORANGE));
        p.addView(metric("Speciale wensen", specialCount(), RED));
        p.addView(riskView(dayAdvice("19:00", 0, "Alles", "")));
        list.addView(p, full());
        list.addView(wixMailTile(), full());
        list.addView(info("Aanname-advies: " + adviceLine("19:00", 4, "Alles", ""),
                riskColor(dayAdvice("19:00", 4, "Alles", "").level)), full());
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    @Override void more() {
        LinearLayout p = panel();
        p.addView(txt("Meer", 25, INK, true));
        Button phoneBtn = btn("Telefoonmodus", BLACK, Color.WHITE);
        phoneBtn.setOnClickListener(v -> { screen = "phone"; render(); });
        Button staffBtn = btn("Personeel vandaag", BLUE, Color.WHITE);
        staffBtn.setOnClickListener(v -> { screen = "staff"; render(); });
        Button emailBtn = btn("E-mailkoppeling", GREEN, Color.WHITE);
        emailBtn.setOnClickListener(v -> { screen = "email"; render(); });
        Button mode = btn(edit ? "Service modus aanzetten" : "Bewerk modus aanzetten",
                edit ? ORANGE : BLACK, Color.WHITE);
        mode.setOnClickListener(v -> { edit = !edit; selected.clear(); render(); });
        Button reset = btn("Reset plattegrond", RED, Color.WHITE);
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Reset plattegrond")
                .setMessage("Alle tafels terug naar standaard?")
                .setNegativeButton("Annuleren", null)
                .setPositiveButton("Reset", (dialog, which) -> { seed(); saveTables(); render(); })
                .show());
        p.addView(phoneBtn, full());
        p.addView(staffBtn, full());
        p.addView(emailBtn, full());
        p.addView(mode, full());
        p.addView(reset, full());
        content.addView(p, full());
    }

    void emailScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = col();
        scroll.addView(list);

        LinearLayout p = panel();
        p.addView(txt("E-mailkoppeling", 25, INK, true));
        p.addView(txt("Wix-reserveringsmails automatisch verwerken", 15, MUTED, false));
        p.addView(metric("Mailaccount", connected() ? 1 : 0, connected() ? GREEN : ORANGE));
        p.addView(txt(connected() ? account() : "Nog geen account gekoppeld", 16, connected() ? GREEN : ORANGE, true));
        p.addView(txt("Laatste sync: " + prefs.getString(K_EMAIL_LAST_SYNC, "nog niet"), 15, MUTED, false));
        p.addView(metric("Wix-mails verwerkt", processedIds().size(), BLUE));
        p.addView(metric("Reserveringen aangemaakt", prefs.getInt(K_EMAIL_CREATED, 0), GREEN));
        p.addView(metric("Annuleringen verwerkt", prefs.getInt(K_EMAIL_CANCELLED, 0), RED));
        p.addView(metric("Controle nodig", prefs.getInt(K_EMAIL_REVIEW, 0), ORANGE));
        list.addView(p, full());

        LinearLayout buttons = panel();
        Button connect = btn(connected() ? "Mailaccount wijzigen" : "Mailaccount koppelen", BLACK, Color.WHITE);
        connect.setOnClickListener(v -> connectEmailAccount());
        Button sync = btn("Mail synchroniseren", GREEN, Color.WHITE);
        sync.setOnClickListener(v -> syncWixEmails());
        Button disconnect = btn("Mailaccount ontkoppelen", RED, Color.WHITE);
        disconnect.setOnClickListener(v -> disconnectEmailAccount());
        buttons.addView(connect, full());
        buttons.addView(sync, full());
        buttons.addView(disconnect, full());
        buttons.addView(txt("Er worden geen e-mailwachtwoorden opgeslagen. De module is voorbereid op Gmail/Google Workspace OAuth met alleen-lezen rechten.", 14, MUTED, false));
        list.addView(buttons, full());

        LinearLayout log = panel();
        log.addView(txt("E-mailverwerking", 22, INK, true));
        ArrayList<String> items = emailLog();
        if (items.isEmpty()) log.addView(txt("Nog geen verwerking uitgevoerd.", 15, MUTED, false));
        for (String item : items) log.addView(txt(item, 15, item.contains("Controle") ? ORANGE : INK, true));
        list.addView(log, full());
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    LinearLayout wixMailTile() {
        LinearLayout p = panel();
        p.setBackground(bg(Color.WHITE, connected() ? GREEN : ORANGE, 1, 18));
        p.addView(txt("Wix Mail Sync", 22, INK, true));
        p.addView(txt("Laatste sync: " + prefs.getString(K_EMAIL_LAST_SYNC, "nog niet"), 15, MUTED, false));
        p.addView(metric("Nieuwe reserveringen", prefs.getInt(K_EMAIL_CREATED, 0), GREEN));
        p.addView(metric("Annuleringen", prefs.getInt(K_EMAIL_CANCELLED, 0), RED));
        p.addView(metric("Controle nodig", prefs.getInt(K_EMAIL_REVIEW, 0), ORANGE));
        Button sync = btn("Mail synchroniseren", GREEN, Color.WHITE);
        sync.setOnClickListener(v -> syncWixEmails());
        p.addView(sync, full());
        return p;
    }

    void connectEmailAccount() {
        LinearLayout box = col();
        box.setPadding(dp(8), 0, dp(8), 0);
        EditText email = field("E-mailadres", account().isEmpty() ? "info@vanboekelproperties.com" : account());
        email.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        box.addView(txt("OAuth-login", 18, INK, true));
        box.addView(txt("Vul het mailadres in. In de definitieve Gmail-koppeling opent hier het Google-toestemmingsscherm; wachtwoorden worden nooit opgeslagen.", 15, MUTED, false));
        box.addView(email);
        new AlertDialog.Builder(this)
                .setTitle("Mailaccount koppelen")
                .setView(box)
                .setNegativeButton("Annuleren", null)
                .setPositiveButton("Koppelen", (dialog, which) -> {
                    prefs.edit()
                            .putBoolean(K_EMAIL_CONNECTED, true)
                            .putString(K_EMAIL_ACCOUNT, val(email, "info@vanboekelproperties.com"))
                            .apply();
                    addEmailLog("Mailaccount gekoppeld: " + val(email, "info@vanboekelproperties.com"));
                    toast("Mailaccount gekoppeld");
                    render();
                })
                .show();
    }

    void disconnectEmailAccount() {
        new AlertDialog.Builder(this)
                .setTitle("Mailaccount ontkoppelen")
                .setMessage("Toestemming intrekken en lokale koppelingsgegevens wissen?")
                .setNegativeButton("Annuleren", null)
                .setPositiveButton("Ontkoppel", (dialog, which) -> {
                    prefs.edit()
                            .putBoolean(K_EMAIL_CONNECTED, false)
                            .remove(K_EMAIL_ACCOUNT)
                            .apply();
                    addEmailLog("Mailaccount ontkoppeld");
                    render();
                })
                .show();
    }

    void syncWixEmails() {
        if (!connected()) {
            connectEmailAccount();
            return;
        }
        int createdBefore = prefs.getInt(K_EMAIL_CREATED, 0);
        int cancelledBefore = prefs.getInt(K_EMAIL_CANCELLED, 0);
        int reviewBefore = prefs.getInt(K_EMAIL_REVIEW, 0);
        int processed = 0;
        for (WixEmail email : loadMailboxMessages()) {
            if (processedIds().contains(email.messageId)) continue;
            WixReservation parsed = parseWixReservationEmail(email);
            if (parsed == null) {
                addEmailLog("Controle nodig: Wix-mail kon niet gelezen worden " + email.messageId);
                increase(K_EMAIL_REVIEW);
                markEmailAsProcessed(email.messageId);
                continue;
            }
            if ("geannuleerd".equals(parsed.status)) cancelReservationFromEmail(parsed);
            else if ("gewijzigd".equals(parsed.status)) updateReservationFromEmail(parsed);
            else createReservationFromEmail(parsed);
            markEmailAsProcessed(email.messageId);
            processed++;
        }
        prefs.edit().putString(K_EMAIL_LAST_SYNC, LocalDateTime.now().toString().replace('T', ' ')).apply();
        int made = prefs.getInt(K_EMAIL_CREATED, 0) - createdBefore;
        int cancels = prefs.getInt(K_EMAIL_CANCELLED, 0) - cancelledBefore;
        int review = prefs.getInt(K_EMAIL_REVIEW, 0) - reviewBefore;
        addEmailLog("Sync klaar: " + processed + " mail(s), " + made + " nieuw, " + cancels + " annulering(en), " + review + " controle nodig");
        Toast.makeText(this, "Wix mail sync klaar", Toast.LENGTH_LONG).show();
        render();
    }

    WixReservation parseWixReservationEmail(WixEmail email) {
        String body = email.body;
        String lower = (email.subject + "\n" + body).toLowerCase(Locale.ROOT);
        WixReservation r = new WixReservation();
        r.messageId = email.messageId;
        r.source = "Wix";
        r.status = lower.contains("geannuleerd") || lower.contains("annulering") ? "geannuleerd"
                : lower.contains("gewijzigd") || lower.contains("wijziging") ? "gewijzigd" : "nieuw";
        r.reservationNumber = first(body, "Reserveringsnummer", "Reservatie", "Boekingsnummer");
        r.name = first(body, "Naam", "Gast", "Klant");
        r.date = first(body, "Datum", "Dag");
        r.time = norm(first(body, "Tijd", "Aanvang"));
        r.party = Math.max(1, parseInt(first(body, "Personen", "Aantal personen", "Gasten"), 2));
        r.phone = first(body, "Telefoon", "Telefoonnummer", "Mobiel");
        r.email = first(body, "E-mail", "Email", "Mail");
        r.zone = normalizeZone(first(body, "Zone", "Voorkeur", "Plaats"));
        r.notes = first(body, "Opmerkingen", "Notitie", "Speciale wensen", "Wensen");
        if (r.name.isEmpty() || r.time.isEmpty()) return null;
        return r;
    }

    void createReservationFromEmail(WixReservation mail) {
        Res r = new Res();
        fillReservation(r, mail);
        Candidate c = assignBestAvailableTable(r);
        if (c != null) {
            r.tables.addAll(c.ids);
            markTables(r, "Gereserveerd");
            r.status = "Gereserveerd";
            addEmailLog("Nieuwe Wix-reservering toegevoegd: " + r.name + ", " + r.party + " personen, " + r.time + ", " + c.label);
            toast("Nieuwe Wix-reservering toegevoegd: " + r.name + ", " + r.party + " personen, " + r.time + ", " + c.label + ".");
        } else {
            r.status = "controle nodig";
            r.notes = appendNote(r.notes, "Controle nodig: geen zekere tafel beschikbaar");
            addEmailLog("Controle nodig: nieuwe Wix-reservering zonder tafel voor " + r.name + " om " + r.time);
            increase(K_EMAIL_REVIEW);
        }
        finishSave(r, true, false);
        increase(K_EMAIL_CREATED);
    }

    void updateReservationFromEmail(WixReservation mail) {
        Res r = findExisting(mail);
        if (r == null) {
            createReservationFromEmail(mail);
            addEmailLog("Wijziging werd als nieuwe reservering verwerkt: " + mail.name);
            return;
        }
        release(r);
        r.tables.clear();
        fillReservation(r, mail);
        Candidate c = assignBestAvailableTable(r);
        if (c != null) {
            r.tables.addAll(c.ids);
            markTables(r, "Gereserveerd");
            r.status = "Gereserveerd";
            addEmailLog("Wix-wijziging verwerkt: " + r.name + " naar " + c.label);
        } else {
            r.status = "controle nodig";
            r.notes = appendNote(r.notes, "Controle nodig: tafel opnieuw kiezen na wijziging");
            addEmailLog("Controle nodig: Wix-wijziging past niet zeker op een tafel voor " + r.name);
            increase(K_EMAIL_REVIEW);
        }
        finishSave(r, false, false);
    }

    void cancelReservationFromEmail(WixReservation mail) {
        Res r = findExisting(mail);
        if (r == null) {
            addEmailLog("Controle nodig: annulering niet gevonden voor " + mail.name + " om " + mail.time);
            increase(K_EMAIL_REVIEW);
            return;
        }
        release(r);
        r.tables.clear();
        r.status = "geannuleerd";
        r.notes = appendNote(r.notes, "Geannuleerd via Wix-mail");
        finishSave(r, false, false);
        increase(K_EMAIL_CANCELLED);
        addEmailLog("Wix-annulering verwerkt: " + r.name + " om " + r.time);
    }

    Candidate assignBestAvailableTable(Res r) {
        return suggest(r, null);
    }

    void markEmailAsProcessed(String messageId) {
        HashSet<String> ids = processedIds();
        ids.add(messageId);
        JSONArray arr = new JSONArray();
        for (String id : ids) arr.put(id);
        prefs.edit().putString(K_EMAIL_PROCESSED, arr.toString()).apply();
    }

    ArrayList<WixEmail> loadMailboxMessages() {
        return sampleWixEmails();
    }

    ArrayList<WixEmail> sampleWixEmails() {
        ArrayList<WixEmail> mails = new ArrayList<>();
        mails.add(new WixEmail("wix-demo-1001", "Nieuwe reservering via Wix", "" +
                "Reserveringsnummer: WX-1001\n" +
                "Naam: Jansen\n" +
                "Datum: vandaag\n" +
                "Tijd: 19:00\n" +
                "Personen: 4\n" +
                "Telefoon: 0612345678\n" +
                "E-mail: jansen@example.com\n" +
                "Voorkeur: Binnen\n" +
                "Opmerkingen: verjaardag en glutenvrij"));
        mails.add(new WixEmail("wix-demo-1002", "Gewijzigde reservering via Wix", "" +
                "Reserveringsnummer: WX-1001\n" +
                "Naam: Jansen\n" +
                "Datum: vandaag\n" +
                "Tijd: 19:30\n" +
                "Personen: 4\n" +
                "Telefoon: 0612345678\n" +
                "E-mail: jansen@example.com\n" +
                "Voorkeur: Binnen\n" +
                "Opmerkingen: verjaardag, glutenvrij, rustig plekje"));
        mails.add(new WixEmail("wix-demo-1003", "Annulering reservering via Wix", "" +
                "Reserveringsnummer: WX-2002\n" +
                "Naam: Pieters\n" +
                "Datum: vandaag\n" +
                "Tijd: 18:30\n" +
                "Personen: 2\n" +
                "E-mail: pieters@example.com\n" +
                "Opmerkingen: geannuleerd door gast"));
        return mails;
    }

    void fillReservation(Res r, WixReservation mail) {
        r.name = mail.name;
        r.time = mail.time;
        r.party = mail.party;
        r.zone = mail.zone;
        r.phone = mail.phone;
        r.email = mail.email;
        r.duration = 120;
        r.notes = appendNote(mail.notes, "Bron: Wix" +
                (mail.reservationNumber.isEmpty() ? "" : " | Wix nummer: " + mail.reservationNumber) +
                (mail.date.isEmpty() ? "" : " | Datum: " + mail.date));
    }

    Res findExisting(WixReservation mail) {
        for (Res r : reservations) {
            if (!mail.reservationNumber.isEmpty() && r.notes.contains("Wix nummer: " + mail.reservationNumber)) return r;
        }
        for (Res r : reservations) {
            boolean sameName = r.name.equalsIgnoreCase(mail.name);
            boolean sameTime = r.time.equals(mail.time);
            if (sameName && sameTime) return r;
        }
        return null;
    }

    boolean connected() {
        return prefs.getBoolean(K_EMAIL_CONNECTED, false);
    }

    String account() {
        return prefs.getString(K_EMAIL_ACCOUNT, "");
    }

    HashSet<String> processedIds() {
        HashSet<String> ids = new HashSet<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(K_EMAIL_PROCESSED, "[]"));
            for (int i = 0; i < arr.length(); i++) ids.add(arr.optString(i));
        } catch (Exception ignored) {
        }
        return ids;
    }

    ArrayList<String> emailLog() {
        ArrayList<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(K_EMAIL_LOG, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.optString(i));
        } catch (Exception ignored) {
        }
        return out;
    }

    void addEmailLog(String message) {
        ArrayList<String> items = emailLog();
        items.add(0, LocalDateTime.now().toString().replace('T', ' ') + " - " + message);
        while (items.size() > 20) items.remove(items.size() - 1);
        JSONArray arr = new JSONArray();
        for (String item : items) arr.put(item);
        prefs.edit().putString(K_EMAIL_LOG, arr.toString()).apply();
    }

    void increase(String key) {
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply();
    }

    String first(String body, String... labels) {
        String[] lines = body.split("\\n");
        for (String label : labels) {
            String lowLabel = label.toLowerCase(Locale.ROOT);
            for (String line : lines) {
                String clean = line.trim();
                String low = clean.toLowerCase(Locale.ROOT);
                if (low.startsWith(lowLabel.toLowerCase(Locale.ROOT) + ":")) {
                    return clean.substring(clean.indexOf(':') + 1).trim();
                }
            }
        }
        return "";
    }

    String normalizeZone(String value) {
        String v = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (v.contains("serre")) return "Serre";
        if (v.contains("tuin") || v.contains("terras") || v.contains("buiten")) return "Tuinterras";
        if (v.contains("binnen")) return "Binnen";
        return "Alles";
    }

    int parseInt(String raw, int fallback) {
        try {
            String digits = raw.replaceAll("[^0-9]", "");
            return digits.isEmpty() ? fallback : Integer.parseInt(digits);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    String appendNote(String note, String extra) {
        if (extra == null || extra.trim().isEmpty()) return note == null ? "" : note;
        if (note == null || note.trim().isEmpty()) return extra;
        return note + " | " + extra;
    }

    static class WixEmail {
        String messageId;
        String subject;
        String body;
        WixEmail(String messageId, String subject, String body) {
            this.messageId = messageId;
            this.subject = subject;
            this.body = body;
        }
    }

    static class WixReservation {
        String messageId = "";
        String reservationNumber = "";
        String name = "";
        String date = "";
        String time = "";
        String phone = "";
        String email = "";
        String zone = "Alles";
        String notes = "";
        String status = "nieuw";
        String source = "Wix";
        int party = 2;
    }
}
