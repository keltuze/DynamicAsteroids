# 🌌 Dynamic Asteroids for Starsector

[![Game Version](https://img.shields.io/badge/Starsector-0.98a--RC8%20%7C%200.97a-blue.svg)](https://fractalsoftworks.com/)
[![Java Compatibility](https://img.shields.io/badge/Java-8%20(Bytecode%2052.0)-green.svg)]()
[![LunaLib](https://img.shields.io/badge/LunaLib-Required-orange.svg)]()
[![Latest Release](https://img.shields.io/badge/Release-v2.0.64-brightgreen.svg)](https://github.com/keltuze/DynamicAsteroids/releases/tag/v2.0.64)

> **Breathes life into static asteroid belts and deep space combat!**  
> In vanilla Starsector, asteroids just hang motionless in space. **Dynamic Asteroids** transforms them into rushing kinetic rivers of space debris, derelict recovery operations, interactive salvage gathering, and tactical survival challenges during combat.  
> **Version 2.0.64** brings smart AI obstacle navigation (ships gently glide around colossal megaliths and derelicts without spinning or breaking weapon lock), precise derelict hitboxes with scrape dampening, majestic slower megalith cruising speeds, and bundled LunaLib Cyrillic fonts.

---

## 📦 Downloads (v2.0.64 Release)

Direct download links for the latest version. Both editions contain all 5 live presets (from Cinematic soft drift to hypervelocity Chaos):

| Edition | Language | Highlights & Features | Direct Download |
| :--- | :---: | :--- | :--- |
| **Russian Edition (RU)** | 🇷🇺 Русский | Полная русская локализация (интерфейс, LunaLib, боевые тревоги), эвакуация остовов, сбор пасхалок щитами, шрифты для LunaLib | [**`DynamicAsteroids_RU_v2.0.64.zip`**](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.0.64/DynamicAsteroids_RU_v2.0.64.zip) |
| **Standard Edition (EN)** | 🇬🇧 English | Full English edition, directional shield tractor beam, derelict recovery, Domain caches, smart PD whitelist | [**`DynamicAsteroids_v2.0.64.zip`**](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.0.64/DynamicAsteroids_v2.0.64.zip) |

> 🔗 All release assets and past versions are available on the [**GitHub Releases Page**](https://github.com/keltuze/DynamicAsteroids/releases).

---

## 🚀 What's New in v2.0.64

### 🧭 1. Colossal Obstacle AI Navigation (Gliding Thruster Evasion)
* **Smooth Lateral Separation:** Ships approaching massive megaliths or drifting derelict ship hulls predict collision 4.5 seconds in advance and gently strafe laterally (`STRAFE_LEFT / STRAFE_RIGHT`) with smooth speed throttling (`DECELERATE`).
* **Zero Heading Disruption:** Ships glide around colossal obstacles without throwing their nose off target or breaking weapon fire on hostile ships.
* **Smart Mass-Based Reactions:** Massive capital ships hold steady while agile destroyers and frigates effortlessly glide around floating hulks.

### 📐 2. Precise Derelict Hitbox & Collision Dampening
* **Tight Visual Bounding:** Collision circles for elongated derelict ships are now clamped strictly to sprite dimensions. No more invisible phantom barriers extending into empty space along ship flanks.
* **Scrape Damage Reduction (90% Scrape Dampening):** Accidental side scraping or bumping against derelict hulls now reduces impact damage by 90%, preventing fragile frigates from obliterating themselves on floating wrecks.
* **Deployment Safety:** Derelicts now inherit full combat deployment damage immunity during the first 7 seconds of battle.

### 🪐 3. Megalith Speed Tuning (Majestic Cosmic Drift)
* Megalith cruise speeds have been adjusted to a slower, more majestic **60 – 120 u/s** (down from excessive hypervelocity drift), making them feel like truly colossal celestial bodies that are easy to maneuver around and utilize as kinetic cover.

### 🔤 4. Bundled LunaLib Cyrillic Fonts (RU)
* Included optional Orbitron Cyrillic fonts and a 1-click installer (`Установить_шрифты.bat`) to fix `??????` question marks in the LunaLib settings menu for Russian Starsector players.

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

1. Download [`DynamicAsteroids_RU_v2.0.64.zip`](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.0.64/DynamicAsteroids_RU_v2.0.64.zip) (Russian) or [`DynamicAsteroids_v2.0.64.zip`](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.0.64/DynamicAsteroids_v2.0.64.zip) (English).
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
