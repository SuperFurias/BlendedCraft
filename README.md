# BlendedCraft

**Author:** SuperFurias  
**Version:** 1.0.0+26.2  
**Minecraft:** 26.2 | **Fabric Loader:** 0.19.3 | **Fabric API:** 0.158.0+26.2 | **Java:** 25

BlendedCraft lets you craft **any tool or armor from any block or item** — fully flexible, fully blended.

### Features

- **Universal Crafting:** Use any block or item as material for any tool or armor. Mix and match freely. Handles and heads are separate: handles must be uniform (e.g. 2x Oak for a pickaxe handle), heads can be blended (e.g. 2x Bedrock +1x Obsidian).
- **Dynamic Stats:** Durability, mining speed, attack damage, armor, toughness and enchantability are averaged from the materials you use. Handle influences swing speed & durability, head influences mining/attack & durability. Unbreakable for Bedrock.
- **Dynamic Textures:** Inventory items get a unique 16×16 blended texture per craft, tiled from the ingredients and masked to the item's shape (pickaxe stays pickaxe, helmet stays helmet). Small items like buttons are tiled across armor so the whole piece is filled.
- **Equipped Armor:** Custom `modid:blended` `EquipmentAsset` with `BlendedArmorTextureManager` generates 64×32 `DynamicTexture`s for `humanoid` and `humanoid_leggings` layers, so equipped armor matches the blended inventory look (diamond/netherite shapes).
- **Smart Naming:** Single-material crafts show `Obsidian Pickaxe` (no `Blended`), blended show `Blended Bedrock Pickaxe` with >50% dominant or 50% tie strongest hardness/durability.
- **Smart Tooltips:** Hold `Shift` for stats (Durability, Mining Speed, Armor, Attack) and `Ctrl` for materials (handle/head breakdown) — no external mod names, all BlendedCraft.
- **Vanilla Respect:** Full vanilla recipes (e.g. 8 diamonds → Diamond Chestplate) still give vanilla, mixing gives blended.
- **Performance:** Sodium, Lithium, FerriteCore friendly. Fabric Loader 0.19.3.

### Installation

1. Install Fabric Loader 0.19.3 for Minecraft 26.2
2. Install Fabric API 0.158.0+26.2
3. Put `blendedcraft-1.0.0+26.2.jar` in `mods/`
4. Launch with Java 25

### Recipes

All 9 blended items have shaped recipes under `data/blendedcraft/recipe/blended_*.json`:
- `blended_helmet` `XXX/X X` (5), `blended_chestplate` `# #/###/###` (8), `blended_leggings` `XXX/X X/X X` (7), `blended_boots` `X X/X X` (4)
- `blended_pickaxe` `XXX/ # / # ` (3 head +2 handle), `blended_axe` `XX/X#/ #` (3+2), `blended_shovel` `X/#/ #` (1+2), `blended_sword` `X/X/#` (2+1), `blended_hoe` `XX/ #/ #` (2+2)
`X` = head (any, can be mixed), `#` = handle (`stick` placeholder, any but uniform per craft). Armor uses `iron_<type>.png` silhouette.

### Building

```bash
./gradlew build          # -> build/libs/blendedcraft-1.0.0+26.2.jar
./gradlew runClient      # dev client with Sodium/Lithium/REI
powershell -ExecutionPolicy Bypass -File build-watcher.ps1 # external build with 60s stuck-daemon guard
```

### Credits

- Author: **SuperFurias**
- Built with Fabric Loom 1.17, Gradle 9.5.1

### License

MIT — see `LICENSE`
