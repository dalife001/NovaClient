package com.nova.client.module;

import com.nova.client.compat.Mc;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;

/** Clicks per second (left | right). Reads the mouse every frame, so fast clicking is counted. */
public class CpsModule extends SimpleHudModule {
    private final Deque<Long> left = new ArrayDeque<>();
    private final Deque<Long> right = new ArrayDeque<>();
    private boolean prevLeft, prevRight;

    public CpsModule() { super("CPS", "Clicks per second counter", true, 6, 46, false, false); }

    private void poll(Minecraft mc) {
        long now = System.currentTimeMillis();
        boolean l = mc.mouseHandler.isLeftPressed();
        boolean r = mc.mouseHandler.isRightPressed();
        if (Mc.screen(mc) == null) {
            if (l && !prevLeft) left.addLast(now);
            if (r && !prevRight) right.addLast(now);
        }
        prevLeft = l;
        prevRight = r;
        while (!left.isEmpty() && now - left.peekFirst() > 1000) left.pollFirst();
        while (!right.isEmpty() && now - right.peekFirst() > 1000) right.pollFirst();
    }

    @Override protected String label() { return "CPS"; }
    @Override protected String value(Minecraft mc) {
        poll(mc);
        return left.size() + " | " + right.size();
    }
}
