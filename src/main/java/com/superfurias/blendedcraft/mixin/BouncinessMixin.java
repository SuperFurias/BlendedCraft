package com.superfurias.blendedcraft.mixin;

import com.superfurias.blendedcraft.util.MaterialEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gates the vanilla BOUNCINESS bounce (slime boots) to real falls only.
 *
 * The bounce itself is vanilla physics driven by the BOUNCINESS attribute on
 * the boots - vanilla runs it identically on client and server, which is why
 * the boing feels correct. Without a gate, though, vanilla also:
 *   - bounces on every micro hop (walking becomes impossible)
 *   - flips X/Z velocity on wall brushes (position-correct "teleports")
 *
 * THE KEY: `isSuppressingBounce()` is the gate vanilla itself checks BEFORE
 * reading the bounciness value (offset 0 of restituteMovementAfterCollisions)
 * and it runs on BOTH client and server with no fall-distance ordering
 * problems - falling blocks never even read the attribute when we say so.
 *
 * Strategy:
 *  - We capture fallDistance at resetFallDistance (which fires exactly when a
 *    landing happens; fallDistance is zeroed right after).
 *  - While the captured fall is below 3 blocks (or no landing occurred), we
 *    make isSuppressingBounce() return true -> vanilla treats bounciness as 0:
 *    no micro-hop bounces, no wall flips.
 *  - At 3+ blocks we return the vanilla value and the full slime physics play
 *    out natively on both sides.
 */
@Mixin(Entity.class)
public abstract class BouncinessMixin {

    @Shadow
    public double fallDistance;

    @Unique
    private static final double BOUNCE_MIN_FALL = 3.0;

    /** Fall height captured at the landing reset (fallDamage ordering workaround). */
    @Unique
    private double blendedcraft$capturedFall = 0.0;

    @Unique
    private boolean blendedcraft$suppressBounce = false;

    @Inject(method = "resetFallDistance", at = @At("HEAD"))
    private void blendedcraft$captureFall(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof LivingEntity living && MaterialEffects.hasSlimeBoots(living)) {
            blendedcraft$capturedFall = this.fallDistance;
            // decide once per landing: below 3 blocks -> suppress until next landing
            blendedcraft$suppressBounce = this.fallDistance < BOUNCE_MIN_FALL;
        }
    }

    @Inject(method = "isSuppressingBounce", at = @At("HEAD"), cancellable = true)
    private void blendedcraft$gateBounce(CallbackInfoReturnable<Boolean> cir) {
        try {
            Entity self = (Entity) (Object) this;
            if (!(self instanceof LivingEntity living)) return;
            if (!MaterialEffects.hasSlimeBoots(living)) return;
            if (self.isShiftKeyDown()) {
                cir.setReturnValue(true);
                return;
            }
            // suppress micro hops / wall brushes; allow real 3+ block landings
            if (blendedcraft$suppressBounce) {
                cir.setReturnValue(true);
            }
        } catch (Exception ignored) {}
    }
}
