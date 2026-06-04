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
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;

public class TaskMainActivity extends EmailMainActivity {
    static final String K_TASK_DEFS = "task_defs";
    static final String K_TASK_DONE = "task_done";
    static final String K_TASK_HISTORY = "task_history";
    static final String K_TASK_EMPLOYEE = "task_employee";
    static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("nl-NL"));
    String taskTab = "Opening";

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
        if (!"staff".equals(screen) && !"email".equals(screen) && !"tasks".equals(screen)) root.addView(addReservationBar(), full());
        root.addView(nav());
        if ("tasks".equals(screen)) tasksScreen();
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
        Button tasksBtn = btn("Taken", ORANGE, Color.WHITE);
        tasksBtn.setOnClickListener(v -> { screen = "tasks"; taskTab = "Opening"; render(); });
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
        p.addView(tasksBtn, full());
        p.addView(phoneBtn, full());
        p.addView(staffBtn, full());
        p.addView(emailBtn, full());
        p.addView(mode, full());
        p.addView(reset, full());
        content.addView(p, full());
    }

    void tasksScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = col();
        scroll.addView(list);
        list.addView(taskHeader(), full());
        list.addView(taskTabs(), full());
        if ("Historie".equals(taskTab)) historyView(list);
        else taskListView(list, taskTab);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    LinearLayout taskHeader() {
        LinearLayout p = panel();
        p.addView(txt("Checklists & Taken", 25, INK, true));
        p.addView(txt("Vandaag: " + today(), 15, MUTED, false));
        p.addView(taskProgressLine("Opening"));
        p.addView(taskProgressLine("Sluiting"));
        p.addView(taskProgressLine("Extra"));
        String warning = taskWarning();
        if (!warning.isEmpty()) p.addView(info(warning, warning.contains("Sluit") ? RED : ORANGE));
        LinearLayout buttons = row();
        Button add = btn("+ Taak", BLACK, Color.WHITE);
        add.setOnClickListener(v -> taskDialog(new TaskDef(taskTab.equals("Historie") ? "Extra" : taskTab), true));
        Button who = btn("Medewerker", BLUE, Color.WHITE);
        who.setOnClickListener(v -> employeeDialog());
        buttons.addView(add, w());
        buttons.addView(who, w());
        p.addView(buttons);
        p.addView(txt("Afvinken slaat medewerker, datum en tijd op.", 14, MUTED, false));
        return p;
    }

    TextView taskProgressLine(String category) {
        int done = doneCount(category);
        int total = activeTasks(category).size();
        int color = done == total && total > 0 ? GREEN : total == 0 ? MUTED : ORANGE;
        return txt(category + ": " + done + "/" + total + " klaar", 17, color, true);
    }

    LinearLayout taskTabs() {
        LinearLayout tabs = row();
        tabs.setBackground(bg(Color.WHITE, LINE, 1, 18));
        tabs.addView(taskTabButton("Opening"), w());
        tabs.addView(taskTabButton("Sluiting"), w());
        tabs.addView(taskTabButton("Extra"), w());
        tabs.addView(taskTabButton("Historie"), w());
        return tabs;
    }

    Button taskTabButton(String label) {
        boolean on = taskTab.equals(label);
        Button b = btn(label, on ? BLACK : Color.TRANSPARENT, on ? Color.WHITE : INK);
        b.setMinHeight(dp(58));
        b.setOnClickListener(v -> { taskTab = label; render(); });
        return b;
    }

    void taskListView(LinearLayout list, String category) {
        ArrayList<TaskDef> tasks = activeTasks(category);
        Collections.sort(tasks, (a, b) -> {
            boolean ad = isDone(a.id), bd = isDone(b.id);
            if (ad != bd) return ad ? 1 : -1;
            return priorityScore(b.priority) - priorityScore(a.priority);
        });
        if (tasks.isEmpty()) list.addView(info("Geen taken in deze lijst.", MUTED), full());
        for (TaskDef task : tasks) list.addView(taskCard(task), full());
    }

    LinearLayout taskCard(TaskDef task) {
        boolean done = isDone(task.id);
        TaskDone d = doneInfo(task.id);
        int border = done ? GREEN : "hoog".equals(task.priority) ? RED : ORANGE;
        LinearLayout p = panel();
        p.setBackground(bg(Color.WHITE, border, 1, 18));
        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(txt(done ? "✓" : "○", 28, done ? GREEN : border, true), fix(42, 50));
        LinearLayout title = col();
        title.addView(txt(task.name, 19, INK, true));
        title.addView(txt(task.category + " - " + task.zone + " - prioriteit " + task.priority, 14, MUTED, false));
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        p.addView(top);
        if (!task.description.isEmpty()) p.addView(txt(task.description, 15, MUTED, false));
        p.addView(txt("Herhaling: " + task.repeat + " - toegewezen aan: " + task.assignee, 14, MUTED, false));
        if (done && d != null) p.addView(txt("Gedaan door " + d.employee + " om " + d.time, 15, GREEN, true));
        LinearLayout actions = row();
        Button check = btn(done ? "Terugzetten" : "Afvinken", done ? ORANGE : GREEN, Color.WHITE);
        check.setOnClickListener(v -> toggleTask(task));
        Button editBtn = btn("Wijzig", BLUE, Color.WHITE);
        editBtn.setOnClickListener(v -> taskDialog(task, false));
        Button delete = btn("Verwijder", RED, Color.WHITE);
        delete.setOnClickListener(v -> deleteTask(task));
        actions.addView(check, w());
        actions.addView(editBtn, w());
        actions.addView(delete, w());
        p.addView(actions);
        return p;
    }

    void historyView(LinearLayout list) {
        LinearLayout p = panel();
        p.addView(txt("Historie", 24, INK, true));
        ArrayList<TaskDone> history = history();
        if (history.isEmpty()) p.addView(txt("Nog geen taken afgevinkt.", 15, MUTED, false));
        int shown = 0;
        for (TaskDone item : history) {
            p.addView(txt(item.date + " " + item.time + " - " + item.employee + " - " + item.name + " (" + item.category + ")", 15, INK, true));
            if (++shown >= 40) break;
        }
        list.addView(p, full());

        LinearLayout open = panel();
        open.addView(txt("Vandaag nog open", 22, INK, true));
        int openCount = 0;
        for (TaskDef t : definitions()) {
            if (t.deleted || !appliesToday(t) || isDone(t.id)) continue;
            open.addView(txt(t.category + " - " + t.name, 15, "hoog".equals(t.priority) ? RED : ORANGE, true));
            openCount++;
        }
        if (openCount == 0) open.addView(txt("Alles is gedaan.", 15, GREEN, true));
        list.addView(open, full());
    }

    void employeeDialog() {
        LinearLayout box = col();
        box.setPadding(dp(8), 0, dp(8), 0);
        EditText name = field("Naam medewerker", employee());
        box.addView(txt("Wie vinkt taken af?", 18, INK, true));
        box.addView(name);
        new AlertDialog.Builder(this)
                .setTitle("Medewerker")
                .setView(box)
                .setNegativeButton("Annuleren", null)
                .setPositiveButton("Opslaan", (dialog, which) -> {
                    prefs.edit().putString(K_TASK_EMPLOYEE, val(name, "Medewerker")).apply();
                    render();
                })
                .show();
    }

    void taskDialog(TaskDef task, boolean fresh) {
        LinearLayout box = col();
        box.setPadding(dp(8), 0, dp(8), 0);
        EditText name = field("Taaknaam", task.name);
        EditText desc = field("Omschrijving", task.description);
        Spinner cat = spin(new String[]{"Opening", "Sluiting", "Extra"}, task.category);
        Spinner zone = spin(new String[]{"algemeen", "binnen", "serre", "terras", "keuken", "bar"}, task.zone);
        Spinner priority = spin(new String[]{"laag", "normaal", "hoog"}, task.priority);
        Spinner repeat = spin(new String[]{"eenmalig", "dagelijks", "wekelijks", "specifieke dagen"}, task.repeat);
        EditText assignee = field("Toegewezen aan", task.assignee);
        EditText order = field("Volgorde", String.valueOf(task.order));
        order.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(name);
        box.addView(desc);
        box.addView(txt("Categorie", 14, INK, true)); box.addView(cat);
        box.addView(txt("Locatie/zone", 14, INK, true)); box.addView(zone);
        box.addView(txt("Prioriteit", 14, INK, true)); box.addView(priority);
        box.addView(txt("Herhaling", 14, INK, true)); box.addView(repeat);
        box.addView(assignee);
        box.addView(order);
        new AlertDialog.Builder(this)
                .setTitle(fresh ? "Taak toevoegen" : "Taak bewerken")
                .setView(box)
                .setNegativeButton("Annuleren", null)
                .setPositiveButton("Opslaan", (dialog, which) -> {
                    task.name = val(name, "Nieuwe taak");
                    task.description = val(desc, "");
                    task.category = cat.getSelectedItem().toString();
                    task.zone = zone.getSelectedItem().toString();
                    task.priority = priority.getSelectedItem().toString();
                    task.repeat = repeat.getSelectedItem().toString();
                    task.assignee = val(assignee, "iedereen");
                    task.order = num(order, task.order);
                    ArrayList<TaskDef> defs = definitions();
                    if (fresh) defs.add(task);
                    saveDefinitions(defs);
                    taskTab = task.category;
                    render();
                })
                .show();
    }

    void toggleTask(TaskDef task) {
        if (isDone(task.id)) {
            HashSet<String> keys = doneKeys();
            keys.remove(doneKey(task.id));
            saveDoneKeys(keys);
            render();
            return;
        }
        TaskDone done = new TaskDone();
        done.taskId = task.id;
        done.name = task.name;
        done.category = task.category;
        done.date = today();
        done.time = LocalTime.now().format(TIME_FMT);
        done.employee = employee();
        addHistory(done);
        HashSet<String> keys = doneKeys();
        keys.add(doneKey(task.id));
        saveDoneKeys(keys);
        Toast.makeText(this, task.name + " afgevinkt door " + done.employee, Toast.LENGTH_SHORT).show();
        render();
    }

    void deleteTask(TaskDef task) {
        new AlertDialog.Builder(this)
                .setTitle("Taak verwijderen")
                .setMessage("Verwijderen uit toekomstige checklists? Historie blijft bewaard.")
                .setNegativeButton("Annuleren", null)
                .setPositiveButton("Verwijder", (dialog, which) -> {
                    ArrayList<TaskDef> defs = definitions();
                    for (TaskDef d : defs) if (d.id.equals(task.id)) d.deleted = true;
                    saveDefinitions(defs);
                    render();
                })
                .show();
    }

    String taskWarning() {
        LocalTime now = LocalTime.now();
        int openingOpen = activeTasks("Opening").size() - doneCount("Opening");
        int closingOpen = activeTasks("Sluiting").size() - doneCount("Sluiting");
        if (now.isAfter(LocalTime.of(11, 30)) && openingOpen > 0) return "Nog " + openingOpen + " openingstaken open.";
        if (now.isAfter(LocalTime.of(22, 0)) && closingOpen > 0) return "Sluitchecklist nog niet compleet.";
        return "";
    }

    ArrayList<TaskDef> activeTasks(String category) {
        ArrayList<TaskDef> out = new ArrayList<>();
        for (TaskDef d : definitions()) {
            if (d.deleted || !d.category.equals(category) || !appliesToday(d)) continue;
            out.add(d);
        }
        Collections.sort(out, (a, b) -> a.order - b.order);
        return out;
    }

    boolean appliesToday(TaskDef task) {
        return !"eenmalig".equals(task.repeat) || !isDone(task.id);
    }

    int doneCount(String category) {
        int count = 0;
        for (TaskDef t : activeTasks(category)) if (isDone(t.id)) count++;
        return count;
    }

    boolean isDone(String taskId) {
        return doneKeys().contains(doneKey(taskId));
    }

    TaskDone doneInfo(String taskId) {
        String key = doneKey(taskId);
        for (TaskDone d : history()) if ((d.date + "|" + d.taskId).equals(key)) return d;
        return null;
    }

    String doneKey(String taskId) {
        return today() + "|" + taskId;
    }

    String today() {
        return LocalDate.now().format(DAY_FMT);
    }

    String employee() {
        return prefs.getString(K_TASK_EMPLOYEE, "Medewerker");
    }

    int priorityScore(String p) {
        if ("hoog".equals(p)) return 3;
        if ("normaal".equals(p)) return 2;
        return 1;
    }

    ArrayList<TaskDef> definitions() {
        ArrayList<TaskDef> defs = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(K_TASK_DEFS, ""));
            for (int i = 0; i < arr.length(); i++) defs.add(TaskDef.from(arr.getJSONObject(i)));
        } catch (Exception ignored) {
        }
        if (defs.isEmpty()) {
            seedTasks(defs);
            saveDefinitions(defs);
        }
        return defs;
    }

    void saveDefinitions(ArrayList<TaskDef> defs) {
        JSONArray arr = new JSONArray();
        for (TaskDef d : defs) arr.put(d.json());
        prefs.edit().putString(K_TASK_DEFS, arr.toString()).apply();
    }

    HashSet<String> doneKeys() {
        HashSet<String> keys = new HashSet<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(K_TASK_DONE, "[]"));
            for (int i = 0; i < arr.length(); i++) keys.add(arr.optString(i));
        } catch (Exception ignored) {
        }
        return keys;
    }

    void saveDoneKeys(HashSet<String> keys) {
        JSONArray arr = new JSONArray();
        for (String key : keys) arr.put(key);
        prefs.edit().putString(K_TASK_DONE, arr.toString()).apply();
    }

    ArrayList<TaskDone> history() {
        ArrayList<TaskDone> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(K_TASK_HISTORY, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(TaskDone.from(arr.getJSONObject(i)));
        } catch (Exception ignored) {
        }
        Collections.sort(out, (a, b) -> (b.date + b.time).compareTo(a.date + a.time));
        return out;
    }

    void addHistory(TaskDone done) {
        ArrayList<TaskDone> items = history();
        items.add(0, done);
        JSONArray arr = new JSONArray();
        for (TaskDone item : items) arr.put(item.json());
        prefs.edit().putString(K_TASK_HISTORY, arr.toString()).apply();
    }

    void seedTasks(ArrayList<TaskDef> defs) {
        String[] opening = {"Terras klaarzetten", "Tafels schoonmaken", "Toiletten controleren", "Kaarsen / verlichting aan", "Muziek aan", "Kassalade controleren", "Koffiemachine aan", "Koelingen controleren", "Menukaarten klaarleggen", "Reserveringen van vandaag controleren", "Speciale wensen/allergieën controleren", "Personeelsbezetting controleren"};
        String[] closing = {"Tafels afruimen", "Terras opruimen", "Toiletten controleren", "Koelingen dicht/controleren", "Koffiemachine schoonmaken", "Vaatwasser leeg", "Bar schoonmaken", "Kassa tellen", "Pinbonnen controleren", "Lichten uit", "Muziek uit", "Deuren op slot", "Alarm controleren"};
        String[] extra = {"Parasols schoonmaken", "Sligro-bestelling controleren", "Bloemen water geven", "Fles wijn klaarzetten voor verjaardag", "Terrasverwarming klaarzetten", "Menukaart aanpassen"};
        int order = 1;
        for (String s : opening) defs.add(new TaskDef(s, "Opening", zoneFor(s), priorityFor(s), order++));
        order = 1;
        for (String s : closing) defs.add(new TaskDef(s, "Sluiting", zoneFor(s), priorityFor(s), order++));
        order = 1;
        for (String s : extra) defs.add(new TaskDef(s, "Extra", zoneFor(s), priorityFor(s), order++));
    }

    String zoneFor(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("terras") || n.contains("parasol")) return "terras";
        if (n.contains("koffie") || n.contains("koeling") || n.contains("vaatwasser")) return "keuken";
        if (n.contains("bar") || n.contains("kassa") || n.contains("pin")) return "bar";
        if (n.contains("toilet")) return "algemeen";
        return "algemeen";
    }

    String priorityFor(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("allerg") || n.contains("alarm") || n.contains("deuren") || n.contains("kassa") || n.contains("koeling")) return "hoog";
        return "normaal";
    }

    static class TaskDef {
        String id = UUID.randomUUID().toString();
        String name = "";
        String description = "";
        String category = "Extra";
        String zone = "algemeen";
        String priority = "normaal";
        String repeat = "dagelijks";
        String assignee = "iedereen";
        boolean deleted = false;
        int order = 100;

        TaskDef(String category) { this.category = category; }
        TaskDef(String name, String category, String zone, String priority, int order) {
            this.name = name;
            this.category = category;
            this.zone = zone;
            this.priority = priority;
            this.order = order;
        }

        JSONObject json() {
            JSONObject j = new JSONObject();
            try {
                j.put("id", id); j.put("name", name); j.put("description", description);
                j.put("category", category); j.put("zone", zone); j.put("priority", priority);
                j.put("repeat", repeat); j.put("assignee", assignee); j.put("deleted", deleted);
                j.put("order", order);
            } catch (Exception ignored) {}
            return j;
        }

        static TaskDef from(JSONObject j) {
            TaskDef t = new TaskDef(j.optString("category", "Extra"));
            t.id = j.optString("id", UUID.randomUUID().toString());
            t.name = j.optString("name", "Taak");
            t.description = j.optString("description", "");
            t.zone = j.optString("zone", "algemeen");
            t.priority = j.optString("priority", "normaal");
            t.repeat = j.optString("repeat", "dagelijks");
            t.assignee = j.optString("assignee", "iedereen");
            t.deleted = j.optBoolean("deleted", false);
            t.order = j.optInt("order", 100);
            return t;
        }
    }

    static class TaskDone {
        String taskId = "";
        String name = "";
        String category = "";
        String date = "";
        String time = "";
        String employee = "";

        JSONObject json() {
            JSONObject j = new JSONObject();
            try {
                j.put("taskId", taskId); j.put("name", name); j.put("category", category);
                j.put("date", date); j.put("time", time); j.put("employee", employee);
            } catch (Exception ignored) {}
            return j;
        }

        static TaskDone from(JSONObject j) {
            TaskDone d = new TaskDone();
            d.taskId = j.optString("taskId", "");
            d.name = j.optString("name", "");
            d.category = j.optString("category", "");
            d.date = j.optString("date", "");
            d.time = j.optString("time", "");
            d.employee = j.optString("employee", "");
            return d;
        }
    }
}
