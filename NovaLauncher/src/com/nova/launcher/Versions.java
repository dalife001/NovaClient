package com.nova.launcher;

final class Versions {
    private Versions() {}

    /** Compares dotted versions like 0.3.0 and 1.10.2 numerically. A suffix such as "-mc1.21.4" or "+build.8" is ignored. */
    static int cmp(String a, String b) {
        int[] x = parts(a), y = parts(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0, q = i < y.length ? y[i] : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static int[] parts(String v) {
        if (v == null) return new int[0];
        String[] t = v.replaceFirst("^[^0-9]*", "").replaceFirst("[+\\-].*$", "").split("[^0-9]+");
        int[] r = new int[t.length];
        for (int i = 0; i < t.length; i++) {
            try { r[i] = t[i].isEmpty() ? 0 : Integer.parseInt(t[i]); } catch (NumberFormatException e) { r[i] = 0; }
        }
        return r;
    }
}
