package com.accessdroid.termux;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Componentes de UI reutilizables para CostPhone Fase 2c.
 * Solo usa tokens de colors.xml (vía recursos), sin colores sueltos en Java.
 */
public final class Ui {

    private Ui() { }

    // ── Helpers base ──

    public static int dp(Activity a, float v) {
        return (int) (v * a.getResources().getDisplayMetrics().density);
    }

    public static int color(Activity a, String name) {
        int id = a.getResources().getIdentifier(name, "color", a.getPackageName());
        return id != 0 ? a.getResources().getColor(id) : Color.MAGENTA;
    }

    public static GradientDrawable shape(Activity a, String fillColor, float radiusDp, String strokeColor, float strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color(a, fillColor));
        g.setCornerRadius(dp(a, radiusDp));
        if (strokeColor != null) g.setStroke(dp(a, strokeDp), color(a, strokeColor));
        return g;
    }

    /** Ripple con contenido y/o máscara (máscara define el área si content es null). */
    public static RippleDrawable ripple(Activity a, Drawable content, Drawable mask, int fgColor) {
        if (Build.VERSION.SDK_INT >= 21) {
            return new RippleDrawable(android.content.res.ColorStateList.valueOf(fgColor), content, mask);
        }
        // fallback: sin ripple en API < 21 (minSdk 24 → nunca ocurre)
        return null;
    }

    // ── Card ──

    public static LinearLayout card(Activity a) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = shape(a, "surface", 20f, "border", 1f);
        c.setBackground(bg);
        c.setPadding(dp(a, 16), dp(a, 16), dp(a, 16), dp(a, 16));
        return c;
    }

    // ── Section header (icono + título) ──

    public static LinearLayout sectionHeader(Activity a, int iconRes, String title) {
        LinearLayout h = new LinearLayout(a);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(a);
        icon.setImageResource(iconRes);
        icon.setColorFilter(color(a, "accent"));
        h.addView(icon, new LinearLayout.LayoutParams(dp(a, 20), dp(a, 20)));

        TextView t = new TextView(a);
        t.setText(title);
        t.setTextSize(15);
        t.setTextColor(color(a, "text"));
        t.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(a, 10);
        h.addView(t, lp);

        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.bottomMargin = dp(a, 12);
        h.setLayoutParams(hlp);
        return h;
    }

    // ── Primary button: degradado turquesa, texto on_accent ──

    public static LinearLayout primaryButton(Activity a, final int iconRes, String text, final View.OnClickListener onClick) {
        LinearLayout b = new LinearLayout(a);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER);
        b.setMinimumHeight(dp(a, 48));
        b.setPadding(dp(a, 16), dp(a, 12), dp(a, 16), dp(a, 12));

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{color(a, "accent"), color(a, "accent")});
        bg.setCornerRadius(dp(a, 14));
        b.setBackground(ripple(a, bg, null, color(a, "accent_primary_dark")));

        if (iconRes != 0) {
            ImageView iv = new ImageView(a);
            iv.setImageResource(iconRes);
            iv.setColorFilter(color(a, "on_accent"));
            b.addView(iv, new LinearLayout.LayoutParams(dp(a, 20), dp(a, 20)));
        }
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(color(a, "on_accent"));
        t.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (iconRes != 0) tlp.leftMargin = dp(a, 8);
        b.addView(t, tlp);

        b.setOnClickListener(onClick);
        return b;
    }

    // ── Secondary button: fondo surface2, borde acento 40%, texto acento ──

    public static LinearLayout secondaryButton(Activity a, final int iconRes, String text, final View.OnClickListener onClick) {
        LinearLayout b = new LinearLayout(a);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER);
        b.setMinimumHeight(dp(a, 48));
        b.setPadding(dp(a, 16), dp(a, 12), dp(a, 16), dp(a, 12));

        GradientDrawable bg = shape(a, "surface2", 14f, null, 0);
        int accent = color(a, "accent");
        bg.setStroke(dp(a, 1), (accent & 0x00FFFFFF) | 0x66000000);
        b.setBackground(ripple(a, bg, null, (accent & 0x00FFFFFF) | 0x33000000));

        if (iconRes != 0) {
            ImageView iv = new ImageView(a);
            iv.setImageResource(iconRes);
            iv.setColorFilter(color(a, "accent"));
            b.addView(iv, new LinearLayout.LayoutParams(dp(a, 20), dp(a, 20)));
        }
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(color(a, "accent"));
        t.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (iconRes != 0) tlp.leftMargin = dp(a, 8);
        b.addView(t, tlp);

        b.setOnClickListener(onClick);
        return b;
    }

    // ── Ghost button: solo texto ──

    public static LinearLayout ghostButton(Activity a, final int iconRes, String text, final View.OnClickListener onClick) {
        LinearLayout b = new LinearLayout(a);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER);
        b.setMinimumHeight(dp(a, 48));
        b.setPadding(dp(a, 16), dp(a, 12), dp(a, 16), dp(a, 12));
        b.setBackground(ripple(a, null, shape(a, "surface", 14f, null, 0),
                color(a, "accent")));

        if (iconRes != 0) {
            ImageView iv = new ImageView(a);
            iv.setImageResource(iconRes);
            iv.setColorFilter(color(a, "accent"));
            b.addView(iv, new LinearLayout.LayoutParams(dp(a, 20), dp(a, 20)));
        }
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(color(a, "accent"));
        t.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (iconRes != 0) tlp.leftMargin = dp(a, 8);
        b.addView(t, tlp);

        b.setOnClickListener(onClick);
        return b;
    }

    // ── Chip: pastilla ──

    public static LinearLayout chip(Activity a, String text, String colorToken) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(a, 10), dp(a, 4), dp(a, 10), dp(a, 4));
        // relleno translúcido (18%) del propio token → texto legible (>= 4.5:1)
        GradientDrawable bg = new GradientDrawable();
        int base = color(a, colorToken);
        bg.setColor((base & 0x00FFFFFF) | 0x2E000000);
        bg.setCornerRadius(dp(a, 999f));
        c.setBackground(bg);

        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(11);
        t.setTextColor(color(a, colorToken));
        t.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(t);
        return c;
    }

    // ── Stat tile: icono + etiqueta + valor ──

    public static LinearLayout statTile(Activity a, int iconRes, String label, String value) {
        LinearLayout tile = new LinearLayout(a);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(a, 12), dp(a, 14), dp(a, 12), dp(a, 14));
        tile.setBackground(shape(a, "surface", 20f, "border", 1f));
        tile.setMinimumHeight(dp(a, 90));

        ImageView icon = new ImageView(a);
        icon.setImageResource(iconRes);
        icon.setColorFilter(color(a, "accent"));
        tile.addView(icon, new LinearLayout.LayoutParams(dp(a, 24), dp(a, 24)));

        TextView v = new TextView(a);
        v.setText(value);
        v.setTextSize(14);
        v.setTextColor(color(a, "text"));
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER);
        v.setMaxLines(2);
        v.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = dp(a, 6);
        tile.addView(v, vlp);

        TextView l = new TextView(a);
        l.setText(label);
        l.setTextSize(11);
        l.setTextColor(color(a, "text3"));
        l.setGravity(Gravity.CENTER);
        l.setMaxLines(1);
        l.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(a, 2);
        tile.addView(l, llp);

        return tile;
    }

    // ── Log row: punto tintado por tipo + hora + mensaje ──

    public static LinearLayout logRow(Activity a, String type, String hora, String mensaje) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(a, 4), dp(a, 3), dp(a, 4), dp(a, 3));

        View dot = new View(a);
        String colorToken;
        switch (type) {
            case "ok": colorToken = "ok"; break;
            case "info": colorToken = "info"; break;
            case "aviso": colorToken = "aviso"; break;
            case "error": colorToken = "error"; break;
            default: colorToken = "text3"; break;
        }
        GradientDrawable dg = new GradientDrawable();
        dg.setShape(GradientDrawable.OVAL);
        dg.setColor(color(a, colorToken));
        dot.setBackground(dg);
        row.addView(dot, new LinearLayout.LayoutParams(dp(a, 7), dp(a, 7)));

        TextView h = new TextView(a);
        h.setText(hora);
        h.setTextSize(11);
        h.setTextColor(color(a, "text3"));
        h.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.leftMargin = dp(a, 8);
        row.addView(h, hlp);

        TextView m = new TextView(a);
        m.setText(mensaje);
        m.setTextSize(12);
        m.setTextColor(color(a, "text2"));
        m.setMaxLines(2);
        m.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mlp.leftMargin = dp(a, 8);
        row.addView(m, mlp);

        return row;
    }

    // ── Empty state: icono grande + texto + botón opcional ──

    public static LinearLayout emptyState(Activity a, int iconRes, String message) {
        LinearLayout e = new LinearLayout(a);
        e.setOrientation(LinearLayout.VERTICAL);
        e.setGravity(Gravity.CENTER);
        e.setPadding(dp(a, 16), dp(a, 32), dp(a, 16), dp(a, 32));

        ImageView icon = new ImageView(a);
        icon.setImageResource(iconRes);
        icon.setColorFilter(color(a, "text3"));
        e.addView(icon, new LinearLayout.LayoutParams(dp(a, 48), dp(a, 48)));

        TextView t = new TextView(a);
        t.setText(message);
        t.setTextSize(14);
        t.setTextColor(color(a, "text3"));
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(3);
        t.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(a, 12);
        e.addView(t, tlp);

        return e;
    }
}
