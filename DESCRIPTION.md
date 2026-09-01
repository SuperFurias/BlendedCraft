# BlendedCraft - Craft any tool or armor from any block or item

BlendedCraft completely reimagines crafting. Any block or item can be used as material for any tool or armor. Mix materials freely, every combination creates a unique piece of gear with its own stats, look, and special traits.

## Features

### Universal Crafting
- **9 craftable items** - Helmet, Chestplate, Leggings, Boots, Pickaxe, Axe, Shovel, Sword, Hoe.
- Handles must be uniform (e.g. 2x Oak for a pickaxe handle), heads can be **blended** (e.g. 2x Bedrock + 1x Obsidian).
- Armor uses the full 3x3 grid - every slot counts.

### Dynamic Stats
- **Durability, mining speed, attack damage, attack speed, armor, toughness, knockback resistance and enchantability** are all blended from your materials.
- Handles affect swing speed and durability, heads affect mining/attack and durability.
- **Bedrock makes items unbreakable.** Full **Nether Star** sets are unbreakable too.
- Rare materials grant **Epic/Legendary rarity** styling.
- Real vanilla tool behavior: correct mining speed, correct mineable tags, proper enchantability (tools get tool enchants, armor gets armor enchants).
- Hold **Shift** in the tooltip for detailed stats, **Ctrl** for the material list.

### Dynamic Patchwork Textures
- Every item's texture is **generated from its actual ingredients** - a patchwork that samples the real vanilla textures of what you crafted with, placed in the same grid positions you used.
- **Full 3D support**: textured in your hand, third person, and as dropped items - just like vanilla.
- **Vanilla per-pixel shading** transferred onto the blended texture, with adaptive rim-lighting on dark materials.
- **Enchant glint** works in the inventory.
- Animated materials (magma etc.) supported.
- Mob heads, compasses, and multi-part blocks (cactus, grass, logs...) all render with their real colors.

### Material Traits (special perks)
Craft with special materials to gain traits - **full item = max vanilla level, blended = level 1**:

| Material | Trait |
|---|---|
| Magma / fiery blocks | **Blazing Harvest** - auto-smelts mined blocks (works with Fortune), **Scorching Blade** - Fire Aspect, **Molten Skin** - Fire Resistance set bonus |
| Slime | **Bouncy Blade** - Knockback on swords, **Slime Boots** - bounce like slime blocks from 3+ block falls (shift to cancel), no fall damage |
| Cactus | **Prickly** - Thorns on armor |
| Lapis / Emerald | **Enchanting Fortune** - Fortune on mining tools, Looting on swords (III when full) |
| Quartz | **Razor Edge** - up to Sharpness V on swords, works with every quartz block type |
| Feather boots | **Featherweight** - Feather Falling IV |
| Ice / Packed Ice | **Frozen Sole** - Frost Walker + freeze immunity |
| Soul Sand / Soil | **Soulbound Stride** - Soul Speed |
| Redstone | **Haste** on tools |
| Sugar | **Speed** |
| Prismarine / Heart of the Sea | **Oceanborn** - Water Breathing helmet |
| Glowstone / Sea Lantern | **Luminous** - Night Vision helmet |
| Emerald armor | **Lucky Charm** - Luck |

### Backward Compatibility
- **Old items update automatically on load** - stat fixes, trait corrections, and re-leveling all happen without recrafting.

### Modern Minecraft
- Built for **Minecraft 26.2** on **Fabric**. Works with REI, Sodium, Lithium and FerriteCore.
