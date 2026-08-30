package com.superfurias.blendedcraft.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.superfurias.blendedcraft.util.BlendedLegacyUpgrader;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Unique
    private static final ThreadLocal<Boolean> BLENDED_UPDATING = ThreadLocal.withInitial(() -> false);

    @Inject(method = "<init>(Lnet/minecraft/core/Holder;ILnet/minecraft/core/component/PatchedDataComponentMap;)V", at = @At("RETURN"))
    private void onInitPatched(Holder<Item> holder, int count, PatchedDataComponentMap components, CallbackInfo ci) {
        if (BLENDED_UPDATING.get()) return;
        ItemStack self = (ItemStack) (Object) this;
        try {
            if (self.isEmpty()) return;
            String path;
            try {
                path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(self.getItem()).getPath();
            } catch (Exception e) {
                return;
            }
            if (path != null && path.startsWith("blended_")) {
                BLENDED_UPDATING.set(true);
                try {
                    BlendedLegacyUpgrader.upgradeStack(self);
                } finally {
                    BLENDED_UPDATING.set(false);
                }
            }
        } catch (Exception ignored) {}
    }

    @Inject(method = "set", at = @At("RETURN"))
    private <T> void onSetComponent(net.minecraft.core.component.DataComponentType<T> type, T value, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<T> cir) {
        if (BLENDED_UPDATING.get()) return;
        // Only react to component changes that can affect stats; skip hot paths like DAMAGE (durability),
        // which would otherwise re-run the upgrader on every hit / block break
        if (type != net.minecraft.core.component.DataComponents.CUSTOM_DATA
                && type != net.minecraft.core.component.DataComponents.TOOL
                && type != net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS) return;
        try {
            ItemStack self = (ItemStack) (Object) this;
            if (self.isEmpty()) return;
            String p;
            try {
                p = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(self.getItem()).getPath();
            } catch (Exception e) { return; }
            if (p.startsWith("blended_")) {
                var tool = self.get(DataComponents.TOOL);
                boolean needsFix = false;
                if (tool != null && Math.abs(tool.defaultMiningSpeed() - 1.0f) > 0.01f) needsFix = true;
                else if (type != net.minecraft.core.component.DataComponents.CUSTOM_DATA) {
                    var attrs = self.get(DataComponents.ATTRIBUTE_MODIFIERS);
                    if (attrs != null) {
                        needsFix = true;
                    }
                }
                if (needsFix) {
                    BLENDED_UPDATING.set(true);
                    try { BlendedLegacyUpgrader.upgradeStack(self); } finally { BLENDED_UPDATING.set(false); }
                }
            }
        } catch (Exception ignored) {}
    }
}
