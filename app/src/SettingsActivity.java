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

    // Tokens de colors.xml (Fase 2c): inicializados en onCreate vía Ui.color()
    private int BG_DARK, BG_HEADER, BG_CARD, BG_CARD_ALT, BG_INPUT;
    private int ACCENT_BLUE, ACCENT_GREEN, ACCENT_RED, ACCENT_AMBER, ACCENT_VIOLET;
    private int TEXT_MAIN, TEXT_DIM, TEXT_MUTED, LOG_BG, DIVIDER;
    private static final int CARD_RADIUS  = 20;
    private static final int STATUS_RADIUS= 999;
    private static final int MAX_LOG_ROWS = 80;

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
    private LinearLayout statusCardRef;
    private ImageView heroStatusIcon;
    private int a11yBtnIndex = -1;
    private LinearLayout ajustesLogContainer;
    private LinearLayout ajustesFilterRow;
    private String logFilter = "all";  // all | servicio | acciones | errores
    private View headerDot;
    private TextView headerStatusText;

    private View shizukuDot;
    private TextView tvShizuku;
    private Button btnShizuku;

    private EditText etInspectorSearch;

    private View tabInicio, tabControl, tabInspector, tabRutinas, tabAjustes;
    private LinearLayout panelInicio, panelControl, panelInspector, panelRutinas, panelAjustes;
    private LinearLayout contentArea;
    private int lastServiceState = -1;

    private Handler handler = new Handler(Looper.getMainLooper());
    private int statusRefreshRetries = 0;
    private final Runnable connectionRefreshRunnable = new Runnable() {
        @Override public void run() {
            refreshStatus();
            refreshShizuku();
            if (serviceState() != 2 && statusRefreshRetries > 0) {
                statusRefreshRetries--;
                handler.postDelayed(this, 700L);
            }
        }
    };
    private SharedPreferences prefs;

    private static final int REQ_SHIZUKU_PERMISSION = 41;

    private final rikka.shizuku.Shizuku.OnRequestPermissionResultListener shizukuPermListener =
            new rikka.shizuku.Shizuku.OnRequestPermissionResultListener() {
                @Override public void onRequestPermissionResult(int requestCode, int grantResult) {
                    if (requestCode == REQ_SHIZUKU_PERMISSION) {
                        if (grantResult == PackageManager.PERMISSION_GRANTED) ShizukuManager.connectNow();
                        refreshShizuku();
                    }
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
        CrashReporter.install(this);
        ConnectionLog.init(this);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        ShizukuManager.get();

        // Cargar tokens de colors.xml
        BG_DARK      = Ui.color(this, "bg");
        BG_HEADER    = Ui.color(this, "bg");
        BG_CARD      = Ui.color(this, "surface");
        BG_CARD_ALT  = Ui.color(this, "surface2");
        BG_INPUT     = Ui.color(this, "surface");
        ACCENT_BLUE  = Ui.color(this, "accent");
        ACCENT_GREEN = Ui.color(this, "ok");
        ACCENT_RED   = Ui.color(this, "error");
        ACCENT_AMBER = Ui.color(this, "aviso");
        ACCENT_VIOLET= Ui.color(this, "accent2");
        TEXT_MAIN    = Ui.color(this, "text");
        TEXT_DIM     = Ui.color(this, "text2");
        TEXT_MUTED   = Ui.color(this, "text3");
        LOG_BG       = Ui.color(this, "surface");
        DIVIDER      = Ui.color(this, "border");

        android.widget.FrameLayout outer = new android.widget.FrameLayout(this);
        outer.setBackgroundColor(BG_DARK);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG_DARK);

        LinearLayout headerArea = new LinearLayout(this);
        headerArea.setOrientation(LinearLayout.VERTICAL);
        root.addView(headerArea, lpMATCH(0));

        headerArea.addView(buildHeader(), lpMATCH(0));

        // Ocultar barra inferior con teclado abierto (ajusteResize + adjustNothing)
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        ScrollView contentScroll = new ScrollView(this);
        contentScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        contentArea = new LinearLayout(this);
        contentArea.setOrientation(LinearLayout.VERTICAL);
        contentArea.setPadding(dp(20), dp(12), dp(20), dp(18));
        contentScroll.addView(contentArea);
        root.addView(contentScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // Carga diferida: no construir todas las pantallas y sus listas al arrancar.
        // showTab() crea cada panel la primera vez que se necesita.

        final View tabBar = buildTabBar();
        LinearLayout.LayoutParams navLayout = lpMATCH(0);
        navLayout.setMargins(dp(14), dp(6), dp(14), dp(8));
        root.addView(tabBar, navLayout);

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
                android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
                if (!(lp instanceof android.widget.FrameLayout.LayoutParams)) return;
                android.widget.FrameLayout.LayoutParams flp =
                        (android.widget.FrameLayout.LayoutParams) lp;
                // Solo tocar LayoutParams si el valor realmente cambia (evita bucles).
                if (w > maxWidthPx && flp.width != maxWidthPx) {
                    flp.width = maxWidthPx;
                    flp.gravity = Gravity.CENTER_HORIZONTAL;
                    v.setLayoutParams(flp);
                } else if (w <= maxWidthPx && flp.width != android.widget.FrameLayout.LayoutParams.MATCH_PARENT) {
                    flp.width = android.widget.FrameLayout.LayoutParams.MATCH_PARENT;
                    flp.gravity = Gravity.CENTER_HORIZONTAL;
                    v.setLayoutParams(flp);
                }
            }
        });

        // Ocultar barra inferior cuando el teclado está abierto
        root.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    private final int threshold = dp(150);
                    @Override public void onGlobalLayout() {
                        android.graphics.Rect r = new android.graphics.Rect();
                        root.getWindowVisibleDisplayFrame(r);
                        // pantalla completa visible vs área visible (barra+teclado)
                        int screenH = root.getResources().getDisplayMetrics().heightPixels;
                        int covered = screenH - (r.bottom - r.top);
                        boolean keyboardOpen = covered > threshold;
                        int want = keyboardOpen ? View.GONE : View.VISIBLE;
                        if (tabBar.getVisibility() != want) tabBar.setVisibility(want);
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
        ShizukuManager.get();
        requestNotificationPermission();
        requestLegacyStoragePermission();
        // Si Android/OEM dejó habilitada Accesibilidad pero soltó el FGS,
        // recuperarlo desde una Activity visible es una ruta segura.
        if (isServiceEnabled()) KeepAliveService.start(this);
        refreshStatus();
        refreshShizuku();
        handler.removeCallbacks(connectionRefreshRunnable);
        statusRefreshRetries = 5;
        handler.postDelayed(connectionRefreshRunnable, 300L);
    }

    private static final int REQ_POST_NOTIFICATIONS = 42;
    private static final int REQ_LEGACY_STORAGE = 43;

    private void requestLegacyStoragePermission() {
        if (Build.VERSION.SDK_INT < 23 || Build.VERSION.SDK_INT > 28) return;
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) return;
        try {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQ_LEGACY_STORAGE);
        } catch (Throwable ignored) { }
    }

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
        bar.setBackground(getResources().getDrawable(R.drawable.bg_tab_bar));
        bar.setPadding(dp(7), dp(6), dp(7), dp(6));
        bar.setElevation(dp(8));
        bar.setClipToOutline(true);

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
        item.setPadding(dp(3), dp(7), dp(3), dp(7));
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

    private void ensureTabPanel(int index) {
        switch (index) {
            case TAB_INICIO:
                if (panelInicio == null) { panelInicio = buildInicioPanel(); contentArea.addView(panelInicio); }
                break;
            case TAB_CONTROL:
                if (panelControl == null) { panelControl = buildControlPanel(); contentArea.addView(panelControl); }
                break;
            case TAB_INSPECTOR:
                if (panelInspector == null) { panelInspector = buildInspectorPanel(); contentArea.addView(panelInspector); }
                break;
            case TAB_RUTINAS:
                if (panelRutinas == null) { panelRutinas = buildRutinasPanel(); contentArea.addView(panelRutinas); }
                break;
            case TAB_AJUSTES:
                if (panelAjustes == null) { panelAjustes = buildAjustesPanel(); contentArea.addView(panelAjustes); }
                break;
            default:
                return;
        }
    }

    private void showTab(int index) {
        ensureTabPanel(index);
        if (panelInicio != null) panelInicio.setVisibility(index == TAB_INICIO ? View.VISIBLE : View.GONE);
        if (panelControl != null) panelControl.setVisibility(index == TAB_CONTROL ? View.VISIBLE : View.GONE);
        if (panelInspector != null) panelInspector.setVisibility(index == TAB_INSPECTOR ? View.VISIBLE : View.GONE);
        if (panelRutinas != null) panelRutinas.setVisibility(index == TAB_RUTINAS ? View.VISIBLE : View.GONE);
        if (panelAjustes != null) panelAjustes.setVisibility(index == TAB_AJUSTES ? View.VISIBLE : View.GONE);

        styleTab(tabInicio, index == TAB_INICIO);
        styleTab(tabControl, index == TAB_CONTROL);
        styleTab(tabInspector, index == TAB_INSPECTOR);
        styleTab(tabRutinas, index == TAB_RUTINAS);
        styleTab(tabAjustes, index == TAB_AJUSTES);
    }

    private void styleTab(View tab, boolean active) {
        if (!(tab instanceof LinearLayout)) return;
        LinearLayout item = (LinearLayout) tab;
        // píldora translúcida DETRÁS de icono+etiqueta (fondo, no cubre)
        item.setBackgroundResource(active ? R.drawable.bg_tab_active : 0);
        int fg = active ? ACCENT_BLUE : TEXT_MUTED;
        if (item.getChildCount() > 0 && item.getChildAt(0) instanceof ImageView) {
            ((ImageView) item.getChildAt(0)).setColorFilter(fg);
        }
        if (item.getChildCount() > 1 && item.getChildAt(1) instanceof TextView) {
            ((TextView) item.getChildAt(1)).setTextColor(fg);
            ((TextView) item.getChildAt(1)).setTypeface(Typeface.create("sans-serif-medium", active ? Typeface.BOLD : Typeface.NORMAL));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  PANTALLA: INICIO
    // ═══════════════════════════════════════════════════════════════

    private LinearLayout buildInicioPanel() {
        LinearLayout panel = createPanel();
        panel.addView(welcomeHero(), lpMATCH(5));

        LinearLayout statusCard = Ui.card(this);
        GradientDrawable statusBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Ui.color(this, "hero"), Ui.color(this, "surface")});
        statusBg.setCornerRadius(dp(22));
        statusBg.setStroke(dp(1), Ui.color(this, "outline_accent"));
        statusCard.setBackground(statusBg);
        statusCard.setElevation(dp(2));
        statusCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout iconRing = new LinearLayout(this);
        iconRing.setGravity(Gravity.CENTER);
        iconRing.setBackground(Ui.shape(this, "surface2", 17f, "outline_accent", 1.5f));
        ImageView statusIcon = new ImageView(this);
        statusIcon.setImageResource(R.drawable.ic_connected);
        statusIcon.setColorFilter(ACCENT_GREEN);
        iconRing.addView(statusIcon, new LinearLayout.LayoutParams(dp(28), dp(28)));
        statusRow.addView(iconRing, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout statusCopy = new LinearLayout(this);
        statusCopy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams statusCopyLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        statusCopyLp.leftMargin = dp(13);
        statusRow.addView(statusCopy, statusCopyLp);

        TextView eyebrow = new TextView(this);
        eyebrow.setText("ESTADO DEL SISTEMA");
        eyebrow.setTextSize(9);
        eyebrow.setLetterSpacing(0.09f);
        eyebrow.setTextColor(TEXT_MUTED);
        eyebrow.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        statusCopy.addView(eyebrow);

        tvStatus = new TextView(this);
        tvStatus.setTextSize(18);
        tvStatus.setTextColor(TEXT_MAIN);
        tvStatus.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        statusCopy.addView(tvStatus, lpMATCH(3));

        tvStatusDesc = new TextView(this);
        tvStatusDesc.setTextSize(12);
        tvStatusDesc.setTextColor(TEXT_DIM);
        tvStatusDesc.setMaxLines(2);
        tvStatusDesc.setEllipsize(TextUtils.TruncateAt.END);
        statusCopy.addView(tvStatusDesc, lpMATCH(3));

        statusCard.addView(statusRow);
        heroStatusIcon = statusIcon;
        statusCardRef = statusCard;
        statusDot = new View(this);
        statusDot.setVisibility(View.GONE);
        statusCard.addView(statusDot, lp(0, 0, 0));

        View accessButton = a11yButton(serviceState());
        a11yBtnIndex = statusCard.getChildCount();
        statusCard.addView(accessButton, lpMATCH(13));
        panel.addView(statusCard, lpMATCH(12));

        panel.addView(sectionTitle("EXPLORA COSTPHONE"), lpMATCH(4));
        panel.addView(featureRow("Control del dispositivo",
                "Toca, navega, desliza y escribe desde Termux.",
                R.drawable.ic_control, ACCENT_BLUE, new View.OnClickListener() {
                    @Override public void onClick(View v) { showTab(TAB_CONTROL); }
                }), sectionCardLp());
        panel.addView(featureRow("Inspector de pantalla",
                "Busca por texto o ID; captura solo cuando haga falta.",
                R.drawable.ic_inspector, ACCENT_VIOLET, new View.OnClickListener() {
                    @Override public void onClick(View v) { showTab(TAB_INSPECTOR); }
                }), sectionCardLp());
        panel.addView(featureRow("Rutinas y secuencias",
                "Agrupa acciones frecuentes y ejecútalas con menos pausas.",
                R.drawable.ic_routines, ACCENT_GREEN, new View.OnClickListener() {
                    @Override public void onClick(View v) { showTab(TAB_RUTINAS); }
                }), sectionCardLp());

        TextView footer = new TextView(this);
        footer.setText("COSTPHONE  ·  CONTROL LOCAL PARA ANDROID");
        footer.setTextSize(9);
        footer.setLetterSpacing(0.05f);
        footer.setTextColor(TEXT_MUTED);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = lpMATCH(10);
        footerLp.bottomMargin = dp(10);
        panel.addView(footer, footerLp);
        return panel;
    }

    private View welcomeHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(17), dp(18), dp(13), dp(18));
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Ui.color(this, "hero"), Ui.color(this, "surface")});
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(1), Ui.color(this, "outline_accent"));
        hero.setBackground(bg);
        hero.setElevation(dp(2));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        copyLp.rightMargin = dp(8);
        hero.addView(copy, copyLp);

        TextView eyebrow = new TextView(this);
        eyebrow.setText("CONTROL INTELIGENTE");
        eyebrow.setTextSize(9);
        eyebrow.setLetterSpacing(0.09f);
        eyebrow.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        eyebrow.setTextColor(ACCENT_BLUE);
        copy.addView(eyebrow);

        TextView headline = new TextView(this);
        android.text.SpannableString headlineText =
                new android.text.SpannableString("Tu IA trabaja\\npor ti.");
        int accentStart = headlineText.toString().indexOf("por ti.");
        headlineText.setSpan(new android.text.style.ForegroundColorSpan(ACCENT_BLUE),
                accentStart, headlineText.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        headline.setText(headlineText);
        headline.setTextSize(23);
        headline.setLineSpacing(dp(1), 1f);
        headline.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        headline.setTextColor(TEXT_MAIN);
        headline.setMaxLines(2);
        copy.addView(headline, lpMATCH(5));

        TextView description = new TextView(this);
        description.setText("Tu teléfono, al alcance de tu agente desde Termux.");
        description.setTextSize(12);
        description.setTextColor(TEXT_DIM);
        description.setMaxLines(2);
        description.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(description, lpMATCH(7));

        LinearLayout tags = new LinearLayout(this);
        tags.setOrientation(LinearLayout.HORIZONTAL);
        tags.setGravity(Gravity.CENTER_VERTICAL);
        tags.addView(heroTag("INTEGRACIÓN TERMUX"), lpWRAP(0));
        tags.addView(heroTag("SIN ROOT"), lpWRAP(0));
        copy.addView(tags, lpMATCH(0));

        ImageView illustration = new ImageView(this);
        illustration.setImageResource(R.drawable.ic_ai_orbit);
        illustration.setScaleType(ImageView.ScaleType.FIT_CENTER);
        illustration.setContentDescription("Ilustración de CostPhone");
        hero.addView(illustration, new LinearLayout.LayoutParams(dp(82), dp(82)));
        return hero;
    }

    private View heroTag(String label) {
        TextView tag = new TextView(this);
        tag.setText(label);
        tag.setTextSize(7.5f);
        tag.setLetterSpacing(0.015f);
        tag.setTextColor(TEXT_DIM);
        tag.setPadding(dp(7), dp(5), dp(7), dp(5));
        tag.setBackground(rounded((ACCENT_BLUE & 0x00FFFFFF) | 0x18000000,
                999, Ui.color(this, "outline_accent")));
        return tag;
    }

    private View a11yButton(int state) {
        final View.OnClickListener go = new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
                catch (Throwable t) { toast("No se pudieron abrir los ajustes de accesibilidad"); }
            }
        };
        if (state == 2) {
            return Ui.ghostButton(this, R.drawable.ic_accessibility, "Gestionar accesibilidad", go);
        }
        if (state == 1) {
            return Ui.primaryButton(this, R.drawable.ic_accessibility, "Reparar conexión", go);
        }
        return Ui.primaryButton(this, R.drawable.ic_accessibility, "Activar accesibilidad", go);
    }

    private View actionTile(String label, int iconRes, View.OnClickListener onClick) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(7), dp(13), dp(7), dp(13));
        tile.setBackground(Ui.ripple(this,
                Ui.shape(this, "surface2", 15f, "border", 1f),
                Ui.shape(this, "surface2", 15f, null, 0),
                (ACCENT_BLUE & 0x00FFFFFF) | 0x2D000000));
        tile.setMinimumHeight(dp(88));
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setOnClickListener(onClick);

        LinearLayout iconShell = new LinearLayout(this);
        iconShell.setGravity(Gravity.CENTER);
        iconShell.setBackground(Ui.shape(this, "surface", 11f, "border", 1f));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(ACCENT_BLUE);
        iconShell.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        tile.addView(iconShell, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(11);
        tv.setTextColor(TEXT_MAIN);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(2);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tvlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tvlp.topMargin = dp(7);
        tile.addView(tv, tvlp);
        return tile;
    }

    private View featureRow(String title, String description, int iconRes, int accent,
                             View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(10), dp(12));
        row.setMinimumHeight(dp(78));
        row.setBackground(Ui.ripple(this,
                Ui.shape(this, "surface2", 19f, "border", 1f),
                Ui.shape(this, "hero", 19f, "outline_accent", 1f),
                (accent & 0x00FFFFFF) | 0x26000000));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(onClick);

        LinearLayout iconShell = new LinearLayout(this);
        iconShell.setGravity(Gravity.CENTER);
        iconShell.setBackground(rounded((accent & 0x00FFFFFF) | 0x22000000,
                14, (accent & 0x00FFFFFF) | 0x60000000));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(accent);
        iconShell.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        row.addView(iconShell, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        copyLp.leftMargin = dp(12);
        copyLp.rightMargin = dp(8);
        row.addView(copy, copyLp);

        TextView label = new TextView(this);
        label.setText(title);
        label.setTextSize(14);
        label.setTextColor(TEXT_MAIN);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        label.setMaxLines(1);
        label.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(label);

        TextView summary = new TextView(this);
        summary.setText(description);
        summary.setTextSize(11);
        summary.setTextColor(TEXT_DIM);
        summary.setMaxLines(2);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(summary, lpMATCH(3));

        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_chevron_right);
        arrow.setColorFilter(TEXT_MUTED);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(20), dp(20)));
        row.setContentDescription(title + ". " + description);
        return row;
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
        panel.addView(pageIntro("CONTROL DEL DISPOSITIVO",
                "Hazlo directamente", "Gestos, navegación y escritura con menos pasos.",
                R.drawable.ic_control, ACCENT_BLUE), lpMATCH(3));

        // ── Gestos ──
        LinearLayout gesturesCard = sectionCard("Gestos", R.drawable.ic_touch);

        final EditText etX = new EditText(this);
        etX.setTextColor(TEXT_MAIN);
        etX.setHintTextColor(TEXT_MUTED);
        etX.setHint("X (píxeles)");
        etX.setPadding(dp(14), dp(12), dp(14), dp(12));
        etX.setBackground(rounded(BG_INPUT, 10, DIVIDER));
        etX.setSingleLine();
        etX.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etX.setMinHeight(dp(48));
        gesturesCard.addView(etX, cardLp());

        final EditText etY = new EditText(this);
        etY.setTextColor(TEXT_MAIN);
        etY.setHintTextColor(TEXT_MUTED);
        etY.setHint("Y (píxeles)");
        etY.setPadding(dp(14), dp(12), dp(14), dp(12));
        etY.setBackground(rounded(BG_INPUT, 10, DIVIDER));
        etY.setSingleLine();
        etY.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etY.setMinHeight(dp(48));
        gesturesCard.addView(etY, cardLp());

        LinearLayout gestures = new LinearLayout(this);
        gestures.setOrientation(LinearLayout.HORIZONTAL);
        gestures.setWeightSum(3);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        glp.topMargin = dp(8);
        gesturesCard.addView(gestures, glp);

        Button btnTap = iconButton("Tap", R.drawable.ic_touch, false);
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

        Button btnSwipe = iconButton("Swipe", R.drawable.ic_touch, false);
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

        Button btnLong = iconButton("Long press", R.drawable.ic_pulse, false);
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
        LinearLayout navCard = sectionCard("Navegación Android", R.drawable.ic_nav);
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setWeightSum(3);
        navCard.addView(nav, cardLp());

        Button btnBack = iconButton("Atrás", R.drawable.ic_arrow_back, false);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendNavigation("back"); }
        });
        nav.addView(btnBack, controlLp());

        Button btnHome = iconButton("Inicio", R.drawable.ic_home, false);
        btnHome.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendNavigation("home"); }
        });
        nav.addView(btnHome, controlLp());

        Button btnRecents = iconButton("Recientes", R.drawable.ic_nav, false);
        btnRecents.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendNavigation("recents"); }
        });
        nav.addView(btnRecents, controlLp());

        panel.addView(navCard, sectionCardLp());

        // ── Escritura ──
        LinearLayout writeCard = sectionCard("Escritura", R.drawable.ic_keyboard);
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

        Button btnType = iconButton("Escribir", R.drawable.ic_keyboard, true);
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

        Button btnClear = iconButton("Limpiar texto", R.drawable.ic_eraser, false);
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendClearText(); }
        });
        writeRow.addView(btnClear, controlLp());

        panel.addView(writeCard, sectionCardLp());

        // ── Elementos ──
        LinearLayout elemCard = sectionCard("Elementos", R.drawable.ic_crosshair);
        final EditText etElement = new EditText(this);
        etElement.setTextColor(TEXT_MAIN);
        etElement.setHintTextColor(TEXT_MUTED);
        etElement.setHint("Texto visible o resource ID");
        etElement.setPadding(dp(14), dp(12), dp(14), dp(12));
        etElement.setBackground(rounded(BG_INPUT, 10, DIVIDER));
        etElement.setSingleLine();
        etElement.setMinHeight(dp(48));
        elemCard.addView(etElement, cardLp());

        LinearLayout elemRow = new LinearLayout(this);
        elemRow.setOrientation(LinearLayout.HORIZONTAL);
        elemRow.setWeightSum(2);
        LinearLayout.LayoutParams erlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        erlp.topMargin = dp(8);
        elemCard.addView(elemRow, erlp);

        Button btnClickText = iconButton("Click texto", R.drawable.ic_crosshair, true);
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

        Button btnClickId = iconButton("Click ID", R.drawable.ic_search, false);
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
        LinearLayout advCard = sectionCard("Avanzado", R.drawable.ic_sliders);
        LinearLayout adv = new LinearLayout(this);
        adv.setOrientation(LinearLayout.HORIZONTAL);
        adv.setWeightSum(2);
        advCard.addView(adv, cardLp());

        Button btnScroll = iconButton("Scroll abajo", R.drawable.ic_arrow_down, false);
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

        Button btnScreenshot = iconButton("Captura", R.drawable.ic_camera, true);
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
        panel.addView(pageIntro("INSPECTOR DE PANTALLA",
                "Encuentra y actúa", "Primero texto o ID; captura solo cuando haga falta.",
                R.drawable.ic_inspector, ACCENT_VIOLET), lpMATCH(3));

        // ── Árbol de accesibilidad ──
        LinearLayout treeCard = sectionCard("Árbol de accesibilidad", R.drawable.ic_tree);
        Button btnTree = iconButton("Leer árbol UI", R.drawable.ic_tree, true);
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
        LinearLayout shotCard = sectionCard("Captura de pantalla", R.drawable.ic_camera);
        Button btnScreenshot = iconButton("Guardar captura PNG", R.drawable.ic_camera, true);
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
        LinearLayout findCard = sectionCard("Buscar elemento", R.drawable.ic_search);
        etInspectorSearch = new EditText(this);
        etInspectorSearch.setTextColor(TEXT_MAIN);
        etInspectorSearch.setHintTextColor(TEXT_MUTED);
        etInspectorSearch.setHint("Texto visible del elemento");
        etInspectorSearch.setPadding(dp(14), dp(12), dp(14), dp(12));
        etInspectorSearch.setBackground(rounded(BG_INPUT, 10, DIVIDER));
        etInspectorSearch.setSingleLine();
        etInspectorSearch.setMinHeight(dp(48));
        findCard.addView(etInspectorSearch, cardLp());

        Button btnFind = iconButton("Buscar y hacer click", R.drawable.ic_search, true);
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

        return panel;
    }

    // ═══════════════════════════════════════════════════════════════
    //  PANTALLA: RUTINAS
    // ═══════════════════════════════════════════════════════════════

    private LinearLayout buildRutinasPanel() {
        LinearLayout panel = createPanel();
        panel.addView(pageIntro("AUTOMATIZACIÓN",
                "Menos pasos, más trabajo", "Agrupa acciones repetidas y ejecútalas en secuencia.",
                R.drawable.ic_routines, ACCENT_GREEN), lpMATCH(3));

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
            LinearLayout empty = Ui.emptyState(this, R.drawable.ic_routines,
                    "No hay rutinas guardadas todavía.");
            View createBtn = Ui.primaryButton(this, R.drawable.ic_plus, "Crear rutina",
                    new View.OnClickListener() {
                        @Override public void onClick(View v) { showTab(TAB_RUTINAS); }
                    });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.topMargin = dp(16);
            empty.addView(createBtn, clp);
            container.addView(empty);
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
            tvName.setMaxLines(1);
            tvName.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(tvName, tlp);

            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);

            Button btnEdit = new Button(this);
            btnEdit.setText("Ver/Editar");
            btnEdit.setAllCaps(false);
            btnEdit.setTextSize(11);
            btnEdit.setTextColor(Ui.color(this, "on_accent"));
            btnEdit.setBackground(rounded(ACCENT_BLUE, 11, 0));
            btnEdit.setPadding(dp(10), dp(6), dp(10), dp(6));
            btnEdit.setMinimumHeight(dp(48));
            btnEdit.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    showRoutineEditor(routine);
                }
            });
            btnRow.addView(btnEdit);

            Button btnRun = new Button(this);
            btnRun.setText("Ejecutar");
            btnRun.setAllCaps(false);
            btnRun.setTextSize(11);
            btnRun.setTextColor(Ui.color(this, "on_accent"));
            btnRun.setBackground(rounded(ACCENT_GREEN, 11, 0));
            btnRun.setPadding(dp(10), dp(6), dp(10), dp(6));
            btnRun.setMinimumHeight(dp(48));
            btnRun.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    executeRoutine(routine);
                }
            });
            btnRow.addView(btnRun);

            Button btnDelete = new Button(this);
            btnDelete.setText("Eliminar");
            btnDelete.setAllCaps(false);
            btnDelete.setTextSize(11);
            btnDelete.setTextColor(ACCENT_RED);
            btnDelete.setBackground(rounded(BG_CARD_ALT, 8, 0));
            btnDelete.setPadding(dp(10), dp(6), dp(10), dp(6));
            btnDelete.setMinimumHeight(dp(48));
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
            btnRow.addView(btnDelete);

            card.addView(row);

            LinearLayout.LayoutParams brlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            brlp.topMargin = dp(8);
            card.addView(btnRow, brlp);

            for (int i = 1; i < routine.length; i += 2) {
                String action = routine[i];
                String param = i + 1 < routine.length ? routine[i + 1] : "";
                TextView step = new TextView(this);
                step.setText((i / 2 + 1) + ". " + action + (param.isEmpty() ? "" : " (" + param + ")"));
                step.setTextSize(12);
                step.setTextColor(TEXT_DIM);
                step.setPadding(dp(16), dp(4), dp(16), dp(4));
                step.setMaxLines(2);
                step.setEllipsize(TextUtils.TruncateAt.END);
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
        LinearLayout editor = (LinearLayout) panelRutinas.getChildAt(2);
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
        long offsetMs = 0L;
        for (int i = 1; i < routine.length; i += 2) {
            final String action = routine[i];
            final String param = i + 1 < routine.length ? routine[i + 1] : "";
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    dispatchAction(action, param);
                }
            }, offsetMs);
            offsetMs += routineStepDelayMs(action);
        }
    }

    private long routineStepDelayMs(String action) {
        if (action == null) return 450L;
        switch (action.toLowerCase()) {
            case "type":
            case "cleartext":
            case "paste": return 350L;
            case "clicktext":
            case "clickid":
            case "tap": return 550L;
            case "swipe":
            case "longpress":
            case "back":
            case "home":
            case "recents":
            case "scrolldown": return 700L;
            case "screenshot":
            case "screenshotdump":
            case "tree": return 450L;
            default: return 500L;
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
        panel.addView(pageIntro("SISTEMA Y CONEXIÓN",
                "Todo bajo control", "Permisos, Shizuku, batería y registro de errores.",
                R.drawable.ic_settings, ACCENT_AMBER), lpMATCH(3));
        panel.addView(shizukuChip(), lpMATCH(8));

        // ── Batería: chip de estado real + acción ──
        LinearLayout batCard = sectionCard("Batería", R.drawable.ic_battery);

        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        boolean excluded = false;
        if (pm != null && Build.VERSION.SDK_INT >= 23) {
            excluded = pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        batCard.addView(Ui.chip(this, excluded ? "Excluida" : "No excluida",
                excluded ? "ok" : "aviso"), lpWRAP(0));

        TextView batDesc = new TextView(this);
        batDesc.setText("Excluye CostPhone de la optimización de batería para que Android no lo cierre.");
        batDesc.setTextSize(13);
        batDesc.setTextColor(TEXT_DIM);
        batDesc.setLineSpacing(dp(4), 1f);
        batDesc.setMaxLines(3);
        batDesc.setEllipsize(TextUtils.TruncateAt.END);
        batCard.addView(batDesc, cardLp());

        Button btnBat = iconButton("Excluir de optimización", R.drawable.ic_battery, false);
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

        // ── Registro: filtros + limpiar (ghost en el título) + filas con icono ──
        View ghostClear = Ui.ghostButton(this, R.drawable.ic_trash, "Limpiar",
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        ConnectionLog.clear();
                        populateLog();
                    }
                });
        LinearLayout logCard = sectionCard("Registro de eventos", R.drawable.ic_clock, ghostClear);

        ajustesFilterRow = new LinearLayout(this);
        ajustesFilterRow.setOrientation(LinearLayout.HORIZONTAL);
        ajustesFilterRow.setGravity(Gravity.CENTER_VERTICAL);
        ajustesFilterRow.addView(filterChip("Todo", "all"), lpWRAP(0));
        ajustesFilterRow.addView(filterChip("Servicio", "servicio"), lpWRAP(0));
        ajustesFilterRow.addView(filterChip("Acciones", "acciones"), lpWRAP(0));
        ajustesFilterRow.addView(filterChip("Errores", "errores"), lpWRAP(0));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        flp.bottomMargin = dp(8);
        logCard.addView(ajustesFilterRow, flp);

        ajustesLogContainer = new LinearLayout(this);
        ajustesLogContainer.setOrientation(LinearLayout.VERTICAL);
        ajustesLogContainer.setPadding(dp(12), dp(10), dp(12), dp(10));
        ajustesLogContainer.setBackground(Ui.shape(this, "surface2", 14f, "border", 1f));
        populateLog();
        logCard.addView(ajustesLogContainer, cardLp());

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

    private View filterChip(String label, final String key) {
        final TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(12);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        chip.setGravity(Gravity.CENTER);
        chip.setMinHeight(dp(48));
        chip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                logFilter = key;
                restyleFilterChips();
                populateLog();
            }
        });
        styleFilterChip(chip, key);
        return chip;
    }

    private void styleFilterChip(TextView chip, String key) {
        boolean active = logFilter.equals(key);
        String token = active ? "accent" : "text3";
        chip.setTextColor(Ui.color(this, active ? "on_accent" : token));
        chip.setBackground(Ui.shape(this, active ? "accent" : "surface2",
                999f, active ? null : "border", active ? 0f : 1f));
    }

    private void restyleFilterChips() {
        if (ajustesFilterRow == null) return;
        for (int i = 0; i < ajustesFilterRow.getChildCount(); i++) {
            View child = ajustesFilterRow.getChildAt(i);
            if (child instanceof TextView) {
                String label = ((TextView) child).getText().toString();
                String key = label.equals("Todo") ? "all"
                        : label.equals("Servicio") ? "servicio"
                        : label.equals("Acciones") ? "acciones" : "errores";
                styleFilterChip((TextView) child, key);
            }
        }
    }

    private void populateLog() {
        if (ajustesLogContainer == null) return;
        ajustesLogContainer.removeAllViews();
        List<String> lines = filteredLog();
        if (lines.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Sin eventos en este filtro.");
            empty.setTextSize(12);
            empty.setTextColor(TEXT_MUTED);
            empty.setPadding(dp(4), dp(4), dp(4), dp(4));
            ajustesLogContainer.addView(empty);
            return;
        }
        if (lines.size() > MAX_LOG_ROWS) {
            lines = new ArrayList<>(lines.subList(lines.size() - MAX_LOG_ROWS, lines.size()));
            TextView note = new TextView(this);
            note.setText("Mostrando los últimos " + MAX_LOG_ROWS + " eventos.");
            note.setTextSize(11);
            note.setTextColor(TEXT_MUTED);
            note.setPadding(dp(4), dp(2), dp(4), dp(8));
            ajustesLogContainer.addView(note, lpMATCH(0));
        }
        for (String line : lines) {
            String hora = line.length() >= 8 ? line.substring(0, 8) : "";
            String msg = line.length() > 10 ? line.substring(10) : line;
            ajustesLogContainer.addView(
                    Ui.logRow(this, logTypeName(logKindFor(line)), hora, msg), lpMATCH(0));
        }
    }

    private List<String> filteredLog() {
        List<String> all = ConnectionLog.dump();
        if (logFilter.equals("all")) return all;
        List<String> out = new ArrayList<>();
        for (String line : all) {
            int kind = logKindFor(line);
            if (logFilter.equals("servicio") && kind == 0) out.add(line);
            else if (logFilter.equals("acciones") && kind == 1) out.add(line);
            else if (logFilter.equals("errores") && kind == 2) out.add(line);
        }
        return out;
    }

    /** 0 = servicio, 1 = acciones, 2 = errores, 3 = aviso, 4 = otros */
    private int logKindFor(String line) {
        String lower = line.toLowerCase();
        if (lower.contains("desconect") || lower.contains("disconnect") || lower.contains("error") || lower.contains("fail"))
            return 2;
        if (lower.contains("aviso") || lower.contains("warning"))
            return 3;
        if (lower.contains("servicio") || lower.contains("service") || lower.contains("conectado") || lower.contains("notific"))
            return 0;
        if (lower.contains("ui envió") || lower.contains("ui envio") || lower.contains("action"))
            return 1;
        return 4;
    }

    private String logTypeName(int kind) {
        switch (kind) {
            case 0: return "info";
            case 1: return "ok";
            case 2: return "error";
            case 3: return "aviso";
            default: return "text3";
        }
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
        if (!isServiceAvailable()) {
            refreshStatus();
            ConnectionLog.add("UI error: " + action + " — servicio de accesibilidad desconectado");
            toast("CostPhone no está conectado. Pulsa Reparar conexión en Inicio.");
            return;
        }
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
        boolean stateChanged = lastServiceState != state;
        lastServiceState = state;
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
                desc = "Android conserva el permiso, pero el servicio no se ha enlazado. Pulsa Reparar conexión y vuelve a activar CostPhone en Accesibilidad.";
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
            headerStatusText.setTextColor(color);
        }
        if (heroStatusIcon != null) {
            heroStatusIcon.setImageResource(state == 2
                    ? R.drawable.ic_connected : R.drawable.ic_disconnected);
            heroStatusIcon.setColorFilter(color);
        }
        if (stateChanged && statusCardRef != null && a11yBtnIndex >= 0
                && a11yBtnIndex < statusCardRef.getChildCount()) {
            View old = statusCardRef.getChildAt(a11yBtnIndex);
            LinearLayout.LayoutParams blp = (LinearLayout.LayoutParams) old.getLayoutParams();
            statusCardRef.removeViewAt(a11yBtnIndex);
            statusCardRef.addView(a11yButton(state), a11yBtnIndex, blp);
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
        if (shizukuDot == null || tvShizuku == null || btnShizuku == null) return;
        int state = ShizukuManager.state();
        if (state == ShizukuManager.UNAVAILABLE) {
            shizukuDot.setBackground(rounded(ACCENT_RED, STATUS_RADIUS, 0));
            tvShizuku.setText("Shizuku no iniciado");
            tvShizuku.setTextColor(ACCENT_RED);
            btnShizuku.setVisibility(View.VISIBLE);
            btnShizuku.setText("Abrir Shizuku");
            btnShizuku.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try {
                        Intent launch = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                        if (launch != null) {
                            startActivity(launch);
                            toast("Inicia el servicio en Shizuku y vuelve a CostPhone.");
                        } else {
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://shizuku.rikka.app/download/")));
                        }
                    } catch (Throwable t) { toast("No se pudo abrir Shizuku."); }
                }
            });
            return;
        }
        if (state == ShizukuManager.NO_PERMISSION) {
            shizukuDot.setBackground(rounded(ACCENT_AMBER, STATUS_RADIUS, 0));
            tvShizuku.setText("Falta permiso de CostPhone");
            tvShizuku.setTextColor(ACCENT_AMBER);
            btnShizuku.setVisibility(View.VISIBLE);
            btnShizuku.setText("Conceder permiso");
            btnShizuku.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (!ShizukuManager.requestPermission(REQ_SHIZUKU_PERMISSION)) {
                        toast("Shizuku no respondió. Comprueba que su servicio esté iniciado.");
                    }
                    refreshShizuku();
                }
            });
            return;
        }
        if (ShizukuManager.isShellConnected()) {
            shizukuDot.setBackground(rounded(ACCENT_GREEN, STATUS_RADIUS, 0));
            tvShizuku.setText("Shizuku · Shell conectado");
            tvShizuku.setTextColor(ACCENT_GREEN);
            btnShizuku.setVisibility(View.GONE);
            return;
        }
        ShizukuManager.connectNow();
        shizukuDot.setBackground(rounded(ACCENT_AMBER, STATUS_RADIUS, 0));
        tvShizuku.setText(ShizukuManager.isShellBinding()
                ? "Enlazando UserService…" : "Permiso listo · sin enlace");
        tvShizuku.setTextColor(ACCENT_AMBER);
        btnShizuku.setVisibility(View.VISIBLE);
        btnShizuku.setText("Reintentar conexión");
        btnShizuku.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ShizukuManager.connectNow();
                refreshShizuku();
                toast("Reintentando enlace. Shizuku debe tener su servicio iniciado.");
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //  BUILDERS DE UI
    // ═══════════════════════════════════════════════════════════════

    private LinearLayout pageIntro(String eyebrow, String title, String description,
                                   int iconRes, int accent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(15), dp(14), dp(15));
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Ui.color(this, "hero"), Ui.color(this, "surface")});
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), (accent & 0x00FFFFFF) | 0x52000000);
        card.setBackground(bg);
        card.setElevation(dp(1));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        copyLp.rightMargin = dp(10);
        card.addView(copy, copyLp);

        TextView overline = new TextView(this);
        overline.setText(eyebrow);
        overline.setTextSize(8.5f);
        overline.setLetterSpacing(0.075f);
        overline.setTextColor(accent);
        overline.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        overline.setMaxLines(1);
        overline.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(overline);

        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(20);
        heading.setTextColor(TEXT_MAIN);
        heading.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        heading.setMaxLines(2);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(heading, lpMATCH(4));

        TextView note = new TextView(this);
        note.setText(description);
        note.setTextSize(12);
        note.setTextColor(TEXT_DIM);
        note.setMaxLines(2);
        note.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(note);

        LinearLayout iconShell = new LinearLayout(this);
        iconShell.setGravity(Gravity.CENTER);
        iconShell.setBackground(rounded((accent & 0x00FFFFFF) | 0x21000000,
                16, (accent & 0x00FFFFFF) | 0x66000000));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(accent);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconShell.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));
        card.addView(iconShell, new LinearLayout.LayoutParams(dp(54), dp(54)));
        return card;
    }

    private LinearLayout createPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        return panel;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(6), dp(12), dp(6));
        header.setBackgroundColor(BG_DARK);
        header.setMinimumHeight(dp(60));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setPadding(dp(3), dp(3), dp(3), dp(3));
        logo.setBackground(Ui.shape(this, "surface2", 12f, "outline_accent", 1f));
        header.addView(logo, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setPadding(dp(11), 0, 0, 0);
        titleBlock.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setTextSize(19);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        android.text.SpannableString span = new android.text.SpannableString("CostPhone");
        span.setSpan(new android.text.style.ForegroundColorSpan(TEXT_MAIN), 0, 4,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new android.text.style.ForegroundColorSpan(ACCENT_BLUE), 4, 9,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setText(span);
        titleBlock.addView(title);

        TextView sub = new TextView(this);
        sub.setText("CONTROL LOCAL · ANDROID");
        sub.setTextSize(9);
        sub.setLetterSpacing(0.08f);
        sub.setTextColor(TEXT_DIM);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(3);
        titleBlock.addView(sub, subLp);
        header.addView(titleBlock);

        LinearLayout statusChip = new LinearLayout(this);
        statusChip.setOrientation(LinearLayout.HORIZONTAL);
        statusChip.setGravity(Gravity.CENTER_VERTICAL);
        statusChip.setPadding(dp(7), dp(5), dp(8), dp(5));
        statusChip.setBackground(Ui.shape(this, "surface2", 999f, "border", 1f));

        headerDot = new View(this);
        headerDot.setBackground(rounded(TEXT_MUTED, STATUS_RADIUS, 0));
        statusChip.addView(headerDot, lp(dp(7), dp(7), 0));

        headerStatusText = new TextView(this);
        headerStatusText.setTextSize(10);
        headerStatusText.setTextColor(TEXT_DIM);
        headerStatusText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        headerStatusText.setSingleLine(true);
        LinearLayout.LayoutParams hstlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hstlp.leftMargin = dp(6);
        statusChip.addView(headerStatusText, hstlp);

        LinearLayout.LayoutParams sclp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sclp.rightMargin = dp(8);
        header.addView(statusChip, sclp);

        ImageView gear = new ImageView(this);
        gear.setImageResource(R.drawable.ic_settings);
        gear.setColorFilter(TEXT_DIM);
        gear.setPadding(dp(9), dp(9), dp(9), dp(9));
        gear.setMinimumHeight(dp(48));
        gear.setMinimumWidth(dp(48));
        gear.setBackground(Ui.shape(this, "surface2", 14f, "border", 1f));
        gear.setContentDescription("Abrir ajustes");
        gear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTab(TAB_AJUSTES); }
        });
        header.addView(gear, new LinearLayout.LayoutParams(dp(48), dp(48)));
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
        badge.setBackground(rounded((ACCENT_GREEN & 0x00FFFFFF) | 0x25000000, 7, 0));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.rightMargin = dp(8);
        chip.addView(badge, blp);

        btnShizuku = new Button(this);
        btnShizuku.setAllCaps(false);
        btnShizuku.setTextSize(12);
        btnShizuku.setTextColor(Ui.color(this, "on_accent"));
        btnShizuku.setTypeface(Typeface.DEFAULT_BOLD);
        btnShizuku.setBackground(rounded(ACCENT_BLUE, 12, 0));
        btnShizuku.setPadding(dp(14), dp(12), dp(14), dp(12));
        btnShizuku.setMinimumHeight(dp(48));
        btnShizuku.setStateListAnimator(null);
        chip.addView(btnShizuku);

        return chip;
    }

    private LinearLayout sectionCard(String title, int iconRes, View trailing) {
        LinearLayout card = sectionCard(title, iconRes);
        LinearLayout header = (LinearLayout) card.getChildAt(0);
        if (header.getChildCount() > 1 && header.getChildAt(1) instanceof TextView) {
            TextView titleView = (TextView) header.getChildAt(1);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            titleLp.leftMargin = dp(11);
            titleView.setLayoutParams(titleLp);
        }
        LinearLayout.LayoutParams trailingLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        trailingLp.leftMargin = dp(8);
        header.addView(trailing, trailingLp);
        return card;
    }

    private LinearLayout sectionCard(String title, int iconRes) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(getResources().getDrawable(R.drawable.bg_card));
        card.setPadding(dp(17), dp(17), dp(17), dp(18));
        card.setElevation(dp(1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout iconShell = new LinearLayout(this);
        iconShell.setGravity(Gravity.CENTER);
        iconShell.setBackground(Ui.shape(this, "surface2", 11f, "outline_accent", 1f));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(ACCENT_BLUE);
        iconShell.addView(icon, new LinearLayout.LayoutParams(dp(19), dp(19)));
        header.addView(iconShell, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(15);
        tv.setTextColor(TEXT_MAIN);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout.LayoutParams tvlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tvlp.leftMargin = dp(11);
        header.addView(tv, tvlp);

        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headerLp.bottomMargin = dp(5);
        card.addView(header, headerLp);
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
        b.setBackground(Ui.ripple(this, getResources().getDrawable(
                        primary ? R.drawable.bg_button_primary : R.drawable.bg_button_secondary),
                null,
                primary ? Ui.color(this, "accent_primary_dark")
                        : (ACCENT_GREEN & 0x00FFFFFF) | 0x33000000));
        b.setPadding(dp(8), dp(10), dp(8), dp(10));
        b.setStateListAnimator(null);
        b.setMinimumHeight(dp(48));
        // primario: texto on_accent (oscuro) sobre turquesa; secundario: acento
        if (primary) b.setTextColor(Ui.color(this, "on_accent"));
        else b.setTextColor(ACCENT_GREEN);
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
        tv.setTextSize(11);
        tv.setLetterSpacing(0.07f);
        tv.setTextColor(TEXT_MUTED);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tv.setPadding(dp(8), dp(6), dp(8), dp(5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        lp.bottomMargin = dp(4);
        tv.setLayoutParams(lp);
        return tv;
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
