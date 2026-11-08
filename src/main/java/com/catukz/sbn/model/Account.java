package com.catukz.sbn.model;

public class Account {
    public long id;
    public String distributor;
    public String label;
    public String email;
    public String lastSync; // teks waktu sync terakhir (untuk auto-sync V4)
    // password TIDAK disimpan di sini sebagai teks — disimpan terenkripsi di DB

    public Account() {}

    public Account(String distributor, String email) {
        this.distributor = distributor;
        this.email = email;
    }
}
