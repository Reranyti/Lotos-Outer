package com.lotusblight.overlay;

import java.io.File;

/** Paths of the Windows tools the overlay starts: the real ones in System32, never whatever happens to share their name in the working directory. */
final class WinTools {
    private WinTools() {}

    static String powershell() {
        return system32("WindowsPowerShell\\v1.0\\powershell.exe", "powershell");
    }

    static String reg() {
        return system32("reg.exe", "reg");
    }

    private static String system32(String relative, String fallback) {
        String root = System.getenv("SystemRoot");
        if (root != null) {
            File f = new File(root + "\\System32\\" + relative);
            if (f.isFile()) return f.getPath();
        }
        return fallback;
    }
}
