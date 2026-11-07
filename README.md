# 🌌 Dynamic Asteroids for Starsector

[![Game Version](https://img.shields.io/badge/Starsector-0.98a--RC8%20%7C%200.97a-blue.svg)](https://fractalsoftworks.com/)
[![Java Compatibility](https://img.shields.io/badge/Java-8%20(Bytecode%2052.0)-green.svg)]()
[![LunaLib](https://img.shields.io/badge/LunaLib-Compatible-orange.svg)]()

> **Breathes life into static asteroid belts!**  
> In vanilla Starsector, asteroids just hang motionless in space. **Dynamic Asteroids** turns them into active, rushing rivers of space debris during combat.  
> **Version 2.0.5** evolves the mod from a "blind cosmic meat grinder" into a deep tactical simulation of fleet survival, complete with 360° tactical formations, fleet survival intelligence, and active point-defense interception.

---

## 📦 Downloads (v2.0.5 Release)

We have **unified the distribution**: separate "Extreme" zip downloads have been retired in favor of **Live In-Game Presets**! Instead of 4 confusing archives, there are now **only 2 editions** (English and Russian), each containing all playstyles from relaxed cinematic drift to extreme hypervelocity chaos:

| Edition | Language | Features & Mechanics | Download |
| :--- | :---: | :--- | :--- |
| **Standard Edition (EN)** | English | Full mod with 5 live presets (400 to 2400 u/s), active PD interception, fleet survival AI | [`DynamicAsteroids_v2.0.5.zip`](https://github.com/keltuze/DynamicAsteroids/releases/latest/download/DynamicAsteroids_v2.0.5.zip) |
| **Russian Edition (RU)** | Russian | Полная русская локализация (интерфейс, LunaLib, боевые тревоги), 5 пресетов, авто-ПВО и ИИ уклонения | [`DynamicAsteroids_RU_v2.0.5.zip`](https://github.com/keltuze/DynamicAsteroids/releases/latest/download/DynamicAsteroids_RU_v2.0.5.zip) |

---

## 🚀 What's New in 2.0.5: The Tactical Evolution

The difference between Dynamic Asteroids 2.0.3 and 2.0.5 isn't just a couple of minor tweaks—it is a true evolution from a blind hazard into a cinematic sci-fi thriller where both allied and enemy fleets actively fight to survive.

### 🧠 1. Survival Intelligence: Ships Are No Longer Suicidal
* **What it was like in 2.0.3:** Vanilla Starsector AI doesn't recognize asteroids as dynamic threats. In 2.0.3, frigates and destroyers frequently perished not from enemy fire, but from an unlucky multi-ton boulder crashing into their hulls.
* **How it plays in 2.0.5:**
  * **Micro-Dodge Maneuvers:** Nimble destroyers and frigates calculate collision vectors and use brief lateral thruster bursts to strafe out of the way, letting boulders skim by mere millimeters from the hull.
  * **Heavy Maneuvers for Giants:** Lumbering cruisers and capitals cannot instant-strafe. Their captains preemptively turn the bow and engage main propulsion thrusters (`ACCELERATE`), hauling the colossus out of lethal trajectories.
  * **Sixth Sense Against Formation Waves:** When map-wide tactical formations strike (Meteor Lances, Comet Swarms, Dual Rails, Expanding Fans, Megalith Packs), ships detect the danger **5.5 seconds early**, calculate the corridor's width, and clear the kill zone well in advance.

### ☂️ 2. Point Defense as an Anti-Meteor Umbrella
* **What it was like in 2.0.3:** Point Defense weapons (Vulcans, Flak Cannons, PD Lasers) only fired at missiles and fighters. An asteroid the size of a space station could barrel toward you, and your turrets wouldn't even glance at it.
* **How it plays in 2.0.5:**
  * **Kinetic Interception:** All PD weapons set to autofire actively track and engage incoming rocks. Ballistic mounts calculate quadratic relative intercept leads, while beam lasers instantly burn into the rock via zero-latency hitscan.
  * **Spectacular Shattering into Dust:** In vanilla, asteroids have far too much HP for gunfire to stop them. In 2.0.5, PD weapons received a **6.0x (600%) damage multiplier** against asteroids with rebalanced baseline durability (60% HP). Concentrated fire actually pulverizes rocks into space dust a fraction of a second before impact!
  * **Strict Threat Hierarchy:** PD weapons NEVER get distracted by an asteroid if an incoming Reaper torpedo or a dive-bombing strike craft is threatening the ship. Hostile ordnance always takes priority.
  * **Overload Protection (Flux Safety):** If locked in a fierce firefight and ship flux exceeds a safe threshold (default: 75%), PD weapons cease firing at rocks, preserving capacity for shields.
  * **Flagship Autofire Support:** Autofiring weapon groups on your flagship automatically participate in shooting down incoming asteroids!

### 🛡️ 3. Shield Reactions & Phase Dives
* **What it was like in 2.0.3:** Omni-shields remained locked toward the enemy while rocks battered the unshielded stern. Phase ships simply shattered on impact.
* **How it plays in 2.0.5:**
  * **Omni-Shield Reorientation:** If a ship mounts an omni-directional shield, the AI smoothly rotates the shield bubble to meet incoming boulders, taking the kinetic impact onto the forcefield rather than naked armor. Frontal shields raise preemptively if rocks enter their arc.
  * **The Phase Trick:** Phase ships (such as Afflictor or Shade frigates) facing an unavoidable impact dive into subspace for a fraction of a second—massive asteroids pass right through the ship's phantom silhouette, and the craft reemerges unscathed without a scratch!

### 🎛️ 4. Five Playstyles Out of the Box (Live LunaLib Presets)
In 2.0.3, you had to tweak a dozen ambiguous sliders by hand. In 2.0.5, ready-made presets were introduced directly in the **LunaLib (F2)** menu, switchable live without restarting:
1. **Balanced Standard:** The sweet spot. Stream speed of 600 u/s, 200–300 asteroids, full AI evasion and PD interception enabled. Feels intense, dangerous, and completely fair.
2. **Cinematic Soft:** Reduced speed (400 u/s), less chaos, rocks easily shatter from a couple of shots. Ideal for relaxed atmospheric fleet battles and scenic screenshots.
3. **Asteroid Storm:** Stream speed of 1000 u/s, up to 700 rocks on the field, tactical formations strike every 18 seconds, and PD never stops firing. A genuine test of tactical skill.
4. **Extreme Chaos:** The former Extreme Edition! Hypervelocity meteors (2400 u/s!), up to 900 rocks, insane density, and a frantic test of reflexes and shield reserves. *(Note: Above 600 u/s, Starsector engine limits cap visual speed and scale kinetic impact mass instead!)*
5. **Custom:** Complete control to fine-tune every parameter manually.

> [!NOTE]
> **LunaLib Preset Behavior:** When switching presets 1–4, LunaLib does not move the lower sliders visually on screen (this is standard LunaLib behavior). Presets actively enforce their calibrated parameters in battle. To unlock and adjust individual sliders manually, switch the profile to **Preset 0 (Custom)**.

### 🛠️ 5. Fully Restored LunaLib Configuration UI (F2)
* Completely resolved the previous visual bug where LunaLib settings rows corrupted and slider controls disappeared.
* Clean tab layout: parameters are organized across **Asteroid Stream** and **Fleet AI Reaction** tabs with fully functional sliders and zero broken lines.

---

## ⚡ Core Features

* **Intelligent Planetary Detection:** Strictly checks campaign map coordinates — only spawns streams when inside actual asteroid belts, fields, or planetary rings.
* **Calm Deep Space:** In open space outside belts, only rare stray meteors drift by at gentle intervals without alarms.
* **Zero-Lag Particle Trails:** Sparkling kinetic trails (golden glints, cyan comet dust, crimson plasma for targeted snipes) optimized for solid 120 FPS.
* **Anti-Brake Cruise Control:** Overrides vanilla physics collision dampening to keep streams flowing steadily.
* **30-60s Despawn:** Asteroids dissolve gracefully into vapor puffs, preserving performance.
* **Refit Simulator Controls:** `[/]` toggles stream ON/OFF, and `[Shift +]` / `[Shift -]` dynamically adjusts asteroid capacity.
* **Safe Mid-Campaign:** 100% save-game safe. Add or remove anytime without corrupting campaign saves.

---

## 🛠️ Installation

1. Download `DynamicAsteroids_v2.0.5.zip` (English) or `DynamicAsteroids_RU_v2.0.5.zip` (Russian).
2. Extract the archive into your `Starsector/mods/` directory.
3. Enable **Dynamic Asteroids** in the game launcher.

---

## 🤖 AI Transparency Disclosure

* All Java code in this mod was machine-generated through iterative architectural prompting and dialogue with an AI assistant (Google Antigravity / Gemini).
* The human author acted as the creative director, gameplay designer, and QA tester: identifying physics bounds, diagnosing Starsector engine collision quirks, designing terrain detection logic, evaluating save file safety, and directing multiple architectural refactors for lightweight performance.

---

**Author:** keltuze  
**Contact (Discord):** `keltuze`
