package data.scripts.plugins;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.json.JSONObject;
import org.lwjgl.input.Keyboard;
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
 * Dynamic Asteroids 2.0 (Cyclone Edition)
 */
public class DynamicAsteroidsCombatPlugin extends BaseEveryFrameCombatPlugin {

    // Configurable compilation flags
    private static final boolean IS_RUSSIAN = true;
    private static final float DEFAULT_STREAM_SPEED = 3000.0f;

    public enum StreamDirection {
        RIGHT_TO_LEFT,  // West (<-)
        LEFT_TO_RIGHT,  // East (->)
        BOTTOM_TO_TOP,  // North (^)
        TOP_TO_BOTTOM   // South (v)
    }

    public enum FigureType {
        LANCE_BEAM(
            IS_RUSSIAN ? "Метеоритный луч" : "Meteor Lance",
            IS_RUSSIAN ? "Плотный гиперзвуковой луч прорезает сектор" : "Dense hypervelocity beam cutting through the sector"
        ),
        COMET_SWARM(
            IS_RUSSIAN ? "Кометный рой" : "Comet Swarm",
            IS_RUSSIAN ? "Огромное плотное скопление астероидов" : "Massive dense cloud of tumbling asteroids"
        ),
        DUAL_RAILS(
            IS_RUSSIAN ? "Двойные рельсы" : "Dual Rails",
            IS_RUSSIAN ? "Два параллельных луча зажимают сектор" : "Twin parallel streams squeezing the combat zone"
        ),
        EXPANDING_FAN(
            IS_RUSSIAN ? "Веерный залп" : "Expanding Fan",
            IS_RUSSIAN ? "Конический веерный шквал астероидов" : "Spreading conical salvo of asteroids"
        ),
        HEAVY_MEGALITHS(
            IS_RUSSIAN ? "Пакет мегалитов" : "Megalith Pack",
            IS_RUSSIAN ? "Залп колоссальных мегалитов тяжелого класса" : "Salvo of colossal behemoth-class asteroids"
        );

        private final String displayName;
        private final String description;

        FigureType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    public static class ActiveFigureStream {
        public Vector2f origin = new Vector2f();
        public Vector2f velocity = new Vector2f();
        public Vector2f perp = new Vector2f();
        public FigureType type = FigureType.LANCE_BEAM;
        public int rocksRemaining = 0;
        public float burstTimer = 0f;
        public float burstInterval = 0.018f;
        public boolean isTargeted = false;
        public ShipAPI targetShip = null;
        public float beamSpread = 650f;
    }

    private CombatEngineAPI engine;
    private boolean active = false;
    private boolean isTitleScreen = false;
    private boolean isInAsteroidField = false;
    private Random random = new Random();

    // Constant background avalanche direction for the whole battle
    private StreamDirection avalancheDirection = StreamDirection.RIGHT_TO_LEFT;

    // Stream settings (Default 200 - 300)
    private int minFieldRocks = 100;
    private int maxFieldRocks = 1000;
    private float streamSpeed = DEFAULT_STREAM_SPEED;
    private float asteroidLifespan = 60.0f;    // 30 seconds lifespan
    private float beamWidth = 650.0f;          // Corridor width for beams
    private float targetedSnipeChance = 0.05f; // 5% chance to aim directly at random ship
    private float figureEventInterval = 28.0f; // Paced wave timing (25-35s)
    private boolean enableInSimulation = true;

    // Sparkling Kinetic Trails (Optimized: zero lag at 120 FPS)
    private boolean enableParticleTrails = true;
    private float particleTrailChance = 0.20f;
    private float trailTickTimer = 0f;

    // Simulation toggle state (persistent across encounters in same session)
    private static boolean simulationEnabled = true;
    private boolean simulationHintShown = false;

    // Active figure streams list (supports 1, 2, or 3 concurrent angles!)
    private List<ActiveFigureStream> activeStreams = new ArrayList<ActiveFigureStream>();

    // Dynamic Battle Capacity (Tuned on the fly via Shift + Plus / Minus)
    private int battleTargetCount = 300;
    private int maxBackgroundRocks = 130;
    private float backgroundSpawnTimer = 0f;
    private float backgroundSpawnInterval = 0.055f;

    // Dynamic Figure Wave timer
    private float figureEventTimer = 0f;

    // Open Space settings (Quiet, rare solitary stray meteors)
    private int openSpaceMinRocks = 4;
    private int openSpaceMaxRocks = 10;
    private float openSpaceMinDelay = 2.0f;
    private float openSpaceMaxDelay = 5.5f;
    private int openSpaceTargetCount = 7;
    private float openSpaceTimer = 0f;
    private float openSpaceCurrentDelay = 2.5f;

    // Combat tracking
    private float totalCombatTime = 0f;
    private boolean notifiedPlayer = false;
    private float notifyTimer = 0f;

    private static final String SETTINGS_PATH = "data/config/dynamic_asteroids_settings.json";
    private static final String MOD_ID = "dynamic_asteroids";
    private static final String TRACKED_KEY = "da_v2_rock";
    private static final String SPAWN_TIME_KEY = "da_v2_birth";
    private static final String BASE_VX_KEY = "da_v2_bvx";
    private static final String BASE_VY_KEY = "da_v2_bvy";
    private static final String TRAIL_TYPE_KEY = "da_v2_trail";

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        this.active = false;
        this.totalCombatTime = 0f;
        this.backgroundSpawnTimer = 0f;
        this.figureEventTimer = 7.0f; // First figure appears after 7 seconds
        this.activeStreams.clear();
        this.notifiedPlayer = false;
        this.notifyTimer = 0f;
        this.simulationHintShown = false;
        this.trailTickTimer = 0f;

        if (engine == null) return;

        // Detect title screen
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

        // 1. Disable vanilla background asteroid belt generators
        disableVanillaAsteroidFields();

        // 2. Clear static vanilla asteroids
        clearAllAsteroids(false);

        // 2. Strict campaign terrain check
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

        // 3. Location classification
        if (isTitleScreen) {
            isInAsteroidField = true;
        } else if (engine.isSimulation()) {
            isInAsteroidField = true;
        } else if (engine.isInCampaign()) {
            isInAsteroidField = campaignTerrainHasAsteroids;
        } else {
            isInAsteroidField = false;
        }

        loadSettings();

        // If simulation is active and disabled in settings
        if (engine.isSimulation() && !enableInSimulation) {
            simulationEnabled = false;
        }

        // 4. RULE 1: Baseline avalanche flows in ONE CONSTANT DIRECTION throughout the whole battle
        int dirChoice = random.nextInt(4);
        switch (dirChoice) {
            case 0: avalancheDirection = StreamDirection.RIGHT_TO_LEFT; break;
            case 1: avalancheDirection = StreamDirection.LEFT_TO_RIGHT; break;
            case 2: avalancheDirection = StreamDirection.BOTTOM_TO_TOP; break;
            case 3: avalancheDirection = StreamDirection.TOP_TO_BOTTOM; break;
        }

        // 5. Total asteroid capacity rolled between minFieldRocks (200) and maxFieldRocks (300)
        if (isInAsteroidField) {
            int minR = Math.min(minFieldRocks, maxFieldRocks);
            int maxR = Math.max(minFieldRocks, maxFieldRocks);
            battleTargetCount = minR + random.nextInt(Math.max(1, maxR - minR + 1));
            maxBackgroundRocks = Math.min(450, battleTargetCount / 2);
            backgroundSpawnInterval = Math.max(0.015f, 0.055f * (300f / Math.max(300, battleTargetCount)));
        } else {
            int minR = Math.min(openSpaceMinRocks, openSpaceMaxRocks);
            int maxR = Math.max(openSpaceMinRocks, openSpaceMaxRocks);
            openSpaceTargetCount = minR + random.nextInt(Math.max(1, maxR - minR + 1));
            openSpaceCurrentDelay = openSpaceMinDelay + random.nextFloat() * (openSpaceMaxDelay - openSpaceMinDelay);
        }

        this.active = true;
    }

    private void loadSettings() {
        try {
            JSONObject config = Global.getSettings().loadJSON(SETTINGS_PATH);
            if (config != null) {
                minFieldRocks = config.optInt("minFieldRocks", minFieldRocks);
                maxFieldRocks = config.optInt("maxFieldRocks", maxFieldRocks);
                streamSpeed = (float) config.optDouble("fieldSurgeSpeed", streamSpeed);
                asteroidLifespan = (float) config.optDouble("asteroidLifespan", asteroidLifespan);
                beamWidth = (float) config.optDouble("beamWidth", beamWidth);
                targetedSnipeChance = (float) config.optDouble("targetedSnipeChance", targetedSnipeChance);
                figureEventInterval = (float) config.optDouble("figureEventInterval", figureEventInterval);
                enableInSimulation = config.optBoolean("enableInSimulation", enableInSimulation);
                enableParticleTrails = config.optBoolean("enableParticleTrails", enableParticleTrails);
                particleTrailChance = (float) config.optDouble("particleTrailChance", particleTrailChance);

                openSpaceMinRocks = config.optInt("openSpaceMinRocks", openSpaceMinRocks);
                openSpaceMaxRocks = config.optInt("openSpaceMaxRocks", openSpaceMaxRocks);
                openSpaceMinDelay = (float) config.optDouble("openSpaceMinDelay", openSpaceMinDelay);
                openSpaceMaxDelay = (float) config.optDouble("openSpaceMaxDelay", openSpaceMaxDelay);
            }
        } catch (Throwable t) {
        }

        // LunaLib F2 overrides
        try {
            if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
                loadLunaSettingNumber("da_minFieldRocks", minFieldRocks);
                loadLunaSettingNumber("da_maxFieldRocks", maxFieldRocks);
                loadLunaSettingNumber("da_fieldSurgeSpeed", streamSpeed);
                loadLunaSettingNumber("da_asteroidLifespan", asteroidLifespan);
                loadLunaSettingNumber("da_beamWidth", beamWidth);
                loadLunaSettingNumber("da_targetedSnipeChance", targetedSnipeChance);
                loadLunaSettingNumber("da_particleTrailChance", particleTrailChance);
                loadLunaSettingNumber("da_openSpaceMinRocks", openSpaceMinRocks);
                loadLunaSettingNumber("da_openSpaceMaxRocks", openSpaceMaxRocks);
                loadLunaSettingNumber("da_openSpaceMinDelay", openSpaceMinDelay);
                loadLunaSettingNumber("da_openSpaceMaxDelay", openSpaceMaxDelay);

                Boolean simVal = getLunaBoolean("da_enableInSimulation");
                if (simVal != null) enableInSimulation = simVal.booleanValue();

                Boolean ptVal = getLunaBoolean("da_enableParticleTrails");
                if (ptVal != null) enableParticleTrails = ptVal.booleanValue();
            }
        } catch (Throwable t) {
        }

        // Clamping (Defaults 200 to 300)
        if (minFieldRocks < 20) minFieldRocks = 100;
        if (maxFieldRocks < minFieldRocks) maxFieldRocks = minFieldRocks + 100;
        if (maxFieldRocks > 2500) maxFieldRocks = 2500;
        if (streamSpeed > 5000f) streamSpeed = 5000f;
        if (asteroidLifespan <= 0f) asteroidLifespan = 60.0f;
        if (beamWidth < 300f) beamWidth = 650f;
        if (targetedSnipeChance < 0f) targetedSnipeChance = 0f;
        if (targetedSnipeChance > 1f) targetedSnipeChance = 1f;
        if (particleTrailChance < 0f) particleTrailChance = 0f;
        if (particleTrailChance > 1f) particleTrailChance = 1f;
        if (figureEventInterval < 10.0f) figureEventInterval = 28.0f;
        if (openSpaceMinRocks < 1) openSpaceMinRocks = 4;
        if (openSpaceMaxRocks < openSpaceMinRocks) openSpaceMaxRocks = openSpaceMinRocks + 5;
        if (openSpaceMinDelay <= 0f) openSpaceMinDelay = 2.0f;
        if (openSpaceMaxDelay < openSpaceMinDelay) openSpaceMaxDelay = openSpaceMinDelay + 3.0f;
    }

    private void loadLunaSettingNumber(String fieldId, float currentVal) {
        Number num = getLunaNumber(fieldId);
        if (num == null) return;
        if (fieldId.equals("da_minFieldRocks")) minFieldRocks = num.intValue();
        else if (fieldId.equals("da_maxFieldRocks")) maxFieldRocks = num.intValue();
        else if (fieldId.equals("da_fieldSurgeSpeed")) streamSpeed = num.floatValue();
        else if (fieldId.equals("da_asteroidLifespan")) asteroidLifespan = num.floatValue();
        else if (fieldId.equals("da_beamWidth")) beamWidth = num.floatValue();
        else if (fieldId.equals("da_targetedSnipeChance")) targetedSnipeChance = num.floatValue();
        else if (fieldId.equals("da_particleTrailChance")) particleTrailChance = num.floatValue();
        else if (fieldId.equals("da_openSpaceMinRocks")) openSpaceMinRocks = num.intValue();
        else if (fieldId.equals("da_openSpaceMaxRocks")) openSpaceMaxRocks = num.intValue();
        else if (fieldId.equals("da_openSpaceMinDelay")) openSpaceMinDelay = num.floatValue();
        else if (fieldId.equals("da_openSpaceMaxDelay")) openSpaceMaxDelay = num.floatValue();
    }

    private Number getLunaNumber(String fieldId) {
        try {
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
                Object val = method.invoke(null, MOD_ID, fieldId);
                if (val instanceof Number) return (Number) val;
            }
        } catch (Throwable t) {
        }
        return null;
    }

    private Boolean getLunaBoolean(String fieldId) {
        try {
            Class<?> clazz = Class.forName("lunalib.lunaSettings.LunaSettings");
            Method method = clazz.getMethod("getBoolean", String.class, String.class);
            if (method != null) {
                Object val = method.invoke(null, MOD_ID, fieldId);
                if (val instanceof Boolean) return (Boolean) val;
            }
        } catch (Throwable t) {
        }
        return null;
    }

    private ShipAPI getPlayerShipSafely() {
        try {
            if (engine != null) return engine.getPlayerShip();
        } catch (Throwable t) {
        }
        return null;
    }

    private String getAvalancheDirectionLabel() {
        if (IS_RUSSIAN) {
            switch (avalancheDirection) {
                case RIGHT_TO_LEFT: return "Справа налево (Запад)";
                case LEFT_TO_RIGHT: return "Слева направо (Восток)";
                case BOTTOM_TO_TOP: return "Снизу вверх (Север)";
                case TOP_TO_BOTTOM: return "Сверху вниз (Юг)";
            }
            return "Запад";
        } else {
            switch (avalancheDirection) {
                case RIGHT_TO_LEFT: return "Right -> Left (West)";
                case LEFT_TO_RIGHT: return "Left -> Right (East)";
                case BOTTOM_TO_TOP: return "Bottom -> Top (North)";
                case TOP_TO_BOTTOM: return "Top -> Bottom (South)";
            }
            return "West";
        }
    }

    // Track vanilla field disabling
    private boolean vanillaFieldsDisabled = false;

    private void disableVanillaAsteroidFields() {
        if (engine == null) return;
        try {
            Method getMapMethod = null;
            try {
                getMapMethod = engine.getClass().getMethod("getCombatMap");
            } catch (Throwable t) {
                for (Method m : engine.getClass().getDeclaredMethods()) {
                    if (m.getParameterTypes().length == 0 && m.getReturnType().getName().endsWith(".combat.A.B")) {
                        getMapMethod = m;
                        break;
                    }
                }
            }

            Object combatMap = null;
            if (getMapMethod != null) {
                getMapMethod.setAccessible(true);
                combatMap = getMapMethod.invoke(engine);
            } else {
                for (Field f : engine.getClass().getDeclaredFields()) {
                    if (f.getType().getName().endsWith(".combat.A.B")) {
                        f.setAccessible(true);
                        combatMap = f.get(engine);
                        break;
                    }
                }
            }

            if (combatMap != null) {
                for (Field f : combatMap.getClass().getDeclaredFields()) {
                    if (List.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        Type gt = f.getGenericType();
                        if (gt != null && gt.toString().contains(".combat.A.E")) {
                            List<?> list = (List<?>) f.get(combatMap);
                            if (list != null) {
                                list.clear();
                                vanillaFieldsDisabled = true;
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
        }
    }

    private void clearAllAsteroids(boolean withSmoke) {
        if (engine == null) return;
        List<CombatEntityAPI> existing = engine.getAsteroids();
        if (existing != null && !existing.isEmpty()) {
            Color dustColor = new Color(130, 115, 100, 110);
            for (CombatEntityAPI ast : existing) {
                if (ast != null && !ast.isExpired()) {
                    if (withSmoke && ast.getLocation() != null && !isTitleScreen) {
                        float radius = Math.max(25f, ast.getCollisionRadius());
                        engine.addSmokeParticle(ast.getLocation(), new Vector2f(0f, 0f), radius * 0.8f, 0.4f, 0.6f, dustColor);
                    }
                    engine.removeEntity(ast);
                }
            }
        }
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (!active || engine == null) return;

        totalCombatTime += amount;

        // Process Simulation Hotkeys:
        // ONLY ACTIVE IN SIMULATION! In regular campaign battles, does nothing.
        if (engine.isSimulation() && events != null) {
            boolean shiftHeld = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

            for (InputEventAPI event : events) {
                if (!event.isConsumed() && event.isKeyDownEvent()) {
                    int key = event.getEventValue();

                    if (key == Keyboard.KEY_SLASH) {
                        event.consume();
                        simulationEnabled = !simulationEnabled;
                        if (!simulationEnabled) {
                            activeStreams.clear();
                            clearAllAsteroids(true);
                            if (engine.getCombatUI() != null) {
                                engine.getCombatUI().addMessage(1, IS_RUSSIAN ? "Dynamic Asteroids: Поток ОТКЛЮЧЕН (Нажмите [/] для включения)" : "Dynamic Asteroids: Stream DISABLED (Press [/] to enable)", Color.YELLOW);
                            }
                        } else {
                            if (engine.getCombatUI() != null) {
                                engine.getCombatUI().addMessage(1, IS_RUSSIAN ? "Dynamic Asteroids: Поток ВКЛЮЧЕН (Нажмите [/] для отключения)" : "Dynamic Asteroids: Stream ENABLED (Press [/] to disable)", Color.GREEN);
                            }
                        }
                    } else if (shiftHeld && (key == Keyboard.KEY_EQUALS || key == Keyboard.KEY_ADD)) {
                        event.consume();
                        battleTargetCount = Math.min(10000, battleTargetCount + 50);
                        maxBackgroundRocks = battleTargetCount / 2;
                        backgroundSpawnInterval = Math.max(0.005f, 0.055f * (300f / Math.max(300, battleTargetCount)));
                        showLimitFeedback(IS_RUSSIAN ? "Лимит астероидов УВЕЛИЧЕН: " + battleTargetCount + " шт." : "Asteroid Limit INCREASED: " + battleTargetCount + " rocks", Color.GREEN);
                    } else if (shiftHeld && (key == Keyboard.KEY_MINUS || key == Keyboard.KEY_SUBTRACT)) {
                        event.consume();
                        battleTargetCount = Math.max(25, battleTargetCount - 50);
                        maxBackgroundRocks = Math.max(12, battleTargetCount / 2);
                        backgroundSpawnInterval = Math.max(0.005f, 0.055f * (300f / Math.max(300, battleTargetCount)));
                        showLimitFeedback(IS_RUSSIAN ? "Лимит астероидов УМЕНЬШЕН: " + battleTargetCount + " шт." : "Asteroid Limit DECREASED: " + battleTargetCount + " rocks", Color.YELLOW);
                    }
                }
            }
        }

        // Show simulation one-time hint
        if (engine.isSimulation() && !simulationHintShown) {
            simulationHintShown = true;
            if (engine.getCombatUI() != null) {
                engine.getCombatUI().addMessage(1, IS_RUSSIAN ? "[Dynamic Asteroids 2.0] [/] Вкл/Выкл | [Shift + / -] Лимит (+-50)" : "[Dynamic Asteroids 2.0] [/] Toggle ON/OFF | [Shift + / -] Adjust limit (+-50)", Color.CYAN);
            }
        }

        // Single combat notification
        if (!isTitleScreen && !notifiedPlayer) {
            ShipAPI player = getPlayerShipSafely();
            if (player != null) {
                notifyTimer += amount;
                if (notifyTimer >= 0.4f) {
                    notifiedPlayer = true;
                    showInitialNotification(player);
                }
            }
        }

        // HUD status in bottom-left
        if (!isTitleScreen && getPlayerShipSafely() != null) {
            try {
                String icon = Global.getSettings().getSpriteName("ui", "icon_fleet_danger");
                if (engine.isSimulation()) {
                    if (!simulationEnabled) {
                        engine.maintainStatusForPlayerShip(
                            "DYNAMIC_ASTEROIDS_STATUS",
                            icon,
                            IS_RUSSIAN ? "СИМУЛЯЦИЯ (ОТКЛЮЧЕНА)" : "SIMULATION (DISABLED)",
                            IS_RUSSIAN ? "Астероиды: ВЫКЛ | [/] Включить" : "Asteroids: OFF | Press [/] to enable",
                            false
                        );
                    } else {
                        String streamInfo = activeStreams.isEmpty() ? (IS_RUSSIAN ? "Лавина: " : "Avalanche: ") + getAvalancheDirectionLabel() : (IS_RUSSIAN ? "АКТИВНО ПОТОКОВ: " : "STREAMS ACTIVE: ") + activeStreams.size();
                        engine.maintainStatusForPlayerShip(
                            "DYNAMIC_ASTEROIDS_STATUS",
                            icon,
                            (IS_RUSSIAN ? "СИМУЛЯЦИЯ: ПОЯС (" : "SIMULATION: ASTEROID BELT (") + battleTargetCount + (IS_RUSSIAN ? " макс)" : " max)"),
                            streamInfo + (IS_RUSSIAN ? " | [Shift +/-] Лимит" : " | [Shift +/-] Limit"),
                            true
                        );
                    }
                } else if (isInAsteroidField) {
                    String sub = (IS_RUSSIAN ? "Лавина: " : "Avalanche: ") + getAvalancheDirectionLabel();
                    if (!activeStreams.isEmpty()) {
                        sub += IS_RUSSIAN ? (" | Формаций: " + activeStreams.size()) : (" | Formations: " + activeStreams.size() + " active");
                    } else {
                        sub += IS_RUSSIAN ? (" | Вместимость: " + battleTargetCount) : (" | Capacity: " + battleTargetCount + " rocks");
                    }
                    engine.maintainStatusForPlayerShip(
                        "DYNAMIC_ASTEROIDS_STATUS",
                        icon,
                        IS_RUSSIAN ? "ОПАСНОСТЬ: АСТЕРОИДНЫЙ ПОЯС" : "HAZARD: ASTEROID BELT",
                        sub,
                        true
                    );
                } else {
                    engine.maintainStatusForPlayerShip(
                        "DYNAMIC_ASTEROIDS_STATUS",
                        icon,
                        IS_RUSSIAN ? "ОТКРЫТЫЙ КОСМОС" : "OPEN SPACE",
                        (IS_RUSSIAN ? "Дрейф: " : "Drift: ") + getAvalancheDirectionLabel() + (IS_RUSSIAN ? " | Редкие метеоры: " : " | Stray Meteors: ") + openSpaceTargetCount,
                        false
                    );
                }
            } catch (Throwable t) {
            }
        }

        // Pause check
        if (!isTitleScreen && engine.isPaused()) {
            return;
        }

        // Title screen movement
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

        // Ensure vanilla background generators remain permanently disabled
        if (!vanillaFieldsDisabled || totalCombatTime < 2.0f) {
            disableVanillaAsteroidFields();
        }

        // Simulation disabled check
        if (engine.isSimulation() && !simulationEnabled) {
            clearAllAsteroids(false);
            return;
        }

        // Visible boundaries and strict despawn margins
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

        float despawnMargin = isTitleScreen ? 400f : 350f;
        float minX = focalCX - halfW - despawnMargin;
        float maxX = focalCX + halfW + despawnMargin;
        float minY = focalCY - halfH - despawnMargin;
        float maxY = focalCY + halfH + despawnMargin;

        // Advance existing asteroids
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

                // Untracked vanilla rock removal
                if (ast.getCustomData().get(TRACKED_KEY) == null) {
                    if (toRemove == null) toRemove = new ArrayList<CombatEntityAPI>();
                    toRemove.add(ast);
                    continue;
                }

                // Strict out of bounds check
                boolean outOfBounds = (loc.x < minX || loc.x > maxX || loc.y < minY || loc.y > maxY);
                if (outOfBounds) {
                    if (toRemove == null) toRemove = new ArrayList<CombatEntityAPI>();
                    toRemove.add(ast);
                    continue;
                }

                // Lifespan expiration
                Object birthObj = ast.getCustomData().get(SPAWN_TIME_KEY);
                if (birthObj instanceof Float) {
                    float age = totalCombatTime - ((Float) birthObj).floatValue();
                    if (age >= asteroidLifespan) {
                        if (toPoof == null) toPoof = new ArrayList<CombatEntityAPI>();
                        toPoof.add(ast);
                        continue;
                    }
                }

                // Kinetic Cruise Control
                maintainVelocity(ast);

                activeCount++;
            }

            // Dissolve into smoke puff on expiration
            if (toPoof != null) {
                Color dustColor = new Color(130, 115, 100, 110);
                for (CombatEntityAPI dead : toPoof) {
                    if (dead != null && dead.getLocation() != null && !isTitleScreen) {
                        float radius = Math.max(25f, dead.getCollisionRadius());
                        engine.addSmokeParticle(dead.getLocation(), new Vector2f(0f, 0f), radius * 0.8f, 0.4f, 0.6f, dustColor);
                    }
                    engine.removeEntity(dead);
                }
            }

            // Silent removal for out of bounds
            if (toRemove != null) {
                for (CombatEntityAPI dead : toRemove) {
                    engine.removeEntity(dead);
                }
            }
        }

        // =========================================================================
        // SPARKLING KINETIC PARTICLE TRAILS (ZERO LAG AT 120 FPS)
        // =========================================================================
        if (enableParticleTrails && !isTitleScreen && asteroids != null && !asteroids.isEmpty()) {
            trailTickTimer += amount;
            if (trailTickTimer >= 0.038f) { // ~26 ticks per sec
                trailTickTimer = 0f;

                Color goldSpark = new Color(255, 205, 115, 220);
                Color goldDust = new Color(175, 150, 120, 90);

                Color cyanSpark = new Color(120, 220, 255, 220);
                Color cyanDust = new Color(110, 160, 210, 90);

                Color redSpark = new Color(255, 65, 35, 240);
                Color redDust = new Color(200, 50, 30, 110);

                for (int i = 0; i < asteroids.size(); i++) {
                    CombatEntityAPI ast = asteroids.get(i);
                    if (ast == null || ast.isExpired()) continue;
                    Object trailObj = ast.getCustomData().get(TRAIL_TYPE_KEY);
                    if (trailObj instanceof Integer) {
                        Vector2f loc = ast.getLocation();
                        Vector2f vel = ast.getVelocity();
                        if (loc == null || vel == null) continue;

                        int tt = ((Integer) trailObj).intValue();
                        Color sparkCol = goldSpark;
                        Color dustCol = goldDust;
                        if (tt == 2) {
                            sparkCol = redSpark;
                            dustCol = redDust;
                        } else if (tt == 3) {
                            sparkCol = cyanSpark;
                            dustCol = cyanDust;
                        }

                        Vector2f pVel = new Vector2f(-vel.x * 0.04f, -vel.y * 0.04f);
                        float radius = Math.max(14f, ast.getCollisionRadius());

                        // Sparkling glint
                        engine.addHitParticle(loc, pVel, radius * 0.55f, 0.85f, 0.28f, sparkCol);
                        // Subtle soft trailing vapor
                        engine.addSmoothParticle(loc, pVel, radius * 0.75f, 0.40f, 0.35f, dustCol);
                    }
                }
            }
        }

        // =========================================================================
        // OPEN SPACE SPAWN LOGIC (Quiet, rare solitary stray meteors)
        // =========================================================================
        if (!isInAsteroidField && !isTitleScreen) {
            openSpaceTimer += amount;
            if (openSpaceTimer >= openSpaceCurrentDelay) {
                openSpaceTimer = 0f;
                openSpaceCurrentDelay = openSpaceMinDelay + random.nextFloat() * (openSpaceMaxDelay - openSpaceMinDelay);
                if (activeCount < openSpaceTargetCount) {
                    spawnOpenSpaceMeteor(focalCX, focalCY, halfW, halfH);
                }
            }
            return;
        }

        // =========================================================================
        // ASTEROID FIELD SPAWN LOGIC (300 ROCKS CAPACITY):
        // 1. Constant Baseline Avalanche (Steady flow in constant direction)
        // 2. Paced Formation Waves (Every 25-35s: Massive dense shapes, 1-3 angles, 5% Snipe)
        // =========================================================================

        // 1. Baseline Avalanche Spawner (stays strictly in avalancheDirection across the map)
        backgroundSpawnTimer += amount;
        if (backgroundSpawnTimer >= backgroundSpawnInterval) {
            backgroundSpawnTimer = 0f;
            if (activeCount < maxBackgroundRocks) {
                spawnBackgroundAsteroid(focalCX, focalCY, halfW, halfH);
                activeCount++;
            }
        }

        // 2. Dynamic Formation Event Spawner (Paced: every 25-35s)
        figureEventTimer += amount;
        if (figureEventTimer >= figureEventInterval && activeStreams.isEmpty()) {
            figureEventTimer = 0f;
            figureEventInterval = 25.0f + random.nextFloat() * 10.0f;
            triggerMultiAngleFormationWave(focalCX, focalCY, halfW, halfH);
        }

        // 3. Process Active Formation Streams (Dense heavy bursts across 1, 2, or 3 concurrent angles)
        if (!activeStreams.isEmpty()) {
            List<ActiveFigureStream> finishedStreams = null;

            for (int i = 0; i < activeStreams.size(); i++) {
                ActiveFigureStream stream = activeStreams.get(i);
                if (stream == null || stream.rocksRemaining <= 0) {
                    if (finishedStreams == null) finishedStreams = new ArrayList<ActiveFigureStream>();
                    finishedStreams.add(stream);
                    continue;
                }

                stream.burstTimer += amount;
                if (stream.burstTimer >= stream.burstInterval) {
                    stream.burstTimer = 0f;
                    if (activeCount < battleTargetCount) {
                        spawnStreamAsteroid(stream);
                        activeCount++;
                    }
                    stream.rocksRemaining--;
                }
            }

            if (finishedStreams != null) {
                activeStreams.removeAll(finishedStreams);
            }
        }
    }

    private void maintainVelocity(CombatEntityAPI ast) {
        Vector2f vel = ast.getVelocity();
        if (vel == null) return;

        Object bvxObj = ast.getCustomData().get(BASE_VX_KEY);
        Object bvyObj = ast.getCustomData().get(BASE_VY_KEY);
        if (bvxObj instanceof Float && bvyObj instanceof Float) {
            float bvx = ((Float) bvxObj).floatValue();
            float bvy = ((Float) bvyObj).floatValue();

            float currentSpeedSq = vel.x * vel.x + vel.y * vel.y;
            float targetSpeedSq = bvx * bvx + bvy * bvy;
            if (currentSpeedSq < targetSpeedSq * 0.7f) {
                vel.x = bvx;
                vel.y = bvy;
            }
        }
    }

    /**
     * OPEN SPACE: Spawns a gentle solitary stray meteor.
     */
    private void spawnOpenSpaceMeteor(float focalCX, float focalCY, float halfW, float halfH) {
        float speed = Math.min(streamSpeed, 400.0f) * (0.8f + random.nextFloat() * 0.4f);
        float off = 120f;
        float spawnX = 0f;
        float spawnY = 0f;
        float vx = 0f;
        float vy = 0f;

        switch (avalancheDirection) {
            case RIGHT_TO_LEFT:
                spawnX = focalCX + halfW + off;
                spawnY = focalCY + (random.nextFloat() - 0.5f) * (halfH * 2.0f);
                vx = -speed;
                vy = (random.nextFloat() - 0.5f) * 40.0f;
                break;
            case LEFT_TO_RIGHT:
                spawnX = focalCX - halfW - off;
                spawnY = focalCY + (random.nextFloat() - 0.5f) * (halfH * 2.0f);
                vx = speed;
                vy = (random.nextFloat() - 0.5f) * 40.0f;
                break;
            case BOTTOM_TO_TOP:
                spawnX = focalCX + (random.nextFloat() - 0.5f) * (halfW * 2.0f);
                spawnY = focalCY - halfH - off;
                vx = (random.nextFloat() - 0.5f) * 40.0f;
                vy = speed;
                break;
            case TOP_TO_BOTTOM:
                spawnX = focalCX + (random.nextFloat() - 0.5f) * (halfW * 2.0f);
                spawnY = focalCY + halfH + off;
                vx = (random.nextFloat() - 0.5f) * 40.0f;
                vy = -speed;
                break;
        }

        int type = rollAsteroidType(false);
        spawnSingleAsteroidEntity(type, spawnX, spawnY, vx, vy, 0);
    }

    /**
     * RULE 1: Spawns ambient background avalanche rocks in the CONSTANT battle direction.
     * Places spawn coordinates directly along the border rectangle (120 units offscreen).
     */
    private void spawnBackgroundAsteroid(float focalCX, float focalCY, float halfW, float halfH) {
        float spawnX = 0f;
        float spawnY = 0f;
        float vx = 0f;
        float vy = 0f;

        float off = 120f;

        switch (avalancheDirection) {
            case RIGHT_TO_LEFT:
                spawnX = focalCX + halfW + off;
                spawnY = focalCY + (random.nextFloat() - 0.5f) * (halfH * 2.0f);
                vx = -streamSpeed;
                vy = (random.nextFloat() - 0.5f) * 40.0f;
                break;
            case LEFT_TO_RIGHT:
                spawnX = focalCX - halfW - off;
                spawnY = focalCY + (random.nextFloat() - 0.5f) * (halfH * 2.0f);
                vx = streamSpeed;
                vy = (random.nextFloat() - 0.5f) * 40.0f;
                break;
            case BOTTOM_TO_TOP:
                spawnX = focalCX + (random.nextFloat() - 0.5f) * (halfW * 2.0f);
                spawnY = focalCY - halfH - off;
                vx = (random.nextFloat() - 0.5f) * 40.0f;
                vy = streamSpeed;
                break;
            case TOP_TO_BOTTOM:
                spawnX = focalCX + (random.nextFloat() - 0.5f) * (halfW * 2.0f);
                spawnY = focalCY + halfH + off;
                vx = (random.nextFloat() - 0.5f) * 40.0f;
                vy = -streamSpeed;
                break;
        }

        int type = rollAsteroidType(false);
        spawnSingleAsteroidEntity(type, spawnX, spawnY, vx, vy, 0);
    }

    /**
     * Solves exact ray intersection with the map bounding rectangle:
     * Guarantees spawn point lies PRECISELY along the outer map edge (150 units offscreen)
     * for ANY arbitrary angle (0 to 360 degrees).
     */
    private Vector2f getRectangleBoundaryPoint(float focalCX, float focalCY, float halfW, float halfH, float angle) {
        float W = halfW + 150f;
        float H = halfH + 150f;
        float cosA = (float) Math.cos(angle);
        float sinA = (float) Math.sin(angle);

        float absCos = Math.abs(cosA) > 1e-4f ? Math.abs(cosA) : 1e-4f;
        float absSin = Math.abs(sinA) > 1e-4f ? Math.abs(sinA) : 1e-4f;

        float tX = W / absCos;
        float tY = H / absSin;
        float t = Math.min(tX, tY);

        return new Vector2f(focalCX + t * cosA, focalCY + t * sinA);
    }

    /**
     * RULES 2, 3:
     * - Omnidirectional 360 lines across the map rectangle.
     * - Multi-Angle Duplication: 1, 2, or 3 simultaneous streams.
     * - Heavy rock allocations (60 to 90 rocks per wave) for dense, dramatic formations!
     * - 5% Targeted Snipe towards any random alive ship.
     */
    private void triggerMultiAngleFormationWave(float focalCX, float focalCY, float halfW, float halfH) {
        // Roll number of concurrent streams/angles:
        int streamCount = 1;
        float roll = random.nextFloat();
        if (roll < 0.45f) {
            streamCount = 1;
        } else if (roll < 0.85f) {
            streamCount = 2; // Dual stream from 2 angles
        } else {
            streamCount = 3; // Triple cross from 3 angles
        }

        // Heavy rock budget for 300-capacity battles:
        // 1 stream  -> 70 rocks (massive continuous torrent / cloud)
        // 2 streams -> 40 rocks each (total 80)
        // 3 streams -> 28 rocks each (total 84)
        float capScale = Math.max(1.0f, battleTargetCount / 300.0f);
        int rocksPerStream = Math.round(70 * capScale);
        if (streamCount == 2) rocksPerStream = Math.round(40 * capScale);
        else if (streamCount == 3) rocksPerStream = Math.round(28 * capScale);

        float baseEntryAngle = random.nextFloat() * (float) (Math.PI * 2.0);

        // Pick base figure type for this wave
        FigureType[] allFigures = FigureType.values();
        FigureType waveFigureType = allFigures[random.nextInt(allFigures.length)];

        // Query eligible alive ships for 5% targeted snipe roll
        List<ShipAPI> eligibleShips = new ArrayList<ShipAPI>();
        List<ShipAPI> allShips = engine.getShips();
        if (allShips != null) {
            for (ShipAPI ship : allShips) {
                if (ship != null && ship.isAlive() && !ship.isHulk() && !ship.isShuttlePod()) {
                    eligibleShips.add(ship);
                }
            }
        }

        boolean anyTargeted = false;

        for (int i = 0; i < streamCount; i++) {
            ActiveFigureStream stream = new ActiveFigureStream();
            stream.type = waveFigureType;
            stream.rocksRemaining = rocksPerStream;
            stream.burstInterval = (waveFigureType == FigureType.COMET_SWARM) ? 0.008f : 0.016f;
            stream.beamSpread = beamWidth;

            // Distinct angles across the boundary
            float entryAngle = baseEntryAngle + i * (float) (Math.PI * 2.0 / streamCount) + (random.nextFloat() - 0.5f) * 0.35f;

            // Precise boundary point along map rectangle (no out-of-bounds stalling!)
            Vector2f startPt = getRectangleBoundaryPoint(focalCX, focalCY, halfW, halfH, entryAngle);
            stream.origin.set(startPt.x, startPt.y);

            // 5% Chance: lock vector directly towards ANY random alive ship!
            boolean isThisStreamTargeted = (random.nextFloat() < targetedSnipeChance) && !eligibleShips.isEmpty();

            if (isThisStreamTargeted) {
                ShipAPI target = eligibleShips.get(random.nextInt(eligibleShips.size()));
                stream.isTargeted = true;
                stream.targetShip = target;
                stream.type = FigureType.LANCE_BEAM;
                anyTargeted = true;

                float tx = target.getLocation().x;
                float ty = target.getLocation().y;
                float dx = tx - startPt.x;
                float dy = ty - startPt.y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > 0.001f) {
                    stream.velocity.set((dx / dist) * streamSpeed, (dy / dist) * streamSpeed);
                } else {
                    stream.velocity.set(-streamSpeed, 0f);
                }

                showTargetedAlert(target);
            } else {
                // Arbitrary line across the battlefield towards opposite/interior area
                float targetX = focalCX + (random.nextFloat() - 0.5f) * (halfW * 1.2f);
                float targetY = focalCY + (random.nextFloat() - 0.5f) * (halfH * 1.2f);

                float dx = targetX - startPt.x;
                float dy = targetY - startPt.y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > 0.001f) {
                    stream.velocity.set((dx / dist) * streamSpeed, (dy / dist) * streamSpeed);
                } else {
                    stream.velocity.set(-streamSpeed, 0f);
                }
            }

            // Perpendicular unit vector for width
            float vx = stream.velocity.x;
            float vy = stream.velocity.y;
            float norm = (float) Math.sqrt(vx * vx + vy * vy);
            if (norm > 0.001f) {
                stream.perp.set(-vy / norm, vx / norm);
            } else {
                stream.perp.set(0f, 1f);
            }

            activeStreams.add(stream);
        }

        // Paced non-spammy combat announcements
        if (!anyTargeted) {
            if (IS_RUSSIAN) {
                if (streamCount == 1) {
                    notifyWaveIncoming("ВНИМАНИЕ: " + waveFigureType.getDisplayName().toUpperCase() + " прорезает сектор!");
                } else if (streamCount == 2) {
                    notifyWaveIncoming("ДВОЙНОЙ ЗАЛП: 2 волны " + waveFigureType.getDisplayName() + " с разных углов!");
                } else {
                    notifyWaveIncoming("ТРОЙНОЙ ШКВАЛ: 3 волны " + waveFigureType.getDisplayName() + " пересекают сектор!");
                }
            } else {
                if (streamCount == 1) {
                    notifyWaveIncoming(waveFigureType.getDisplayName().toUpperCase() + " incoming across sector!");
                } else if (streamCount == 2) {
                    notifyWaveIncoming("DUAL CONVERGENCE: 2 " + waveFigureType.getDisplayName() + " waves converging!");
                } else {
                    notifyWaveIncoming("TRIPLE CROSS-BARRAGE: 3 " + waveFigureType.getDisplayName() + " waves intersecting!");
                }
            }
        }
    }

    private void spawnStreamAsteroid(ActiveFigureStream stream) {
        float spawnX = 0f;
        float spawnY = 0f;
        float vx = stream.velocity.x;
        float vy = stream.velocity.y;
        int type = rollAsteroidType(stream.type == FigureType.HEAVY_MEGALITHS);

        // Visual Trail type:
        // 2: Crimson plasma trail for targeted snipe!
        // 3: Glowing cyan comet trail for Comet Swarm!
        // 1: Golden cosmic spark trail for Lance Beam!
        int trailType = 0;
        if (stream.isTargeted) {
            trailType = 2;
        } else if (stream.type == FigureType.COMET_SWARM) {
            trailType = 3;
        } else if (stream.type == FigureType.LANCE_BEAM) {
            trailType = 1;
        }

        switch (stream.type) {
            case LANCE_BEAM:
                // Thick concentrated beam with both lateral spread and longitudinal depth
                float lateral = (random.nextFloat() - 0.5f) * stream.beamSpread;
                float longitudinal = (random.nextFloat() - 0.5f) * 200f;
                float norm = (float) Math.sqrt(vx * vx + vy * vy);
                float normVx = (norm > 0.001f) ? (vx / norm) : 0f;
                float normVy = (norm > 0.001f) ? (vy / norm) : 0f;

                spawnX = stream.origin.x + stream.perp.x * lateral + normVx * longitudinal;
                spawnY = stream.origin.y + stream.perp.y * lateral + normVy * longitudinal;
                break;

            case COMET_SWARM:
                // Massive dense cloud cluster of radius ~850u
                float ang = random.nextFloat() * (float) (Math.PI * 2.0);
                float r = (float) Math.sqrt(random.nextFloat()) * 850.0f; // Uniform circle distribution
                spawnX = stream.origin.x + (float) Math.cos(ang) * r;
                spawnY = stream.origin.y + (float) Math.sin(ang) * r;
                vx += (random.nextFloat() - 0.5f) * 45.0f;
                vy += (random.nextFloat() - 0.5f) * 45.0f;
                break;

            case DUAL_RAILS:
                // Parallel rails spaced 1600 units apart
                float railOffset = random.nextBoolean() ? -800f : 800f;
                float railJitter = (random.nextFloat() - 0.5f) * 350f;
                spawnX = stream.origin.x + stream.perp.x * (railOffset + railJitter);
                spawnY = stream.origin.y + stream.perp.y * (railOffset + railJitter);
                break;

            case EXPANDING_FAN:
                // Conical fan spreading at +-22 degrees
                float spreadAngle = (random.nextFloat() - 0.5f) * 0.40f;
                float baseAngle = (float) Math.atan2(stream.velocity.y, stream.velocity.x) + spreadAngle;
                vx = streamSpeed * (float) Math.cos(baseAngle);
                vy = streamSpeed * (float) Math.sin(baseAngle);
                float fanJitter = (random.nextFloat() - 0.5f) * 350f;
                spawnX = stream.origin.x + stream.perp.x * fanJitter;
                spawnY = stream.origin.y + stream.perp.y * fanJitter;
                break;

            case HEAVY_MEGALITHS:
                type = (random.nextFloat() < 0.55f) ? 2 : 3;
                float megaJitter = (random.nextFloat() - 0.5f) * stream.beamSpread;
                spawnX = stream.origin.x + stream.perp.x * megaJitter;
                spawnY = stream.origin.y + stream.perp.y * megaJitter;
                break;
        }

        spawnSingleAsteroidEntity(type, spawnX, spawnY, vx, vy, trailType);
    }

    private void spawnSingleAsteroidEntity(int type, float x, float y, float vx, float vy, int trailType) {
        CombatEntityAPI ast = engine.spawnAsteroid(type, x, y, vx, vy);
        if (ast != null) {
            ast.getVelocity().set(vx, vy);
            ast.setFacing(random.nextFloat() * 360f);
            ast.setAngularVelocity((random.nextFloat() - 0.5f) * 55f);

            ast.setCustomData(TRACKED_KEY, Boolean.TRUE);
            ast.setCustomData(SPAWN_TIME_KEY, Float.valueOf(totalCombatTime));
            ast.setCustomData(BASE_VX_KEY, Float.valueOf(vx));
            ast.setCustomData(BASE_VY_KEY, Float.valueOf(vy));

            if (enableParticleTrails) {
                if (trailType > 0) {
                    ast.setCustomData(TRAIL_TYPE_KEY, Integer.valueOf(trailType));
                } else if (random.nextFloat() < particleTrailChance) {
                    ast.setCustomData(TRAIL_TYPE_KEY, Integer.valueOf(1)); // Normal golden spark
                }
            }
        }
    }

    private int rollAsteroidType(boolean forceHeavy) {
        if (forceHeavy) return (random.nextFloat() < 0.55f) ? 2 : 3;
        float r = random.nextFloat();
        if (r < 0.45f) return 0;
        if (r < 0.75f) return 1;
        if (r < 0.93f) return 2;
        return 3;
    }

    private void showInitialNotification(ShipAPI player) {
        try {
            if (isInAsteroidField) {
                engine.addFloatingText(
                    player.getLocation(),
                    (IS_RUSSIAN ? "ОПАСНОСТЬ: АСТЕРОИДНЫЙ ПОЯС (" : "HAZARD: ASTEROID BELT (") + getAvalancheDirectionLabel() + ")",
                    24f,
                    new Color(255, 120, 60),
                    player,
                    0.35f,
                    3.5f
                );
                if (engine.getCombatUI() != null) {
                    engine.getCombatUI().addMessage(1, (IS_RUSSIAN ? "ОПАСНОСТЬ: Астероидный пояс (" : "HAZARD: Asteroid Belt active (") + battleTargetCount + (IS_RUSSIAN ? " макс). Поток: " : " max). Baseline flow: ") + getAvalancheDirectionLabel());
                }
            } else {
                if (engine.getCombatUI() != null) {
                    engine.getCombatUI().addMessage(1, IS_RUSSIAN ? "Dynamic Asteroids: Открытый космос - редкие метеоры" : "Dynamic Asteroids: Deep space - sporadic stray meteors");
                }
            }
        } catch (Throwable t) {
        }
    }

    private void notifyWaveIncoming(String text) {
        try {
            if (engine.getCombatUI() != null && !isTitleScreen) {
                engine.getCombatUI().addMessage(1, (IS_RUSSIAN ? "ВНИМАНИЕ: " : "WARNING: ") + text, new Color(255, 175, 50));
            }
        } catch (Throwable t) {
        }
    }

    private void showLimitFeedback(String text, Color color) {
        try {
            ShipAPI player = getPlayerShipSafely();
            if (player != null && !isTitleScreen) {
                engine.addFloatingText(
                    player.getLocation(),
                    text,
                    22f,
                    color,
                    player,
                    0.25f,
                    2.5f
                );
            }
            if (engine.getCombatUI() != null && !isTitleScreen) {
                engine.getCombatUI().addMessage(1, text, color);
            }
        } catch (Throwable t) {
        }
    }

    private void showTargetedAlert(ShipAPI ship) {
        try {
            if (ship == null || isTitleScreen) return;

            boolean isPlayer = (engine != null && ship == engine.getPlayerShip());
            if (isPlayer) {
                engine.addFloatingText(
                    ship.getLocation(),
                    IS_RUSSIAN ? "КРИТИЧЕСКАЯ ТРЕВОГА: ПРИЦЕЛЬНЫЙ ШКВАЛ ПО ВАШЕЙ ПОЗИЦИИ!" : "CRITICAL ALERT: METEOR TORRENT TARGETED ON YOUR POSITION!",
                    26f,
                    new Color(255, 30, 30),
                    ship,
                    0.35f,
                    4.0f
                );
                if (engine.getCombatUI() != null) {
                    engine.getCombatUI().addMessage(1, IS_RUSSIAN ? "КРИТИЧЕСКАЯ ОПАСНОСТЬ: 5% Прицельный захват! Шквал астероидов по флагману!" : "CRITICAL HAZARD: 5% Torrent Lock! Hypersonic barrage targeted on flagship!", new Color(255, 50, 50));
                }
            } else {
                String shipName = ship.getName();
                String hullName = (ship.getHullSpec() != null) ? ship.getHullSpec().getHullName() : (IS_RUSSIAN ? "Корабль" : "Ship");
                engine.addFloatingText(
                    ship.getLocation(),
                    IS_RUSSIAN ? "ПРИЦЕЛЬНЫЙ ШКВАЛ АСТЕРОИДОВ!" : "METEOR TORRENT TARGETED!",
                    20f,
                    new Color(255, 80, 50),
                    ship,
                    0.35f,
                    3.0f
                );
                if (engine.getCombatUI() != null) {
                    engine.getCombatUI().addMessage(1, (IS_RUSSIAN ? "ОПАСНОСТЬ: 5% Захват цели на " : "HAZARD: 5% Torrent Lock on ") + shipName + " (" + hullName + ")!", new Color(255, 120, 50));
                }
            }
        } catch (Throwable t) {
        }
    }
}
