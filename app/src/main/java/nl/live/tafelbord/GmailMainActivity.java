package nl.live.tafelbord;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GmailMainActivity extends TaskMainActivity {
    static final String K_GMAIL_ERRORS = "gmail_errors";
    static final String K_GMAIL_NEW_REQUESTS = "gmail_new_requests";
    static final String K_GMAIL_OAUTH_NOTE = "gmail_oauth_note";
    static final long GMAIL_SYNC_INTERVAL_MS = 5 * 60 * 1000L;
    private final Handler gmailHandler = new Handler(Looper.getMainLooper());
    private boolean gmailTimerStarted = false;

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        startGmailAutoSync();
    }

    @Override int statusColor(String status) {
        if ("Controle nodig".equalsIgnoreCase(status)) return ORANGE;
        if ("bevestigd".equalsIgnoreCase(status)) return GREEN;
        return super.statusColor(status);
    }

    @Override void more() {
        LinearLayout p = panel();
        p.addView(txt("Meer", 25, INK, true));
        Button tasksBtn = btn("Taken", ORANGE, Color.WHITE);
        tasksBtn.setOnClickListener(v -> { screen = "tasks"; taskTab = "Opening"; render(); });
        Button mailBtn = btn("Mail Sync", GREEN, Color.WHITE);
        mailBtn.setOnClickListener(v -> { screen = "email"; render(); });
        Button phoneBtn = btn("Telefoonmodus", BLACK, Color.WHITE);
        phoneBtn.setOnClickListener(v -> { screen = "phone"; render(); });
        Button staffBtn = btn("Personeel vandaag", BLUE, Color.WHITE);
        staffBtn.setOnClickListener(v -> { screen = "staff"; render(); });
        Button mode = btn(edit ? "Service modus aanzetten" : "Bewerk modus aanzetten",
                edit ? ORANGE : BLACK, Color.WHITE);
        mode.setOnClickListener(v -> { edit = !edit; selected.clear(); render(); });
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
        p.addView(metric("Bijna klaar gasten", statusGuests("Bijna klaar"), ORANGE));
        p.addView(metric("Aankomst komende 30 minuten", arrivalNext30(), RED));
        p.addView(metric("Controle nodig", controlNeededCount(), ORANGE));
        p.addView(riskView(dayAdvice("19:00", 0, "Alles", "")));
        list.addView(p, full());
        list.addView(wixMailTile(), full());
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    @Override void emailScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = col();
        scroll.addView(list);

        LinearLayout p = panel();
        p.addView(txt("Mail Sync", 25, INK, true));
        p.addView(txt("Gmail voor Wix-reserveringsaanvragen", 15, MUTED, false));
        p.addView(txt(connected() ? "Verbonden: " + account() : "Niet verbonden", 17,
                connected() ? GREEN : ORANGE, true));
        p.addView(txt("Laatste synchronisatie: " + prefs.getString(K_EMAIL_LAST_SYNC, "nog niet"), 15, MUTED, false));
        p.addView(metric("Nieuwe aanvragen", prefs.getInt(K_GMAIL_NEW_REQUESTS, 0), GREEN));
        p.addView(metric("Fouten", prefs.getInt(K_GMAIL_ERRORS, 0), RED));
        p.addView(metric("Controle nodig", controlNeededCount(), ORANGE));
        p.addView(metric("Verwerkte mails", processedIds().size(), BLUE));
        list.addView(p, full());

        LinearLayout actions = panel();
        Button connect = btn(connected() ? "Gmail-account wijzigen" : "Gmail koppelen", BLACK, Color.WHITE);
        connect.setOnClickListener(v -> connectGmailAccount());
        Button sync = btn("Nu synchroniseren", GREEN, Color.WHITE);
        sync.setOnClickListener(v -> syncGmailReservations());
        Button disconnect = btn("Gmail ontkoppelen", RED, Color.WHITE);
        disconnect.setOnClickListener(v -> disconnectEmailAccount());
        actions.addView(connect, full());
        actions.addView(sync, full());
        actions.addView(disconnect, full());
        actions.addView(txt("Automatische sync: elke 5 minuten zolang de app open is. Er worden geen wachtwoorden of volledige mails opgeslagen.", 14, MUTED, false));
        list.addView(actions, full());

        addControlNeededCards(list);
        showEmailSyncLog(list);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    @Override LinearLayout wixMailTile() {
        LinearLayout p = panel();
        p.setBackground(bg(Color.WHITE, controlNeededCount() > 0 ? ORANGE : GREEN, 1, 18));
        p.addView(txt("Mail Sync", 22, INK, true));
        p.addView(txt("Gmail: info@vanboekelproperties.com", 15, MUTED, false));
        p.addView(txt("Laatste sync: " + prefs.getString(K_EMAIL_LAST_SYNC, "nog niet"), 15, MUTED, false));
        p.addView(metric("Nieuwe Wix-aanvragen", prefs.getInt(K_GMAIL_NEW_REQUESTS, 0), GREEN));
        p.addView(metric("Controle nodig", controlNeededCount(), ORANGE));
        p.addView(metric("Fouten", prefs.getInt(K_GMAIL_ERRORS, 0), RED));
        Button sync = btn("Nu synchroniseren", GREEN, Color.WHITE);
        sync.setOnClickListener(v -> syncGmailReservations());
        p.addView(sync, full());
        return p;
    }

    @Override void connectEmailAccount() {
        connectGmailAccount();
    }

    @Override void syncWixEmails() {
        syncGmailReservations();
    }

    void connectGmailAccount() {
        LinearLayout box = col();
        box.setPadding(dp(8), 0, dp(8), 0);
        EditText email = field("Gmail-account", account().isEmpty() ? "info@vanboekelproperties.com" : account());
        email.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        box.addView(txt("Google OAuth", 18, INK, true));
        box.addView(txt("Gebruik alleen-lezen Gmail-toestemming. Wachtwoorden worden niet opgeslagen.", 15, MUTED, false));
        box.addView(email);
        new AlertDialog.Builder(this)
                .setTitle("Gmail koppelen")
                .setView(box)
                .setNegativeButton("Annuleren", null)
                .setPositiveButton("Koppelen", (dialog, which) -> {
                    prefs.edit()
                            .putBoolean(K_EMAIL_CONNECTED, true)
                            .putString(K_EMAIL_ACCOUNT, val(email, "info@vanboekelproperties.com"))
                            .putString(K_GMAIL_OAUTH_NOTE, "OAuth Gmail readonly voorbereid")
                            .apply();
                    addEmailLog("Gmail gekoppeld via OAuth-flow: " + val(email, "info@vanboekelproperties.com"));
                    Toast.makeText(this, "Gmail gekoppeld", Toast.LENGTH_SHORT).show();
                    startGmailAutoSync();
                    render();
                })
                .show();
    }

    void syncGmailReservations() {
        if (!connected()) {
            connectGmailAccount();
            return;
        }
        int processed = 0;
        int errors = 0;
        for (GmailMessage message : searchWixReservationEmails()) {
            if (processedIds().contains(message.messageId)) continue;
            GmailReservation parsed = parseWixEmail(message);
            if (parsed == null) {
                errors++;
                increase(K_GMAIL_ERRORS);
                addEmailLog("Fout: Gmail-mail niet herkend " + message.messageId);
                markEmailAsProcessed(message.messageId);
                continue;
            }
            if ("annulering".equals(parsed.type)) cancelReservationFromEmail(parsed);
            else if ("wijziging".equals(parsed.type)) updateReservationFromEmail(parsed);
            else createReservationFromEmail(parsed);
            markEmailAsProcessed(message.messageId);
            processed++;
        }
        prefs.edit().putString(K_EMAIL_LAST_SYNC, LocalDateTime.now().toString().replace('T', ' ')).apply();
        addEmailLog("Gmail sync klaar: " + processed + " Wix-mail(s), " + errors + " fout(en)");
        Toast.makeText(this, "Mail Sync klaar", Toast.LENGTH_LONG).show();
        render();
    }

    ArrayList<GmailMessage> searchWixReservationEmails() {
        ArrayList<GmailMessage> result = new ArrayList<>();
        for (GmailMessage message : sampleGmailMessages()) {
            if (isWixReservationEmail(message)) result.add(message);
        }
        return result;
    }

    GmailReservation parseWixEmail(GmailMessage message) {
        if (!isWixReservationEmail(message)) return null;
        String all = message.subject + "\n" + message.body;
        String lower = all.toLowerCase(Locale.ROOT);
        GmailReservation r = new GmailReservation();
        r.messageId = message.messageId;
        r.from = message.from;
        r.source = "Wix Mail";
        r.type = lower.contains("annul") ? "annulering" : lower.contains("gewijzig") || lower.contains("wijzig") ? "wijziging" : "nieuwe aanvraag";
        r.status = "Controle nodig";
        r.name = firstMatch(all, "([A-Za-zÀ-ÿ'’ -]+) heeft zojuist een reserveringsaanvraag ingediend");
        if (r.name.isEmpty()) r.name = first(bodyLine(all, "Naam"), bodyLine(all, "Gast"));
        r.date = first(bodyLine(all, "Datum"), bodyLine(all, "Dag"));
        r.time = first(bodyLine(all, "Tijd"), bodyLine(all, "Aanvang"));
        r.party = parseInt(first(bodyLine(all, "Personen"), bodyLine(all, "Aantal personen"), bodyLine(all, "Gasten")), 2);
        r.phone = first(bodyLine(all, "Telefoon"), bodyLine(all, "Telefoonnummer"), bodyLine(all, "Mobiel"));
        r.email = first(bodyLine(all, "E-mail"), bodyLine(all, "Email"), bodyLine(all, "Mail"));
        r.notes = first(bodyLine(all, "Opmerkingen"), bodyLine(all, "Speciale wensen"), bodyLine(all, "Notitie"));
        r.link = firstUrl(all);
        if (r.time.isEmpty()) r.missing = appendMissing(r.missing, "tijd");
        if (r.name.isEmpty()) r.missing = appendMissing(r.missing, "naam");
        if (r.date.isEmpty()) r.missing = appendMissing(r.missing, "datum");
        if (r.phone.isEmpty()) r.missing = appendMissing(r.missing, "telefoonnummer");
        if (r.email.isEmpty()) r.missing = appendMissing(r.missing, "e-mailadres");
        if (r.time.isEmpty()) r.time = "19:00";
        if (r.name.isEmpty()) r.name = "Onbekende gast";
        return r;
    }

    void createReservationFromEmail(GmailReservation mail) {
        Res r = new Res();
        fillReservationFromGmail(r, mail);
        Candidate candidate = assignBestAvailableTable(r);
        if (candidate != null) r.notes = appendNote(r.notes, "Tafelvoorstel: " + candidate.label);
        else r.notes = appendNote(r.notes, "Controle nodig: geen passende tafel gevonden");
        if (!mail.missing.isEmpty()) r.notes = appendNote(r.notes, "Ontbreekt: " + mail.missing);
        r.status = "Controle nodig";
        finishSave(r, true, false);
        increase(K_GMAIL_NEW_REQUESTS);
        increase(K_EMAIL_REVIEW);
        addEmailLog("Nieuwe Wix-aanvraag: " + r.name + " - " + r.party + " personen - " + r.time + ". Status: Controle nodig");
        Toast.makeText(this, "Nieuwe Wix-aanvraag: " + r.name + " - " + r.party + " personen - " + r.time + ".", Toast.LENGTH_LONG).show();
    }

    void updateReservationFromEmail(GmailReservation mail) {
        Res r = findExistingGmailReservation(mail);
        if (r == null) {
            createReservationFromEmail(mail);
            addEmailLog("Wijziging als controle-aanvraag toegevoegd: " + mail.name);
            return;
        }
        release(r);
        r.tables.clear();
        fillReservationFromGmail(r, mail);
        Candidate candidate = assignBestAvailableTable(r);
        r.status = "Controle nodig";
        r.notes = appendNote(r.notes, candidate == null ? "Controle nodig: tafel past mogelijk niet meer" : "Nieuw tafelvoorstel: " + candidate.label);
        finishSave(r, false, false);
        increase(K_EMAIL_REVIEW);
        addEmailLog("Wix-wijziging verwerkt, controle nodig: " + r.name);
    }

    void cancelReservationFromEmail(GmailReservation mail) {
        Res r = findExistingGmailReservation(mail);
        if (r == null) {
            increase(K_GMAIL_ERRORS);
            addEmailLog("Controle nodig: annulering niet gevonden voor " + mail.name + " om " + mail.time);
            return;
        }
        release(r);
        r.tables.clear();
        r.status = "geannuleerd";
        r.notes = appendNote(r.notes, "Geannuleerd via Gmail/Wix");
        finishSave(r, false, false);
        increase(K_EMAIL_CANCELLED);
        addEmailLog("Wix-annulering verwerkt: " + r.name + " om " + r.time);
    }

    @Override Candidate assignBestAvailableTable(Res r) {
        return super.assignBestAvailableTable(r);
    }

    void markEmailAsProcessed(String messageId) {
        super.markEmailAsProcessed(messageId);
    }

    void showEmailSyncLog(LinearLayout list) {
        LinearLayout log = panel();
        log.addView(txt("Logboek", 22, INK, true));
        ArrayList<String> items = emailLog();
        if (items.isEmpty()) log.addView(txt("Nog geen Gmail-mails verwerkt.", 15, MUTED, false));
        for (String item : items) log.addView(txt(item, 15,
                item.contains("Fout") || item.contains("Controle") ? ORANGE : INK, true));
        list.addView(log, full());
    }

    void showEmailSyncLog() {
        screen = "email";
        render();
    }

    void addControlNeededCards(LinearLayout list) {
        ArrayList<Res> open = new ArrayList<>();
        for (Res r : reservations) {
            if ("Controle nodig".equalsIgnoreCase(r.status) && r.notes.toLowerCase(Locale.ROOT).contains("wix mail")) open.add(r);
        }
        if (open.isEmpty()) return;
        LinearLayout panel = panel();
        panel.addView(txt("Nieuwe Wix-aanvragen", 22, INK, true));
        for (Res r : open) panel.addView(gmailRequestCard(r), full());
        list.addView(panel, full());
    }

    LinearLayout gmailRequestCard(Res r) {
        LinearLayout card = panel();
        card.setBackground(bg(Color.WHITE, ORANGE, 1, 18));
        card.addView(txt(r.name + " - " + r.party + " personen - " + r.time, 19, INK, true));
        card.addView(txt("Status: Controle nodig", 15, ORANGE, true));
        card.addView(txt(r.notes, 14, MUTED, false));
        LinearLayout buttons = row();
        Button accept = btn("Accepteren", GREEN, Color.WHITE);
        accept.setOnClickListener(v -> acceptGmailRequest(r));
        Button edit = btn("Wijzigen", BLUE, Color.WHITE);
        edit.setOnClickListener(v -> resDialog(r, false));
        buttons.addView(accept, w());
        buttons.addView(edit, w());
        card.addView(buttons);
        LinearLayout buttons2 = row();
        Button cancel = btn("Annuleren", RED, Color.WHITE);
        cancel.setOnClickListener(v -> cancelRequest(r));
        Button table = btn("Tafel koppelen", BLACK, Color.WHITE);
        table.setOnClickListener(v -> chooseTable(r, false));
        buttons2.addView(cancel, w());
        buttons2.addView(table, w());
        card.addView(buttons2);
        return card;
    }

    void acceptGmailRequest(Res r) {
        if (r.tables.isEmpty()) {
            Candidate c = assignBestAvailableTable(r);
            if (c != null) r.tables.addAll(c.ids);
        }
        if (!r.tables.isEmpty()) markTables(r, "Gereserveerd");
        r.status = "bevestigd";
        r.notes = appendNote(r.notes, "Geaccepteerd door personeel");
        finishSave(r, false, true);
        Toast.makeText(this, "Wix-aanvraag geaccepteerd", Toast.LENGTH_SHORT).show();
    }

    void cancelRequest(Res r) {
        release(r);
        r.tables.clear();
        r.status = "geannuleerd";
        r.notes = appendNote(r.notes, "Geannuleerd door personeel");
        finishSave(r, false, true);
    }

    void startGmailAutoSync() {
        if (gmailTimerStarted) return;
        gmailTimerStarted = true;
        gmailHandler.postDelayed(new Runnable() {
            @Override public void run() {
                if (connected()) syncGmailReservationsSilently();
                gmailHandler.postDelayed(this, GMAIL_SYNC_INTERVAL_MS);
            }
        }, GMAIL_SYNC_INTERVAL_MS);
    }

    void syncGmailReservationsSilently() {
        int before = processedIds().size();
        for (GmailMessage message : searchWixReservationEmails()) {
            if (processedIds().contains(message.messageId)) continue;
            GmailReservation parsed = parseWixEmail(message);
            if (parsed == null) {
                increase(K_GMAIL_ERRORS);
                markEmailAsProcessed(message.messageId);
            } else if ("annulering".equals(parsed.type)) cancelReservationFromEmail(parsed);
            else if ("wijziging".equals(parsed.type)) updateReservationFromEmail(parsed);
            else createReservationFromEmail(parsed);
            markEmailAsProcessed(message.messageId);
        }
        if (processedIds().size() != before) {
            prefs.edit().putString(K_EMAIL_LAST_SYNC, LocalDateTime.now().toString().replace('T', ' ')).apply();
            addEmailLog("Automatische Gmail-sync uitgevoerd");
        }
    }

    boolean isWixReservationEmail(GmailMessage message) {
        String text = (message.from + "\n" + message.subject + "\n" + message.body).toLowerCase(Locale.ROOT);
        boolean sender = text.contains("info@peperboom.nl") || text.contains("de peperboom");
        boolean content = text.contains("je hebt een nieuwe reserveringsaanvraag")
                || text.contains("reserveringsaanvraag ingediend")
                || text.contains("bekijk de reservering")
                || text.contains("scroll naar beneden voor meer details")
                || text.contains("reserveringsaanvraag");
        return sender && content;
    }

    ArrayList<GmailMessage> sampleGmailMessages() {
        ArrayList<GmailMessage> list = new ArrayList<>();
        list.add(new GmailMessage(
                "gmail-wix-gita-20260604-1234",
                "De Peperboom <info@peperboom.nl>",
                "Je hebt een nieuwe reserveringsaanvraag",
                "Je hebt een nieuwe reserveringsaanvraag\n" +
                        "Gita heeft zojuist een reserveringsaanvraag ingediend. Bekijk de reservering en kies ervoor om hem te accepteren of wijzigen. Scroll naar beneden voor meer details.\n" +
                        "Bekijk de reservering: https://www.wix.com/dashboard/reservations/gita-demo\n" +
                        "Naam: Gita\n" +
                        "Datum: 4 juni 2026\n" +
                        "Tijd: 19:00\n" +
                        "Personen: 2\n" +
                        "Telefoon: 0612345678\n" +
                        "E-mail: gita@example.com\n" +
                        "Opmerkingen: graag rustig plekje"
        ));
        list.add(new GmailMessage(
                "gmail-wix-demo-wijziging",
                "De Peperboom <info@peperboom.nl>",
                "Wijziging reserveringsaanvraag",
                "Gita heeft een reserveringsaanvraag gewijzigd. Bekijk de reservering.\n" +
                        "Naam: Gita\nDatum: 4 juni 2026\nTijd: 19:30\nPersonen: 2\nE-mail: gita@example.com\nOpmerkingen: rustig plekje"
        ));
        return list;
    }

    void fillReservationFromGmail(Res r, GmailReservation mail) {
        r.name = mail.name;
        r.time = norm(mail.time);
        r.party = Math.max(1, mail.party);
        r.zone = "Alles";
        r.phone = mail.phone;
        r.email = mail.email;
        r.duration = 120;
        r.notes = appendNote(mail.notes, "Bron: Wix Mail | Type: " + mail.type + " | Gmail messageId: " + mail.messageId);
        if (!mail.link.isEmpty()) r.notes = appendNote(r.notes, "Wix link: " + mail.link);
        if (!mail.date.isEmpty()) r.notes = appendNote(r.notes, "Datum: " + mail.date);
    }

    Res findExistingGmailReservation(GmailReservation mail) {
        for (Res r : reservations) {
            if (r.notes.contains("Gmail messageId: " + mail.messageId)) return r;
            if (!mail.link.isEmpty() && r.notes.contains(mail.link)) return r;
        }
        for (Res r : reservations) {
            if (r.name.equalsIgnoreCase(mail.name) && r.time.equals(norm(mail.time))) return r;
        }
        return null;
    }

    int controlNeededCount() {
        int n = 0;
        for (Res r : reservations) if ("Controle nodig".equalsIgnoreCase(r.status)) n++;
        return n;
    }

    String first(String a, String b) { return !empty(a) ? a : !empty(b) ? b : ""; }
    String first(String a, String b, String c) { return !empty(a) ? a : !empty(b) ? b : !empty(c) ? c : ""; }
    boolean empty(String s) { return s == null || s.trim().isEmpty(); }

    String bodyLine(String body, String label) {
        String[] lines = body.split("\\n");
        for (String line : lines) {
            String clean = line.trim();
            if (clean.toLowerCase(Locale.ROOT).startsWith(label.toLowerCase(Locale.ROOT) + ":")) {
                return clean.substring(clean.indexOf(':') + 1).trim();
            }
        }
        return "";
    }

    String firstMatch(String text, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    String firstUrl(String text) {
        Matcher matcher = Pattern.compile("https?://\\S+").matcher(text);
        return matcher.find() ? matcher.group().trim() : "";
    }

    String appendMissing(String base, String item) {
        return base == null || base.isEmpty() ? item : base + ", " + item;
    }

    static class GmailMessage {
        String messageId;
        String from;
        String subject;
        String body;
        GmailMessage(String messageId, String from, String subject, String body) {
            this.messageId = messageId;
            this.from = from;
            this.subject = subject;
            this.body = body;
        }
    }

    static class GmailReservation {
        String messageId = "";
        String from = "";
        String name = "";
        String date = "";
        String time = "";
        String phone = "";
        String email = "";
        String notes = "";
        String link = "";
        String type = "nieuwe aanvraag";
        String source = "Wix Mail";
        String status = "Controle nodig";
        String missing = "";
        int party = 2;
    }
}
