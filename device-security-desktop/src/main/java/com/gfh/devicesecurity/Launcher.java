package com.gfh.devicesecurity;

/**
 * Separate entry point for classpath-based Windows packaging.
 * Never performs disk modifications.
 */
public final class Launcher {
    private Launcher() { }
    public static void main(String[] args) {
        DeviceSecurityDesktop.main(args);
    }
}
