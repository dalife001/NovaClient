package com.nova.client.mixin;

//#if MC >= 1900
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets Fullbright write the raw value, skipping the 0..1 check on the brightness slider. */
@Mixin(SimpleOption.class)
public interface SimpleOptionAccessor<T> {
    @Accessor("value")
    void novaSetRawValue(T value);
}
//#endif
