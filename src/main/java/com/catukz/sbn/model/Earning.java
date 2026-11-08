package com.catukz.sbn.model;

import java.time.LocalDate;

public class Earning {
    public long id;
    public String distributor;
    public LocalDate date;
    public String track;
    public String platform;
    public String country;
    public long streams;
    public double amount;
    public String currency = "USD";

    public Earning() {}
}
