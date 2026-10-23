package com.nova.client.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets Fullbright write the raw value, skipping the 0..1 check on the brightness slider. */
@Mixin(OptionInstance.class)
public interface SimpleOptionAccessor<T> {
    @Accessor("value")
    void novaSetRawValue(T value);
}
