package nl.live.tafelbord;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("nl-NL"));

    private final List<TableEntry> tables = new ArrayList<>();
    private LinearLayout board;
    private String activeZone = "Alles";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        seedTables();
        render();
    }

    private void render() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(244, 247, 246));

        LinearLayout root = vertical();
        root.setPadding(dp(16), dp(18), dp(16), dp(24));
        scrollView.addView(root);

        TextView title = text("Live Tafelbord", 30, Color.rgb(12, 35, 30), true);
        root.addView(title);
        root.addView(text("Service-overzicht voor nu", 15, Color.rgb(82, 98, 94), false));
        root.addView(space(14));

        LinearLayout quick = horizontal();
        quick.setGravity(Gravity.CENTER_VERTICAL);
        addSummary(quick, "Vrij", count(Status.FREE), Color.rgb(22, 163, 74));
        addSummary(quick, "Let op", urgentCount(), Color.rgb(234, 88, 12));
        addSummary(quick, "Bezet", count(Status.OCCUPIED), Color.rgb(220, 38, 38));
        root.addView(quick);
        root.addView(space(14));

        LinearLayout filters = horizontal();
        String[] zones = {"Alles", "Binnen", "Tuin", "Terras"};
        for (String zone : zones) {
            Button button = button(zone, activeZone.equals(zone) ? Color.rgb(15, 118, 110) : Color.WHITE, activeZone.equals(zone) ? Color.WHITE : Color.rgb(15, 118, 110));
            button.setOnClickListener(v -> {
                activeZone = zone;
                render();
            });
            filters.addView(button, buttonWeight());
        }
        root.addView(filters);
        root.addView(space(12));

        board = vertical();
        root.addView(board);
        for (TableEntry table : tables) {
            if ("Alles".equals(activeZone) || table.zone.equals(activeZone)) {
                board.addView(tableCard(table));
                board.addView(space(10));
            }
        }

        setContentView(scrollView);
    }

    private View tableCard(TableEntry table) {
        LinearLayout card = vertical();
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(makeCardBackground(table.status));

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout names = vertical();
        TextView name = text(table.name, 24, Color.rgb(15, 23, 42), true);
        names.addView(name);
        names.addView(text(table.zone + " · " + table.capacity + " personen", 14, Color.rgb(71, 85, 105), false));
        top.addView(names, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView status = pill(table.status.label, table.status.color);
        top.addView(status);
        card.addView(top);

        String guestLine = hasGuest(table) ? table.guestName + " · " + table.partySize + " gasten" : "Geen gast ingepland";
        card.addView(line("Gast", guestLine));
        card.addView(line("Tijd", table.startTime + " - " + table.endTime));
        card.addView(line("Notities", table.notes.isEmpty() ? "Geen bijzonderheden" : table.notes));

        String warning = warningText(table);
        if (!warning.isEmpty()) {
            TextView warningView = text(warning, 15, Color.WHITE, true);
            warningView.setPadding(dp(12), dp(10), dp(12), dp(10));
            warningView.setBackgroundColor(warning.contains("15 min") ? Color.rgb(185, 28, 28) : Color.rgb(234, 88, 12));
            card.addView(space(10));
            card.addView(warningView);
        }

        card.addView(space(12));
        LinearLayout actionsOne = horizontal();
        actionsOne.addView(action("Reservering", () -> editBooking(table, Status.RESERVED)), buttonWeight());
        actionsOne.addView(action("Walk-in", () -> editBooking(table, Status.OCCUPIED)), buttonWeight());
        card.addView(actionsOne);

        LinearLayout actionsTwo = horizontal();
        actionsTwo.addView(action("Geplaatst", () -> updateStatus(table, Status.OCCUPIED)), buttonWeight());
        actionsTwo.addView(action("Rekening", () -> updateStatus(table, Status.BILL_REQUESTED)), buttonWeight());
        card.addView(actionsTwo);

        LinearLayout actionsThree = horizontal();
        actionsThree.addView(action("Bijna vrij", () -> updateStatus(table, Status.ALMOST_FREE)), buttonWeight());
        actionsThree.addView(action("Vrij", () -> markFree(table)), buttonWeight());
        card.addView(actionsThree);

        LinearLayout actionsFour = horizontal();
        actionsFour.addView(action("Blokkeren", () -> updateStatus(table, Status.BLOCKED)), buttonWeight());
        actionsFour.addView(action("Annuleer", () -> markFree(table)), buttonWeight());
        card.addView(actionsFour);

        return card;
    }

    private void editBooking(TableEntry table, Status targetStatus) {
        LinearLayout form = vertical();
        form.setPadding(dp(8), 0, dp(8), 0);

        EditText guest = input("Gastnaam", InputType.TYPE_CLASS_TEXT);
        guest.setText(table.guestName);
        EditText party = input("Aantal gasten", InputType.TYPE_CLASS_NUMBER);
        party.setText(table.partySize > 0 ? String.valueOf(table.partySize) : "");
        EditText start = input("Starttijd (HH:mm)", InputType.TYPE_CLASS_TEXT);
        start.setText(table.startTime);
        EditText end = input("Eindtijd (HH:mm)", InputType.TYPE_CLASS_TEXT);
        end.setText(table.endTime);
        EditText notes = input("Notities", InputType.TYPE_CLASS_TEXT);
        notes.setText(table.notes);

        form.addView(guest);
        form.addView(party);
        form.addView(start);
        form.addView(end);
        form.addView(notes);

        new AlertDialog.Builder(this)
                .setTitle(targetStatus == Status.RESERVED ? "Reservering toevoegen" : "Walk-in toevoegen")
                .setView(form)
                .setNegativeButton("Terug", null)
                .setPositiveButton("Opslaan", (dialog, which) -> {
                    table.status = targetStatus;
                    table.guestName = valueOr(guest, targetStatus == Status.RESERVED ? "Reservering" : "Walk-in");
                    table.partySize = parseInt(valueOr(party, "2"), 2);
                    table.startTime = valueOr(start, nowRounded());
                    table.endTime = valueOr(end, plusMinutes(table.startTime, 90));
                    table.notes = valueOr(notes, "");
                    render();
                })
                .show();
    }

    private void updateStatus(TableEntry table, Status status) {
        table.status = status;
        if (!hasGuest(table) && status != Status.BLOCKED) {
            table.guestName = "Walk-in";
            table.partySize = Math.min(table.capacity, 2);
            table.startTime = nowRounded();
            table.endTime = plusMinutes(table.startTime, 90);
        }
        if (status == Status.BLOCKED && table.notes.isEmpty()) {
            table.notes = "Geblokkeerd voor service";
        }
        render();
    }

    private void markFree(TableEntry table) {
        table.status = Status.FREE;
        table.guestName = "";
        table.partySize = 0;
        table.startTime = "-";
        table.endTime = "-";
        table.notes = "";
        render();
    }

    private void seedTables() {
        if (!tables.isEmpty()) {
            return;
        }
        tables.add(new TableEntry("Tafel 1", "Binnen", 2, Status.FREE, "", 0, "-", "-", ""));
        tables.add(new TableEntry("Tafel 2", "Binnen", 4, Status.OCCUPIED, "Jansen", 4, "18:00", "19:30", "Hoofdgerecht loopt"));
        tables.add(new TableEntry("Tafel 3", "Binnen", 6, Status.RESERVED, "De Vries", 5, "19:45", "21:15", "Kinderstoel"));
        tables.add(new TableEntry("Tafel 8", "Tuin", 2, Status.BILL_REQUESTED, "Smit", 2, "17:45", "19:00", "Rekening gebracht"));
        tables.add(new TableEntry("Tafel 9", "Tuin", 4, Status.ALMOST_FREE, "Bakker", 3, "18:15", "19:15", "Dessert overgeslagen"));
        tables.add(new TableEntry("Tafel 12", "Terras", 4, Status.FREE, "", 0, "-", "-", ""));
        tables.add(new TableEntry("Tafel 14", "Terras", 6, Status.BLOCKED, "", 0, "-", "-", "Niet gebruiken door wind"));
        tables.add(new TableEntry("Tafel 15", "Terras", 2, Status.RESERVED, "Meijer", 2, "20:00", "21:30", "Aan de rand"));
    }

    private void addSummary(LinearLayout parent, String label, int value, int color) {
        LinearLayout box = vertical();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(8), dp(10), dp(8), dp(10));
        box.setBackgroundColor(Color.WHITE);
        box.addView(text(String.valueOf(value), 24, color, true));
        box.addView(text(label, 12, Color.rgb(71, 85, 105), false));
        parent.addView(box, buttonWeight());
    }

    private TextView line(String label, String value) {
        TextView view = text(label + ": " + value, 15, Color.rgb(51, 65, 85), false);
        view.setPadding(0, dp(7), 0, 0);
        return view;
    }

    private Button action(String label, Runnable action) {
        Button button = button(label, Color.rgb(15, 118, 110), Color.WHITE);
        button.setTextSize(13);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private Button button(String label, int background, int foreground) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(foreground);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setMinHeight(dp(48));
        button.setBackgroundColor(background);
        return button;
    }

    private TextView pill(String label, int color) {
        TextView view = text(label, 13, Color.WHITE, true);
        view.setPadding(dp(10), dp(7), dp(10), dp(7));
        view.setGravity(Gravity.CENTER);
        view.setBackgroundColor(color);
        return view;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(true);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private EditText input(String hint, int inputType) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setInputType(inputType);
        return editText;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setBaselineAligned(false);
        return layout;
    }

    private LinearLayout.LayoutParams buttonWeight() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        return params;
    }

    private View space(int height) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return view;
    }

    private android.graphics.drawable.GradientDrawable makeCardBackground(Status status) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(2), status.color);
        return drawable;
    }

    private int count(Status status) {
        int total = 0;
        for (TableEntry table : tables) {
            if (table.status == status) {
                total++;
            }
        }
        return total;
    }

    private int urgentCount() {
        int total = 0;
        for (TableEntry table : tables) {
            if (!warningText(table).isEmpty()) {
                total++;
            }
        }
        return total;
    }

    private String warningText(TableEntry table) {
        if (table.status == Status.FREE || table.status == Status.BLOCKED || "-".equals(table.endTime)) {
            return "";
        }
        long minutes = minutesUntil(table.endTime);
        if (minutes >= 0 && minutes <= 15) {
            return "Moet binnen 15 min vrij zijn";
        }
        if (minutes > 15 && minutes <= 45) {
            return "Bijna vrij binnen 45 min";
        }
        return "";
    }

    private long minutesUntil(String time) {
        try {
            LocalTime end = LocalTime.parse(time, TIME_FORMAT);
            return ChronoUnit.MINUTES.between(LocalTime.now(), end);
        } catch (Exception ignored) {
            return Long.MAX_VALUE;
        }
    }

    private boolean hasGuest(TableEntry table) {
        return table.guestName != null && !table.guestName.trim().isEmpty() && table.partySize > 0;
    }

    private String valueOr(EditText input, String fallback) {
        String value = input.getText().toString().trim();
        return value.isEmpty() ? fallback : value;
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String nowRounded() {
        LocalTime now = LocalTime.now();
        int minute = (now.getMinute() / 15) * 15;
        return now.withMinute(minute).withSecond(0).withNano(0).format(TIME_FORMAT);
    }

    private String plusMinutes(String start, int minutes) {
        try {
            return LocalTime.parse(start, TIME_FORMAT).plusMinutes(minutes).format(TIME_FORMAT);
        } catch (Exception ignored) {
            return LocalTime.now().plusMinutes(minutes).format(TIME_FORMAT);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private enum Status {
        FREE("Vrij", Color.rgb(22, 163, 74)),
        RESERVED("Gereserveerd", Color.rgb(37, 99, 235)),
        OCCUPIED("Bezet", Color.rgb(220, 38, 38)),
        BILL_REQUESTED("Rekening gevraagd", Color.rgb(234, 88, 12)),
        ALMOST_FREE("Bijna vrij", Color.rgb(202, 138, 4)),
        BLOCKED("Geblokkeerd", Color.rgb(71, 85, 105));

        final String label;
        final int color;

        Status(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    private static class TableEntry {
        final String name;
        final String zone;
        final int capacity;
        Status status;
        String guestName;
        int partySize;
        String startTime;
        String endTime;
        String notes;

        TableEntry(String name, String zone, int capacity, Status status, String guestName, int partySize, String startTime, String endTime, String notes) {
            this.name = name;
            this.zone = zone;
            this.capacity = capacity;
            this.status = status;
            this.guestName = guestName;
            this.partySize = partySize;
            this.startTime = startTime;
            this.endTime = endTime;
            this.notes = notes;
        }
    }
}
