# BlendedCraft

**Author:** SuperFurias  
**Version:** 1.0.0+26.2  
**Minecraft:** 26.2 | **Fabric Loader:** 0.19.3 | **Fabric API:** 0.158.0+26.2 | **Java:** 25

BlendedCraft lets you craft **any tool or armor from any block or item** — fully flexible, fully blended.

### Features

- **Universal Crafting:** Use any block or item as material for any tool or armor. Mix and match freely. Handles and heads are separate: handles must be uniform (e.g. 2x Oak for a pickaxe handle), heads can be blended (e.g. 2x Bedrock +1x Obsidian).
- **Dynamic Stats:** Durability, mining speed, attack damage, armor, toughness and enchantability are blended from your materials. Handles affect swing speed & durability, heads affect mining/attack & durability. Bedrock makes unbreakable.
- **Dynamic Textures:** Every craft gets a unique texture blended from the ingredients and masked to the item's shape — a pickaxe stays a pickaxe, a helmet stays a helmet. Tiny items like buttons are tiled across armor so the whole piece is filled.
- **Equipped Armor:** Armor looks the same equipped as it does in your inventory, using custom blended armor layers.
- **Smart Naming:** Single-material crafts show `Obsidian Pickaxe`, blended show `Blended Bedrock Pickaxe` (dominant >50% or strongest in a 50% tie).
- **Smart Tooltips:** Hold `Shift` for stats and `Ctrl` for materials.
- **Vanilla Respect:** Full vanilla recipes (e.g. 8 diamonds → Diamond Chestplate) still give vanilla — mixing gives blended.
- **Performance Friendly:** Works with Sodium, Lithium and FerriteCore.

### Installation

1. Install Fabric Loader 0.19.3 for Minecraft 26.2
2. Install Fabric API 0.158.0+26.2
3. Put `blendedcraft-1.0.0+26.2.jar` in `mods/`
4. Launch with Java 25

### Recipes

- **Armor:** Helmet (5), Chestplate (8), Leggings (7), Boots (4) — all head materials, can be mixed
- **Tools:** Pickaxe (3 head +2 handle), Axe (3+2), Shovel (1+2), Sword (2+1), Hoe (2+2) — `X` = head (any, can be mixed), `#` = handle (any but uniform per craft)

### Credits

- Author: **SuperFurias**

### License

MIT — see `LICENSE`
