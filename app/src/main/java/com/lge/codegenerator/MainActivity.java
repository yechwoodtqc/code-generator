package com.lge.codegenerator;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends Activity {
    private static final String CODE_ENDPOINT =
            "https://yw-app-store-api.yw-app-installer-store-2026.workers.dev/admin/codes";

    private EditText secretInput;
    private EditText countInput;
    private EditText expiryInput;
    private Button generateButton;
    private TextView statusView;
    private LinearLayout codesContainer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        page.setPadding(pad, dp(26), pad, pad);
        scroll.addView(page);

        TextView title = new TextView(this);
        title.setText("YW Code Generator");
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        page.addView(title, matchWrap());

        TextView intro = new TextView(this);
        intro.setText("Create single-use activation codes for your app store. Enter your admin secret below.");
        intro.setTextSize(16);
        intro.setPadding(0, dp(8), 0, dp(18));
        page.addView(intro, matchWrap());

        addLabel(page, "Admin secret");
        secretInput = new EditText(this);
        secretInput.setSingleLine(true);
        secretInput.setHint("Enter admin secret");
        secretInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        page.addView(secretInput, matchWrap());

        addLabel(page, "Number of codes (1–50)");
        countInput = new EditText(this);
        countInput.setSingleLine(true);
        countInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        countInput.setText("1");
        page.addView(countInput, matchWrap());

        addLabel(page, "Expiry in hours (1–720)");
        expiryInput = new EditText(this);
        expiryInput.setSingleLine(true);
        expiryInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        expiryInput.setText("168");
        page.addView(expiryInput, matchWrap());

        generateButton = new Button(this);
        generateButton.setText("Generate code");
        page.addView(generateButton, matchWrap());
        generateButton.setOnClickListener(v -> generateCodes());

        statusView = new TextView(this);
        statusView.setTextSize(16);
        statusView.setPadding(0, dp(12), 0, dp(8));
        page.addView(statusView, matchWrap());

        codesContainer = new LinearLayout(this);
        codesContainer.setOrientation(LinearLayout.VERTICAL);
        page.addView(codesContainer, matchWrap());

        TextView safety = new TextView(this);
        safety.setText("Security: use this on a device you control. The admin secret is not saved to disk. Each code can be used once.");
        safety.setTextSize(13);
        safety.setPadding(0, dp(22), 0, 0);
        page.addView(safety, matchWrap());

        setContentView(scroll);
        getWindow().setStatusBarColor(0xFF26364A);
        getWindow().setNavigationBarColor(0xFF26364A);
    }

    private void generateCodes() {
        String secret = secretInput.getText().toString().trim();
        int count;
        int ttl;
        try {
            count = Integer.parseInt(countInput.getText().toString().trim());
            ttl = Integer.parseInt(expiryInput.getText().toString().trim());
        } catch (NumberFormatException ex) {
            showStatus("Enter a valid number of codes and expiry hours.", true);
            return;
        }
        if (secret.isEmpty()) {
            showStatus("Enter your admin secret first.", true);
            return;
        }
        if (count < 1 || count > 50 || ttl < 1 || ttl > 720) {
            showStatus("Codes: 1–50. Expiry: 1–720 hours.", true);
            return;
        }

        View view = getCurrentFocus();
        if (view != null) {
            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
        generateButton.setEnabled(false);
        statusView.setText("Connecting securely…");
        statusView.setTextColor(0xFF26364A);
        codesContainer.removeAllViews();

        final int requestedCount = count;
        final int requestedTtl = ttl;
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(CODE_ENDPOINT);
                connection = (HttpURLConnection) url.openConnection();
                if (connection instanceof HttpsURLConnection) {
                    LegacyTlsCompat.configure(this, (HttpsURLConnection) connection);
                }
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Authorization", "Bearer " + secret);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Accept", "application/json");

                JSONObject payload = new JSONObject();
                payload.put("count", requestedCount);
                payload.put("ttlHours", requestedTtl);
                byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }

                int responseCode = connection.getResponseCode();
                InputStream stream = responseCode >= 200 && responseCode < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                String responseBody = readAll(stream);
                JSONObject response;
                try {
                    response = new JSONObject(responseBody);
                } catch (Exception parseError) {
                    if (responseCode < 200 || responseCode >= 300) {
                        String error = "Code service returned HTTP " + responseCode
                                + ". Check the service configuration and admin secret.";
                        runOnUiThread(() -> finishWithError(error));
                    } else {
                        String error = "Code service returned an unreadable response (HTTP "
                                + responseCode + ").";
                        runOnUiThread(() -> finishWithError(error));
                    }
                    return;
                }

                if (responseCode < 200 || responseCode >= 300) {
                    String error = response.optString("error",
                            "Request failed (HTTP " + responseCode + ").");
                    runOnUiThread(() -> finishWithError(error));
                    return;
                }

                JSONArray codes = response.optJSONArray("codes");
                if (codes == null || codes.length() == 0) {
                    runOnUiThread(() -> finishWithError("The server returned no codes."));
                    return;
                }
                runOnUiThread(() -> showCodes(codes, requestedTtl));
            } catch (Exception ex) {
                String detail = ex.getClass().getSimpleName();
                String message = ex.getMessage();
                String error = "Connection failed (" + detail
                        + (message == null || message.trim().isEmpty() ? "" : ": " + message)
                        + "). Check internet access and the service URL.";
                runOnUiThread(() -> finishWithError(error));
            } finally {
                if (connection != null) connection.disconnect();
                runOnUiThread(() -> generateButton.setEnabled(true));
            }
        });
    }

    private void showCodes(JSONArray codes, int ttlHours) {
        statusView.setText("Success — " + codes.length() + " code(s) generated. Expires in "
                + ttlHours + " hour(s).");
        statusView.setTextColor(0xFF17633A);
        for (int i = 0; i < codes.length(); i++) {
            String code = codes.optString(i);
            TextView codeText = new TextView(this);
            codeText.setText(code);
            codeText.setTextSize(22);
            codeText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            codeText.setGravity(Gravity.CENTER);
            codeText.setPadding(dp(8), dp(12), dp(8), dp(12));
            codeText.setBackgroundColor(0xFFF0F3F7);
            LinearLayout.LayoutParams codeParams = matchWrap();
            codeParams.setMargins(0, dp(10), 0, dp(4));
            codesContainer.addView(codeText, codeParams);

            Button copyButton = new Button(this);
            copyButton.setText("Copy code");
            copyButton.setOnClickListener(v -> {
                ClipboardManager clipboard =
                        (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("YW activation code", code));
                Toast.makeText(this, "Code copied", Toast.LENGTH_SHORT).show();
            });
            codesContainer.addView(copyButton, matchWrap());
        }
    }

    private void finishWithError(String message) {
        statusView.setText(message);
        statusView.setTextColor(0xFF9B1C1C);
    }

    private void showStatus(String message, boolean error) {
        statusView.setText(message);
        statusView.setTextColor(error ? 0xFF9B1C1C : 0xFF26364A);
    }

    private void addLabel(LinearLayout parent, String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(15);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setPadding(0, dp(12), 0, dp(4));
        parent.addView(label, matchWrap());
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String readAll(InputStream input) throws Exception {
        if (input == null) return "{}";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
