# 🌌 Dynamic Asteroids for Starsector

[![Game Version](https://img.shields.io/badge/Starsector-0.98a--RC8%20%7C%200.97a-blue.svg)](https://fractalsoftworks.com/)
[![Java Compatibility](https://img.shields.io/badge/Java-8%20(Bytecode%2052.0)-green.svg)]()
[![LunaLib](https://img.shields.io/badge/LunaLib-Required-red.svg)]()

> **Breathes dynamic life into static asteroid belts!**  
> In vanilla Starsector, asteroids hang motionless in space. **Dynamic Asteroids** turns them into active, rushing rivers of cosmic debris during combat.  
> **Version 2.0.6** expands combat functionality with new interactive objects, a ship rescue mechanic, magnetic gathering, collision damage scaling, and rare deep space encounters.

---

## 📦 Downloads (v2.0.6 Release)

The mod is available in two editions (English and Russian), each containing all 5 live presets (from relaxed cinematic drift to hypervelocity chaos):

| Edition | Language | Features & Mechanics | Download |
| :--- | :---: | :--- | :--- |
| **Standard Edition (EN)** | English | Full mod with 5 presets, derelict ship rescue, shield magnetic gathering, deep space encounters, active PD interception | [`DynamicAsteroids_v2.0.6.zip`](https://github.com/keltuze/DynamicAsteroids/releases/latest/download/DynamicAsteroids_v2.0.6.zip) |
| **Russian Edition (RU)** | Russian | Полная русская локализация (интерфейс, LunaLib, боевые тревоги), 5 пресетов, спасение остовов, притягивание лута щитами | [`DynamicAsteroids_RU_v2.0.6.zip`](https://github.com/keltuze/DynamicAsteroids/releases/latest/download/DynamicAsteroids_RU_v2.0.6.zip) |

---

## 🚀 What's New in 2.0.6: Interactive Salvage & Rescue Operations

Version 2.0.6 evolves the asteroid stream from pure environmental danger into an interactive, rewarding battlefield:

### 🛳️ 1. Derelict Ship Rescue Operations
* **Drifting Hulls in Combat:** Abandoned hulls (including ships from installed mods!) occasionally drift through the asteroid streams. Dynamic distribution spans frigates (45%), destroyers (30%), cruisers (18%), and rare capital ships (7%).
* **Tactical SOS HUD Tracker:** When a derelict enters the area, a live HUD tracker displays its direction arrow (`↑`, `↖`, `←`, `↙`, `↓`, `↘`, `→`, `↗`), relative clock heading (`2 o'clock`), distance, and stabilization timer.
* **Salvage Screen Recovery:** Protect the ship from colliding asteroids and enemy weapons fire for 60 seconds to stabilize its systems. Upon winning the battle, the rescued vessel is guaranteed to appear on the post-battle Salvage Screen for recovery into your fleet!

### 📦 2. Space Debris, Domain Caches & Shield Attraction
* **Valuable Cosmic Objects:** Domain supply caches, geological survey probes, emergency cryo stasis pods, nav buoys, and drifting fighters now fly through the debris.
* **Direct Cargo Transfer:** Bumping into containers instantly secures resources into fleet cargo (supplies, fuel, metals, rescued crew members, or fighter LPC chips).
* **🧲 Shield Magnetic Attraction:** Active ship shields act as a tractor beam, automatically pulling passing loot containers toward your ship for effortless gathering during battle.
* **Smart Point Defense:** Friendly PD systems automatically recognize and ignore valuable objects and derelict hulls, preventing accidental destruction.

### 🌌 3. Rare Deep Space Encounters
* Easter eggs extend beyond asteroid belts! During standard space engagements, amid rare stray meteors, you may occasionally spot a drifting derelict or an ancient cache.
* A dedicated LunaLib slider (`da_openSpaceEasterEggMult`) allows you to tune their frequency or disable them entirely.

### 💣 4. Explosive Fuel Tanks
* Drifting orange fuel canisters barrel through the combat zone. Colliding with a ship or detonating from weapons fire triggers a massive 1200 HE blast with a 350-unit shockwave. Keep your distance or tactically detonate them near enemy clusters!

### ⚖️ 5. Collision Damage Scaling
* Asteroid ramming damage is now dynamically scaled across LunaLib profiles:
  * **Cinematic:** `0.35x` damage (gentle bumps, very forgiving)
  * **Balanced:** `1.0x` damage (standard balanced collision force)
  * **Storm:** `1.8x` damage (hazardous kinetic impacts)
  * **Chaos:** `3.5x` damage (hyperkinetic destruction)
  * **Custom:** Freely adjustable via slider from `0.1x` to `5.0x`.

### ⌨️ 6. Simulator & Test Hotkeys
* `[/]` — Toggle asteroid stream ON/OFF
* `[Shift + / -]` — Adjust asteroid limit (+-50 rocks)
* `[Ctrl + / -]` — Adjust stream velocity on the fly (+-100 u/s)
* `[']` (Apostrophe / Tilde) — Spawn a test derelict ship near the player
* `[;]` (Semicolon) — Spawn a test loot container / easter egg near the player

### 🛠️ 7. Technical Notes
* **LunaLib** is now declared as a hard dependency in `mod_info.json`.

---

## ⚡ Core Features from v2.0.5

* **Fleet Survival Intelligence:** Micro-dodge maneuvers for frigates/destroyers; heavy evasive turn & burn for cruisers and capitals; early formation evasion.
* **Point Defense Interception:** Active autofire PD tracking of hazardous asteroids with 6.0x damage multiplier and flux overload conservation.
* **Shield Reactions & Phase Dives:** Omni-shields rotate smoothly toward impacts; phase ships dive to let boulders safely pass through.
* **Save-Game Safe:** Can be safely enabled or disabled mid-campaign without corrupting saves.

---

**Author:** keltuze  
**Contact (Discord):** `keltuze`
