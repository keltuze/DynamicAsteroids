# ☄️ Dynamic Asteroids for Starsector

[![Game Version](https://img.shields.io/badge/Starsector-0.98a--RC8%20%7C%200.97a-blue.svg)](https://fractalsoftworks.com/)
[![Java Compatibility](https://img.shields.io/badge/Java-8%20(Bytecode%2052.0)-green.svg)]()
[![LunaLib](https://img.shields.io/badge/LunaLib-Compatible-orange.svg)]()
[![Version](https://img.shields.io/badge/Version-2.0.0%20(Cyclone%20Edition)-red.svg)](https://github.com/keltuze/DynamicAsteroids/releases/latest)

> **Breathes life into static asteroid belts!**  
> In vanilla Starsector, asteroids just hang motionless in space. **Dynamic Asteroids** turns them into active, rushing rivers of space debris during combat.

---

## 🌟 Dynamic Asteroids: Extreme Edition 2.0 (Cyclone Edition)

### Серьезное обновление боевой динамики астероидных полей:

* **Живой пояс астероидов:** Непрерывная фоновая лавина течёт строго в одном генеральном направлении на протяжении всего боя, задавая реальный макро-дрейф планетного кольца.
* **Циклоны, антициклоны и 360° траектории:** Каждые 25–35 секунд в сектор врываются крупные тактические формации (сверхплотные метеоритные лучи, гигантские кометные рои, двойные рельсы, веера и мегалиты). Они могут пролетать под любыми произвольными углами и дублироваться сразу с 2 или 3 направлений!
* **Снайперский захват цели (шанс 5%):** Любой вылетающий поток с вероятностью 5% может взять вектор строго сквозь координаты любого случайного живого корабля на карте (флагмана игрока, вражеского крейсера или союзника) с тревожным предупреждением на экране!
* **Искрящиеся кинетические шлейфы:** Часть камней оставляет сверкающие хвосты искр (золотые искры, бирюзовый кометный след, багровая плазма для прицельных залпов).
* **Честные 300+ астероидов при 120 FPS:** Полная оптимизация, мгновенный деспавн за пределами сетки и нулевая просадка кадров.

#### Горячие клавиши в симуляторе:
* `[/]` **(Слэш)** — моментальное отключение/включение астероидов.
* `[Shift +]` / `[Shift -]` — изменение лимита астероидов (+-50) прямо на лету для стресс-теста нагрузки! *(Работает строго в симуляторе, в кампании заблокировано)*

---

## 📦 Downloads (Версии мода v2.0.0)

All versions are available in the **[Releases](https://github.com/keltuze/DynamicAsteroids/releases/latest)** tab:

| Edition / Версия | Lang | Speed / Скорость | Описание / Description | Download |
| :--- | :---: | :---: | :--- | :--- |
| **Extreme Edition 2.0 (Циклон)** | **RU** | **3000 u/s** | Сокрушительный кинетический импульс, циклоны 360°, 5% снайперские залпы, русский интерфейс | [Скачать ZIP](https://github.com/keltuze/DynamicAsteroids/releases/latest/download/DynamicAsteroids_Extreme_RU_v2.0.0.zip) |
| **Extreme Edition 2.0 (Cyclone)** | **EN** | **3000 u/s** | High-velocity kinetic storm, 360° cyclones, 5% sniper barrages, English UI | [Download ZIP](https://github.com/keltuze/DynamicAsteroids/releases/latest/download/DynamicAsteroids_Extreme_v2.0.0.zip) |
| **Standard Edition 2.0 (Циклон)** | **RU** | **600 u/s** | Безопасная скорость столкновений, циклоны 360°, 5% залпы, русский интерфейс | [Скачать ZIP](https://github.com/keltuze/DynamicAsteroids/releases/latest/download/DynamicAsteroids_RU_v2.0.0.zip) |
| **Standard Edition 2.0 (Cyclone)** | **EN** | **600 u/s** | Safe balanced velocity cap, 360° cyclones, 5% sniper barrages, English UI | [Download ZIP](https://github.com/keltuze/DynamicAsteroids/releases/latest/download/DynamicAsteroids_v2.0.0.zip) |

---

## ⚙️ How It Works / Как это работает

* **Terrain Detection:** Uses the campaign world map to strictly check if your fleet is inside an asteroid belt, field, or planetary ring (`ASTEROID_BELT`, `ASTEROID_FIELD`, `RING`).
* **Asteroid Fields / Belts (300 Rocks Max):**
  * **Continuous Avalanche:** Steady ambient river flows across the battlefield in one constant cardinal direction (West, East, North, South).
  * **Tactical Formation Waves:** Every 25–35 seconds, complex formations rush across from arbitrary 360° angles, duplicating into 1, 2, or 3 concurrent intersecting streams.
  * **Sniper Torrent (5% Chance):** Locks vector directly through an alive ship with critical on-screen and combat log alerts.
  * **Sparkling Trails:** Cinematic kinetic sparks without performance penalty.
* **Deep Space / Open Space (Вне поясов):**
  * **Quiet & Calm:** Only 4 to 10 stray solitary rocks drifting gently at safe speeds (~320–480 u/s).
  * **No sirens or tactical alarms:** Keeps open space atmospheric and peaceful.
* **Refit Simulator:**
  * Fully active with hotkey controls `[/]` and `[Shift +/-]` to test ship defenses and stress-test performance.
* **Anti-Brake Cruise Control:** Prevents physics engine collision dampening from stalling the rocks.
* **Mid-Campaign Safe:** 100% safe to add or remove at any point in an ongoing campaign (pure combat engine plugin, writes zero data to save files).

---

## 🛠️ Configuration & LunaLib

All parameters can be tuned in real-time in-game via **LunaLib (F2)** or configured in:  
`mods/DynamicAsteroids/data/config/dynamic_asteroids_settings.json`

---

## 📥 Installation / Установка

1. Скачайте нужный `.zip` из раздела **[Releases](https://github.com/keltuze/DynamicAsteroids/releases/latest)**.
2. Распакуйте архив в папку `Starsector/mods/` (внутри должна получиться папка `DynamicAsteroids`).
3. Включите **Dynamic Asteroids** в лаунчере игры.

---

## 🤖 AI Transparency Disclosure

* All underlying Java code in this mod was machine-generated through iterative architectural prompting and dialogue with an AI assistant (Google Antigravity / Gemini).
* The human author acted as the creative director, gameplay designer, and QA tester.

---

**Author:** keltuze  
**Contact (Discord):** `keltuze`