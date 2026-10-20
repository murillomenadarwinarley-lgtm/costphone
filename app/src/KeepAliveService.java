package com.accessdroid.termux;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

/**
 * Servicio en primer plano que mantiene a CostPhone con vida.
 *
 * Android mata procesos enseguida cuando una app no tiene nada visible, y el
 * servicio de accesibilidad es justo el que más necesita no morir. Una
 * notificación persistente (imposible de quitar sin desactivar el servicio) es
 * el precio de eso: el sistema ve trabajo en primer plano y deja el proceso en
 * paz.
 *
 * Target SDK 34: el tipo es `specialUse` y hace falta declarar también el
 * permiso FOREGROUND_SERVICE_SPECIAL_USE, no basta con FOREGROUND_SERVICE.
 */
public class KeepAliveService extends Service {

    private static final String TAG = "CostPhone";
    public static final String CHANNEL_ID = "costphone_remote_v2";
    private static final String OLD_CHANNEL_ID = "costphone_keep";
    public static final int NOTIF_ID = 1001;

    // Una desvinculación puede ser transitoria: no retirar de inmediato el foreground service.
    private static final long UNBIND_GRACE_MS = 15_000L;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static volatile Context appContext;
    private static final Runnable STOP_IF_DISABLED = new Runnable() {
        @Override public void run() {
            Context ctx = appContext;
            if (ctx == null) return;
            if (isAccessibilityEnabled(ctx)) {
                Log.w(TAG, "Accesibilidad sigue habilitada; se conserva keep-alive durante reconexión");
                ConnectionLog.add("Keep-alive conservado: Accesibilidad sigue habilitada; esperando reconexión");
                return;
            }
            ConnectionLog.add("Accesibilidad deshabilitada; se detiene keep-alive");
            stop(ctx);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n;
        try {
            n = buildNotification();
        } catch (Throwable t) {
            // Si la notificación falla, el servicio no puede quedar en primer
            // plano sin ella: paramos en vez de dejarlo roto a medias.
            Log.e(TAG, "no se pudo construir la notificación", t);
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            startForeground(NOTIF_ID, n);
            ConnectionLog.add("Notificación persistente activa");
            return START_STICKY;
        } catch (Throwable t) {
            Log.e(TAG, "keep-alive no disponible; CostPhone seguirá intentando operar", t);
            try {
                ConnectionLog.add("KEEPALIVE_START_FAILED type=" + t.getClass().getSimpleName()
                        + " message=" + String.valueOf(t.getMessage()));
            } catch (Throwable ignored) { }
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        // Borrar canal anterior si existe (migración de costphone_keep → costphone_remote_v2).
        if (nm.getNotificationChannel(OLD_CHANNEL_ID) != null) {
            nm.deleteNotificationChannel(OLD_CHANNEL_ID);
        }
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Control remoto de CostPhone", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Mantiene el control remoto de CostPhone disponible.");
        ch.setShowBadge(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        // Botón de la notificación: abrir la app.
        Intent open = new Intent(this, SettingsActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(
                this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        b.setContentTitle("CostPhone")
         .setContentText("Control remoto activo para Termux")
         .setSmallIcon(R.drawable.ic_stat_costphone)
         .setContentIntent(openPi)
         .setOngoing(true)
         .setCategory(Notification.CATEGORY_SERVICE)
         .setColor(0xFF1FBF8F)
         .setVisibility(Notification.VISIBILITY_PUBLIC)
         .setShowWhen(false);
        if (Build.VERSION.SDK_INT < 26) {
            b.setPriority(Notification.PRIORITY_LOW)
             .setDefaults(0);
        }

        // Acción explícita: abrir la app sin tener que tocar la notificación.
        Intent action = new Intent(this, SettingsActivity.class);
        action.setAction(Intent.ACTION_MAIN);
        action.addCategory(Intent.CATEGORY_LAUNCHER);
        PendingIntent actionPi = PendingIntent.getActivity(
                this, 2, action, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        b.addAction(new Notification.Action.Builder(
                null, "Abrir app", actionPi).build());

        return b.build();
    }

    @Override public void onDestroy() {
        // Al irse, se acabó: el servicio de accesibilidad se apaga y no hay
        // nada que mantener.
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (Throwable ignored) {
            // STOP_FOREGROUND_REMOVE no existe en API < 24.
            try { stopForeground(true); } catch (Throwable ignored2) { }
        }
        ConnectionLog.add("Notificación persistente retirada");
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    /** Arranca el servicio y cancela cualquier parada pendiente. */
    public static void start(Context ctx) {
        if (ctx == null) return;
        Context app = ctx.getApplicationContext();
        appContext = app;
        MAIN_HANDLER.removeCallbacks(STOP_IF_DISABLED);
        try {
            Intent i = new Intent(app, KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i);
            else app.startService(i);
        } catch (Throwable t) {
            Log.e(TAG, "no se pudo arrancar el servicio en primer plano", t);
            try {
                ConnectionLog.add("KEEPALIVE_START_REJECTED type=" + t.getClass().getSimpleName()
                        + " message=" + String.valueOf(t.getMessage()));
            } catch (Throwable ignored) { }
        }
    }

    /**
     * Se llama desde onUnbind/onDestroy. Solo apagar el FGS si el usuario ha
     * deshabilitado CostPhone en Accesibilidad; si aún está habilitado, dejar
     * que Android vuelva a enlazar el AccessibilityService.
     */
    public static void stopIfAccessibilityDisabled(Context ctx) {
        if (ctx == null) return;
        appContext = ctx.getApplicationContext();
        MAIN_HANDLER.removeCallbacks(STOP_IF_DISABLED);
        MAIN_HANDLER.postDelayed(STOP_IF_DISABLED, UNBIND_GRACE_MS);
        ConnectionLog.add("Accesibilidad se desvinculó; verificar antes de detener keep-alive");
    }

    private static boolean isAccessibilityEnabled(Context ctx) {
        try {
            String enabled = Settings.Secure.getString(ctx.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null || enabled.trim().isEmpty()) return false;
            ComponentName target = new ComponentName(ctx, AccessibilityServiceImpl.class);
            for (String item : enabled.split(":")) {
                if (target.equals(ComponentName.unflattenFromString(item))) return true;
            }
        } catch (Throwable t) {
            Log.w(TAG, "no se pudo comprobar Accesibilidad; se conserva keep-alive", t);
            return true;
        }
        return false;
    }

    public static void stop(Context ctx) {
        try {
            ctx.stopService(new Intent(ctx, KeepAliveService.class));
        } catch (Throwable ignored) { }
    }
}
