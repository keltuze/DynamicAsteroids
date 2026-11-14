# 🌌 Dynamic Asteroids for Starsector

[![Game Version](https://img.shields.io/badge/Starsector-0.98.5a--RC4%20%7C%200.98.5a-blue.svg)](https://fractalsoftworks.com/)
[![Java Compatibility](https://img.shields.io/badge/Java-8%20(Bytecode%2052.0)-green.svg)]()
[![LunaLib](https://img.shields.io/badge/LunaLib-Required-orange.svg)]()
[![Latest Release](https://img.shields.io/badge/Release-v2.0.67-brightgreen.svg)](https://github.com/keltuze/DynamicAsteroids/releases/tag/v2.0.67)

> **Breathes life into static asteroid belts and deep space combat!**  
> In vanilla Starsector, asteroids just hang motionless in space. **Dynamic Asteroids** transforms them into rushing kinetic rivers of space debris, derelict recovery operations, interactive salvage gathering, and tactical survival challenges during combat.  
> **Version 2.0.67** introduces station-grade deployment deflection fields for all ships (7 seconds of visible rotating deflector domes with laminar flow and 98% kinetic asteroid protection for opening spawns and reinforcements), fixed station dome circuit breaker recovery, guaranteed post-battle derelict recovery, and full compatibility with Starsector 0.98.5a.

---

## 📦 Downloads (v2.0.67 Release)

Direct download links for the latest version. Both editions contain all 5 live presets (from Cinematic soft drift to hypervelocity Chaos):

| Edition | Language | Highlights & Features | Direct Download |
| :--- | :---: | :--- | :--- |
| **Russian Edition (RU)** | 🇷🇺 Русский | Полная русская локализация (интерфейс, LunaLib, боевые тревоги), дефлекторные купола кораблей на старте и подкреплениях, эвакуация остовов, шрифты для LunaLib | [**`DynamicAsteroids_RU_v2.0.67.zip`**](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.0.67/DynamicAsteroids_RU_v2.0.67.zip) |
| **Standard Edition (EN)** | 🇬🇧 English | Full English edition, ship deployment deflection domes, directional shield tractor beam, derelict recovery, Domain caches, smart PD whitelist | [**`DynamicAsteroids_v2.0.67.zip`**](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.0.67/DynamicAsteroids_v2.0.67.zip) |

> 🔗 All release assets and past versions are available on the [**GitHub Releases Page**](https://github.com/keltuze/DynamicAsteroids/releases).

---

## 🚀 What's New in v2.0.67

### 🛡️ 1. Ship Deployment Deflection Dome (Station-Grade Grace Barrier)
* **Visible Rotating Deflector Barrier for 7 Seconds:** Every ship appearing on the battlefield now activates a visible rotating holographic deflection field for strictly 7 seconds upon spawn, sized proportioned to the ship's bounding area.
* **Laminar Flow Asteroid Deflection:** Incoming asteroids smoothly glide and curve around ship perimeters through tangential laminar flow, with repulsive push force and visual contact sparks if asteroids near the barrier.
* **98% Kinetic Collision Protection:** While the barrier is active, collision damage from asteroids is reduced by 98% (`0.02x` multiplier, matching station dome defense).
* **Dual Spawn Handling:** Seamlessly protects both opening battle deployments and mid-battle reinforcement calls with zero delay.
* **Activation & Expiration Cues:** Displays visual particle rings and floating combat text ("ПОЛЕ ОБТЕКАНИЯ: АКТИВНО (7с)" / "ПОЛЕ ОБТЕКАНИЯ: ОТКЛЮЧЕНО"), with dedicated UI audio for the player ship.

### ⚡ 2. Station Deflection Dome Circuit Breaker Fix
* Fixed edge-detection timer bug: station module destruction or flux overload triggers a 60-second offline outage with visible sparks and notification, and automatically restores once systems recover, without restarting every scan tick.

### ⚓ 3. Guaranteed Derelict Recovery & Battle-End Safety
* Stabilized rare derelict hulls captured during combat are now guaranteed to be offered on the post-battle recovery screen (`FleetEncounterContext`).
* Battle-end safety logic automatically completes stabilization for derelicts being held when battle ends.
* Out-of-bounds rescue beacons grant a 50% recovery chance if stabilized hulls are pushed past map edges.

### 💥 4. Volatile Fuel Tank Overhaul
* Dedicated high-explosive fuel canister texture (`cargo/fuel.png`) with dynamic radar pings.
* Devastating detonation dealing 3,200 HE + 1,500 EMP blast damage with zero friendly self-immunity.

### 🪐 5. Starsector 0.98.5a Compatibility
* Fully updated compatibility headers and API integration for Starsector **0.98.5a** (`0.98.5a-RC4`).

---

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

1. Download [`DynamicAsteroids_RU_v2.0.67.zip`](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.0.67/DynamicAsteroids_RU_v2.0.67.zip) (Russian) or [`DynamicAsteroids_v2.0.67.zip`](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.0.67/DynamicAsteroids_v2.0.67.zip) (English).
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
