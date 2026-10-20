package com.accessdroid.termux;

/** Read-only diagnostics and a whitelisted virtual-display setting operation. */
interface ICostPhoneShizukuService {
    String getDiagnostics();
    String createVirtualDisplay(int width, int height, int density);
    String removeVirtualDisplay(int width, int height, int density);
}
