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
        // Account creation, verification and recovery are now integrated into the
        // JavaFX login screen. Do not invoke JavaFX UI before Application.launch(),
        // because the JavaFX toolkit is not initialized yet at this point.
        startPreLoginUpdateCheck();
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

    private static void startPreLoginUpdateCheck() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;

        Thread checker = new Thread(() -> {
            try {
                UpdateService updater = new UpdateService();
                UpdateService.UpdateInfo info = updater.check();
                if (!info.updateAvailable()) return;
                if (!confirmWindowsUpdate(info.latestVersion())) return;
                Path installer = updater.downloadAndVerify(info);
                updater.launchInstallerAndRelaunch(installer);
                System.exit(0);
            } catch (Throwable ex) {
                System.err.println("Pre-login update check failed: " + ex.getMessage());
            }
        }, "electrical-workbench-pre-login-update");
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
            return false;
        }
    }
}
