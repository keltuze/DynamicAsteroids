# 🌌 Dynamic Asteroids for Starsector

[![Game Version](https://img.shields.io/badge/Starsector-0.98a--RC8%20%7C%200.97a-blue.svg)](https://fractalsoftworks.com/)
[![Java Compatibility](https://img.shields.io/badge/Java-8%20(Bytecode%2052.0)-green.svg)]()
[![LunaLib](https://img.shields.io/badge/LunaLib-Compatible-orange.svg)]()

> **Breathes life into static asteroid belts!**  
> In vanilla Starsector, asteroids just hang motionless in space. **Dynamic Asteroids** turns them into active, rushing rivers of space debris during combat.

---

## 📦 Downloads (Releases)

All 4 versions are available in the **[Releases](https://github.com/releases)** tab:

| Edition | Language | Velocity / Mechanics | File |
| :--- | :---: | :--- | :--- |
| **Standard Edition** | English | **600 u/s** — Engine safe speed cap, balanced combat hazards | `DynamicAsteroids_v1.0.0.zip` |
| **Standard Edition** | Russian | **600 ед/с** — Безопасный потолок движка, сбалансированный поток | `DynamicAsteroids_RU_v1.0.0.zip` |
| **Extreme Edition** | English | **3000 u/s** — Vastly multiplied kinetic mass & impact! Ramming sends ships spinning across the map | `DynamicAsteroids_Extreme_v1.0.0.zip` |
| **Extreme Edition** | Russian | **3000 ед/с** — Огромный кинетический импульс, таран швыряет и закручивает корабли! | `DynamicAsteroids_Extreme_RU_v1.0.0.zip` |

---

## ⚡ How It Works

* **Terrain Detection:** Uses the campaign world map to strictly check if your fleet is inside an asteroid belt, field, or planetary ring (`ASTEROID_BELT`, `ASTEROID_FIELD`, `RING`).
* **Asteroid Fields / Belts:**
  * **Continuous Stream:** Asteroids rush across the map along 1 of 4 cardinal directions (Left, Right, Up, Down — rolled 25% each at battle start).
  * **Density:** 70 to 160 rocks rolled once per combat.
  * **HUD Hazard Alert:** The flagship's HUD displays the current drift direction and active field rock count.
* **Deep Space / Open Space (Outside Asteroid Belts):**
  * **Sparse Meteors:** Only 4 to 10 stray rocks in the entire sector.
  * **Irregular Spacing:** Spawns with a randomized delay between 1.5s and 5.0s, ensuring rocks drift by sporadically one by one rather than forming a solid wave.
  * **Quiet HUD:** Shows calm situational info without emergency alarms.
* **Refit Simulator:** Always models a full asteroid storm so you can stress-test ship shields and point defense.
* **Anti-Brake Cruise Control:** Prevents physics engine collision dampening from stalling the rocks — asteroids actively maintain speed after impacts.
* **30-Second Lifespan:** Asteroids expire gracefully with a soft dust puff after 30 seconds, preventing sector clutter and preserving FPS.
* **Mid-Campaign Safe:** 100% safe to add or remove at any point in an ongoing campaign (pure combat engine plugin, writes zero data to save files).

---

## ⚙️ Configuration & LunaLib

All parameters can be tuned in real-time in-game via **LunaLib (F2)** or configured in:  
`mods/DynamicAsteroids/data/config/dynamic_asteroids_settings.json`

* `minFieldRocks` & `maxFieldRocks` — Minimum and maximum rock density in asteroid fields.
* `openSpaceMinRocks` & `openSpaceMaxRocks` — Stray meteor limit in deep space.
* `openSpaceMinDelay` & `openSpaceMaxDelay` — Delay range between deep space meteors.
* `fieldSurgeSpeed` — Asteroid velocity.
* `asteroidLifespan` — Duration before rocks dissolve.

---

## 🛠️ Installation

1. Download your preferred `.zip` from the **[Releases](https://github.com/releases)** tab.
2. Extract the archive into your `Starsector/mods/` directory.
3. Enable **Dynamic Asteroids** in the Starsector launcher mod menu.

---

## 🤖 AI Transparency Disclosure

* All underlying Java code in this mod was machine-generated through iterative architectural prompting and dialogue with an AI assistant (Google Antigravity / Gemini).
* The human author acted as the creative director, gameplay designer, and QA tester: identifying physics bounds, diagnosing Starsector engine collision quirks, designing terrain detection logic, evaluating save file safety, and directing multiple architectural refactors for lightweight performance.

---

**Author:** keltuze  
**Contact (Discord):** `keltuze`
