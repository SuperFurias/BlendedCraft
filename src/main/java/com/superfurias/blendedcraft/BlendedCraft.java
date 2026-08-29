package com.superfurias.blendedcraft;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.superfurias.blendedcraft.item.ModItems;

public class BlendedCraft implements ModInitializer {
	public static final String MOD_ID = "blendedcraft";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModItems.initialize();

		// Backward compatibility: upgrade old blended items on player login (once, low overhead)
		try {
			net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
				var player = handler.getPlayer();
				server.execute(() -> {
					try {
						int upgraded = com.superfurias.blendedcraft.util.BlendedLegacyUpgrader.upgradeInventory(player);
						if (upgraded > 0) {
							player.containerMenu.broadcastChanges();
							LOGGER.info("Auto-upgraded {} old blended item(s) for {}", upgraded, player.getName().getString());
						}
					} catch (Exception e) {
						LOGGER.warn("Legacy upgrade failed for {}: {}", player.getName().getString(), e.toString());
					}
				});
			});
			LOGGER.info("Registered blended legacy upgrader on player login (lightweight, scan-on-join only)");
		} catch (Exception e) {
			LOGGER.warn("Failed to register legacy upgrader: {}", e.toString());
		}

		LOGGER.info("BlendedCraft initialized: blended tools and armor ready.");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
