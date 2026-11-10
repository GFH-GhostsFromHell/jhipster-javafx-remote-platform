package com.gfh.industrial.hmi;

/** Plain launcher avoids JavaFX Application classpath detection issues in MSI launchers. */
public final class HmiLauncher {
    private HmiLauncher() {}
    public static void main(String[] args) {
        IndustrialHmiApp.main(args);
    }
}
