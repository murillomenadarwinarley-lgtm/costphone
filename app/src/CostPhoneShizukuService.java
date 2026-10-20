package com.accessdroid.termux;

import android.os.RemoteException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Shizuku UserService.
 *
 * The service only exposes diagnostics and a constrained virtual-display
 * operation. It never accepts or runs arbitrary shell commands from the UI/AI.
 */
public final class CostPhoneShizukuService extends ICostPhoneShizukuService.Stub {

    public CostPhoneShizukuService() {
        super();
    }

    @Override
    public String getDiagnostics() throws RemoteException {
        int uid = android.os.Process.myUid();
        StringBuilder result = new StringBuilder();
        result.append("UID=").append(uid);
        result.append("\nModo=").append(uid == 0 ? "root" : (uid == 2000 ? "shell/ADB" : "otro UID"));
        result.append("\nAndroid=").append(readOnlyCommand("/system/bin/getprop", "ro.build.version.release"));
        String overlay = readOnlyCommand("/system/bin/settings", "get", "global", "overlay_display_devices");
        result.append("\nPantalla virtual=").append(
                overlay == null || overlay.isEmpty() || "null".equalsIgnoreCase(overlay)
                        ? "no configurada" : overlay);
        return result.toString();
    }

    @Override
    public String createVirtualDisplay(int width, int height, int density) throws RemoteException {
        if (width < 100 || width > 4096 || height < 100 || height > 4096 ||
                density < 120 || density > 640) {
            return "ERROR: rango válido: 100–4096 px y 120–640 dpi.";
        }

        // own_content_only is mandatory: the virtual display must not mirror display 0.
        String config = width + "x" + height + "/" + density + ",own_content_only";
        String current = readOnlyCommand("/system/bin/settings", "get", "global", "overlay_display_devices");
        List<String> items = splitOverlay(current);
        if (items.contains(config)) {
            return "EXISTS: La configuración ya existe; no se cambió ningún ajuste.";
        }
        items.add(config);
        String result = writeOverlay(join(items));
        if (result == null) {
            return "CREATED: " + config;
        }
        return "ERROR: No se pudo activar la pantalla virtual: " + result;
    }

    @Override
    public String removeVirtualDisplay(int width, int height, int density) throws RemoteException {
        if (width < 100 || width > 4096 || height < 100 || height > 4096 ||
                density < 120 || density > 640) {
            return "ERROR: dimensiones no válidas.";
        }

        String config = width + "x" + height + "/" + density + ",own_content_only";
        List<String> items = splitOverlay(readOnlyCommand(
                "/system/bin/settings", "get", "global", "overlay_display_devices"));
        if (!items.remove(config)) {
            return "MISSING: No se encontró la configuración exacta de CostPhone; no se tocó el resto.";
        }

        String result = items.isEmpty() ? deleteOverlay() : writeOverlay(join(items));
        if (result == null) {
            return "REMOVED: Pantalla virtual de CostPhone desactivada.";
        }
        return "ERROR: No se pudo quitar la pantalla virtual: " + result;
    }

    private List<String> splitOverlay(String raw) {
        List<String> items = new ArrayList<>();
        if (raw == null || raw.isEmpty() || "null".equalsIgnoreCase(raw)) {
            return items;
        }
        for (String item : raw.split(";")) {
            String value = item.trim();
            if (!value.isEmpty() && !items.contains(value)) items.add(value);
        }
        return items;
    }

    private String join(List<String> items) {
        StringBuilder result = new StringBuilder();
        for (String item : items) {
            if (result.length() > 0) result.append(';');
            result.append(item);
        }
        return result.toString();
    }

    /** Returns null on success, otherwise a bounded error string. */
    private String writeOverlay(String value) {
        String output = readOnlyCommand("/system/bin/settings", "put", "global",
                "overlay_display_devices", value);
        // settings put normally prints nothing. Verify the exact value after writing.
        String check = readOnlyCommand("/system/bin/settings", "get", "global", "overlay_display_devices");
        return value.equals(check) ? null : (output == null ? "la verificación no coincide" : output);
    }

    /** Returns null on success, otherwise an error string. */
    private String deleteOverlay() {
        readOnlyCommand("/system/bin/settings", "delete", "global", "overlay_display_devices");
        String check = readOnlyCommand("/system/bin/settings", "get", "global", "overlay_display_devices");
        return (check == null || check.isEmpty() || "null".equalsIgnoreCase(check))
                ? null : "el ajuste continúa configurado";
    }

    private String readOnlyCommand(String... command) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            java.lang.Process process = builder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() > 0) output.append(' ');
                    output.append(line);
                    if (output.length() > 512) {
                        output.setLength(512);
                        break;
                    }
                }
            }
            int exitCode = process.waitFor();
            String value = output.toString().trim();
            if (exitCode != 0) return value.isEmpty() ? "error del sistema" : value;
            return value;
        } catch (IOException e) {
            return "comando del sistema no disponible";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "comando interrumpido";
        }
    }

    /** Called by Shizuku when the remote user-service process is being removed. */
    public void destroy() {
        System.exit(0);
    }
}
