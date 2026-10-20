package com.accessdroid.termux;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.AccessibilityService.ScreenshotResult;
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AccessibilityServiceImpl extends AccessibilityService {

    private static final String TAG = "AccessDroid";
    private static final String ACTION_TAP   = "com.accessdroid.termux.ACTION_TAP";
    private static final String ACTION_SWIPE = "com.accessdroid.termux.ACTION_SWIPE";
    private static final String ACTION_LONG_PRESS = "com.accessdroid.termux.ACTION_LONG_PRESS";
    private static final String ACTION_SCROLL_UP   = "com.accessdroid.termux.ACTION_SCROLL_UP";
    private static final String ACTION_SCROLL_DOWN = "com.accessdroid.termux.ACTION_SCROLL_DOWN";
    private static final String ACTION_BACK  = "com.accessdroid.termux.ACTION_BACK";
    private static final String ACTION_HOME  = "com.accessdroid.termux.ACTION_HOME";
    private static final String ACTION_RECENTS = "com.accessdroid.termux.ACTION_RECENTS";
    private static final String ACTION_TYPE  = "com.accessdroid.termux.ACTION_TYPE";
    private static final String ACTION_CLICK_TEXT = "com.accessdroid.termux.ACTION_CLICK_TEXT";
    private static final String ACTION_CLICK_ID  = "com.accessdroid.termux.ACTION_CLICK_ID";
    private static final String ACTION_FIND_ID   = "com.accessdroid.termux.ACTION_FIND_ID";
    private static final String ACTION_SCROLL_TO = "com.accessdroid.termux.ACTION_SCROLL_TO";
    private static final String ACTION_PRESS_KEY = "com.accessdroid.termux.ACTION_PRESS_KEY";
    private static final String ACTION_SCREENDUMP = "com.accessdroid.termux.ACTION_SCREENDUMP";
    private static final String ACTION_GET_UI_TREE = "com.accessdroid.termux.ACTION_GET_UI_TREE";
    private static final String ACTION_GET_FOREGROUND_PACKAGE = "com.accessdroid.termux.ACTION_GET_FOREGROUND_PACKAGE";
    private static final String ACTION_EXPORT_LOG = "com.accessdroid.termux.ACTION_EXPORT_LOG";
    // Extra actions for AI agent control
    private static final String ACTION_PINCH = "com.accessdroid.termux.ACTION_PINCH";
    private static final String ACTION_DOUBLE_TAP = "com.accessdroid.termux.ACTION_DOUBLE_TAP";
    private static final String ACTION_CLEAR_TEXT = "com.accessdroid.termux.ACTION_CLEAR_TEXT";
    private static final String ACTION_PASTE = "com.accessdroid.termux.ACTION_PASTE";
    private static final String ACTION_FOCUS = "com.accessdroid.termux.ACTION_FOCUS";
    private static final String ACTION_NOTIFICATIONS = "com.accessdroid.termux.ACTION_NOTIFICATIONS";
    private static final String ACTION_QUICK_SETTINGS = "com.accessdroid.termux.ACTION_QUICK_SETTINGS";
    private static final String ACTION_LOCK = "com.accessdroid.termux.ACTION_LOCK";
    private static final String ACTION_SCREENSHOT = "com.accessdroid.termux.ACTION_SCREENSHOT";
    private static final String ACTION_OPEN_SETTINGS = "com.accessdroid.termux.ACTION_OPEN_SETTINGS";
    private static final String ACTION_START_ACTIVITY = "com.accessdroid.termux.ACTION_START_ACTIVITY";
    private static final String ACTION_OPEN_URI = "com.accessdroid.termux.ACTION_OPEN_URI";
    private static final String ACTION_POWER = "com.accessdroid.termux.ACTION_POWER";
    private static final String ACTION_DISPLAY = "com.accessdroid.termux.ACTION_DISPLAY";
    private static final String ACTION_DISPLAY_BACK = "com.accessdroid.termux.ACTION_DISPLAY_BACK";

    /** Pantalla virtual (display secundario). */
    static final String SETTING_OVERLAY_DISPLAYS = "overlay_display_devices";
    static final String FILE_DISPLAYS = "accessdroid-displays.txt";

    /**
     * Flags que acepta AOSP en overlay_display_devices. Allowlist estricta a
     * propósito: un flag mal escrito hace que Android rechace el ajuste entero
     * sin crear ningún display y sin decir por qué.
     */
    private static final List<String> OVERLAY_FLAGS = java.util.Arrays.asList(
        "secure", "own_content_only", "should_show_system_decorations",
        "gravity_top_left", "gravity_top_right", "gravity_bottom_left", "gravity_bottom_right");

    static AccessibilityServiceImpl sInstance;
    int screenW, screenH;
    BroadcastReceiver receiver;
    // Cola interactiva corta: nunca dejar una larga fila de toques obsoletos.
    private final ThreadPoolExecutor commandExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(32));
    // Los árboles/capturas son caros; nunca compiten con las acciones.
    private final ThreadPoolExecutor diagnosticExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(4));
    // La guardia consulta el paquete activo muchas veces. Aíslarla evita falsos
    // bloqueos cuando una captura o un árbol lento ocupa la cola de diagnósticos.
    private final ThreadPoolExecutor foregroundExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(8));
    private static final int MAX_UI_TREE_LINES = 1200;
    private static final int MAX_UI_TREE_NODES = 5000;
    private static final int MAX_SEARCH_NODES = 3500;
    private static final int MAX_SEARCH_DEPTH = 32;
    // Shared secret – set via build-time constant or default. amctl must send it.
    static final String SECRET = "termux-accessdroid-2025";
    // Screenshot callback state
    interface ScreenshotCB { void onResult(android.graphics.Bitmap bmp); }
    ScreenshotCB pendingCb = null;

    public boolean isConnected() { return sInstance == this && receiver != null; }

    public void captureBitmap(final ScreenshotCB cb) {
        if (Build.VERSION.SDK_INT < 33) { if (cb != null) cb.onResult(null); return; }
        takeScreenshot(android.view.Display.DEFAULT_DISPLAY, getMainExecutor(),
            new TakeScreenshotCallback() {
                @Override public void onSuccess(ScreenshotResult r) {
                    android.graphics.Bitmap bmp = null;
                    try { bmp = android.graphics.Bitmap.wrapHardwareBuffer(r.getHardwareBuffer(), r.getColorSpace()); } catch (Exception e) { Log.w(TAG, "wrapHB failed", e); }
                    if (cb != null) cb.onResult(bmp);
                }
                @Override public void onFailure(int errCode) {
                    Log.e(TAG, "captureBitmap fail " + errCode);
                    if (cb != null) cb.onResult(null);
                }
            });
    }

    /** Nombre del PNG por display: el 0 conserva accessdroid-shot.png (compat). */
    static String shotFile(int displayId) {
        return displayId == android.view.Display.DEFAULT_DISPLAY
            ? "accessdroid-shot.png"
            : "accessdroid-shot-d" + displayId + ".png";
    }

    /* Save a file to the public Downloads collection (no storage permission needed). */
    private void saveToDownloads(String name, String mime, byte[] data) {
        try {
            if (Build.VERSION.SDK_INT < 29) {
                java.io.File downloads = android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS);
                if (!downloads.exists() && !downloads.mkdirs()) {
                    Log.w(TAG, "No se pudo crear Downloads para " + name);
                    return;
                }
                java.io.File target = new java.io.File(downloads, name);
                try (java.io.FileOutputStream os = new java.io.FileOutputStream(target, false)) {
                    os.write(data);
                    os.flush();
                }
                Log.i(TAG, "saved Download/" + name + " (" + data.length + " bytes)");
                return;
            }
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name);
            cv.put(android.provider.MediaStore.Downloads.MIME_TYPE, mime);
            cv.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
            try {
                getContentResolver().delete(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    android.provider.MediaStore.Downloads.DISPLAY_NAME + "=?", new String[]{name});
            } catch (Exception ignored) { }
            android.net.Uri uri = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) { Log.w(TAG, "saveToDownloads: insert failed for " + name); return; }
            java.io.OutputStream os = getContentResolver().openOutputStream(uri);
            if (os != null) { os.write(data); os.close(); }
            Log.i(TAG, "saved Download/" + name + " (" + data.length + " bytes)");
        } catch (Exception e) { Log.w(TAG, "saveToDownloads fail: " + name, e); }
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        CrashReporter.install(this);
        ConnectionLog.init(this);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final Intent command = new Intent(intent);
                final long queuedAt = android.os.SystemClock.elapsedRealtime();
                final String action = command.getAction();
                final ThreadPoolExecutor targetExecutor = ACTION_GET_FOREGROUND_PACKAGE.equals(action)
                        ? foregroundExecutor
                        : (usesDiagnosticExecutor(action) ? diagnosticExecutor : commandExecutor);
                try {
                    targetExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            final long startedAt = android.os.SystemClock.elapsedRealtime();
                            long queueWait = startedAt - queuedAt;
                            if (queueWait >= 300L) {
                                ConnectionLog.add("PERF command=" + command.getAction()
                                        + " queue_wait_ms=" + queueWait);
                            }
                            try {
                                handleCommand(command);
                            } catch (Throwable e) {
                                Log.e(TAG, "Error procesando comando " + command.getAction(), e);
                                ConnectionLog.add("COMMAND exception action=" + command.getAction()
                                        + " type=" + e.getClass().getSimpleName());
                            } finally {
                                long total = android.os.SystemClock.elapsedRealtime() - queuedAt;
                                if (total >= 700L) {
                                    ConnectionLog.add("PERF command=" + command.getAction()
                                            + " total_ms=" + total);
                                }
                            }
                        }
                    });
                } catch (RejectedExecutionException e) {
                    ConnectionLog.add("QUEUE busy: comando rechazado " + intent.getAction());
                    Log.w(TAG, "Comando rechazado: cola acotada llena o servicio cerrándose", e);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_TAP);
        filter.addAction(ACTION_SWIPE);
        filter.addAction(ACTION_LONG_PRESS);
        filter.addAction(ACTION_SCROLL_UP);
        filter.addAction(ACTION_SCROLL_DOWN);
        filter.addAction(ACTION_BACK);
        filter.addAction(ACTION_HOME);
        filter.addAction(ACTION_RECENTS);
        filter.addAction(ACTION_TYPE);
        filter.addAction(ACTION_CLICK_TEXT);
        filter.addAction(ACTION_CLICK_ID);
        filter.addAction(ACTION_FIND_ID);
        filter.addAction(ACTION_SCROLL_TO);
        filter.addAction(ACTION_PRESS_KEY);
        filter.addAction(ACTION_SCREENDUMP);
        filter.addAction(ACTION_GET_UI_TREE);
        filter.addAction(ACTION_GET_FOREGROUND_PACKAGE);
        filter.addAction(ACTION_EXPORT_LOG);
        filter.addAction(ACTION_PINCH);
        filter.addAction(ACTION_DOUBLE_TAP);
        filter.addAction(ACTION_CLEAR_TEXT);
        filter.addAction(ACTION_PASTE);
        filter.addAction(ACTION_FOCUS);
        filter.addAction(ACTION_NOTIFICATIONS);
        filter.addAction(ACTION_QUICK_SETTINGS);
        filter.addAction(ACTION_LOCK);
        filter.addAction(ACTION_SCREENSHOT);
        filter.addAction(ACTION_OPEN_SETTINGS);
        filter.addAction(ACTION_START_ACTIVITY);
        filter.addAction(ACTION_OPEN_URI);
        filter.addAction(ACTION_POWER);
        filter.addAction(ACTION_DISPLAY);
        filter.addAction(ACTION_DISPLAY_BACK);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(receiver, filter);
            }
        } catch (Throwable t) {
            Log.e(TAG, "No se pudo registrar el receptor de comandos", t);
            receiver = null;
            sInstance = null;
            return;
        }
        sInstance = this;
        // Cargar configuración persistente de pantallas virtuales desde
        // Settings.Global overlay_display_devices. Si hay valor guardado,
        // aplicarlo como si el usuario hubiera ejecutado "on" desde amctl.
        try {
            String savedOverlay = android.provider.Settings.Global.getString(
                    getContentResolver(), SETTING_OVERLAY_DISPLAYS);
            if (savedOverlay != null && !savedOverlay.trim().isEmpty()) {
                String[] slashParts = savedOverlay.split("/", 2);
                if (slashParts.length == 2) {
                    String[] dimensions = slashParts[0].trim().split("x");
                    String[] dpiAndFlags = slashParts[1].trim().split(",", 2);
                    if (dimensions.length == 2 && dpiAndFlags.length >= 1) {
                        int width = Integer.parseInt(dimensions[0].trim());
                        int height = Integer.parseInt(dimensions[1].trim());
                        int dpi = Integer.parseInt(dpiAndFlags[0].trim());
                        if (width > 0 && height > 0 && dpi > 0) {
                            applySavedOverlayDisplay(width, height, dpi);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Configuración de pantalla virtual inválida; se ignora", t);
            try { ConnectionLog.add("Pantalla virtual: configuración inválida; ignorada"); } catch (Throwable ignored) { }
        }
        // Calienta Shizuku: si ya tiene permiso, deja el UserService enlazado
        // para que el primer comando vaya por shell y no por el arranque lento
        // de accessibility. Si no está, no pasa nada.
        ShizukuManager.get();
        ConnectionLog.init(this);
        ConnectionLog.add("Servicio de accesibilidad conectado y receptor listo");
        KeepAliveService.start(this);
        Log.i(TAG, "AccessibilityService connected – ready");
    }

    /** Aísla los comandos cuyo coste depende del tamaño de la pantalla o del almacenamiento. */
    private boolean usesDiagnosticExecutor(String action) {
        return ACTION_GET_UI_TREE.equals(action)
                || ACTION_SCREENDUMP.equals(action)
                || ACTION_EXPORT_LOG.equals(action)
                || ACTION_DISPLAY.equals(action);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        if (sInstance == this) sInstance = null;
        if (receiver != null) {
            try { unregisterReceiver(receiver); } catch (Throwable ignored) { }
            receiver = null;
        }
        try { KeepAliveService.stopIfAccessibilityDisabled(this); } catch (Throwable ignored) { }
        try { ConnectionLog.add("Servicio de accesibilidad desvinculado; keep-alive en comprobación diferida"); } catch (Throwable ignored) { }
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (receiver != null) {
            try { unregisterReceiver(receiver); } catch (Exception e) { }
        }
        commandExecutor.shutdownNow();
        diagnosticExecutor.shutdownNow();
        foregroundExecutor.shutdownNow();
        // Shizuku tiene ciclo de vida propio; no desconectarlo al reparar Accesibilidad.
        super.onDestroy();
        KeepAliveService.stopIfAccessibilityDisabled(this);
        ConnectionLog.add("Servicio de accesibilidad destruido; keep-alive en comprobación diferida");
        sInstance = null;
    }

    /* -------------------------------------------------------------- */
    /*                        Command dispatcher                      */
    /* -------------------------------------------------------------- */
    private void handleCommand(Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        // Basic safety: require the shared secret from amctl.
        String secret = intent.getStringExtra("secret");
        if (secret == null || !secret.equals(SECRET)) {
            Log.w(TAG, "rejected command with bad/missing secret: " + action);
            return;
        }
        Log.d(TAG, "cmd: " + action);
        final long commandStart = android.os.SystemClock.elapsedRealtime();

        try {
        switch (action) {
            case ACTION_TAP: {
                int x = intent.getIntExtra("x", -1);
                int y = intent.getIntExtra("y", -1);
                if (x < 0 || y < 0) return;
                final int display = disp(intent);
                runShellOrFallback(display, "input_tap", new Runnable() {
                    @Override public void run() { AccessibilityServiceImpl.this.run(makeTap(x, y, 80L, display), "tap"); }
                }, x, y, 80L);
                break;
            }
            case ACTION_LONG_PRESS: {
                int x = intent.getIntExtra("x", -1);
                int y = intent.getIntExtra("y", -1);
                long dur = intent.getLongExtra("duration", 500);
                if (x < 0 || y < 0) return;
                final int display = disp(intent);
                runShellOrFallback(display, "input_swipe", new Runnable() {
                    @Override public void run() { AccessibilityServiceImpl.this.run(makeTap(x, y, dur, display), "longpress"); }
                }, x, y, x, y, dur);
                break;
            }
            case ACTION_SWIPE: {
                int x1 = intent.getIntExtra("x1", -1);
                int y1 = intent.getIntExtra("y1", -1);
                int x2 = intent.getIntExtra("x2", -1);
                int y2 = intent.getIntExtra("y2", -1);
                long dur = intent.getLongExtra("duration", 300);
                if (x1 < 0 || x2 < 0) return;
                final int display = disp(intent);
                runShellOrFallback(display, "input_swipe", new Runnable() {
                    @Override public void run() { AccessibilityServiceImpl.this.run(makeSwipe(x1, y1, x2, y2, dur, display), "swipe"); }
                }, x1, y1, x2, y2, dur);
                break;
            }
            case ACTION_SCROLL_UP: {
                android.util.DisplayMetrics dm = metricsOf(disp(intent));
                int x = intent.getIntExtra("x", dm.widthPixels / 2);
                int y = intent.getIntExtra("y", (int)(dm.heightPixels * 0.5f));
                final int display = disp(intent);
                runShellOrFallback(display, "input_swipe", new Runnable() {
                    @Override public void run() { AccessibilityServiceImpl.this.run(makeScroll(x, y, -0.4f, display), "scrollUp"); }
                }, x, y, x, (int)(y + (dm.heightPixels * -0.4f)), 200L);
                break;
            }
            case ACTION_SCROLL_DOWN: {
                android.util.DisplayMetrics dm = metricsOf(disp(intent));
                int x = intent.getIntExtra("x", dm.widthPixels / 2);
                int y = intent.getIntExtra("y", (int)(dm.heightPixels * 0.5f));
                final int display = disp(intent);
                runShellOrFallback(display, "input_swipe", new Runnable() {
                    @Override public void run() { AccessibilityServiceImpl.this.run(makeScroll(x, y, 0.4f, display), "scrollDown"); }
                }, x, y, x, (int)(y + (dm.heightPixels * 0.4f)), 200L);
                break;
            }
            case ACTION_BACK:
                runShellOrFallback(disp(intent), "input_keyevent",
                        new Runnable() { @Override public void run() { performGlobalAction(GLOBAL_ACTION_BACK); } }, 4);
                break;
            case ACTION_HOME:
                runShellOrFallback(disp(intent), "input_keyevent",
                        new Runnable() { @Override public void run() { performGlobalAction(GLOBAL_ACTION_HOME); } }, 3);
                break;
            case ACTION_RECENTS:
                runShellOrFallback(disp(intent), "input_keyevent",
                        new Runnable() { @Override public void run() { performGlobalAction(GLOBAL_ACTION_RECENTS); } }, 187);
                break;
            case ACTION_TYPE: {
                String text = intent.getStringExtra("text");
                if (text != null && !text.isEmpty()) {
                    Bundle args = new Bundle();
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                    AccessibilityNodeInfo focus = focusIn(disp(intent));
                    if (focus != null) {
                        try {
                            focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                            Log.i(TAG, "typed text length=" + text.length());
                        } finally { try { focus.recycle(); } catch (Throwable ignored) { } }
                    } else {
                        Log.w(TAG, "no focused input field");
                    }
                }
                break;
            }
            case ACTION_CLICK_TEXT: {
                String txt = intent.getStringExtra("text");
                if (txt != null && !txt.isEmpty()) {
                    AccessibilityNodeInfo root = rootFor(disp(intent));
                    if (root != null) {
                        try {
                            AccessibilityNodeInfo target = findByText(root, txt);
                            if (target != null) {
                                try { Log.i(TAG, "click text outcome=" + clickNodeOrAncestor(target)); }
                                finally { try { target.recycle(); } catch (Throwable ignored) { } }
                            } else Log.w(TAG, "click text not found");
                        } finally { try { root.recycle(); } catch (Throwable ignored) { } }
                    }
                }
                break;
            }
            case ACTION_CLICK_ID:
            case ACTION_FIND_ID: {
                String rid = intent.getStringExtra("id");
                if (rid != null && !rid.isEmpty()) {
                    AccessibilityNodeInfo root = rootFor(disp(intent));
                    if (root != null) {
                        try {
                            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(rid);
                            if (nodes != null && !nodes.isEmpty()) {
                                try { Log.i(TAG, "click ID outcome=" + clickNodeOrAncestor(nodes.get(0))); }
                                finally { for (AccessibilityNodeInfo n : nodes) if (n != null) try { n.recycle(); } catch (Throwable ignored) { } }
                            } else Log.w(TAG, "resource ID not found");
                        } finally { try { root.recycle(); } catch (Throwable ignored) { } }
                    }
                }
                break;
            }
            case ACTION_SCROLL_TO: {
                String txt = intent.getStringExtra("text");
                if (txt != null && !txt.isEmpty()) {
                    AccessibilityNodeInfo root = rootFor(disp(intent));
                    if (root != null) {
                        try {
                            AccessibilityNodeInfo target = findByText(root, txt);
                            if (target != null) {
                                try { Log.i(TAG, "scroll-to outcome=" + target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)); }
                                finally { try { target.recycle(); } catch (Throwable ignored) { } }
                            }
                        } finally { try { root.recycle(); } catch (Throwable ignored) { } }
                    }
                }
                break;
            }
            case ACTION_GET_FOREGROUND_PACKAGE: {
                int displayId = disp(intent);
                AccessibilityNodeInfo root = rootFor(displayId);
                String packageName = "";
                if (root != null) {
                    try {
                        CharSequence name = root.getPackageName();
                        packageName = name == null ? "" : name.toString();
                    } catch (Throwable t) {
                        Log.w(TAG, "No se pudo leer el paquete en primer plano", t);
                    } finally {
                        try { root.recycle(); } catch (Throwable ignored) { }
                    }
                }
                String response = "# pkg=" + packageName + "\n";
                try {
                    saveToDownloads("accessdroid-foreground.txt", "text/plain",
                            response.getBytes("UTF-8"));
                } catch (Exception e) {
                    Log.w(TAG, "No se pudo guardar el paquete en primer plano", e);
                }
                break;
            }
            case ACTION_GET_UI_TREE: {
                List<String> lines = new ArrayList<>();
                int d = disp(intent);
                dumpAllWindows(lines, d);
                if (lines.isEmpty()) {
                    // SIEMPRE se escribe el fichero. Antes, un display sin
                    // ventanas no escribía nada y el CLI se quejaba de que "el
                    // servicio no responde", que es mentira y manda a debuggear
                    // lo que no está roto.
                    lines.add("# d" + d + ": " + (d == android.view.Display.DEFAULT_DISPLAY
                        ? "sin ventana activa" : "sin ventanas propias (vacío)"));
                }
                if (lines.size() > MAX_UI_TREE_LINES) {
                    lines = new ArrayList<>(lines.subList(0, MAX_UI_TREE_LINES));
                    lines.add("# truncated: límite de " + MAX_UI_TREE_LINES + " líneas");
                }
                try { saveToDownloads("accessdroid-tree.txt", "text/plain", joinLines(lines).getBytes("UTF-8")); }
                catch (Exception e) { Log.w(TAG, "tree encode fail", e); }
                break;
            }
            case ACTION_SCREENDUMP: {
                int displayId = intent.getIntExtra("displayId", android.view.Display.DEFAULT_DISPLAY);
                if (Build.VERSION.SDK_INT >= 33) {
                    try {
                    takeScreenshot(displayId, diagnosticExecutor,
                        new TakeScreenshotCallback() {
                            @Override
                            public void onSuccess(ScreenshotResult r) {
                                Log.i(TAG, "screenshot ok (displayId=" + displayId + ")");
                                try {
                                    android.graphics.Bitmap hw = android.graphics.Bitmap.wrapHardwareBuffer(r.getHardwareBuffer(), r.getColorSpace());
                                    if (hw != null) {
                                        android.graphics.Bitmap sw = hw.copy(android.graphics.Bitmap.Config.ARGB_8888, false);
                                        hw.recycle();
                                        if (sw != null) {
                                            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                            sw.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, baos);
                                            sw.recycle();
                                            saveToDownloads(shotFile(displayId), "image/png", baos.toByteArray());
                                            try {
                                                getContentResolver().delete(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                                    android.provider.MediaStore.Downloads.DISPLAY_NAME + "=?", new String[]{"accessdroid-shot-error.txt"});
                                            } catch (Exception ignored) { }
                                        }
                                    }
                                    try { r.getHardwareBuffer().close(); } catch (Exception ignored) { }
                                } catch (Exception e) { Log.w(TAG, "screenshot save fail", e); }
                                if (pendingCb != null) {
                                    pendingCb.onResult(null);
                                    pendingCb = null;
                                }
                            }
                            @Override
                            public void onFailure(int errCode) {
                                Log.e(TAG, "screenshot fail: " + errCode + " (display " + displayId + ")");
                                saveToDownloads("accessdroid-shot-error.txt", "text/plain",
                                    ("display=" + displayId + " onFailure errCode=" + errCode).getBytes());
                            }
                        });
                    } catch (Exception e) {
                        Log.w(TAG, "takeScreenshot threw", e);
                        try {
                            saveToDownloads("accessdroid-shot-error.txt", "text/plain",
                                ("display=" + displayId + " takeScreenshot threw: " + e).getBytes("UTF-8"));
                        } catch (Exception ignored) { }
                    }
                } else {
                    Log.w(TAG, "takeScreenshot requires API 33+");
                }
                break;
            }
            case ACTION_PRESS_KEY: {
                int keyCode = intent.getIntExtra("keyCode", 0);
                int global = -1;
                switch (keyCode) {
                    case KeyEvent.KEYCODE_BACK:        global = GLOBAL_ACTION_BACK; break;
                    case KeyEvent.KEYCODE_HOME:        global = GLOBAL_ACTION_HOME; break;
                    case KeyEvent.KEYCODE_APP_SWITCH:  global = GLOBAL_ACTION_RECENTS; break;
                    case KeyEvent.KEYCODE_VOLUME_UP:   global = -2; break; // handled below
                    case KeyEvent.KEYCODE_VOLUME_DOWN: global = -3; break;
                    default: 
                        Log.w(TAG, "unsupported keycode via a11y: " + keyCode);
                        return;
                }
                if (global == -2 || global == -3) {
                    // Volume via audio manager fallback
                    try {
                        android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
                        if (am != null) {
                            if (global == -2) am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI);
                            else am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI);
                        }
                    } catch (Exception e) { Log.w(TAG, "volume adjust fail", e); }
                } else {
                    performGlobalAction(global);
                }
                break;
            }
            case ACTION_PINCH: {
                int dispId = disp(intent);
                android.util.DisplayMetrics dmp = metricsOf(dispId);
                int cx = intent.getIntExtra("cx", dmp.widthPixels / 2);
                int cy = intent.getIntExtra("cy", dmp.heightPixels / 2);
                float scale = intent.getFloatExtra("scale", 0.5f);
                int dist = Math.min(dmp.widthPixels, dmp.heightPixels) / 3;
                int d = (int)(dist * (1 - scale));
                Path p1 = new Path(); p1.moveTo(cx - dist/2, cy); p1.lineTo(cx - d/2, cy);
                Path p2 = new Path(); p2.moveTo(cx + dist/2, cy); p2.lineTo(cx + d/2, cy);
                GestureDescription gd = build(java.util.Arrays.asList(
                        new GestureDescription.StrokeDescription(p1, 0, 400),
                        new GestureDescription.StrokeDescription(p2, 0, 400)), dispId);
                run(gd, scale < 1 ? "pinchIn" : "pinchOut");
                break;
            }
            case ACTION_DOUBLE_TAP: {
                int x = intent.getIntExtra("x", -1);
                int y = intent.getIntExtra("y", -1);
                if (x < 0 || y < 0) return;
                Path p1 = new Path(); p1.moveTo(x, y);
                GestureDescription.StrokeDescription s1 = new GestureDescription.StrokeDescription(p1, 0, 40);
                Path p2 = new Path(); p2.moveTo(x, y);
                GestureDescription.StrokeDescription s2 = new GestureDescription.StrokeDescription(p2, 120, 40);
                GestureDescription gd = build(java.util.Arrays.asList(s1, s2), disp(intent));
                run(gd, "doubleTap");
                break;
            }
            case ACTION_CLEAR_TEXT: {
                AccessibilityNodeInfo f = focusIn(disp(intent));
                if (f != null) {
                    try {
                        Bundle b = new Bundle();
                        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
                        f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
                    } finally { try { f.recycle(); } catch (Throwable ignored) { } }
                }
                break;
            }
            case ACTION_PASTE: {
                AccessibilityNodeInfo f2 = focusIn(disp(intent));
                if (f2 != null) {
                    try { f2.performAction(AccessibilityNodeInfo.ACTION_PASTE); }
                    finally { try { f2.recycle(); } catch (Throwable ignored) { } }
                }
                break;
            }
            case ACTION_FOCUS: {
                String txt = intent.getStringExtra("text");
                String rid = intent.getStringExtra("id");
                AccessibilityNodeInfo root = rootFor(disp(intent));
                if (root == null) break;
                try {
                    AccessibilityNodeInfo target = null;
                    if (txt != null && !txt.isEmpty()) target = findByText(root, txt);
                    else if (rid != null && !rid.isEmpty()) {
                        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(rid);
                        if (nodes != null) {
                            try { if (!nodes.isEmpty()) target = AccessibilityNodeInfo.obtain(nodes.get(0)); }
                            finally { for (AccessibilityNodeInfo n : nodes) if (n != null) try { n.recycle(); } catch (Throwable ignored) { } }
                        }
                    }
                    if (target != null) {
                        try { target.performAction(AccessibilityNodeInfo.ACTION_FOCUS); }
                        finally { try { target.recycle(); } catch (Throwable ignored) { } }
                    }
                } finally { try { root.recycle(); } catch (Throwable ignored) { } }
                break;
            }
            case ACTION_NOTIFICATIONS:
                performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
                break;
            case ACTION_QUICK_SETTINGS:
                performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
                break;
            case ACTION_LOCK:
                if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
                break;
            case ACTION_SCREENSHOT:
                if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
                break;
            case ACTION_POWER:
                if (Build.VERSION.SDK_INT >= 30) performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
                break;
            case ACTION_OPEN_SETTINGS: {
                try {
                    String page = intent.getStringExtra("page");
                    Intent launch;
                    if (page != null && !page.isEmpty()) {
                        launch = new Intent(page);
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    } else {
                        launch = new Intent(android.provider.Settings.ACTION_SETTINGS);
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                    startActivity(launch);
                } catch (Exception e) { Log.w(TAG, "openSettings fail", e); }
                break;
            }
            case ACTION_START_ACTIVITY: {
                try {
                    String pkg = intent.getStringExtra("package");
                    String cls = intent.getStringExtra("class");
                    String action2 = intent.getStringExtra("action");
                    int dispId = disp(intent);
                    Intent launch2 = null;
                    if (action2 != null && !action2.isEmpty()) {
                        launch2 = new Intent(action2);
                    }
                    if (pkg != null && !pkg.isEmpty()) {
                        android.content.pm.PackageManager pm = getPackageManager();
                        if (launch2 == null) launch2 = pm.getLaunchIntentForPackage(pkg);
                        else launch2.setPackage(pkg);
                    }
                    if (cls != null && !cls.isEmpty() && pkg != null) {
                        launch2 = new Intent();
                        launch2.setClassName(pkg, cls);
                    }
                    if (launch2 != null) {
                        launch2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        if (dispId != android.view.Display.DEFAULT_DISPLAY && Build.VERSION.SDK_INT >= 26) {
                            // lanzar la app EN la pantalla virtual, no en la del usuario
                            startActivity(launch2, android.app.ActivityOptions.makeBasic()
                                .setLaunchDisplayId(dispId).toBundle());
                        } else {
                            startActivity(launch2);
                        }
                    }
                } catch (Exception e) { Log.w(TAG, "startActivity fail", e); }
                break;
            }
            case ACTION_OPEN_URI: {
                try {
                    String uri = intent.getStringExtra("uri");
                    String mime = intent.getStringExtra("mime");
                    if (uri != null) {
                        int dispId = disp(intent);
                        Intent open = new Intent(Intent.ACTION_VIEW);
                        open.setData(android.net.Uri.parse(uri));
                        if (mime != null && !mime.isEmpty()) open.setType(mime);
                        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        // El selector de apps ("Abrir con") es una activity del
                        // SISTEMA y se abre siempre en el display 0: ignora
                        // setLaunchDisplayId. Si la URL resuelve a una sola app,
                        // la apuntamos directamente y así sí podemos dirigirla.
                        boolean ambiguous = true;
                        try {
                            android.content.pm.ResolveInfo ri =
                                getPackageManager().resolveActivity(open, 0);
                            if (ri != null && ri.activityInfo != null
                                    && !"android".equals(ri.activityInfo.packageName)) {
                                open.setComponent(new android.content.ComponentName(
                                    ri.activityInfo.packageName, ri.activityInfo.name));
                                ambiguous = false;
                            }
                        } catch (Exception ignored) { }

                        if (dispId != android.view.Display.DEFAULT_DISPLAY && ambiguous
                                && Build.VERSION.SDK_INT >= 26) {
                            try { saveToDownloads("accessdroid-error.txt", "text/plain",
                                ("display=" + dispId + ": esa URL tiene varias apps y el selector del "
                                 + "sistema se abrirá en la pantalla 0 (no admite display). "
                                 + "Usa amctl -d " + dispId + " launch <paquete>").getBytes("UTF-8")); }
                            catch (Exception ignored) { }
                        }

                        if (dispId != android.view.Display.DEFAULT_DISPLAY && Build.VERSION.SDK_INT >= 26) {
                            startActivity(open, android.app.ActivityOptions.makeBasic()
                                .setLaunchDisplayId(dispId).toBundle());
                        } else {
                            startActivity(open);
                        }
                    }
                } catch (Exception e) { Log.w(TAG, "openUri fail", e); }
                break;
            }
            case ACTION_DISPLAY:
                handleDisplayOp(intent);
                break;
            case ACTION_DISPLAY_BACK: {
                int dispId = disp(intent);
                if (dispId == android.view.Display.DEFAULT_DISPLAY) {
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    break;
                }
                // GLOBAL_ACTION_BACK siempre es del display 0: buscamos el botón
                // de retroceso de la app en la pantalla virtual.
                AccessibilityNodeInfo root = rootFor(dispId);
                try {
                    AccessibilityNodeInfo btn = findBackNode(root, 0);
                    if (btn != null) {
                        try { Log.i(TAG, "back en display " + dispId + " → " + btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)); }
                        finally { try { btn.recycle(); } catch (Throwable ignored) { } }
                    } else {
                    Log.w(TAG, "sin botón atrás en display " + dispId);
                    try { saveToDownloads("accessdroid-error.txt", "text/plain",
                        ("display=" + dispId + ": la app no tiene botón de retroceso reconocible en ese display").getBytes("UTF-8")); }
                    catch (Exception ignored) { }
                    }
                } finally { if (root != null) try { root.recycle(); } catch (Throwable ignored) { } }
                break;
            }
            case ACTION_EXPORT_LOG: {
                StringBuilder report = new StringBuilder();
                report.append("CostPhone diagnostic log\n");
                report.append("SDK=").append(Build.VERSION.SDK_INT).append("\n");
                report.append("Time=").append(System.currentTimeMillis()).append("\n");
                String crash = CrashReporter.readLastCrash(this);
                if (crash != null && !crash.isEmpty()) report.append("\n--- LAST FATAL CRASH ---\n").append(crash).append("\n");
                for (String line : ConnectionLog.dumpAll()) report.append(line).append("\n");
                try {
                    saveToDownloads("accessdroid-connection.log", "text/plain",
                            report.toString().getBytes("UTF-8"));
                } catch (Exception e) { Log.w(TAG, "No se pudo exportar el registro", e); }
                break;
            }
        }
        } finally {
            long elapsed = android.os.SystemClock.elapsedRealtime() - commandStart;
            if (elapsed >= 60L) {
                String safeAction = action.length() > 50 ? action.substring(0, 50) : action;
                ConnectionLog.add("PERF command=" + safeAction + " duration_ms=" + elapsed);
            }
        }
    }

    private void runShellOrFallback(int displayId, String action, Runnable fallback, Object... args) {
        // Preferir Shizuku cuando el UserService está enlazado: es el camino
        // rápido habitual del proyecto. La llamada es asíncrona y no bloquea
        // esta cola; si no está preparado, usar Accesibilidad de inmediato.
        if (!ShizukuManager.runShellAsync(displayId, action, fallback, args)
                && fallback != null) {
            fallback.run();
        }
    }

    /* -------------------------------------------------------------- */
    /*                    Multi-display (pantalla virtual)             */
    /* -------------------------------------------------------------- */

    /** Display de la orden (0 = pantalla principal del teléfono). */
    private int disp(Intent intent) {
        return intent.getIntExtra("display", android.view.Display.DEFAULT_DISPLAY);
    }

    /** Métricas de un display (para scroll/pinch y para reports). */
    private android.util.DisplayMetrics metricsOf(int displayId) {
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        android.hardware.display.DisplayManager dman =
            (android.hardware.display.DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        try {
            android.view.Display d = dman == null ? null : dman.getDisplay(displayId);
            if (d != null) { d.getRealMetrics(dm); return dm; }
        } catch (Exception e) { Log.w(TAG, "metricsOf d" + displayId, e); }
        dm.setTo(getResources().getDisplayMetrics());
        return dm;
    }

    /** Árbol raíz de un display cualquiera (0 = el de siempre). */
    /** Nodo raíz del display; el nodo devuelto pertenece al llamador. */
    private AccessibilityNodeInfo rootFor(int displayId) {
        if (displayId == android.view.Display.DEFAULT_DISPLAY) {
            if (Build.VERSION.SDK_INT >= 30) {
                AccessibilityNodeInfo own = topmostAppRoot(displayId);
                if (own != null) return own;
            }
            return getRootInActiveWindow();
        }
        if (Build.VERSION.SDK_INT < 30) return null;
        List<AccessibilityWindowInfo> wins = windowsOf(displayId);
        boolean hasOwnWindows = wins != null && !wins.isEmpty();
        recycleWindows(wins);
        if (!hasOwnWindows) return null;
        AccessibilityNodeInfo own = topmostAppRoot(displayId);
        if (own != null) return own;
        try { return getRootInActiveWindow(displayId); }
        catch (IllegalArgumentException e) {
            Log.w(TAG, "prefetch de accesibilidad ocupado en d" + displayId + "; se omite");
            return null;
        } catch (Exception e) {
            Log.w(TAG, "getRootInActiveWindow(" + displayId + ")", e);
            return null;
        }
    }

    private boolean displayExists(int displayId) {
        try {
            android.hardware.display.DisplayManager dm =
                    (android.hardware.display.DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            return dm != null && dm.getDisplay(displayId) != null;
        } catch (Exception e) { return false; }
    }

    private AccessibilityNodeInfo topmostAppRoot(int displayId) {
        List<AccessibilityWindowInfo> wins = windowsOf(displayId);
        if (wins == null) return null;
        AccessibilityNodeInfo result = null;
        try {
            for (int i = wins.size() - 1; i >= 0; i--) {
                AccessibilityWindowInfo aw = wins.get(i);
                if (aw == null || aw.getDisplayId() != displayId
                        || aw.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                try {
                    result = aw.getRoot();
                    if (result != null) break;
                } catch (Exception e) { Log.w(TAG, "topmostAppRoot d" + displayId, e); }
            }
        } finally { recycleWindows(wins); }
        return result;
    }

    private void recycleWindows(List<AccessibilityWindowInfo> wins) {
        if (wins == null) return;
        for (AccessibilityWindowInfo w : wins) {
            if (w != null) try { w.recycle(); } catch (Throwable ignored) { }
        }
    }

    private List<AccessibilityWindowInfo> windowsOf(int displayId) {
        if (Build.VERSION.SDK_INT < 30) return null;
        try {
            android.util.SparseArray<List<AccessibilityWindowInfo>> all = getWindowsOnAllDisplays();
            if (all == null) return null;
            List<AccessibilityWindowInfo> selected = all.get(displayId);
            for (int i = 0; i < all.size(); i++) {
                List<AccessibilityWindowInfo> list = all.valueAt(i);
                if (list != null && list != selected) recycleWindows(list);
            }
            return selected;
        } catch (Exception e) {
            Log.w(TAG, "getWindowsOnAllDisplays", e);
            return null;
        }
    }

    private AccessibilityNodeInfo focusIn(int displayId) {
        if (Build.VERSION.SDK_INT >= 30) {
            List<AccessibilityWindowInfo> wins = windowsOf(displayId);
            AccessibilityNodeInfo found = null;
            try {
                for (int i = wins != null ? wins.size() - 1 : -1; i >= 0; i--) {
                    AccessibilityWindowInfo aw = wins.get(i);
                    if (aw == null || aw.getDisplayId() != displayId) continue;
                    AccessibilityNodeInfo root = null;
                    try {
                        root = aw.getRoot();
                        if (root != null) found = findEditableFocus(root, 0);
                    } catch (Exception e) { Log.w(TAG, "focusIn d" + displayId, e); }
                    finally { if (root != null) try { root.recycle(); } catch (Throwable ignored) { } }
                    if (found != null) break;
                }
            } finally { recycleWindows(wins); }
            if (found != null) return found;
        }
        if (displayId == android.view.Display.DEFAULT_DISPLAY) return findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        AccessibilityNodeInfo root = rootFor(displayId);
        if (root == null) return null;
        try { return findEditableFocus(root, 0); }
        finally { try { root.recycle(); } catch (Throwable ignored) { } }
    }

    private AccessibilityNodeInfo findEditableFocus(AccessibilityNodeInfo node, int depth) {
        return findEditableFocus(node, depth, new int[]{0});
    }

    private AccessibilityNodeInfo findEditableFocus(AccessibilityNodeInfo node, int depth, int[] visited) {
        if (node == null || depth > 15 || visited[0]++ >= MAX_SEARCH_NODES) return null;
        if (node.isFocused() && node.isEditable()) return AccessibilityNodeInfo.obtain(node);
        for (int i = 0; i < node.getChildCount() && visited[0] < MAX_SEARCH_NODES; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            AccessibilityNodeInfo found = null;
            try { found = findEditableFocus(child, depth + 1, visited); }
            finally { try { child.recycle(); } catch (Throwable ignored) { } }
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findBackNode(AccessibilityNodeInfo node, int depth) {
        return findBackNode(node, depth, new int[]{0});
    }

    private AccessibilityNodeInfo findBackNode(AccessibilityNodeInfo node, int depth, int[] visited) {
        if (node == null || depth > 10 || visited[0]++ >= MAX_SEARCH_NODES) return null;
        if (node.isClickable() || node.isVisibleToUser()) {
            CharSequence cs = node.getText();
            CharSequence cd = node.getContentDescription();
            String hay = ((cs == null ? "" : cs) + " " + (cd == null ? "" : cd)).toLowerCase();
            if (hay.contains("back") || hay.contains("atrás") || hay.contains("atras")
                    || hay.contains("regresar") || hay.contains("navigate up") || hay.contains("volver")) {
                if (node.isClickable()) return AccessibilityNodeInfo.obtain(node);
            }
        }
        for (int i = 0; i < node.getChildCount() && visited[0] < MAX_SEARCH_NODES; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            AccessibilityNodeInfo found = null;
            try { found = findBackNode(child, depth + 1, visited); }
            finally { try { child.recycle(); } catch (Throwable ignored) { } }
            if (found != null) return found;
        }
        return null;
    }

    private boolean canWriteSettings() {
        return getPackageManager().checkPermission("android.permission.WRITE_SECURE_SETTINGS",
                getPackageName()) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /** Reporta todos los displays (id, tamaño, dpi, ventanas) a /sdcard/Download. */
    private void saveDisplays(String errorLine) {
        List<String> out = new ArrayList<>();
        if (errorLine != null) out.add("error=" + errorLine);

        android.hardware.display.DisplayManager dman =
            (android.hardware.display.DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        java.util.TreeSet<Integer> ids = new java.util.TreeSet<>();
        if (dman != null) {
            try { for (android.view.Display d : dman.getDisplays()) ids.add(d.getDisplayId()); }
            catch (Exception e) { Log.w(TAG, "getDisplays", e); }
        }
        // los displays que ve el servicio de accesibilidad también cuentan
        for (int i = 0; i < windowsAllCount(); i++) {
            try { ids.add(allDisplays().keyAt(i)); } catch (Exception e) { break; }
        }

        for (int id : ids) {
            android.view.Display d = dman == null ? null : dman.getDisplay(id);
            android.util.DisplayMetrics dm = metricsOf(id);
            int nwins = 0;
            List<AccessibilityWindowInfo> ws = windowsOf(id);
            if (ws != null) nwins = ws.size();
            out.add(String.format("display=%d name=%s size=%dx%d dpi=%d state=%s wins=%d",
                id,
                d == null ? "?" : String.valueOf(d.getName()),
                dm.widthPixels, dm.heightPixels, dm.densityDpi,
                d == null ? "?" : (d.getState() == android.view.Display.STATE_ON ? "on" : "off"),
                nwins));
        }

        String raw = null;
        try { raw = android.provider.Settings.Global.getString(getContentResolver(), SETTING_OVERLAY_DISPLAYS); }
        catch (Exception ignored) { }
        out.add("overlay=" + (raw == null || raw.isEmpty() ? "none" : raw));
        try {
            saveToDownloads(FILE_DISPLAYS, "text/plain", joinLines(out).getBytes("UTF-8"));
        } catch (Exception e) { Log.w(TAG, "displays encode fail", e); }
    }

    private android.util.SparseArray<List<AccessibilityWindowInfo>> allDisplays() {
        if (Build.VERSION.SDK_INT < 30) return null;
        try { return getWindowsOnAllDisplays(); } catch (Exception e) { return null; }
    }

    private int windowsAllCount() {
        android.util.SparseArray<List<AccessibilityWindowInfo>> a = allDisplays();
        return a == null ? 0 : a.size();
    }

    private void handleDisplayOp(Intent intent) {
        String op = intent.getStringExtra("op");
        if (op == null || op.isEmpty()) op = "status";
        if ("status".equals(op)) { saveDisplays(null); return; }

        if (!canWriteSettings()) {
            saveDisplays("WRITE_SECURE_SETTINGS no concedido a CostPhone. Concede una vez con: "
                + "adb shell pm grant com.accessdroid.termux android.permission.WRITE_SECURE_SETTINGS");
            return;
        }
        if ("off".equals(op)) {
            // borrar el ajuste apaga todas las pantallas virtuales
            android.provider.Settings.Global.putString(getContentResolver(), SETTING_OVERLAY_DISPLAYS, null);
            Log.i(TAG, "pantalla virtual apagada");
            saveDisplays(null);
            return;
        }
        if ("on".equals(op)) {
            int w = intent.getIntExtra("width", 1080);
            int h = intent.getIntExtra("height", 1920);
            int dpi = intent.getIntExtra("density", 320);

            // overlay_display_devices NO es JSON:
            //   [ancho]x[alto]/[dpi][,flag1][,flag2];[display2];...
            if (w < 100 || w > 4096 || h < 100 || h > 4096 || dpi < 120 || dpi > 640) {
                saveDisplays("fuera de rango (100-4096 px, 120-640 dpi): " + w + "x" + h + "@" + dpi);
                return;
            }

            // Flags: allowlist estricta. Un flag desconocido hace fallar TODO el
            // ajuste en AOSP (no crea display y no avisa), así que aquí se
            // rechaza antes de escribir y se dice cuál es el malo.
            LinkedHashSet<String> flags = new LinkedHashSet<>();
            boolean mirror = false;
            String raw = intent.getStringExtra("flags");
            if (raw != null) {
                for (String t : raw.split(",")) {
                    String f = t.trim().toLowerCase(java.util.Locale.ROOT);
                    if (f.isEmpty()) continue;
                    if (f.equals("mirror")) { mirror = true; continue; }  // pseudo-flag, no va al ajuste
                    if (!OVERLAY_FLAGS.contains(f)) {
                        saveDisplays("flag no soportado: '" + f + "'. Válidos: "
                            + String.join(", ", OVERLAY_FLAGS) + " (o 'mirror')");
                        return;
                    }
                    flags.add(f);
                }
            }

            // SUELO DE SEGURIDAD: own_content_only va siempre salvo 'mirror'
            // explícito. Sin él el overlay ESPEJA la pantalla 0 y el agente
            // acaba leyendo y escribiendo en lo que el usuario está usando.
            if (mirror) {
                flags.remove("own_content_only");
            } else {
                flags.add("own_content_only");
            }

            String value = w + "x" + h + "/" + dpi
                + (flags.isEmpty() ? "" : "," + String.join(",", flags));
            try {
                android.provider.Settings.Global.putString(getContentResolver(), SETTING_OVERLAY_DISPLAYS, value);
                Log.i(TAG, "pantalla virtual pedida: " + value);
                saveDisplays(mirror
                    ? "aviso: modo espejo (sin own_content_only). El overlay muestra d0 y el "
                      + "agente puede escribir en la pantalla del usuario: úsalo solo para mirar."
                    : null);
            } catch (Exception e) {
                saveDisplays("fallo al escribir overlay_display_devices: " + e);
            }
            return;
        }
        saveDisplays("op desconocido: " + op);
    }

    /* -------------------------------------------------------------- */
    /*                        Gesture builders                        */
    /* -------------------------------------------------------------- */
    private void applySavedOverlayDisplay(int w, int h, int dpi) {
        // Mismos flags que al ejecutar "on" sin mirror explícito
        LinkedHashSet<String> flags = new LinkedHashSet<>();
        flags.add("own_content_only");
        String value = w + "x" + h + "/" + dpi
            + (flags.isEmpty() ? "" : "," + String.join(",", flags));
        try {
            android.provider.Settings.Global.putString(getContentResolver(), SETTING_OVERLAY_DISPLAYS, value);
            Log.i(TAG, "pantalla virtual restaurada desde configuración guardada: " + value);
            saveDisplays(null);
        } catch (Exception e) {
            saveDisplays("fallo al restaurar overlay_display_devices: " + e);
        }
    }

    /* -------------------------------------------------------------- */
    /*                        Gesture builders                        */
    /* -------------------------------------------------------------- */
    private void run(GestureDescription g, String name) {
        dispatchGesture(g, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                Log.d(TAG, name + " ok");
            }
            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                Log.w(TAG, name + " cancelled");
            }
        }, null);
    }

    /** GestureDescription dirigido a un display (0 = pantalla principal). */
    private GestureDescription build(List<GestureDescription.StrokeDescription> strokes, int displayId) {
        GestureDescription.Builder b = new GestureDescription.Builder();
        for (GestureDescription.StrokeDescription s : strokes) b.addStroke(s);
        if (displayId != android.view.Display.DEFAULT_DISPLAY) b.setDisplayId(displayId);
        return b.build();
    }

    private GestureDescription makeTap(long x, long y, long duration, int displayId) {
        Path p = new Path();
        p.moveTo(x, y);
        return build(java.util.Arrays.asList(new GestureDescription.StrokeDescription(p, 0, duration)), displayId);
    }

    private GestureDescription makeSwipe(int x1, int y1, int x2, int y2, long duration, int displayId) {
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        return build(java.util.Arrays.asList(new GestureDescription.StrokeDescription(p, 0, duration)), displayId);
    }

    private GestureDescription makeScroll(int cx, int cy, float fraction, int displayId) {
        // vertical scroll gesture: from center, flick (negative = scroll content down)
        android.util.DisplayMetrics dm = metricsOf(displayId);
        long dy = (long)(dm.heightPixels * fraction);
        Path p = new Path();
        p.moveTo(cx, cy);
        p.lineTo(cx, cy + dy);
        return build(java.util.Arrays.asList(new GestureDescription.StrokeDescription(p, 0, 200)), displayId);
    }

    public void typeText(String text) {
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        AccessibilityNodeInfo focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused != null) {
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            focused.recycle();
            Log.d(TAG, "typeText: " + text);
        } else {
            Log.w(TAG, "typeText: no focused input field");
        }
    }

    public void pressBack()  { performGlobalAction(GLOBAL_ACTION_BACK); }
    public void pressHome()  { performGlobalAction(GLOBAL_ACTION_HOME); }
    public void pressRecents() { performGlobalAction(GLOBAL_ACTION_RECENTS); }

    /* -------------------------------------------------------------- */
    /*                      UI-tree / find helpers                    */
    /* -------------------------------------------------------------- */

    /** Dump main window + IME/overlay windows: keyboard and floating toolbars
     *  become part of the tree (amctl-snap tags them [ime]/[ovl]/[sys]).
     *  Con displayId != 0 solo se vuelca ese display (pantalla virtual). */
    private void dumpAllWindows(List<String> out, int displayId) {
        AccessibilityNodeInfo root = rootFor(displayId);
        if (root == null) return;
        final Rect mainRect = new Rect();
        final String mainPkg;
        final int[] visitedNodes = new int[]{0};
        try {
            root.getBoundsInScreen(mainRect);
            mainPkg = String.valueOf(root.getPackageName());
            out.add("# display=" + displayId);
            out.add("# pkg=" + mainPkg);
            dumpNode(root, out, 0, 25, visitedNodes);
        } finally { try { root.recycle(); } catch (Throwable ignored) { } }

        List<AccessibilityWindowInfo> wins = windowsOf(displayId);
        if (wins == null) {
            if (visitedNodes[0] >= MAX_UI_TREE_NODES) out.add("# truncated: límite de " + MAX_UI_TREE_NODES + " nodos");
            return;
        }
        try {
            for (int i = 0; i < wins.size() && out.size() < MAX_UI_TREE_LINES
                    && visitedNodes[0] < MAX_UI_TREE_NODES; i++) {
                AccessibilityWindowInfo aw = wins.get(i);
                if (aw == null || aw.getDisplayId() != displayId) continue;
                AccessibilityNodeInfo wroot = null;
                try { wroot = aw.getRoot(); } catch (Exception ignored) { }
                if (wroot == null) continue;
                try {
                    Rect wr = new Rect();
                    wroot.getBoundsInScreen(wr);
                    String wpkg = String.valueOf(wroot.getPackageName());
                    if (wr.equals(mainRect) && wpkg.equals(mainPkg)) continue;
                    String kind = windowTag(aw.getType());
                    if (kind == null) continue;
                    out.add("# win=" + kind + " pkg=" + wpkg);
                    dumpNode(wroot, out, 0, 25, visitedNodes);
                } finally { try { wroot.recycle(); } catch (Throwable ignored) { } }
            }
        } finally { recycleWindows(wins); }
        if (out.size() >= MAX_UI_TREE_LINES) {
            out.add("# truncated: límite de " + MAX_UI_TREE_LINES + " líneas");
        } else if (visitedNodes[0] >= MAX_UI_TREE_NODES) {
            out.add("# truncated: límite de " + MAX_UI_TREE_NODES + " nodos");
        }
    }

    private String windowTag(int type) {
        switch (type) {
            case AccessibilityWindowInfo.TYPE_INPUT_METHOD:        return "ime"; // teclado
            case AccessibilityWindowInfo.TYPE_SYSTEM:              return "sys"; // system overlays
            case AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY: return "ovl"; // toolbars flotantes
            case AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER: return "div";
            case AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY: return "mag";
            default: return null;                                   // TYPE_APPLICATION: duplica la raíz
        }
    }

    private String joinLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /**
     * Intenta accionar el nodo encontrado y después sube por sus contenedores.
     * Muchas apps asignan el listener a la fila o tarjeta, no al TextView cuyo
     * texto ve el agente. Así se evita calcular coordenadas a partir de capturas.
     */
    private boolean clickNodeOrAncestor(AccessibilityNodeInfo target) {
        if (target == null) return false;
        AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(target);
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current.isEnabled()
                    && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                current.recycle();
                return true;
            }
            AccessibilityNodeInfo parent = current.getParent();
            current.recycle();
            current = parent;
        }
        if (current != null) current.recycle();
        return false;
    }

    private AccessibilityNodeInfo findByText(AccessibilityNodeInfo node, String text) {
        return findByText(node, text, 0, new int[]{0});
    }

    private AccessibilityNodeInfo findByText(AccessibilityNodeInfo node, String text, int depth, int[] visited) {
        if (node == null || text == null || depth > MAX_SEARCH_DEPTH
                || visited[0]++ >= MAX_SEARCH_NODES) return null;
        CharSequence cs = node.getText();
        CharSequence desc = node.getContentDescription();
        if ((cs != null && cs.toString().contains(text))
                || (desc != null && desc.toString().contains(text))) return AccessibilityNodeInfo.obtain(node);
        for (int i = 0; i < node.getChildCount() && visited[0] < MAX_SEARCH_NODES; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            AccessibilityNodeInfo found = null;
            try { found = findByText(child, text, depth + 1, visited); }
            finally { try { child.recycle(); } catch (Throwable ignored) { } }
            if (found != null) return found;
        }
        return null;
    }

    private void dumpNode(AccessibilityNodeInfo node, List<String> out, int depth,
                          int maxDepth, int[] visitedNodes) {
        if (depth > maxDepth || node == null || out.size() >= MAX_UI_TREE_LINES
                || visitedNodes[0] >= MAX_UI_TREE_NODES) return;
        visitedNodes[0]++;
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        CharSequence txt = node.getText();
        CharSequence desc = node.getContentDescription();
        String vid = node.getViewIdResourceName();
        int cc = node.getChildCount();
        boolean meaningful = (txt != null && txt.length() > 0)
                || (desc != null && desc.length() > 0)
                || vid != null
                || (cc == 0 && !r.isEmpty());
        if (meaningful) {
            String indent = new String(new char[depth]).replace("\0", "  ");
            String cls = node.getClassName() != null ? node.getClassName().toString() : "";
            String shortClass = cls.contains(".") ? cls.substring(cls.lastIndexOf('.') + 1) : cls;
            StringBuilder flags = new StringBuilder();
            if (node.isClickable()) flags.append(" clk=1");
            if (node.isScrollable()) flags.append(" scr=1");
            if (node.isEditable()) flags.append(" edt=1");
            if (node.isCheckable()) flags.append(" chk=1");
            if (node.isFocused()) flags.append(" foc=1");
            out.add(String.format("%s[%s] id=%s bounds=%s txt=%s desc=%s%s",
                indent, shortClass, vid, r,
                txt == null ? "" : txt, desc == null ? "" : desc, flags));
        }
        for (int i = 0; i < cc && out.size() < MAX_UI_TREE_LINES
                && visitedNodes[0] < MAX_UI_TREE_NODES; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try { dumpNode(child, out, depth + 1, maxDepth, visitedNodes); }
            finally { try { child.recycle(); } catch (Throwable ignored) { } }
        }
    }
}