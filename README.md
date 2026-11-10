# 🌌 Dynamic Asteroids for Starsector

[![Game Version](https://img.shields.io/badge/Starsector-0.98a--RC8%20%7C%200.97a-blue.svg)](https://fractalsoftworks.com/)
[![Java Compatibility](https://img.shields.io/badge/Java-8%20(Bytecode%2052.0)-green.svg)]()
[![LunaLib](https://img.shields.io/badge/LunaLib-Required-orange.svg)]()
[![Latest Release](https://img.shields.io/badge/Release-v2.0.63-brightgreen.svg)](https://github.com/keltuze/DynamicAsteroids/releases/tag/v2.0.63)

> **Breathes life into static asteroid belts and deep space combat!**  
> In vanilla Starsector, asteroids just hang motionless in space. **Dynamic Asteroids** transforms them into rushing kinetic rivers of space debris, derelict recovery operations, interactive salvage gathering, and tactical survival challenges during combat.  
> **Version 2.0.63** brings an autopilot navigation overhaul (eliminating forced ship spinning against flank asteroids), colossal pushable megaliths with shatter loot, station deflector domes, spawn protection, and critical sector map fixes.

---

## 📦 Downloads (v2.0.63 Release)

Direct download links for the latest version. Both editions contain all 5 live presets (from Cinematic soft drift to hypervelocity Chaos):

| Edition | Language | Highlights & Features | Direct Download |
| :--- | :---: | :--- | :--- |
| **Russian Edition (RU)** | 🇷🇺 Русский | Полная русская локализация (интерфейс, LunaLib, боевые тревоги), эвакуация остовов, сбор пасхалок щитами, пресеты | [**`DynamicAsteroids_RU_v2.0.63.zip`**](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.0.63/DynamicAsteroids_RU_v2.0.63.zip) |
| **Standard Edition (EN)** | 🇬🇧 English | Full English edition, directional shield tractor beam, derelict recovery, Domain caches, smart PD whitelist | [**`DynamicAsteroids_v2.0.63.zip`**](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.0.63/DynamicAsteroids_v2.0.63.zip) |

> 🔗 All release assets and past versions are available on the [**GitHub Releases Page**](https://github.com/keltuze/DynamicAsteroids/releases).

---

## 🚀 What's New in v2.0.63

### 🧭 1. Autopilot Navigation & Helm Authority Overhaul
* **No More Ship Spinning:** Completely removed all forced `TURN_LEFT / TURN_RIGHT` helm overrides. Autopilot and fleet AI maintain 100% full authority over ship facing and target pursuit (e.g. during chase battles) without ever spinning in circles.
* **Smart Defensive Separation:**
  * **Omni-Shield Ships (`ShieldType.OMNI`):** The shield dome rotates independently towards flank rocks to absorb impact, while the ship continues flying straight towards its target!
  * **Front-Shield Ships (`ShieldType.FRONT`):** Ships ignore rotating broadside towards the asteroid stream. Instead, Point Defense weapons automatically shoot down incoming rocks, and micro-strafe thrusters slip the hull out of harm's way without changing heading.
  * **Removed AI Panic:** Eliminated `RUN_QUICKLY` AI flags from asteroid avoidance, ensuring ships never abandon their pursuit orders.

### 🪐 2. Colossal Megaliths: Pushable Physics & Shatter Loot
* **Station-Sized Behemoths:** Colossal boulders with diameters over 1100 units now drift into battle, featuring bedrock-level durability.
* **Interactive Kinetic Shielding:** Ram or shoot megaliths to "untether" them into full Newtonian physics. Bulldoze them with ship thrusters and shields to use as portable planetary cover against enemy torpedo salvos or slam them into enemy formations!
* **High-Value Shatter Rewards:** Blasting and cracking open a giant megalith breaks loose valuable treasures frozen inside its core:
  * **Valuable Salvage Containers (20% chance):** High-tier Domain caches, survey probes, or cryopods (supplies, fuel, rare metals, crew).
  * **Preserved Fighters (1.5% chance):** An intact fighter craft and its LPC chip recovered directly into your fleet's cargo hold.
  * **Entombed Derelict Vessel (0.1% chance):** An abandoned ship breaks free from the shattered rock with active stabilization tracker for recovery into your fleet!
* **Natural Orbital Arcs:** Megaliths can travel along smooth gravitational curves, mimicking hyperbolic flybys across the battlefield.

### 🛡️ 3. Station Deflection Domes & Overload Vulnerability
* **Automated Station Shielding:** Orbital stations project a deflector barrier that smoothly diverts incoming asteroid rivers around the citadel.
* **Overload Vulnerability Window:** If a defense platform or the station core suffers flux overload (`Overload`), vents flux, or is destroyed, the deflector dome in that sector collapses with EMP arcs, leaving the station exposed to kinetic asteroid bombardment!

### 🚀 4. Fleet Deployment Grace Period
* **Safe Combat Start:** During the first 7 seconds of battle and within fleet deployment zones, all player and friendly ships are shielded by deflector dampers (0.0x collision damage). No more unfair hull damage before you can even touch the throttle!

### 💥 5. Denser Wave Formations
* **Heavier Strikes:** Formation waves ("Expanding Fan / Shotgun Spray" and "Dual Rails") are packed with +20 to +40 additional asteroids per stream, launched in rapid-fire bursts for a much punchier tactical impact.

### 🛠️ 6. Core Fixes & Map Accuracy
* **Accurate Campaign Map Triggers:** Battles in empty gaps between planetary rings or fine non-hazardous dust rings no longer trigger asteroid hazards — only physical asteroid belts and direct rock collisions do.
* **Fixed Left Border Flickering Asteroids:** Eliminated the engine glitch that caused a persistent vertical line of vibrating vanilla asteroids to spawn along the left boundary.
* **Clean Boundary Despawn:** Asteroids and debris drifting far beyond combat perimeter repellors cleanly despawn after 5 seconds, preserving performance.

---

## 🌟 Core Features

### 🧲 Directional Shield Tractor Beam & Mooring
* **Directional Shield Vectors:** Active shields act as tractor beams. Directional shields pull within their forward shield arc, while 360° omni-shields and phase cloaks pull from all angles.
* **Non-Linear Attraction & Mass Ratio:** Pull force scales smoothly as targets draw closer. Heavy capital ships reel in lightweight containers effortlessly, while frigates towing massive derelicts pull themselves toward the hulk!
* **Pure Velocity Damping:** Targets smoothly lock into formation at your shield perimeter without bouncing, clipping, or rubber-banding.

### ⚓ Derelict Ship Rescue Operations
* Abandoned hulls occasionally drift through combat. Protect them from incoming meteors and hostile fire for 60 seconds, and they are **guaranteed to appear on the post-battle Salvage Screen** for recovery!

### 📦 Interactive Containers & Hazardous Fuel Tanks
* **Loot Caches:** Domain survey probes, equipment caches, and cryopods drift through space — fly into them or reel them in with shields to loot cargo.
* **Volatile Fuel Canisters:** Highly volatile fuel tanks detonate with massive high-explosive blast waves (1,200 HE damage) upon impact or weapon hits — detonate them near enemy clusters!
* **Smart Point Defense:** Friendly PD turrets automatically shoot down dangerous asteroids while sparing valuable salvage containers.

### 🎛️ Five Live LunaLib Presets (F2)
Switch presets on the fly in the **LunaLib (F2)** menu without restarting:
1. **Balanced Standard [Default]:** 600 u/s stream velocity, balanced collisions, full AI evasion and PD defense.
2. **Cinematic Soft:** Gentle flow (400 u/s), reduced density, fragile debris easily shattered by point defense.
3. **Asteroid Storm:** Fast flow (1,200 u/s), high density, frequent formation strikes.
4. **Extreme Chaos:** Hypervelocity stream (3,000 u/s!), high ramming damage. High risk, high salvage!
5. **Custom:** Complete manual control over every slider.

### 🎮 Refit Simulator Hotkeys
Test features safely in the simulator:
* `[/]` — Toggle asteroid stream ON / OFF
* `[']` *(Apostrophe)* — Spawn a drifting derelict
* `[;]` *(Semicolon)* — Spawn a random loot cache / probe / tank
* `[L]` — Spawn a colossal megalith boulder (orbital arc)
* `[Shift +]` / `[Shift -]` — Adjust asteroid stream capacity
* `[Ctrl +]` / `[Ctrl -]` — Adjust asteroid stream velocity

---

## 🛠️ Installation

1. Download [`DynamicAsteroids_RU_v2.0.63.zip`](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.0.63/DynamicAsteroids_RU_v2.0.63.zip) (Russian) or [`DynamicAsteroids_v2.0.63.zip`](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.0.63/DynamicAsteroids_v2.0.63.zip) (English).
2. Extract into your `Starsector/mods/` directory.
3. Ensure **LunaLib** is installed and enabled.
4. Activate **Dynamic Asteroids** in the launcher.
5. Save-game compatible: safe to add or remove mid-campaign.

---

## 🤖 AI Transparency Disclosure

* All Java code in this mod was machine-generated through iterative architectural prompting and dialogue with an AI assistant (Google Antigravity / Gemini).
* The human author acted as the creative director, gameplay designer, and QA tester: identifying physics bounds, diagnosing Starsector engine collision quirks, designing terrain detection logic, evaluating save file safety, and directing multiple architectural refactors for lightweight performance.

---

**Author:** keltuze  
**Contact (Discord):** `keltuze`
