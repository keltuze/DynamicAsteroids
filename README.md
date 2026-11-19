# 🌌 Dynamic Asteroids for Starsector

[![Game Version](https://img.shields.io/badge/Starsector-0.98.5a--RC4%20%7C%200.98.5a-blue.svg)](https://fractalsoftworks.com/)
[![Java Compatibility](https://img.shields.io/badge/Java-8%20(Bytecode%2052.0)-green.svg)]()
[![LunaLib](https://img.shields.io/badge/LunaLib-Required-orange.svg)]()
[![Latest Release](https://img.shields.io/badge/Release-v2.1.0-brightgreen.svg)](https://github.com/keltuze/DynamicAsteroids/releases/tag/v2.1.0)

> **Breathes life into static asteroid belts and deep space combat!**  
> In vanilla Starsector, asteroids just hang motionless in space. **Dynamic Asteroids** transforms them into rushing kinetic rivers of space debris, derelict recovery operations, interactive salvage gathering, and tactical survival challenges during combat.  
> **Version 2.1.0** features a complete architectural overhaul and Zero-Allocation combat loop (over 3,600 lines of dead code removed, zero GC stutter), 50% rebalanced cinematic Megalith speeds, disciplined Point Defense fire discipline (PD guns strictly prioritize threats and ignore colossal megaliths), smooth hull-collection for shield magnet [T], and full Starsector 0.98.5a compatibility.

---

## 📦 Downloads (v2.1.0 Release)

Direct download links for the latest version. Both editions contain all 5 live presets (from Cinematic soft drift to hypervelocity Chaos):

| Edition | Language | Highlights & Features | Direct Download |
| :--- | :---: | :--- | :--- |
| **Russian Edition (RU)** | 🇷🇺 Русский | Полная русская локализация (интерфейс, LunaLib, боевые тревоги), гарантированная выдача остовов во флот, дефлекторные купола кораблей на старте и подкреплениях, шрифты для LunaLib | [**`DynamicAsteroids_RU_v2.1.0.zip`**](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.1.0/DynamicAsteroids_RU_v2.1.0.zip) |
| **Standard Edition (EN)** | 🇬🇧 English | Full English edition, guaranteed combat derelict ship delivery to fleet, ship deployment deflection domes, directional shield tractor beam, Domain caches, smart PD whitelist | [**`DynamicAsteroids_v2.1.0.zip`**](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.1.0/DynamicAsteroids_v2.1.0.zip) |

> 🔗 All release assets and past versions are available on the [**GitHub Releases Page**](https://github.com/keltuze/DynamicAsteroids/releases).

---

## 🚀 What's New in v2.1.0

### 🧹 1. Архитектурная чистота и Zero-Allocation / Architecture Cleanup
* **3,600+ строк мертвого кода удалено:** Полный аудит кодовой базы, устранение дублирующей логики и неиспользуемых процедур.
* **Zero-Allocation в цикле боя (`advance`):** Устранены покадровые аллокации промежуточных списков и объектов — минимальная нагрузка на сборщик мусора Java (GC) и стабильный FPS в затяжных боях.

### 🪨 2. Ребаланс колоссальных мегалитов (-50% скорости) / Megalith Rebalance
* **Кинематографичная скорость:** Средняя скорость мегалитов снижена на 50% (в сбалансированном режиме — с 90 до 45 ед/с). Колоссальные глыбы ощущаются по-настоящему массивными, а у игрока и флота есть время среагировать на угрозу тарана.
* **Драйв обычных потоков сохранен:** Скорости основных астероидных рек, штормовых формаций и находок остались прежними (400–3000 ед/с) — экшен поясов никуда не пропал.

### 🚫 3. Дисциплина огня ПРО / Disciplined Point Defense
* **ПРО не отвлекается на мегалиты:** Орудия точечной защиты (Вулканы, Флаки, лазеры ПРО) больше не выбирают гигантские монолиты в качестве целей, не сливают в них боезапас и не забивают флакс. ПРО сфокусировано на ракетах, бомбардировщиках и мелких сбиваемых камнях.
* **Главный калибр по-прежнему эффективен:** Обычные орудия и ручной огонь игрока могут раскалывать мегалиты, как и прежде.

### 🧲 4. Плавное поглощение лута щит-магнитом [T] / Smooth Shield Magnet
* **Плавный сбор:** Полезные контейнеры (тайники, зонды, криокапсулы, чипы истребителей, металл) беспрепятственно втягиваются внутрь контура щита и подбираются корпусом без зависания у кромки поля.
* **Безопасность остовов:** Заброшенные корабли (`DERELICT`) по-прежнему бережно швартуются у кромки щита на безопасной дистанции без риска повреждения корпуса.

### 🎁 5. Баланс наград и кулдаун дропа / Asteroid Loot Balancing
* **Кулдаун 60 секунд:** Уничтожение мелких астероидов теперь дает дроп не чаще одного раза в 60 секунд.
* **Оптимизация проверки снарядов:** Опрос снарядов игрока ограничен до 5 раз в секунду (вместо 60), снижая нагрузку на движок на 92%. Мегалиты по-прежнему дают щедрый независимый лут при разрушении.

### 🛡️ 6. Защита омни-щитами и синхронизация LunaLib
* **Удержание щитов ИИ:** Исправлен таймер удержания щитов союзного и вражеского ИИ при сближении с колоссальными глыбами.
* **Полная синхронизация LunaLib:** Описания пресетов и параметров в интерфейсе настроек согласованы с актуальной физикой.

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

1. Download [`DynamicAsteroids_RU_v2.1.0.zip`](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.1.0/DynamicAsteroids_RU_v2.1.0.zip) (Russian) or [`DynamicAsteroids_v2.1.0.zip`](https://github.com/keltuze/DynamicAsteroids/releases/download/v2.1.0/DynamicAsteroids_v2.1.0.zip) (English).
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
