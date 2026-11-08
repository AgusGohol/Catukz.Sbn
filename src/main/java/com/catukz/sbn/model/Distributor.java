package com.catukz.sbn.model;

public enum Distributor {
    DISTROKID("DistroKid"),
    AMUSE("Amuse"),
    SOUNDON("SoundOn"),
    OFFSTEP("OffStep"),
    OTHER("Lainnya");

    private final String label;

    Distributor(String label) { this.label = label; }

    public String label() { return label; }

    public static Distributor fromLabel(String l) {
        for (Distributor d : values()) if (d.label.equalsIgnoreCase(l)) return d;
        return OTHER;
    }
}
