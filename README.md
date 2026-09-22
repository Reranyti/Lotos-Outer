# Lotos-Outer — Lotus Blight / Порча лотоса

Minecraft Forge 1.20.1 mod about a beautiful but dangerous lotus infection. The infection begins in water, follows river courses, reaches the shore, and blooms into a green-and-pink domain.

## Current prototype features

The prototype contains infected lotus, infected water, infected soil, lotus roots and the Lotus Heart. A lotus seed starts an outbreak in a water source. Active blocks spread only in loaded areas and periodically prefer nearby water, then natural ground. When Streams Reflowing is installed, the mod follows the actual water layout and lower connected water positions without requiring a hard dependency on that mod.

Living creatures near active outbreaks receive Lotus Spores. The Lotus Infection Map is used with right click and searches nearby loaded terrain; green particles mark ordinary infected zones and pink particles mark Lotus Hearts. Cleansing Powder turns nearby infected ground, water and roots back into dirt.

A normal villager can become the Lotus Botanist by claiming a Lotus Heart as a job site. The profession offers lotus seeds, an infection map and cleansing powder. It uses the vanilla villager entity and profession system, so it is compatible with standard village mechanics.

## Building

Use Java 17 and run:

```text
./gradlew build
```

The resulting jar is written to `build/libs/`. Put the jar in the `mods` folder of a Forge 1.20.1 profile.

## Suggested client visual setup

For the green-and-pink flowering theme, use Oculus on the Forge client and Complementary Reimagined as the shader pack. Start with high-quality foliage and colored lighting, medium volumetric fog and medium shadow distance. For this system with 8 GB RAM, allocate about 4–5 GB to Minecraft rather than 20 GB; the RTX 3050 6 GB is suitable for shader effects, while RAM remains the main constraint.

The shader is optional. The mod's particles and colors are designed to remain readable without shaders.

## Configuration

The common config is generated under `config/lotusblight-common.toml`. It controls spread interval, spread radius, map scan radius, map cooldown and soft Streams Reflowing compatibility.

## License

This project's own code and lore are All Rights Reserved (see `mod_license` in `gradle.properties`) — nobody else may copy, redistribute, or fork it without permission. The `LICENSE` and `LICENSE.txt` files in this repo are boilerplate carried over from the Forge MDK template (Eclipse Public License / LGPL 2.1) — they cover Forge's own tooling, not this mod's original code.

Character concepts are partly original, partly inspired by/referencing DOORS (Roblox) — lore and writing here are our own. Music and other third-party references used as inspiration or temporary assets belong to their original creators; if you recognize your own work and don't want it associated with this project, reach out and it will be removed. Minecraft is a trademark of Mojang Studios. Streams Reflowing, Oculus and Complementary Reimagined are separate projects with their own licenses.
