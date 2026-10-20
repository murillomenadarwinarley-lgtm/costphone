package com.accessdroid.termux;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * CostPhone — panel principal.
 *
 * Interfaz rediseñada en Java nativo, sin dependencias externas.
 * El cambio es exclusivamente visual: el estado real del AccessibilityService
 * y el acceso a los ajustes de Android siguen funcionando como antes.
 */
public class SettingsActivity extends Activity {

    // Fondo nocturno y acentos de la identidad visual CostPhone.
    private static final int BG_DARK = 0xFF060B14;
    private static final int BG_CARD = 0xFF0B1524;
    private static final int BG_CARD_ALT = 0xFF102035;
    private static final int BG_HERO = 0xFF10283A;
    private static final int CYAN = 0xFF35E6E8;
    private static final int CYAN_DARK = 0xFF0C8E9B;
    private static final int TEAL = 0xFF18D9BC;
    private static final int GREEN = 0xFF25D9A1;
    private static final int RED = 0xFFFF7185;
    private static final int TEXT_MAIN = 0xFFF2F7FF;
    private static final int TEXT_MUTED = 0xFF9AADC8;
    private static final int TEXT_SOFT = 0xFF7186A5;
    private static final int STROKE = 0xFF1A3046;
    private static final int STROKE_CYAN = 0xFF205064;

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

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        if (getActionBar() != null) {
            getActionBar().hide();
        }

        getWindow().setStatusBarColor(BG_DARK);
        getWindow().setNavigationBarColor(BG_DARK);
        getWindow().getDecorView().setSystemUiVisibility(0);

        setContentView(buildScreen());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setBackgroundColor(BG_DARK);

        LinearLayout page = column();
        page.setPadding(dp(20), dp(16), dp(20), dp(28));
        scroll.addView(page, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        page.addView(buildHeader(), matchWrap());
        page.addView(buildHero(), topMargin(match(), 22));
        page.addView(buildServiceCard(), topMargin(match(), 16));

        page.addView(sectionLabel("FUNCIONES PRINCIPALES"), topMargin(wrap(), 26));

        page.addView(buildInfoCard(
                R.drawable.ic_terminal,
                "Control desde Termux",
                "Lanza aplicaciones, toca, desliza y escribe mediante comandos amctl.",
                0xFF45DCE6), topMargin(match(), 12));

        page.addView(buildInfoCard(
                R.drawable.ic_inspect,
                "Lectura de interfaz",
                "Inspecciona elementos en pantalla y obtiene referencias para el agente.",
                0xFF8B9CFF), topMargin(match(), 10));

        page.addView(buildInfoCard(
                R.drawable.ic_display,
                "Pantalla virtual",
                "Modo opcional para separar las tareas del agente de la pantalla principal.",
                0xFF45D9B2), topMargin(match(), 10));

        TextView footer = text(
                "CostPhone  ·  v1.2\nControl local para Android, junto a Termux.",
                11,
                TEXT_SOFT,
                false);
        footer.setGravity(Gravity.CENTER);
        footer.setLineSpacing(dp(4), 1.0f);
        page.addView(footer, topMargin(match(), 24));

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
        header.addView(logo, size(dp(48), dp(48)));

        LinearLayout brand = column();
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        brandLp.leftMargin = dp(11);
        header.addView(brand, brandLp);

        TextView name = text("CostPhone", 21, TEXT_MAIN, true);
        brand.addView(name);

        TextView tagline = text("AGENTE LOCAL · ANDROID", 9, TEXT_MUTED, true);
        tagline.setLetterSpacing(0.07f);
        LinearLayout.LayoutParams taglineLp = topMargin(wrap(), 4);
        brand.addView(tagline, taglineLp);

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
        card.setPadding(dp(17), dp(18), dp(17), dp(16));
        card.setBackground(gradient(BG_HERO, BG_CARD, 24, STROKE_CYAN));
        card.setElevation(dp(2));

        LinearLayout content = row();
        content.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout copy = column();
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        copyLp.rightMargin = dp(3);
        content.addView(copy, copyLp);

        TextView eyebrow = text("CONTROL INTELIGENTE", 9, CYAN, true);
        eyebrow.setLetterSpacing(0.09f);
        copy.addView(eyebrow);

        TextView titleOne = text("Tu IA trabaja", 23, TEXT_MAIN, true);
        LinearLayout.LayoutParams titleOneLp = topMargin(wrap(), 10);
        copy.addView(titleOne, titleOneLp);

        TextView titleTwo = text("por ti.", 26, CYAN, true);
        LinearLayout.LayoutParams titleTwoLp = topMargin(wrap(), 0);
        copy.addView(titleTwo, titleTwoLp);

        TextView body = text(
                "Conecta tu agente con Android a través de Termux.",
                12,
                TEXT_MUTED,
                false);
        body.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams bodyLp = topMargin(match(), 9);
        copy.addView(body, bodyLp);

        LinearLayout.LayoutParams orbLp = size(dp(100), dp(116));
        orbLp.leftMargin = dp(1);
        content.addView(createOrb(), orbLp);

        card.addView(content);

        LinearLayout chips = row();
        chips.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams chipsLp = topMargin(wrap(), 14);
        card.addView(chips, chipsLp);
        chips.addView(chip("INTEGRACIÓN CON TERMUX"), wrap());
        chips.addView(chip("SIN ROOT"), topMarginAndLeft(wrap(), 0, 7));

        return card;
    }

    private View createOrb() {
        FrameLayout orb = new FrameLayout(this);

        View halo = new View(this);
        halo.setBackground(gradient(0x3332D7E0, 0x00112C42, 999, 0));
        orb.addView(halo, new FrameLayout.LayoutParams(
                dp(108), dp(108), Gravity.CENTER));

        View outerRing = new View(this);
        outerRing.setBackground(rounded(0x00000000, 999, 0x6634DDE8));
        orb.addView(outerRing, new FrameLayout.LayoutParams(
                dp(104), dp(104), Gravity.CENTER));

        View middleRing = new View(this);
        middleRing.setBackground(rounded(0x00000000, 999, 0xAA35BFCF));
        FrameLayout.LayoutParams middleLp = new FrameLayout.LayoutParams(
                dp(82), dp(82), Gravity.CENTER);
        orb.addView(middleRing, middleLp);

        View core = new View(this);
        core.setBackground(gradient(0xFF173B55, 0xFF091421, 999, 0xFF38DCE6));
        FrameLayout.LayoutParams coreLp = new FrameLayout.LayoutParams(
                dp(58), dp(58), Gravity.CENTER);
        orb.addView(core, coreLp);

        LinearLayout eyes = row();
        eyes.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams eyesLp = new FrameLayout.LayoutParams(
                dp(42), dp(22), Gravity.CENTER);
        orb.addView(eyes, eyesLp);

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
                dp(24), dp(24), Gravity.TOP | Gravity.RIGHT);
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
        serviceIconGlyph.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        serviceIconGlyph.setPadding(dp(10), dp(10), dp(10), dp(10));
        ((FrameLayout) serviceIconBox).addView(serviceIconGlyph,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
        header.addView(serviceIconBox, size(dp(44), dp(44)));

        LinearLayout heading = column();
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        headingLp.leftMargin = dp(11);
        header.addView(heading, headingLp);

        TextView small = text("ESTADO DEL SISTEMA", 9, TEXT_SOFT, true);
        small.setLetterSpacing(0.07f);
        heading.addView(small);

        TextView serviceTitle = text("Accesibilidad", 16, TEXT_MAIN, true);
        heading.addView(serviceTitle, topMargin(wrap(), 4));

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
                13,
                TEXT_MUTED,
                false);
        serviceDescription.setLineSpacing(dp(3), 1.0f);
        card.addView(serviceDescription, topMargin(match(), 14));

        View divider = new View(this);
        divider.setBackgroundColor(STROKE);
        card.addView(divider, topMargin(matchHeight(dp(1)), 16));

        actionButton = row();
        actionButton.setGravity(Gravity.CENTER_VERTICAL);
        actionButton.setPadding(dp(16), dp(14), dp(15), dp(14));
        actionButton.setBackground(gradient(0xFF18CFC2, 0xFF087D91, 15, 0xFF2ADBE1));
        actionButton.setClickable(true);
        actionButton.setFocusable(true);
        actionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });

        actionLabel = text("Gestionar accesibilidad", 14, 0xFFFFFFFF, true);
        LinearLayout.LayoutParams actionLabelLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        actionButton.addView(actionLabel, actionLabelLp);

        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_arrow_right);
        arrow.setColorFilter(0xFFFFFFFF);
        arrow.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        actionButton.addView(arrow, size(dp(28), dp(28)));

        card.addView(actionButton, topMargin(match(), 15));

        return card;
    }

    private View buildInfoCard(int iconResource, String heading, String description, int accent) {
        LinearLayout card = row();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(gradient(BG_CARD, BG_CARD_ALT, 18, STROKE));

        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setBackground(rounded(withAlpha(accent, 0x28), 14, withAlpha(accent, 0x66)));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setColorFilter(accent);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        iconBox.addView(icon, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        card.addView(iconBox, size(dp(44), dp(44)));

        LinearLayout copy = column();
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        copyLp.leftMargin = dp(12);
        card.addView(copy, copyLp);

        TextView title = text(heading, 14, TEXT_MAIN, true);
        copy.addView(title);

        TextView body = text(description, 12, TEXT_MUTED, false);
        body.setLineSpacing(dp(2), 1.0f);
        copy.addView(body, topMargin(match(), 5));

        return card;
    }

    private View sectionLabel(String label) {
        LinearLayout line = row();
        line.setGravity(Gravity.CENTER_VERTICAL);

        View accent = new View(this);
        accent.setBackground(rounded(CYAN, 999, 0));
        line.addView(accent, size(dp(3), dp(14)));

        TextView caption = text(label, 10, TEXT_MUTED, true);
        caption.setLetterSpacing(0.11f);
        line.addView(caption, topMarginAndLeft(wrap(), 0, 9));

        return line;
    }

    private View chip(String label) {
        TextView view = text(label, 8, TEXT_MUTED, true);
        view.setLetterSpacing(0.04f);
        view.setPadding(dp(9), dp(7), dp(9), dp(7));
        view.setBackground(rounded(0x1A35DCE6, 999, 0x5535DCE6));
        return view;
    }

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
                    on ? 0x2233D9B2 : 0x22FF7185,
                    15,
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
            actionLabel.setText(on
                    ? "Gestionar accesibilidad"
                    : "Activar accesibilidad");
        }
        if (actionButton != null) {
            actionButton.setBackground(on
                    ? gradient(0xFF18CFC2, 0xFF087D91, 15, 0xFF2ADBE1)
                    : gradient(0xFF21D7DE, 0xFF147CBA, 15, 0xFF32E0E7));
        }
    }

    private boolean isServiceOn() {
        AccessibilityManager manager =
                (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (manager == null) {
            return false;
        }

        List<AccessibilityServiceInfo> services =
                manager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : services) {
            String id = info.getId();
            if (id != null && id.contains(getPackageName())) {
                return true;
            }
        }
        return false;
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
        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private GradientDrawable gradient(int start, int end, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] { start, end });
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
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
                LinearLayout.LayoutParams.MATCH_PARENT,
                height);
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
