package com.nova.launcher;

interface Progress {
    void status(String message);
    /** done/total, or total <= 0 for "unknown". */
    void bar(int done, int total);

    Progress NONE = new Progress() {
        public void status(String m) {}
        public void bar(int d, int t) {}
    };
}
