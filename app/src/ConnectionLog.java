package com.accessdroid.termux;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Registro de eventos de conexión, pensado para depurar con la app abierta.
 *
 * Dos capas: una cola circular en memoria (para el panel, que se lee al
 * instante) y un fichero en el directorio externo de la app (que sobrevive al
 * reinicio del proceso y se puede leer con adb o desde Termux).
 *
 * Sin estado global raro: la cola es estática y el fichero se elige una vez con
 * {@link #init(android.content.Context)}. Si no se inicializa, todo cae a
 * memoria y el panel sigue funcionando.
 */
public final class ConnectionLog {

    /** Líneas que se muestran en el panel. */
    private static final int PANEL_LINES = 24;
    /** Tope del fichero: al llegar, se descarta la mitad más vieja. */
    private static final int FILE_LINES = 400;

    private static final List<String> LINES = new ArrayList<>();
    private static volatile File file;
    // No bloquear UI/Accesibilidad al persistir eventos.
    // Acota la cola de escritura para que una ráfaga de comandos no consuma la RAM.
    // En saturación se priorizan las entradas recientes.
    private static final ThreadPoolExecutor DISK = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(256),
            new ThreadPoolExecutor.DiscardOldestPolicy());
    private static final long MAX_FILE_BYTES = 96L * 1024L;

    private ConnectionLog() { }

    /** Fija el fichero de registro. Se puede llamar varias veces, sin efecto. */
    public static synchronized void init(android.content.Context ctx) {
        if (file != null) return;
        try {
            File dir = ctx.getExternalFilesDir(null);
            if (dir == null) return;
            file = new File(dir, "connection.log");
            // Semilla la memoria con lo que ya había, para que el panel no empiece
            // vacío tras un reinicio del proceso.
            try {
                BufferedReader r = new BufferedReader(new FileReader(file));
                String l;
                while ((l = r.readLine()) != null) {
                    if (!l.isEmpty()) LINES.add(l);
                }
                r.close();
            } catch (Exception ignored) { }
            if (LINES.size() > FILE_LINES) trim();
        } catch (Throwable ignored) {
            file = null;
        }
    }

    /** Añade el evento a memoria; escribe al disco en segundo plano. */
    public static void add(String msg) {
        final String line;
        final File target;
        synchronized (ConnectionLog.class) {
            String stamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            line = stamp + "  " + msg;
            LINES.add(line);
            if (LINES.size() > FILE_LINES) trim();
            target = file;
        }
        if (target == null) return;
        try {
            DISK.execute(new Runnable() {
                @Override public void run() {
                    try (FileWriter w = new FileWriter(target, true)) {
                        w.write(line);
                        w.write("\n");
                    } catch (Exception ignored) { }
                    if (target.length() > MAX_FILE_BYTES) compactFile(target);
                }
            });
        } catch (Throwable ignored) { }
    }

    /** Compacta el archivo solo al superar 96 KiB y fuera del hilo llamador. */
    private static void compactFile(File target) {
        final List<String> snapshot;
        synchronized (ConnectionLog.class) {
            if (file != target) return;
            int from = Math.max(0, LINES.size() - FILE_LINES);
            snapshot = new ArrayList<>(LINES.subList(from, LINES.size()));
        }
        try (FileWriter w = new FileWriter(target, false)) {
            for (String line : snapshot) {
                w.write(line);
                w.write("\n");
            }
        } catch (Exception ignored) { }
    }

    /** Últimas líneas, de más vieja a más nueva. */
    public static synchronized List<String> dump() {
        int from = Math.max(0, LINES.size() - PANEL_LINES);
        return new ArrayList<>(LINES.subList(from, LINES.size()));
    }

    /** Ruta del fichero, o null si no hay fichero. */
    public static synchronized String path() {
        return file == null ? null : file.getAbsolutePath();
    }

    /** Todas las líneas retenidas, para exportación de diagnóstico. */
    public static synchronized List<String> dumpAll() {
        int from = Math.max(0, LINES.size() - FILE_LINES);
        return new ArrayList<>(LINES.subList(from, LINES.size()));
    }

    /** Vacía el registro y serializa el borrado tras cualquier escritura pendiente. */
    public static void clear() {
        final File target;
        synchronized (ConnectionLog.class) {
            LINES.clear();
            target = file;
        }
        if (target != null) {
            try {
                DISK.execute(new Runnable() {
                    @Override public void run() {
                        try { if (target.exists()) target.delete(); } catch (Throwable ignored) { }
                    }
                });
            } catch (Throwable ignored) { }
        }
    }

    private static void trim() {
        int cut = LINES.size() - FILE_LINES / 2;
        for (int i = 0; i < cut; i++) LINES.remove(0);
    }
}
