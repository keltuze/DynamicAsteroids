# 🌌 Dynamic Asteroids for Starsector

[![Game Version](https://img.shields.io/badge/Starsector-0.98.5a--RC4%20%7C%200.98.5a-blue.svg)](https://fractalsoftworks.com/)
[![Java Compatibility](https://img.shields.io/badge/Java-8%20(Bytecode%2052.0)-green.svg)]()
[![LunaLib](https://img.shields.io/badge/LunaLib-Required-orange.svg)]()
[![Latest Release](https://img.shields.io/badge/Release-v2.0.75-brightgreen.svg)](https://github.com/keltuze/DynamicAsteroids/releases/tag/v2.0.75)

> **Breathes life into static asteroid belts and deep space combat!**  
> In vanilla Starsector, asteroids just hang motionless in space. **Dynamic Asteroids** transforms them into rushing kinetic rivers of space debris, derelict recovery operations, interactive salvage gathering, and tactical survival challenges during combat.  
> **Version 2.0.75** completely overhauls the derelict recovery pipeline: stabilized derelict ships are now reliably transferred directly into the player's fleet with proper D-mod preparation and instant fleet sync, resolving issues where ships were lost across retreat/disengage battle cycles. Includes station-grade deployment deflection fields for all ships (14 seconds of visible rotating deflector domes with laminar flow and 98% kinetic asteroid protection for opening spawns and reinforcements), 100% manual player flight authority, and full compatibility with Starsector 0.98.5a.

---

## 📦 Downloads (v2.0.75 Release)

Direct download links for the latest version. Both editions contain all 5 live presets (from Cinematic soft drift to hypervelocity Chaos):

| Edition | Language | Highlights & Features | Direct Download |
| :--- | :---: | :--- | :--- |
| **Russian Edition (RU)** | 🇷🇺 Русский | Полная русская локализация (интерфейс, LunaLib, боевые тревоги), гарантированная выдача остовов во флот, дефлекторные купола кораблей на старте и подкреплениях, шрифты для LunaLib | [**`DynamicAsteroids_RU_v2.0.75.zip`**](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.0.75/DynamicAsteroids_RU_v2.0.75.zip) |
| **Standard Edition (EN)** | 🇬🇧 English | Full English edition, guaranteed combat derelict ship delivery to fleet, ship deployment deflection domes, directional shield tractor beam, Domain caches, smart PD whitelist | [**`DynamicAsteroids_v2.0.75.zip`**](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.0.75/DynamicAsteroids_v2.0.75.zip) |

> 🔗 All release assets and past versions are available on the [**GitHub Releases Page**](https://github.com/keltuze/DynamicAsteroids/releases).

---

## 🚀 What's New in v2.0.75 ("Горячие клавиши и Сильная защита пво")

### 🛡️ 1. Strong Point Defense (8.0x Asteroid Damage Multiplier)
* **Standardized 8.0x PD Multiplier:** Point defense damage against asteroids is now set to 8.0x across all fixed presets (Cinematic, Balanced, Storm, Chaos).
* **Rapid Interception:** Flak cannons, vulcan batteries, and point defense lasers vaporize incoming space rock threats before they can breach hull or shields.
* **Custom Preset Support:** Fully adjustable from 1.0x to 30.0x in Custom Preset (0) via LunaLib.

### ☄️ 2. Straight-Line Colossal Megaliths & Interactive Newtonian Untethering
* **Strict Straight Trajectories:** Colossal Megaliths no longer curve on arbitrary arcs. They fly in direct straight trajectories across the sector.
* **65% Targeted Snipe Chance:** 65% of naturally spawning Megaliths lock course directly toward a random active ship on the battlefield, creating high-stakes tactical navigation hazards.
* **Immunity to Small Rock Bumps:** Megaliths maintain their precise vector and velocity through streams, completely undisturbed by smaller asteroid collisions.
* **Ship Impact Untethering:** The moment ANY ship touches or shoots a Megalith (kinetic, high-explosive, energy projectiles, missiles, or ship hulls), the Megalith untethers from scripted trajectory control into a 100% free physical Newtonian body.

### 🌌 3. 8 to 16 Concurrent Megaliths in Asteroid Belts
* **Massive Atmospheric Presence:** When engaging in battle inside an asteroid field, the sector smoothly spawns and maintains between 8 and 16 simultaneous colossal Megaliths (randomized per battle).
* **Sector-Wide Spread:** Pre-seeded Megaliths appear distributed across the entire operational area from the opening seconds of engagement.

### 🧲 4. Shield Magnet Toggle Hotkey [T] / Mouse 5
* **In-Combat Toggle:** Press `[T]` (default keycode 20) or Mouse 5 (forward side button) at any time to toggle the shield tractor beam attraction of derelicts, caches, and debris ON or OFF.
* **Clean Visual Feedback:** Displays floating text above the flagship: `"Magnit On"` in cyan (`#3CDCf0`) or `"Magnit Off"` in red (`#FF4141`). Silent and unobtrusive without audio or banner clutter.
* **Fully Configurable:** Easily rebindable in LunaLib (F2) under Keycode setting or via `dynamic_asteroids_settings.json`.

### 🌊 5. Dynamic Formation Cycles & Pattern Combinations
* **Increased Formation Frequency:** Shorter torrent/burst intervals and higher transition probabilities bring dense geometric formations to the battlefield more frequently.
* **Simultaneous Avalanche Streams:** 45% chance for formation waves to trigger alongside baseline avalanche streams.
* **Multi-Pattern Combinations:** Multi-angle streams roll independent formation shapes (e.g., Comet Swarm crossed with Expanding Fan) rather than repeating identical geometries. Rarest at 3 concurrent streams (12%), common at 1 stream (60%).

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

1. Download [`DynamicAsteroids_RU_v2.0.75.zip`](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.0.75/DynamicAsteroids_RU_v2.0.75.zip) (Russian) or [`DynamicAsteroids_v2.0.75.zip`](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.0.75/DynamicAsteroids_v2.0.75.zip) (English).
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
