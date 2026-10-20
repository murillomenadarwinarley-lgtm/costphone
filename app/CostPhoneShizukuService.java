package com.accessdroid.termux;

import android.os.Process;
import android.os.RemoteException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Minimal, read-only Shizuku UserService.
 *
 * This deliberately exposes diagnostics only; it does not accept arbitrary
 * shell commands from the UI or the model.
 */
public final class CostPhoneShizukuService extends ICostPhoneShizukuService.Stub {

    public CostPhoneShizukuService() {
        super();
    }

    @Override
    public String getDiagnostics() throws RemoteException {
        StringBuilder result = new StringBuilder();
        int uid = Process.myUid();
        result.append("UID=").append(uid);
        result.append("\nModo=").append(uid == 0 ? "root" : (uid == 2000 ? "shell/ADB" : "otro UID"));
        result.append("\nAndroid=").append(readOnlyCommand("/system/bin/getprop", "ro.build.version.release"));
        result.append("\nPantalla virtual=").append(readOnlyCommand(
                "/system/bin/settings", "get", "global", "overlay_display_devices"));
        return result.toString();
    }

    private String readOnlyCommand(String... command) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() > 0) output.append(' ');
                    output.append(line);
                    if (output.length() > 240) {
                        output.setLength(240);
                        break;
                    }
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) return "no disponible";
            String value = output.toString().trim();
            return value.isEmpty() || "null".equalsIgnoreCase(value) ? "no configurada" : value;
        } catch (IOException e) {
            return "no disponible";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrumpido";
        }
    }

    /** Called by Shizuku when the remote user-service process is being removed. */
    public void destroy() {
        System.exit(0);
    }
}
