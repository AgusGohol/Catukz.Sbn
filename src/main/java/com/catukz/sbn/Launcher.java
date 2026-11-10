package com.catukz.sbn;

/**
 * Entry point untuk fat-jar / distribusi.
 * Tidak meng-extend Application supaya JavaFX bisa jalan dari jar biasa.
 */
public class Launcher {
    public static void main(String[] args) {
        // Perbaiki bug WebView "Unable to establish loopback connection" (HTTP/2 loader)
        System.setProperty("com.sun.webkit.useHTTP2Loader", "false");
        App.main(args);
    }
}
