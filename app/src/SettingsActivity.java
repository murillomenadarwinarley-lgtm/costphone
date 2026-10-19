package com.accessdroid.termux;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public class SettingsActivity extends Activity {

    private static final int BG_DARK       = 0xFF0A0F1E;
    private static final int BG_HEADER     = 0xFF111827;
    private static final int BG_CARD       = 0xFF151D2E;
    private static final int BG_CARD_ALT   = 0xFF1A2338;
    private static final int ACCENT_BLUE   = 0xFF3B82F6;
    private static final int ACCENT_GREEN  = 0xFF10B981;
    private static final int ACCENT_RED    = 0xFFEF4444;
    private static final int ACCENT_AMBER  = 0xFFF59E0B;
    private static final int TEXT_MAIN     = 0xFFF1F5F9;
    private static final int TEXT_DIM      = 0xFF94A3B8;
    private static final int TEXT_MUTED    = 0xFF64748B;
    private static final int LOG_TEXT      = 0xFF7DD3FC;
    private static final int LOG_BG        = 0xFF0F172A;
    private static final int DIVIDER       = 0xFF1E293B;
    private static final int CARD_RADIUS   = 18;
    private static final int STATUS_RADIUS = 999;

    private View statusDot;
    private TextView tvStatus;
    private TextView tvStatusDesc;
    private View statusBanner;

    private View shizukuDot;
    private TextView tvShizuku;
    private Button btnShizuku;

    private TextView tvLog;

    private static final int REQ_SHIZUKU_PERMISSION = 41;

    private final rikka.shizuku.Shizuku.OnRequestPermissionResultListener shizukuPermListener =
            new rikka.shizuku.Shizuku.OnRequestPermissionResultListener() {
                @Override public void onRequestPermissionResult(int requestCode, int grantResult) {
                    if (requestCode == REQ_SHIZUKU_PERMISSION) refreshShizuku();
                }
            };

    private final ShizukuManager.StateListener shizukuStateListener =
            new ShizukuManager.StateListener() {
                @Override public void onStateChanged(int state) {
                    refreshShizuku();
                }
            };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG_DARK);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(24));
        scroll.addView(root);

        root.addView(buildHeader(), lpMATCH(0));
        root.addView(serviceCard(), lpMATCH(16));
        root.addView(shizukuCard(), lpMATCH(12));
        root.addView(maintenanceCard(), lpMATCH(12));
        root.addView(buildFooter(), lpMATCH(20));

        setContentView(scroll);

        try {
            rikka.shizuku.Shizuku.addRequestPermissionResultListener(shizukuPermListener);
            ShizukuManager.registerStateListener(shizukuStateListener);
        } catch (Throwable ignored) { }
    }

    @Override protected void onDestroy() {
        try {
            rikka.shizuku.Shizuku.removeRequestPermissionResultListener(shizukuPermListener);
            ShizukuManager.unregisterStateListener(shizukuStateListener);
        } catch (Throwable ignored) { }
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        requestNotificationPermission();
        refreshStatus();
        refreshShizuku();
    }

    private static final int REQ_POST_NOTIFICATIONS = 42;

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        try {
            requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    REQ_POST_NOTIFICATIONS);
        } catch (Throwable ignored) { }
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        header.setPadding(dp(24), dp(28), dp(24), dp(28));
        header.setBackground(rounded(BG_HEADER, CARD_RADIUS, 0));

        View iconContainer = new View(this);
        iconContainer.setBackground(rounded(0xFF1E293B, 999, 0));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        logo.setPadding(dp(14), dp(14), dp(14), dp(14));
        logo.setBackground(rounded(ACCENT_BLUE, 22, 0));
        logo.setLayoutParams(new LinearLayout.LayoutParams(dp(72), dp(72)));

        LinearLayout logoWrap = new LinearLayout(this);
        logoWrap.setGravity(Gravity.CENTER);
        logoWrap.addView(logo);
        header.addView(logoWrap, lpWRAP(0));

        TextView title = new TextView(this);
        title.setText("CostPhone");
        title.setTextSize(28);
        title.setTextColor(TEXT_MAIN);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        header.addView(title, lpWRAP(14));

        TextView sub = new TextView(this);
        sub.setText("Centro de control de Android");
        sub.setTextSize(15);
        sub.setTextColor(TEXT_DIM);
        sub.setGravity(Gravity.CENTER);
        header.addView(sub, lpWRAP(8));

        return header;
    }

    private View serviceCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(BG_CARD, CARD_RADIUS, 0));
        card.setPadding(dp(20), dp(20), dp(20), dp(20));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        statusDot = new View(this);
        row.addView(statusDot, lp(dp(16), dp(16), 0));

        tvStatus = new TextView(this);
        tvStatus.setTextSize(18);
        tvStatus.setTextColor(TEXT_MAIN);
        tvStatus.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = dp(12);
        row.addView(tvStatus, tlp);
        card.addView(row);

        statusBanner = new View(this);
        statusBanner.setBackground(rounded(0xFF1E293B, 8, 0));
        card.addView(statusBanner, lpMATCH(14));

        tvStatusDesc = new TextView(this);
        tvStatusDesc.setTextSize(13);
        tvStatusDesc.setTextColor(TEXT_DIM);
        tvStatusDesc.setLineSpacing(dp(5), 1f);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = dp(12);
        card.addView(tvStatusDesc, dlp);

        View divider = new View(this);
        divider.setBackgroundColor(DIVIDER);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        dividerLp.topMargin = dp(16);
        dividerLp.bottomMargin = dp(16);
        card.addView(divider, dividerLp);

        Button btn = new Button(this);
        btn.setText("Abrir ajustes de accesibilidad");
        btn.setAllCaps(false);
        btn.setTextSize(15);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setBackground(rounded(ACCENT_BLUE, 12, 0));
        btn.setPadding(dp(18), dp(14), dp(18), dp(14));
        btn.setStateListAnimator(null);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        card.addView(btn, blp);

        return card;
    }

    private View shizukuCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(BG_CARD_ALT, CARD_RADIUS, 0));
        card.setPadding(dp(20), dp(18), dp(20), dp(18));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        shizukuDot = new View(this);
        row.addView(shizukuDot, lp(dp(12), dp(12), 0));

        tvShizuku = new TextView(this);
        tvShizuku.setTextSize(15);
        tvShizuku.setTextColor(TEXT_MAIN);
        tvShizuku.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = dp(10);
        row.addView(tvShizuku, tlp);

        TextView badge = new TextView(this);
        badge.setText("OPCIONAL");
        badge.setTextSize(9);
        badge.setTextColor(ACCENT_BLUE);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setPadding(dp(8), dp(3), dp(8), dp(3));
        badge.setBackground(rounded(0xFF1E3A5F, 4, 0));
        LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        blp2.leftMargin = dp(10);
        row.addView(badge, blp2);

        card.addView(row);

        TextView desc = new TextView(this);
        desc.setText("Shell con permisos especiales para gestos y teclas rápidos.");
        desc.setTextSize(13);
        desc.setTextColor(TEXT_DIM);
        desc.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = dp(12);
        card.addView(desc, dlp);

        btnShizuku = new Button(this);
        btnShizuku.setAllCaps(false);
        btnShizuku.setTextSize(14);
        btnShizuku.setTextColor(0xFFFFFFFF);
        btnShizuku.setTypeface(Typeface.DEFAULT_BOLD);
        btnShizuku.setBackground(rounded(ACCENT_BLUE, 12, 0));
        btnShizuku.setPadding(dp(18), dp(12), dp(18), dp(12));
        btnShizuku.setStateListAnimator(null);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(16);
        card.addView(btnShizuku, blp);

        return card;
    }

    private View maintenanceCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(BG_CARD, CARD_RADIUS, 0));
        card.setPadding(dp(20), dp(18), dp(20), dp(18));

        TextView head = new TextView(this);
        head.setText("Mantenimiento");
        head.setTextSize(15);
        head.setTextColor(TEXT_MAIN);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(head);

        TextView batDesc = new TextView(this);
        batDesc.setText("Excluye CostPhone de la optimización de batería para que "
                + "Android no lo cierre en segundo plano.");
        batDesc.setTextSize(13);
        batDesc.setTextColor(TEXT_DIM);
        batDesc.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams bdlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bdlp.topMargin = dp(8);
        card.addView(batDesc, bdlp);

        Button btnBat = new Button(this);
        btnBat.setText("Excluir de optimización");
        btnBat.setAllCaps(false);
        btnBat.setTextSize(14);
        btnBat.setTextColor(0xFFFFFFFF);
        btnBat.setTypeface(Typeface.DEFAULT_BOLD);
        btnBat.setBackground(rounded(ACCENT_BLUE, 12, 0));
        btnBat.setPadding(dp(16), dp(12), dp(16), dp(12));
        btnBat.setStateListAnimator(null);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(12);
        btnBat.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Throwable t) {
                    try {
                        Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                        startActivity(i);
                    } catch (Throwable ignored) { }
                }
            }
        });
        card.addView(btnBat, blp);

        View divider = new View(this);
        divider.setBackgroundColor(DIVIDER);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        dividerLp.topMargin = dp(16);
        dividerLp.bottomMargin = dp(14);
        card.addView(divider, dividerLp);

        TextView logHead = new TextView(this);
        logHead.setText("Registro de eventos");
        logHead.setTextSize(13);
        logHead.setTextColor(TEXT_MAIN);
        logHead.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(logHead);

        tvLog = new TextView(this);
        tvLog.setText(logText());
        tvLog.setTextSize(11);
        tvLog.setTextColor(LOG_TEXT);
        tvLog.setTypeface(Typeface.MONOSPACE);
        tvLog.setLineSpacing(dp(3), 1f);
        tvLog.setPadding(dp(12), dp(10), dp(12), dp(10));
        tvLog.setBackground(rounded(LOG_BG, 8, 0));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(8);
        card.addView(tvLog, llp);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams brlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        brlp.topMargin = dp(10);
        card.addView(btnRow, brlp);

        Button btnClear = new Button(this);
        btnClear.setText("Limpiar registro");
        btnClear.setAllCaps(false);
        btnClear.setTextSize(13);
        btnClear.setTextColor(TEXT_DIM);
        btnClear.setTypeface(Typeface.DEFAULT_BOLD);
        btnClear.setBackground(rounded(BG_CARD_ALT, 10, DIVIDER));
        btnClear.setPadding(dp(14), dp(10), dp(14), dp(10));
        btnClear.setStateListAnimator(null);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ConnectionLog.clear();
                tvLog.setText("");
            }
        });
        btnRow.addView(btnClear, clp);

        return card;
    }

    private View buildFooter() {
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER);

        TextView ver = new TextView(this);
        ver.setText("CostPhone v" + versionName());
        ver.setTextSize(11);
        ver.setTextColor(TEXT_MUTED);
        footer.addView(ver);

        return footer;
    }

    private void refreshStatus() {
        int state = serviceState();
        int color;
        String text;
        String desc;
        switch (state) {
            case 2:
                color = ACCENT_GREEN;
                text = "Conectado";
                desc = "El servicio de accesibilidad está activo y responde a los comandos de Termux.";
                break;
            case 1:
                color = ACCENT_AMBER;
                text = "Habilitado, sin conexión";
                desc = "El servicio está activado en ajustes pero no ha establecido conexión todavía.";
                break;
            default:
                color = ACCENT_RED;
                text = "Desactivado";
                desc = "El servicio de accesibilidad está apagado. Actívalo para permitir el control remoto.";
                break;
        }
        statusDot.setBackground(rounded(color, STATUS_RADIUS, 0));
        tvStatus.setText(text);
        tvStatus.setTextColor(color);
        tvStatusDesc.setText(desc);
        statusBanner.setBackground(rounded(0xFF1E293B, 8, 0));
    }

    private int serviceState() {
        if (!isServiceEnabled()) return 0;
        if (AccessibilityServiceImpl.sInstance != null
                && AccessibilityServiceImpl.sInstance.isConnected()) return 2;
        return 1;
    }

    private boolean isServiceEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (flat == null || flat.isEmpty()) return false;
        ComponentName me = new ComponentName(this, AccessibilityServiceImpl.class);
        for (String s : flat.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(s);
            if (cn == null) continue;
            if (cn.equals(me)) return true;
        }
        return false;
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "?";
        }
    }

    private String logText() {
        StringBuilder sb = new StringBuilder();
        for (String l : ConnectionLog.dump()) sb.append(l).append('\n');
        if (sb.length() == 0) sb.append("Sin eventos registrados todavía.");
        return sb.toString();
    }

    private void refreshShizuku() {
        switch (ShizukuManager.state()) {
            case ShizukuManager.READY:
                shizukuDot.setBackground(rounded(ACCENT_GREEN, STATUS_RADIUS, 0));
                tvShizuku.setText("Shizuku activo");
                tvShizuku.setTextColor(ACCENT_GREEN);
                btnShizuku.setVisibility(View.GONE);
                break;

            case ShizukuManager.NO_PERMISSION:
                shizukuDot.setBackground(rounded(ACCENT_AMBER, STATUS_RADIUS, 0));
                tvShizuku.setText("Falta permiso");
                tvShizuku.setTextColor(ACCENT_AMBER);
                btnShizuku.setVisibility(View.VISIBLE);
                btnShizuku.setText("Pedir permiso");
                btnShizuku.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        ShizukuManager.requestPermission(REQ_SHIZUKU_PERMISSION);
                    }
                });
                break;

            default:
                shizukuDot.setBackground(rounded(TEXT_MUTED, STATUS_RADIUS, 0));
                tvShizuku.setText("No disponible");
                tvShizuku.setTextColor(TEXT_DIM);
                btnShizuku.setVisibility(View.VISIBLE);
                btnShizuku.setText("Cómo instalar");
                btnShizuku.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://shizuku.rikka.app/download/")));
                    }
                });
                break;
        }
        if (tvLog != null) tvLog.setText(logText());
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) g.setStroke(dp(1), strokeColor);
        return g;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int topMarginDp) {
        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(topMarginDp);
        return p;
    }

    private LinearLayout.LayoutParams lpWRAP(int topMarginDp) {
        return lp(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, topMarginDp);
    }

    private LinearLayout.LayoutParams lpMATCH(int topMarginDp) {
        return lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, topMarginDp);
    }
}
