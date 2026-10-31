package com.example.desktop;

/**
 * Plain Java entry point used by jpackage.
 * Keeping the packaged main class separate from the JavaFX Application subclass
 * avoids Java launcher JavaFX-module detection issues in non-modular builds.
 */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        try {
            RemoteDesktopApp.main(args);
        } catch (Throwable t) {
            t.printStackTrace();
            try {
                java.nio.file.Path log = java.nio.file.Path.of(System.getProperty("user.home"), "RemoteJavaFXClient-launch-error.log");
                try (java.io.PrintWriter out = new java.io.PrintWriter(java.nio.file.Files.newBufferedWriter(log))) {
                    t.printStackTrace(out);
                }
            } catch (Throwable ignored) {
                // Best-effort diagnostic logging only.
            }
            throw t;
        }
    }
}
