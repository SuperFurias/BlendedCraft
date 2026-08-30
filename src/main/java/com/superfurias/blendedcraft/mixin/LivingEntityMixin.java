package com.superfurias.blendedcraft.mixin;

import com.superfurias.blendedcraft.util.MaterialEffects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;
import java.util.function.BiConsumer;

/**
 * Applies blended material effects:
 * - Slime boots: cancel fall damage + bounce (unless sneaking)
 * - Feather trait armor: reduced fall damage (Feather Falling tier)
 * - Ice boots: immune to freeze damage (powder snow / freezing)
 * - Cactus armor: Thorns - attackers take damage = trait tier
 * - Fiery sword: Fire Aspect - ignites the target for 4s x tier
 * - Slime sword: Knockback - extra knockback = 0.4 x tier on hit
 * - Lapis/Emerald sword: Looting - mob loot-table drops are multiplied by the trait tier
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    /** Re-entrancy guard: skips trait application for damage dealt BY trait effects (sharpness bonus, thorns). */
    private static final ThreadLocal<Boolean> BLENDED_TRAIT_DAMAGE = ThreadLocal.withInitial(() -> false);

    // ---------------- fall damage: slime boots + feather fall ----------------
    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void blendedcraft$fallTraits(double fallDistance, float damageMultiplier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        try {
            LivingEntity self = (LivingEntity) (Object) this;
            // slime boots: no fall damage (the bounce is vanilla BOUNCINESS physics
            // gated by BouncinessMixin via isSuppressingBounce)
            if (MaterialEffects.hasSlimeBoots(self)) {
                cir.setReturnValue(false);
                return;
            }
            // feather boots: reduced fall damage (Feather Falling style, boots only)
            int feather = MaterialEffects.effectLevel(self.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET), MaterialEffects.T_FEATHER_FEATHERFALL);
            if (feather > 0) {
                double damage = (fallDistance - 3.0) * damageMultiplier;
                double reduced = damage * Math.max(0.25, 1.0 - 0.25 * feather);
                cir.setReturnValue(false);
                if (reduced > 0 && self.level() instanceof ServerLevel server) {
                    self.hurtServer(server, self.damageSources().fall(), (float) reduced);
                }
            }
        } catch (Exception ignored) {}
    }

    // ---------------- freeze immunity for ice boots ----------------
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void blendedcraft$freezeImmunity(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!source.is(DamageTypes.FREEZE)) return;
            LivingEntity self = (LivingEntity) (Object) this;
            if (MaterialEffects.effectLevel(self.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET), MaterialEffects.T_ICE_FROSTWALKER) > 0) {
                cir.setReturnValue(false);
            }
        } catch (Exception ignored) {}
    }

    // ---------------- quartz sword: sharpness bonus folded into the incoming hit ----------------
    /**
     * Sharpness works like vanilla: it must be part of the SAME hurt event, not a
     * second hurtServer call (which fights invulnerability frames and splits the
     * damage into two events). We add the bonus to the incoming amount at HEAD so
     * armor, absorption, shields, death handling and knockback all see one hit.
     * Applies only to direct melee attacks by the weapon's holder.
     */
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float blendedcraft$sharpnessBonus(float amount, ServerLevel level, DamageSource source) {
        try {
            if (BLENDED_TRAIT_DAMAGE.get()) return amount;
            if (!(source.getEntity() instanceof LivingEntity attacker)) return amount;
            if (source.getDirectEntity() != attacker) return amount; // melee only
            if (source.is(DamageTypes.THORNS)) return amount;
            int sharp = MaterialEffects.effectLevel(attacker.getMainHandItem(), MaterialEffects.T_QUARTZ_SHARPNESS);
            if (sharp <= 0) return amount;
            return amount + (0.5f * sharp + 0.5f);
        } catch (Exception e) {
            return amount;
        }
    }

    // ---------------- thorns / fire aspect / knockback / record looting on successful hurt ----------------
    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void blendedcraft$hurtTraits(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!cir.getReturnValueZ()) return;
            // damage from our own trait effects must not re-trigger traits (thorns loop)
            if (BLENDED_TRAIT_DAMAGE.get()) return;
            LivingEntity victim = (LivingEntity) (Object) this;
            // no thorns retaliation from thorns damage (prevents infinite loop)
            if (source.is(DamageTypes.THORNS)) return;

            LivingEntity attacker = source.getEntity() instanceof LivingEntity living ? living : null;
            ItemStack attackerWeapon = attacker != null ? attacker.getMainHandItem() : ItemStack.EMPTY;

            // cactus armor: thorns
            int thorns = 0;
            for (var slot : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
                thorns = Math.max(thorns, MaterialEffects.effectLevel(victim.getItemBySlot(slot), MaterialEffects.T_CACTUS_ARMOR));
            }
            if (thorns > 0 && attacker != null && attacker != victim) {
                BLENDED_TRAIT_DAMAGE.set(true);
                try {
                    attacker.hurtServer(level, level.damageSources().thorns(victim), thorns);
                } finally {
                    BLENDED_TRAIT_DAMAGE.set(false);
                }
            }

            if (attacker != null) {
                // fiery sword: fire aspect
                int fire = MaterialEffects.effectLevel(attackerWeapon, MaterialEffects.T_FIERY_FIREASPECT);
                if (fire > 0) {
                    victim.igniteForTicks(80 * fire);
                }
                // slime sword: knockback (vanilla-style, uses vanilla knockback() for correct physics)
                int kb = MaterialEffects.effectLevel(attackerWeapon, MaterialEffects.T_SLIME_SWORD);
                if (kb > 0) {
                    victim.knockback(0.2 * kb + 0.2, victim.getX() - attacker.getX(), victim.getZ() - attacker.getZ(), source, 1.0f);
                }
                // lapis/emerald sword: record looting tier for the drop multiplier below
                int looting = Math.max(MaterialEffects.effectLevel(attackerWeapon, MaterialEffects.T_LAPIS_LOOTING), MaterialEffects.effectLevel(attackerWeapon, MaterialEffects.T_EMERALD_LOOT));
                if (looting > 0) {
                    MaterialEffects.recordLootingHit(victim.getUUID(), looting);
                }
            }
        } catch (Exception ignored) {}
    }

    // ---------------- looting: multiply mob loot-table drops ----------------
    @Inject(method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/resources/ResourceKey;Ljava/util/function/Function;Ljava/util/function/BiConsumer;)Z", at = @At("HEAD"), cancellable = true)
    private void blendedcraft$lootingDrops(ServerLevel level, ResourceKey<LootTable> lootTable, Function<LootParams.Builder, LootParams> paramsFactory, BiConsumer<ServerLevel, ItemStack> drops, CallbackInfoReturnable<Boolean> cir) {
        try {
            LivingEntity self = (LivingEntity) (Object) this;
            int looting = MaterialEffects.takeLootingTier(self.getUUID());
            if (looting <= 1) return;

            LootParams params = paramsFactory.apply(new LootParams.Builder(level));
            LootTable table = level.getServer().reloadableRegistries().getLootTable(lootTable);
            var items = table.getRandomItems(params);
            for (ItemStack stack : items) {
                if (!stack.isEmpty() && stack.isStackable()) {
                    stack.setCount(Math.min(stack.getMaxStackSize(), stack.getCount() * looting));
                }
                drops.accept(level, stack);
            }
            cir.setReturnValue(!items.isEmpty());
        } catch (Exception e) {
            // on any failure fall back to vanilla (do not cancel)
        }
    }
}
