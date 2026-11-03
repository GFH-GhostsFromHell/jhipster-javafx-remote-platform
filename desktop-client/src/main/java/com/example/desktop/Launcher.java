package com.example.desktop;

import java.nio.file.Path;

/**
 * Plain Java entry point used by jpackage.
 * Keeping the packaged main class separate from the JavaFX Application subclass
 * avoids Java launcher JavaFX-module detection issues in non-modular builds.
 */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        // Preview builds deliberately skip the normal updater so they can be installed
        // beside the stable Electrical Workbench without ever replacing it.
        if (!Boolean.getBoolean("app.previewBuild")) startUpdateMonitor();
        try {
            ElectricalWorkbenchNativeApp.main(args);
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

    private static void startUpdateMonitor() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;

        Thread checker = new Thread(() -> {
            String lastPromptedVersion = null;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    UpdateService updater = new UpdateService();
                    UpdateService.UpdateInfo info = updater.check();
                    if (info.updateAvailable()
                        && (lastPromptedVersion == null || !lastPromptedVersion.equals(info.latestVersion()))) {
                        lastPromptedVersion = info.latestVersion();
                        if (confirmWindowsUpdate(info.latestVersion())) {
                            Path installer = updater.downloadAndVerify(info);
                            updater.launchInstallerAndRelaunch(installer);
                            System.exit(0);
                            return;
                        }
                    }
                } catch (Throwable ex) {
                    logUpdateFailure(ex);
                }

                try {
                    Thread.sleep(60_000L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "electrical-workbench-update-monitor");
        checker.setDaemon(true);
        checker.start();
    }

    private static boolean confirmWindowsUpdate(String latestVersion) {
        String safeVersion = latestVersion == null ? "newer" : latestVersion.replace("'", "");
        String command = "Add-Type -AssemblyName PresentationFramework; " +
            "$r=[System.Windows.MessageBox]::Show(" +
            "'Electrical Workbench v" + safeVersion + " is available. Download and install it now?'," +
            "'Electrical Workbench Update','YesNo','Information'); " +
            "if($r -eq 'Yes'){exit 0}else{exit 1}";
        try {
            Process process = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command
            ).start();
            return process.waitFor() == 0;
        } catch (Exception ex) {
            logUpdateFailure(ex);
            return false;
        }
    }

    private static void logUpdateFailure(Throwable error) {
        try {
            Path log = Path.of(System.getProperty("user.home"), "RemoteJavaFXClient-update.log");
            String detail = java.time.Instant.now() + "  " + error.getClass().getSimpleName() + ": " +
                (error.getMessage() == null ? error.toString() : error.getMessage()) + System.lineSeparator();
            java.nio.file.Files.writeString(
                log,
                detail,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND
            );
        } catch (Throwable ignored) {
            // Update logging must never block application startup.
        }
    }
}
