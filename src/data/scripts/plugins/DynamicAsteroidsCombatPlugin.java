package data.scripts.plugins;

import java.awt.Color;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.json.JSONObject;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.input.InputEventAPI;

/**
 * Dynamic Asteroids V2 (Русская версия) - Extreme Edition
 * Непрерывный гиперзвуковой поток астероидов в полях (3000 ед/сек)
 * и редкие нерегулярные метеоры в открытом космосе.
 */
public class DynamicAsteroidsCombatPlugin extends BaseEveryFrameCombatPlugin {

    public enum StreamDirection {
        RIGHT_TO_LEFT,  // Влево (<-)
        LEFT_TO_RIGHT,  // Вправо (->)
        BOTTOM_TO_TOP,  // Вверх (^)
        TOP_TO_BOTTOM   // Вниз (v)
    }

    private CombatEngineAPI engine;
    private boolean active = false;
    private boolean isTitleScreen = false;
    private boolean isInAsteroidField = false;
    private Random random = new Random();

    // Настройки плотного потока (Астероидное поле)
    private int minFieldRocks = 70;
    private int maxFieldRocks = 160;
    private float streamSpeed = 3000.0f;       // Гиперзвуковая скорость (3000 ед/сек)
    private float asteroidLifespan = 30.0f;    // 30 секунд жизни
    private float fieldSpawnInterval = 0.025f; // Быстрый спавн сплошного потока

    // Настройки открытого космоса (Вне астероидных полей)
    private int openSpaceMinRocks = 4;
    private int openSpaceMaxRocks = 10;
    private float openSpaceMinDelay = 1.5f;
    private float openSpaceMaxDelay = 5.0f;

    // Состояние текущего боя
    private int battleTargetCount = 110;
    private StreamDirection streamDirection = StreamDirection.RIGHT_TO_LEFT;
    private float currentSpawnDelay = 0.025f;
    private float spawnTimer = 0f;
    private float totalCombatTime = 0f;
    private boolean notifiedPlayer = false;
    private float notifyTimer = 0f;

    private static final String SETTINGS_PATH = "data/config/dynamic_asteroids_settings.json";
    private static final String MOD_ID = "dynamic_asteroids";
    private static final String TRACKED_KEY = "da_v2_rock";
    private static final String SPAWN_TIME_KEY = "da_v2_birth";

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        this.active = false;
        this.spawnTimer = 0f;
        this.totalCombatTime = 0f;
        this.notifiedPlayer = false;
        this.notifyTimer = 0f;

        if (engine == null) return;

        // Проверка главного меню / титульного экрана
        isTitleScreen = false;
        try {
            if (Global.getCurrentState() == GameState.TITLE) {
                isTitleScreen = true;
            } else if (!engine.isInCampaign() && !engine.isSimulation() && !engine.isMission()) {
                isTitleScreen = true;
            }
        } catch (Throwable t) {
            isTitleScreen = true;
        }

        // 1. Очищаем любые неподвижные ванильные астероиды со старта боя
        List<CombatEntityAPI> existing = engine.getAsteroids();
        if (existing != null && !existing.isEmpty()) {
            for (CombatEntityAPI ast : existing) {
                if (ast != null) {
                    engine.removeEntity(ast);
                }
            }
        }

        // 2. Строгая проверка террейна на глобальной карте кампании
        boolean campaignTerrainHasAsteroids = false;
        try {
            if (engine.isInCampaign() && Global.getSector() != null && Global.getSector().getPlayerFleet() != null) {
                CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
                LocationAPI loc = playerFleet.getContainingLocation();
                if (loc != null) {
                    List<CampaignTerrainAPI> terrains = loc.getTerrainCopy();
                    if (terrains != null) {
                        Vector2f playerLoc = playerFleet.getLocation();
                        for (CampaignTerrainAPI terrain : terrains) {
                            if (terrain == null) continue;
                            String type = terrain.getType();
                            String terrainId = (terrain.getPlugin() != null) ? terrain.getPlugin().getTerrainId() : null;

                            boolean isAsteroidTerrain = false;
                            if (type != null) {
                                String tLower = type.toLowerCase();
                                if (tLower.contains("asteroid") || tLower.contains("ring")) {
                                    isAsteroidTerrain = true;
                                }
                            }
                            if (terrainId != null) {
                                String idLower = terrainId.toLowerCase();
                                if (idLower.contains("asteroid") || idLower.contains("ring")) {
                                    isAsteroidTerrain = true;
                                }
                            }

                            if (isAsteroidTerrain && terrain.getPlugin() != null) {
                                if (terrain.getPlugin().containsEntity(playerFleet) || 
                                    terrain.getPlugin().containsPoint(playerLoc, 600f)) {
                                    campaignTerrainHasAsteroids = true;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
        }

        // 3. Определяем, является ли данный бой боем в астероидном поле.
        // Опираемся СТРОГО на глобальную карту мира (не верим случайным спавнам ваниллы в космосе).
        if (isTitleScreen) {
            isInAsteroidField = true;
        } else if (engine.isSimulation()) {
            isInAsteroidField = true; // Симулятор подгонки кораблей всегда моделирует поле
        } else if (engine.isInCampaign()) {
            isInAsteroidField = campaignTerrainHasAsteroids; // Строго террейн пояса/кольца на карте!
        } else {
            isInAsteroidField = false; // Открытый космос
        }

        loadSettings();

        // 4. Случайное направление на весь бой (25% каждое)
        int dirChoice = random.nextInt(4);
        switch (dirChoice) {
            case 0: streamDirection = StreamDirection.RIGHT_TO_LEFT; break; // Влево
            case 1: streamDirection = StreamDirection.LEFT_TO_RIGHT; break; // Вправо
            case 2: streamDirection = StreamDirection.BOTTOM_TO_TOP; break; // Вверх
            case 3: streamDirection = StreamDirection.TOP_TO_BOTTOM; break; // Вниз
        }

        // 5. Расчет количества камней и стартового интервала спавна
        if (isInAsteroidField) {
            int minR = Math.min(minFieldRocks, maxFieldRocks);
            int maxR = Math.max(minFieldRocks, maxFieldRocks);
            battleTargetCount = minR + random.nextInt(Math.max(1, maxR - minR + 1));
            currentSpawnDelay = fieldSpawnInterval;
        } else {
            int minR = Math.min(openSpaceMinRocks, openSpaceMaxRocks);
            int maxR = Math.max(openSpaceMinRocks, openSpaceMaxRocks);
            battleTargetCount = minR + random.nextInt(Math.max(1, maxR - minR + 1));
            // В открытом космосе: первая задержка случайна, чтобы камни не летели со старта волной
            currentSpawnDelay = openSpaceMinDelay + random.nextFloat() * (openSpaceMaxDelay - openSpaceMinDelay);
        }

        this.active = true;
    }

    private void loadSettings() {
        // 1. JSON конфиг
        try {
            JSONObject config = Global.getSettings().loadJSON(SETTINGS_PATH);
            if (config != null) {
                minFieldRocks = config.optInt("minFieldRocks", minFieldRocks);
                maxFieldRocks = config.optInt("maxFieldRocks", maxFieldRocks);
                streamSpeed = (float) config.optDouble("fieldSurgeSpeed", streamSpeed);
                asteroidLifespan = (float) config.optDouble("asteroidLifespan", asteroidLifespan);
                openSpaceMinRocks = config.optInt("openSpaceMinRocks", openSpaceMinRocks);
                openSpaceMaxRocks = config.optInt("openSpaceMaxRocks", openSpaceMaxRocks);
                openSpaceMinDelay = (float) config.optDouble("openSpaceMinDelay", openSpaceMinDelay);
                openSpaceMaxDelay = (float) config.optDouble("openSpaceMaxDelay", openSpaceMaxDelay);
            }
        } catch (Throwable t) {
        }

        // 2. Переопределения LunaLib (F2)
        try {
            if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
                Class<?> clazz = Class.forName("lunalib.lunaSettings.LunaSettings");
                Method method = null;
                try {
                    method = clazz.getMethod("getFloat", String.class, String.class);
                } catch (NoSuchMethodException e) {
                    try {
                        method = clazz.getMethod("getDouble", String.class, String.class);
                    } catch (NoSuchMethodException e2) {
                    }
                }
                if (method != null) {
                    Object spdVal = method.invoke(null, MOD_ID, "da_fieldSurgeSpeed");
                    if (spdVal instanceof Number) {
                        streamSpeed = ((Number) spdVal).floatValue();
                    }
                    Object val = method.invoke(null, MOD_ID, "da_asteroidLifespan");
                    if (val instanceof Number) {
                        asteroidLifespan = ((Number) val).floatValue();
                    }
                    Object minVal = method.invoke(null, MOD_ID, "da_minFieldRocks");
                    if (minVal instanceof Number) {
                        minFieldRocks = ((Number) minVal).intValue();
                    }
                    Object maxVal = method.invoke(null, MOD_ID, "da_maxFieldRocks");
                    if (maxVal instanceof Number) {
                        maxFieldRocks = ((Number) maxVal).intValue();
                    }
                    Object osMinVal = method.invoke(null, MOD_ID, "da_openSpaceMinRocks");
                    if (osMinVal instanceof Number) {
                        openSpaceMinRocks = ((Number) osMinVal).intValue();
                    }
                    Object osMaxVal = method.invoke(null, MOD_ID, "da_openSpaceMaxRocks");
                    if (osMaxVal instanceof Number) {
                        openSpaceMaxRocks = ((Number) osMaxVal).intValue();
                    }
                    Object osMinDVal = method.invoke(null, MOD_ID, "da_openSpaceMinDelay");
                    if (osMinDVal instanceof Number) {
                        openSpaceMinDelay = ((Number) osMinDVal).floatValue();
                    }
                    Object osMaxDVal = method.invoke(null, MOD_ID, "da_openSpaceMaxDelay");
                    if (osMaxDVal instanceof Number) {
                        openSpaceMaxDelay = ((Number) osMaxDVal).floatValue();
                    }
                }
            }
        } catch (Throwable t) {
        }

        if (streamSpeed > 5000f) streamSpeed = 5000f;
        if (minFieldRocks < 10) minFieldRocks = 70;
        if (maxFieldRocks < minFieldRocks) maxFieldRocks = minFieldRocks + 30;
        if (asteroidLifespan <= 0f) asteroidLifespan = 30.0f;
        if (openSpaceMinRocks < 1) openSpaceMinRocks = 4;
        if (openSpaceMaxRocks < openSpaceMinRocks) openSpaceMaxRocks = openSpaceMinRocks + 5;
        if (openSpaceMinDelay <= 0f) openSpaceMinDelay = 1.5f;
        if (openSpaceMaxDelay < openSpaceMinDelay) openSpaceMaxDelay = openSpaceMinDelay + 3.0f;
    }

    private ShipAPI getPlayerShipSafely() {
        try {
            if (engine != null) {
                return engine.getPlayerShip();
            }
        } catch (Throwable t) {
        }
        return null;
    }

    private String getDirectionLabelEn() {
        switch (streamDirection) {
            case RIGHT_TO_LEFT: return "Right -> Left (West)";
            case LEFT_TO_RIGHT: return "Left -> Right (East)";
            case BOTTOM_TO_TOP: return "Bottom -> Top (North)";
            case TOP_TO_BOTTOM: return "Top -> Bottom (South)";
        }
        return "Right -> Left";
    }

    private String getDirectionLabelRu() {
        switch (streamDirection) {
            case RIGHT_TO_LEFT: return "Справа налево (Запад)";
            case LEFT_TO_RIGHT: return "Слева направо (Восток)";
            case BOTTOM_TO_TOP: return "Снизу вверх (Север)";
            case TOP_TO_BOTTOM: return "Сверху вниз (Юг)";
        }
        return "Справа налево";
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (!active || engine == null) return;

        totalCombatTime += amount;

        // 1. Однократное оповещение на старте боя
        if (!isTitleScreen && !notifiedPlayer) {
            ShipAPI player = getPlayerShipSafely();
            if (player != null) {
                notifyTimer += amount;
                if (notifyTimer >= 0.4f) {
                    notifiedPlayer = true;
                    showNotification(player);
                }
            }
        }

        // 2. Постоянный статус в нижнем левом углу на панели флагмана (HUD)
        if (!isTitleScreen && getPlayerShipSafely() != null) {
            try {
                String icon = Global.getSettings().getSpriteName("ui", "icon_fleet_danger");
                if (isInAsteroidField) {
                    engine.maintainStatusForPlayerShip(
                        "DYNAMIC_ASTEROIDS_STATUS",
                        icon,
                        "ОПАСНОСТЬ: АСТЕРОИДНЫЙ ШТОРМ",
                        "Поток: " + getDirectionLabelRu() + " | В поле: " + battleTargetCount + " камней",
                        true
                    );
                } else {
                    engine.maintainStatusForPlayerShip(
                        "DYNAMIC_ASTEROIDS_STATUS",
                        icon,
                        "ОТКРЫТЫЙ КОСМОС",
                        "Дрейф: " + getDirectionLabelRu() + " | Редкие метеоры: " + battleTargetCount + " шт.",
                        false
                    );
                }
            } catch (Throwable t) {
            }
        }

        // Пауза во время боя
        if (!isTitleScreen && engine.isPaused()) {
            return;
        }

        // Главное меню: фоновое перемещение
        if (isTitleScreen) {
            List<CombatEntityAPI> list = engine.getAsteroids();
            if (list != null) {
                for (CombatEntityAPI ast : list) {
                    if (ast == null || ast.isExpired()) continue;
                    Vector2f loc = ast.getLocation();
                    Vector2f vel = ast.getVelocity();
                    if (loc != null && vel != null) {
                        loc.x += vel.x * amount;
                        loc.y += vel.y * amount;
                        ast.setFacing(ast.getFacing() + ast.getAngularVelocity() * amount);
                    }
                }
            }
        }

        // 3. Вычисление точных границ видимой зоны (учитывает смещение камеры в меню!)
        float focalCX = 0f;
        float focalCY = 0f;
        float halfW = engine.getMapWidth() * 0.5f;
        float halfH = engine.getMapHeight() * 0.5f;

        if (isTitleScreen) {
            ViewportAPI vp = engine.getViewport();
            if (vp != null && vp.getCenter() != null) {
                focalCX = vp.getCenter().x;
                focalCY = vp.getCenter().y;
            }
            float vw = (vp != null) ? vp.getVisibleWidth() : 2500f;
            float vh = (vp != null) ? vp.getVisibleHeight() : 1500f;
            halfW = vw * 0.5f;
            halfH = vh * 0.5f;
        }

        float boundMargin = isTitleScreen ? 400f : 1200f;
        float minX = focalCX - halfW - boundMargin;
        float maxX = focalCX + halfW + boundMargin;
        float minY = focalCY - halfH - boundMargin;
        float maxY = focalCY + halfH + boundMargin;

        // 4. Обновление существующих астероидов
        List<CombatEntityAPI> asteroids = engine.getAsteroids();
        int activeCount = 0;

        if (asteroids != null && !asteroids.isEmpty()) {
            List<CombatEntityAPI> toRemove = null;
            List<CombatEntityAPI> toPoof = null;

            for (int i = 0; i < asteroids.size(); i++) {
                CombatEntityAPI ast = asteroids.get(i);
                if (ast == null || ast.isExpired()) continue;

                Vector2f loc = ast.getLocation();
                if (loc == null) continue;

                // Удаляем случайные ванильные камни без нашей метки
                if (ast.getCustomData().get(TRACKED_KEY) == null) {
                    if (toRemove == null) toRemove = new ArrayList<CombatEntityAPI>();
                    toRemove.add(ast);
                    continue;
                }

                // Проверка вылета за границу экрана/карты по текущему вектору потока
                boolean outOfBounds = false;
                switch (streamDirection) {
                    case RIGHT_TO_LEFT:
                        if (loc.x < minX) outOfBounds = true;
                        break;
                    case LEFT_TO_RIGHT:
                        if (loc.x > maxX) outOfBounds = true;
                        break;
                    case BOTTOM_TO_TOP:
                        if (loc.y > maxY) outOfBounds = true;
                        break;
                    case TOP_TO_BOTTOM:
                        if (loc.y < minY) outOfBounds = true;
                        break;
                }

                // Боковой улет слишком далеко
                if (loc.x < minX - 2000f || loc.x > maxX + 2000f || loc.y < minY - 2000f || loc.y > maxY + 2000f) {
                    outOfBounds = true;
                }

                if (outOfBounds) {
                    if (toRemove == null) toRemove = new ArrayList<CombatEntityAPI>();
                    toRemove.add(ast);
                    continue;
                }

                // Время жизни: ровно указанное время (30 секунд)
                Object birthObj = ast.getCustomData().get(SPAWN_TIME_KEY);
                if (birthObj instanceof Float) {
                    float age = totalCombatTime - ((Float) birthObj).floatValue();
                    if (age >= asteroidLifespan) {
                        if (toPoof == null) toPoof = new ArrayList<CombatEntityAPI>();
                        toPoof.add(ast);
                        continue;
                    }
                }

                // КРУИЗ-КОНТРОЛЬ СКОРОСТИ (Анти-торможение):
                Vector2f vel = ast.getVelocity();
                if (vel != null) {
                    float brakeThreshold = streamSpeed * 0.85f;
                    switch (streamDirection) {
                        case RIGHT_TO_LEFT:
                            if (vel.x > -brakeThreshold) vel.x = -streamSpeed;
                            if (Math.abs(vel.y) > 120.0f) vel.y = Math.signum(vel.y) * 50.0f;
                            break;
                        case LEFT_TO_RIGHT:
                            if (vel.x < brakeThreshold) vel.x = streamSpeed;
                            if (Math.abs(vel.y) > 120.0f) vel.y = Math.signum(vel.y) * 50.0f;
                            break;
                        case BOTTOM_TO_TOP:
                            if (vel.y < brakeThreshold) vel.y = streamSpeed;
                            if (Math.abs(vel.x) > 120.0f) vel.x = Math.signum(vel.x) * 50.0f;
                            break;
                        case TOP_TO_BOTTOM:
                            if (vel.y > -brakeThreshold) vel.y = -streamSpeed;
                            if (Math.abs(vel.x) > 120.0f) vel.x = Math.signum(vel.x) * 50.0f;
                            break;
                    }
                }

                activeCount++;
            }

            // Мягкое растворение в пыль по истечении времени жизни
            if (toPoof != null) {
                Color dustColor = new Color(130, 115, 100, 110);
                for (CombatEntityAPI dead : toPoof) {
                    if (dead != null && dead.getLocation() != null && !isTitleScreen) {
                        float radius = Math.max(25f, dead.getCollisionRadius());
                        Vector2f puffVel = new Vector2f(0f, 0f);
                        switch (streamDirection) {
                            case RIGHT_TO_LEFT: puffVel.x = -streamSpeed * 0.15f; break;
                            case LEFT_TO_RIGHT:  puffVel.x = streamSpeed * 0.15f; break;
                            case BOTTOM_TO_TOP: puffVel.y = streamSpeed * 0.15f; break;
                            case TOP_TO_BOTTOM: puffVel.y = -streamSpeed * 0.15f; break;
                        }
                        engine.addSmokeParticle(dead.getLocation(), puffVel, radius * 0.8f, 0.4f, 0.7f, dustColor);
                    }
                    engine.removeEntity(dead);
                }
            }

            // Бесследное удаление улетевших за границы
            if (toRemove != null) {
                for (CombatEntityAPI dead : toRemove) {
                    engine.removeEntity(dead);
                }
            }
        }

        // 5. Поочерёдный спавн с учетом типа локации
        int targetLimit = isTitleScreen ? 25 : battleTargetCount;
        spawnTimer += amount;
        if (spawnTimer >= currentSpawnDelay) {
            spawnTimer = 0f;

            if (activeCount < targetLimit) {
                spawnOneAsteroid(focalCX, focalCY, halfW, halfH);
            }

            // Следующая пауза спавна:
            if (isTitleScreen || isInAsteroidField) {
                currentSpawnDelay = fieldSpawnInterval;
            } else {
                // В открытом космосе: сильно разный диапазон времени спавна (чтобы не шли волной)
                currentSpawnDelay = openSpaceMinDelay + random.nextFloat() * (openSpaceMaxDelay - openSpaceMinDelay);
            }
        }
    }

    /**
     * Спавнит ровно 1 астероид вдоль границы со скоростью 3000 ед/сек.
     */
    private void spawnOneAsteroid(float focalCX, float focalCY, float halfW, float halfH) {
        float spawnX = 0f;
        float spawnY = 0f;
        float vx = 0f;
        float vy = 0f;

        float offset = isTitleScreen ? 150f : 500f;
        float jitter = isTitleScreen ? 250f : 1000f;

        switch (streamDirection) {
            case RIGHT_TO_LEFT:
                spawnX = focalCX + halfW + offset + random.nextFloat() * jitter;
                spawnY = focalCY + (random.nextFloat() - 0.5f) * (halfH * 2.1f);
                vx = -streamSpeed;
                vy = (random.nextFloat() - 0.5f) * 50.0f;
                break;

            case LEFT_TO_RIGHT:
                spawnX = focalCX - halfW - offset - random.nextFloat() * jitter;
                spawnY = focalCY + (random.nextFloat() - 0.5f) * (halfH * 2.1f);
                vx = streamSpeed;
                vy = (random.nextFloat() - 0.5f) * 50.0f;
                break;

            case BOTTOM_TO_TOP:
                spawnX = focalCX + (random.nextFloat() - 0.5f) * (halfW * 2.1f);
                spawnY = focalCY - halfH - offset - random.nextFloat() * jitter;
                vx = (random.nextFloat() - 0.5f) * 50.0f;
                vy = streamSpeed;
                break;

            case TOP_TO_BOTTOM:
                spawnX = focalCX + (random.nextFloat() - 0.5f) * (halfW * 2.1f);
                spawnY = focalCY + halfH + offset + random.nextFloat() * jitter;
                vx = (random.nextFloat() - 0.5f) * 50.0f;
                vy = -streamSpeed;
                break;
        }

        float r = random.nextFloat();
        int type = 0;
        if (r < 0.45f) type = 0;
        else if (r < 0.75f) type = 1;
        else if (r < 0.93f) type = 2;
        else type = 3;

        CombatEntityAPI ast = engine.spawnAsteroid(type, spawnX, spawnY, vx, vy);
        if (ast != null) {
            ast.getVelocity().set(vx, vy);
            ast.setFacing(random.nextFloat() * 360f);
            ast.setAngularVelocity((random.nextFloat() - 0.5f) * 50f);

            ast.setCustomData(TRACKED_KEY, Boolean.TRUE);
            ast.setCustomData(SPAWN_TIME_KEY, Float.valueOf(totalCombatTime));
        }
    }

    private void showNotification(ShipAPI player) {
        try {
            System.out.println(">>> DynamicAsteroids: IN_FIELD=" + isInAsteroidField + ", DIRECTION=" + streamDirection + ", TARGET_COUNT=" + battleTargetCount + " <<<");

            if (isInAsteroidField) {
                // Всплывающий текст над флагманом игрока (тревожный красный)
                engine.addFloatingText(
                    player.getLocation(),
                    "ОПАСНОСТЬ: АСТЕРОИДНЫЙ ШТОРМ (" + getDirectionLabelRu() + ", " + battleTargetCount + " шт.)!",
                    26f,
                    new Color(255, 65, 65),
                    player,
                    0.35f,
                    3.5f
                );

                // Журнал сообщений боя (вверху слева) - латиница
                if (engine.getCombatUI() != null) {
                    engine.getCombatUI().addMessage(1, "HAZARD: Asteroid Stream! Flow: " + getDirectionLabelEn() + " | Field: " + battleTargetCount + " rocks");
                }
            } else {
                // В открытом космосе: без тревожной сирены, аккуратная запись в журнал боя
                if (engine.getCombatUI() != null) {
                    engine.getCombatUI().addMessage(1, "Dynamic Asteroids: Deep space - stray meteors (" + battleTargetCount + " rocks)");
                }
            }
        } catch (Throwable t) {
        }
    }
}
