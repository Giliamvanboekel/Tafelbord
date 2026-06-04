package nl.live.tafelbord;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyStore;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

public class LiveGmailMainActivity extends GmailMainActivity {
    static final String K_GOOGLE_CLIENT_ID = "google_oauth_client_id";
    static final String K_GMAIL_ACCESS_TOKEN = "gmail_access_token_secure";
    static final String K_GMAIL_REFRESH_TOKEN = "gmail_refresh_token_secure";
    static final String K_GMAIL_EXPIRES_AT = "gmail_expires_at";
    static final String K_GMAIL_DEVICE_CODE = "gmail_device_code";
    static final String KEY_ALIAS = "live_tafelbord_gmail_tokens";
    static final String GMAIL_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
    }

    @Override void emailScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = col();
        scroll.addView(list);

        LinearLayout p = panel();
        p.addView(txt("Mail Sync", 25, INK, true));
        p.addView(txt("Live Gmail lezen via Google OAuth", 15, MUTED, false));
        p.addView(txt(connected() ? "Verbonden: " + account() : "Niet verbonden", 17,
                connected() ? GREEN : ORANGE, true));
        p.addView(txt("OAuth client: " + (clientId().isEmpty() ? "nog niet ingesteld" : "ingesteld"), 15,
                clientId().isEmpty() ? ORANGE : GREEN, true));
        p.addView(txt("Laatste synchronisatie: " + prefs.getString(K_EMAIL_LAST_SYNC, "nog niet"), 15, MUTED, false));
        p.addView(metric("Nieuwe aanvragen", prefs.getInt(K_GMAIL_NEW_REQUESTS, 0), GREEN));
        p.addView(metric("Fouten", prefs.getInt(K_GMAIL_ERRORS, 0), RED));
        p.addView(metric("Controle nodig", controlNeededCount(), ORANGE));
        p.addView(metric("Verwerkte Gmail-berichten", processedIds().size(), BLUE));
        list.addView(p, full());

        LinearLayout actions = panel();
        Button connect = btn(connected() ? "Gmail opnieuw koppelen" : "Gmail koppelen", BLACK, Color.WHITE);
        connect.setOnClickListener(v -> connectGmailAccount());
        Button sync = btn("Nu live synchroniseren", GREEN, Color.WHITE);
        sync.setOnClickListener(v -> syncGmailReservations());
        Button check = btn("Toestemming controleren", BLUE, Color.WHITE);
        check.setOnClickListener(v -> pollSavedDeviceCode());
        Button disconnect = btn("Gmail ontkoppelen", RED, Color.WHITE);
        disconnect.setOnClickListener(v -> disconnectLiveGmail());
        actions.addView(connect, full());
        actions.addView(sync, full());
        actions.addView(check, full());
        actions.addView(disconnect, full());
        actions.addView(txt("De app gebruikt Gmail readonly. Wachtwoorden en volledige mailboxinhoud worden niet opgeslagen; alleen messageId, status en reserveringsgegevens.", 14, MUTED, false));
        list.addView(actions, full());

        addControlNeededCards(list);
        showEmailSyncLog(list);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    @Override LinearLayout wixMailTile() {
        LinearLayout p = panel();
        p.setBackground(bg(Color.WHITE, connected() ? GREEN : ORANGE, 1, 18));
        p.addView(txt("Live Gmail Sync", 22, INK, true));
        p.addView(txt("Account: " + (account().isEmpty() ? "info@vanboekelproperties.com" : account()), 15, MUTED, false));
        p.addView(txt("Laatste sync: " + prefs.getString(K_EMAIL_LAST_SYNC, "nog niet"), 15, MUTED, false));
        p.addView(metric("Nieuwe Wix-aanvragen", prefs.getInt(K_GMAIL_NEW_REQUESTS, 0), GREEN));
        p.addView(metric("Controle nodig", controlNeededCount(), ORANGE));
        p.addView(metric("Fouten", prefs.getInt(K_GMAIL_ERRORS, 0), RED));
        Button sync = btn("Nu live synchroniseren", GREEN, Color.WHITE);
        sync.setOnClickListener(v -> syncGmailReservations());
        p.addView(sync, full());
        return p;
    }

    @Override void connectGmailAccount() {
        LinearLayout box = col();
        box.setPadding(dp(8), 0, dp(8), 0);
        EditText email = field("Gmail-account", account().isEmpty() ? "info@vanboekelproperties.com" : account());
        email.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText client = field("Google OAuth Client ID", clientId());
        box.addView(txt("Gmail OAuth koppelen", 18, INK, true));
        box.addView(txt("Vul de Google OAuth client-ID in. Daarna opent Google toestemming met alleen Gmail-lezen.", 15, MUTED, false));
        box.addView(email);
        box.addView(client);
        new AlertDialog.Builder(this)
                .setTitle("Gmail koppelen")
                .setView(box)
                .setNegativeButton("Annuleren", null)
                .setPositiveButton("Start OAuth", (dialog, which) -> {
                    String mail = val(email, "info@vanboekelproperties.com");
                    String id = val(client, "");
                    prefs.edit().putString(K_EMAIL_ACCOUNT, mail).putString(K_GOOGLE_CLIENT_ID, id).apply();
                    if (id.isEmpty()) {
                        addEmailLog("Fout: Google OAuth client-ID ontbreekt");
                        Toast.makeText(this, "OAuth client-ID ontbreekt", Toast.LENGTH_LONG).show();
                        render();
                        return;
                    }
                    requestDeviceCode(id);
                })
                .show();
    }

    @Override void syncGmailReservations() {
        if (!ensureAccessToken()) {
            connectGmailAccount();
            return;
        }
        int before = processedIds().size();
        int errors = 0;
        int handled = 0;
        for (GmailMessage message : searchWixReservationEmails()) {
            if (processedIds().contains(message.messageId)) continue;
            GmailReservation parsed = parseWixEmail(message);
            if (parsed == null) {
                errors++;
                increase(K_GMAIL_ERRORS);
                addEmailLog("Fout: Gmail-bericht niet te lezen " + message.messageId);
            } else if ("annulering".equals(parsed.type)) {
                cancelReservationFromEmail(parsed);
                handled++;
            } else if ("wijziging".equals(parsed.type)) {
                updateReservationFromEmail(parsed);
                handled++;
            } else {
                createReservationFromEmail(parsed);
                handled++;
            }
            markEmailAsProcessed(message.messageId);
        }
        prefs.edit().putString(K_EMAIL_LAST_SYNC, LocalDateTime.now().toString().replace('T', ' ')).apply();
        addEmailLog("Live Gmail sync klaar: " + handled + " verwerkt, " + errors + " fout(en), " + (processedIds().size() - before) + " nieuw messageId");
        Toast.makeText(this, "Live Gmail sync klaar", Toast.LENGTH_LONG).show();
        render();
    }

    @Override ArrayList<GmailMessage> searchWixReservationEmails() {
        ArrayList<GmailMessage> result = new ArrayList<>();
        if (!ensureAccessToken()) return result;
        try {
            String query = "from:info@peperboom.nl (\"Je hebt een nieuwe reserveringsaanvraag\" OR \"reserveringsaanvraag ingediend\" OR \"Bekijk de reservering\" OR \"Scroll naar beneden voor meer details\") newer_than:30d";
            JSONObject search = gmailGet("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=20&q=" + enc(query));
            JSONArray messages = search.optJSONArray("messages");
            if (messages == null) return result;
            for (int i = 0; i < messages.length(); i++) {
                String id = messages.getJSONObject(i).optString("id");
                if (id.isEmpty() || processedIds().contains(id)) continue;
                JSONObject full = gmailGet("https://gmail.googleapis.com/gmail/v1/users/me/messages/" + enc(id) + "?format=full");
                GmailMessage message = toGmailMessage(full);
                if (isWixReservationEmail(message)) result.add(message);
            }
        } catch (Exception e) {
            increase(K_GMAIL_ERRORS);
            addEmailLog("Fout bij Gmail lezen: " + e.getMessage());
        }
        return result;
    }

    @Override void syncGmailReservationsSilently() {
        if (!ensureAccessToken()) return;
        int before = processedIds().size();
        for (GmailMessage message : searchWixReservationEmails()) {
            if (processedIds().contains(message.messageId)) continue;
            GmailReservation parsed = parseWixEmail(message);
            if (parsed == null) increase(K_GMAIL_ERRORS);
            else if ("annulering".equals(parsed.type)) cancelReservationFromEmail(parsed);
            else if ("wijziging".equals(parsed.type)) updateReservationFromEmail(parsed);
            else createReservationFromEmail(parsed);
            markEmailAsProcessed(message.messageId);
        }
        if (processedIds().size() != before) {
            prefs.edit().putString(K_EMAIL_LAST_SYNC, LocalDateTime.now().toString().replace('T', ' ')).apply();
            addEmailLog("Automatische live Gmail-sync uitgevoerd");
        }
    }

    void requestDeviceCode(String id) {
        new Thread(() -> {
            try {
                JSONObject json = postForm("https://oauth2.googleapis.com/device/code",
                        "client_id=" + enc(id) + "&scope=" + enc(GMAIL_SCOPE));
                String device = json.optString("device_code");
                String userCode = json.optString("user_code");
                String url = json.optString("verification_url", json.optString("verification_uri", "https://www.google.com/device"));
                int interval = Math.max(5, json.optInt("interval", 5));
                if (device.isEmpty() || userCode.isEmpty()) throw new Exception(json.toString());
                prefs.edit().putString(K_GMAIL_DEVICE_CODE, device).apply();
                runOnUiThread(() -> showDeviceDialog(userCode, url, device, interval));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    addEmailLog("Fout bij OAuth starten: " + e.getMessage());
                    Toast.makeText(this, "OAuth starten mislukt", Toast.LENGTH_LONG).show();
                    render();
                });
            }
        }).start();
    }

    void showDeviceDialog(String userCode, String url, String device, int interval) {
        new AlertDialog.Builder(this)
                .setTitle("Google toestemming")
                .setMessage("Open Google en vul deze code in:\n\n" + userCode + "\n\nDaarna leest de app alleen Gmail-reserveringsmails.")
                .setPositiveButton("Open Google", (dialog, which) -> {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
                    pollToken(device, interval);
                })
                .setNeutralButton("Toestemming controleren", (dialog, which) -> pollToken(device, interval))
                .setNegativeButton("Later", null)
                .show();
    }

    void pollSavedDeviceCode() {
        String device = prefs.getString(K_GMAIL_DEVICE_CODE, "");
        if (device.isEmpty()) {
            Toast.makeText(this, "Start eerst Gmail koppelen", Toast.LENGTH_SHORT).show();
            return;
        }
        pollToken(device, 5);
    }

    void pollToken(String device, int interval) {
        new Thread(() -> {
            try {
                String id = clientId();
                for (int attempt = 0; attempt < 60; attempt++) {
                    JSONObject json = postForm("https://oauth2.googleapis.com/token",
                            "client_id=" + enc(id) +
                                    "&device_code=" + enc(device) +
                                    "&grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code"));
                    String error = json.optString("error");
                    if (error.isEmpty()) {
                        saveTokens(json);
                        prefs.edit().putBoolean(K_EMAIL_CONNECTED, true).remove(K_GMAIL_DEVICE_CODE).apply();
                        runOnUiThread(() -> {
                            addEmailLog("Gmail OAuth actief voor " + account());
                            Toast.makeText(this, "Gmail is live gekoppeld", Toast.LENGTH_LONG).show();
                            render();
                        });
                        return;
                    }
                    if (!"authorization_pending".equals(error) && !"slow_down".equals(error)) throw new Exception(error);
                    Thread.sleep(("slow_down".equals(error) ? interval + 5 : interval) * 1000L);
                }
                throw new Exception("toestemming verlopen");
            } catch (Exception e) {
                runOnUiThread(() -> {
                    addEmailLog("OAuth controle mislukt: " + e.getMessage());
                    Toast.makeText(this, "Gmail toestemming nog niet klaar", Toast.LENGTH_LONG).show();
                    render();
                });
            }
        }).start();
    }

    boolean ensureAccessToken() {
        if (clientId().isEmpty()) return false;
        long expires = prefs.getLong(K_GMAIL_EXPIRES_AT, 0);
        String access = secureGet(K_GMAIL_ACCESS_TOKEN);
        if (!access.isEmpty() && expires > System.currentTimeMillis() + 60000) return true;
        String refresh = secureGet(K_GMAIL_REFRESH_TOKEN);
        if (refresh.isEmpty()) return false;
        try {
            JSONObject json = postForm("https://oauth2.googleapis.com/token",
                    "client_id=" + enc(clientId()) +
                            "&refresh_token=" + enc(refresh) +
                            "&grant_type=refresh_token");
            if (!json.optString("access_token").isEmpty()) {
                saveTokens(json);
                return true;
            }
            addEmailLog("Gmail token vernieuwen mislukt: " + json.optString("error"));
        } catch (Exception e) {
            addEmailLog("Gmail token vernieuwen mislukt: " + e.getMessage());
        }
        return false;
    }

    void saveTokens(JSONObject json) {
        securePut(K_GMAIL_ACCESS_TOKEN, json.optString("access_token"));
        String refresh = json.optString("refresh_token");
        if (!refresh.isEmpty()) securePut(K_GMAIL_REFRESH_TOKEN, refresh);
        long expires = System.currentTimeMillis() + Math.max(60, json.optInt("expires_in", 3600)) * 1000L;
        prefs.edit().putLong(K_GMAIL_EXPIRES_AT, expires).apply();
    }

    JSONObject gmailGet(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + secureGet(K_GMAIL_ACCESS_TOKEN));
        conn.setRequestProperty("Accept", "application/json");
        return new JSONObject(read(conn));
    }

    JSONObject postForm(String url, String body) throws Exception {
        byte[] bytes = body.getBytes("UTF-8");
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Accept", "application/json");
        OutputStream out = conn.getOutputStream();
        out.write(bytes);
        out.close();
        return new JSONObject(read(conn));
    }

    String read(HttpURLConnection conn) throws Exception {
        InputStream stream = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) builder.append(line);
        reader.close();
        return builder.toString();
    }

    GmailMessage toGmailMessage(JSONObject full) {
        String id = full.optString("id");
        JSONObject payload = full.optJSONObject("payload");
        String from = header(payload, "From");
        String subject = header(payload, "Subject");
        String body = payload == null ? full.optString("snippet") : extractBody(payload);
        if (body.trim().isEmpty()) body = full.optString("snippet");
        return new GmailMessage(id, from, subject, body);
    }

    String header(JSONObject payload, String name) {
        if (payload == null) return "";
        JSONArray headers = payload.optJSONArray("headers");
        if (headers == null) return "";
        for (int i = 0; i < headers.length(); i++) {
            JSONObject h = headers.optJSONObject(i);
            if (h != null && name.equalsIgnoreCase(h.optString("name"))) return h.optString("value", "");
        }
        return "";
    }

    String extractBody(JSONObject payload) {
        StringBuilder builder = new StringBuilder();
        appendPart(payload, builder);
        return cleanupHtml(builder.toString());
    }

    void appendPart(JSONObject part, StringBuilder builder) {
        JSONObject body = part.optJSONObject("body");
        String mime = part.optString("mimeType", "");
        if (body != null && body.has("data") && (mime.startsWith("text/plain") || mime.startsWith("text/html") || mime.isEmpty())) {
            builder.append('\n').append(decodeGmailData(body.optString("data")));
        }
        JSONArray parts = part.optJSONArray("parts");
        if (parts != null) for (int i = 0; i < parts.length(); i++) appendPart(parts.optJSONObject(i), builder);
    }

    String decodeGmailData(String data) {
        try {
            byte[] bytes = Base64.decode(data, Base64.URL_SAFE | Base64.NO_WRAP);
            return new String(bytes, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    String cleanupHtml(String raw) {
        return raw.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replaceAll("[ \\t]+", " ")
                .trim();
    }

    void disconnectLiveGmail() {
        new AlertDialog.Builder(this)
                .setTitle("Gmail ontkoppelen")
                .setMessage("Lokale OAuth-tokens wissen? Toestemming kan ook in je Google-account worden ingetrokken.")
                .setNegativeButton("Annuleren", null)
                .setPositiveButton("Ontkoppel", (dialog, which) -> {
                    securePut(K_GMAIL_ACCESS_TOKEN, "");
                    securePut(K_GMAIL_REFRESH_TOKEN, "");
                    prefs.edit().putBoolean(K_EMAIL_CONNECTED, false)
                            .remove(K_GMAIL_EXPIRES_AT)
                            .remove(K_GMAIL_DEVICE_CODE)
                            .apply();
                    addEmailLog("Gmail ontkoppeld en lokale tokens gewist");
                    render();
                })
                .show();
    }

    String clientId() {
        return prefs.getString(K_GOOGLE_CLIENT_ID, "").trim();
    }

    String enc(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }

    void securePut(String key, String value) {
        try {
            SecretKey secret = secretKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secret);
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal((value == null ? "" : value).getBytes("UTF-8"));
            String packed = Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP);
            prefs.edit().putString(key, packed).apply();
        } catch (Exception e) {
            prefs.edit().putString(key, "").apply();
        }
    }

    String secureGet(String key) {
        try {
            String packed = prefs.getString(key, "");
            if (packed.isEmpty() || !packed.contains(":")) return "";
            String[] parts = packed.split(":", 2);
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    SecretKey secretKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(KEY_ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            generator.generateKey();
        }
        return ((KeyStore.SecretKeyEntry) store.getEntry(KEY_ALIAS, null)).getSecretKey();
    }
}
