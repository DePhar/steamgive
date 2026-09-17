package com.dephar.steamgive;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private LinearLayout root, content;
    private final int bg = Color.rgb(11, 13, 18);
    private final int card = Color.rgb(21, 26, 34);
    private final int blue = Color.rgb(102, 192, 244);
    private final int textSecondary = Color.rgb(190, 195, 205);

    private final String[] titles = {"Cyberpunk 2077", "Hades II", "Balatro"};
    private final String[] values = {"$59.99", "$29.99", "$14.99"};
    private final String[] entries = {"842", "314", "197"};
    private final String[] times = {"03:42:18", "05:17:03", "08:09:44"};
    private final int[] appIds = {1091500, 1145350, 2379780};

    private android.content.SharedPreferences prefs;
    private int points;
    private String steamId = "";
    private boolean inDetail = false;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        prefs = getSharedPreferences("steamgive", MODE_PRIVATE);
        points = prefs.getInt("points", 100);
        steamId = prefs.getString("steam_id", "");
        showHome();
        handleSteamCallback(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSteamCallback(intent);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView tv(String s, float size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        return t;
    }

    private Button btn(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setMinHeight(dp(48));
        return b;
    }

    private void base() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(0, dp(24), 0, 0);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setBackgroundColor(Color.rgb(17, 20, 27));
        String[] ns = {"⌂ Home", "♥ Wishlist", "＋ Create", "● Profile"};
        for (int i = 0; i < ns.length; i++) {
            Button n = btn(ns[i]);
            final int x = i;
            n.setOnClickListener(v -> {
                if (x == 0) showHome();
                if (x == 1) showWishlist();
                if (x == 2) showCreate();
                if (x == 3) showProfile();
            });
            nav.addView(n, new LinearLayout.LayoutParams(0, dp(60), 1));
        }
        root.addView(nav);
        setContentView(root);
    }

    private void showHome() {
        inDetail = false;
        base();
        TextView logo = tv("SteamGive", 30);
        logo.setTypeface(null, Typeface.BOLD);
        content.addView(logo);
        content.addView(tv("Free games. Fair chances.", 16));

        TextView account = tv(steamId.isEmpty() ? "🔐 Steam not connected   •   💎 " + points + " points" : "🟢 Steam connected   •   💎 " + points + " points", 15);
        account.setTextColor(blue);
        content.addView(account);
        content.addView(tv("🔥 Recommended", 24));

        for (int i = 0; i < titles.length; i++) {
            final int x = i;
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setPadding(dp(8), dp(8), dp(8), dp(8));
            c.setBackgroundColor(card);

            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            c.addView(image, new LinearLayout.LayoutParams(-1, dp(180)));
            loadGameImage(image, appIds[i]);

            TextView a = tv(titles[i], 22);
            a.setTypeface(null, Typeface.BOLD);
            c.addView(a);
            c.addView(tv(values[i] + "   •   " + entries[i] + " entries   •   ⏱ " + times[i], 14));

            Button e = btn("🎁 Enter giveaway  •  10 points");
            e.setOnClickListener(v -> detail(x));
            c.addView(e);

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
            p.setMargins(dp(12), dp(8), dp(12), dp(8));
            content.addView(c, p);
        }
    }

    private void detail(int i) {
        inDetail = true;
        base();
        Button back = btn("‹  Back");
        back.setOnClickListener(v -> showHome());
        content.addView(back);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        content.addView(image, new LinearLayout.LayoutParams(-1, dp(210)));
        loadGameImage(image, appIds[i]);

        TextView title = tv(titles[i], 30);
        title.setTypeface(null, Typeface.BOLD);
        content.addView(title);
        content.addView(tv(values[i] + "   •   " + entries[i] + " participants", 17));
        content.addView(tv("💎 Your points: " + points, 17));
        content.addView(tv("About this giveaway\n\nPrototype giveaway. Steam login, points and Steam game artwork are now connected in the prototype. Production version will move points and entries to a secure server.", 17));

        Button b = btn("🎁  Enter giveaway — 10 points");
        if (points < 10) {
            b.setEnabled(false);
            b.setText("Not enough points");
        } else if (prefs.getBoolean("entered_" + appIds[i], false)) {
            b.setText("✓  You are entered");
        }
        b.setOnClickListener(v -> {
            if (steamId.isEmpty()) {
                Toast.makeText(this, "Сначала подключи Steam в профиле", Toast.LENGTH_LONG).show();
                showProfile();
                return;
            }
            if (points >= 10) {
                points -= 10;
                prefs.edit().putInt("points", points).putBoolean("entered_" + appIds[i], true).apply();
                b.setText("✓  You are entered");
                b.setEnabled(false);
                Toast.makeText(this, "Участие добавлено. Списано 10 points.", Toast.LENGTH_SHORT).show();
            }
        });
        content.addView(b);
    }

    private void showWishlist() {
        inDetail = false;
        base();
        content.addView(tv("♥ Wishlist", 28));
        content.addView(tv("Games you want to watch for giveaways.", 16));
        content.addView(tv("\nYour wishlist is empty", 20));
    }

    private void showCreate() {
        inDetail = false;
        base();
        content.addView(tv("🎁 Create giveaway", 28));
        content.addView(tv("Add the Steam App ID of the game.", 16));
        EditText e = new EditText(this);
        e.setHint("Steam App ID");
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.GRAY);
        content.addView(e);
        Button b = btn("Create giveaway");
        content.addView(b);
        b.setOnClickListener(v -> Toast.makeText(this, "Создание раздач будет подключено к серверу следующим этапом", Toast.LENGTH_LONG).show());
    }

    private void showProfile() {
        inDetail = false;
        base();
        content.addView(tv("●  Profile", 28));
        if (steamId.isEmpty()) {
            content.addView(tv("Steam account not connected", 17));
            content.addView(tv("\n💎 Points    " + points + "\n🏆 Won       0", 17));
            Button login = btn("🔐 Connect Steam");
            content.addView(login);
            login.setOnClickListener(v -> startSteamLogin());
        } else {
            content.addView(tv("🟢 Steam connected", 18));
            content.addView(tv("SteamID\n" + steamId + "\n\n💎 Points    " + points + "\n🏆 Won       0", 17));
            Button logout = btn("Log out");
            content.addView(logout);
            logout.setOnClickListener(v -> {
                steamId = "";
                prefs.edit().remove("steam_id").apply();
                showProfile();
            });
        }
    }

    private void startSteamLogin() {
        try {
            String returnTo = "steamgive://auth";
            String url = "https://steamcommunity.com/openid/login" +
                    "?openid.ns=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0" +
                    "&openid.mode=checkid_setup" +
                    "&openid.return_to=" + URLEncoder.encode(returnTo, "UTF-8") +
                    "&openid.realm=" + URLEncoder.encode(returnTo, "UTF-8") +
                    "&openid.identity=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select" +
                    "&openid.claimed_id=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select";
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть Steam", Toast.LENGTH_LONG).show();
        }
    }

    private void handleSteamCallback(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri data = intent.getData();
        if (!"steamgive".equals(data.getScheme()) || !"auth".equals(data.getHost())) return;

        final List<String> names = new ArrayList<>();
        for (String name : data.getQueryParameterNames()) names.add(name);
        new Thread(() -> {
            try {
                String claimed = data.getQueryParameter("openid.claimed_id");
                if (claimed == null || !claimed.contains("/id/")) throw new Exception("SteamID missing");

                String steam = claimed.substring(claimed.lastIndexOf("/id/") + 4);
                StringBuilder body = new StringBuilder();
                for (String name : names) {
                    if ("openid.mode".equals(name)) continue;
                    String value = data.getQueryParameter(name);
                    if (value == null) continue;
                    if (body.length() > 0) body.append('&');
                    body.append(URLEncoder.encode(name, "UTF-8"));
                    body.append('=');
                    body.append(URLEncoder.encode(value, "UTF-8"));
                }
                if (body.length() > 0) body.append('&');
                body.append("openid.mode=check_authentication");

                URL u = new URL("https://steamcommunity.com/openid/login");
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                OutputStream os = c.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();

                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                String line;
                StringBuilder response = new StringBuilder();
                while ((line = r.readLine()) != null) response.append(line).append('\n');
                r.close();
                c.disconnect();

                if (!response.toString().contains("is_valid:true")) throw new Exception("Steam validation failed");

                runOnUiThread(() -> {
                    steamId = steam;
                    points = prefs.getInt("points", 100);
                    prefs.edit().putString("steam_id", steamId).apply();
                    Toast.makeText(this, "Steam подключён", Toast.LENGTH_SHORT).show();
                    showProfile();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Не удалось подтвердить вход Steam", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void loadGameImage(ImageView view, int appId) {
        new Thread(() -> {
            try {
                URL url = new URL("https://cdn.akamai.steamstatic.com/steam/apps/" + appId + "/header.jpg");
                Bitmap bitmap = BitmapFactory.decodeStream(url.openStream());
                if (bitmap != null) runOnUiThread(() -> view.setImageBitmap(bitmap));
            } catch (Exception ignored) {
                runOnUiThread(() -> view.setBackgroundColor(Color.rgb(30, 35, 45)));
            }
        }).start();
    }

    @Override
    public void onBackPressed() {
        if (inDetail) showHome();
        else super.onBackPressed();
    }
}
