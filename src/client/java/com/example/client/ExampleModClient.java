package com.example.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.CustomData;
import org.lwjgl.glfw.GLFW;
import com.example.util.FlexibleRecipeHelper;

public class ExampleModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			if (stack.isEmpty()) return;
			if (!FlexibleRecipeHelper.isFlexibleResult(stack)) return;
			CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
			if (cd == null || cd.isEmpty()) return;
			var tag = cd.copyTag();
			boolean isShift = InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
			boolean isCtrl = InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
			if (!isShift && !isCtrl) {
				lines.add(Component.literal("Hold §eShift§r for Stats §7| Hold §bCtrl§r for Materials").withStyle(s -> s.withColor(0xAAAAAA).withItalic(true)));
				return;
			}
			if (isCtrl) {
				lines.add(Component.literal("§6§l» Materials «"));
				ListTag all = tag.getListOrEmpty("blended_ingredients");
				if (!all.isEmpty()) {
					java.util.Map<String, Integer> counts = new java.util.HashMap<>();
					java.util.Map<String, String> display = new java.util.HashMap<>();
					for (int i = 0; i < all.size(); i++) {
						String id = all.getString(i).orElse("unknown");
						String key = id;
						String name = id.contains(":") ? id.split(":")[1] : id;
						name = name.replace("_", " ");
						String cap = "";
						for (String p : name.split(" ")) cap += cap.isEmpty() ? Character.toUpperCase(p.charAt(0)) + p.substring(1) : " " + Character.toUpperCase(p.charAt(0)) + p.substring(1);
						counts.put(key, counts.getOrDefault(key, 0) + 1);
						display.put(key, cap);
					}
					for (var e : counts.entrySet()) {
						String id = e.getKey();
						int cnt = e.getValue();
						int total = all.size();
						int pct = (int)(cnt * 100.0 / total);
						String col = pct >= 50 ? "§a" : pct >= 30 ? "§e" : "§7";
						lines.add(Component.literal(col + " §7- " + cnt + "x " + display.get(id) + " §8[" + pct + "%]"));
					}
				}
				boolean isArmorCtrl = stack.get(DataComponents.EQUIPPABLE) != null;
				ListTag head = tag.getListOrEmpty("blended_head");
				ListTag handle = tag.getListOrEmpty("blended_handle");
				if (isArmorCtrl) {
					// Armor has no handle, just show blended materials
					if (!head.isEmpty()) {
						java.util.Map<String,Integer> hc = new java.util.HashMap<>();
						for (int i=0;i<head.size();i++) hc.put(head.getString(i).orElse(""), hc.getOrDefault(head.getString(i).orElse(""),0)+1);
						lines.add(Component.literal("§aMaterials: §7" + head.size() + "x"));
						for (var e : hc.entrySet()) {
							String n = e.getKey().split(":")[1].replace("_"," ");
							lines.add(Component.literal("  §8• " + e.getValue() + "x " + n));
						}
					}
				} else {
					if (!handle.isEmpty()) {
						String hId = handle.getString(0).orElse("unknown");
						String hName = hId.contains(":") ? hId.split(":")[1].replace("_"," ") : hId;
						lines.add(Component.literal("§eHandle: §7" + handle.size() + "x " + hName + " §8- Swing Speed & Durability"));
					}
					if (!head.isEmpty()) {
						java.util.Map<String,Integer> hc = new java.util.HashMap<>();
						for (int i=0;i<head.size();i++) hc.put(head.getString(i).orElse(""), hc.getOrDefault(head.getString(i).orElse(""),0)+1);
						lines.add(Component.literal("§aHead: §7" + head.size() + "x blended §8- Mining/Attack & Durability"));
						for (var e : hc.entrySet()) {
							String n = e.getKey().split(":")[1].replace("_"," ");
							lines.add(Component.literal("  §8• " + e.getValue() + "x " + n));
						}
					}
				}

			}
			if (isShift) {
				boolean isArmor = stack.get(DataComponents.EQUIPPABLE) != null;
				boolean isTool = stack.get(DataComponents.TOOL) != null || stack.get(DataComponents.WEAPON) != null;
				lines.add(Component.literal(isArmor ? "§d§l» Armor Stats «" : isTool ? "§d§l» Tool Stats «" : "§d§l» Stats «"));
				Integer maxDamage = stack.get(DataComponents.MAX_DAMAGE);
				if (maxDamage != null) {
					int cur = stack.getOrDefault(DataComponents.DAMAGE, 0);
					int dur = maxDamage - cur;
					int pct = (int)(dur * 100.0 / maxDamage);
					String col = pct >= 80 ? "§a" : pct >= 50 ? "§e" : "§c";
					lines.add(Component.literal(col + " Durability: §f" + dur + " / " + maxDamage + " §8[" + pct + "%]"));
				}
                if (isTool) {
                    var tool = stack.get(DataComponents.TOOL);
                    if (tool != null) {
                        float speed = tool.defaultMiningSpeed();
                        try {
                            String p = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
                            boolean isSword = p.contains("sword");
                            if (!isSword) {
                                if (tool.rules().size() >= 2) {
                                    var maybe = tool.rules().get(1).speed();
                                    if (maybe.isPresent() && maybe.get() != Float.MAX_VALUE) speed = maybe.get();
                                    else for (var r : tool.rules()) { var s = r.speed(); if (s.isPresent() && s.get() != Float.MAX_VALUE) { speed = s.get(); break; } }
                                } else for (var r : tool.rules()) { var s = r.speed(); if (s.isPresent() && s.get() != Float.MAX_VALUE) { speed = s.get(); break; } }
                            } else speed = -1;
                        } catch (Exception ignored) {}
                        if (speed >= 0) lines.add(Component.literal("§bMining Speed: §f" + String.format("%.2f", speed) + " §8[Head]"));
                    }
					var attrs = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
					if (attrs != null) {
						for (var e : attrs.modifiers()) {
							String n = e.attribute().value().getDescriptionId();
							double amt = e.modifier().amount();
							String pct = " §8[Head]";
							if (n.contains("attack_damage")) lines.add(Component.literal("§cAttack Damage: §f" + String.format("%.2f", amt) + pct));
							if (n.contains("attack_speed")) {
								// Attack speed is from handle
								lines.add(Component.literal("§cAttack Speed: §f" + String.format("%.2f", amt) + " §8[Handle]"));
							}
						}
					}
				}
				if (isArmor) {
					var attrs = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
					if (attrs != null) {
						for (var e : attrs.modifiers()) {
							String n = e.attribute().value().getDescriptionId();
							double amt = e.modifier().amount();
							String pct = " §8[100%]";
							if (n.contains("armor") && !n.contains("toughness")) lines.add(Component.literal("§9Armor: §f" + String.format("%.1f", amt) + pct));
							if (n.contains("armor_toughness")) lines.add(Component.literal("§9Toughness: §f" + String.format("%.1f", amt) + pct));
							if (n.contains("knockback")) lines.add(Component.literal("§9Knockback Res: §f" + String.format("%.2f", amt) + pct));
						}
					}
				} else if (isTool) {
					// For tools, also show if they have armor? No
				}
				var ench = stack.get(DataComponents.ENCHANTABLE);
				if (ench != null) lines.add(Component.literal("§dEnchantability: §f" + ench.value() + " §8[100%]"));
				if (stack.has(DataComponents.UNBREAKABLE)) lines.add(Component.literal("§5§lUnbreakable §8[Bedrock/Obsidian]"));
			}
			if (isShift && isCtrl) {
				lines.add(Component.literal("§8Hold §eShift§8+§bCtrl§8 to see blended view").withStyle(s -> s.withItalic(true)));
			} else if (isShift) {
				lines.add(Component.literal("§8[Hold §bCtrl§8 for Materials]").withStyle(s -> s.withItalic(true)));
			} else if (isCtrl) {
				lines.add(Component.literal("§8[Hold §eShift§8 for Stats]").withStyle(s -> s.withItalic(true)));
			}
		});
	}
}