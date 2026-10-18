package com.nova.client.module;

/** A slider setting on a module. */
public final class NumberSetting {
    public final String name;
    public final double min, max, step;
    private double value;

    public NumberSetting(String name, double min, double max, double value, double step) {
        this.name = name; this.min = min; this.max = max; this.step = step;
        this.value = value;
    }

    public double get() { return value; }

    public void set(double v) {
        v = Math.max(min, Math.min(max, v));
        if (step > 0) v = Math.round((v - min) / step) * step + min;
        this.value = Math.max(min, Math.min(max, v));
    }

    public double fraction() { return (value - min) / (max - min); }
    public void setFraction(double f) { set(min + Math.max(0, Math.min(1, f)) * (max - min)); }
}
