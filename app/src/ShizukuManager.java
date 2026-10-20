package com.accessdroid.termux;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Display;

import com.accessdroid.termux.shell.ICostPhoneShell;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import rikka.shizuku.Shizuku;

/**
 * Shizuku como backend preferido de CostPhone.
 *
 * Da identidad root/shell para los gestos y teclas que hoy hace el servicio de
 * accesibilidad, que es más lento y se cancela cuando la app pierde el foco.
 * El shell corre en un proceso aparte enlazado con
 * {@link Shizuku#bindUserService} (no con newProcess, que es interno y está
 * terminado de usar en versiones recientes).
 *
 * La regla importante: {@link #runShell} devuelve false en cuanto Shizuku no
 * está disponible, no tiene permiso o el UserService todavía no está enlazado.
 * El llamador cae entonces a accesibilidad. Shizuku es una mejora, nunca un
 * requisito: sin él, CostPhone sigue haciendo exactamente lo que siempre hizo.
 */
public final class ShizukuManager {

    private static final String TAG = "CostPhone";

    /** Shizuku no está corriendo (no instalado, o apagado en este arranque). */
    public static final int UNAVAILABLE = 0;
    /** Shizuku corre, pero CostPhone aún no tiene su permiso. */
    public static final int NO_PERMISSION = 1;
    /** Permiso concedido: se puede enlazar el UserService. */
    public static final int READY = 2;

    private static final ComponentName SHELL_SERVICE = new ComponentName(
            "com.accessdroid.termux", "com.accessdroid.termux.ShizukuShellService");

    /** Margen antes de reintentar un enlace que no llegó a completarse. */
    private static final long BIND_RETRY_MS = 5000L;

    /** Código de petición que usa Shizuku para el permiso. */
    private static final int REQ_PERMISSION = 41;
    /** Intentos de ejecución por comando antes de darlo por fallido. */
    private static final int MAX_EXEC_ATTEMPTS = 3;
    /** Pausa entre intentos. */
    private static final long EXEC_RETRY_DELAY_MS = 200L;
    /** Umbral para detectar IPC de input atascado sin bloquear el dispatcher. */
    private static final long ASYNC_COMMAND_TIMEOUT_MS = 1200L;

    private static ShizukuManager sInstance;

    /** A quién avisar cuando cambia el estado: el panel lo escucha. */
    public interface StateListener { void onStateChanged(int state); }

    private static final List<StateListener> listeners = new ArrayList<>();

    private final Shizuku.UserServiceArgs args =
            new Shizuku.UserServiceArgs(SHELL_SERVICE).daemon(false);

    // Cola serial acotada: no acumular órdenes ilimitadas cuando un Binder se atasca.
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(16));
    private final Handler handler = new Handler(Looper.getMainLooper());

    private volatile ICostPhoneShell shell;
    private volatile boolean binding;
    private volatile long bindStartedAt;
    /** true cuando alguien pidió el permiso: si Shizuku vuelve, se vuelve a pedir. */
    private static volatile boolean wantsPermission;

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            shell = ICostPhoneShell.Stub.asInterface(service);
            binding = false;
            Log.i(TAG, "Shizuku: UserService conectado");
            ConnectionLog.add("Shizuku: UserService conectado");
            notifyStateChanged(state());
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            shell = null;
            binding = false;
            Log.w(TAG, "Shizuku: UserService desconectado");
            ConnectionLog.add("Shizuku: UserService desconectado");
            notifyStateChanged(state());
        }
    };

    private final Shizuku.OnBinderDeadListener deadListener = new Shizuku.OnBinderDeadListener() {
        @Override public void onBinderDead() {
            shell = null;
            binding = false;
            Log.w(TAG, "Shizuku: binder muerto (Shizuku se reinició o paró)");
            notifyStateChanged(UNAVAILABLE);   // cae a accesibilidad, sin ruido
        }
    };

    private final Shizuku.OnBinderReceivedListener receivedListener =
            new Shizuku.OnBinderReceivedListener() {
                @Override public void onBinderReceived() {
                    // Shizuku arrancó con la app ya abierta.
                    int st = state();
                    if (st == NO_PERMISSION && wantsPermission) {
                        // El usuario ya quiso Shizuku una vez: si vuelve y sigue
                        // sin permiso, se lo volvemos a pedir en vez de dejar
                        // el panel en ámbar para siempre.
                        ConnectionLog.add("Shizuku volvió a estar disponible (sin permiso)");
                        requestPermission(REQ_PERMISSION);
                    } else if (st == READY) {
                        ensureBound();
                    }
                    notifyStateChanged(st);
                }
            };

    /** Suscriptor de cambios de estado (el panel). */
    public static void registerStateListener(StateListener l) {
        if (l == null) return;
        synchronized (listeners) {
            if (!listeners.contains(l)) listeners.add(l);
        }
    }

    public static void unregisterStateListener(StateListener l) {
        synchronized (listeners) { listeners.remove(l); }
    }

    /** Avisa en el hilo principal: los listeners tocan la UI. */
    private static void notifyStateChanged(final int st) {
        final List<StateListener> copy;
        synchronized (listeners) { copy = new ArrayList<>(listeners); }
        if (copy.isEmpty()) return;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                for (StateListener l : copy) {
                    try { l.onStateChanged(st); } catch (Throwable ignored) { }
                }
            }
        });
    }

    private ShizukuManager() { }

    /** No hace falta un Context: bindUserService localiza la app por el ComponentName. */
    static synchronized ShizukuManager get() {
        if (sInstance == null) {
            sInstance = new ShizukuManager();
            try {
                Shizuku.addBinderReceivedListenerSticky(sInstance.receivedListener);
                Shizuku.addBinderDeadListener(sInstance.deadListener);
            } catch (Throwable t) {
                Log.w(TAG, "no se pudieron registrar los listeners: " + t);
                ConnectionLog.add("Shizuku: no se pudo registrar el listener: " + t.getClass().getSimpleName());
            }
        }
        ShizukuManager manager = sInstance;
        if (manager != null && state() == READY) manager.ensureBound();
        return manager;
    }

    /** El gestor ya creado, o null. Para soltar recursos sin crearlo. */
    static synchronized ShizukuManager peek() {
        return sInstance;
    }

    /**
     * Estado actual. Es una foto: el binder lo entrega Shizuku de forma
     * asíncrona al arrancar, así que {@link UNAVAILABLE} al principio no
     * significa que Shizuku no esté instalado.
     */
    public static int state() {
        try {
            if (!Shizuku.pingBinder()) return UNAVAILABLE;
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return NO_PERMISSION;
            }
            return READY;
        } catch (Throwable t) {
            ConnectionLog.add("Shizuku no disponible: " + t);
            return UNAVAILABLE;
        }
    }

    /** Pide el permiso de Shizuku. El resultado llega al listener del panel. */
    public static boolean requestPermission(int requestCode) {
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                ShizukuManager manager = get();
                if (manager != null) manager.ensureBound();
                return true;
            }
            wantsPermission = true;
            Shizuku.requestPermission(requestCode);
            ConnectionLog.add("Shizuku: permiso pedido");
            return true;
        } catch (Throwable t) {
            ConnectionLog.add("Shizuku: error al pedir permiso: " + t);
            Log.w(TAG, "requestPermission: " + t);
            return false;
        }
    }

    public static boolean isShellConnected() {
        ShizukuManager manager = peek();
        return manager != null && manager.shell != null;
    }

    public static boolean isShellBinding() {
        ShizukuManager manager = peek();
        return manager != null && manager.binding;
    }

    public static void connectNow() {
        ShizukuManager manager = get();
        if (manager != null) manager.ensureBound();
    }

    /**
     * Intenta ejecutar una operación tipada por Shizuku.
     *
     * @return true si Shizuku ejecutó la operación; false si no hay que esperar
     *         nada y el llamador debe usar accesibilidad.
     */
    /**
     * Encola las acciones de entrada y retorna al dispatcher sin esperar la respuesta Binder.
     * Si el backend/cola no está disponible, el caller usa Accesibilidad directamente.
     * Si un IPC expira tras empezar, no se repite el gesto porque podría ejecutarse dos veces.
     */
    public static boolean runShellAsync(int displayId, String action, Runnable fallback, Object... args) {
        if (displayId != Display.DEFAULT_DISPLAY) return false;
        ShizukuManager manager = peek();
        if (manager == null) return false;
        return manager.execTypedAsync(action, args, fallback);
    }

    private boolean execTypedAsync(final String action, final Object[] args, final Runnable fallback) {
        final ICostPhoneShell target = shell;
        if (target == null) {
            ensureBound();
            return false;
        }
        final AtomicBoolean settled = new AtomicBoolean(false);
        final AtomicBoolean started = new AtomicBoolean(false);
        final long queuedAt = android.os.SystemClock.elapsedRealtime();
        final Runnable[] taskRef = new Runnable[1];
        final Runnable watchdog = new Runnable() {
            @Override public void run() {
                if (!settled.compareAndSet(false, true)) return;
                boolean removed = !started.get() && worker.remove(taskRef[0]);
                if (removed) {
                    ConnectionLog.add("PERF Shizuku action=" + action
                            + " queue_wait_ms=" + (android.os.SystemClock.elapsedRealtime() - queuedAt)
                            + " outcome=not_started");
                    if (fallback != null) fallback.run();
                    return;
                }
                if (shell == target) shell = null;
                binding = true;
                bindStartedAt = android.os.SystemClock.elapsedRealtime();
                ConnectionLog.add("PERF Shizuku action=" + action
                        + " timeout_ms=" + ASYNC_COMMAND_TIMEOUT_MS
                        + " outcome=unknown_no_duplicate_fallback");
                notifyStateChanged(state());
                handler.postDelayed(new Runnable() {
                    @Override public void run() {
                        if (shell == null) {
                            binding = false;
                            ensureBound();
                        }
                    }
                }, BIND_RETRY_MS);
            }
        };
        taskRef[0] = new Runnable() {
            @Override public void run() {
                started.set(true);
                long startedAt = android.os.SystemClock.elapsedRealtime();
                String value = null;
                Throwable failure = null;
                try { value = executeInputAction(target, action, args); }
                catch (Throwable t) { failure = t; }
                long elapsed = android.os.SystemClock.elapsedRealtime() - startedAt;
                if (!settled.compareAndSet(false, true)) {
                    ConnectionLog.add("PERF Shizuku action=" + action
                            + " duration_ms=" + elapsed + " outcome=late_reply_ignored");
                    return;
                }
                handler.removeCallbacks(watchdog);
                if (failure == null && value != null && !value.trim().startsWith("error:")) {
                    if (elapsed >= 60L) ConnectionLog.add("PERF Shizuku action=" + action
                            + " duration_ms=" + elapsed + " outcome=ok");
                    return;
                }
                if (failure == null && value != null && value.trim().startsWith("error:")) {
                    ConnectionLog.add("Shizuku: error explícito en '" + action + "'; fallback de accesibilidad");
                    if (fallback != null) fallback.run();
                    return;
                }
                if (shell == target) shell = null;
                binding = true;
                bindStartedAt = android.os.SystemClock.elapsedRealtime();
                ConnectionLog.add("PERF Shizuku action=" + action
                        + " duration_ms=" + elapsed + " outcome=ipc_exception_no_duplicate");
                Log.w(TAG, "IPC Shizuku ambiguo para " + action, failure);
                notifyStateChanged(state());
                handler.postDelayed(new Runnable() {
                    @Override public void run() {
                        if (shell == null) {
                            binding = false;
                            ensureBound();
                        }
                    }
                }, BIND_RETRY_MS);
            }
        };
        handler.postDelayed(watchdog, ASYNC_COMMAND_TIMEOUT_MS);
        try {
            worker.execute(taskRef[0]);
            return true;
        } catch (RejectedExecutionException e) {
            settled.set(true);
            handler.removeCallbacks(watchdog);
            ConnectionLog.add("Shizuku: cola llena; fallback de accesibilidad");
            return false;
        } catch (Throwable t) {
            settled.set(true);
            handler.removeCallbacks(watchdog);
            ConnectionLog.add("Shizuku: no se pudo encolar '" + action + "': " + t.getClass().getSimpleName());
            return false;
        }
    }

    private String executeInputAction(ICostPhoneShell target, String action, Object[] args)
            throws Exception {
        switch (action) {
            case "input_tap": {
                if (args == null || args.length < 3) return "error: input_tap requires 3 args";
                int x = (int) args[0], y = (int) args[1];
                long duration = (long) args[2];
                if (x < 0 || y < 0 || duration < 0 || duration > 8000)
                    return "error: input_tap arguments out of range";
                return target.inputTap(x, y, duration);
            }
            case "input_swipe": {
                if (args == null || args.length < 5) return "error: input_swipe requires 5 args";
                int x1 = (int) args[0], y1 = (int) args[1];
                int x2 = (int) args[2], y2 = (int) args[3];
                long duration = (long) args[4];
                if (x1 < 0 || y1 < 0 || x2 < 0 || y2 < 0 || duration < 0 || duration > 8000)
                    return "error: input_swipe arguments out of range";
                return target.inputSwipe(x1, y1, x2, y2, duration);
            }
            case "input_keyevent":
                if (args == null || args.length < 1) return "error: input_keyevent requires 1 arg";
                return target.inputKeyevent((int) args[0]);
            default: return "error: acción no permitida por el despachador asíncrono";
        }
    }

    private static String shortCmd(String cmd) {
        if (cmd == null) return "";
        return cmd.length() > 40 ? cmd.substring(0, 37) + "…" : cmd;
    }

    private void ensureBound() {
        if (shell != null) return;
        if (state() != READY) { binding = false; return; }
        long now = android.os.SystemClock.elapsedRealtime();
        if (binding && now - bindStartedAt < BIND_RETRY_MS) return;
        try {
            binding = true;
            bindStartedAt = now;
            ConnectionLog.add("Shizuku: enlazando UserService");
            Shizuku.bindUserService(args, conn);
        } catch (Throwable t) {
            binding = false;
            ConnectionLog.add("Shizuku: bindUserService falló: " + t.getClass().getSimpleName());
            Log.w(TAG, "bindUserService falló: " + t);
            notifyStateChanged(state());
        }
    }

    /** Suelta el UserService. Lo llama el servicio de accesibilidad al morir. */
    static void release() {
        ShizukuManager m;
        synchronized (ShizukuManager.class) {
            m = sInstance;
            sInstance = null;
        }
        if (m == null) return;
        try {
            Shizuku.removeBinderReceivedListener(m.receivedListener);
            Shizuku.removeBinderDeadListener(m.deadListener);
        } catch (Throwable ignored) { }
        try {
            if (m.shell != null) Shizuku.unbindUserService(m.args, m.conn, true);
        } catch (Throwable ignored) { }
        m.shell = null;
        m.worker.shutdown();
    }
}
