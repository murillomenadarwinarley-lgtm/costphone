package com.accessdroid.termux;

import android.content.Context;
import android.os.Process;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CrashReporter {
    private static final String TAG = "CostPhoneCrash";
    private static final String FILE_NAME = "last-crash.txt";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private CrashReporter() { }

    public static void install(Context context) {
        if (context == null || !INSTALLED.compareAndSet(false, true)) return;
        final Context app = context.getApplicationContext();
        final File target = new File(app.getFilesDir(), FILE_NAME);
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread thread, Throwable error) {
                try {
                    String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
                    StringWriter buffer = new StringWriter();
                    PrintWriter writer = new PrintWriter(buffer);
                    writer.println("CostPhone fatal crash");
                    writer.println("time=" + stamp);
                    writer.println("thread=" + (thread == null ? "unknown" : thread.getName()));
                    if (error != null) error.printStackTrace(writer);
                    writer.flush();
                    byte[] bytes = buffer.toString().getBytes(StandardCharsets.UTF_8);
                    try (java.io.FileOutputStream out = new java.io.FileOutputStream(target, false)) {
                        out.write(bytes, 0, Math.min(bytes.length, 65536));
                        out.flush();
                        out.getFD().sync();
                    }
                    Log.e(TAG, "Fatal crash captured", error);
                } catch (Throwable ignored) { }
                if (previous != null && previous != this) previous.uncaughtException(thread, error);
                else {
                    Process.killProcess(Process.myPid());
                    System.exit(10);
                }
            }
        });
    }

    public static String readLastCrash(Context context) {
        if (context == null) return "";
        File file = new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
        if (!file.isFile()) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && out.length() < 24000) out.append(line).append('\n');
        } catch (Throwable t) {
            return "No se pudo leer last-crash.txt: " + t.getClass().getSimpleName();
        }
        return out.toString();
    }
}
