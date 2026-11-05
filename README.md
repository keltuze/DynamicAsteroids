# ☄️ Dynamic Asteroids for Starsector

[![Game Version](https://img.shields.io/badge/Starsector-0.98a--RC8%20%7C%200.97a-blue.svg)](https://fractalsoftworks.com/)
[![Java Compatibility](https://img.shields.io/badge/Java-8%20(Bytecode%2052.0)-green.svg)]()
[![LunaLib](https://img.shields.io/badge/LunaLib-Compatible-orange.svg)]()
[![Version](https://img.shields.io/badge/Version-2.0.2%20(Cyclone%20Edition)-red.svg)](https://github.com/keltuze/DynamicAsteroids/releases/latest)

> **Breathes life into static asteroid belts!**  
> In vanilla Starsector, asteroids just hang motionless in space. **Dynamic Asteroids** turns them into active, rushing rivers of space debris during combat.

---

## 📦 Downloads (v2.0.2 Releases)

All 4 versions are available in the **[Releases](https://github.com/keltuze/DynamicAsteroids/releases/latest)** tab:

| Edition | Language | Velocity / Mechanics | Download |
| :--- | :---: | :--- | :--- |
| **Extreme Edition 2.0 (Cyclone)** | English | **3000 u/s** — High-velocity kinetic storm, 100–1000 rocks stream, 60s lifespan, 360° cyclones, 5% sniper barrages, English UI | [Download ZIP](https://github.com/keltuze/DynamicAsteroids/releases/latest/download/DynamicAsteroids_Extreme_v2.0.2.zip) |
| **Extreme Edition 2.0 (Циклон)** | Русский | **3000 u/s** — Сокрушительный кинетический импульс, поток 100–1000 камней, время жизни 60 сек, циклоны 360°, русский интерфейс | [Скачать ZIP](https://github.com/keltuze/DynamicAsteroids/releases/latest/download/DynamicAsteroids_Extreme_RU_v2.0.2.zip) |
| **Standard Edition 2.0 (Cyclone)** | English | **600 u/s** — Safe balanced velocity cap, 100–1000 rocks stream, 60s lifespan, 360° cyclones, 5% sniper barrages, English UI | [Download ZIP](https://github.com/keltuze/DynamicAsteroids/releases/latest/download/DynamicAsteroids_v2.0.2.zip) |
| **Standard Edition 2.0 (Циклон)** | Русский | **600 u/s** — Безопасная скорость столкновений, поток 100–1000 камней, время жизни 60 сек, циклоны 360°, русский интерфейс | [Скачать ZIP](https://github.com/keltuze/DynamicAsteroids/releases/latest/download/DynamicAsteroids_RU_v2.0.2.zip) |

---

## 🌟 What's New in v2.0.2 (Cyclone Edition)

* **Extended 60-Second Asteroid Lifespan:** Extensive combat testing revealed that extending rock lifespan from 30s to **60 seconds** has virtually zero performance impact (rock-solid 120 FPS preserved). This drastically improves immersion, allowing debris streams and tactical clouds to persist across larger battle sectors!
* **Expanded 100–1000 Asteroids Field Stream Range:** Battles in asteroid fields dynamically roll asteroid capacity between **100 and 1000 asteroids** (configurable via settings and LunaLib). Formation wave budgets scale dynamically with field density.
* **Living Planetary Ring Avalanche:** Continuous baseline avalanche steadily flows across the sector in one constant cardinal direction throughout the entire fight, defining the real orbital macro-drift of the planetary ring.
* **360° Omnidirectional Cyclones & Tactical Formations:** Every 25–35 seconds, complex high-density formations slice across the sector from arbitrary 360° angles (Meteor Lances, Comet Swarms, Dual Rails, Expanding Fans, and Megalith Packs). They can duplicate and converge from 1, 2, or 3 simultaneous angles!
* **5% Targeted Sniper Torrents:** Any formation stream has a 5% chance to lock its vector directly across the position of an alive ship (player flagship, enemy cruiser, or ally) with high-priority visual warnings and combat log alerts!
* **Sparkling Kinetic Trails:** Optimized particle glints and vapor tails (golden sparks, cyan comet dust, crimson plasma for sniper locks) with zero FPS drop.
* **Rock-Solid 120 FPS at up to 1000 Asteroids:** Immediate off-grid despawning, strict ray-rectangle boundary intersection, and zero border clutter.
* **Refit Simulator Live Controls:** Press `[/]` to toggle streams ON/OFF; press `[Shift +]` / `[Shift -]` to adjust rock capacity on the fly (+-50 rocks) for stress testing.

---

<a id="how-it-works"></a>
## ⚙️ How It Works

* **Strict Terrain Detection:** Uses the campaign world map to strictly check if your fleet is inside an asteroid belt, field, or planetary ring (`ASTEROID_BELT`, `ASTEROID_FIELD`, `RING`).
* **Asteroid Fields & Planetary Rings (100–1000 Rocks Configurable):**
  * **Continuous Avalanche:** Steady ambient river flows across the battlefield in one constant cardinal direction (West, East, North, South).
  * **Tactical Formation Waves:** Every 25–35 seconds, complex formations rush across from arbitrary 360° angles, duplicating into 1, 2, or 3 concurrent intersecting streams.
  * **Sniper Torrent (5% Chance):** Locks vector directly through an alive ship with critical on-screen and combat log alerts.
  * **Sparkling Kinetic Trails:** Cinematic kinetic sparks without performance penalty.
* **Deep Space / Open Space (Outside Asteroid Belts):**
  * **Quiet & Atmospheric:** Only 4 to 10 stray solitary rocks drifting gently at safe speeds (~320–480 u/s).
  * **No sirens or emergency alarms:** Keeps open space calm and peaceful.
* **Refit Simulator:**
  * Fully active with hotkey controls `[/]` and `[Shift +/-]` to test ship defenses and stress-test performance.
* **Anti-Brake Cruise Control:** Prevents physics engine collision dampening from stalling the rocks — asteroids maintain speed after collisions.
* **60-Second Lifespan:** Asteroids expire gracefully with a soft dust puff after 60 seconds, preserving FPS while sustaining deep tactical persistence.
* **Mid-Campaign Safe:** 100% safe to add or remove at any point in an ongoing campaign (pure combat engine plugin, writes zero data to save files).

---

## 🎮 Simulator Hotkeys (Simulator Only)

| Hotkey | Action | Description |
| :--- | :--- | :--- |
| `[/]` (Slash) | **Toggle Stream** | Instantly turns asteroid streams ON or OFF in simulator battles. |
| `[Shift +]` | **Increase Limit (+50)** | Increases active battle asteroid limit (+50 rocks) for stress-testing. |
| `[Shift -]` | **Decrease Limit (-50)** | Decreases active battle asteroid limit (-50 rocks). |

> [!NOTE]
> Simulator controls are strictly blocked during campaign battles to prevent exploits.

---

## 🛠️ Configuration & LunaLib

All parameters can be tuned in real-time in-game via **LunaLib (F2)** or configured in:  
`mods/DynamicAsteroids/data/config/dynamic_asteroids_settings.json`

| Setting | Default | Description |
| :--- | :---: | :--- |
| `minFieldRocks` | `100` | Minimum asteroid density rolled at battle start. |
| `maxFieldRocks` | `1000` | Maximum concurrent asteroids in asteroid belts (up to 2000 in LunaLib). |
| `fieldSurgeSpeed` | `3000.0` / `600.0` | Flight velocity of asteroids (3000 for Extreme, 600 for Standard). |
| `asteroidLifespan` | `60.0` | Seconds before an asteroid dissolves into smoke (now 60s for enhanced persistence). |
| `enableParticleTrails`| `true` | Enables sparkling kinetic tails behind flying asteroids. |
| `particleTrailChance` | `0.20` | Fraction of rocks that emit sparkling particle tails (20%). |
| `targetedSnipeChance` | `0.05` | Chance for incoming streams to lock course directly through a ship (5%). |
| `beamWidth` | `650.0` | Corridor width (units) for concentrated meteor lances. |
| `figureEventInterval`| `28.0` | Pacing interval (25–35s) between dynamic formation waves. |
| `openSpaceMinRocks` | `4` | Minimum stray meteors in calm deep space. |
| `openSpaceMaxRocks` | `10` | Maximum stray meteors in calm deep space. |

---

## 📥 Installation

1. Download your preferred `.zip` from the **[Releases](https://github.com/keltuze/DynamicAsteroids/releases/latest)** tab.
2. Extract the archive into your `Starsector/mods/` directory (it extracts into a folder named `DynamicAsteroids`).
3. Enable **Dynamic Asteroids** in the Starsector launcher mod menu.

---

<details>
<summary><b>🇷🇺 Русское описание / Russian Overview (Нажмите, чтобы развернуть)</b></summary>

### Dynamic Asteroids: Extreme Edition 2.0 (Cyclone Edition)

Серьезное обновление боевой динамики астероидных полей:

* **Живой пояс астероидов:** Диапазон потока в бою настраивается от 100 до 1000 астероидов, а время жизни увеличено до 60 секунд! Непрерывная фоновая лавина течёт строго в одном генеральном направлении на протяжении всего боя, задавая реальный макро-дрейф планетного кольца.
* **Циклоны, антициклоны и 360° траектории:** Каждые 25–35 секунд в сектор врываются крупные тактические формации (сверхплотные метеоритные лучи, гигантские кометные рои, двойные рельсы, веера и мегалиты). Они могут пролетать под любыми произвольными углами и дублироваться сразу с 2 или 3 направлений!
* **Снайперский захват цели (шанс 5%):** Любой вылетающий поток с вероятностью 5% может взять вектор строго сквозь координаты любого случайного живого корабля на карте (флагмана игрока, вражеского крейсера или союзника) с тревожным предупреждением на экране!
* **Искрящиеся кинетические шлейфы:** Часть камней оставляет сверкающие хвосты искр (золотые искры, бирюзовый кометный след, багровая плазма для прицельных залпов).
* **Честные 100–1000 астероидов при 120 FPS:** Полная оптимизация, мгновенный деспавн за пределами сетки и нулевая просадка кадров.

**Горячие клавиши в симуляторе:**
* `[/]` (Слэш) — моментальное отключение/включение астероидов.
* `[Shift +]` / `[Shift -]` — изменение лимита астероидов (+-50) прямо на лету для стресс-теста нагрузки! *(Работает только в симуляторе)*

</details>

---

## 🤖 AI Transparency Disclosure

* All underlying Java code in this mod was machine-generated through iterative architectural prompting and dialogue with an AI assistant (Google Antigravity / Gemini).
* The human author acted as the creative director, gameplay designer, and QA tester: identifying physics bounds, diagnosing Starsector engine collision quirks, designing terrain detection logic, evaluating save file safety, and directing multiple architectural refactors for lightweight performance.

---

**Author:** keltuze  
**Contact (Discord):** `keltuze`