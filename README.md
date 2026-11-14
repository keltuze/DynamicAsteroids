# 🌌 Dynamic Asteroids for Starsector

[![Game Version](https://img.shields.io/badge/Starsector-0.98.5a--RC4%20%7C%200.98.5a-blue.svg)](https://fractalsoftworks.com/)
[![Java Compatibility](https://img.shields.io/badge/Java-8%20(Bytecode%2052.0)-green.svg)]()
[![LunaLib](https://img.shields.io/badge/LunaLib-Required-orange.svg)]()
[![Latest Release](https://img.shields.io/badge/Release-v2.0.70-brightgreen.svg)](https://github.com/keltuze/DynamicAsteroids/releases/tag/v2.0.70)

> **Breathes life into static asteroid belts and deep space combat!**  
> In vanilla Starsector, asteroids just hang motionless in space. **Dynamic Asteroids** transforms them into rushing kinetic rivers of space debris, derelict recovery operations, interactive salvage gathering, and tactical survival challenges during combat.  
> **Version 2.0.70** completely overhauls the derelict recovery pipeline: stabilized derelict ships are now reliably transferred directly into the player's fleet with proper D-mod preparation and instant fleet sync, resolving issues where ships were lost across retreat/disengage battle cycles. Includes station-grade deployment deflection fields for all ships (14 seconds of visible rotating deflector domes with laminar flow and 98% kinetic asteroid protection for opening spawns and reinforcements), 100% manual player flight authority, and full compatibility with Starsector 0.98.5a.

---

## 📦 Downloads (v2.0.70 Release)

Direct download links for the latest version. Both editions contain all 5 live presets (from Cinematic soft drift to hypervelocity Chaos):

| Edition | Language | Highlights & Features | Direct Download |
| :--- | :---: | :--- | :--- |
| **Russian Edition (RU)** | 🇷🇺 Русский | Полная русская локализация (интерфейс, LunaLib, боевые тревоги), гарантированная выдача остовов во флот, дефлекторные купола кораблей на старте и подкреплениях, шрифты для LunaLib | [**`DynamicAsteroids_RU_v2.0.70.zip`**](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.0.70/DynamicAsteroids_RU_v2.0.70.zip) |
| **Standard Edition (EN)** | 🇬🇧 English | Full English edition, guaranteed combat derelict ship delivery to fleet, ship deployment deflection domes, directional shield tractor beam, Domain caches, smart PD whitelist | [**`DynamicAsteroids_v2.0.70.zip`**](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.0.70/DynamicAsteroids_v2.0.70.zip) |

> 🔗 All release assets and past versions are available on the [**GitHub Releases Page**](https://github.com/keltuze/DynamicAsteroids/releases).

---

## 🚀 What's New in v2.0.70

### ⚓ 1. Fixed Derelict Ship Recovery Upon Combat Stabilization
* **Direct & Guaranteed Fleet Transfer:** Fixed derelict ship recovery upon combat stabilization. Stabilized abandoned hulls are now directly and seamlessly transferred to the player's fleet (`playerFleet.getFleetData().addFleetMember(member)`), bypassing fragile post-battle salvage screen filters that previously discarded derelicts in retreat, disengage, or pursuit combat contexts.
* **Full Starsector Salvage Conditioning:** Recovered derelict hulls receive canonical Starsector salvage preparation: weapons and hull intact, standard D-mod generation, D-hull hullmod assignment, and 40% initial Combat Readiness (CR).
* **Instant Fleet Synchronization:** Calls engine `syncIfNeeded()`, `setSyncNeeded()`, and `updateCargoCapacities()` upon transfer, ensuring fleet screens, member lists, and supply/fuel capacities update immediately without ghosting.
* **Clean & Immersive:** Silent campaign delivery without distracting map banner popups.

### 🕹️ 2. Strictly Preserved Manual Player Flight Controls
* **Zero Interception During Manual Piloting:** The collision avoidance system never overrides player ship controls or thrusters when flying manually.
* **Autopilot Mode Support:** Collision avoidance on the flagship activates strictly when the player engages Autopilot ('U' key).
* **Fleet AI Evasion Intact:** All allied and enemy AI-controlled vessels continue actively dodging asteroid streams, colossal megaliths, and drifting hulks.

### 🛸 3. AI Collision Avoidance Overhaul
* **Full Avoidance for All Megalith Trajectories:** Ships detect colossal boulders and heavy wreckage traveling in any direction (including vertical upward drift) up to 8 seconds ahead.
* **Easter Egg & Derelict Collision Awareness:** Ships actively recognize drifting debris fragments, abandoned hulls, and boulder-embedded caches as physical obstacles.
* **Thruster Propulsion Assist:** Heavy cruisers and capital ships apply genuine maneuvering thruster propulsion to briskly steer clear of impending impacts.

### 🛡️ 4. Ship Deployment Deflection Dome (Station-Grade Grace Barrier)
* **14-Second Rotating Deflector Barrier:** Ships entering combat deploy a visible rotating holographic deflection field for 14 seconds upon spawn.
* **98% Kinetic Collision Protection:** Reduces kinetic collision damage from asteroids by 98% (`0.02x` multiplier) during deployment.
* **Laminar Flow Deflection:** Asteroids smoothly curve around ship perimeters through repulsive push forces.
* **Clean Presentation:** No noisy text spam or alarms upon entry, keeping combat immersive.

---

## 🌟 Core Features

### 🧲 Directional Shield Tractor Beam & Mooring
* **Directional Shield Vectors:** Active shields act as tractor beams. Directional shields pull within their forward shield arc, while 360° omni-shields and phase cloaks pull from all angles.
* **Non-Linear Attraction & Mass Ratio:** Pull force scales smoothly as targets draw closer. Heavy capital ships reel in lightweight containers effortlessly, while frigates towing massive derelicts pull themselves toward the hulk!
* **Pure Velocity Damping:** Targets smoothly lock into formation at your shield perimeter without bouncing, clipping, or rubber-banding.

### ⚓ Derelict Ship Rescue Operations
* Abandoned hulls occasionally drift through combat. Protect them from incoming meteors and hostile fire for 60 seconds, and they will be **safely recovered directly into your fleet after battle**!

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

1. Download [`DynamicAsteroids_RU_v2.0.70.zip`](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.0.70/DynamicAsteroids_RU_v2.0.70.zip) (Russian) or [`DynamicAsteroids_v2.0.70.zip`](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.0.70/DynamicAsteroids_v2.0.70.zip) (English).
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
