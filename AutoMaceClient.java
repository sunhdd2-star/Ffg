package com.example.automace;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Auto Mace (client-side, no keybind needed -> works on touch/mobile).
 * While falling (fallDistance > MIN_FALL) and aiming at a living entity,
 * switches to a Mace in the hotbar and attacks once.
 * Note: many servers ban this kind of automation. Use in singleplayer / allowed servers only.
 */
public class AutoMaceClient implements ClientModInitializer {
    private static final double MIN_FALL = 1.5;   // mace smash needs a fall
    private int previousSlot = -1;
    private int cooldownTicks = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.interactionManager == null || client.currentScreen != null) return;
        if (cooldownTicks > 0) cooldownTicks--;

        boolean falling = !player.isOnGround() && !player.isClimbing() && !player.isTouchingWater()
                && player.fallDistance > MIN_FALL;

        if (!falling) {
            // restore previous item after landing
            if (previousSlot != -1 && player.isOnGround()) {
                player.getInventory().setSelectedSlot(previousSlot);
                previousSlot = -1;
            }
            return;
        }

        if (!(client.crosshairTarget instanceof EntityHitResult hit)
                || client.crosshairTarget.getType() != HitResult.Type.ENTITY) return;
        Entity target = hit.getEntity();
        if (!(target instanceof LivingEntity) || !target.isAlive()) return;
        if (player.squaredDistanceTo(target) > 9.0) return; // ~3 blocks reach

        int maceSlot = findMace(player);
        if (maceSlot == -1) return;

        if (player.getInventory().getSelectedSlot() != maceSlot) {
            if (previousSlot == -1) previousSlot = player.getInventory().getSelectedSlot();
            player.getInventory().setSelectedSlot(maceSlot);
            return; // attack next tick so the server registers the held item
        }

        if (cooldownTicks == 0 && player.getAttackCooldownProgress(0.0f) >= 0.9f) {
            client.interactionManager.attackEntity(player, target);
            player.swingHand(Hand.MAIN_HAND);
            cooldownTicks = 10;
        }
    }

    private int findMace(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).isOf(Items.MACE)) return i;
        }
        return -1;
    }
}
