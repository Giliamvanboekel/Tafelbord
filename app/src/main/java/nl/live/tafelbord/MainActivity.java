package nl.live.tafelbord;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
    private static final String PREFS = "live_tafelbord_horeca_v2";
    private static final String KEY_TABLES = "tables", KEY_RES = "reservations", KEY_SETTINGS = "settings";
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("nl-NL"));
    private static final int CREAM = Color.rgb(250, 247, 238), PAPER = Color.rgb(255, 253, 247), INK = Color.rgb(28, 43, 35);
    private static final int GREEN = Color.rgb(32, 132, 79), DARK_GREEN = Color.rgb(18, 77, 56), ORANGE = Color.rgb(222, 133, 38);
    private static final int RED = Color.rgb(193, 55, 49), BLUE = Color.rgb(43, 103, 178), GREY = Color.rgb(112, 117, 124), DARK_GREY = Color.rgb(66, 70, 75), LINE = Color.rgb(225, 216, 196), WARM = Color.rgb(178, 125, 66), RISK_RED = Color.rgb(128, 28, 28);

    private final ArrayList<Table> tables = new ArrayList<>();
    private final ArrayList<Reservation> reservations = new ArrayList<>();
    private final LinkedHashSet<String> selectedTableIds = new LinkedHashSet<>();
    private final ArrayList<Risk> risks = new ArrayList<>();
    private SharedPreferences prefs;
    private LinearLayout root, content;
    private String screen = "map", activeZone = "Alles", resZoneFilter = "Alles", searchText = "";
    private boolean serviceMode = true;
    private LocalDate selectedDate = LocalDate.now();
    private LocalTime selectedTime = roundedNow();
    private int floorRevision = 1, serviceStaff = 4, kitchenStaff = 2, defaultDuration = 120;
    private String kitchenCapacity = "Normaal", weather = "Normaal";
    private String phoneDate = LocalDate.now().toString(), phoneTime = "19:00", phoneParty = "4", phoneZone = "Geen voorkeur", phoneAdvice = "Vul de beller in en tik op Controleer.";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadAll();
        computeRisks();
        render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root = column();
        root.setPadding(dp(12), dp(12), dp(12), dp(20));
        root.setBackgroundColor(CREAM);
        scroll.addView(root);

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout title = column();
        title.addView(text("Restaurant De Peperboom", 14, DARK_GREEN, true));
        title.addView(text("Live Tafelbord", 28, INK, true));
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView mode = pill(serviceMode ? "Service modus" : "Bewerk modus", serviceMode ? DARK_GREEN : ORANGE);
        header.addView(mode);
        root.addView(header);
        root.addView(gap(8));
        root.addView(dashboard());
        root.addView(gap(8));
        root.addView(timeline());
        root.addView(gap(8));
        root.addView(navigation());
        root.addView(gap(8));
        content = column();
        root.addView(content);

        if ("res".equals(screen)) renderReservations();
        else if ("phone".equals(screen)) renderPhone();
        else if ("risk".equals(screen)) renderRisk();
        else if ("settings".equals(screen)) renderSettings();
        else renderMap();
        setContentView(scroll);
    }

    private LinearLayout dashboard() {
        computeRisks();
        LinearLayout box = column();
        LinearLayout a = row(), b = row();
        a.addView(dash("Reserveringen", todayReservations(), DARK_GREEN), weight());
        a.addView(dash("Gasten", todayGuests(), WARM), weight());
        a.addView(dash("Nu vrij", freeNow(), GREEN), weight());
        b.addView(dash("30 min vrij", freeSoon(), ORANGE), weight());
        b.addView(dash("Risico's", risks.size(), riskColor()), weight());
        b.addView(dash("Rekening", countStatus("rekening"), BLUE), weight());
        box.addView(a); box.addView(b);
        return box;
    }

    private TextView dash(String label, int value, int color) {
        TextView v = text(value + "\n" + label, 13, color, true);
        v.setGravity(Gravity.CENTER);
        v.setMinHeight(dp(62));
        v.setBackground(bg(Color.WHITE, color, 1, 8));
        return v;
    }

    private HorizontalScrollView timeline() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout line = row();
        line.setPadding(0, dp(2), 0, dp(2));
        Button minus = smallButton("-30", DARK_GREEN, Color.WHITE);
        minus.setOnClickListener(v -> { selectedTime = selectedTime.minusMinutes(30); render(); });
        line.addView(minus, fixed(64, 48));
        for (int h = 12; h <= 22; h++) {
            String t = String.format(Locale.ROOT, "%02d:00", h);
            boolean on = selectedTime.getHour() == h;
            Button b = smallButton(t, on ? ORANGE : Color.WHITE, on ? Color.WHITE : DARK_GREEN);
            final int hour = h;
            b.setOnClickListener(v -> { selectedTime = LocalTime.of(hour, 0); render(); });
            line.addView(b, fixed(80, 48));
        }
        Button plus = smallButton("+30", DARK_GREEN, Color.WHITE);
        plus.setOnClickListener(v -> { selectedTime = selectedTime.plusMinutes(30); render(); });
        line.addView(plus, fixed(64, 48));
        hsv.addView(line);
        return hsv;
    }

    private LinearLayout navigation() {
        LinearLayout box = column();
        LinearLayout tabs = row();
        tabs.addView(tab("Plattegrond", "map"), weight());
        tabs.addView(tab("Reserveringen", "res"), weight());
        tabs.addView(tab("Telefoon", "phone"), weight());
        LinearLayout tabs2 = row();
        tabs2.addView(tab("Risico", "risk"), weight());
        tabs2.addView(tab("Instellingen", "settings"), weight());
        box.addView(tabs); box.addView(tabs2);
        return box;
    }

    private Button tab(String label, String target) {
        boolean on = screen.equals(target);
        Button b = button(label, on ? DARK_GREEN : Color.WHITE, on ? Color.WHITE : DARK_GREEN);
        b.setOnClickListener(v -> { screen = target; selectedTableIds.clear(); render(); });
        return b;
    }

    private void renderMap() {
        LinearLayout zoneLine = row();
        zoneLine.addView(zoneButton("Alles"), weight());
        zoneLine.addView(zoneButton("Binnen"), weight());
        zoneLine.addView(zoneButton("Tuin"), weight());
        zoneLine.addView(zoneButton("Buitenterras"), weight());
        content.addView(zoneLine);

        LinearLayout modeLine = row();
        Button service = button("Service", serviceMode ? DARK_GREEN : Color.WHITE, serviceMode ? Color.WHITE : DARK_GREEN);
        service.setOnClickListener(v -> { serviceMode = true; selectedTableIds.clear(); render(); });
        Button edit = button("Bewerk", !serviceMode ? ORANGE : Color.WHITE, !serviceMode ? Color.WHITE : DARK_GREEN);
        edit.setOnClickListener(v -> { serviceMode = false; selectedTableIds.clear(); render(); });
        modeLine.addView(service, weight()); modeLine.addView(edit, weight());
        content.addView(modeLine);

        TextView hint = text(serviceMode ? "Tik op een grote tafel voor snelle service-acties." : "Selecteer tafels. Sleep grof of gebruik de grote pijlen. Alles klikt vast op het raster.", 15, Color.rgb(83, 78, 66), false);
        hint.setPadding(dp(4), dp(6), dp(4), dp(8));
        content.addView(hint);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(bg(PAPER, LINE, 1, 10));
        frame.addView(new FloorView(this), new FrameLayout.LayoutParams(-1, dp(620)));
        content.addView(frame, full());
        if (!serviceMode) editPanel();
    }

    private Button zoneButton(String z) {
        boolean on = activeZone.equals(z);
        Button b = button(z, on ? DARK_GREEN : Color.WHITE, on ? Color.WHITE : DARK_GREEN);
        b.setTextSize(12);
        b.setOnClickListener(v -> { activeZone = z; selectedTableIds.clear(); render(); });
        return b;
    }

    private void editPanel() {
        LinearLayout p = panel();
        p.addView(text("Bewerk modus", 23, INK, true));
        p.addView(text(selectedTableIds.isEmpty() ? "Geen tafel geselecteerd" : selectedTableIds.size() + " tafel(s) geselecteerd", 16, WARM, true));
        LinearLayout a = row();
        Button add = button("Tafel toevoegen", DARK_GREEN, Color.WHITE);
        add.setOnClickListener(v -> editTable(new Table("t" + System.currentTimeMillis(), nextNumber(), zoneForNewTable(), 50, 50, 90, 80, "vierkant", 4), true));
        Button save = button("Opslaan", BLUE, Color.WHITE);
        save.setOnClickListener(v -> { saveTables(); toast("Plattegrond opgeslagen"); });
        a.addView(add, weight()); a.addView(save, weight()); p.addView(a);
        LinearLayout r1 = row(), r2 = row(), r3 = row();
        r1.addView(moveButton("Omhoog", 0, -5), weight()); r1.addView(moveButton("Omlaag", 0, 5), weight());
        r2.addView(moveButton("Links", -5, 0), weight()); r2.addView(moveButton("Rechts", 5, 0), weight());
        r3.addView(sizeButton("Groter", 10), weight()); r3.addView(sizeButton("Kleiner", -10), weight());
        p.addView(r1); p.addView(r2); p.addView(r3);
        LinearLayout m = row();
        Button change = button("Wijzig tafel", Color.WHITE, DARK_GREEN);
        change.setOnClickListener(v -> { Table t = firstSelected(); if (t != null) editTable(t, false); });
        Button del = button("Verwijder", DARK_GREY, Color.WHITE);
        del.setOnClickListener(v -> deleteSelected());
        m.addView(change, weight()); m.addView(del, weight()); p.addView(m);
        LinearLayout c = row();
        Button merge = button("Tafels samenvoegen", ORANGE, Color.WHITE);
        merge.setOnClickListener(v -> mergeTables());
        Button split = button("Samenvoeging losmaken", GREY, Color.WHITE);
        split.setOnClickListener(v -> splitTables());
        c.addView(merge, weight()); c.addView(split, weight()); p.addView(c);
        Button standard = button("Groep opslaan als standaardopstelling", WARM, Color.WHITE);
        standard.setOnClickListener(v -> { floorRevision++; saveSettings(); saveTables(); toast("Standaardopstelling opgeslagen"); });
        p.addView(standard, full());
        Button cancel = button("Annuleren", Color.WHITE, DARK_GREEN);
        cancel.setOnClickListener(v -> { selectedTableIds.clear(); loadTables(); render(); });
        p.addView(cancel, full());
        content.addView(p, full());
    }

    private Button moveButton(String label, int dx, int dy) {
        Button b = button(label, Color.WHITE, DARK_GREEN);
        b.setOnClickListener(v -> { forSelected(t -> { t.x = clamp(t.x + dx, 5, 95); t.y = clamp(t.y + dy, 8, 95); snap(t); }); saveTables(); render(); });
        return b;
    }

    private Button sizeButton(String label, int delta) {
        Button b = button(label, Color.WHITE, DARK_GREEN);
        b.setOnClickListener(v -> { forSelected(t -> { t.width = clamp(t.width + delta, 70, 180); t.height = clamp(t.height + delta, 70, 160); }); saveTables(); render(); });
        return b;
    }

    private void renderReservations() {
        LinearLayout top = panel();
        top.addView(text("Reserveringen vandaag", 25, INK, true));
        LinearLayout filters = row();
        filters.addView(resFilter("Alles"), weight()); filters.addView(resFilter("Binnen"), weight()); filters.addView(resFilter("Tuin"), weight()); filters.addView(resFilter("Buitenterras"), weight());
        top.addView(filters);
        EditText search = field("Zoek op naam", searchText);
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){ searchText=s.toString(); } public void afterTextChanged(Editable e){} });
        top.addView(search);
        Button add = button("Reservering toevoegen", DARK_GREEN, Color.WHITE);
        add.setOnClickListener(v -> reservationDialog(new Reservation(), true));
        top.addView(add, full());
        content.addView(top, full());

        ArrayList<Reservation> list = new ArrayList<>(reservations);
        Collections.sort(list, (a,b) -> (a.date + a.time).compareTo(b.date + b.time));
        for (Reservation r : list) {
            if (!r.date.equals(selectedDate.toString())) continue;
            if (!"Alles".equals(resZoneFilter) && !r.preferredZone.equals(resZoneFilter)) continue;
            if (!searchText.trim().isEmpty() && !r.name.toLowerCase(Locale.ROOT).contains(searchText.trim().toLowerCase(Locale.ROOT))) continue;
            content.addView(reservationCard(r), full());
        }
    }

    private Button resFilter(String z) {
        boolean on = resZoneFilter.equals(z);
        Button b = button(z, on ? DARK_GREEN : Color.WHITE, on ? Color.WHITE : DARK_GREEN);
        b.setTextSize(12);
        b.setOnClickListener(v -> { resZoneFilter = z; render(); });
        return b;
    }

    private View reservationCard(Reservation r) {
        LinearLayout p = panel();
        int color = reservationColor(r.status);
        p.setBackground(bg(Color.WHITE, color, 1, 8));
        p.addView(text(r.time + "  " + safe(r.name, "Gast"), 21, INK, true));
        p.addView(text(r.partySize + " pers. - " + r.preferredZone + " - " + displayStatus(r.status), 15, color, true));
        p.addView(text("Tafel: " + (r.assignedTableIds.isEmpty() ? "nog kiezen" : tableNames(r.assignedTableIds)), 16, INK, false));
        if (!r.notes.isEmpty()) p.addView(text("Notitie: " + r.notes, 14, Color.rgb(83, 78, 66), false));
        Assignment a = suggest(r, false);
        p.addView(text("Beste keuze: " + a.bestText, 15, DARK_GREEN, true));
        if (!a.alternatives.isEmpty()) p.addView(text("Alternatieven: " + join(a.alternatives, ", "), 14, Color.rgb(83, 78, 66), false));
        if (!a.riskText.isEmpty()) p.addView(text("Risico: " + a.riskText, 14, RISK_RED, true));
        LinearLayout buttons = row();
        buttons.addView(cardAction("Aanwezig", GREEN, () -> { r.status = "aanwezig"; saveReservations(); render(); }), weight());
        buttons.addView(cardAction("Zit", RED, () -> seatReservation(r)), weight());
        buttons.addView(cardAction("Tafel", BLUE, () -> chooseTable(r)), weight());
        p.addView(buttons);
        LinearLayout buttons2 = row();
        buttons2.addView(cardAction("Wijzig", Color.WHITE, () -> reservationDialog(r, false)), weight());
        buttons2.addView(cardAction("Rekening", BLUE, () -> { r.status = "rekening"; markReservationTables(r, "rekening"); saveAll(); render(); }), weight());
        buttons2.addView(cardAction("No-show", DARK_GREY, () -> { r.status = "no-show"; releaseReservationTables(r); saveAll(); render(); }), weight());
        p.addView(buttons2);
        return p;
    }

    private Button cardAction(String label, int color, Runnable r) {
        int fg = color == Color.WHITE ? DARK_GREEN : Color.WHITE;
        Button b = button(label, color, fg);
        b.setTextSize(13);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private void renderPhone() {
        LinearLayout p = panel();
        p.addView(text("Telefoonmodus", 25, INK, true));
        p.addView(text("Snel antwoord voor bellers. Grote velden, direct voorstel.", 15, Color.rgb(83,78,66), false));
        EditText d = field("Datum", phoneDate), t = field("Tijd", phoneTime), party = field("Aantal personen", phoneParty);
        party.setInputType(InputType.TYPE_CLASS_NUMBER);
        Spinner pref = spinner(new String[]{"Geen voorkeur", "Binnen", "Tuin", "Buitenterras"}, phoneZone);
        p.addView(d); p.addView(t); p.addView(party); p.addView(text("Voorkeur", 14, INK, true)); p.addView(pref);
        Button check = button("Controleer beschikbaarheid", DARK_GREEN, Color.WHITE);
        check.setOnClickListener(v -> {
            phoneDate = value(d, LocalDate.now().toString());
            phoneTime = normalizeTime(t.getText().toString(), "19:00");
            phoneParty = value(party, "2");
            phoneZone = pref.getSelectedItem().toString();
            Reservation probe = new Reservation();
            probe.name = "Beller"; probe.date = phoneDate; probe.time = phoneTime; probe.partySize = Math.max(1, number(party, 2)); probe.durationMinutes = defaultDuration; probe.preferredZone = phoneZone;
            Assignment a = suggest(probe, false);
            phoneAdvice = phoneAnswer(probe, a);
            render();
        });
        p.addView(check, full());
        TextView answer = text(phoneAdvice, 20, Color.WHITE, true);
        answer.setPadding(dp(14), dp(16), dp(14), dp(16));
        answer.setBackground(bg(phoneAdvice.contains("vol") ? ORANGE : DARK_GREEN, DARK_GREEN, 0, 10));
        p.addView(answer, full());
        Button make = button("Reservering maken met dit voorstel", BLUE, Color.WHITE);
        make.setOnClickListener(v -> {
            Reservation r = new Reservation();
            r.name = "Nieuwe gast"; r.phone = ""; r.date = phoneDate; r.time = phoneTime; r.partySize = Math.max(1, parseInt(phoneParty, 2)); r.preferredZone = phoneZone; r.durationMinutes = defaultDuration; r.source = "telefoon";
            Assignment a = suggest(r, true); applyAssignment(r, a); reservations.add(r); saveAll(); screen = "res"; render();
        });
        p.addView(make, full());
        content.addView(p, full());
    }

    private void renderRisk() {
        computeRisks();
        LinearLayout top = panel();
        int color = riskColor();
        top.setBackground(bg(Color.WHITE, color, 2, 8));
        top.addView(text(riskTitle(), 25, color, true));
        top.addView(text("Personeel, keuken, planning en tafelvrijgave in een oogopslag.", 15, Color.rgb(83,78,66), false));
        top.addView(stepper("Bediening", serviceStaff, v -> { serviceStaff = Math.max(1, v); saveSettings(); render(); }));
        top.addView(stepper("Keuken", kitchenStaff, v -> { kitchenStaff = Math.max(1, v); saveSettings(); render(); }));
        top.addView(choice("Keukencapaciteit", new String[]{"Laag", "Normaal", "Hoog"}, kitchenCapacity, s -> { kitchenCapacity = s; saveSettings(); render(); }));
        top.addView(choice("Weer buiten", new String[]{"Normaal", "Slecht weer", "Zeer warm"}, weather, s -> { weather = s; saveSettings(); render(); }));
        content.addView(top, full());
        if (risks.isEmpty()) content.addView(info("Laag risico: service is haalbaar.", GREEN), full());
        for (Risk r : risks) content.addView(info(r.severityLabel() + ": " + r.message, r.color()), full());
    }

    private void renderSettings() {
        LinearLayout p = panel();
        p.addView(text("Instellingen", 25, INK, true));
        p.addView(stepper("Gemiddelde tafelduur", defaultDuration, v -> { defaultDuration = clamp(v, 45, 240); saveSettings(); render(); }));
        p.addView(text("Zones: Binnen, Tuin, Buitenterras", 16, DARK_GREEN, true));
        p.addView(text("Plattegrondversie: " + floorRevision, 16, WARM, true));
        content.addView(p, full());
        content.addView(settingButton("Reset plattegrond", ORANGE, () -> { seedTables(); saveTables(); render(); }), full());
        content.addView(settingButton("Reset demo-data", WARM, () -> { seedTables(); seedDemoReservations(); saveAll(); render(); }), full());
        content.addView(settingButton("Alle reserveringen vandaag wissen", DARK_GREY, () -> { reservations.removeIf(r -> r.date.equals(LocalDate.now().toString())); releaseAllTables(); saveAll(); render(); }), full());
    }

    private Button settingButton(String label, int color, Runnable r) {
        Button b = button(label, color, Color.WHITE);
        b.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(label).setMessage("Weet je het zeker?").setNegativeButton("Annuleren", null).setPositiveButton("Ja", (d,w) -> r.run()).show());
        return b;
    }

    private void serviceDialog(Table t) {
        ArrayList<Table> group = groupFor(t);
        LinearLayout box = column();
        box.setPadding(dp(8), 0, dp(8), 0);
        box.addView(text(group.size() > 1 ? "Tafelgroep " + tableNames(idsOf(group)) : "Tafel " + t.number, 24, INK, true));
        box.addView(text(totalCapacity(group) + " personen - " + displayStatus(displayStatusAt(t)), 16, statusColor(displayStatusAt(t)), true));
        Reservation linked = linkedReservation(t);
        if (linked != null) box.addView(text("Reservering: " + linked.time + " - " + linked.name, 16, BLUE, true));
        EditText note = field("Notitie", t.note);
        box.addView(note);
        addDialogButton(box, "Reservering bekijken", BLUE, () -> { if (linked != null) reservationDialog(linked, false); else toast("Geen reservering gekoppeld"); });
        addDialogButton(box, "Gast aanwezig", GREEN, () -> { if (linked != null) linked.status = "aanwezig"; saveReservations(); render(); });
        addDialogButton(box, "Tafel bezet maken", RED, () -> { markGroup(group, "bezet"); copyNote(group, note); saveTables(); render(); });
        addDialogButton(box, "Tafel vrijmaken", GREEN, () -> { for (Table x : group) x.clearService(); saveAll(); render(); });
        addDialogButton(box, "Rekening gevraagd", BLUE, () -> { markGroup(group, "rekening"); copyNote(group, note); saveTables(); render(); });
        addDialogButton(box, "Bijna vrij", ORANGE, () -> { markGroup(group, "bijna vrij"); copyNote(group, note); saveTables(); render(); });
        addDialogButton(box, "Reservering verplaatsen", WARM, () -> { if (linked != null) chooseTable(linked); else toast("Geen reservering gekoppeld"); });
        addDialogButton(box, "Tafel koppelen aan reservering", DARK_GREEN, () -> linkReservationToTable(t));
        addDialogButton(box, "Buiten gebruik", DARK_GREY, () -> { for (Table x: group) { x.isOutOfService = true; x.status = "buiten gebruik"; } saveTables(); render(); });
        new AlertDialog.Builder(this).setView(box).setNegativeButton("Sluiten", null).setPositiveButton("Notitie opslaan", (d,w) -> { copyNote(group, note); saveTables(); render(); }).show();
    }

    private void addDialogButton(LinearLayout box, String label, int color, Runnable r) {
        Button b = button(label, color, Color.WHITE);
        b.setOnClickListener(v -> r.run());
        box.addView(b, full());
    }

    private void reservationDialog(Reservation r, boolean fresh) {
        LinearLayout box = column(); box.setPadding(dp(8), 0, dp(8), 0);
        EditText name = field("Naam", r.name), phone = field("Telefoon", r.phone), date = field("Datum", r.date.isEmpty() ? selectedDate.toString() : r.date), time = field("Tijd", r.time.isEmpty() ? selectedTime.format(TIME_FMT) : r.time);
        EditText party = field("Aantal personen", r.partySize > 0 ? "" + r.partySize : "2"), duration = field("Duur minuten", r.durationMinutes > 0 ? "" + r.durationMinutes : "" + defaultDuration), notes = field("Notitie", r.notes);
        party.setInputType(InputType.TYPE_CLASS_NUMBER); duration.setInputType(InputType.TYPE_CLASS_NUMBER);
        Spinner pref = spinner(new String[]{"Geen voorkeur", "Binnen", "Tuin", "Buitenterras"}, safe(r.preferredZone, "Geen voorkeur"));
        Spinner status = spinner(new String[]{"verwacht", "aanwezig", "zit", "hoofdgerecht", "dessert", "rekening", "vertrokken", "geannuleerd", "no-show"}, safe(r.status, "verwacht"));
        box.addView(name); box.addView(phone); box.addView(date); box.addView(time); box.addView(party); box.addView(duration); box.addView(text("Voorkeur", 14, INK, true)); box.addView(pref); box.addView(text("Status", 14, INK, true)); box.addView(status); box.addView(notes);
        new AlertDialog.Builder(this).setTitle(fresh ? "Reservering toevoegen" : "Reservering wijzigen").setView(box).setNegativeButton("Annuleren", null).setPositiveButton("Opslaan", (d,w) -> {
            r.name = value(name, "Gast"); r.phone = phone.getText().toString().trim(); r.date = value(date, selectedDate.toString()); r.time = normalizeTime(time.getText().toString(), selectedTime.format(TIME_FMT));
            r.partySize = Math.max(1, number(party, 2)); r.durationMinutes = Math.max(30, number(duration, defaultDuration)); r.preferredZone = pref.getSelectedItem().toString(); r.status = status.getSelectedItem().toString(); r.notes = notes.getText().toString().trim();
            if (r.createdAt.isEmpty()) r.createdAt = LocalDateTime.now().toString();
            Assignment a = suggest(r, true); applyAssignment(r, a);
            if (fresh) reservations.add(r);
            saveAll(); render();
        }).show();
    }

    private void editTable(Table t, boolean fresh) {
        LinearLayout box = column(); box.setPadding(dp(8), 0, dp(8), 0);
        EditText nr = field("Tafelnummer", t.number), cap = field("Capaciteit", "" + t.capacity), width = field("Breedte", "" + t.width), height = field("Hoogte", "" + t.height);
        cap.setInputType(InputType.TYPE_CLASS_NUMBER); width.setInputType(InputType.TYPE_CLASS_NUMBER); height.setInputType(InputType.TYPE_CLASS_NUMBER);
        Spinner shape = spinner(new String[]{"2-persoons", "4-persoons", "6-persoons", "lange groepstafel", "ronde tafel", "vierkante tafel"}, t.shape);
        Spinner zone = spinner(new String[]{"Binnen", "Tuin", "Buitenterras"}, t.zone);
        box.addView(nr); box.addView(cap); box.addView(width); box.addView(height); box.addView(text("Vorm", 14, INK, true)); box.addView(shape); box.addView(text("Zone", 14, INK, true)); box.addView(zone);
        new AlertDialog.Builder(this).setTitle(fresh ? "Tafel toevoegen" : "Tafel wijzigen").setView(box).setNegativeButton("Annuleren", null).setPositiveButton("Opslaan", (d,w) -> {
            String old = t.number; t.number = value(nr, old); t.capacity = Math.max(1, number(cap, t.capacity)); t.width = clamp(number(width, t.width), 70, 220); t.height = clamp(number(height, t.height), 70, 180); t.shape = shape.getSelectedItem().toString(); t.zone = zone.getSelectedItem().toString(); snap(t);
            if (fresh) tables.add(t); renameTable(old, t.number); selectedTableIds.clear(); selectedTableIds.add(t.id); saveTables(); render();
        }).show();
    }

    private class FloorView extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); Table dragging; float downX, downY; boolean moved;
        FloorView(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            p.setStyle(Paint.Style.FILL); p.setColor(PAPER); c.drawRect(0,0,getWidth(),getHeight(),p);
            drawGrid(c); drawGroups(c);
            for (Table t : tables) if (visible(t)) drawTable(c, t);
        }
        private void drawGrid(Canvas c) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1)); p.setColor(Color.rgb(235, 228, 210));
            for (int x = 5; x < 100; x += 5) { float px = x * getWidth() / 100f; c.drawLine(px, dp(42), px, getHeight() - dp(10), p); }
            for (int y = 10; y < 100; y += 5) { float py = y * getHeight() / 100f; c.drawLine(dp(10), py, getWidth() - dp(10), py, p); }
            p.setStyle(Paint.Style.FILL); p.setColor(DARK_GREEN); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(dp(16));
            c.drawText(activeZone + " - " + selectedTime.format(TIME_FMT), dp(18), dp(30), p);
        }
        private void drawGroups(Canvas c) {
            HashSet<String> drawn = new HashSet<>();
            for (Table t : tables) if (visible(t) && !t.linkedTableIds.isEmpty() && !drawn.contains(t.id)) {
                ArrayList<Table> g = groupFor(t); for (Table x : g) drawn.add(x.id);
                RectF bounds = null; for (Table x : g) if (visible(x)) { RectF r = rect(x); if (bounds == null) bounds = new RectF(r); else bounds.union(r); }
                if (bounds != null) { bounds.inset(-dp(10), -dp(10)); p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(255, 243, 222)); c.drawRoundRect(bounds, dp(14), dp(14), p); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(4)); p.setColor(ORANGE); c.drawRoundRect(bounds, dp(14), dp(14), p); }
            }
        }
        private void drawTable(Canvas c, Table t) {
            RectF r = rect(t); String st = displayStatusAt(t); int color = statusColor(st);
            p.setStyle(Paint.Style.FILL); p.setColor(color);
            if (isRound(t)) c.drawOval(r, p); else c.drawRoundRect(r, dp(9), dp(9), p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(selectedTableIds.contains(t.id) ? dp(6) : dp(2)); p.setColor(selectedTableIds.contains(t.id) ? WARM : Color.WHITE);
            if (isRound(t)) c.drawOval(r, p); else c.drawRoundRect(r, dp(9), dp(9), p);
            p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); p.setColor(Color.WHITE); p.setTextSize(dp(t.number.length() > 3 ? 18 : 23));
            c.drawText(t.number, r.centerX(), r.centerY() + dp(7), p);
            p.setTextSize(dp(12));
            String line = labelForTable(t, st); if (!line.isEmpty()) c.drawText(line, r.centerX(), r.bottom + dp(16), p);
            p.setTextAlign(Paint.Align.LEFT);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            Table hit = hit(e.getX(), e.getY());
            if (e.getAction() == MotionEvent.ACTION_DOWN) { dragging = hit; downX = e.getX(); downY = e.getY(); moved = false; return hit != null; }
            if (e.getAction() == MotionEvent.ACTION_MOVE && !serviceMode && dragging != null) {
                if (Math.abs(e.getX() - downX) > dp(5) || Math.abs(e.getY() - downY) > dp(5)) moved = true;
                dragging.x = clamp(Math.round(e.getX() * 100f / Math.max(1, getWidth())), 5, 95); dragging.y = clamp(Math.round(e.getY() * 100f / Math.max(1, getHeight())), 8, 95); snap(dragging); invalidate(); return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP && dragging != null) {
                Table t = dragging; dragging = null;
                if (serviceMode) serviceDialog(t); else { if (!moved) { if (selectedTableIds.contains(t.id)) selectedTableIds.remove(t.id); else selectedTableIds.add(t.id); } saveTables(); render(); }
                return true;
            }
            return true;
        }
        private RectF rect(Table t) { float cx = t.x * getWidth() / 100f, cy = t.y * getHeight() / 100f; float w = Math.max(dp(70), dp(t.width)), h = Math.max(dp(70), dp(t.height)); return new RectF(cx - w/2, cy - h/2, cx + w/2, cy + h/2); }
        private Table hit(float x, float y) { for (int i = tables.size()-1; i >= 0; i--) { Table t = tables.get(i); if (!visible(t)) continue; RectF r = rect(t); r.inset(-dp(8), -dp(8)); if (r.contains(x, y)) return t; } return null; }
    }

    private Assignment suggest(Reservation r, boolean allowGroups) {
        Assignment out = new Assignment(); LocalTime start = parseTime(r.time, selectedTime); int duration = r.durationMinutes > 0 ? r.durationMinutes : defaultDuration;
        ArrayList<Candidate> candidates = new ArrayList<>(); HashSet<String> seenGroups = new HashSet<>();
        for (Table t : tables) {
            if (!zoneOk(t.zone, r.preferredZone) || t.isOutOfService) continue;
            if (!freeAt(t, r.date, start, duration, r.id)) continue;
            ArrayList<Table> group = groupFor(t); String key = groupKey(group); if (seenGroups.contains(key)) continue; seenGroups.add(key);
            int cap = totalCapacity(group); if (cap >= r.partySize) candidates.add(new Candidate(idsOf(group), cap, group.size() > 1 ? "combinatie " + tableNames(idsOf(group)) : "tafel " + t.number, group.get(0).zone));
        }
        if (allowGroups && r.partySize >= 5) candidates.addAll(groupCandidates(r, start, duration));
        Collections.sort(candidates, (a,b) -> a.capacity != b.capacity ? a.capacity - b.capacity : a.label.compareTo(b.label));
        Candidate best = null; for (Candidate c : candidates) if (c.capacity == r.partySize) { best = c; break; } if (best == null && !candidates.isEmpty()) best = candidates.get(0);
        if (best != null) { out.best = best; out.bestText = best.label + " (" + best.capacity + " pers.)"; for (Candidate c : candidates) if (c != best && out.alternatives.size() < 3) out.alternatives.add(c.label); }
        else { out.bestText = "geen passende tafel vrij"; out.riskText = laterSuggestion(r); }
        if (peakAround(r.date, start) >= 4) out.riskText = appendRisk(out.riskText, "veel reserveringen rond " + r.time);
        return out;
    }

    private ArrayList<Candidate> groupCandidates(Reservation r, LocalTime start, int duration) {
        ArrayList<Candidate> out = new ArrayList<>(); ArrayList<Table> free = new ArrayList<>();
        for (Table t : tables) if (t.linkedTableIds.isEmpty() && !t.isOutOfService && zoneOk(t.zone, r.preferredZone) && freeAt(t, r.date, start, duration, r.id)) free.add(t);
        for (int i=0;i<free.size();i++) for (int j=i+1;j<free.size();j++) {
            Table a = free.get(i), b = free.get(j); if (!a.zone.equals(b.zone)) continue; if (Math.abs(a.x-b.x) > 22 || Math.abs(a.y-b.y) > 22) continue;
            int cap = a.capacity + b.capacity; if (cap >= r.partySize) out.add(new Candidate(idsOf(Arrays.asList(a,b)), cap, a.number + " + " + b.number + " aanbevolen", a.zone));
        }
        Collections.sort(out, (a,b) -> a.capacity - b.capacity); return out;
    }

    private void applyAssignment(Reservation r, Assignment a) {
        releaseReservationTables(r); r.assignedTableIds.clear(); if (a.best == null) return;
        r.assignedTableIds.addAll(a.best.tableIds);
        for (String id : r.assignedTableIds) { Table t = tableById(id); if (t != null) { t.reservationId = r.id; if ("verwacht".equals(r.status)) t.status = "gereserveerd"; } }
    }

    private void seatReservation(Reservation r) { if (r.assignedTableIds.isEmpty()) applyAssignment(r, suggest(r, true)); r.status = "zit"; markReservationTables(r, "bezet"); saveAll(); render(); }
    private void chooseTable(Reservation r) {
        Assignment a = suggest(r, true); ArrayList<String> labels = new ArrayList<>(); ArrayList<Candidate> options = new ArrayList<>(); labels.add("Geen tafel"); if (a.best != null) { labels.add("Beste keuze: " + a.best.label); options.add(a.best); }
        for (String alt : a.alternatives) for (Candidate c : groupCandidatesForLabel(r, alt)) { labels.add(c.label); options.add(c); }
        for (Table t : tables) if (zoneOk(t.zone, r.preferredZone) && !t.isOutOfService) { Candidate c = new Candidate(one(t.id), t.capacity, "tafel " + t.number, t.zone); labels.add(c.label); options.add(c); }
        Spinner sp = spinner(labels.toArray(new String[0]), labels.get(0));
        new AlertDialog.Builder(this).setTitle("Tafel kiezen").setView(sp).setNegativeButton("Annuleren", null).setPositiveButton("Koppelen", (d,w) -> { int pos = sp.getSelectedItemPosition(); releaseReservationTables(r); r.assignedTableIds.clear(); if (pos > 0) r.assignedTableIds.addAll(options.get(pos-1).tableIds); saveAll(); render(); }).show();
    }

    private ArrayList<Candidate> groupCandidatesForLabel(Reservation r, String label) { ArrayList<Candidate> l = new ArrayList<>(); LocalTime st = parseTime(r.time, selectedTime); for (Candidate c : groupCandidates(r, st, r.durationMinutes > 0 ? r.durationMinutes : defaultDuration)) if (c.label.equals(label)) l.add(c); return l; }
    private void linkReservationToTable(Table t) {
        ArrayList<Reservation> open = new ArrayList<>(); ArrayList<String> labels = new ArrayList<>(); labels.add("Kies reservering");
        for (Reservation r : reservations) if (r.date.equals(selectedDate.toString()) && !closed(r.status)) { open.add(r); labels.add(r.time + " - " + r.name + " (" + r.partySize + ")"); }
        Spinner sp = spinner(labels.toArray(new String[0]), labels.get(0));
        new AlertDialog.Builder(this).setTitle("Koppel aan reservering").setView(sp).setNegativeButton("Annuleren", null).setPositiveButton("Koppelen", (d,w) -> { int pos = sp.getSelectedItemPosition()-1; if (pos >= 0) { Reservation r = open.get(pos); releaseReservationTables(r); r.assignedTableIds.clear(); r.assignedTableIds.addAll(idsOf(groupFor(t))); for (String id : r.assignedTableIds) { Table x = tableById(id); if (x != null) x.reservationId = r.id; } saveAll(); render(); } }).show();
    }

    private void mergeTables() {
        if (selectedTableIds.size() < 2) { toast("Selecteer minimaal twee tafels"); return; }
        ArrayList<String> ids = new ArrayList<>(selectedTableIds);
        for (String id : ids) { Table t = tableById(id); if (t != null) { t.linkedTableIds.clear(); for (String other : ids) if (!other.equals(id)) t.linkedTableIds.add(other); } }
        saveTables(); render();
    }
    private void splitTables() { forSelected(t -> t.linkedTableIds.clear()); saveTables(); render(); }
    private void deleteSelected() { if (selectedTableIds.isEmpty()) return; new AlertDialog.Builder(this).setTitle("Tafels verwijderen").setMessage("Geselecteerde tafels verwijderen?").setNegativeButton("Annuleren", null).setPositiveButton("Verwijder", (d,w) -> { tables.removeIf(t -> selectedTableIds.contains(t.id)); selectedTableIds.clear(); saveTables(); render(); }).show(); }

    private void computeRisks() {
        risks.clear(); HashMap<String,Integer> perQuarter = new HashMap<>();
        for (Reservation r : reservations) if (r.date.equals(selectedDate.toString()) && !closed(r.status)) { String bucket = r.time; perQuarter.put(bucket, perQuarter.containsKey(bucket) ? perQuarter.get(bucket)+r.partySize : r.partySize); }
        for (String bucket : perQuarter.keySet()) if (perQuarter.get(bucket) >= 18) risks.add(new Risk("keukenpiek", "hoog", "Keukenpiek rond " + bucket + ": " + perQuarter.get(bucket) + " gasten tegelijk.")); else if (perQuarter.get(bucket) >= 12) risks.add(new Risk("drukte", "middel", "Veel gasten rond " + bucket + ". Spreid reserveringen waar mogelijk."));
        int seatedGuests = seatedGuests(); if (seatedGuests / Math.max(1, serviceStaff) > 18) risks.add(new Risk("bediening", "hoog", "Te weinig bediening voor " + seatedGuests + " gasten."));
        int kitchenLimit = "Hoog".equals(kitchenCapacity) ? 28 : "Laag".equals(kitchenCapacity) ? 14 : 20; if (seatedGuests / Math.max(1, kitchenStaff) > kitchenLimit) risks.add(new Risk("keuken", "hoog", "Keuken raakt overbelast. Beperk grote groepen tijdelijk."));
        if (countStatus("rekening") >= 4) risks.add(new Risk("rekening", "middel", "Veel tafels op rekening. Versnel afrekenen."));
        if (countStatus("bijna vrij") >= 5) risks.add(new Risk("vrijgave", "middel", "Veel tafels bijna vrij. Zet iemand op afruimen."));
        if ("Slecht weer".equals(weather)) for (Reservation r : reservations) if (r.date.equals(selectedDate.toString()) && ("Tuin".equals(r.preferredZone) || "Buitenterras".equals(r.preferredZone))) { risks.add(new Risk("weer", "middel", "Buitenreservering bij slecht weer: " + r.time + " " + r.name)); break; }
        for (Reservation r : reservations) if (r.date.equals(selectedDate.toString()) && !r.assignedTableIds.isEmpty() && !closed(r.status)) for (String id : r.assignedTableIds) if (doubleBooked(r, id)) risks.add(new Risk("dubbel", "hoog", "Tafel dubbel gepland rond " + r.time + ": " + tableNumber(id)));
    }

    private void loadAll() { loadSettings(); loadTables(); loadReservations(); if (tables.isEmpty()) { seedTables(); saveTables(); } }
    private void loadTables() { tables.clear(); try { JSONArray a = new JSONArray(prefs.getString(KEY_TABLES, "[]")); for (int i=0;i<a.length();i++) tables.add(Table.from(a.getJSONObject(i))); } catch(Exception ignored) { tables.clear(); } }
    private void loadReservations() { reservations.clear(); try { JSONArray a = new JSONArray(prefs.getString(KEY_RES, "[]")); for (int i=0;i<a.length();i++) reservations.add(Reservation.from(a.getJSONObject(i))); } catch(Exception ignored) { reservations.clear(); } }
    private void loadSettings() { try { JSONObject j = new JSONObject(prefs.getString(KEY_SETTINGS, "{}")); serviceStaff = j.optInt("serviceStaff", 4); kitchenStaff = j.optInt("kitchenStaff", 2); defaultDuration = j.optInt("defaultDuration", 120); kitchenCapacity = j.optString("kitchenCapacity", "Normaal"); weather = j.optString("weather", "Normaal"); floorRevision = j.optInt("floorRevision", 1); } catch(Exception ignored) {} }
    private void saveAll() { saveTables(); saveReservations(); saveSettings(); }
    private void saveTables() { JSONArray a = new JSONArray(); for (Table t : tables) a.put(t.json()); prefs.edit().putString(KEY_TABLES, a.toString()).apply(); }
    private void saveReservations() { JSONArray a = new JSONArray(); for (Reservation r : reservations) a.put(r.json()); prefs.edit().putString(KEY_RES, a.toString()).apply(); }
    private void saveSettings() { JSONObject j = new JSONObject(); try { j.put("serviceStaff", serviceStaff); j.put("kitchenStaff", kitchenStaff); j.put("defaultDuration", defaultDuration); j.put("kitchenCapacity", kitchenCapacity); j.put("weather", weather); j.put("floorRevision", floorRevision); } catch(Exception ignored) {} prefs.edit().putString(KEY_SETTINGS, j.toString()).apply(); }

    private void seedTables() {
        tables.clear();
        String[] inside = {"1","2","3","3b","4","5","5b","6","7","8","9","10","11","12","13","14","15","16"};
        int[][] pi = {{14,14},{33,14},{52,14},{72,14},{88,14},{15,33},{34,33},{53,33},{75,33},{15,53},{35,53},{55,53},{77,53},{15,75},{35,75},{55,75},{75,75},{90,75}};
        for (int i=0;i<inside.length;i++) addSeed(inside[i], "Binnen", pi[i][0], pi[i][1], inside[i].contains("b") ? 2 : (i>=11 && i<=12 ? 6 : 4), inside[i].contains("b") ? "ronde tafel" : "vierkante tafel");
        String[] garden = {"40","41","41-1","42","43","43-2","44","45","46","47","47-2","48","49","49-2"};
        int[][] pg = {{14,16},{32,16},{50,16},{70,16},{88,16},{18,38},{38,38},{58,38},{78,38},{20,62},{40,62},{60,62},{80,62},{50,84}};
        for (int i=0;i<garden.length;i++) addSeed(garden[i], "Tuin", pg[i][0], pg[i][1], garden[i].contains("-") ? 2 : 4, garden[i].contains("-") ? "ronde tafel" : "4-persoons");
        String[] terrace = {"50","51","52","53","53-2","54","54-2","55","56","57","58"};
        int[][] pt = {{14,18},{34,18},{54,18},{74,18},{90,18},{18,45},{38,45},{58,45},{78,45},{30,72},{62,72}};
        for (int i=0;i<terrace.length;i++) addSeed(terrace[i], "Buitenterras", pt[i][0], pt[i][1], terrace[i].contains("-") ? 2 : 4, i>=9 ? "lange groepstafel" : "vierkante tafel");
    }
    private void addSeed(String number, String zone, int x, int y, int cap, String shape) { int w = cap >= 6 ? 140 : 88, h = cap >= 6 ? 86 : 78; if (shape.contains("ronde")) { w = 80; h = 80; } tables.add(new Table("table-" + number, number, zone, x, y, w, h, shape, cap)); }
    private void seedDemoReservations() { reservations.clear(); Reservation a = demo("Jansen", "18:00", 2, "Binnen"); Reservation b = demo("De Vries", "18:30", 4, "Tuin"); Reservation c = demo("Bakker", "19:00", 6, "Geen voorkeur"); reservations.add(a); reservations.add(b); reservations.add(c); for (Reservation r : reservations) applyAssignment(r, suggest(r, true)); }
    private Reservation demo(String name, String time, int party, String zone) { Reservation r = new Reservation(); r.name = name; r.date = LocalDate.now().toString(); r.time = time; r.partySize = party; r.preferredZone = zone; r.durationMinutes = defaultDuration; r.source = "demo"; r.createdAt = LocalDateTime.now().toString(); return r; }

    private boolean visible(Table t) { return "Alles".equals(activeZone) || activeZone.equals(t.zone); }
    private String displayStatusAt(Table t) { if (t.isOutOfService) return "buiten gebruik"; Reservation r = linkedReservation(t); if (r != null && r.date.equals(selectedDate.toString()) && !closed(r.status)) { LocalTime st = parseTime(r.time, selectedTime); LocalTime end = st.plusMinutes(r.durationMinutes > 0 ? r.durationMinutes : defaultDuration); if (!selectedTime.isBefore(st.minusMinutes(30)) && selectedTime.isBefore(st)) return "gereserveerd"; if (!selectedTime.isBefore(st) && selectedTime.isBefore(end)) return r.status.equals("verwacht") || r.status.equals("aanwezig") ? "gereserveerd" : r.status; } return t.status; }
    private int statusColor(String s) { if ("vrij".equals(s)) return GREEN; if ("gereserveerd".equals(s)) return Color.rgb(238, 238, 232); if ("bijna vrij".equals(s) || "dessert".equals(s)) return ORANGE; if ("bezet".equals(s) || "zit".equals(s) || "hoofdgerecht".equals(s)) return RED; if ("rekening".equals(s)) return BLUE; if ("buiten gebruik".equals(s)) return DARK_GREY; if ("risico".equals(s)) return RISK_RED; return GREEN; }
    private int reservationColor(String s) { if ("verwacht".equals(s)) return WARM; if ("aanwezig".equals(s)) return GREEN; if ("zit".equals(s) || "hoofdgerecht".equals(s)) return RED; if ("dessert".equals(s)) return ORANGE; if ("rekening".equals(s)) return BLUE; if (closed(s)) return DARK_GREY; return WARM; }
    private String displayStatus(String s) { if (s == null || s.isEmpty()) return "vrij"; return s.substring(0,1).toUpperCase(Locale.ROOT) + s.substring(1); }
    private String labelForTable(Table t, String st) { Reservation r = linkedReservation(t); if (r != null && !closed(r.status)) return r.time + " " + shortText(r.name, 10); if ("rekening".equals(st) || "bijna vrij".equals(st) || "buiten gebruik".equals(st)) return displayStatus(st); return ""; }
    private boolean isRound(Table t) { return t.shape.contains("ronde") || t.shape.contains("rond"); }
    private boolean zoneOk(String tableZone, String pref) { return pref == null || pref.isEmpty() || "Geen voorkeur".equals(pref) || tableZone.equals(pref); }
    private boolean freeAt(Table t, String date, LocalTime start, int duration, String ignoreId) { if (t.isOutOfService) return false; LocalTime end = start.plusMinutes(duration); for (Reservation r : reservations) { if (r.id.equals(ignoreId) || !r.date.equals(date) || closed(r.status)) continue; if (!r.assignedTableIds.contains(t.id)) continue; LocalTime rs = parseTime(r.time, start); LocalTime re = rs.plusMinutes(r.durationMinutes > 0 ? r.durationMinutes : defaultDuration); if (start.isBefore(re) && end.isAfter(rs)) return false; } if (date.equals(LocalDate.now().toString()) && Math.abs(Duration.between(LocalTime.now(), start).toMinutes()) < 45) return "vrij".equals(t.status) || "gereserveerd".equals(t.status); return true; }
    private boolean closed(String s) { return "vertrokken".equals(s) || "geannuleerd".equals(s) || "no-show".equals(s); }
    private boolean doubleBooked(Reservation base, String tableId) { LocalTime s = parseTime(base.time, selectedTime), e = s.plusMinutes(base.durationMinutes > 0 ? base.durationMinutes : defaultDuration); for (Reservation r : reservations) if (!r.id.equals(base.id) && r.date.equals(base.date) && !closed(r.status) && r.assignedTableIds.contains(tableId)) { LocalTime rs = parseTime(r.time, s), re = rs.plusMinutes(r.durationMinutes > 0 ? r.durationMinutes : defaultDuration); if (s.isBefore(re) && e.isAfter(rs)) return true; } return false; }
    private int peakAround(String date, LocalTime time) { int n=0; for (Reservation r : reservations) if (r.date.equals(date) && !closed(r.status)) { long diff = Math.abs(Duration.between(time, parseTime(r.time, time)).toMinutes()); if (diff <= 15) n++; } return n; }
    private String laterSuggestion(Reservation r) { LocalTime base = parseTime(r.time, selectedTime); for (int d : new int[]{-30,30,-60,60,75}) { Reservation p = r.copy(); p.time = base.plusMinutes(d).format(TIME_FMT); if (suggest(p, false).best != null) return "om " + p.time + " kan het mogelijk wel"; } return "vol op dit moment"; }
    private String phoneAnswer(Reservation r, Assignment a) { if (a.best != null) return "Ja hoor, wij hebben om " + r.time + " plek voor " + r.partySize + " personen " + zoneText(a.best.zone) + ". Beste tafel: " + a.best.label + "."; return "Om " + r.time + " zitten we vol voor " + r.partySize + " personen. " + a.riskText + "."; }
    private String zoneText(String z) { return "Binnen".equals(z) ? "binnen" : "op " + z.toLowerCase(Locale.ROOT); }
    private String appendRisk(String old, String add) { return old == null || old.isEmpty() ? add : old + "; " + add; }

    private int todayReservations() { int n=0; for (Reservation r: reservations) if (r.date.equals(selectedDate.toString()) && !closed(r.status)) n++; return n; }
    private int todayGuests() { int n=0; for (Reservation r: reservations) if (r.date.equals(selectedDate.toString()) && !closed(r.status)) n += r.partySize; return n; }
    private int freeNow() { int n=0; for (Table t: tables) if ("vrij".equals(displayStatusAt(t))) n++; return n; }
    private int freeSoon() { int n=0; for (Table t: tables) if ("bijna vrij".equals(t.status) || "dessert".equals(displayStatusAt(t)) || "rekening".equals(displayStatusAt(t))) n++; return n; }
    private int countStatus(String status) { int n=0; for (Table t: tables) if (status.equals(t.status) || status.equals(displayStatusAt(t))) n++; return n; }
    private int seatedGuests() { int n=0; for (Reservation r: reservations) if (r.date.equals(selectedDate.toString()) && ("zit".equals(r.status) || "hoofdgerecht".equals(r.status) || "dessert".equals(r.status) || "rekening".equals(r.status))) n += r.partySize; return n; }
    private int riskColor() { for (Risk r: risks) if ("hoog".equals(r.severity)) return RISK_RED; return risks.isEmpty() ? GREEN : ORANGE; }
    private String riskTitle() { for (Risk r: risks) if ("hoog".equals(r.severity)) return "Hoog risico"; return risks.isEmpty() ? "Laag risico" : "Middel risico"; }

    private ArrayList<Table> groupFor(Table t) { ArrayList<Table> g = new ArrayList<>(); g.add(t); for (String id : t.linkedTableIds) { Table x = tableById(id); if (x != null && !g.contains(x)) g.add(x); } return g; }
    private ArrayList<String> idsOf(Collection<Table> group) { ArrayList<String> ids = new ArrayList<>(); for (Table t : group) ids.add(t.id); return ids; }
    private ArrayList<String> one(String id) { ArrayList<String> ids = new ArrayList<>(); ids.add(id); return ids; }
    private String groupKey(ArrayList<Table> g) { ArrayList<String> ids = idsOf(g); Collections.sort(ids); return join(ids, "+"); }
    private int totalCapacity(ArrayList<Table> group) { int n=0; for (Table t: group) n += t.capacity; return n; }
    private String tableNames(Collection<String> ids) { ArrayList<String> names = new ArrayList<>(); for (String id: ids) names.add(tableNumber(id)); return join(names, " + "); }
    private String tableNumber(String id) { Table t = tableById(id); return t == null ? "?" : t.number; }
    private Table tableById(String id) { for (Table t: tables) if (t.id.equals(id)) return t; return null; }
    private Table firstSelected() { for (String id: selectedTableIds) { Table t = tableById(id); if (t != null) return t; } return null; }
    private Reservation linkedReservation(Table t) { for (Reservation r: reservations) if (r.id.equals(t.reservationId) || r.assignedTableIds.contains(t.id)) return r; return null; }
    private void releaseReservationTables(Reservation r) { for (Table t: tables) if (r.id.equals(t.reservationId)) { t.reservationId = ""; if ("gereserveerd".equals(t.status)) t.status = "vrij"; } }
    private void releaseAllTables() { for (Table t: tables) { t.reservationId = ""; if ("gereserveerd".equals(t.status)) t.status = "vrij"; } }
    private void markReservationTables(Reservation r, String status) { for (String id: r.assignedTableIds) { Table t = tableById(id); if (t != null) t.status = status; } }
    private void markGroup(ArrayList<Table> group, String status) { for (Table t: group) { t.status = status; t.isOutOfService = "buiten gebruik".equals(status); } }
    private void copyNote(ArrayList<Table> group, EditText note) { for (Table t: group) t.note = note.getText().toString().trim(); }
    private void renameTable(String old, String nr) { for (Reservation r: reservations) { } }
    private void forSelected(TableAction action) { for (String id: selectedTableIds) { Table t = tableById(id); if (t != null) action.run(t); } }
    private String nextNumber() { int max=0; for (Table t: tables) try { max = Math.max(max, Integer.parseInt(t.number.replaceAll("[^0-9]", ""))); } catch(Exception ignored) {} return "" + (max + 1); }
    private String zoneForNewTable() { return "Alles".equals(activeZone) ? "Binnen" : activeZone; }
    private void snap(Table t) { t.x = clamp(Math.round(t.x / 5f) * 5, 5, 95); t.y = clamp(Math.round(t.y / 5f) * 5, 8, 95); }

    private LinearLayout panel() { LinearLayout p = column(); p.setPadding(dp(12), dp(12), dp(12), dp(12)); p.setBackground(bg(Color.WHITE, LINE, 1, 8)); return p; }
    private TextView info(String s, int color) { TextView v = text(s, 16, INK, false); v.setPadding(dp(12), dp(12), dp(12), dp(12)); v.setBackground(bg(Color.WHITE, color, 1, 8)); return v; }
    private LinearLayout stepper(String label, int value, IntSave save) { LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL); r.addView(text(label, 16, INK, true), new LinearLayout.LayoutParams(0, -2, 1)); Button m=smallButton("-", Color.WHITE, DARK_GREEN), p=smallButton("+", Color.WHITE, DARK_GREEN); TextView val=text(""+value, 18, INK, true); val.setGravity(Gravity.CENTER); m.setOnClickListener(v -> save.save(value-1)); p.setOnClickListener(v -> save.save(value+1)); r.addView(m, fixed(52,48)); r.addView(val, fixed(58,48)); r.addView(p, fixed(52,48)); return r; }
    private LinearLayout choice(String label, String[] values, String selected, TextSave save) { LinearLayout box = column(); box.addView(text(label, 16, INK, true)); Spinner sp = spinner(values, selected); sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){ public void onItemSelected(AdapterView<?> p, View v, int pos, long id){ String s=values[pos]; if (!s.equals(selected)) save.save(s); } public void onNothingSelected(AdapterView<?> p){} }); box.addView(sp); return box; }
    private Spinner spinner(String[] values, String selected) { Spinner s = new Spinner(this); s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values)); for (int i=0;i<values.length;i++) if (values[i].equals(selected)) s.setSelection(i); return s; }
    private Button button(String label, int bgColor, int fg) { Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(15); b.setTypeface(Typeface.DEFAULT_BOLD); b.setTextColor(fg); b.setMinHeight(dp(54)); b.setPadding(dp(8), dp(5), dp(8), dp(5)); b.setBackground(bg(bgColor, bgColor == Color.WHITE ? LINE : bgColor, 1, 8)); return b; }
    private Button smallButton(String label, int bgColor, int fg) { Button b = button(label, bgColor, fg); b.setTextSize(13); b.setMinHeight(dp(46)); return b; }
    private EditText field(String hint, String value) { EditText e = new EditText(this); e.setHint(hint); e.setText(value); e.setTextSize(18); e.setSingleLine(false); return e; }
    private TextView text(String s, int sp, int color, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); return v; }
    private TextView pill(String s, int color) { TextView v = text(s, 13, Color.WHITE, true); v.setGravity(Gravity.CENTER); v.setPadding(dp(10), dp(7), dp(10), dp(7)); v.setBackground(bg(color, color, 0, 18)); return v; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setBaselineAligned(false); return l; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private GradientDrawable bg(int fill, int stroke, int sw, int radius) { GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radius)); if (sw > 0) g.setStroke(dp(sw), stroke); return g; }
    private LinearLayout.LayoutParams weight() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.setMargins(dp(3), dp(3), dp(3), dp(3)); return p; }
    private LinearLayout.LayoutParams full() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, dp(5), 0, dp(5)); return p; }
    private LinearLayout.LayoutParams fixed(int w, int h) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(w), dp(h)); p.setMargins(dp(3), dp(3), dp(3), dp(3)); return p; }
    private View gap(int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return v; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private int number(EditText e, int fallback) { return parseInt(e.getText().toString().trim(), fallback); }
    private int parseInt(String s, int fallback) { try { return Integer.parseInt(s.trim()); } catch(Exception e) { return fallback; } }
    private String value(EditText e, String fallback) { String s = e.getText().toString().trim(); return s.isEmpty() ? fallback : s; }
    private String safe(String s, String fallback) { return s == null || s.isEmpty() ? fallback : s; }
    private String shortText(String s, int max) { s = safe(s, ""); return s.length() <= max ? s : s.substring(0, max); }
    private String join(Collection<String> items, String sep) { StringBuilder b = new StringBuilder(); int i=0; for (String s: items) { if (i++>0) b.append(sep); b.append(s); } return b.toString(); }
    private String normalizeTime(String s, String fallback) { String c = s.trim().replace('.', ':'); try { if (c.contains(":")) { String[] p = c.split(":"); return LocalTime.of(Integer.parseInt(p[0]), Integer.parseInt(p[1])).format(TIME_FMT); } if (!c.isEmpty()) return LocalTime.of(Integer.parseInt(c), 0).format(TIME_FMT); } catch(Exception ignored) {} return fallback; }
    private LocalTime parseTime(String s, LocalTime fallback) { try { return LocalTime.parse(normalizeTime(s, fallback.format(TIME_FMT)), TIME_FMT); } catch(Exception e) { return fallback; } }
    private static LocalTime roundedNow() { LocalTime t = LocalTime.now(); int m = ((t.getMinute() + 14) / 15) * 15; return t.withMinute(0).withSecond(0).withNano(0).plusMinutes(m); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    static class Table {
        String id, number, zone, shape, status = "vrij", reservationId = "", note = ""; int x, y, width, height, capacity; boolean isOutOfService; ArrayList<String> linkedTableIds = new ArrayList<>();
        Table(String id, String number, String zone, int x, int y, int width, int height, String shape, int capacity) { this.id=id; this.number=number; this.zone=zone; this.x=x; this.y=y; this.width=width; this.height=height; this.shape=shape; this.capacity=capacity; }
        void clearService() { status = "vrij"; reservationId = ""; note = ""; isOutOfService = false; }
        JSONObject json() { JSONObject j = new JSONObject(); try { j.put("id", id); j.put("number", number); j.put("zone", zone); j.put("x", x); j.put("y", y); j.put("width", width); j.put("height", height); j.put("shape", shape); j.put("capacity", capacity); j.put("status", status); j.put("reservationId", reservationId); j.put("notes", note); j.put("isOutOfService", isOutOfService); JSONArray a = new JSONArray(); for (String s: linkedTableIds) a.put(s); j.put("linkedTableIds", a); } catch(Exception ignored) {} return j; }
        static Table from(JSONObject j) { String number = j.optString("number", j.optString("nr", j.optString("name", "?"))); Table t = new Table(j.optString("id", "table-" + number), number, j.optString("zone", "Binnen"), j.optInt("x", 50), j.optInt("y", 50), Math.max(70, j.optInt("width", j.optInt("breed", 88))), Math.max(70, j.optInt("height", j.optInt("hoog", 78))), j.optString("shape", j.optString("vorm", "vierkante tafel")), j.optInt("capacity", j.optInt("cap", 4))); t.status = migrateStatus(j.optString("status", "vrij")); t.reservationId = j.optString("reservationId", j.optString("resId", "")); t.note = j.optString("notes", j.optString("notitie", "")); t.isOutOfService = j.optBoolean("isOutOfService", "buiten gebruik".equals(t.status)); JSONArray a = j.optJSONArray("linkedTableIds"); if (a == null) a = j.optJSONArray("tafels"); if (a != null) for (int i=0;i<a.length();i++) t.linkedTableIds.add(a.optString(i)); return t; }
        static String migrateStatus(String s) { if ("VRIJ".equals(s) || "FREE".equals(s)) return "vrij"; if ("BEZET".equals(s) || "OCCUPIED".equals(s)) return "bezet"; if ("BIJNA".equals(s) || "SOON".equals(s)) return "bijna vrij"; if ("REKENING".equals(s) || "BILL".equals(s)) return "rekening"; if ("GEBLOKKEERD".equals(s) || "BLOCKED".equals(s)) return "buiten gebruik"; if ("VERWACHT".equals(s)) return "gereserveerd"; return s == null || s.isEmpty() ? "vrij" : s.toLowerCase(Locale.ROOT); }
    }

    static class Reservation {
        String id = UUID.randomUUID().toString(), name = "", phone = "", date = LocalDate.now().toString(), time = "", preferredZone = "Geen voorkeur", status = "verwacht", notes = "", source = "handmatig", createdAt = ""; int partySize = 2, durationMinutes = 120; ArrayList<String> assignedTableIds = new ArrayList<>();
        Reservation copy() { Reservation r = new Reservation(); r.id=id; r.name=name; r.phone=phone; r.date=date; r.time=time; r.preferredZone=preferredZone; r.status=status; r.notes=notes; r.source=source; r.createdAt=createdAt; r.partySize=partySize; r.durationMinutes=durationMinutes; r.assignedTableIds.addAll(assignedTableIds); return r; }
        JSONObject json() { JSONObject j = new JSONObject(); try { j.put("id", id); j.put("name", name); j.put("phone", phone); j.put("date", date); j.put("time", time); j.put("partySize", partySize); j.put("durationMinutes", durationMinutes); j.put("preferredZone", preferredZone); JSONArray a = new JSONArray(); for (String s: assignedTableIds) a.put(s); j.put("assignedTableIds", a); j.put("status", status); j.put("notes", notes); j.put("source", source); j.put("createdAt", createdAt); } catch(Exception ignored) {} return j; }
        static Reservation from(JSONObject j) { Reservation r = new Reservation(); r.id = j.optString("id", UUID.randomUUID().toString()); r.name = j.optString("name", j.optString("naam", "")); r.phone = j.optString("phone", ""); r.date = j.optString("date", LocalDate.now().toString()); r.time = j.optString("time", j.optString("tijd", "")); r.partySize = j.optInt("partySize", j.optInt("personen", j.optInt("people", 2))); r.durationMinutes = j.optInt("durationMinutes", 120); r.preferredZone = j.optString("preferredZone", j.optString("voorkeur", "Geen voorkeur")); r.status = migrateStatus(j.optString("status", "verwacht")); r.notes = j.optString("notes", j.optString("notitie", j.optString("note", ""))); r.source = j.optString("source", "handmatig"); r.createdAt = j.optString("createdAt", ""); JSONArray a = j.optJSONArray("assignedTableIds"); if (a == null) a = j.optJSONArray("tafels"); if (a != null) for (int i=0;i<a.length();i++) r.assignedTableIds.add(a.optString(i)); return r; }
        static String migrateStatus(String s) { if ("Verwacht".equals(s)) return "verwacht"; if ("Geplaatst".equals(s)) return "zit"; if ("Geannuleerd".equals(s)) return "geannuleerd"; if ("No-show".equals(s)) return "no-show"; return s == null || s.isEmpty() ? "verwacht" : s.toLowerCase(Locale.ROOT); }
    }

    static class Risk { String id = UUID.randomUUID().toString(), type, severity, message, relatedReservationId = ""; ArrayList<String> relatedTableIds = new ArrayList<>(); Risk(String type, String severity, String message) { this.type=type; this.severity=severity; this.message=message; } String severityLabel() { return "hoog".equals(severity) ? "Hoog risico" : "middel".equals(severity) ? "Middel risico" : "Laag risico"; } int color() { return "hoog".equals(severity) ? RISK_RED : "middel".equals(severity) ? ORANGE : GREEN; } }
    static class Candidate { ArrayList<String> tableIds; int capacity; String label, zone; Candidate(ArrayList<String> tableIds, int capacity, String label, String zone) { this.tableIds=tableIds; this.capacity=capacity; this.label=label; this.zone=zone; } }
    static class Assignment { Candidate best; String bestText = "geen voorstel", riskText = ""; ArrayList<String> alternatives = new ArrayList<>(); }
    interface IntSave { void save(int v); } interface TextSave { void save(String s); } interface TableAction { void run(Table t); }
}
