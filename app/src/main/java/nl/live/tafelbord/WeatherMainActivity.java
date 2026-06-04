package nl.live.tafelbord;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Locale;

public class WeatherMainActivity extends LiveGmailMainActivity {
    static final String K_WEATHER_LOCATION = "weather_location";
    static final String K_WEATHER_LAT = "weather_lat";
    static final String K_WEATHER_LON = "weather_lon";
    static final String K_WEATHER_LAST_SYNC = "weather_last_sync";
    static final String K_WEATHER_TEMP = "weather_temp";
    static final String K_WEATHER_RAIN_NOW = "weather_rain_now";
    static final String K_WEATHER_RAIN_2H = "weather_rain_2h";
    static final String K_WEATHER_RAIN_4H = "weather_rain_4h";
    static final String K_WEATHER_WIND = "weather_wind";
    static final String K_WEATHER_GUST = "weather_gust";
    static final String K_WEATHER_DRY_HOURS = "weather_dry_hours";
    static final String K_WEATHER_LEVEL = "weather_level";
    static final String K_WEATHER_ADVICE = "weather_advice";
    static final String K_WEATHER_BEST_TIME = "weather_best_time";
    static final String K_WEATHER_LOG = "weather_log";
    static final long WEATHER_SYNC_INTERVAL_MS = 15 * 60 * 1000L;
    private final Handler weatherHandler = new Handler(Looper.getMainLooper());
    private boolean weatherTimerStarted = false;

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        ensureDefaultWeatherLocation();
        startWeatherAutoSync();
    }

    @Override void render() {
        root = col();
        root.setBackgroundColor(BG);
        root.setPadding(dp(14), dp(14), dp(14), 0);
        root.addView(header());
        if ("map".equals(screen)) root.addView(zoneTabs());
        content = col();
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        if (!"staff".equals(screen) && !"email".equals(screen) && !"tasks".equals(screen) && !"weather".equals(screen)) root.addView(addReservationBar(), full());
        root.addView(nav());
        if ("weather".equals(screen)) weatherScreen();
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
        Button weather = btn("Terrasweer", weatherColor(), Color.WHITE);
        weather.setOnClickListener(v -> { screen = "weather"; render(); fetchWeatherAsync(false); });
        Button tasksBtn = btn("Taken", ORANGE, Color.WHITE);
        tasksBtn.setOnClickListener(v -> { screen = "tasks"; taskTab = "Opening"; render(); });
        Button mailBtn = btn("Mail Sync", GREEN, Color.WHITE);
        mailBtn.setOnClickListener(v -> { screen = "email"; render(); });
        Button phoneBtn = btn("Telefoonmodus", BLACK, Color.WHITE);
        phoneBtn.setOnClickListener(v -> { screen = "phone"; render(); });
        Button staffBtn = btn("Personeel vandaag", BLUE, Color.WHITE);
        staffBtn.setOnClickListener(v -> { screen = "staff"; render(); });
        Button mode = btn(edit ? "Service modus aanzetten" : "Bewerk modus aanzetten", edit ? ORANGE : BLACK, Color.WHITE);
        mode.setOnClickListener(v -> { edit = !edit; selected.clear(); render(); });
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
        p.addView(metric("Bijna klaar gasten", statusGuests("Bijna klaar"), ORANGE));
        p.addView(metric("Aankomst komende 30 minuten", arrivalNext30(), RED));
        p.addView(metric("Controle nodig", controlNeededCount(), ORANGE));
        p.addView(riskView(dayAdvice("19:00", 0, "Alles", "")));
        list.addView(p, full());
        list.addView(weatherTile(), full());
        list.addView(wixMailTile(), full());
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    void weatherScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = col();
        scroll.addView(list);
        list.addView(weatherTile(), full());

        LinearLayout details = panel();
        details.addView(txt("Details", 22, INK, true));
        details.addView(metric("Temperatuur", Math.round(weatherFloat(K_WEATHER_TEMP)), weatherColor()));
        details.addView(metric("Regen nu mm", Math.round(weatherFloat(K_WEATHER_RAIN_NOW)), rainColor(weatherFloat(K_WEATHER_RAIN_NOW), 1f)));
        details.addView(metric("Regenkans 2 uur", Math.round(weatherFloat(K_WEATHER_RAIN_2H)), rainChanceColor(weatherFloat(K_WEATHER_RAIN_2H))));
        details.addView(metric("Regen komende 4 uur mm", Math.round(weatherFloat(K_WEATHER_RAIN_4H)), rainColor(weatherFloat(K_WEATHER_RAIN_4H), 2f)));
        details.addView(metric("Wind km/u", Math.round(weatherFloat(K_WEATHER_WIND)), windColor(weatherFloat(K_WEATHER_WIND))));
        details.addView(metric("Windstoten km/u", Math.round(weatherFloat(K_WEATHER_GUST)), windColor(weatherFloat(K_WEATHER_GUST))));
        details.addView(metric("Droge uren", prefs.getInt(K_WEATHER_DRY_HOURS, 0), prefs.getInt(K_WEATHER_DRY_HOURS, 0) >= 3 ? GREEN : ORANGE));
        details.addView(txt("Beste moment: " + prefs.getString(K_WEATHER_BEST_TIME, "nog onbekend"), 16, BLUE, true));
        list.addView(details, full());

        LinearLayout actions = panel();
        Button refresh = btn("Nu weer verversen", GREEN, Color.WHITE);
        refresh.setOnClickListener(v -> fetchWeatherAsync(true));
        Button location = btn("Locatie instellen", BLUE, Color.WHITE);
        location.setOnClickListener(v -> weatherLocationDialog());
        actions.addView(refresh, full());
        actions.addView(location, full());
        actions.addView(txt("Automatische controle: elke 15 minuten zolang de app open is. Bron: Open-Meteo live verwachting.", 14, MUTED, false));
        list.addView(actions, full());

        LinearLayout log = panel();
        log.addView(txt("Weerlog", 22, INK, true));
        ArrayList<String> items = weatherLog();
        if (items.isEmpty()) log.addView(txt("Nog geen weercontrole uitgevoerd.", 15, MUTED, false));
        for (String item : items) log.addView(txt(item, 15, item.contains("niet") || item.contains("Regen") ? ORANGE : INK, true));
        list.addView(log, full());
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    LinearLayout weatherTile() {
        LinearLayout p = panel();
        p.setBackground(bg(Color.WHITE, weatherColor(), 1, 18));
        p.addView(txt("Terrasweer", 22, INK, true));
        p.addView(txt(prefs.getString(K_WEATHER_LOCATION, "De Peperboom, Veere"), 15, MUTED, false));
        p.addView(txt("Laatste check: " + prefs.getString(K_WEATHER_LAST_SYNC, "nog niet"), 14, MUTED, false));
        p.addView(txt(prefs.getString(K_WEATHER_LEVEL, "Nog geen advies"), 24, weatherColor(), true));
        p.addView(txt(prefs.getString(K_WEATHER_ADVICE, "Tik op Nu weer verversen."), 17, INK, true));
        p.addView(metric("Regenkans 2 uur", Math.round(weatherFloat(K_WEATHER_RAIN_2H)), rainChanceColor(weatherFloat(K_WEATHER_RAIN_2H))));
        p.addView(metric("Windstoten", Math.round(weatherFloat(K_WEATHER_GUST)), windColor(weatherFloat(K_WEATHER_GUST))));
        Button open = btn("Open Terrasweer", weatherColor(), Color.WHITE);
        open.setOnClickListener(v -> { screen = "weather"; render(); fetchWeatherAsync(false); });
        p.addView(open, full());
        return p;
    }

    void weatherLocationDialog() {
        LinearLayout box = col();
        box.setPadding(dp(8), 0, dp(8), 0);
        EditText name = field("Naam locatie", prefs.getString(K_WEATHER_LOCATION, "De Peperboom, Veere"));
        EditText lat = field("Latitude", prefs.getString(K_WEATHER_LAT, "51.5483"));
        EditText lon = field("Longitude", prefs.getString(K_WEATHER_LON, "3.6667"));
        lat.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        lon.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        box.addView(txt("Locatie terrasweer", 18, INK, true));
        box.addView(txt("Standaard staat dit op De Peperboom in Veere. Pas dit alleen aan als de locatie niet klopt.", 15, MUTED, false));
        box.addView(name);
        box.addView(lat);
        box.addView(lon);
        new AlertDialog.Builder(this)
                .setTitle("Locatie instellen")
                .setView(box)
                .setNegativeButton("Annuleren", null)
                .setPositiveButton("Opslaan", (dialog, which) -> {
                    prefs.edit()
                            .putString(K_WEATHER_LOCATION, val(name, "De Peperboom, Veere"))
                            .putString(K_WEATHER_LAT, val(lat, "51.5483"))
                            .putString(K_WEATHER_LON, val(lon, "3.6667"))
                            .apply();
                    fetchWeatherAsync(true);
                    render();
                })
                .show();
    }

    void startWeatherAutoSync() {
        if (weatherTimerStarted) return;
        weatherTimerStarted = true;
        weatherHandler.postDelayed(new Runnable() {
            @Override public void run() {
                fetchWeatherAsync(false);
                weatherHandler.postDelayed(this, WEATHER_SYNC_INTERVAL_MS);
            }
        }, 2000);
    }

    void fetchWeatherAsync(boolean showToast) {
        new Thread(() -> {
            try {
                WeatherResult result = fetchWeather();
                saveWeather(result);
                runOnUiThread(() -> {
                    if (showToast) Toast.makeText(this, result.level + ": " + result.shortAdvice, Toast.LENGTH_LONG).show();
                    if ("weather".equals(screen) || "dash".equals(screen)) render();
                });
            } catch (Exception e) {
                addWeatherLog("Weer ophalen mislukt: " + e.getMessage());
                runOnUiThread(() -> {
                    if (showToast) Toast.makeText(this, "Weer ophalen mislukt", Toast.LENGTH_LONG).show();
                    if ("weather".equals(screen)) render();
                });
            }
        }).start();
    }

    WeatherResult fetchWeather() throws Exception {
        ensureDefaultWeatherLocation();
        String lat = prefs.getString(K_WEATHER_LAT, "51.5483");
        String lon = prefs.getString(K_WEATHER_LON, "3.6667");
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + encWeather(lat) +
                "&longitude=" + encWeather(lon) +
                "&current=temperature_2m,precipitation,wind_speed_10m,wind_gusts_10m,weather_code" +
                "&hourly=temperature_2m,precipitation_probability,precipitation,wind_speed_10m,wind_gusts_10m" +
                "&forecast_days=1&timezone=Europe%2FAmsterdam";
        JSONObject json = new JSONObject(httpGet(url));
        JSONObject current = json.optJSONObject("current");
        JSONObject hourly = json.optJSONObject("hourly");
        WeatherResult r = new WeatherResult();
        if (current != null) {
            r.temp = (float) current.optDouble("temperature_2m", 0);
            r.rainNow = (float) current.optDouble("precipitation", 0);
            r.wind = (float) current.optDouble("wind_speed_10m", 0);
            r.gust = (float) current.optDouble("wind_gusts_10m", 0);
        }
        if (hourly != null) readHourlyWeather(hourly, r);
        calculateWeatherAdvice(r);
        return r;
    }

    void readHourlyWeather(JSONObject hourly, WeatherResult r) {
        JSONArray prob = hourly.optJSONArray("precipitation_probability");
        JSONArray rain = hourly.optJSONArray("precipitation");
        JSONArray gust = hourly.optJSONArray("wind_gusts_10m");
        JSONArray temp = hourly.optJSONArray("temperature_2m");
        JSONArray time = hourly.optJSONArray("time");
        int start = Math.max(0, Math.min(LocalTime.now().getHour(), prob == null ? 0 : prob.length() - 1));
        float max2 = 0, max4 = 0, rain4 = 0, maxGust = r.gust;
        int dry = 0;
        String best = "nog onbekend";
        float bestScore = -999;
        for (int i = start; i < Math.min(start + 8, prob == null ? 0 : prob.length()); i++) {
            float p = (float) prob.optDouble(i, 0);
            float mm = rain == null ? 0 : (float) rain.optDouble(i, 0);
            float g = gust == null ? 0 : (float) gust.optDouble(i, 0);
            float t = temp == null ? r.temp : (float) temp.optDouble(i, r.temp);
            if (i < start + 2) max2 = Math.max(max2, p);
            if (i < start + 4) {
                max4 = Math.max(max4, p);
                rain4 += mm;
            }
            maxGust = Math.max(maxGust, g);
            if (p < 30 && mm < 0.2 && g < 35 && t >= 15) dry++;
            float score = 100 - p - (mm * 25) - Math.max(0, g - 25) - Math.max(0, 16 - t) * 4;
            if (score > bestScore) {
                bestScore = score;
                best = time == null ? "komende uren" : cleanHour(time.optString(i, "komende uren"));
            }
        }
        r.rainChance2h = max2;
        r.rainChance4h = max4;
        r.rain4h = rain4;
        r.gust = maxGust;
        r.dryHours = dry;
        r.bestTime = best;
    }

    void calculateWeatherAdvice(WeatherResult r) {
        if (r.temp < 8 || r.gust >= 50 || r.rainChance2h >= 70 || r.rain4h >= 2.0f) {
            r.level = "Rood: terras liever dicht";
            r.shortAdvice = "Laat gasten binnen zitten of beperk terras sterk.";
        } else if (r.temp < 14 || r.gust >= 35 || r.rainChance2h >= 40 || r.rain4h >= 0.5f) {
            r.level = "Oranje: terras met opletten";
            r.shortAdvice = "Terras kan, maar houd regen en wind in de gaten.";
        } else {
            r.level = "Groen: goed terrasweer";
            r.shortAdvice = "Terras openzetten en buiten actief aanbieden.";
        }
        if (r.dryHours == 0) r.shortAdvice += " Geen droog uur verwacht in de komende uren.";
        else r.shortAdvice += " Droge uren verwacht: " + r.dryHours + ".";
    }

    void saveWeather(WeatherResult r) {
        prefs.edit()
                .putString(K_WEATHER_TEMP, String.valueOf(r.temp))
                .putString(K_WEATHER_RAIN_NOW, String.valueOf(r.rainNow))
                .putString(K_WEATHER_RAIN_2H, String.valueOf(r.rainChance2h))
                .putString(K_WEATHER_RAIN_4H, String.valueOf(r.rain4h))
                .putString(K_WEATHER_WIND, String.valueOf(r.wind))
                .putString(K_WEATHER_GUST, String.valueOf(r.gust))
                .putInt(K_WEATHER_DRY_HOURS, r.dryHours)
                .putString(K_WEATHER_LEVEL, r.level)
                .putString(K_WEATHER_ADVICE, r.shortAdvice)
                .putString(K_WEATHER_BEST_TIME, r.bestTime)
                .putString(K_WEATHER_LAST_SYNC, LocalDateTime.now().toString().replace('T', ' '))
                .apply();
        addWeatherLog(r.level + " - " + r.shortAdvice);
    }

    void ensureDefaultWeatherLocation() {
        if (prefs.getString(K_WEATHER_LAT, "").isEmpty()) {
            prefs.edit()
                    .putString(K_WEATHER_LOCATION, "De Peperboom, Kapellestraat 11, Veere")
                    .putString(K_WEATHER_LAT, "51.5483")
                    .putString(K_WEATHER_LON, "3.6667")
                    .apply();
        }
    }

    String httpGet(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        InputStream stream = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) builder.append(line);
        reader.close();
        return builder.toString();
    }

    void addWeatherLog(String message) {
        ArrayList<String> items = weatherLog();
        items.add(0, LocalDateTime.now().toString().replace('T', ' ') + " - " + message);
        while (items.size() > 20) items.remove(items.size() - 1);
        JSONArray arr = new JSONArray();
        for (String item : items) arr.put(item);
        prefs.edit().putString(K_WEATHER_LOG, arr.toString()).apply();
    }

    ArrayList<String> weatherLog() {
        ArrayList<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(K_WEATHER_LOG, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.optString(i));
        } catch (Exception ignored) {
        }
        return out;
    }

    int weatherColor() {
        String level = prefs.getString(K_WEATHER_LEVEL, "");
        if (level.startsWith("Rood")) return RED;
        if (level.startsWith("Oranje")) return ORANGE;
        if (level.startsWith("Groen")) return GREEN;
        return BLUE;
    }

    int rainChanceColor(float value) {
        if (value >= 70) return RED;
        if (value >= 40) return ORANGE;
        return GREEN;
    }

    int rainColor(float value, float redLimit) {
        if (value >= redLimit) return RED;
        if (value >= 0.5f) return ORANGE;
        return GREEN;
    }

    int windColor(float value) {
        if (value >= 50) return RED;
        if (value >= 35) return ORANGE;
        return GREEN;
    }

    float weatherFloat(String key) {
        try {
            return Float.parseFloat(prefs.getString(key, "0"));
        } catch (Exception ignored) {
            return 0;
        }
    }

    String cleanHour(String raw) {
        int t = raw.indexOf('T');
        if (t >= 0 && raw.length() >= t + 6) return raw.substring(t + 1, t + 6);
        return raw;
    }

    String encWeather(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }

    static class WeatherResult {
        float temp;
        float rainNow;
        float rainChance2h;
        float rainChance4h;
        float rain4h;
        float wind;
        float gust;
        int dryHours;
        String level = "Nog geen advies";
        String shortAdvice = "Tik op Nu weer verversen.";
        String bestTime = "nog onbekend";
    }
}
