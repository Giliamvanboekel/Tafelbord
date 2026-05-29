package nl.live.tafelbord;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class MainActivity extends Activity {
    private static final String PREFS = "live_tafelbord_de_peperboom";
    private static final String TABLES_KEY = "tables";
    private static final String SETTINGS_KEY = "settings";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("nl-NL"));
    private static final int GREEN = Color.rgb(31, 139, 83);
    private static final int RED = Color.rgb(198, 50, 45);
    private static final int ORANGE = Color.rgb(224, 132, 38);
    private static final int BLUE = Color.rgb(38, 99, 174);
    private static final int GREY = Color.rgb(108, 116, 125);
    private static final int DARK_RED = Color.rgb(122, 25, 26);
    private static final int DARK_GREEN = Color.rgb(18, 79, 58);
    private static final int CREAM = Color.rgb(250, 247, 238);
    private static final int INK = Color.rgb(28, 43, 36);

    private final ArrayList<Table> tables = new ArrayList<>();
    private SharedPreferences prefs;
    private LinearLayout content;
    private String screen = "map";
    private String zone = "Alles";
    private String filter = "Alles";
    private boolean editMode;
    private int frontStaff = 3;
    private int kitchenStaff = 2;
    private String kitchenCapacity = "Normaal";
    private String crowdLevel = "Normaal";
    private int callerSize = 4;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadSettings();
        loadTables();
        render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = col();
        root.setPadding(dp(12), dp(12), dp(12), dp(16));
        root.setBackgroundColor(CREAM);
        scroll.addView(root);

        LinearLayout head = row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = col();
        titles.addView(label("De Peperboom", 14, DARK_GREEN, true));
        titles.addView(label("Live Tafelbord", 29, INK, true));
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(pill(editMode ? "Bewerkmodus" : "Service", editMode ? ORANGE : DARK_GREEN));
        root.addView(head);
        root.addView(gap(10));
        root.addView(summary());
        root.addView(gap(8));
        root.addView(nav());
        root.addView(gap(8));
        content = col();
        root.addView(content);

        if ("risk".equals(screen)) renderRisk();
        else if ("free".equals(screen)) renderAvailability();
        else renderMap();
        setContentView(scroll);
    }

    private LinearLayout summary() {
        LinearLayout all = col();
        LinearLayout r1 = row();
        r1.addView(tile("Vrij", count(Status.FREE), GREEN), weight());
        r1.addView(tile("Bezet", count(Status.OCCUPIED), RED), weight());
        r1.addView(tile("Bijna vrij", count(Status.SOON), ORANGE), weight());
        LinearLayout r2 = row();
        r2.addView(tile("Rekening", count(Status.BILL), BLUE), weight());
        r2.addView(tile("Geblokkeerd", count(Status.BLOCKED), GREY), weight());
        r2.addView(tile("Let op", warningCount(), DARK_RED), weight());
        all.addView(r1); all.addView(r2);
        return all;
    }

    private TextView tile(String text, int amount, int color) {
        TextView v = label(amount + "\n" + text, 13, color, true);
        v.setGravity(Gravity.CENTER);
        v.setMinHeight(dp(58));
        v.setBackground(bg(Color.WHITE, color, 1, 8));
        return v;
    }

    private LinearLayout nav() {
        LinearLayout box = col();
        LinearLayout tabs = row();
        tabs.addView(tab("Plattegrond", "map"), weight());
        tabs.addView(tab("Risico", "risk"), weight());
        tabs.addView(tab("Beschikbaarheid", "free"), weight());
        box.addView(tabs);
        if ("map".equals(screen)) {
            LinearLayout f1 = row();
            f1.addView(filter("Alles"), weight());
            f1.addView(filter("Binnen"), weight());
            f1.addView(filter("Tuin"), weight());
            box.addView(f1);
            LinearLayout f2 = row();
            f2.addView(filter("Alleen risico"), weight());
            f2.addView(filter("Alleen rekening"), weight());
            f2.addView(filter("Alleen bijna vrij"), weight());
            box.addView(f2);
        }
        return box;
    }

    private Button tab(String title, String target) {
        Button b = btn(title, screen.equals(target) ? DARK_GREEN : Color.WHITE, screen.equals(target) ? Color.WHITE : DARK_GREEN);
        b.setOnClickListener(v -> { screen = target; editMode = false; render(); });
        return b;
    }

    private Button filter(String value) {
        Button b = btn(value, filter.equals(value) ? DARK_GREEN : Color.WHITE, filter.equals(value) ? Color.WHITE : DARK_GREEN);
        b.setTextSize(12);
        b.setOnClickListener(v -> {
            filter = value;
            zone = ("Binnen".equals(value) || "Tuin".equals(value)) ? value : "Alles";
            render();
        });
        return b;
    }

    private void renderMap() {
        LinearLayout zones = row();
        Button inside = btn("Binnen", "Binnen".equals(zone) ? DARK_GREEN : Color.WHITE, "Binnen".equals(zone) ? Color.WHITE : DARK_GREEN);
        inside.setOnClickListener(v -> { zone = "Binnen"; filter = "Binnen"; render(); });
        Button garden = btn("Tuin / buitenterras", "Tuin".equals(zone) ? DARK_GREEN : Color.WHITE, "Tuin".equals(zone) ? Color.WHITE : DARK_GREEN);
        garden.setOnClickListener(v -> { zone = "Tuin"; filter = "Tuin"; render(); });
        zones.addView(inside, weight()); zones.addView(garden, weight());
        content.addView(zones);
        Button edit = btn(editMode ? "Klaar met bewerken" : "Plattegrond bewerken", editMode ? ORANGE : DARK_GREEN, Color.WHITE);
        edit.setOnClickListener(v -> { editMode = !editMode; render(); });
        content.addView(edit, full());
        content.addView(label(editMode ? "Sleep tafels. Tik om nummer, vorm, grootte of personen te wijzigen." : "Tik op een tafel voor acties.", 14, Color.rgb(91, 84, 72), false));
        content.addView(gap(8));
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(bg(Color.rgb(255,253,247), Color.rgb(218,207,185), 1, 10));
        frame.addView(new PlanView(this), new FrameLayout.LayoutParams(-1, dp(530)));
        content.addView(frame, full());
        if (editMode) {
            Button add = btn("Tafel toevoegen", DARK_GREEN, Color.WHITE);
            add.setOnClickListener(v -> editTable(new Table("Nieuw", "Alles".equals(zone) ? "Binnen" : zone, 50, 50, 12, "Rond", 2)));
            content.addView(add, full());
        }
    }

    private void renderRisk() {
        Risk risk = risk();
        int color = risk.level == 2 ? DARK_RED : risk.level == 1 ? ORANGE : GREEN;
        TextView top = label(risk.title, 24, Color.WHITE, true);
        top.setGravity(Gravity.CENTER);
        top.setPadding(dp(14), dp(16), dp(14), dp(16));
        top.setBackground(bg(color, color, 0, 10));
        content.addView(top, full());
        LinearLayout panel = col();
        panel.setPadding(dp(12), dp(12), dp(12), dp(12));
        panel.setBackground(bg(Color.WHITE, Color.rgb(230,223,207), 1, 8));
        panel.addView(stepper("Bediening", frontStaff, n -> { frontStaff = Math.max(1, n); saveSettings(); render(); }));
        panel.addView(stepper("Keuken", kitchenStaff, n -> { kitchenStaff = Math.max(1, n); saveSettings(); render(); }));
        panel.addView(choice("Keukencapaciteit", new String[]{"Laag","Normaal","Hoog"}, kitchenCapacity, s -> { kitchenCapacity = s; saveSettings(); render(); }));
        panel.addView(choice("Drukteverwachting", new String[]{"Rustig","Normaal","Druk","Extreem druk"}, crowdLevel, s -> { crowdLevel = s; saveSettings(); render(); }));
        content.addView(panel, full());
        for (String line : risk.lines) {
            TextView v = label(line, 16, INK, false);
            v.setPadding(dp(12), dp(10), dp(12), dp(10));
            v.setBackground(bg(Color.WHITE, color, 1, 8));
            content.addView(v, full());
        }
    }

    private void renderAvailability() {
        LinearLayout panel = col();
        panel.setPadding(dp(12), dp(12), dp(12), dp(12));
        panel.setBackground(bg(Color.WHITE, Color.rgb(230,223,207), 1, 8));
        panel.addView(label("Beschikbaarheid nu", 24, INK, true));
        panel.addView(stepper("Aantal personen", callerSize, n -> { callerSize = Math.max(1, n); render(); }));
        panel.addView(label("Vrij binnen: " + capacity("Binnen", Status.FREE) + " personen", 18, GREEN, true));
        panel.addView(label("Vrij buiten: " + capacity("Tuin", Status.FREE) + " personen", 18, GREEN, true));
        panel.addView(label("Bijna vrij binnen: " + capacity("Binnen", Status.SOON) + " personen", 16, ORANGE, true));
        panel.addView(label("Bijna vrij buiten: " + capacity("Tuin", Status.SOON) + " personen", 16, ORANGE, true));
        TextView advice = label(phoneAdvice(), 20, Color.WHITE, true);
        advice.setPadding(dp(14), dp(14), dp(14), dp(14));
        advice.setBackground(bg(DARK_GREEN, DARK_GREEN, 0, 10));
        panel.addView(gap(8)); panel.addView(advice);
        content.addView(panel, full());
        for (Table t : tables) if (t.status == Status.FREE || t.status == Status.SOON) {
            TextView row = label("Tafel " + t.name + " - " + t.zone + " - " + t.capacity + " personen - " + t.status.label, 16, INK, false);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(bg(Color.WHITE, t.status.color, 1, 8));
            row.setOnClickListener(v -> service(t));
            content.addView(row, full());
        }
    }

    private LinearLayout stepper(String title, int value, NumberSave save) {
        LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(label(title, 16, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        Button min = btn("-", Color.WHITE, DARK_GREEN); Button plus = btn("+", Color.WHITE, DARK_GREEN);
        TextView num = label(String.valueOf(value), 18, INK, true); num.setGravity(Gravity.CENTER);
        min.setOnClickListener(v -> save.save(value - 1)); plus.setOnClickListener(v -> save.save(value + 1));
        r.addView(min, small()); r.addView(num, small()); r.addView(plus, small());
        return r;
    }

    private LinearLayout choice(String title, String[] values, String selected, TextSave save) {
        LinearLayout c = col(); c.addView(label(title, 16, INK, true));
        Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
        for (int i = 0; i < values.length; i++) if (values[i].equals(selected)) sp.setSelection(i);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { if (!values[pos].equals(selected)) save.save(values[pos]); }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        c.addView(sp); return c;
    }

    private void service(Table t) {
        LinearLayout box = col(); box.setPadding(dp(8), 0, dp(8), 0);
        box.addView(label("Tafel " + t.name + " - " + t.zone, 22, INK, true));
        box.addView(label(t.capacity + " personen - " + t.status.label, 15, t.status.color, true));
        EditText guest = field("Gastnaam", t.guest);
        EditText people = field("Aantal personen", t.party > 0 ? String.valueOf(t.party) : ""); people.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText time = field("Tijd / reservering", t.time);
        EditText note = field("Notitie", t.note);
        box.addView(guest); box.addView(people); box.addView(time); box.addView(note);
        for (Status s : Status.values()) {
            Button b = btn(s.action, s.color, Color.WHITE);
            b.setOnClickListener(v -> { apply(t, guest, people, time, note); t.status = s; if (s == Status.FREE) t.clear(); if (s == Status.OCCUPIED && t.time.isEmpty()) t.time = now(); saveTables(); render(); });
            box.addView(b, full());
        }
        new AlertDialog.Builder(this).setView(box).setNegativeButton("Terug", null).setPositiveButton("Opslaan", (d,w) -> { apply(t, guest, people, time, note); saveTables(); render(); }).show();
    }

    private void editTable(Table t) {
        boolean fresh = !tables.contains(t);
        LinearLayout box = col(); box.setPadding(dp(8), 0, dp(8), 0);
        EditText name = field("Tafelnummer", t.name);
        EditText cap = field("Aantal personen", String.valueOf(t.capacity)); cap.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText size = field("Grootte", String.valueOf(t.size)); size.setInputType(InputType.TYPE_CLASS_NUMBER);
        Spinner shape = spinner(new String[]{"Rond","Vierkant","Rechthoek"}, t.shape);
        Spinner zonePick = spinner(new String[]{"Binnen","Tuin"}, t.zone);
        box.addView(name); box.addView(cap); box.addView(size); box.addView(label("Vorm", 14, INK, true)); box.addView(shape); box.addView(label("Zone", 14, INK, true)); box.addView(zonePick);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(fresh ? "Tafel toevoegen" : "Tafel bewerken").setView(box).setNegativeButton("Terug", null).setPositiveButton("Opslaan", (d,w) -> {
            t.name = val(name, "Nieuw"); t.capacity = Math.max(1, parse(cap, t.capacity)); t.size = clamp(parse(size, t.size), 7, 22); t.shape = shape.getSelectedItem().toString(); t.zone = zonePick.getSelectedItem().toString(); if (fresh) tables.add(t); saveTables(); render();
        });
        if (!fresh) b.setNeutralButton("Verwijderen", (d,w) -> { tables.remove(t); saveTables(); render(); });
        b.show();
    }

    private Spinner spinner(String[] values, String selected) {
        Spinner s = new Spinner(this); s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
        for (int i = 0; i < values.length; i++) if (values[i].equals(selected)) s.setSelection(i);
        return s;
    }

    private void apply(Table t, EditText guest, EditText people, EditText time, EditText note) {
        t.guest = guest.getText().toString().trim();
        t.party = parse(people, t.party);
        t.time = time.getText().toString().trim().replace("-", "");
        t.note = note.getText().toString().trim();
    }

    private class PlanView extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); Table drag; boolean moved;
        PlanView(Context c) { super(c); setMinimumHeight(dp(530)); }
        protected void onDraw(Canvas c) {
            p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(255,253,247)); c.drawRect(0,0,getWidth(),getHeight(),p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); p.setColor(Color.rgb(225,216,196)); c.drawRoundRect(dp(8),dp(8),getWidth()-dp(8),getHeight()-dp(8),dp(14),dp(14),p);
            p.setStyle(Paint.Style.FILL); p.setTextSize(dp(15)); p.setTypeface(Typeface.DEFAULT_BOLD); p.setColor(DARK_GREEN);
            c.drawText("Alles".equals(zone) ? "Binnen en Tuin / buitenterras" : ("Tuin".equals(zone) ? "Tuin / buitenterras" : "Binnen"), dp(18), dp(30), p);
            p.setColor(Color.rgb(239,234,219)); c.drawRect(dp(20), dp(42), getWidth()-dp(20), dp(46), p); c.drawRect(dp(20), getHeight()-dp(44), getWidth()-dp(20), getHeight()-dp(40), p);
            for (Table t : tables) if (visible(t)) drawTable(c, t);
        }
        private void drawTable(Canvas c, Table t) {
            float cx = t.x * getWidth()/100f, cy = t.y * getHeight()/100f, s = dp(t.size), w = "Rechthoek".equals(t.shape) ? s*1.55f : s, h = "Rechthoek".equals(t.shape) ? s*.95f : s;
            RectF r = new RectF(cx-w, cy-h, cx+w, cy+h); p.setStyle(Paint.Style.FILL); p.setColor(isRisk(t) ? DARK_RED : t.status.color);
            if ("Rond".equals(t.shape)) c.drawOval(r,p); else c.drawRoundRect(r,dp(6),dp(6),p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); p.setColor(Color.WHITE); if ("Rond".equals(t.shape)) c.drawOval(r,p); else c.drawRoundRect(r,dp(6),dp(6),p);
            p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(dp(t.name.length()>3?11:14)); p.setColor(Color.WHITE); c.drawText(t.name,cx,cy+dp(5),p);
            if (t.status == Status.BILL || t.status == Status.SOON || isRisk(t)) { p.setTextSize(dp(10)); c.drawText(t.status.shortName,cx,cy+h+dp(15),p); }
            p.setTextAlign(Paint.Align.LEFT);
        }
        public boolean onTouchEvent(MotionEvent e) {
            Table hit = hit(e.getX(), e.getY());
            if (e.getAction()==MotionEvent.ACTION_DOWN) { drag = hit; moved = false; return hit != null; }
            if (e.getAction()==MotionEvent.ACTION_MOVE && editMode && drag != null) { drag.x = clamp(Math.round(e.getX()*100/Math.max(1,getWidth())),5,95); drag.y = clamp(Math.round(e.getY()*100/Math.max(1,getHeight())),5,95); moved = true; invalidate(); return true; }
            if (e.getAction()==MotionEvent.ACTION_UP && drag != null) { Table t = drag; drag = null; if (editMode) { saveTables(); if (!moved) editTable(t); } else service(t); return true; }
            return true;
        }
        private Table hit(float x, float y) { for (int i=tables.size()-1;i>=0;i--) { Table t=tables.get(i); if (!visible(t)) continue; float cx=t.x*getWidth()/100f, cy=t.y*getHeight()/100f, s=dp(t.size+8), w="Rechthoek".equals(t.shape)?s*1.7f:s; if (x>=cx-w && x<=cx+w && y>=cy-s && y<=cy+s) return t; } return null; }
    }

    private boolean visible(Table t) {
        if (!"Alles".equals(zone) && !t.zone.equals(zone)) return false;
        if ("Alleen risico".equals(filter)) return isRisk(t);
        if ("Alleen rekening".equals(filter)) return t.status == Status.BILL;
        if ("Alleen bijna vrij".equals(filter)) return t.status == Status.SOON;
        return true;
    }
    private boolean isRisk(Table t) { return t.status == Status.LATE || longSeated(t) || reservationSoon(t); }
    private boolean reservationSoon(Table t) { long m = minutesUntil(t.time); return t.status == Status.FREE && m >= 0 && m <= 30; }
    private boolean longSeated(Table t) { return (t.status == Status.OCCUPIED || t.status == Status.LATE) && minutesSince(t.time) >= 135; }
    private int count(Status s) { int n=0; for (Table t:tables) if (t.status==s) n++; return n; }
    private int warningCount() { int n=0; for (Table t:tables) if (isRisk(t)) n++; return n; }
    private int activeGuests() { int n=0; for (Table t:tables) if (t.status==Status.OCCUPIED||t.status==Status.SOON||t.status==Status.BILL||t.status==Status.LATE) n += Math.max(t.party, t.capacity); return n; }
    private int capacity(String z, Status s) { int n=0; for (Table t:tables) if (t.zone.equals(z) && t.status==s) n += t.capacity; return n; }
    private boolean tableFor(String z, Status s, int size) { for (Table t:tables) if (t.zone.equals(z)&&t.status==s&&t.capacity>=size) return true; return false; }
    private String phoneAdvice() { if (tableFor("Binnen",Status.FREE,callerSize)&&tableFor("Tuin",Status.FREE,callerSize)) return "Voor " + callerSize + " personen hebben we nu binnen en buiten plek."; if (tableFor("Binnen",Status.FREE,callerSize)) return "Voor " + callerSize + " personen hebben we nu plek binnen. Buiten is waarschijnlijk later mogelijk."; if (tableFor("Tuin",Status.FREE,callerSize)) return "Voor " + callerSize + " personen hebben we nu plek buiten. Binnen is later mogelijk."; if (tableFor("Binnen",Status.SOON,callerSize)||tableFor("Tuin",Status.SOON,callerSize)) return "Voor " + callerSize + " personen is waarschijnlijk over 20 tot 30 minuten plek."; return "Voor " + callerSize + " personen is nu geen goede plek vrij. Vraag om later terug te bellen."; }

    private Risk risk() {
        Risk r = new Risk(); int score = 0, occupied = count(Status.OCCUPIED)+count(Status.LATE), soon = count(Status.SOON), bill = count(Status.BILL), guests = activeGuests();
        if (occupied >= 18) { score+=2; r.lines.add("Veel tafels tegelijk bezet. Neem tijdelijk minder walk-ins aan."); } else if (occupied >= 12) { score++; r.lines.add("Veel bezette tafels. Werk strak per zone."); }
        if (soon >= 5) { score++; r.lines.add("Veel tafels bijna klaar. Zet iemand op afruimen en indekken."); }
        if (bill >= 4) { score++; r.lines.add("Veel tafels op rekening. Voorkant moet afrekenen versnellen."); }
        int soonReservations = 0, lateTables = 0; for (Table t:tables) { if (reservationSoon(t)) soonReservations++; if (longSeated(t)) lateTables++; }
        if (soonReservations >= 3) { score++; r.lines.add("Reserveringen komen bijna aan. Houd passende tafels vrij."); }
        if (lateTables >= 3) { score++; r.lines.add("Tafels zitten lang. Check dessert, koffie of rekening."); }
        if (guests / Math.max(1, frontStaff) > 22) { score+=2; r.lines.add("Te weinig bediening. Zet extra bediening op terras."); } else if (guests / Math.max(1, frontStaff) > 16) { score++; r.lines.add("Bediening staat strak. Verdeel taken duidelijk."); }
        int kitchenLimit = "Hoog".equals(kitchenCapacity) ? 24 : "Laag".equals(kitchenCapacity) ? 12 : 18;
        if (guests / Math.max(1, kitchenStaff) > kitchenLimit) { score+=2; r.lines.add("Keuken loopt risico, beperk grote tafels."); }
        if ("Druk".equals(crowdLevel)) score++; if ("Extreem druk".equals(crowdLevel)) { score+=2; r.lines.add("Piekmoment verwacht tussen reserveringen en walk-ins."); }
        if (r.lines.isEmpty()) { r.lines.add("Rustig beeld. Nieuwe gasten kunnen normaal geplaatst worden."); r.lines.add("Blijf reserveringstafels alvast vrijhouden."); }
        r.level = score >= 5 ? 2 : score >= 2 ? 1 : 0; r.title = r.level==2 ? "Rood: risico op vertraging" : r.level==1 ? "Oranje: opletten" : "Groen: haalbaar"; return r;
    }

    private void loadTables() { tables.clear(); String raw = prefs.getString(TABLES_KEY, ""); if (!raw.isEmpty()) try { JSONArray a = new JSONArray(raw); for (int i=0;i<a.length();i++) tables.add(Table.from(a.getJSONObject(i))); } catch (Exception e) { tables.clear(); } if (tables.isEmpty()) { seed(); saveTables(); } }
    private void saveTables() { JSONArray a = new JSONArray(); for (Table t:tables) a.put(t.json()); prefs.edit().putString(TABLES_KEY, a.toString()).apply(); }
    private void loadSettings() { try { JSONObject j = new JSONObject(prefs.getString(SETTINGS_KEY, "{}")); frontStaff=j.optInt("front",3); kitchenStaff=j.optInt("kitchen",2); kitchenCapacity=j.optString("cap","Normaal"); crowdLevel=j.optString("crowd","Normaal"); } catch(Exception ignored){} }
    private void saveSettings() { JSONObject j = new JSONObject(); try { j.put("front",frontStaff); j.put("kitchen",kitchenStaff); j.put("cap",kitchenCapacity); j.put("crowd",crowdLevel); } catch(Exception ignored){} prefs.edit().putString(SETTINGS_KEY,j.toString()).apply(); }
    private void seed() { String[] inside={"1","2","3","3b","4","5","5b","6","7","8","9","10","11","12","13","14","15","16"}; int[][] pi={{14,16},{30,16},{47,16},{64,16},{82,17},{17,35},{35,35},{53,35},{72,36},{18,55},{36,56},{54,56},{75,57},{16,76},{34,77},{52,77},{70,77},{86,76}}; for(int i=0;i<inside.length;i++) tables.add(new Table(inside[i],"Binnen",pi[i][0],pi[i][1],inside[i].contains("b")?8:10,inside[i].contains("b")?"Rond":"Vierkant",inside[i].contains("b")?2:4)); String[] out={"40","41","41-1","42","43","43-2","44","45","46","47","47-2","48","49","49-2","50","51","52","53","53-2","54","54-2","55","56","57","58"}; int[][] po={{11,14},{26,14},{41,14},{57,14},{75,14},{90,14},{12,31},{29,31},{46,31},{64,31},{82,31},{13,49},{32,49},{49,49},{67,49},{85,49},{13,68},{31,68},{48,68},{66,68},{84,68},{20,86},{42,86},{64,86},{86,86}}; for(int i=0;i<out.length;i++) tables.add(new Table(out[i],"Tuin",po[i][0],po[i][1],out[i].contains("-")?8:10,out[i].contains("-")?"Rond":"Vierkant",out[i].contains("-")?2:4)); }

    private enum Status { FREE("Vrij","Vrijmaken","vrij",GREEN), OCCUPIED("Bezet","Bezet zetten","bezet",RED), SOON("Bijna vrij","Bijna vrij","bijna",ORANGE), BILL("Rekening","Rekening", "rekening",BLUE), BLOCKED("Geblokkeerd","Blokkeren","blok",GREY), LATE("Te laat / risico","Te laat / risico","let op",DARK_RED); final String label, action, shortName; final int color; Status(String l,String a,String s,int c){label=l;action=a;shortName=s;color=c;} static Status from(String s){ for(Status st:values()) if(st.name().equals(s)) return st; if("BILL_REQUESTED".equals(s)) return BILL; if("ALMOST_FREE".equals(s)) return SOON; return FREE; } }
    private static class Table { String name, zone, shape, guest="", time="", note=""; int x,y,size,capacity,party; Status status=Status.FREE; Table(String n,String z,int xx,int yy,int sz,String sh,int cap){name=n;zone=z;x=xx;y=yy;size=sz;shape=sh;capacity=cap;} void clear(){guest="";party=0;time="";note="";} JSONObject json(){ JSONObject j=new JSONObject(); try{j.put("name",name);j.put("zone",zone);j.put("x",x);j.put("y",y);j.put("size",size);j.put("shape",shape);j.put("capacity",capacity);j.put("party",party);j.put("status",status.name());j.put("guest",guest);j.put("time",time);j.put("note",note);}catch(Exception ignored){} return j;} static Table from(JSONObject j){ Table t=new Table(j.optString("name","?"),j.optString("zone","Binnen"),j.optInt("x",50),j.optInt("y",50),j.optInt("size",10),j.optString("shape","Rond"),j.optInt("capacity",2)); t.party=j.optInt("party",j.optInt("partySize",0)); t.status=Status.from(j.optString("status","FREE")); t.guest=j.optString("guest",j.optString("guestName","")); t.time=j.optString("time",j.optString("startTime","")); t.note=j.optString("note",""); return t; } }
    private static class Risk { int level; String title; ArrayList<String> lines = new ArrayList<>(); }
    private interface NumberSave { void save(int n); } private interface TextSave { void save(String s); }

    private LinearLayout col(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setBaselineAligned(false); return l; }
    private TextView label(String s,int sp,int color,boolean bold){ TextView v=new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); if(bold)v.setTypeface(Typeface.DEFAULT_BOLD); return v; }
    private TextView pill(String s,int color){ TextView v=label(s,13,Color.WHITE,true); v.setGravity(Gravity.CENTER); v.setPadding(dp(10),dp(7),dp(10),dp(7)); v.setBackground(bg(color,color,0,18)); return v; }
    private Button btn(String s,int bg,int fg){ Button b=new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT_BOLD); b.setTextColor(fg); b.setMinHeight(dp(48)); b.setBackground(bg(bg,bg==Color.WHITE?Color.rgb(222,214,198):bg,1,8)); return b; }
    private EditText field(String hint,String value){ EditText e=new EditText(this); e.setHint(hint); e.setText(value); e.setTextSize(18); return e; }
    private GradientDrawable bg(int fill,int stroke,int sw,int radius){ GradientDrawable g=new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radius)); if(sw>0)g.setStroke(dp(sw),stroke); return g; }
    private LinearLayout.LayoutParams weight(){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1); p.setMargins(dp(3),dp(3),dp(3),dp(3)); return p; }
    private LinearLayout.LayoutParams full(){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(4),0,dp(4)); return p; }
    private LinearLayout.LayoutParams small(){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(48),dp(48)); p.setMargins(dp(3),dp(3),dp(3),dp(3)); return p; }
    private View gap(int h){ View v=new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h))); return v; }
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
    private int parse(EditText e,int fallback){ try{return Integer.parseInt(e.getText().toString().trim());}catch(Exception ex){return fallback;} }
    private int clamp(int v,int min,int max){ return Math.max(min,Math.min(max,v)); }
    private String val(EditText e,String fallback){ String s=e.getText().toString().trim(); return s.isEmpty()?fallback:s; }
    private String now(){ return LocalTime.now().format(TIME); }
    private long minutesUntil(String s){ try{return ChronoUnit.MINUTES.between(LocalTime.now(),LocalTime.parse(s,TIME));}catch(Exception e){return Long.MAX_VALUE;} }
    private long minutesSince(String s){ try{return ChronoUnit.MINUTES.between(LocalTime.parse(s,TIME),LocalTime.now());}catch(Exception e){return 0;} }
}
