# 🌌 Dynamic Asteroids for Starsector

[![Game Version](https://img.shields.io/badge/Starsector-0.98a--RC8%20%7C%200.97a-blue.svg)](https://fractalsoftworks.com/)
[![Java Compatibility](https://img.shields.io/badge/Java-8%20(Bytecode%2052.0)-green.svg)]()
[![LunaLib](https://img.shields.io/badge/LunaLib-Required-orange.svg)]()
[![Latest Release](https://img.shields.io/badge/Release-v2.0.6%20FixMagneto-brightgreen.svg)](https://github.com/keltuze/DynamicAsteroids/releases/tag/v2.0.6)

> **Breathes life into static asteroid belts and deep space combat!**  
> In vanilla Starsector, asteroids just hang motionless in space. **Dynamic Asteroids** transforms them into rushing kinetic rivers of space debris, derelict recovery operations, interactive salvage gathering, and tactical survival challenges during combat.  
> **Version 2.0.6 FixMagneto** expands combat functionality with directional shield tractor beams, derelict ship stabilization, interactive Domain loot caches, volatile explosive canisters, and deep space drift encounters.

---

## 📦 Downloads (v2.0.6 FixMagneto Release)

Direct download links for the latest version. Both editions contain all 5 live presets (from Cinematic soft drift to hypervelocity Chaos):

| Edition | Language | Highlights & Features | Direct Download |
| :--- | :---: | :--- | :--- |
| **Russian Edition (RU)** | 🇷🇺 Русский | Полная русская локализация (интерфейс, LunaLib, боевые тревоги), эвакуация остовов, сбор пасхалок щитами, пресеты | [**`DynamicAsteroids_RU_v2.0.6.zip`**](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.0.6/DynamicAsteroids_RU_v2.0.6.zip) |
| **Standard Edition (EN)** | 🇬🇧 English | Full English edition, directional shield tractor beam, derelict recovery, Domain caches, smart PD whitelist | [**`DynamicAsteroids_v2.0.6.zip`**](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.0.6/DynamicAsteroids_v2.0.6.zip) |

> 🔗 All release assets and past versions are available on the [**GitHub Releases Page**](https://github.com/keltuze/DynamicAsteroids/releases).

---

## 🚀 Key Features in v2.0.6 FixMagneto

### 🧲 1. Directional Shield Tractor Beam & Pure-Damping Mooring
* **Directional Shield Vectors:** Active shields act as tractor beam projectors. Directional shields (e.g. 60°–180° arc) only attract targets within their forward shield dome. Omni-directional (360°) shields and phase cloaks pull from all angles!
* **Non-Linear Attraction Curve:** Follows a calibrated power-law curve:
  $$F_{\text{norm}}(p) = 0.10 + 0.90 \cdot p^{0.95424}$$
  Starts at a gentle 10% pull at max range, firmly locks on with 20% force within 10% of the journey, and smoothly scales to 100% force at the mooring perimeter.
* **Pure Velocity Damping (Zero Perpetual Motion):** At the shield mooring boundary ($D_{\text{safe}} = R_{\text{shield}} + R_{\text{target}} + 45$), tractive pull drops strictly to zero, and relative closing velocity is extinguished. No bouncing, no rubber-banding, and no perpetual self-acceleration!
* **Newtonian Mass Scaling:** Massive derelicts act as gravitational anchors ($a_{\text{target}} \propto \text{MassRatio}^2$). Light frigates smoothly reel themselves in toward giant hulls, while capital ships easily reel in supply caches without shuddering.

### ⚓ 2. Derelict Ship Rescue Operations
* Abandoned hulls (including modded hulls!) occasionally drift through the combat zone.
* A dedicated HUD tracker displays their direction, range, and a 60-second stabilization timer.
* Protect the drifting ship from incoming asteroids and hostile gunfire until systems stabilize, and it is **guaranteed to appear on the post-battle Salvage Screen** for recovery into your fleet!

### 📦 3. Interactive Containers (Easter Eggs) & Gathering
* **Domain Caches, Probes & Cryopods:** Valuables drift with the stream. Bumping into them transfers loot (supplies, fuel, metals, rare crew, fighter LPCs) directly into your cargo.
* **Volatile Fuel Canisters:** Drifting orange fuel containers detonate with massive high-explosive blast waves (1,200 HE damage) upon collision or weapons fire. Use them tactically against enemy squadrons!
* **Smart Point-Defense Whitelist:** Friendly and allied point-defense turrets shoot down hazardous asteroids while automatically whitelisting and sparing valuable salvage.

### 🌌 4. Deep Space Drift Encounters
* Easter eggs and rare stray meteors now extend beyond dense asteroid belts into ordinary open space battles.
* A dedicated LunaLib slider allows you to fine-tune their frequency or disable open space drift entirely.

### 🎛️ 5. Five Live LunaLib Presets (F2)
Switch presets on the fly in the **LunaLib (F2)** menu without restarting the game:
1. **Balanced Standard [Default]:** Stream speed 600 u/s, 200–800 rocks, full AI evasion and PD defense. The sweet spot of intensity and tactical reward.
2. **Cinematic Soft:** Gentle flow (400 u/s), reduced rock density, fragile debris easily shattered by point defense. Ideal for scenic screenshots.
3. **Asteroid Storm:** Fast flow (1,200 u/s), up to 1,000 rocks, formations strike every 18 seconds.
4. **Extreme Chaos:** Hypervelocity stream (3,000 u/s!), extreme density, high ramming damage. High risk, high salvage!
5. **Custom:** Complete manual control over every slider.

### 🎮 Refit Simulator Hotkeys
Test and experiment safely in the simulator:
* `[/]` — Toggle asteroid stream ON / OFF
* `[']` *(Apostrophe / Tilde)* — Spawn a drifting derelict near your ship
* `[;]` *(Semicolon)* — Spawn a random loot cache / probe / tank
* `[Shift +]` / `[Shift -]` — Increase or decrease asteroid stream capacity
* `[Ctrl +]` / `[Ctrl -]` — Increase or decrease asteroid stream velocity

---

## 🛠️ Installation

1. Download [`DynamicAsteroids_RU_v2.0.6.zip`](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.0.6/DynamicAsteroids_RU_v2.0.6.zip) (Russian) or [`DynamicAsteroids_v2.0.6.zip`](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.0.6/DynamicAsteroids_v2.0.6.zip) (English).
2. Extract the archive into your `Starsector/mods/` directory.
3. Ensure **LunaLib** is installed and enabled.
4. Activate **Dynamic Asteroids** in the Starsector launcher.
5. Save-game compatible: safe to add or remove mid-campaign.

---

## 🤖 AI Transparency Disclosure

* All Java code in this mod was machine-generated through iterative architectural prompting and dialogue with an AI assistant (Google Antigravity / Gemini).
* The human author acted as the creative director, gameplay designer, and QA tester: identifying physics bounds, diagnosing Starsector engine collision quirks, designing terrain detection logic, evaluating save file safety, and directing multiple architectural refactors for lightweight performance.

---

**Author:** keltuze  
**Contact (Discord):** `keltuze`
