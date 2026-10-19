package com.accessdroid.termux;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Activity {

    private static final int BG_DARK      = 0xFF0B1F1D;
    private static final int BG_HEADER    = 0xFF1FBF8F;
    private static final int BG_CARD      = 0xFF12302C;
    private static final int BG_CARD_ALT  = 0xFF1A3A35;
    private static final int BG_INPUT     = 0xFF12302C;
    private static final int ACCENT_BLUE  = 0xFF1FBF8F;
    private static final int ACCENT_GREEN = 0xFF1FBF8F;
    private static final int ACCENT_RED   = 0xFFF0524F;
    private static final int ACCENT_AMBER = 0xFFF5B83D;
    private static final int ACCENT_VIOLET= 0xFF8B5CF6;
    private static final int TEXT_MAIN    = 0xFFE8F5F1;
    private static final int TEXT_DIM     = 0xFF8FB5AC;
    private static final int TEXT_MUTED   = 0xFF6B9488;
    private static final int LOG_BG       = 0xFF1A3A35;
    private static final int DIVIDER      = 0xFF1E4A44;
    private static final int CARD_RADIUS  = 12;
    private static final int STATUS_RADIUS= 999;

    private static final String SECRET = "termux-accessdroid-2025";
    private static final String PREFS_NAME = "costphone_prefs";
    private static final String KEY_ROUTINES = "routines_v4";
    private static final String KEY_ROUTINES_V3 = "routines_v3";
    private static final String KEY_ROUTINES_V2 = "routines_v2";
    private static final String KEY_THEME = "theme_mode";
    private static final String KEY_ROUTINE_INTERRUPTED = "routine_interrupted";

    private static final int TAB_INICIO    = 0;
    private static final int TAB_CONTROL   = 1;
    private static final int TAB_INSPECTOR = 2;
    private static final int TAB_RUTINAS   = 3;
    private static final int TAB_AJUSTES   = 4;

    private static final String[] INT_KEYS = {"x", "y", "x1", "y1", "x2", "y2"};
    private static final String[] LONG_KEYS = {"duration"};

    private View statusDot;
    private TextView tvStatus;
    private TextView tvStatusDesc;
    private View headerDot;
    private TextView headerStatusText;

    private View shizukuDot;
    private TextView tvShizuku;
    private Button btnShizuku;

    private EditText etInspectorSearch;

    private View tabInicio, tabControl, tabInspector, tabRutinas, tabAjustes;
    private LinearLayout panelInicio, panelControl, panelInspector, panelRutinas, panelAjustes;
    private LinearLayout contentArea;

    private Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

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
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        android.widget.FrameLayout outer = new android.widget.FrameLayout(this);
        outer.setBackgroundColor(BG_DARK);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG_DARK);

        LinearLayout headerArea = new LinearLayout(this);
        headerArea.setOrientation(LinearLayout.VERTICAL);
        root.addView(headerArea, lpMATCH(0));

        headerArea.addView(buildHeader(), lpMATCH(0));
        headerArea.addView(shizukuChip(), lpMATCH(8));

        ScrollView contentScroll = new ScrollView(this);
        contentScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        contentArea = new LinearLayout(this);
        contentArea.setOrientation(LinearLayout.VERTICAL);
        contentArea.setPadding(dp(16), dp(8), dp(16), dp(8));
        contentScroll.addView(contentArea);
        root.addView(contentScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        panelInicio = buildInicioPanel();
        panelControl = buildControlPanel();
        panelInspector = buildInspectorPanel();
        panelRutinas = buildRutinasPanel();
        panelAjustes = buildAjustesPanel();

        contentArea.addView(panelInicio);
        contentArea.addView(panelControl);
        contentArea.addView(panelInspector);
        contentArea.addView(panelRutinas);
        contentArea.addView(panelAjustes);

        View tabBar = buildTabBar();
        root.addView(tabBar, lpMATCH(0));

        android.widget.FrameLayout.LayoutParams outerLp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
        outerLp.gravity = Gravity.CENTER_HORIZONTAL;
        outer.addView(root, outerLp);

        setContentView(outer);

        // max-width ~600dp centrado en pantallas anchas
        final int maxWidthPx = dp(600);
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b,
                                                 int ol, int ot, int or, int ob) {
                int w = r - l;
                if (w > maxWidthPx) {
                    android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
                    if (lp instanceof android.widget.FrameLayout.LayoutParams) {
                        android.widget.FrameLayout.LayoutParams flp =
                                (android.widget.FrameLayout.LayoutParams) lp;
                        flp.width = maxWidthPx;
                        flp.gravity = Gravity.CENTER_HORIZONTAL;
                        v.setLayoutParams(flp);
                    }
                }
            }
        });

        // insets de barras del sistema (minSdk 24 → API directa)
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets insets) {
                int bottom = insets.getSystemWindowInsetBottom();
                int top = insets.getSystemWindowInsetTop();
                tabBar.setPadding(tabBar.getPaddingLeft(),
                        Math.max(dp(4), top),
                        tabBar.getPaddingRight(),
                        Math.max(dp(4), bottom));
                headerArea.setPadding(headerArea.getPaddingLeft(),
                        Math.max(0, top),
                        headerArea.getPaddingRight(),
                        headerArea.getPaddingBottom());
                return insets;
            }
        });

        showTab(TAB_INICIO);

        try {
            rikka.shizuku.Shizuku.addRequestPermissionResultListener(shizukuPermListener);
            ShizukuManager.registerStateListener(shizukuStateListener);
        } catch (Throwable ignored) { }

        String interrupted = prefs.getString(KEY_ROUTINE_INTERRUPTED, "");
        if (!interrupted.isEmpty()) {
            toast("La rutina '" + interrupted + "' fue interrumpida. Puedes ejecutarla de nuevo.");
            prefs.edit().remove(KEY_ROUTINE_INTERRUPTED).apply();
        }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
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

    private View buildTabBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(BG_CARD);
        bar.setPadding(dp(4), dp(4), dp(4), dp(4));

        tabInicio    = tabButton("Inicio",    R.drawable.ic_home,     TAB_INICIO);
        tabControl   = tabButton("Control",   R.drawable.ic_control,  TAB_CONTROL);
        tabInspector = tabButton("Inspector", R.drawable.ic_inspector,TAB_INSPECTOR);
        tabRutinas   = tabButton("Rutinas",   R.drawable.ic_routines, TAB_RUTINAS);
        tabAjustes   = tabButton("Ajustes",   R.drawable.ic_settings, TAB_AJUSTES);

        bar.addView(tabInicio,    tabLp());
        bar.addView(tabControl,   tabLp());
        bar.addView(tabInspector, tabLp());
        bar.addView(tabRutinas,   tabLp());
        bar.addView(tabAjustes,   tabLp());

        return bar;
    }

    private LinearLayout.LayoutParams tabLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.leftMargin = dp(2);
        p.rightMargin = dp(2);
        return p;
    }

    private View tabButton(String label, int iconRes, int index) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(4), dp(8), dp(4), dp(8));
        item.setTag(index);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(TEXT_DIM);
        item.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(11);
        tv.setTextColor(TEXT_DIM);
        tv.setGravity(Gravity.CENTER);
        item.addView(tv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        item.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showTab((int) v.getTag());
            }
        });
        return item;
    }

    private void showTab(int index) {
        panelInicio.setVisibility(index == TAB_INICIO ? View.VISIBLE : View.GONE);
        panelControl.setVisibility(index == TAB_CONTROL ? View.VISIBLE : View.GONE);
        panelInspector.setVisibility(index == TAB_INSPECTOR ? View.VISIBLE : View.GONE);
        panelRutinas.setVisibility(index == TAB_RUTINAS ? View.VISIBLE : View.GONE);
        panelAjustes.setVisibility(index == TAB_AJUSTES ? View.VISIBLE : View.GONE);

        styleTab(tabInicio, index == TAB_INICIO);
        styleTab(tabControl, index == TAB_CONTROL);
        styleTab(tabInspector, index == TAB_INSPECTOR);
        styleTab(tabRutinas, index == TAB_RUTINAS);
        styleTab(tabAjustes, index == TAB_AJUSTES);
    }

    private void styleTab(View tab, boolean active) {
        if (!(tab instanceof LinearLayout)) return;
        LinearLayout item = (LinearLayout) tab;
        item.setBackgroundResource(active ? R.drawable.bg_tab_active : 0);
        // icono
        if (item.getChildCount() > 0 && item.getChildAt(0) instanceof ImageView) {
            ((ImageView) item.getChildAt(0)).setColorFilter(active ? ACCENT_GREEN : TEXT_DIM);
        }
        // etiqueta
        if (item.getChildCount() > 1 && item.getChildAt(1) instanceof TextView) {
            ((TextView) item.getChildAt(1)).setTextColor(active ? ACCENT_GREEN : TEXT_DIM);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  PANTALLA: INICIO
    // ═══════════════════════════════════════════════════════════════

    private LinearLayout buildInicioPanel() {
        LinearLayout panel = createPanel();

        // tarjeta de estado de accesibilidad
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setBackground(getResources().getDrawable(R.drawable.bg_card));
        statusCard.setPadding(dp(20), dp(20), dp(20), dp(20));

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
        statusCard.addView(row);

        tvStatusDesc = new TextView(this);
        tvStatusDesc.setTextSize(13);
        tvStatusDesc.setTextColor(TEXT_DIM);
        tvStatusDesc.setLineSpacing(dp(5), 1f);
        tvStatusDesc.setMaxLines(3);
        tvStatusDesc.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = dp(12);
        statusCard.addView(tvStatusDesc, dlp);

        Button btnAccessibility = new Button(this);
        btnAccessibility.setText("Abrir ajustes de accesibilidad");
        btnAccessibility.setAllCaps(false);
        btnAccessibility.setTextSize(15);
        btnAccessibility.setTextColor(0xFFFFFFFF);
        btnAccessibility.setTypeface(Typeface.DEFAULT_BOLD);
        btnAccessibility.setBackground(rounded(ACCENT_BLUE, 12, 0));
        btnAccessibility.setPadding(dp(18), dp(14), dp(18), dp(14));
        btnAccessibility.setStateListAnimator(null);
        btnAccessibility.setMinimumHeight(dp(48));
        btnAccessibility.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(16);
        statusCard.addView(btnAccessibility, blp);

        panel.addView(statusCard);

        // accesos rápidos 2x2 con icono
        LinearLayout.LayoutParams q1lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        q1lp.topMargin = dp(16);
        panel.addView(sectionTitle("Accesos rápidos"), q1lp);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        glp.topMargin = dp(4);
        panel.addView(grid, glp);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        grid.addView(row1, gridRowLp());
        row1.addView(quickTile("Control",   R.drawable.ic_control,  TAB_CONTROL),   gridTileLp());
        row1.addView(quickTile("Inspector", R.drawable.ic_inspector,TAB_INSPECTOR), gridTileLp());

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        grid.addView(row2, gridRowLp());
        row2.addView(quickTile("Rutinas",   R.drawable.ic_routines, TAB_RUTINAS),   gridTileLp());
        row2.addView(quickTile("Ajustes",   R.drawable.ic_settings, TAB_AJUSTES),   gridTileLp());

        return panel;
    }

    private View quickTile(String label, int iconRes, final int tabIndex) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(16), dp(16), dp(16), dp(16));
        tile.setBackground(getResources().getDrawable(R.drawable.bg_card));
        tile.setMinimumHeight(dp(80));
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTab(tabIndex); }
        });

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(ACCENT_GREEN);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13);
        tv.setTextColor(TEXT_MAIN);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(8), 0, 0);
        tile.addView(tv);

        return tile;
    }

    private LinearLayout.LayoutParams gridRowLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(4);
        return p;
    }

    private LinearLayout.LayoutParams gridTileLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.leftMargin = dp(4);
        p.rightMargin = dp(4);
        return p;
    }

    // ═══════════════════════════════════════════════════════════════
    //  PANTALLA: CONTROL
    // ═══════════════════════════════════════════════════════════════

    private LinearLayout buildControlPanel() {
        LinearLayout panel = createPanel();

        // ── Gestos ──
        LinearLayout gesturesCard = sectionCard("Gestos", R.drawable.ic_control);

        final EditText etX = new EditText(this);
        etX.setTextColor(TEXT_MAIN);
        etX.setHintTextColor(TEXT_MUTED);
        etX.setHint("X (píxeles)");
        etX.setPadding(dp(14), dp(12), dp(14), dp(12));
        etX.setBackground(rounded(BG_INPUT, 10, DIVIDER));
        etX.setSingleLine();
        etX.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        gesturesCard.addView(etX, cardLp());

        final EditText etY = new EditText(this);
        etY.setTextColor(TEXT_MAIN);
        etY.setHintTextColor(TEXT_MUTED);
        etY.setHint("Y (píxeles)");
        etY.setPadding(dp(14), dp(12), dp(14), dp(12));
        etY.setBackground(rounded(BG_INPUT, 10, DIVIDER));
        etY.setSingleLine();
        etY.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        gesturesCard.addView(etY, cardLp());

        LinearLayout gestures = new LinearLayout(this);
        gestures.setOrientation(LinearLayout.HORIZONTAL);
        gestures.setWeightSum(3);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        glp.topMargin = dp(8);
        gesturesCard.addView(gestures, glp);

        Button btnTap = iconButton("Tap", R.drawable.ic_bolt, true);
        btnTap.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int x = parseCoord(etX.getText().toString());
                int y = parseCoord(etY.getText().toString());
                if (x >= 0 && y >= 0) {
                    if (isServiceAvailable()) {
                        sendTap(x, y, 80);
                    } else {
                        toast("Activa el servicio de accesibilidad primero");
                    }
                } else {
                    toast("Introduce coordenadas X e Y");
                }
            }
        });
        gestures.addView(btnTap, controlLp());

        Button btnSwipe = iconButton("Swipe", R.drawable.ic_bolt, false);
        btnSwipe.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int x = parseCoord(etX.getText().toString());
                int y = parseCoord(etY.getText().toString());
                if (x >= 0 && y >= 0) {
                    if (isServiceAvailable()) {
                        sendSwipe(x, y, x + 200, y, 300);
                    } else {
                        toast("Activa el servicio de accesibilidad primero");
                    }
                } else {
                    toast("Introduce coordenadas X e Y");
                }
            }
        });
        gestures.addView(btnSwipe, controlLp());

        Button btnLong = iconButton("Long press", R.drawable.ic_clock, false);
        btnLong.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int x = parseCoord(etX.getText().toString());
                int y = parseCoord(etY.getText().toString());
                if (x >= 0 && y >= 0) {
                    if (isServiceAvailable()) {
                        sendLongPress(x, y, 500);
                    } else {
                        toast("Activa el servicio de accesibilidad primero");
                    }
                } else {
                    toast("Introduce coordenadas X e Y");
                }
            }
        });
        gestures.addView(btnLong, controlLp());

        panel.addView(gesturesCard, sectionCardLp());

        // ── Navegación Android ──
        LinearLayout navCard = sectionCard("Navegación Android", R.drawable.ic_chevron_right);
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setWeightSum(3);
        navCard.addView(nav, cardLp());

        Button btnBack = iconButton("Atrás", R.drawable.ic_chevron_right, true);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendNavigation("back"); }
        });
        nav.addView(btnBack, controlLp());

        Button btnHome = iconButton("Inicio", R.drawable.ic_home, false);
        btnHome.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendNavigation("home"); }
        });
        nav.addView(btnHome, controlLp());

        Button btnRecents = iconButton("Recientes", R.drawable.ic_routines, false);
        btnRecents.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendNavigation("recents"); }
        });
        nav.addView(btnRecents, controlLp());

        panel.addView(navCard, sectionCardLp());

        // ── Escritura ──
        LinearLayout writeCard = sectionCard("Escritura", R.drawable.ic_info);
        final EditText etText = new EditText(this);
        etText.setTextColor(TEXT_MAIN);
        etText.setHintTextColor(TEXT_MUTED);
        etText.setHint("Texto a escribir");
        etText.setPadding(dp(14), dp(12), dp(14), dp(12));
        etText.setBackground(rounded(BG_INPUT, 10, DIVIDER));
        etText.setSingleLine(false);
        etText.setMinLines(2);
        etText.setMaxLines(4);
        writeCard.addView(etText, cardLp());

        LinearLayout writeRow = new LinearLayout(this);
        writeRow.setOrientation(LinearLayout.HORIZONTAL);
        writeRow.setWeightSum(2);
        LinearLayout.LayoutParams wrlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        wrlp.topMargin = dp(8);
        writeCard.addView(writeRow, wrlp);

        Button btnType = iconButton("Escribir", R.drawable.ic_bolt, true);
        btnType.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String text = etText.getText().toString();
                if (!text.isEmpty()) {
                    if (isServiceAvailable()) {
                        sendTypeText(text);
                    } else {
                        toast("Activa el servicio de accesibilidad primero");
                    }
                } else {
                    toast("Escribe texto primero");
                }
            }
        });
        writeRow.addView(btnType, controlLp());

        Button btnClear = iconButton("Limpiar texto", R.drawable.ic_trash, false);
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendClearText(); }
        });
        writeRow.addView(btnClear, controlLp());

        panel.addView(writeCard, sectionCardLp());

        // ── Elementos ──
        LinearLayout elemCard = sectionCard("Elementos", R.drawable.ic_inspector);
        final EditText etElement = new EditText(this);
        etElement.setTextColor(TEXT_MAIN);
        etElement.setHintTextColor(TEXT_MUTED);
        etElement.setHint("Texto visible o resource ID");
        etElement.setPadding(dp(14), dp(12), dp(14), dp(12));
        etElement.setBackground(rounded(BG_INPUT, 10, DIVIDER));
        etElement.setSingleLine();
        elemCard.addView(etElement, cardLp());

        LinearLayout elemRow = new LinearLayout(this);
        elemRow.setOrientation(LinearLayout.HORIZONTAL);
        elemRow.setWeightSum(2);
        LinearLayout.LayoutParams erlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        erlp.topMargin = dp(8);
        elemCard.addView(elemRow, erlp);

        Button btnClickText = iconButton("Click texto", R.drawable.ic_control, true);
        btnClickText.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String text = etElement.getText().toString();
                if (!text.isEmpty()) {
                    if (isServiceAvailable()) {
                        sendClickText(text);
                    } else {
                        toast("Activa el servicio de accesibilidad primero");
                    }
                } else {
                    toast("Escribe texto del elemento");
                }
            }
        });
        elemRow.addView(btnClickText, controlLp());

        Button btnClickId = iconButton("Click ID", R.drawable.ic_shield, false);
        btnClickId.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String id = etElement.getText().toString();
                if (!id.isEmpty()) {
                    if (isServiceAvailable()) {
                        sendClickId(id);
                    } else {
                        toast("Activa el servicio de accesibilidad primero");
                    }
                } else {
                    toast("Escribe resource ID");
                }
            }
        });
        elemRow.addView(btnClickId, controlLp());

        panel.addView(elemCard, sectionCardLp());

        // ── Avanzado ──
        LinearLayout advCard = sectionCard("Avanzado", R.drawable.ic_settings);
        LinearLayout adv = new LinearLayout(this);
        adv.setOrientation(LinearLayout.HORIZONTAL);
        adv.setWeightSum(2);
        advCard.addView(adv, cardLp());

        Button btnScroll = iconButton("Scroll abajo", R.drawable.ic_chevron_right, false);
        btnScroll.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (isServiceAvailable()) {
                    sendScrollDown();
                } else {
                    toast("Activa el servicio de accesibilidad primero");
                }
            }
        });
        adv.addView(btnScroll, controlLp());

        Button btnScreenshot = iconButton("Captura", R.drawable.ic_inspector, true);
        btnScreenshot.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (isServiceAvailable()) {
                    sendScreenshot();
                } else {
                    toast("Activa el servicio de accesibilidad primero");
                }
            }
        });
        adv.addView(btnScreenshot, controlLp());

        panel.addView(advCard, sectionCardLp());

        return panel;
    }

    private int parseCoord(String s) {
        if (s == null || s.trim().isEmpty()) return -1;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  PANTALLA: INSPECTOR
    // ═══════════════════════════════════════════════════════════════

    private LinearLayout buildInspectorPanel() {
        LinearLayout panel = createPanel();

        // ── Árbol de accesibilidad ──
        LinearLayout treeCard = sectionCard("Árbol de accesibilidad", R.drawable.ic_routines);
        Button btnTree = iconButton("Leer árbol UI", R.drawable.ic_routines, true);
        btnTree.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (isServiceAvailable()) {
                    sendGetUiTree();
                } else {
                    toast("Activa el servicio de accesibilidad primero");
                }
            }
        });
        treeCard.addView(btnTree, cardLp());

        TextView treeInfo = new TextView(this);
        treeInfo.setText("El árbol se guarda en /sdcard/Download/accessdroid-tree.txt y se lee con: amctl tree");
        treeInfo.setTextSize(11);
        treeInfo.setTextColor(TEXT_DIM);
        treeInfo.setMaxLines(2);
        treeInfo.setEllipsize(TextUtils.TruncateAt.END);
        treeInfo.setPadding(dp(4), dp(6), dp(4), dp(4));
        treeCard.addView(treeInfo, cardLp());

        panel.addView(treeCard, sectionCardLp());

        // ── Captura de pantalla ──
        LinearLayout shotCard = sectionCard("Captura de pantalla", R.drawable.ic_inspector);
        Button btnScreenshot = iconButton("Guardar captura PNG", R.drawable.ic_inspector, true);
        btnScreenshot.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (isServiceAvailable()) {
                    sendScreenshotDump();
                } else {
                    toast("Activa el servicio de accesibilidad primero");
                }
            }
        });
        shotCard.addView(btnScreenshot, cardLp());

        TextView shotInfo = new TextView(this);
        shotInfo.setText("La captura se guarda en /sdcard/Download/accessdroid-shot.png y se lee con: amctl see");
        shotInfo.setTextSize(11);
        shotInfo.setTextColor(TEXT_DIM);
        shotInfo.setMaxLines(2);
        shotInfo.setEllipsize(TextUtils.TruncateAt.END);
        shotInfo.setPadding(dp(4), dp(6), dp(4), dp(4));
        shotCard.addView(shotInfo, cardLp());

        panel.addView(shotCard, sectionCardLp());

        // ── Buscar elemento ──
        LinearLayout findCard = sectionCard("Buscar elemento", R.drawable.ic_inspector);
        etInspectorSearch = new EditText(this);
        etInspectorSearch.setTextColor(TEXT_MAIN);
        etInspectorSearch.setHintTextColor(TEXT_MUTED);
        etInspectorSearch.setHint("Texto visible del elemento");
        etInspectorSearch.setPadding(dp(14), dp(12), dp(14), dp(12));
        etInspectorSearch.setBackground(rounded(BG_INPUT, 10, DIVIDER));
        etInspectorSearch.setSingleLine();
        findCard.addView(etInspectorSearch, cardLp());

        Button btnFind = iconButton("Buscar y hacer click", R.drawable.ic_control, true);
        btnFind.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String text = etInspectorSearch.getText().toString();
                if (!text.isEmpty()) {
                    if (isServiceAvailable()) {
                        sendClickText(text);
                    } else {
                        toast("Activa el servicio de accesibilidad primero");
                    }
                } else {
                    toast("Escribe texto a buscar");
                }
            }
        });
        findCard.addView(btnFind, cardLp());

        panel.addView(findCard, sectionCardLp());

        // ── Estado de operaciones ──
        LinearLayout opsCard = sectionCard("Estado de operaciones", R.drawable.ic_info);
        TextView tvOps = new TextView(this);
        tvOps.setText("Los resultados se confirman leyendo los archivos generados o el ConnectionLog. El envío del broadcast no confirma ejecución.");
        tvOps.setTextSize(12);
        tvOps.setTextColor(TEXT_DIM);
        tvOps.setMaxLines(3);
        tvOps.setEllipsize(TextUtils.TruncateAt.END);
        tvOps.setPadding(dp(4), dp(4), dp(4), dp(4));
        opsCard.addView(tvOps, cardLp());

        panel.addView(opsCard, sectionCardLp());

        return panel;
    }

    // ═══════════════════════════════════════════════════════════════
    //  PANTALLA: RUTINAS
    // ═══════════════════════════════════════════════════════════════

    private LinearLayout buildRutinasPanel() {
        LinearLayout panel = createPanel();

        // ── Rutinas guardadas ──
        LinearLayout listCard = sectionCard("Rutinas guardadas", R.drawable.ic_routines);

        final LinearLayout routinesList = new LinearLayout(this);
        routinesList.setOrientation(LinearLayout.VERTICAL);
        listCard.addView(routinesList, cardLp());

        refreshRoutinesList(routinesList);
        panel.addView(listCard, sectionCardLp());

        // ── Crear rutina ──
        LinearLayout createCard = sectionCard("Crear rutina", R.drawable.ic_plus);
        final EditText etName = new EditText(this);
        etName.setTextColor(TEXT_MAIN);
        etName.setHintTextColor(TEXT_MUTED);
        etName.setHint("Nombre de la rutina");
        etName.setPadding(dp(14), dp(12), dp(14), dp(12));
        etName.setBackground(rounded(BG_INPUT, 10, DIVIDER));
        etName.setSingleLine();
        createCard.addView(etName, cardLp());

        Button btnSave = iconButton("Guardar nueva rutina", R.drawable.ic_plus, true);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String name = etName.getText().toString().trim();
                if (name.isEmpty()) {
                    toast("Ponle un nombre a la rutina");
                    return;
                }
                saveRoutine(name, new ArrayList<String[]>());
                etName.setText("");
                toast("Rutina '" + name + "' guardada. Añádele pasos desde la lista.");
            }
        });
        createCard.addView(btnSave, cardLp());
        panel.addView(createCard, sectionCardLp());

        // ── Añadir paso ──
        LinearLayout stepCard = sectionCard("Añadir paso a rutina", R.drawable.ic_bolt);
        final EditText etRoutineName = new EditText(this);
        etRoutineName.setTextColor(TEXT_MAIN);
        etRoutineName.setHintTextColor(TEXT_MUTED);
        etRoutineName.setHint("Nombre de la rutina");
        etRoutineName.setPadding(dp(14), dp(12), dp(14), dp(12));
        etRoutineName.setBackground(rounded(BG_INPUT, 10, DIVIDER));
        etRoutineName.setSingleLine();
        stepCard.addView(etRoutineName, cardLp());

        final EditText etAction = new EditText(this);
        etAction.setTextColor(TEXT_MAIN);
        etAction.setHintTextColor(TEXT_MUTED);
        etAction.setHint("Acción (tap, swipe, type, back, home...)");
        etAction.setPadding(dp(14), dp(12), dp(14), dp(12));
        etAction.setBackground(rounded(BG_INPUT, 10, DIVIDER));
        etAction.setSingleLine();
        stepCard.addView(etAction, cardLp());

        final EditText etParam = new EditText(this);
        etParam.setTextColor(TEXT_MAIN);
        etParam.setHintTextColor(TEXT_MUTED);
        etParam.setHint("Parámetros (ej: 100,200 o texto)");
        etParam.setPadding(dp(14), dp(12), dp(14), dp(12));
        etParam.setBackground(rounded(BG_INPUT, 10, DIVIDER));
        etParam.setSingleLine();
        stepCard.addView(etParam, cardLp());

        Button btnAddStep = iconButton("Añadir paso", R.drawable.ic_plus, true);
        btnAddStep.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String rName = etRoutineName.getText().toString().trim();
                String action = etAction.getText().toString().trim();
                String param = etParam.getText().toString().trim();
                if (rName.isEmpty() || action.isEmpty()) {
                    toast("Introduce nombre de rutina y acción");
                    return;
                }
                addStepToRoutine(rName, action, param);
                etAction.setText("");
                etParam.setText("");
                toast("Paso añadido a '" + rName + "'");
            }
        });
        stepCard.addView(btnAddStep, cardLp());

        panel.addView(stepCard, sectionCardLp());

        return panel;
    }

    private void refreshRoutinesList(LinearLayout container) {
        container.removeAllViews();
        List<String[]> routines = loadRoutines();
        if (routines.isEmpty()) {
            LinearLayout emptyCard = new LinearLayout(this);
            emptyCard.setOrientation(LinearLayout.VERTICAL);
            emptyCard.setGravity(Gravity.CENTER);
            emptyCard.setPadding(dp(16), dp(24), dp(16), dp(24));

            ImageView emptyIcon = new ImageView(this);
            emptyIcon.setImageResource(R.drawable.ic_routines);
            emptyIcon.setColorFilter(TEXT_MUTED);
            emptyCard.addView(emptyIcon, new LinearLayout.LayoutParams(dp(40), dp(40)));

            TextView empty = new TextView(this);
            empty.setText("No hay rutinas guardadas.\nCrea una con el formulario de abajo.");
            empty.setTextSize(13);
            empty.setTextColor(TEXT_MUTED);
            empty.setGravity(Gravity.CENTER);
            empty.setMaxLines(2);
            empty.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            elp.topMargin = dp(12);
            emptyCard.addView(empty, elp);

            container.addView(emptyCard);
            return;
        }
        for (final String[] routine : routines) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(10), dp(12), dp(10));
            card.setBackground(getResources().getDrawable(R.drawable.bg_card));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvName = new TextView(this);
            tvName.setText(routine[0]);
            tvName.setTextSize(14);
            tvName.setTextColor(TEXT_MAIN);
            tvName.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(tvName, tlp);

            Button btnEdit = new Button(this);
            btnEdit.setText("Ver/Editar");
            btnEdit.setAllCaps(false);
            btnEdit.setTextSize(11);
            btnEdit.setTextColor(0xFFFFFFFF);
            btnEdit.setBackground(rounded(ACCENT_BLUE, 8, 0));
            btnEdit.setPadding(dp(10), dp(6), dp(10), dp(6));
            btnEdit.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    showRoutineEditor(routine);
                }
            });
            row.addView(btnEdit);

            Button btnRun = new Button(this);
            btnRun.setText("Ejecutar");
            btnRun.setAllCaps(false);
            btnRun.setTextSize(11);
            btnRun.setTextColor(0xFFFFFFFF);
            btnRun.setBackground(rounded(ACCENT_GREEN, 8, 0));
            btnRun.setPadding(dp(10), dp(6), dp(10), dp(6));
            btnRun.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    executeRoutine(routine);
                }
            });
            row.addView(btnRun);

            Button btnDelete = new Button(this);
            btnDelete.setText("Eliminar");
            btnDelete.setAllCaps(false);
            btnDelete.setTextSize(11);
            btnDelete.setTextColor(ACCENT_RED);
            btnDelete.setBackground(rounded(BG_CARD_ALT, 8, 0));
            btnDelete.setPadding(dp(10), dp(6), dp(10), dp(6));
            btnDelete.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    new android.app.AlertDialog.Builder(SettingsActivity.this)
                            .setTitle("Eliminar rutina")
                            .setMessage("¿Eliminar '" + routine[0] + "'? Esta acción no se puede deshacer.")
                            .setPositiveButton("Eliminar", new android.content.DialogInterface.OnClickListener() {
                                @Override public void onClick(android.content.DialogInterface d, int which) {
                                    deleteRoutine(routine[0]);
                                    refreshRoutinesList(container);
                                }
                            })
                            .setNegativeButton("Cancelar", null)
                            .show();
                }
            });
            row.addView(btnDelete);

            card.addView(row);

            for (int i = 1; i < routine.length; i += 2) {
                String action = routine[i];
                String param = i + 1 < routine.length ? routine[i + 1] : "";
                TextView step = new TextView(this);
                step.setText((i / 2 + 1) + ". " + action + (param.isEmpty() ? "" : " (" + param + ")"));
                step.setTextSize(12);
                step.setTextColor(TEXT_DIM);
                step.setPadding(dp(16), dp(4), dp(16), dp(4));
                card.addView(step);
            }

            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(8);
            container.addView(card, rlp);
        }
    }

    private void showRoutineEditor(final String[] routine) {
        LinearLayout editor = (LinearLayout) panelRutinas.getChildAt(1);
        EditText etRoutineName = null;
        EditText etAction = null;
        EditText etParam = null;
        for (int i = 0; i < editor.getChildCount(); i++) {
            View child = editor.getChildAt(i);
            if (child instanceof EditText) {
                EditText et = (EditText) child;
                String hint = et.getHint() != null ? et.getHint().toString() : "";
                if (hint.contains("Nombre de la rutina")) etRoutineName = et;
                else if (hint.contains("Acción")) etAction = et;
                else if (hint.contains("Parámetros")) etParam = et;
            }
        }
        if (etRoutineName != null) etRoutineName.setText(routine[0]);
        if (etAction != null) etAction.setText("");
        if (etParam != null) etParam.setText("");
    }

    private void addStepToRoutine(String routineName, String action, String param) {
        List<String[]> routines = loadRoutines();
        for (String[] r : routines) {
            if (r[0].equals(routineName)) {
                String[] newSteps = new String[r.length + 2];
                System.arraycopy(r, 0, newSteps, 0, r.length);
                newSteps[r.length] = action;
                newSteps[r.length + 1] = param;
                routines.set(routines.indexOf(r), newSteps);
                saveRoutines(routines);
                return;
            }
        }
        String[] newRoutine = new String[]{routineName, action, param};
        routines.add(newRoutine);
        saveRoutines(routines);
    }

    private void deleteRoutine(String name) {
        List<String[]> routines = loadRoutines();
        routines.removeIf(r -> r[0].equals(name));
        saveRoutines(routines);
    }

    private void executeRoutine(final String[] routine) {
        int stepCount = (routine.length - 1) / 2;
        if (stepCount == 0) {
            toast("La rutina '" + routine[0] + "' no tiene pasos");
            return;
        }
        toast("Ejecutando '" + routine[0] + "' (" + stepCount + " pasos). Los resultados se confirman por ConnectionLog.");
        for (int i = 1; i < routine.length; i += 2) {
            final String action = routine[i];
            final String param = i + 1 < routine.length ? routine[i + 1] : "";
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    dispatchAction(action, param);
                }
            }, (i / 2) * 1500L);
        }
    }

    private List<String[]> loadRoutines() {
        List<String[]> result = new ArrayList<>();
        String rawV4 = prefs.getString(KEY_ROUTINES, "");
        if (!rawV4.isEmpty()) {
            String[] entries = rawV4.split("\n", -1);
            for (String entry : entries) {
                if (entry.trim().isEmpty()) continue;
                try {
                    JSONObject json = new JSONObject(entry);
                    String name = json.optString("name", "");
                    if (name.isEmpty()) continue;
                    JSONArray steps = json.optJSONArray("steps");
                    if (steps == null) continue;
                    String[] routine = new String[1 + steps.length() * 2];
                    routine[0] = name;
                    int idx = 1;
                    for (int i = 0; i < steps.length(); i++) {
                        JSONObject step = steps.optJSONObject(i);
                        if (step == null) continue;
                        String action = step.optString("action", "");
                        JSONObject params = step.optJSONObject("params");
                        String paramStr = "";
                        if (params != null) {
                            if (params.has("x") && params.has("y")) {
                                paramStr = params.optInt("x") + "," + params.optInt("y");
                            } else if (params.has("text")) {
                                paramStr = params.optString("text", "");
                            } else if (params.has("id")) {
                                paramStr = params.optString("id", "");
                            } else if (params.has("x1")) {
                                paramStr = params.optInt("x1") + "," + params.optInt("y1") + "," +
                                        params.optInt("x2") + "," + params.optInt("y2") + "," +
                                        params.optLong("duration", 300);
                            }
                        }
                        routine[idx++] = action;
                        routine[idx++] = paramStr;
                    }
                    result.add(routine);
                } catch (JSONException e) {
                    toast("Error leyendo rutina v4: datos corruptos. Se conservan los datos originales.");
                    return result;
                }
            }
            return result;
        }
        String rawV3 = prefs.getString(KEY_ROUTINES_V3, "");
        if (!rawV3.isEmpty()) {
            String[] entries = rawV3.split("\n", -1);
            for (String entry : entries) {
                if (entry.trim().isEmpty()) continue;
                try {
                    JSONObject json = new JSONObject(entry);
                    String name = json.optString("name", "");
                    if (name.isEmpty()) continue;
                    JSONArray steps = json.optJSONArray("steps");
                    if (steps == null) continue;
                    String[] routine = new String[1 + steps.length() * 2];
                    routine[0] = name;
                    int idx = 1;
                    for (int i = 0; i < steps.length(); i++) {
                        JSONObject step = steps.optJSONObject(i);
                        if (step == null) continue;
                        routine[idx++] = step.optString("action", "");
                        routine[idx++] = step.optString("param", "");
                    }
                    result.add(routine);
                } catch (JSONException e) {
                    break;
                }
            }
            if (!result.isEmpty()) {
                saveRoutines(result);
                return result;
            }
        }
        String rawV2 = prefs.getString(KEY_ROUTINES_V2, "");
        if (!rawV2.isEmpty()) {
            String[] entries = rawV2.split("\n", -1);
            for (String entry : entries) {
                if (entry.trim().isEmpty()) continue;
                String[] parts = entry.split("\\|", -1);
                if (parts.length >= 1) {
                    for (int i = 0; i < parts.length; i++) {
                        parts[i] = decodeRoutineField(parts[i]);
                    }
                    result.add(parts);
                }
            }
            if (!result.isEmpty()) {
                saveRoutines(result);
            }
        }
        return result;
    }

    private void saveRoutines(List<String[]> routines) {
        StringBuilder sb = new StringBuilder();
        for (String[] r : routines) {
            try {
                JSONObject json = new JSONObject();
                json.put("name", r[0]);
                JSONArray steps = new JSONArray();
                for (int i = 1; i < r.length; i += 2) {
                    JSONObject step = new JSONObject();
                    step.put("action", r[i]);
                    String param = i + 1 < r.length ? r[i + 1] : "";
                    JSONObject params = new JSONObject();
                    if (param.contains(",") && !param.contains(" ")) {
                        String[] parts = param.split(",");
                        if (parts.length == 2) {
                            params.put("x", parseCoord(parts[0]));
                            params.put("y", parseCoord(parts[1]));
                        } else if (parts.length >= 4) {
                            params.put("x1", parseCoord(parts[0]));
                            params.put("y1", parseCoord(parts[1]));
                            params.put("x2", parseCoord(parts[2]));
                            params.put("y2", parseCoord(parts[3]));
                            if (parts.length >= 5) {
                                try {
                                    params.put("duration", Long.parseLong(parts[4].trim()));
                                } catch (NumberFormatException e) {
                                    params.put("duration", 300);
                                }
                            }
                        }
                    } else if (!param.isEmpty()) {
                        params.put("text", param);
                    }
                    step.put("params", params);
                    steps.put(step);
                }
                json.put("steps", steps);
                sb.append(json.toString()).append("\n");
            } catch (JSONException e) {
                toast("Error guardando rutina: " + e.getMessage());
            }
        }
        prefs.edit().putString(KEY_ROUTINES, sb.toString()).apply();
    }

    private void saveRoutine(String name, List<String[]> actions) {
        List<String[]> routines = loadRoutines();
        String[] newRoutine = new String[1 + actions.size() * 2];
        newRoutine[0] = name;
        int idx = 1;
        for (String[] action : actions) {
            newRoutine[idx++] = action[0];
            newRoutine[idx++] = action[1] != null ? action[1] : "";
        }
        routines.add(newRoutine);
        saveRoutines(routines);
    }

    private String encodeRoutineField(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("|", "\\|").replace("\n", "\\n");
    }

    private String decodeRoutineField(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                if (next == '\\') sb.append('\\');
                else if (next == '|') sb.append('|');
                else if (next == 'n') sb.append('\n');
                else sb.append(next);
                i++;
            } else {
                sb.append(s.charAt(i));
            }
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    //  PANTALLA: AJUSTES
    // ═══════════════════════════════════════════════════════════════

    private LinearLayout buildAjustesPanel() {
        LinearLayout panel = createPanel();

        // ── Batería (una sola tarjeta) ──
        LinearLayout batCard = sectionCard("Batería", R.drawable.ic_battery);
        TextView batDesc = new TextView(this);
        batDesc.setText("Excluye CostPhone de la optimización de batería para que Android no lo cierre.");
        batDesc.setTextSize(13);
        batDesc.setTextColor(TEXT_DIM);
        batDesc.setLineSpacing(dp(4), 1f);
        batDesc.setMaxLines(3);
        batDesc.setEllipsize(TextUtils.TruncateAt.END);
        batCard.addView(batDesc, cardLp());

        Button btnBat = iconButton("Excluir de optimización", R.drawable.ic_battery, true);
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
        batCard.addView(btnBat, cardLp());
        panel.addView(batCard, sectionCardLp());

        // ── Registro de eventos ──
        LinearLayout logCard = sectionCard("Registro de eventos", R.drawable.ic_clock);

        // chip "En tiempo real"
        LinearLayout realtimeChip = new LinearLayout(this);
        realtimeChip.setOrientation(LinearLayout.HORIZONTAL);
        realtimeChip.setGravity(Gravity.CENTER_VERTICAL);
        realtimeChip.setPadding(dp(8), dp(3), dp(8), dp(3));
        realtimeChip.setBackground(rounded(0x1A1FBF8F, 999, 0));

        View rtDot = new View(this);
        rtDot.setBackground(rounded(ACCENT_GREEN, 999, 0));
        realtimeChip.addView(rtDot, lp(dp(6), dp(6), 0));

        TextView rtText = new TextView(this);
        rtText.setText("En tiempo real");
        rtText.setTextSize(11);
        rtText.setTextColor(ACCENT_GREEN);
        rtText.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams rtTlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rtTlp.leftMargin = dp(6);
        realtimeChip.addView(rtText, rtTlp);

        LinearLayout.LayoutParams rtLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rtLp.bottomMargin = dp(8);
        logCard.addView(realtimeChip, rtLp);

        // registro con puntos de color por tipo
        LinearLayout logContainer = new LinearLayout(this);
        logContainer.setOrientation(LinearLayout.VERTICAL);
        logContainer.setPadding(dp(12), dp(10), dp(12), dp(10));
        logContainer.setBackground(rounded(LOG_BG, 8, 0));
        populateLog(logContainer);
        logCard.addView(logContainer, cardLp());

        Button btnClearLog = iconButton("Limpiar registro", R.drawable.ic_trash, false);
        btnClearLog.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ConnectionLog.clear();
                populateLog(logContainer);
            }
        });
        logCard.addView(btnClearLog, cardLp());

        panel.addView(logCard, sectionCardLp());

        // ── Información ──
        LinearLayout infoCard = sectionCard("Información", R.drawable.ic_info);

        LinearLayout verRow = new LinearLayout(this);
        verRow.setOrientation(LinearLayout.HORIZONTAL);
        verRow.setGravity(Gravity.CENTER_VERTICAL);
        verRow.setPadding(dp(4), dp(4), dp(4), dp(4));

        TextView verLabel = new TextView(this);
        verLabel.setText("Versión");
        verLabel.setTextSize(13);
        verLabel.setTextColor(TEXT_DIM);
        LinearLayout.LayoutParams vllp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        verRow.addView(verLabel, vllp);

        TextView tvVersion = new TextView(this);
        tvVersion.setText(versionName());
        tvVersion.setTextSize(13);
        tvVersion.setTextColor(TEXT_MAIN);
        tvVersion.setTypeface(Typeface.DEFAULT_BOLD);
        verRow.addView(tvVersion);

        infoCard.addView(verRow, cardLp());
        panel.addView(infoCard, sectionCardLp());

        return panel;
    }

    private void populateLog(LinearLayout container) {
        container.removeAllViews();
        List<String> lines = ConnectionLog.dump();
        if (lines.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Sin eventos registrados todavía.");
            empty.setTextSize(12);
            empty.setTextColor(TEXT_MUTED);
            empty.setPadding(dp(4), dp(4), dp(4), dp(4));
            container.addView(empty);
            return;
        }
        for (String line : lines) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(2), dp(2), dp(2), dp(2));

            View dot = new View(this);
            int dotColor = logColorFor(line);
            dot.setBackground(rounded(dotColor, 999, 0));
            row.addView(dot, lp(dp(8), dp(8), 0));

            TextView tv = new TextView(this);
            tv.setText(line);
            tv.setTextSize(11);
            tv.setTextColor(TEXT_DIM);
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setMaxLines(2);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tvlp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvlp.leftMargin = dp(8);
            row.addView(tv, tvlp);

            container.addView(row);
        }
    }

    private int logColorFor(String line) {
        String lower = line.toLowerCase();
        if (lower.contains("desconect") || lower.contains("disconnect") || lower.contains("error") || lower.contains("fail"))
            return ACCENT_RED;
        if (lower.contains("aviso") || lower.contains("warning"))
            return ACCENT_AMBER;
        if (lower.contains("notific"))
            return ACCENT_GREEN;
        if (lower.contains("ui envió") || lower.contains("ui envio") || lower.contains("action"))
            return ACCENT_VIOLET;
        if (lower.contains("servicio") || lower.contains("service") || lower.contains("conectado"))
            return 0xFF3B9BFF; // info blue
        return TEXT_MUTED;
    }

    // ═══════════════════════════════════════════════════════════════
    //  DESPACHADOR DE ACCIONES — extras correctos según contrato real
    // ═══════════════════════════════════════════════════════════════

    private boolean isServiceAvailable() {
        return isServiceEnabled() && AccessibilityServiceImpl.sInstance != null
                && AccessibilityServiceImpl.sInstance.isConnected();
    }

    private void dispatchAction(String action, String param) {
        switch (action.toLowerCase()) {
            case "tap": {
                if (!param.isEmpty()) {
                    String[] parts = param.split(",");
                    if (parts.length == 2) {
                        int x = parseCoord(parts[0]);
                        int y = parseCoord(parts[1]);
                        if (x >= 0 && y >= 0 && isServiceAvailable()) {
                            sendTap(x, y, 80);
                        } else if (x < 0 || y < 0) {
                            toast("Coordenadas inválidas");
                        } else {
                            toast("Activa el servicio de accesibilidad primero");
                        }
                    } else {
                        toast("Formato: x,y");
                    }
                }
                break;
            }
            case "swipe": {
                if (!param.isEmpty()) {
                    String[] parts = param.split(",");
                    if (parts.length >= 4) {
                        int x1 = parseCoord(parts[0]);
                        int y1 = parseCoord(parts[1]);
                        int x2 = parseCoord(parts[2]);
                        int y2 = parseCoord(parts[3]);
                        if (x1 >= 0 && y1 >= 0 && x2 >= 0 && y2 >= 0 && isServiceAvailable()) {
                            sendSwipe(x1, y1, x2, y2, 300);
                        } else if (x1 < 0 || y1 < 0 || x2 < 0 || y2 < 0) {
                            toast("Coordenadas inválidas");
                        } else {
                            toast("Activa el servicio de accesibilidad primero");
                        }
                    } else {
                        toast("Formato: x1,y1,x2,y2");
                    }
                }
                break;
            }
            case "longpress": {
                if (!param.isEmpty()) {
                    String[] parts = param.split(",");
                    if (parts.length >= 2) {
                        int x = parseCoord(parts[0]);
                        int y = parseCoord(parts[1]);
                        if (x >= 0 && y >= 0 && isServiceAvailable()) {
                            sendLongPress(x, y, 500);
                        } else if (x < 0 || y < 0) {
                            toast("Coordenadas inválidas");
                        } else {
                            toast("Activa el servicio de accesibilidad primero");
                        }
                    } else {
                        toast("Formato: x,y");
                    }
                }
                break;
            }
            case "type": if (!param.isEmpty() && isServiceAvailable()) sendTypeText(param); else toast("Activa el servicio de accesibilidad primero"); break;
            case "cleartext": if (isServiceAvailable()) sendClearText(); else toast("Activa el servicio de accesibilidad primero"); break;
            case "back": if (isServiceAvailable()) sendNavigation("back"); else toast("Activa el servicio de accesibilidad primero"); break;
            case "home": if (isServiceAvailable()) sendNavigation("home"); else toast("Activa el servicio de accesibilidad primero"); break;
            case "recents": if (isServiceAvailable()) sendNavigation("recents"); else toast("Activa el servicio de accesibilidad primero"); break;
            case "scrolldown": if (isServiceAvailable()) sendScrollDown(); else toast("Activa el servicio de accesibilidad primero"); break;
            case "screenshot": if (isServiceAvailable()) sendScreenshot(); else toast("Activa el servicio de accesibilidad primero"); break;
            case "clicktext": if (!param.isEmpty() && isServiceAvailable()) sendClickText(param); else toast("Activa el servicio de accesibilidad primero"); break;
            case "clickid": if (!param.isEmpty() && isServiceAvailable()) sendClickId(param); else toast("Activa el servicio de accesibilidad primero"); break;
            case "tree": if (isServiceAvailable()) sendGetUiTree(); else toast("Activa el servicio de accesibilidad primero"); break;
            case "screenshotdump": if (isServiceAvailable()) sendScreenshotDump(); else toast("Activa el servicio de accesibilidad primero"); break;
            default: toast("Acción desconocida: " + action); break;
        }
    }

    private void sendTap(int x, int y, long duration) {
        sendBroadcastWithExtras("ACTION_TAP", new String[]{"x", "y", "duration"},
                new String[]{String.valueOf(x), String.valueOf(y), String.valueOf(duration)});
    }

    private void sendSwipe(int x1, int y1, int x2, int y2, long duration) {
        sendBroadcastWithExtras("ACTION_SWIPE", new String[]{"x1", "y1", "x2", "y2", "duration"},
                new String[]{String.valueOf(x1), String.valueOf(y1), String.valueOf(x2), String.valueOf(y2), String.valueOf(duration)});
    }

    private void sendLongPress(int x, int y, long duration) {
        sendBroadcastWithExtras("ACTION_LONG_PRESS", new String[]{"x", "y", "duration"},
                new String[]{String.valueOf(x), String.valueOf(y), String.valueOf(duration)});
    }

    private void sendTypeText(String text) {
        sendBroadcastWithExtras("ACTION_TYPE", new String[]{"text"}, new String[]{text});
    }

    private void sendClearText() {
        sendBroadcastWithExtras("ACTION_CLEAR_TEXT", new String[]{}, new String[]{});
    }

    private void sendClickText(String text) {
        sendBroadcastWithExtras("ACTION_CLICK_TEXT", new String[]{"text"}, new String[]{text});
    }

    private void sendClickId(String id) {
        sendBroadcastWithExtras("ACTION_CLICK_ID", new String[]{"id"}, new String[]{id});
    }

    private void sendNavigation(String nav) {
        String action = "ACTION_" + nav.toUpperCase();
        sendBroadcastWithExtras(action, new String[]{}, new String[]{});
    }

    private void sendScrollDown() {
        sendBroadcastWithExtras("ACTION_SCROLL_DOWN", new String[]{}, new String[]{});
    }

    private void sendScreenshot() {
        sendBroadcastWithExtras("ACTION_SCREENSHOT", new String[]{}, new String[]{});
    }

    private void sendGetUiTree() {
        sendBroadcastWithExtras("ACTION_GET_UI_TREE", new String[]{}, new String[]{});
        toast("Árbol enviado. Se guarda en /sdcard/Download/accessdroid-tree.txt");
    }

    private void sendScreenshotDump() {
        sendBroadcastWithExtras("ACTION_SCREENDUMP", new String[]{}, new String[]{});
        toast("Captura enviada. Se guarda en /sdcard/Download/accessdroid-shot.png");
    }

    private void sendBroadcastWithExtras(String action, String[] keys, String[] values) {
        if (keys == null || values == null || keys.length != values.length) {
            toast("Error interno: parámetros inconsistentes");
            return;
        }
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == null || keys[i].trim().isEmpty()) {
                toast("Error interno: clave vacía");
                return;
            }
            if (values[i] == null) {
                toast("Error: parámetro '" + keys[i] + "' es null");
                return;
            }
            if (isIntKey(keys[i])) {
                try {
                    Integer.parseInt(values[i].trim());
                } catch (NumberFormatException e) {
                    toast("Valor inválido para '" + keys[i] + "': " + values[i]);
                    return;
                }
            }
            if (isLongKey(keys[i])) {
                try {
                    Long.parseLong(values[i].trim());
                } catch (NumberFormatException e) {
                    toast("Valor inválido para '" + keys[i] + "': " + values[i]);
                    return;
                }
            }
        }
        try {
            Intent intent = new Intent("com.accessdroid.termux." + action);
            intent.setPackage("com.accessdroid.termux");
            intent.putExtra("secret", SECRET);
            for (int i = 0; i < keys.length; i++) {
                if (isIntKey(keys[i])) {
                    intent.putExtra(keys[i], Integer.parseInt(values[i].trim()));
                } else if (isLongKey(keys[i])) {
                    intent.putExtra(keys[i], Long.parseLong(values[i].trim()));
                } else {
                    intent.putExtra(keys[i], values[i]);
                }
            }
            sendBroadcast(intent);
            ConnectionLog.add("UI envió: " + action);
        } catch (Throwable t) {
            toast("Error al enviar: " + t.getMessage());
            ConnectionLog.add("UI error: " + action + " → " + t.getMessage());
        }
    }

    private boolean isIntKey(String key) {
        for (String k : INT_KEYS) if (k.equals(key)) return true;
        return false;
    }

    private boolean isLongKey(String key) {
        for (String k : LONG_KEYS) if (k.equals(key)) return true;
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    //  REFRESH DE ESTADOS
    // ═══════════════════════════════════════════════════════════════

    private void refreshStatus() {
        int state = serviceState();
        int color;
        String text;
        String desc;
        switch (state) {
            case 2:
                color = ACCENT_GREEN;
                text = "Conectado";
                desc = "El servicio de accesibilidad está activo y responde a los comandos.";
                break;
            case 1:
                color = ACCENT_AMBER;
                text = "Habilitado, sin conexión";
                desc = "El servicio está activado pero no ha establecido conexión todavía.";
                break;
            default:
                color = ACCENT_RED;
                text = "Desactivado";
                desc = "El servicio de accesibilidad está apagado. Actívalo para permitir el control remoto.";
                break;
        }
        if (statusDot != null) {
            statusDot.setBackground(rounded(color, STATUS_RADIUS, 0));
            tvStatus.setText(text);
            tvStatus.setTextColor(color);
            tvStatusDesc.setText(desc);
        }
        if (headerDot != null) {
            headerDot.setBackground(rounded(color, 999, 0));
            headerStatusText.setText(text);
        }
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

    private void refreshShizuku() {
        if (shizukuDot == null) return;
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
    }

    // ═══════════════════════════════════════════════════════════════
    //  BUILDERS DE UI
    // ═══════════════════════════════════════════════════════════════

    private LinearLayout createPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        return panel;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(12), dp(16), dp(12));
        header.setBackground(rounded(BG_HEADER, CARD_RADIUS, 0));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        logo.setPadding(dp(6), dp(6), dp(6), dp(6));
        logo.setBackground(rounded(ACCENT_BLUE, 8, 0));
        logo.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        header.addView(logo);

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setPadding(dp(12), dp(0), dp(0), dp(0));
        titleBlock.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText("CostPhone");
        title.setTextSize(20);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleBlock.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Centro de control de Android");
        sub.setTextSize(12);
        sub.setTextColor(0xFF8FB5AC);
        titleBlock.addView(sub);

        header.addView(titleBlock);

        // chip de estado con punto de color
        LinearLayout statusChip = new LinearLayout(this);
        statusChip.setOrientation(LinearLayout.HORIZONTAL);
        statusChip.setGravity(Gravity.CENTER_VERTICAL);
        statusChip.setPadding(dp(8), dp(4), dp(8), dp(4));
        statusChip.setBackground(rounded(0x33FFFFFF, 999, 0));

        headerDot = new View(this);
        headerDot.setBackground(rounded(TEXT_MUTED, 999, 0));
        statusChip.addView(headerDot, lp(dp(8), dp(8), 0));

        headerStatusText = new TextView(this);
        headerStatusText.setTextSize(11);
        headerStatusText.setTextColor(0xFF8FB5AC);
        headerStatusText.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams hstlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hstlp.leftMargin = dp(6);
        statusChip.addView(headerStatusText, hstlp);

        TextView version = new TextView(this);
        version.setText("v" + versionName());
        version.setTextSize(11);
        version.setTextColor(0xFF8FB5AC);
        version.setPadding(dp(8), dp(4), dp(8), dp(4));
        version.setBackground(rounded(0x33FFFFFF, 4, 0));
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        vlp.leftMargin = dp(8);
        header.addView(version, vlp);
        header.addView(statusChip);

        return header;
    }

    private View shizukuChip() {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(14), dp(10), dp(14), dp(10));
        chip.setBackground(getResources().getDrawable(R.drawable.bg_card));

        shizukuDot = new View(this);
        shizukuDot.setBackground(rounded(TEXT_MUTED, 999, 0));
        chip.addView(shizukuDot, lp(dp(10), dp(10), 0));

        tvShizuku = new TextView(this);
        tvShizuku.setTextSize(13);
        tvShizuku.setTextColor(TEXT_MAIN);
        tvShizuku.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(10);
        chip.addView(tvShizuku, tlp);

        TextView badge = new TextView(this);
        badge.setText("OPCIONAL");
        badge.setTextSize(9);
        badge.setTextColor(ACCENT_BLUE);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setPadding(dp(6), dp(2), dp(6), dp(2));
        badge.setBackground(rounded(0x1A1FBF8F, 4, 0));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.rightMargin = dp(8);
        chip.addView(badge, blp);

        btnShizuku = new Button(this);
        btnShizuku.setAllCaps(false);
        btnShizuku.setTextSize(12);
        btnShizuku.setTextColor(0xFFFFFFFF);
        btnShizuku.setTypeface(Typeface.DEFAULT_BOLD);
        btnShizuku.setBackground(rounded(ACCENT_BLUE, 8, 0));
        btnShizuku.setPadding(dp(10), dp(4), dp(10), dp(4));
        btnShizuku.setStateListAnimator(null);
        chip.addView(btnShizuku);

        return chip;
    }

    private LinearLayout sectionCard(String title, int iconRes) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(getResources().getDrawable(R.drawable.bg_card));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(ACCENT_GREEN);
        header.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));

        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(15);
        tv.setTextColor(TEXT_MAIN);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tvlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tvlp.leftMargin = dp(10);
        header.addView(tv, tvlp);

        card.addView(header);

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(12);
        return card;
    }

    private LinearLayout.LayoutParams sectionCardLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(12);
        return p;
    }

    private Button iconButton(String label, int iconRes, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(0xFFFFFFFF);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(getResources().getDrawable(
                primary ? R.drawable.bg_button_primary : R.drawable.bg_button_secondary));
        b.setPadding(dp(8), dp(10), dp(8), dp(10));
        b.setStateListAnimator(null);
        b.setMinimumHeight(dp(48));
        if (!primary) b.setTextColor(ACCENT_GREEN);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(3);
        lp.rightMargin = dp(3);
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(8);
        return p;
    }

    private TextView sectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(TEXT_MUTED);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(8), dp(4), dp(8), dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        lp.bottomMargin = dp(4);
        tv.setLayoutParams(lp);
        return tv;
    }

    private LinearLayout.LayoutParams sectionLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        return lp;
    }

    private Button controlButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(0xFFFFFFFF);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(rounded(ACCENT_BLUE, 10, 0));
        b.setPadding(dp(8), dp(10), dp(8), dp(10));
        b.setStateListAnimator(null);
        b.setMinimumHeight(dp(48));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(3);
        lp.rightMargin = dp(3);
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout.LayoutParams controlLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.leftMargin = dp(3);
        p.rightMargin = dp(3);
        return p;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
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
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
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
