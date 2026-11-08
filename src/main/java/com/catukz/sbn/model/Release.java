package com.catukz.sbn.model;

import java.time.LocalDate;

public class Release {
    public long id;
    public String distributor;
    public String title;
    public String artist;
    public String status; // Live, Pending, Rejected
    public LocalDate releaseDate;
    public long streams;
    public double earnings;

    public Release() {}
}
