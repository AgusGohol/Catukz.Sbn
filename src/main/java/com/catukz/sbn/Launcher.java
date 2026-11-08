package com.catukz.sbn;

/**
 * Entry point untuk fat-jar / distribusi.
 * Tidak meng-extend Application supaya JavaFX bisa jalan dari jar biasa.
 */
public class Launcher {
    public static void main(String[] args) {
        App.main(args);
    }
}
