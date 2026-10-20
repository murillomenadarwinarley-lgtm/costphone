package com.accessdroid.termux;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import rikka.shizuku.Shizuku;

/**
 * CostPhone — interfaz nativa con navegación inferior.
 *
 * Las cinco secciones son una capa visual sobre las funciones documentadas.
 * Los ejemplos de control se copian al portapapeles; no simulan la ejecución.
 */
public class SettingsActivity extends Activity {

    private static final int BG_DARK = 0xFF060B14;
    private static final int BG_CARD = 0xFF0B1524;
    private static final int BG_CARD_ALT = 0xFF102035;
    private static final int BG_HERO = 0xFF10283A;
    private static final int CYAN = 0xFF35E6E8;
    private static final int TEAL = 0xFF18D9BC;
    private static final int GREEN = 0xFF25D9A1;
    private static final int RED = 0xFFFF7185;
    private static final int TEXT_MAIN = 0xFFF2F7FF;
    private static final int TEXT_MUTED = 0xFF9AADC8;
    private static final int TEXT_SOFT = 0xFF7186A5;
    private static final int STROKE = 0xFF1A3046;
    private static final int STROKE_CYAN = 0xFF205064;

    private FrameLayout contentFrame;
    private LinearLayout bottomNav;
    private final View[] navItems = new View[3];
    private final ImageView[] navIcons = new ImageView[3];
    private final TextView[] navLabels = new TextView[3];
    private int currentTab = 0;

    private LinearLayout headerStatusPill;
    private View headerStatusDot;
    private TextView headerStatusText;
    private LinearLayout serviceStateBadge;
    private TextView serviceStateText;
    private View serviceIconBox;
    private ImageView serviceIconGlyph;
    private TextView serviceDescription;
    private LinearLayout actionButton;
    private TextView actionLabel;

    private static final int SHIZUKU_PERMISSION_REQUEST = 5412;
    private static final int VDISPLAY_WIDTH = 1080;
    private static final int VDISPLAY_HEIGHT = 1920;
    private static final int VDISPLAY_DENSITY = 320;
    private static final String SHIZUKU_PREFS = "costphone_connection";
    private static final String PREF_VDISPLAY_OWNED = "shizuku_virtual_display_owned";

    private TextView shizukuStatusTitle;
    private TextView shizukuStatusDescription;
    private TextView shizukuDiagnostics;
    private View shizukuStatusDot;
    private LinearLayout shizukuActionButton;
    private TextView shizukuActionLabel;
    private LinearLayout virtualDisplayOnButton;
    private TextView virtualDisplayOnLabel;
    private LinearLayout virtualDisplayOffButton;
    private TextView virtualDisplayOffLabel;
    private ICostPhoneShizukuService shizukuService;
    private boolean shizukuServiceBound;
    private Shizuku.UserServiceArgs shizukuServiceArgs;

    private final Shizuku.OnBinderReceivedListener shizukuBinderReceivedListener =
            () -> runOnUiThread(this::refreshShizukuStatus);
    private final Shizuku.OnBinderDeadListener shizukuBinderDeadListener = () ->
            runOnUiThread(() -> {
                shizukuService = null;
                shizukuServiceBound = false;
                refreshShizukuStatus();
            });
    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionResultListener =
            (requestCode, grantResult) -> runOnUiThread(() -> {
                if (requestCode != SHIZUKU_PERMISSION_REQUEST) return;
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    bindShizukuUserService();
                } else {
                    Toast.makeText(this, "Permiso de Shizuku denegado", Toast.LENGTH_SHORT).show();
                }
                refreshShizukuStatus();
            });

    private final ServiceConnection shizukuServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            shizukuService = ICostPhoneShizukuService.Stub.asInterface(service);
            shizukuServiceBound = true;
            refreshShizukuStatus();
            final ICostPhoneShizukuService remote = shizukuService;
            new Thread(() -> {
                final String report;
                try {
                    report = remote.getDiagnostics();
                } catch (RemoteException | RuntimeException e) {
                    runOnUiThread(() -> {
                        if (shizukuDiagnostics != null) {
                            shizukuDiagnostics.setText("Conectado, pero no se pudo leer el diagnóstico: " + e.getMessage());
                        }
                    });
                    return;
                }
                runOnUiThread(() -> {
                    if (shizukuDiagnostics != null) shizukuDiagnostics.setText(report);
                    refreshShizukuStatus();
                });
            }, "CostPhone-Shizuku-Diagnostics").start();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            shizukuService = null;
            shizukuServiceBound = false;
            refreshShizukuStatus();
        }
    };

    private static final String[] NAV_LABELS = {
            "Inicio", "Herramientas", "Conexión"
    };
    private static final int[] NAV_ICONS = {
            R.drawable.ic_home,
            R.drawable.ic_terminal,
            R.drawable.ic_settings
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (getActionBar() != null) getActionBar().hide();

        getWindow().setStatusBarColor(BG_DARK);
        getWindow().setNavigationBarColor(BG_DARK);
        getWindow().getDecorView().setSystemUiVisibility(0);

        LinearLayout root = buildShell();
        setContentView(root);
        Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener);
        Shizuku.addBinderDeadListener(shizukuBinderDeadListener);
        Shizuku.addRequestPermissionResultListener(shizukuPermissionResultListener);
        showTab(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        refreshShizukuStatus();
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener);
        Shizuku.removeBinderDeadListener(shizukuBinderDeadListener);
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionResultListener);
        try {
            if (shizukuServiceBound && shizukuServiceArgs != null && Shizuku.pingBinder()) {
                Shizuku.unbindUserService(shizukuServiceArgs, shizukuServiceConnection, true);
            }
        } catch (Throwable ignored) {
            // The Shizuku binder can disappear while Android is destroying the activity.
        }
        super.onDestroy();
    }

    private LinearLayout buildShell() {
        LinearLayout root = column();
        root.setBackgroundColor(BG_DARK);

        contentFrame = new FrameLayout(this);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(contentFrame, contentLp);

        bottomNav = buildBottomNav();
        LinearLayout.LayoutParams navLp = matchWrap();
        navLp.setMargins(dp(14), dp(6), dp(14), dp(8));
        root.addView(bottomNav, navLp);
        return root;
    }

    private LinearLayout buildBottomNav() {
        LinearLayout bar = row();
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(4), dp(5), dp(4), dp(5));
        bar.setBackground(rounded(BG_CARD, 21, STROKE));

        for (int i = 0; i < NAV_LABELS.length; i++) {
            final int tab = i;
            LinearLayout item = column();
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(1), dp(8), dp(1), dp(7));
            item.setClickable(true);
            item.setFocusable(true);
            item.setContentDescription(NAV_LABELS[i]);
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    showTab(tab);
                }
            });

            ImageView icon = new ImageView(this);
            icon.setImageResource(NAV_ICONS[i]);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            item.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));

            TextView label = text(NAV_LABELS[i], 9, TEXT_SOFT, false);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelLp = topMargin(wrap(), 5);
            item.addView(label, labelLp);

            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            bar.addView(item, itemLp);
            navItems[i] = item;
            navIcons[i] = icon;
            navLabels[i] = label;
        }
        return bar;
    }

    private void showTab(int tab) {
        currentTab = Math.max(0, Math.min(2, tab));

        // Clear references to views on the previous page before rebuilding the next one.
        headerStatusPill = null;
        headerStatusDot = null;
        headerStatusText = null;
        serviceStateBadge = null;
        serviceStateText = null;
        serviceIconBox = null;
        serviceIconGlyph = null;
        serviceDescription = null;
        actionButton = null;
        actionLabel = null;
        shizukuStatusTitle = null;
        shizukuStatusDescription = null;
        shizukuDiagnostics = null;
        shizukuStatusDot = null;
        shizukuActionButton = null;
        shizukuActionLabel = null;
        virtualDisplayOnButton = null;
        virtualDisplayOnLabel = null;
        virtualDisplayOffButton = null;
        virtualDisplayOffLabel = null;

        View page;
        switch (currentTab) {
            case 1: page = buildToolsPage(); break;
            case 2: page = buildConnectionPage(); break;
            default: page = buildHomePage(); break;
        }

        contentFrame.removeAllViews();
        contentFrame.addView(page, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        updateNavigation();
        refreshStatus();
        refreshShizukuStatus();
    }

    private void updateNavigation() {
        for (int i = 0; i < navItems.length; i++) {
            boolean selected = i == currentTab;
            navItems[i].setBackground(rounded(
                    selected ? 0x2235E6E8 : 0x00000000,
                    12,
                    selected ? 0x4435E6E8 : 0));
            navIcons[i].setColorFilter(selected ? CYAN : TEXT_SOFT);
            navLabels[i].setTextColor(selected ? CYAN : TEXT_SOFT);
            navLabels[i].setTypeface(Typeface.create(
                    "sans-serif-medium", selected ? Typeface.BOLD : Typeface.NORMAL));
        }
    }

    private ScrollView newPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setBackgroundColor(BG_DARK);
        scroll.setPadding(0, 0, 0, dp(6));
        return scroll;
    }

    private LinearLayout pageContent(ScrollView scroll) {
        LinearLayout page = column();
        page.setPadding(dp(20), dp(18), dp(20), dp(22));
        scroll.addView(page, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        return page;
    }

    // ───────────────────────────── INICIO ─────────────────────────────

    private View buildHomePage() {
        ScrollView scroll = newPage();
        LinearLayout page = pageContent(scroll);

        page.addView(buildHeader(), matchWrap());
        page.addView(buildHero(), topMargin(match(), 20));
        page.addView(buildServiceCard(), topMargin(match(), 14));
        page.addView(sectionLabel("EXPLORA COSTPHONE"), topMargin(wrap(), 24));

        page.addView(buildFeatureCard(
                R.drawable.ic_terminal,
                "Herramientas rápidas",
                "Lee la pantalla, obtén referencias y copia solo los comandos que necesitas.",
                CYAN, 1), topMargin(match(), 11));

        page.addView(buildFeatureCard(
                R.drawable.ic_settings,
                "Conexiones del dispositivo",
                "Comprueba Accesibilidad y conecta Shizuku para las operaciones avanzadas compatibles.",
                0xFF9A9EFF, 2), topMargin(match(), 9));

        TextView footer = text(
                "COSTPHONE  ·  V1.3\nControl local para Android, junto a Termux.",
                10, TEXT_SOFT, false);
        footer.setGravity(Gravity.CENTER);
        footer.setLineSpacing(dp(4), 1f);
        page.addView(footer, topMargin(match(), 22));
        return scroll;
    }

    private View buildHeader() {
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setBackground(rounded(0xFF0C1D2D, 16, STROKE_CYAN));
        logo.setPadding(dp(3), dp(3), dp(3), dp(3));
        header.addView(logo, size(dp(46), dp(46)));

        LinearLayout brand = column();
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        brandLp.leftMargin = dp(10);
        header.addView(brand, brandLp);

        TextView name = text("CostPhone", 21, TEXT_MAIN, true);
        brand.addView(name);
        TextView tagline = text("AGENTE LOCAL · ANDROID", 9, TEXT_MUTED, true);
        tagline.setLetterSpacing(0.06f);
        brand.addView(tagline, topMargin(wrap(), 4));

        headerStatusPill = row();
        headerStatusPill.setGravity(Gravity.CENTER_VERTICAL);
        headerStatusPill.setPadding(dp(9), dp(8), dp(10), dp(8));
        headerStatusPill.setBackground(rounded(0xFF0D2928, 999, 0xFF1D5A54));

        headerStatusDot = new View(this);
        headerStatusDot.setBackground(rounded(GREEN, 999, 0));
        LinearLayout.LayoutParams dotLp = size(dp(7), dp(7));
        dotLp.rightMargin = dp(6);
        headerStatusPill.addView(headerStatusDot, dotLp);

        headerStatusText = text("ACTIVO", 9, GREEN, true);
        headerStatusPill.addView(headerStatusText);
        header.addView(headerStatusPill, wrap());
        return header;
    }

    private View buildHero() {
        LinearLayout card = column();
        card.setPadding(dp(17), dp(17), dp(16), dp(15));
        card.setBackground(gradient(BG_HERO, BG_CARD, 23, STROKE_CYAN));
        card.setElevation(dp(2));

        LinearLayout content = row();
        content.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = column();
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        copyLp.rightMargin = dp(2);
        content.addView(copy, copyLp);

        TextView eyebrow = text("CONTROL INTELIGENTE", 9, CYAN, true);
        eyebrow.setLetterSpacing(0.08f);
        copy.addView(eyebrow);
        copy.addView(text("Tu IA trabaja", 22, TEXT_MAIN, true), topMargin(wrap(), 9));
        copy.addView(text("por ti.", 26, CYAN, true), topMargin(wrap(), 0));

        TextView body = text(
                "Tu teléfono, al alcance de tu agente desde Termux.",
                12, TEXT_MUTED, false);
        body.setLineSpacing(dp(3), 1f);
        copy.addView(body, topMargin(match(), 9));

        LinearLayout.LayoutParams orbLp = size(dp(98), dp(112));
        orbLp.leftMargin = dp(1);
        content.addView(createOrb(), orbLp);
        card.addView(content);

        LinearLayout chips = row();
        chips.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(chips, topMargin(wrap(), 13));
        chips.addView(chip("INTEGRACIÓN CON TERMUX"), wrap());
        chips.addView(chip("SIN ROOT"), topMarginAndLeft(wrap(), 0, 7));
        return card;
    }

    private View createOrb() {
        FrameLayout orb = new FrameLayout(this);

        View halo = new View(this);
        halo.setBackground(gradient(0x3332D7E0, 0x00112C42, 999, 0));
        orb.addView(halo, new FrameLayout.LayoutParams(dp(108), dp(108), Gravity.CENTER));

        View outerRing = new View(this);
        outerRing.setBackground(rounded(0x00000000, 999, 0x6634DDE8));
        orb.addView(outerRing, new FrameLayout.LayoutParams(dp(104), dp(104), Gravity.CENTER));

        View middleRing = new View(this);
        middleRing.setBackground(rounded(0x00000000, 999, 0xAA35BFCF));
        orb.addView(middleRing, new FrameLayout.LayoutParams(dp(82), dp(82), Gravity.CENTER));

        View core = new View(this);
        core.setBackground(gradient(0xFF173B55, 0xFF091421, 999, 0xFF38DCE6));
        orb.addView(core, new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER));

        LinearLayout eyes = row();
        eyes.setGravity(Gravity.CENTER);
        orb.addView(eyes, new FrameLayout.LayoutParams(dp(42), dp(22), Gravity.CENTER));

        View eyeOne = new View(this);
        eyeOne.setBackground(rounded(CYAN, 999, 0));
        LinearLayout.LayoutParams eyeOneLp = size(dp(7), dp(12));
        eyeOneLp.rightMargin = dp(9);
        eyes.addView(eyeOne, eyeOneLp);

        View eyeTwo = new View(this);
        eyeTwo.setBackground(rounded(CYAN, 999, 0));
        eyes.addView(eyeTwo, size(dp(7), dp(12)));

        ImageView sparkle = new ImageView(this);
        sparkle.setImageResource(R.drawable.ic_sparkles);
        sparkle.setColorFilter(CYAN);
        sparkle.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams sparkleLp = new FrameLayout.LayoutParams(
                dp(22), dp(22), Gravity.TOP | Gravity.RIGHT);
        sparkleLp.topMargin = dp(2);
        sparkleLp.rightMargin = dp(1);
        orb.addView(sparkle, sparkleLp);

        View satellite = new View(this);
        satellite.setBackground(rounded(0xFF8D7BFF, 999, 0));
        FrameLayout.LayoutParams satelliteLp = new FrameLayout.LayoutParams(
                dp(8), dp(8), Gravity.BOTTOM | Gravity.LEFT);
        satelliteLp.leftMargin = dp(11);
        satelliteLp.bottomMargin = dp(15);
        orb.addView(satellite, satelliteLp);
        return orb;
    }

    // ───────────────────────── HERRAMIENTAS ───────────────────────────

    private View buildToolsPage() {
        ScrollView scroll = newPage();
        LinearLayout page = pageContent(scroll);
        page.addView(buildPageHeader(
                R.drawable.ic_terminal, "Herramientas", "Las acciones que más se usan", CYAN));

        page.addView(buildNotice(
                "CÓPIA Y EJECUTA EN TERMUX",
                "CostPhone muestra comandos reales de amctl. Estos botones copian el comando; la ejecución sigue ocurriendo en Termux.",
                R.drawable.ic_clipboard, CYAN), topMargin(match(), 14));

        page.addView(sectionLabel("INSPECCIÓN Y CONTROL"), topMargin(wrap(), 20));
        page.addView(buildCommandCard(
                R.drawable.ic_inspect, 0xFF9A9EFF,
                "Leer pantalla interactiva",
                "Obtén referencias @pN para tocar botones y campos sin usar coordenadas.",
                "amctl snap", "Copiar comando"), topMargin(match(), 10));

        page.addView(buildCommandCard(
                R.drawable.ic_display, TEAL,
                "Capturar pantalla",
                "Guarda un PNG para revisar visualmente lo que muestra el teléfono.",
                "amctl see", "Copiar comando"), topMargin(match(), 9));

        page.addView(buildCommandCard(
                R.drawable.ic_terminal, CYAN,
                "Ver árbol de interfaz",
                "Útil para depurar cuando el snapshot no encuentra un elemento.",
                "amctl tree", "Copiar comando"), topMargin(match(), 9));

        page.addView(buildCommandCard(
                R.drawable.ic_control, 0xFFFFC77D,
                "Tocar una referencia",
                "Sustituye @p3 por una referencia del último snapshot. Tómalo de nuevo tras cambiar de pantalla.",
                "amctl tapref @p3", "Copiar comando"), topMargin(match(), 9));

        page.addView(buildCommandCard(
                R.drawable.ic_routines, TEAL,
                "Desplazar y volver a inspeccionar",
                "Actualiza las referencias después del desplazamiento.",
                "amctl scroll down\namctl snap", "Copiar secuencia"), topMargin(match(), 9));
        return scroll;
    }

    // ─────────────────────────── CONEXIONES ────────────────────────────

    private View buildConnectionPage() {
        ScrollView scroll = newPage();
        LinearLayout page = pageContent(scroll);
        page.addView(buildPageHeader(
                R.drawable.ic_settings, "Conexión", "Accesibilidad + Shizuku", CYAN));

        page.addView(buildServiceCard(), topMargin(match(), 14));
        page.addView(buildShizukuCard(), topMargin(match(), 12));
        page.addView(buildVirtualDisplayCard(), topMargin(match(), 10));

        TextView note = text(
                "Shizuku no es root y no sustituye al servicio de Accesibilidad. En dispositivos sin root debe iniciarse desde Shizuku; en Android 11+ suele poder iniciarse con Depuración inalámbrica.",
                11, TEXT_SOFT, false);
        note.setLineSpacing(dp(3), 1f);
        page.addView(note, topMargin(match(), 16));
        return scroll;
    }

    private View buildShizukuCard() {
        LinearLayout card = column();
        card.setPadding(dp(15), dp(15), dp(15), dp(15));
        card.setBackground(gradient(BG_CARD_ALT, BG_CARD, 21, STROKE));

        LinearLayout heading = row();
        heading.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setBackground(rounded(0x2235E6E8, 14, 0x5535E6E8));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_settings);
        icon.setColorFilter(CYAN);
        icon.setPadding(dp(9), dp(9), dp(9), dp(9));
        iconBox.addView(icon, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        heading.addView(iconBox, size(dp(44), dp(44)));

        LinearLayout titleBox = column();
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = dp(11);
        heading.addView(titleBox, titleLp);
        titleBox.addView(text("Shizuku", 16, TEXT_MAIN, true));
        shizukuStatusTitle = text("Comprobando conexión…", 11, TEXT_MUTED, false);
        titleBox.addView(shizukuStatusTitle, topMargin(wrap(), 4));

        shizukuStatusDot = new View(this);
        shizukuStatusDot.setBackground(rounded(TEXT_SOFT, 999, 0));
        heading.addView(shizukuStatusDot, size(dp(9), dp(9)));
        card.addView(heading);

        shizukuStatusDescription = text(
                "Conecta con Shizuku para verificar el proceso privilegiado disponible en el dispositivo.",
                12, TEXT_MUTED, false);
        shizukuStatusDescription.setLineSpacing(dp(3), 1f);
        card.addView(shizukuStatusDescription, topMargin(match(), 12));

        shizukuDiagnostics = text(
                "El diagnóstico aparecerá cuando el servicio de CostPhone pueda conectarse.",
                11, TEXT_SOFT, false);
        shizukuDiagnostics.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        shizukuDiagnostics.setPadding(dp(10), dp(10), dp(10), dp(10));
        shizukuDiagnostics.setBackground(rounded(0xFF07101C, 10, STROKE));
        shizukuDiagnostics.setLineSpacing(dp(3), 1f);
        card.addView(shizukuDiagnostics, topMargin(match(), 10));

        shizukuActionButton = row();
        shizukuActionButton.setGravity(Gravity.CENTER);
        shizukuActionButton.setPadding(dp(13), dp(12), dp(13), dp(12));
        shizukuActionButton.setBackground(gradient(0xFF18CFC2, 0xFF087D91, 13, 0xFF2ADBE1));
        shizukuActionButton.setClickable(true);
        shizukuActionButton.setFocusable(true);
        shizukuActionButton.setOnClickListener(v -> onShizukuAction());

        shizukuActionLabel = text("Conectar Shizuku", 13, 0xFFFFFFFF, true);
        shizukuActionLabel.setGravity(Gravity.CENTER);
        shizukuActionButton.addView(shizukuActionLabel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(shizukuActionButton, topMargin(match(), 12));
        return card;
    }

    private View buildVirtualDisplayCard() {
        LinearLayout card = column();
        card.setPadding(dp(15), dp(15), dp(15), dp(15));
        card.setBackground(gradient(BG_CARD, BG_CARD_ALT, 19, STROKE));

        LinearLayout heading = row();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setBackground(rounded(0x2235D9B2, 13, 0x5535D9B2));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_display);
        icon.setColorFilter(TEAL);
        icon.setPadding(dp(9), dp(9), dp(9), dp(9));
        iconBox.addView(icon, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        heading.addView(iconBox, size(dp(42), dp(42)));

        LinearLayout copy = column();
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        copyLp.leftMargin = dp(11);
        heading.addView(copy, copyLp);
        copy.addView(text("Pantalla virtual", 15, TEXT_MAIN, true));
        TextView description = text(
                "Shizuku puede activarla sin conceder WRITE_SECURE_SETTINGS a CostPhone. Se utiliza own_content_only para no espejar la pantalla principal.",
                11, TEXT_MUTED, false);
        description.setLineSpacing(dp(3), 1f);
        copy.addView(description, topMargin(match(), 4));
        card.addView(heading);

        LinearLayout actions = row();
        actions.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(actions, topMargin(match(), 12));

        virtualDisplayOnButton = row();
        virtualDisplayOnButton.setGravity(Gravity.CENTER);
        virtualDisplayOnButton.setPadding(dp(8), dp(11), dp(8), dp(11));
        virtualDisplayOnButton.setBackground(rounded(0x2235D9B2, 11, 0x6635D9B2));
        virtualDisplayOnButton.setClickable(true);
        virtualDisplayOnButton.setFocusable(true);
        virtualDisplayOnButton.setOnClickListener(v -> createVirtualDisplay());
        virtualDisplayOnLabel = text("Crear 1080 × 1920", 11, TEAL, true);
        virtualDisplayOnLabel.setGravity(Gravity.CENTER);
        virtualDisplayOnButton.addView(virtualDisplayOnLabel);
        LinearLayout.LayoutParams onLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        onLp.rightMargin = dp(6);
        actions.addView(virtualDisplayOnButton, onLp);

        virtualDisplayOffButton = row();
        virtualDisplayOffButton.setGravity(Gravity.CENTER);
        virtualDisplayOffButton.setPadding(dp(8), dp(11), dp(8), dp(11));
        virtualDisplayOffButton.setBackground(rounded(0x22FF7185, 11, 0x66FF7185));
        virtualDisplayOffButton.setClickable(true);
        virtualDisplayOffButton.setFocusable(true);
        virtualDisplayOffButton.setOnClickListener(v -> removeVirtualDisplay());
        virtualDisplayOffLabel = text("Desactivar propia", 11, RED, true);
        virtualDisplayOffLabel.setGravity(Gravity.CENTER);
        virtualDisplayOffButton.addView(virtualDisplayOffLabel);
        LinearLayout.LayoutParams offLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        offLp.leftMargin = dp(6);
        actions.addView(virtualDisplayOffButton, offLp);

        updateVirtualDisplayButtons();
        return card;
    }

    private void onShizukuAction() {
        try {
            if (!Shizuku.pingBinder()) {
                openShizukuManager();
                return;
            }
            if (Shizuku.isPreV11()) {
                Toast.makeText(this, "Esta versión de Shizuku no es compatible", Toast.LENGTH_LONG).show();
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST);
                refreshShizukuStatus();
                return;
            }
            bindShizukuUserService();
        } catch (Throwable e) {
            if (shizukuStatusDescription != null) {
                shizukuStatusDescription.setText("No se pudo conectar con Shizuku: " + e.getMessage());
            }
            refreshShizukuStatus();
        }
    }

    private void openShizukuManager() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if (launch != null) {
            startActivity(launch);
        } else {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")));
        }
    }

    private void bindShizukuUserService() {
        try {
            if (!Shizuku.pingBinder()) {
                refreshShizukuStatus();
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST);
                return;
            }
            if (shizukuServiceBound) {
                queryShizukuDiagnostics();
                refreshShizukuStatus();
                return;
            }
            if (Shizuku.getVersion() < 10) {
                if (shizukuStatusDescription != null) {
                    shizukuStatusDescription.setText("Se necesita Shizuku API 10 o posterior.");
                }
                return;
            }
            if (shizukuServiceArgs == null) {
                shizukuServiceArgs = new Shizuku.UserServiceArgs(
                        new ComponentName(getPackageName(), CostPhoneShizukuService.class.getName()))
                        .daemon(false)
                        .processNameSuffix("costphone_service")
                        .debuggable(BuildConfig.DEBUG)
                        .version(BuildConfig.VERSION_CODE);
            }
            if (shizukuStatusDescription != null) {
                shizukuStatusDescription.setText("Solicitando el servicio de diagnóstico…");
            }
            Shizuku.bindUserService(shizukuServiceArgs, shizukuServiceConnection);
        } catch (Throwable e) {
            if (shizukuStatusDescription != null) {
                shizukuStatusDescription.setText("No se pudo iniciar el servicio: " + e.getMessage());
            }
            refreshShizukuStatus();
        }
    }

    private void refreshShizukuStatus() {
        boolean binder = false;
        boolean permission = false;
        try {
            binder = Shizuku.pingBinder();
            if (binder && !Shizuku.isPreV11()) {
                permission = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            }
        } catch (Throwable ignored) {
            binder = false;
            permission = false;
        }

        int accent;
        String title;
        String body;
        String action;
        if (!binder) {
            accent = RED;
            title = "No iniciado";
            body = "Abre Shizuku e inicia el servidor. CostPhone no puede iniciarlo silenciosamente.";
            action = "Abrir Shizuku";
        } else if (!permission) {
            accent = 0xFFFFC77D;
            title = "Servidor conectado · permiso pendiente";
            body = "Shizuku está activo. Autoriza a CostPhone en el diálogo de Shizuku para usar las funciones adicionales.";
            action = "Conceder permiso";
        } else if (shizukuServiceBound) {
            accent = GREEN;
            title = "Conexión verificada";
            body = "Permiso concedido y servicio de diagnóstico conectado bajo la identidad de Shizuku.";
            action = "Verificar de nuevo";
        } else {
            accent = CYAN;
            title = "Permiso concedido";
            body = "Conecta el servicio para verificar el UID real y habilitar la gestión de pantalla virtual.";
            action = "Conectar servicio";
        }

        if (shizukuStatusTitle != null) {
            shizukuStatusTitle.setText(title);
            shizukuStatusTitle.setTextColor(accent);
        }
        if (shizukuStatusDot != null) {
            shizukuStatusDot.setBackground(rounded(accent, 999, 0));
        }
        if (shizukuStatusDescription != null) shizukuStatusDescription.setText(body);
        if (shizukuActionLabel != null) shizukuActionLabel.setText(action);
        if (shizukuActionButton != null) {
            shizukuActionButton.setBackground(gradient(
                    binder ? 0xFF18CFC2 : 0xFF4A5468,
                    binder ? 0xFF087D91 : 0xFF2A3448,
                    13, binder ? 0xFF2ADBE1 : STROKE));
        }
        if (shizukuDiagnostics != null && !shizukuServiceBound) {
            shizukuDiagnostics.setText(binder
                    ? "Servidor de Shizuku detectado. Pulsa Conectar servicio para comprobar UID y ajustes."
                    : "Sin servidor disponible. Si no tienes Shizuku, abre su página oficial desde este botón.");
        }
        updateVirtualDisplayButtons();
    }

    private void queryShizukuDiagnostics() {
        final ICostPhoneShizukuService remote = shizukuService;
        if (remote == null) return;
        new Thread(() -> {
            final String result;
            try {
                result = remote.getDiagnostics();
            } catch (RemoteException | RuntimeException e) {
                runOnUiThread(() -> {
                    if (shizukuDiagnostics != null) {
                        shizukuDiagnostics.setText("Error de diagnóstico: " + e.getMessage());
                    }
                });
                return;
            }
            runOnUiThread(() -> {
                if (shizukuDiagnostics != null) shizukuDiagnostics.setText(result);
            });
        }, "CostPhone-Shizuku-Refresh").start();
    }

    private boolean hasOwnedVirtualDisplay() {
        SharedPreferences prefs = getSharedPreferences(SHIZUKU_PREFS, MODE_PRIVATE);
        return prefs.getBoolean(PREF_VDISPLAY_OWNED, false);
    }

    private void updateVirtualDisplayButtons() {
        boolean enabled = false;
        try {
            enabled = Shizuku.pingBinder()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    && shizukuServiceBound && shizukuService != null;
        } catch (Throwable ignored) { }

        if (virtualDisplayOnButton != null) {
            virtualDisplayOnButton.setAlpha(enabled ? 1f : 0.45f);
            virtualDisplayOnButton.setClickable(enabled);
        }
        if (virtualDisplayOffButton != null) {
            boolean canRemove = enabled && hasOwnedVirtualDisplay();
            virtualDisplayOffButton.setAlpha(canRemove ? 1f : 0.40f);
            virtualDisplayOffButton.setClickable(canRemove);
        }
        if (virtualDisplayOnLabel != null) {
            virtualDisplayOnLabel.setText(hasOwnedVirtualDisplay()
                    ? "Pantalla de CostPhone activa" : "Crear 1080 × 1920");
        }
        if (virtualDisplayOffLabel != null) {
            virtualDisplayOffLabel.setText(hasOwnedVirtualDisplay()
                    ? "Desactivar propia" : "Nada propio que apagar");
        }
    }

    private void createVirtualDisplay() {
        final ICostPhoneShizukuService remote = shizukuService;
        if (remote == null || !shizukuServiceBound) {
            Toast.makeText(this, "Conecta Shizuku primero", Toast.LENGTH_SHORT).show();
            return;
        }
        if (hasOwnedVirtualDisplay()) {
            Toast.makeText(this, "CostPhone ya tiene una pantalla virtual marcada como propia", Toast.LENGTH_SHORT).show();
            return;
        }
        if (virtualDisplayOnLabel != null) virtualDisplayOnLabel.setText("Creando…");
        new Thread(() -> {
            final String result;
            try {
                result = remote.createVirtualDisplay(VDISPLAY_WIDTH, VDISPLAY_HEIGHT, VDISPLAY_DENSITY);
            } catch (RemoteException | RuntimeException e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error de Shizuku: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    updateVirtualDisplayButtons();
                });
                return;
            }
            runOnUiThread(() -> {
                if (result.startsWith("CREATED:")) {
                    getSharedPreferences(SHIZUKU_PREFS, MODE_PRIVATE).edit()
                            .putBoolean(PREF_VDISPLAY_OWNED, true).apply();
                    Toast.makeText(this, "Pantalla virtual creada", Toast.LENGTH_SHORT).show();
                } else if (result.startsWith("EXISTS:")) {
                    Toast.makeText(this, "Esa configuración ya existía; no se marcará como propia", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, result, Toast.LENGTH_LONG).show();
                }
                if (shizukuDiagnostics != null) shizukuDiagnostics.setText(result);
                updateVirtualDisplayButtons();
                refreshShizukuStatus();
            });
        }, "CostPhone-VDisplay-Create").start();
    }

    private void removeVirtualDisplay() {
        final ICostPhoneShizukuService remote = shizukuService;
        if (remote == null || !shizukuServiceBound || !hasOwnedVirtualDisplay()) {
            Toast.makeText(this, "No hay una pantalla virtual de CostPhone que desactivar", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            final String result;
            try {
                result = remote.removeVirtualDisplay(VDISPLAY_WIDTH, VDISPLAY_HEIGHT, VDISPLAY_DENSITY);
            } catch (RemoteException | RuntimeException e) {
                runOnUiThread(() -> Toast.makeText(
                        this, "Error al apagar la pantalla: " + e.getMessage(), Toast.LENGTH_LONG).show());
                return;
            }
            runOnUiThread(() -> {
                if (result.startsWith("REMOVED:")) {
                    getSharedPreferences(SHIZUKU_PREFS, MODE_PRIVATE).edit()
                            .remove(PREF_VDISPLAY_OWNED).apply();
                    Toast.makeText(this, "Pantalla virtual desactivada", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, result, Toast.LENGTH_LONG).show();
                }
                if (shizukuDiagnostics != null) shizukuDiagnostics.setText(result);
                updateVirtualDisplayButtons();
                refreshShizukuStatus();
            });
        }, "CostPhone-VDisplay-Remove").start();
    }

    // ───────────────────────── COMPONENTES ────────────────────────────

    private View buildPageHeader(int iconResource, String title, String subtitle, int accent) {
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setBackground(rounded(withAlpha(accent, 0x22), 16, withAlpha(accent, 0x66)));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setColorFilter(accent);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        iconBox.addView(icon, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        header.addView(iconBox, size(dp(48), dp(48)));

        LinearLayout copy = column();
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        copyLp.leftMargin = dp(12);
        header.addView(copy, copyLp);

        TextView eyebrow = text("COSTPHONE / PANEL", 9, accent, true);
        eyebrow.setLetterSpacing(0.08f);
        copy.addView(eyebrow);
        copy.addView(text(title, 24, TEXT_MAIN, true), topMargin(wrap(), 4));
        copy.addView(text(subtitle, 12, TEXT_MUTED, false), topMargin(wrap(), 4));
        return header;
    }

    private View buildNotice(String title, String body, int iconResource, int accent) {
        LinearLayout card = row();
        card.setGravity(Gravity.TOP);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(gradient(BG_CARD_ALT, BG_CARD, 18, STROKE));

        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setBackground(rounded(withAlpha(accent, 0x22), 12, withAlpha(accent, 0x55)));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setColorFilter(accent);
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        iconBox.addView(icon, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        card.addView(iconBox, size(dp(40), dp(40)));

        LinearLayout copy = column();
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        copyLp.leftMargin = dp(12);
        card.addView(copy, copyLp);
        copy.addView(text(title, 10, accent, true));
        TextView description = text(body, 12, TEXT_MUTED, false);
        description.setLineSpacing(dp(3), 1f);
        copy.addView(description, topMargin(match(), 6));
        return card;
    }

    private View buildCommandCard(
            int iconResource, int accent, String title, String description,
            final String command, String copyLabel) {
        LinearLayout card = column();
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(gradient(BG_CARD, BG_CARD_ALT, 18, STROKE));

        LinearLayout heading = row();
        heading.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setBackground(rounded(withAlpha(accent, 0x22), 12, withAlpha(accent, 0x55)));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setColorFilter(accent);
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        iconBox.addView(icon, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        heading.addView(iconBox, size(dp(40), dp(40)));

        LinearLayout titleBox = column();
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = dp(11);
        heading.addView(titleBox, titleLp);
        titleBox.addView(text(title, 14, TEXT_MAIN, true));

        TextView body = text(description, 12, TEXT_MUTED, false);
        body.setLineSpacing(dp(3), 1f);
        titleBox.addView(body, topMargin(match(), 5));
        card.addView(heading);

        TextView code = text(command, 12, 0xFFD7F7FF, false);
        code.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        code.setTextIsSelectable(true);
        code.setPadding(dp(11), dp(11), dp(11), dp(11));
        code.setBackground(rounded(0xFF07101C, 11, 0xFF203B51));
        card.addView(code, topMargin(match(), 13));

        LinearLayout copyButton = row();
        copyButton.setGravity(Gravity.CENTER);
        copyButton.setPadding(dp(12), dp(10), dp(12), dp(10));
        copyButton.setBackground(rounded(withAlpha(accent, 0x1F), 11, withAlpha(accent, 0x77)));
        copyButton.setClickable(true);
        copyButton.setFocusable(true);
        copyButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                copyToClipboard(command);
            }
        });

        ImageView copyIcon = new ImageView(this);
        copyIcon.setImageResource(R.drawable.ic_clipboard);
        copyIcon.setColorFilter(accent);
        copyButton.addView(copyIcon, size(dp(18), dp(18)));

        TextView label = text(copyLabel, 12, accent, true);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        labelLp.leftMargin = dp(8);
        copyButton.addView(label, labelLp);
        card.addView(copyButton, topMargin(match(), 11));

        return card;
    }

    private void copyToClipboard(String value) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("CostPhone", value));
            Toast.makeText(this, "Copiado al portapapeles", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "No se pudo acceder al portapapeles", Toast.LENGTH_SHORT).show();
        }
    }

    private View buildServiceCard() {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(gradient(BG_CARD_ALT, BG_CARD, 22, STROKE));

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);

        serviceIconBox = new FrameLayout(this);
        serviceIconBox.setBackground(rounded(0x2233D9B2, 15, 0x4433D9B2));
        serviceIconGlyph = new ImageView(this);
        serviceIconGlyph.setImageResource(R.drawable.ic_check_circle);
        serviceIconGlyph.setColorFilter(GREEN);
        serviceIconGlyph.setPadding(dp(10), dp(10), dp(10), dp(10));
        serviceIconGlyph.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        ((FrameLayout) serviceIconBox).addView(serviceIconGlyph,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
        header.addView(serviceIconBox, size(dp(44), dp(44)));

        LinearLayout heading = column();
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        headingLp.leftMargin = dp(11);
        header.addView(heading, headingLp);

        TextView small = text("ESTADO DEL SISTEMA", 9, TEXT_SOFT, true);
        small.setLetterSpacing(0.07f);
        heading.addView(small);
        heading.addView(text("Accesibilidad", 16, TEXT_MAIN, true), topMargin(wrap(), 4));

        serviceStateBadge = row();
        serviceStateBadge.setGravity(Gravity.CENTER);
        serviceStateBadge.setPadding(dp(9), dp(6), dp(9), dp(6));
        serviceStateBadge.setBackground(rounded(0xFF0D2928, 999, 0xFF1D5A54));
        serviceStateText = text("ACTIVO", 9, GREEN, true);
        serviceStateBadge.addView(serviceStateText);
        header.addView(serviceStateBadge, wrap());
        card.addView(header);

        serviceDescription = text(
                "CostPhone está habilitado y puede recibir acciones de accesibilidad.",
                13, TEXT_MUTED, false);
        serviceDescription.setLineSpacing(dp(3), 1f);
        card.addView(serviceDescription, topMargin(match(), 14));

        View divider = new View(this);
        divider.setBackgroundColor(STROKE);
        card.addView(divider, topMargin(matchHeight(dp(1)), 16));

        actionButton = row();
        actionButton.setGravity(Gravity.CENTER_VERTICAL);
        actionButton.setPadding(dp(15), dp(13), dp(15), dp(13));
        actionButton.setBackground(gradient(0xFF18CFC2, 0xFF087D91, 14, 0xFF2ADBE1));
        actionButton.setClickable(true);
        actionButton.setFocusable(true);
        actionButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });

        actionLabel = text("Gestionar accesibilidad", 13, 0xFFFFFFFF, true);
        LinearLayout.LayoutParams actionLabelLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        actionButton.addView(actionLabel, actionLabelLp);

        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_arrow_right);
        arrow.setColorFilter(0xFFFFFFFF);
        arrow.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        actionButton.addView(arrow, size(dp(24), dp(24)));
        card.addView(actionButton, topMargin(match(), 14));
        return card;
    }

    private View buildFeatureCard(
            int iconResource, String heading, String description, int accent, final int targetTab) {
        LinearLayout card = row();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(13), dp(13), dp(13), dp(13));
        card.setBackground(gradient(BG_CARD, BG_CARD_ALT, 17, STROKE));

        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setBackground(rounded(withAlpha(accent, 0x22), 13, withAlpha(accent, 0x55)));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setColorFilter(accent);
        icon.setPadding(dp(9), dp(9), dp(9), dp(9));
        iconBox.addView(icon, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        card.addView(iconBox, size(dp(42), dp(42)));

        LinearLayout copy = column();
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        copyLp.leftMargin = dp(11);
        copyLp.rightMargin = dp(6);
        card.addView(copy, copyLp);
        copy.addView(text(heading, 14, TEXT_MAIN, true));
        TextView body = text(description, 12, TEXT_MUTED, false);
        body.setLineSpacing(dp(2), 1f);
        copy.addView(body, topMargin(match(), 5));

        if (targetTab >= 0) {
            ImageView arrow = new ImageView(this);
            arrow.setImageResource(R.drawable.ic_arrow_right);
            arrow.setColorFilter(TEXT_SOFT);
            card.addView(arrow, size(dp(21), dp(21)));
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    showTab(targetTab);
                }
            });
        }
        return card;
    }

    private View sectionLabel(String label) {
        LinearLayout line = row();
        line.setGravity(Gravity.CENTER_VERTICAL);
        View accent = new View(this);
        accent.setBackground(rounded(CYAN, 999, 0));
        line.addView(accent, size(dp(3), dp(14)));

        TextView caption = text(label, 10, TEXT_MUTED, true);
        caption.setLetterSpacing(0.1f);
        line.addView(caption, topMarginAndLeft(wrap(), 0, 9));
        return line;
    }

    private View chip(String label) {
        TextView view = text(label, 8, TEXT_MUTED, true);
        view.setLetterSpacing(0.035f);
        view.setPadding(dp(9), dp(7), dp(9), dp(7));
        view.setBackground(rounded(0x1A35DCE6, 999, 0x5535DCE6));
        return view;
    }

    // ───────────────────────────── ESTADO ─────────────────────────────

    private void refreshStatus() {
        boolean on = isServiceOn();
        int accent = on ? GREEN : RED;
        int pillBg = on ? 0xFF0D2928 : 0xFF321D29;
        int pillStroke = on ? 0xFF1D5A54 : 0xFF75414D;

        if (headerStatusDot != null) {
            headerStatusDot.setBackground(rounded(accent, 999, 0));
        }
        if (headerStatusPill != null) {
            headerStatusPill.setBackground(rounded(pillBg, 999, pillStroke));
        }
        if (headerStatusText != null) {
            headerStatusText.setText(on ? "ACTIVO" : "INACTIVO");
            headerStatusText.setTextColor(accent);
        }
        if (serviceStateBadge != null) {
            serviceStateBadge.setBackground(rounded(pillBg, 999, pillStroke));
        }
        if (serviceStateText != null) {
            serviceStateText.setText(on ? "ACTIVO" : "INACTIVO");
            serviceStateText.setTextColor(accent);
        }
        if (serviceIconBox != null) {
            serviceIconBox.setBackground(rounded(
                    on ? 0x2233D9B2 : 0x22FF7185, 15,
                    on ? 0x4433D9B2 : 0x44FF7185));
        }
        if (serviceIconGlyph != null) {
            serviceIconGlyph.setImageResource(
                    on ? R.drawable.ic_check_circle : R.drawable.ic_alert_triangle);
            serviceIconGlyph.setColorFilter(accent);
        }
        if (serviceDescription != null) {
            serviceDescription.setText(on
                    ? "CostPhone está habilitado y puede recibir acciones de accesibilidad."
                    : "Activa el servicio para que Termux pueda enviar acciones al teléfono.");
        }
        if (actionLabel != null) {
            actionLabel.setText(on ? "Gestionar accesibilidad" : "Activar accesibilidad");
        }
        if (actionButton != null) {
            actionButton.setBackground(on
                    ? gradient(0xFF18CFC2, 0xFF087D91, 14, 0xFF2ADBE1)
                    : gradient(0xFF21D7DE, 0xFF147CBA, 14, 0xFF32E0E7));
        }
    }

    private boolean isServiceOn() {
        AccessibilityManager manager =
                (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        List<AccessibilityServiceInfo> services =
                manager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : services) {
            String id = info.getId();
            if (id != null && id.contains(getPackageName())) return true;
        }
        return false;
    }

    // ─────────────────────────── UTILIDADES ────────────────────────────

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private TextView text(String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(
                bold ? "sans-serif-medium" : "sans-serif",
                bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        return view;
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private GradientDrawable gradient(int start, int end, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[] { start, end });
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams size(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams match() {
        return matchWrap();
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchHeight(int height) {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, height);
    }

    private LinearLayout.LayoutParams topMargin(LinearLayout.LayoutParams params, int marginDp) {
        params.topMargin = dp(marginDp);
        return params;
    }

    private LinearLayout.LayoutParams topMarginAndLeft(
            LinearLayout.LayoutParams params, int topDp, int leftDp) {
        params.topMargin = dp(topDp);
        params.leftMargin = dp(leftDp);
        return params;
    }
}
