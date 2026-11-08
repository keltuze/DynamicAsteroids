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
import com.fs.starfarer.api.combat.CombatAsteroidAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.input.InputEventAPI;

import java.util.Map;
import java.util.HashMap;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.AutofireAIPlugin;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.WeaponGroupAPI;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import java.util.EnumSet;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import lunalib.lunaSettings.LunaSettings;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;


/**
 * Dynamic Asteroids 2.0 (Cyclone Edition)
 */
public class DynamicAsteroidsCombatPlugin extends BaseEveryFrameCombatPlugin {

    // Configurable compilation flags
    private static final boolean IS_RUSSIAN = false;
    private static final float DEFAULT_STREAM_SPEED = 600.0f;

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
        public float lifetime = 35.0f;
        public float timeElapsed = 0f;
        public float totalSpawnDuration = 2.0f;
    }

    private CombatEngineAPI engine;
    private boolean active = false;
    private boolean isTitleScreen = false;
    private boolean isInAsteroidField = false;
    private Random random = new Random();

    // Constant background avalanche direction for the whole battle
    private StreamDirection avalancheDirection = StreamDirection.RIGHT_TO_LEFT;

    // Active Preset Profile (0=Custom, 1=Cinematic, 2=Balanced [Default], 3=Storm, 4=Chaos)
    private int activePreset = 2;

    // Stream settings (Default 200 - 300)
    private int minFieldRocks = 200;
    private int maxFieldRocks = 800;
    private float streamSpeed = DEFAULT_STREAM_SPEED;
    private float asteroidLifespan = 60.0f;    // 30 seconds lifespan
    private float beamWidth = 650.0f;          // Corridor width for beams
    private float targetedSnipeChance = 0.05f; // 5% chance to aim directly at random ship
    private float figureEventInterval = 28.0f; // Paced wave timing (25-35s)
    private boolean enableInSimulation = true;
    private final java.util.Set<String> lunaSettingWarnings = new java.util.HashSet<String>();

    // Sparkling Kinetic Trails (Optimized: zero lag at 120 FPS)
    private boolean enableParticleTrails = true;
    private float particleTrailChance = 0.20f;
    private float trailTickTimer = 0f;

    // Asteroid Ramming & Easter Eggs Tuning
    private float asteroidCollisionDamageMult = 1.0f;
    private float derelictSpawnChance = 0.015f;
    private float debrisEasterEggChance = 0.040f;
    private float resourceEasterEggChance = 0.020f;
    private float otherEasterEggChance = 0.060f;
    private float easterEggSpeed = 400.0f;
    private float easterEggMagnetStrength = 3000.0f;
    private float derelictRescueHoldTime = 60.0f;
    private float derelictRescueChance = 1.0f;

    // Detection Sound state
    private int pendingDetectionSoundPlays = 0;
    private float detectionSoundTimer = 0f;

    // Runtime Easter Egg state
    private boolean battleDerelictSpawned = false;
    private CombatEntityAPI activeDerelictEntity = null;
    private String rescuedDerelictVariantId = null;
    private final List<String> rescuedDerelictVariantIds = new ArrayList<String>();
    private final Map<String, DerelictSalvageRecord> derelictSalvageRecords = new HashMap<String, DerelictSalvageRecord>();
    private float summonKeyCooldown = 0f;
    private float summonContainerKeyCooldown = 0f;
    private String rescuedDerelictHullName = null;
    private boolean derelictRescueNotified = false;
    private int activeOtherEasterEggs = 0;
    private boolean campaignListenerRegistered = false;
    private List<CombatEntityAPI> activeEasterEggEntities = new ArrayList<CombatEntityAPI>();

    // Simulation toggle state (persistent across encounters in same session)
    private static boolean simulationEnabled = true;
    private boolean simulationHintShown = false;

    // Active figure streams list (supports 1, 2, or 3 concurrent angles!)
    private List<ActiveFigureStream> activeStreams = new ArrayList<ActiveFigureStream>();

    // Dynamic Battle Capacity (Tuned on the fly via Shift + Plus / Minus)
    private int battleTargetCount = 500;
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
    private float openSpaceEasterEggMult = 0.14f;

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

    // --- Fleet AI Reaction Settings ---
    private boolean enableMicroDodge = true;
    private boolean enableShieldReaction = true;
    private boolean enablePhaseReaction = true;
    private boolean enablePDTargeting = true;
    private boolean pdCriticalThreatsOnly = true;
    private boolean enablePDFluxSafety = true;
    private float pdMaxFluxThreshold = 0.75f;
    private boolean enableEarlySnipeEvasion = true;

    // Asteroid Durability & Point Defense Shatter Bonus
    private float asteroidHealthMult = 0.60f;
    private float pdAsteroidDamageMult = 6.0f;
    private float aiMinThreatVelocity = 200.0f;
    private float aiLookaheadTime = 1.25f;
    private float aiSafetyMargin = 35.0f;
    private float aiDodgeCooldown = 0.35f;
    private float aiMaxCheckInterval = 0.05f;
    private float aiTickTimer = 0.0f;
    private float lunaCheckTimer = 0.0f;

    private static class ShipAvoidanceState {
        float cooldown = 0.0f;
        ShipCommand activeCommand = null;
        ShipCommand activeThrustCommand = null;
        ShipCommand activeTurnCommand = null;
        CombatEntityAPI targetThreat = null;
        float targetAngle = 0.0f;
    }

    private final Map<ShipAPI, ShipAvoidanceState> shipStates = new HashMap<ShipAPI, ShipAvoidanceState>();
    private final List<CombatEntityAPI> fastThreats = new ArrayList<CombatEntityAPI>(256);

    public static class ImminentThreatData {
        public CombatEntityAPI threat;
        public float timeToImpact;
        public float ttl;
        public ImminentThreatData(CombatEntityAPI threat, float timeToImpact, float ttl) {
            this.threat = threat;
            this.timeToImpact = timeToImpact;
            this.ttl = ttl;
        }
    }

    private final Map<ShipAPI, ImminentThreatData> imminentThreats = new HashMap<ShipAPI, ImminentThreatData>();
    private final java.util.Set<ShipAPI> wrappedShips = new java.util.HashSet<ShipAPI>();
    private static DynamicAsteroidsCombatPlugin instance = null;


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
        this.shipStates.clear();
        this.fastThreats.clear();
        this.aiTickTimer = 0.0f;
        instance = this;
        this.imminentThreats.clear();
        this.wrappedShips.clear();
        this.battleDerelictSpawned = false;
        this.activeDerelictEntity = null;
        this.rescuedDerelictVariantId = null;
        this.rescuedDerelictVariantIds.clear();
        this.derelictSalvageRecords.clear();
        this.summonKeyCooldown = 0f;
        this.summonContainerKeyCooldown = 0f;
        this.rescuedDerelictHullName = null;
        this.derelictRescueNotified = false;
        this.activeOtherEasterEggs = 0;
        this.campaignListenerRegistered = false;
        this.activeEasterEggEntities.clear();

        try {
            Global.getSettings().loadTexture("graphics/icons/derelict.png");
            Global.getSettings().loadTexture("graphics/icons/radar_circle.png");
            Global.getSettings().loadTexture("graphics/icons/danger3.png");
            Global.getSettings().loadTexture("graphics/icons/debris_field_icon.png");
            Global.getSettings().loadTexture("graphics/icons/intel/log_to_derelict_ship.png");
            Global.getSettings().loadTexture("graphics/icons/fleet_triangle.png");
            Global.getSettings().loadTexture("graphics/warroom/ship_arrow.png");
            Global.getSettings().loadTexture("graphics/stations/large_cache.png");
            Global.getSettings().loadTexture("graphics/stations/survey_probe.png");
            Global.getSettings().loadTexture("graphics/stations/probe.png");
            Global.getSettings().loadTexture("graphics/ships/nav_buoy.png");
            Global.getSettings().loadTexture("graphics/stations/cargo_pod_special.png");
            Global.getSettings().loadTexture("graphics/ships/derelict/derelict_mothership_pod_engine_left.png");
            Global.getSettings().loadTexture("graphics/debris/debris_lrg0.png");
            Global.getSettings().loadTexture("graphics/debris/debris_lrg1.png");
            Global.getSettings().loadTexture("graphics/ships/hound/hound_substandard.png");
            Global.getSettings().loadTexture("graphics/ships/cerberus/cerberus_substandard.png");
            Global.getSettings().loadTexture("graphics/ships/lasher/lasher_d.png");
            Global.getSettings().loadTexture("graphics/ships/buffalo/buffalo_d.png");
            Global.getSettings().loadTexture("graphics/ships/wayfarer/wayfarer.png");
            Global.getSettings().loadTexture("graphics/ships/shepherd/shepherd.png");
            Global.getSettings().loadTexture("graphics/ships/dram/dram.png");
        } catch (Throwable t) {}

        initDerelictPool();

        if (engine.getListenerManager() != null) {
            engine.getListenerManager().addListener(new AsteroidDamageModifier());
        }

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
            Global.getLogger(DynamicAsteroidsCombatPlugin.class).warn("Dynamic Asteroids: Failed to load mod settings; continuing with safe defaults.", t);
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

    public void applyPresetSettings(int preset) {
        switch (preset) {
            case 1: // Cinematic Calm (400 speed)
                setStreamSpeed(400.0f);
                minFieldRocks = 100;
                maxFieldRocks = 400;
                asteroidLifespan = 75.0f;
                targetedSnipeChance = 0.02f;
                figureEventInterval = 38.0f;
                enableMicroDodge = true;
                enableShieldReaction = true;
                enablePDTargeting = true;
                pdCriticalThreatsOnly = true;
                asteroidHealthMult = 0.50f;
                pdAsteroidDamageMult = 7.0f;
                asteroidCollisionDamageMult = 0.35f;
                derelictSpawnChance = 0.005f;
                debrisEasterEggChance = 0.015f;
                resourceEasterEggChance = 0.010f;
                otherEasterEggChance = 0.025f;
                easterEggSpeed = 300.0f;
                easterEggMagnetStrength = 3000.0f;
                derelictRescueHoldTime = 45.0f;
                derelictRescueChance = 1.0f;
                openSpaceEasterEggMult = 0.10f;
                break;
            case 2: // Balanced Standard (600 speed)
                setStreamSpeed(600.0f);
                minFieldRocks = 200;
                maxFieldRocks = 800;
                asteroidLifespan = 60.0f;
                targetedSnipeChance = 0.05f;
                figureEventInterval = 28.0f;
                enableMicroDodge = true;
                enableShieldReaction = true;
                enablePDTargeting = true;
                pdCriticalThreatsOnly = true;
                asteroidHealthMult = 0.60f;
                pdAsteroidDamageMult = 6.0f;
                asteroidCollisionDamageMult = 1.0f;
                derelictSpawnChance = 0.015f;
                debrisEasterEggChance = 0.040f;
                resourceEasterEggChance = 0.020f;
                otherEasterEggChance = 0.060f;
                easterEggSpeed = 400.0f;
                easterEggMagnetStrength = 3000.0f;
                derelictRescueHoldTime = 60.0f;
                derelictRescueChance = 1.0f;
                openSpaceEasterEggMult = 0.14f;
                break;
            case 3: // Asteroid Storm (1200 speed)
                setStreamSpeed(1200.0f);
                minFieldRocks = 300;
                maxFieldRocks = 1000;
                asteroidLifespan = 45.0f;
                targetedSnipeChance = 0.08f;
                figureEventInterval = 18.0f;
                enableMicroDodge = true;
                enableShieldReaction = true;
                enablePDTargeting = true;
                pdCriticalThreatsOnly = false;
                asteroidHealthMult = 0.70f;
                pdAsteroidDamageMult = 6.5f;
                asteroidCollisionDamageMult = 1.8f;
                derelictSpawnChance = 0.035f;
                debrisEasterEggChance = 0.080f;
                resourceEasterEggChance = 0.040f;
                otherEasterEggChance = 0.120f;
                easterEggSpeed = 500.0f;
                easterEggMagnetStrength = 3500.0f;
                derelictRescueHoldTime = 60.0f;
                derelictRescueChance = 1.0f;
                openSpaceEasterEggMult = 0.16f;
                break;
            case 4: // Extreme Chaos: High Risk / High Reward (3000 speed, deadly collision, generous loot)
                setStreamSpeed(3000.0f);
                minFieldRocks = 500;
                maxFieldRocks = 1000;
                asteroidLifespan = 35.0f;
                targetedSnipeChance = 0.15f;
                figureEventInterval = 12.0f;
                enableMicroDodge = true;
                enableShieldReaction = true;
                enablePDTargeting = true;
                pdCriticalThreatsOnly = false;
                asteroidHealthMult = 0.80f;
                pdAsteroidDamageMult = 8.0f;
                asteroidCollisionDamageMult = 3.5f;
                derelictSpawnChance = 0.070f;
                debrisEasterEggChance = 0.220f;
                resourceEasterEggChance = 0.080f;
                otherEasterEggChance = 0.250f;
                easterEggSpeed = 650.0f;
                easterEggMagnetStrength = 4500.0f;
                derelictRescueHoldTime = 75.0f;
                derelictRescueChance = 1.0f;
                openSpaceEasterEggMult = 0.20f;
                break;
            default:
                break;
        }

    }

    private void setStreamSpeed(float newSpeed) {
        float oldSpeed = streamSpeed;
        if (engine != null && oldSpeed > 50f && newSpeed > 50f && Math.abs(oldSpeed - newSpeed) > 10f) {
            float speedRatio = newSpeed / oldSpeed;
            List<CombatEntityAPI> asteroids = engine.getAsteroids();
            if (asteroids != null) {
                for (CombatEntityAPI asteroid : asteroids) {
                    if (asteroid == null) continue;
                    if (asteroid.getVelocity() != null) {
                        asteroid.getVelocity().scale(speedRatio);
                    }

                    Object baseVx = asteroid.getCustomData().get(BASE_VX_KEY);
                    Object baseVy = asteroid.getCustomData().get(BASE_VY_KEY);
                    if (baseVx instanceof Float && baseVy instanceof Float) {
                        asteroid.setCustomData(BASE_VX_KEY, Float.valueOf(((Float) baseVx).floatValue() * speedRatio));
                        asteroid.setCustomData(BASE_VY_KEY, Float.valueOf(((Float) baseVy).floatValue() * speedRatio));
                    }
                }
            }
        }
        streamSpeed = newSpeed;
    }

    public String getPresetName() {
        switch (activePreset) {
            case 1: return IS_RUSSIAN ? "Кино" : "Cinematic";
            case 2: return IS_RUSSIAN ? "Баланс" : "Balanced";
            case 3: return IS_RUSSIAN ? "Шторм" : "Storm";
            case 4: return IS_RUSSIAN ? "Хаос" : "Chaos";
            default: return IS_RUSSIAN ? "Свой" : "Custom";
        }
    }

    private void loadSettings() {
        boolean lunaPresetLoaded = false;
        try {
            JSONObject config = Global.getSettings().loadJSON(SETTINGS_PATH);
            if (config != null) {
                activePreset = config.optInt("activePreset", activePreset);
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
                openSpaceEasterEggMult = (float) config.optDouble("openSpaceEasterEggMult", openSpaceEasterEggMult);

                enableMicroDodge = config.optBoolean("enableMicroDodge", enableMicroDodge);
                enableShieldReaction = config.optBoolean("enableShieldReaction", enableShieldReaction);
                enablePhaseReaction = config.optBoolean("enablePhaseReaction", enablePhaseReaction);
                enablePDTargeting = config.optBoolean("enablePDTargeting", enablePDTargeting);
                pdCriticalThreatsOnly = config.optBoolean("pdCriticalThreatsOnly", pdCriticalThreatsOnly);
                enablePDFluxSafety = config.optBoolean("enablePDFluxSafety", enablePDFluxSafety);
                pdMaxFluxThreshold = (float) config.optDouble("pdMaxFluxThreshold", pdMaxFluxThreshold);
                enableEarlySnipeEvasion = config.optBoolean("enableEarlySnipeEvasion", enableEarlySnipeEvasion);
                asteroidHealthMult = (float) config.optDouble("asteroidHealthMult", asteroidHealthMult);
                pdAsteroidDamageMult = (float) config.optDouble("pdAsteroidDamageMult", pdAsteroidDamageMult);
                asteroidCollisionDamageMult = (float) config.optDouble("asteroidCollisionDamageMult", asteroidCollisionDamageMult);
                derelictSpawnChance = (float) config.optDouble("derelictSpawnChance", derelictSpawnChance);
                debrisEasterEggChance = (float) config.optDouble("debrisEasterEggChance", debrisEasterEggChance);
                resourceEasterEggChance = (float) config.optDouble("resourceEasterEggChance", resourceEasterEggChance);
                otherEasterEggChance = (float) config.optDouble("otherEasterEggChance", otherEasterEggChance);
                easterEggSpeed = clampEasterEggSpeed((float) config.optDouble("easterEggSpeed", easterEggSpeed));
                easterEggMagnetStrength = clampEasterEggMagnetStrength((float) config.optDouble("easterEggMagnetStrength", easterEggMagnetStrength));
                derelictRescueHoldTime = (float) config.optDouble("derelictRescueHoldTime", derelictRescueHoldTime);
                derelictRescueChance = (float) config.optDouble("derelictRescueChance", derelictRescueChance);
                aiMinThreatVelocity = (float) config.optDouble("aiMinThreatVelocity", aiMinThreatVelocity);
                aiLookaheadTime = (float) config.optDouble("aiLookaheadTime", aiLookaheadTime);
                aiSafetyMargin = (float) config.optDouble("aiSafetyMargin", aiSafetyMargin);
                aiDodgeCooldown = (float) config.optDouble("aiDodgeCooldown", aiDodgeCooldown);
                aiMaxCheckInterval = (float) config.optDouble("aiMaxCheckInterval", aiMaxCheckInterval);
            }
        } catch (Throwable t) {
            Global.getLogger(DynamicAsteroidsCombatPlugin.class).warn("Dynamic Asteroids: Failed to load mod config; continuing with safe defaults.", t);
        }

        // LunaLib F2 overrides
        try {
            if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
                Integer nPreset = getLunaInt("da_activePreset");
                if (nPreset != null) {
                    activePreset = nPreset.intValue();
                    lunaPresetLoaded = true;
                }
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
                loadLunaSettingNumber("da_openSpaceEasterEggMult", openSpaceEasterEggMult);

                Boolean simVal = getLunaBoolean("da_enableInSimulation");
                if (simVal != null) enableInSimulation = simVal.booleanValue();

                Boolean ptVal = getLunaBoolean("da_enableParticleTrails");
                if (ptVal != null) enableParticleTrails = ptVal.booleanValue();

                Boolean mdVal = getLunaBoolean("da_enableMicroDodge");
                if (mdVal != null) enableMicroDodge = mdVal.booleanValue();

                Boolean srVal = getLunaBoolean("da_enableShieldReaction");
                if (srVal != null) enableShieldReaction = srVal.booleanValue();

                Boolean pdVal = getLunaBoolean("da_enablePDTargeting");
                if (pdVal != null) enablePDTargeting = pdVal.booleanValue();

                Boolean pdcVal = getLunaBoolean("da_pdCriticalThreatsOnly");
                if (pdcVal != null) pdCriticalThreatsOnly = pdcVal.booleanValue();

                Boolean bFluxSafe = getLunaBoolean("da_enablePDFluxSafety");
                if (bFluxSafe != null) enablePDFluxSafety = bFluxSafe.booleanValue();

                loadLunaSettingNumber("da_pdMaxFluxThreshold", pdMaxFluxThreshold);
                loadLunaSettingNumber("da_asteroidHealthMult", asteroidHealthMult);
                loadLunaSettingNumber("da_pdAsteroidDamageMult", pdAsteroidDamageMult);
                loadLunaSettingNumber("da_asteroidCollisionDamageMult", asteroidCollisionDamageMult);
                loadLunaSettingNumber("da_derelictSpawnChance", derelictSpawnChance);
                loadLunaSettingNumber("da_otherEasterEggChance", otherEasterEggChance);
                loadLunaSettingNumber("da_derelictRescueHoldTime", derelictRescueHoldTime);
                loadLunaSettingNumber("da_derelictRescueChance", derelictRescueChance);

                loadLunaSettingNumber("da_aiMinThreatVelocity", aiMinThreatVelocity);
                loadLunaSettingNumber("da_aiLookaheadTime", aiLookaheadTime);
            }
        } catch (Throwable t) {
            Global.getLogger(DynamicAsteroidsCombatPlugin.class).warn("Dynamic Asteroids: Failed to read LunaLib settings; using mod config values.", t);
        }

        if (activePreset < 0 || activePreset > 4) {
            Global.getLogger(DynamicAsteroidsCombatPlugin.class).warn("Dynamic Asteroids: Invalid preset " + activePreset + "; using Balanced (2).");
            activePreset = 2;
        }

        // Clamping (Defaults 200 to 300)
        if (minFieldRocks < 20) minFieldRocks = 100;
        if (maxFieldRocks < minFieldRocks) maxFieldRocks = minFieldRocks + 100;
        if (maxFieldRocks > 2500) maxFieldRocks = 2500;
        if (streamSpeed > 5000f) streamSpeed = 5000f;
        if (asteroidLifespan < 1.0f) asteroidLifespan = 60.0f;
        if (beamWidth < 150f) beamWidth = 650f;
        if (targetedSnipeChance < 0f) targetedSnipeChance = 0f;
        if (targetedSnipeChance > 1f) targetedSnipeChance = 1f;
        if (particleTrailChance < 0f) particleTrailChance = 0f;
        if (particleTrailChance > 1f) particleTrailChance = 1f;
        if (figureEventInterval < 10.0f) figureEventInterval = 28.0f;
        if (openSpaceMinRocks < 1) openSpaceMinRocks = 4;
        if (openSpaceMaxRocks < openSpaceMinRocks) openSpaceMaxRocks = openSpaceMinRocks + 5;
        if (openSpaceMinDelay <= 0f) openSpaceMinDelay = 2.0f;
        if (openSpaceMaxDelay < openSpaceMinDelay) openSpaceMaxDelay = openSpaceMinDelay + 3.0f;
        if (openSpaceEasterEggMult < 0f) openSpaceEasterEggMult = 0f;
        if (openSpaceEasterEggMult > 1.0f) openSpaceEasterEggMult = 1.0f;
        if (asteroidHealthMult < 0.1f) asteroidHealthMult = 0.1f;
        if (asteroidHealthMult > 5.0f) asteroidHealthMult = 5.0f;
        if (pdAsteroidDamageMult < 1.0f) pdAsteroidDamageMult = 1.0f;
        if (pdAsteroidDamageMult > 20.0f) pdAsteroidDamageMult = 20.0f;
        if (derelictSpawnChance < 0f) derelictSpawnChance = 0f;
        if (derelictSpawnChance > 1f) derelictSpawnChance = 1f;
        if (debrisEasterEggChance < 0f) debrisEasterEggChance = 0f;
        if (debrisEasterEggChance > 1f) debrisEasterEggChance = 1f;
        if (resourceEasterEggChance < 0f) resourceEasterEggChance = 0f;
        if (resourceEasterEggChance > 1f) resourceEasterEggChance = 1f;
        if (otherEasterEggChance < 0f) otherEasterEggChance = 0f;
        if (otherEasterEggChance > 1f) otherEasterEggChance = 1f;
        easterEggSpeed = clampEasterEggSpeed(easterEggSpeed);
        easterEggMagnetStrength = clampEasterEggMagnetStrength(easterEggMagnetStrength);
        if (activePreset > 0) {
            applyPresetSettings(activePreset);
        }
        if (!lunaPresetLoaded && Global.getSettings().getModManager().isModEnabled("lunalib")) {
            Global.getLogger(DynamicAsteroidsCombatPlugin.class).warn("Dynamic Asteroids: LunaLib did not return da_activePreset; using preset " + activePreset + ".");
        }
        Global.getLogger(DynamicAsteroidsCombatPlugin.class).info(
            "Dynamic Asteroids: Applied preset " + activePreset + " (" + getPresetName() + "), stream speed "
                + (int) streamSpeed + ", asteroid range " + minFieldRocks + "-" + maxFieldRocks + ".");
    }

    private void loadLunaSettingNumber(String fieldId, float currentVal) {
        Number num = getLunaNumber(fieldId);
        if (num == null) return;
        if (fieldId.equals("da_minFieldRocks")) minFieldRocks = num.intValue();
        else if (fieldId.equals("da_maxFieldRocks")) maxFieldRocks = num.intValue();
        else if (fieldId.equals("da_fieldSurgeSpeed")) streamSpeed = num.floatValue();
        else if (fieldId.equals("da_asteroidLifespan")) asteroidLifespan = num.floatValue();
        else if (fieldId.equals("da_aiMinThreatVelocity")) aiMinThreatVelocity = num.floatValue();
        else if (fieldId.equals("da_aiLookaheadTime")) aiLookaheadTime = num.floatValue();
        else if (fieldId.equals("da_beamWidth")) beamWidth = num.floatValue();
        else if (fieldId.equals("da_targetedSnipeChance")) targetedSnipeChance = num.floatValue();
        else if (fieldId.equals("da_particleTrailChance")) particleTrailChance = num.floatValue();
        else if (fieldId.equals("da_openSpaceMinRocks")) openSpaceMinRocks = num.intValue();
        else if (fieldId.equals("da_openSpaceMaxRocks")) openSpaceMaxRocks = num.intValue();
        else if (fieldId.equals("da_openSpaceMinDelay")) openSpaceMinDelay = num.floatValue();
        else if (fieldId.equals("da_openSpaceMaxDelay")) openSpaceMaxDelay = num.floatValue();
        else if (fieldId.equals("da_openSpaceEasterEggMult")) openSpaceEasterEggMult = num.floatValue();
        else if (fieldId.equals("da_activePreset")) activePreset = num.intValue();
        else if (fieldId.equals("da_pdMaxFluxThreshold")) pdMaxFluxThreshold = num.floatValue();
        else if (fieldId.equals("da_asteroidHealthMult")) asteroidHealthMult = num.floatValue();
        else if (fieldId.equals("da_pdAsteroidDamageMult")) pdAsteroidDamageMult = num.floatValue();
        else if (fieldId.equals("da_asteroidCollisionDamageMult")) asteroidCollisionDamageMult = num.floatValue();
        else if (fieldId.equals("da_derelictSpawnChance")) derelictSpawnChance = num.floatValue();
        else if (fieldId.equals("da_debrisEasterEggChance")) debrisEasterEggChance = num.floatValue();
        else if (fieldId.equals("da_resourceEasterEggChance")) resourceEasterEggChance = num.floatValue();
        else if (fieldId.equals("da_otherEasterEggChance")) otherEasterEggChance = num.floatValue();
        else if (fieldId.equals("da_easterEggSpeed")) setEasterEggSpeed(num.floatValue());
        else if (fieldId.equals("da_easterEggMagnetStrength")) easterEggMagnetStrength = clampEasterEggMagnetStrength(num.floatValue());
        else if (fieldId.equals("da_derelictRescueHoldTime")) derelictRescueHoldTime = num.floatValue();
        else if (fieldId.equals("da_derelictRescueChance")) derelictRescueChance = num.floatValue();
    }

        private void refreshLunaSettings() {
        if (engine == null) return;
        try {
            if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
                Boolean pdVal = getLunaBoolean("da_enablePDTargeting");
                if (pdVal != null) enablePDTargeting = pdVal.booleanValue();

                Boolean pdcVal = getLunaBoolean("da_pdCriticalThreatsOnly");
                if (pdcVal != null) pdCriticalThreatsOnly = pdcVal.booleanValue();

                Boolean mdVal = getLunaBoolean("da_enableMicroDodge");
                if (mdVal != null) enableMicroDodge = mdVal.booleanValue();

                Boolean srVal = getLunaBoolean("da_enableShieldReaction");
                if (srVal != null) enableShieldReaction = srVal.booleanValue();

                Integer nPreset = getLunaInt("da_activePreset");
                if (nPreset != null) {
                    int pVal = nPreset.intValue();
                    if (pVal != activePreset) {
                        activePreset = pVal;
                        if (activePreset > 0) {
                            applyPresetSettings(activePreset);
                            int minR = Math.min(minFieldRocks, maxFieldRocks);
                            int maxR = Math.max(minFieldRocks, maxFieldRocks);
                            battleTargetCount = minR + random.nextInt(Math.max(1, maxR - minR + 1));
                            maxBackgroundRocks = Math.min(450, battleTargetCount / 2);
                            backgroundSpawnInterval = Math.max(0.015f, 0.055f * (300f / Math.max(300, battleTargetCount)));
                            if (engine != null && engine.getCombatUI() != null) {
                                String presetLabel = getPresetName();
                                String msg = IS_RUSSIAN ? 
                                    ("Dynamic Asteroids: Переключен профиль на [" + presetLabel + "] (" + (int)streamSpeed + " ед/с, " + minR + " - " + maxR + " камней, таран x" + asteroidCollisionDamageMult + ")") :
                                    ("Dynamic Asteroids: Profile switched to [" + presetLabel + "] (" + (int)streamSpeed + " u/s, " + minR + " - " + maxR + " rocks, ram x" + asteroidCollisionDamageMult + ")");
                                engine.getCombatUI().addMessage(1, msg, Color.CYAN);
                            }
                        } else {
                            if (engine != null && engine.getCombatUI() != null) {
                                String msg = IS_RUSSIAN ? 
                                    "Dynamic Asteroids: Переключен профиль на [Свой] (Ручная настройка параметров)" :
                                    "Dynamic Asteroids: Profile switched to [Custom] (Manual slider tuning)";
                                engine.getCombatUI().addMessage(1, msg, Color.CYAN);
                            }
                        }
                    }
                }

                Boolean bFluxSafe2 = getLunaBoolean("da_enablePDFluxSafety");
                if (bFluxSafe2 != null) enablePDFluxSafety = bFluxSafe2.booleanValue();

                Number nPdFlux = getLunaNumber("da_pdMaxFluxThreshold");
                if (nPdFlux != null) pdMaxFluxThreshold = nPdFlux.floatValue();

                Boolean bTrails = getLunaBoolean("da_enableParticleTrails");
                if (bTrails != null) enableParticleTrails = bTrails.booleanValue();

                Boolean bEarlyEv = getLunaBoolean("da_enableEarlySnipeEvasion");
                if (bEarlyEv != null) enableEarlySnipeEvasion = bEarlyEv.booleanValue();

                if (activePreset == 0) {
                    // Preset 0 = Custom mode: user manually tunes individual sliders!
                    Number nSpeed = getLunaNumber("da_fieldSurgeSpeed");
                    if (nSpeed != null) {
                        float newSpeed = nSpeed.floatValue();
                        if (Math.abs(newSpeed - streamSpeed) > 10f) {
                            setStreamSpeed(newSpeed);
                        }
                    }

                    Number nMinRocks = getLunaNumber("da_minFieldRocks");
                    if (nMinRocks != null) minFieldRocks = nMinRocks.intValue();

                    Number nMaxRocks = getLunaNumber("da_maxFieldRocks");
                    if (nMaxRocks != null) maxFieldRocks = nMaxRocks.intValue();

                    Number nLifespan = getLunaNumber("da_asteroidLifespan");
                    if (nLifespan != null) asteroidLifespan = nLifespan.floatValue();

                    Number nBeamW = getLunaNumber("da_beamWidth");
                    if (nBeamW != null) beamWidth = nBeamW.floatValue();

                    Number nCollDmg = getLunaNumber("da_asteroidCollisionDamageMult");
                    if (nCollDmg != null) asteroidCollisionDamageMult = nCollDmg.floatValue();

                    Number nDerelictChance = getLunaNumber("da_derelictSpawnChance");
                    if (nDerelictChance != null) derelictSpawnChance = nDerelictChance.floatValue();

                    Number nDebrisChance = getLunaNumber("da_debrisEasterEggChance");
                    if (nDebrisChance != null) debrisEasterEggChance = nDebrisChance.floatValue();

                    Number nResourceChance = getLunaNumber("da_resourceEasterEggChance");
                    if (nResourceChance != null) resourceEasterEggChance = nResourceChance.floatValue();

                    Number nOtherChance = getLunaNumber("da_otherEasterEggChance");
                    if (nOtherChance != null) otherEasterEggChance = nOtherChance.floatValue();

                    Number nEggSpeed = getLunaNumber("da_easterEggSpeed");
                    if (nEggSpeed != null) setEasterEggSpeed(nEggSpeed.floatValue());

                    Number nMagStrength = getLunaNumber("da_easterEggMagnetStrength");
                    if (nMagStrength != null) easterEggMagnetStrength = clampEasterEggMagnetStrength(nMagStrength.floatValue());

                    Number nHoldTime = getLunaNumber("da_derelictRescueHoldTime");
                    if (nHoldTime != null) derelictRescueHoldTime = nHoldTime.floatValue();

                    Number nRescueChance = getLunaNumber("da_derelictRescueChance");
                    if (nRescueChance != null) derelictRescueChance = nRescueChance.floatValue();

                    Number nTrailChance = getLunaNumber("da_particleTrailChance");
                    if (nTrailChance != null) particleTrailChance = nTrailChance.floatValue();

                    Number nAstHp = getLunaNumber("da_asteroidHealthMult");
                    if (nAstHp != null) asteroidHealthMult = nAstHp.floatValue();

                    Number nPdDmg = getLunaNumber("da_pdAsteroidDamageMult");
                    if (nPdDmg != null) pdAsteroidDamageMult = nPdDmg.floatValue();

                    Number nThreatVel = getLunaNumber("da_aiMinThreatVelocity");
                    if (nThreatVel != null) aiMinThreatVelocity = nThreatVel.floatValue();

                    Number nLookahead = getLunaNumber("da_aiLookaheadTime");
                    if (nLookahead != null) aiLookaheadTime = nLookahead.floatValue();
                }
            }
        } catch (Throwable t) {
        }
    }

    private Number getLunaNumber(String fieldId) {
        try {
            Double value = LunaSettings.getDouble(MOD_ID, fieldId);
            if (value != null) return value;
        } catch (Throwable t) {
            logLunaSettingWarning(fieldId, t);
        }
        return null;
    }

    private Integer getLunaInt(String fieldId) {
        try {
            return LunaSettings.getInt(MOD_ID, fieldId);
        } catch (Throwable t) {
            logLunaSettingWarning(fieldId, t);
            return null;
        }
    }

    private Boolean getLunaBoolean(String fieldId) {
        try {
            return LunaSettings.getBoolean(MOD_ID, fieldId);
        } catch (Throwable t) {
            logLunaSettingWarning(fieldId, t);
        }
        return null;
    }

    private void logLunaSettingWarning(String fieldId, Throwable error) {
        if (lunaSettingWarnings.add(fieldId)) {
            Global.getLogger(DynamicAsteroidsCombatPlugin.class).warn("Dynamic Asteroids: Failed to read LunaLib setting " + fieldId + ".", error);
        }
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
                    if (ast.getCustomData().containsKey("IS_EASTER_EGG")) continue;
                    if (withSmoke && ast.getLocation() != null && !isTitleScreen) {
                        float radius = Math.max(25f, ast.getCollisionRadius());
                        engine.addSmokeParticle(ast.getLocation(), new Vector2f(0f, 0f), radius * 0.8f, 0.4f, 0.6f, dustColor);
                    }
                    engine.removeEntity(ast);
                }
            }
        }
    }

    private void queueDetectionSoundBurst() {
        pendingDetectionSoundPlays = Math.min(9, pendingDetectionSoundPlays + 3);
    }

    private void advanceDetectionSound(float amount) {
        if (pendingDetectionSoundPlays <= 0) return;
        detectionSoundTimer -= amount;
        while (pendingDetectionSoundPlays > 0 && detectionSoundTimer <= 0f) {
            try {
                Global.getSoundPlayer().playUISound("da_derelict_detect", 1.0f, 0.9f);
            } catch (Throwable t) {
                try {
                    Global.getSoundPlayer().playUISound("ui_radar_detect_vessel", 1.0f, 1.0f);
                } catch (Throwable t2) {}
            }
            pendingDetectionSoundPlays--;
            detectionSoundTimer += 0.25f;
        }
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (!active || engine == null) return;

        totalCombatTime += amount;
        advanceDetectionSound(amount);

        lunaCheckTimer += amount;
        if (lunaCheckTimer >= 0.5f) {
            lunaCheckTimer = 0f;
            refreshLunaSettings();
        }

        // Summon Derelict Hotkey: [ ' ] (KEY_APOSTROPHE / KEY_GRAVE)
        if (summonKeyCooldown > 0f) {
            summonKeyCooldown -= amount;
        }
        // Summon Loot Container Hotkey: [ ; ] (KEY_SEMICOLON)
        if (summonContainerKeyCooldown > 0f) {
            summonContainerKeyCooldown -= amount;
        }

        boolean summonTriggered = false;
        boolean summonContainerTriggered = false;

        if (events != null) {
            for (InputEventAPI event : events) {
                if (!event.isConsumed() && event.isKeyDownEvent()) {
                    int key = event.getEventValue();
                    if (key == Keyboard.KEY_APOSTROPHE || key == Keyboard.KEY_GRAVE) {
                        event.consume();
                        if (summonKeyCooldown <= 0f) summonTriggered = true;
                        break;
                    } else if (key == Keyboard.KEY_SEMICOLON) {
                        event.consume();
                        if (summonContainerKeyCooldown <= 0f) summonContainerTriggered = true;
                        break;
                    }
                }
            }
        }

        if (!summonTriggered && summonKeyCooldown <= 0f) {
            if (Keyboard.isKeyDown(Keyboard.KEY_APOSTROPHE) || Keyboard.isKeyDown(Keyboard.KEY_GRAVE)) {
                summonTriggered = true;
            }
        }
        if (!summonContainerTriggered && summonContainerKeyCooldown <= 0f) {
            if (Keyboard.isKeyDown(Keyboard.KEY_SEMICOLON)) {
                summonContainerTriggered = true;
            }
        }

        if (summonTriggered) {
            summonKeyCooldown = 0.5f;
            summonDerelictNearPlayer();
        }
        if (summonContainerTriggered) {
            summonContainerKeyCooldown = 0.5f;
            summonContainerNearPlayer();
        }

        // Process Simulation Hotkeys:
        // ONLY ACTIVE IN SIMULATION! In regular campaign battles, does nothing.
        if (engine.isSimulation() && events != null) {
            boolean shiftHeld = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            boolean ctrlHeld = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);

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
                    } else if (ctrlHeld && (key == Keyboard.KEY_EQUALS || key == Keyboard.KEY_ADD)) {
                        event.consume();
                        setStreamSpeed(Math.min(5000f, streamSpeed + 100f));
                        showLimitFeedback(IS_RUSSIAN ? ("Скорость потока УВЕЛИЧЕНА: " + (int)streamSpeed + " ед/с") : ("Stream Speed INCREASED: " + (int)streamSpeed + " u/s"), Color.CYAN);
                    } else if (ctrlHeld && (key == Keyboard.KEY_MINUS || key == Keyboard.KEY_SUBTRACT)) {
                        event.consume();
                        setStreamSpeed(Math.max(100f, streamSpeed - 100f));
                        showLimitFeedback(IS_RUSSIAN ? ("Скорость потока УМЕНЬШЕНА: " + (int)streamSpeed + " ед/с") : ("Stream Speed DECREASED: " + (int)streamSpeed + " u/s"), Color.YELLOW);
                    }
                }
            }
        }

        // Show simulation one-time hint
        if (engine.isSimulation() && !simulationHintShown) {
            simulationHintShown = true;
            if (engine.getCombatUI() != null) {
                engine.getCombatUI().addMessage(1, IS_RUSSIAN ? "[Dynamic Asteroids 2.0] [/] Вкл/Выкл | [Shift +/-] Лимит (+-50) | [Ctrl +/-] Скорость (+-100)" : "[Dynamic Asteroids 2.0] [/] Toggle ON/OFF | [Shift +/-] Limit (+-50) | [Ctrl +/-] Speed (+-100)", Color.CYAN);
                String simPresetMsg = IS_RUSSIAN ?
                    ("Dynamic Asteroids: Профиль [" + getPresetName() + "] (Скорость: " + (int)streamSpeed + " ед/с, " + minFieldRocks + " - " + maxFieldRocks + " камней, таран x" + asteroidCollisionDamageMult + ")") :
                    ("Dynamic Asteroids: Profile [" + getPresetName() + "] (Speed: " + (int)streamSpeed + " u/s, " + minFieldRocks + " - " + maxFieldRocks + " rocks, ram x" + asteroidCollisionDamageMult + ")");
                engine.getCombatUI().addMessage(1, simPresetMsg, Color.CYAN);
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
                            (IS_RUSSIAN ? "СИМУЛЯЦИЯ: ПОЯС [" : "SIMULATION: ASTEROID BELT [") + getPresetName() + (IS_RUSSIAN ? "] (" : "] (") + battleTargetCount + (IS_RUSSIAN ? " макс, " : " max, ") + (int)streamSpeed + (IS_RUSSIAN ? " ск)" : " spd)"),
                            streamInfo + (IS_RUSSIAN ? " | [Shift +/-] Лимит | [Ctrl +/-] Скорость" : " | [Shift +/-] Limit | [Ctrl +/-] Speed"),
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
                        (IS_RUSSIAN ? "ПОЯС: [" : "BELT: [") + getPresetName() + (IS_RUSSIAN ? "] (" : "] (") + (int)streamSpeed + (IS_RUSSIAN ? " ск)" : " spd)"),
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

                // Easter eggs are managed separately by advanceEasterEgg, never delete or prune here!
                if (ast.getCustomData().containsKey("IS_EASTER_EGG")) {
                    continue;
                }

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
                    if (dead != null && dead.getLocation() != null) {
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
                if (stream == null) continue;

                stream.timeElapsed += amount;
                stream.lifetime -= amount;
                if (stream.lifetime <= 0f && stream.rocksRemaining <= 0) {
                    if (finishedStreams == null) finishedStreams = new ArrayList<ActiveFigureStream>();
                    finishedStreams.add(stream);
                    continue;
                }

                if (stream.rocksRemaining > 0) {
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
            }

            if (finishedStreams != null) {
                activeStreams.removeAll(finishedStreams);
            }
        }

        // Fleet AI Collision Avoidance & Interception
        advanceAIAvoidance(amount);

        // Advance Easter Eggs (Derelicts, Caches, Probes, Tanks)
        if (activeEasterEggEntities != null && !activeEasterEggEntities.isEmpty()) {
            for (int i = activeEasterEggEntities.size() - 1; i >= 0; i--) {
                CombatEntityAPI egg = activeEasterEggEntities.get(i);
                if (egg == null || egg.isExpired()) {
                    activeEasterEggEntities.remove(i);
                    continue;
                }
                advanceEasterEgg(egg, amount);
            }
        }

        // Update Derelict HUD Status Compass & Timer
        updateDerelictHUDStatus();

        // Check combat conclusion for derelict recovery registration in campaign
        if (engine != null && engine.isCombatOver() && !campaignListenerRegistered) {
            if (!derelictSalvageRecords.isEmpty() && !engine.isSimulation() && engine.isInCampaign() && Global.getSector() != null) {
                campaignListenerRegistered = true;
                Global.getSector().getListenerManager().addListener(
                    new DerelictRecoveryCampaignListener(new ArrayList<DerelictSalvageRecord>(derelictSalvageRecords.values())),
                    true
                );
            }
        }
    }

    private final Map<String, SpriteAPI> spriteCache = new HashMap<String, SpriteAPI>();

    public SpriteAPI getCachedSprite(String category, String key, String fallbackPath) {
        String cacheKey = (category != null ? category : "") + ":" + (key != null ? key : "") + ":" + (fallbackPath != null ? fallbackPath : "");
        if (spriteCache.containsKey(cacheKey)) {
            return spriteCache.get(cacheKey);
        }

        SpriteAPI s = null;
        if (category != null && key != null) {
            try {
                SpriteAPI cand = Global.getSettings().getSprite(category, key);
                if (isValidSprite(cand)) {
                    s = cand;
                }
            } catch (Throwable t) {}
        }
        if (s == null && fallbackPath != null) {
            try {
                Global.getSettings().loadTexture(fallbackPath);
                SpriteAPI cand = Global.getSettings().getSprite(fallbackPath);
                if (isValidSprite(cand)) {
                    s = cand;
                }
            } catch (Throwable t) {}
        }

        spriteCache.put(cacheKey, s);
        return s;
    }

    public static boolean isValidSprite(SpriteAPI s) {
        if (s == null) return false;
        try {
            // Calling getWidth() throws NPE on broken proxy if the internal sprite (this.o00000) is null!
            return s.getWidth() > 0.001f;
        } catch (Throwable t) {
            return false;
        }
    }

    private void updateDerelictHUDStatus() {
        if (engine == null || activeEasterEggEntities == null || activeEasterEggEntities.isEmpty()) return;

        ShipAPI playerShip = engine.getPlayerShip();
        Vector2f pLoc = (playerShip != null) ? playerShip.getLocation() : null;

        CombatEntityAPI bestDerelict = null;
        float bestDistSq = Float.MAX_VALUE;
        int derelictCount = 0;
        int rescuedCount = 0;

        for (int i = 0; i < activeEasterEggEntities.size(); i++) {
            CombatEntityAPI ent = activeEasterEggEntities.get(i);
            if (ent == null || ent.isExpired() || (ent.getCustomData() != null && ent.getCustomData().containsKey("DA_EXPIRED")) || !engine.isEntityInPlay(ent)) continue;
            if (!"DERELICT".equals(ent.getCustomData().get("EASTER_EGG_TYPE"))) continue;

            derelictCount++;
            Boolean res = (Boolean) ent.getCustomData().get("DERELICT_RESCUED");
            if (res != null && res.booleanValue()) rescuedCount++;

            if (pLoc != null && ent.getLocation() != null) {
                float dx = ent.getLocation().x - pLoc.x;
                float dy = ent.getLocation().y - pLoc.y;
                float dSq = dx * dx + dy * dy;
                boolean isThisRescued = (res != null && res.booleanValue());
                boolean isBestRescued = false;
                if (bestDerelict != null) {
                    Boolean bRes = (Boolean) bestDerelict.getCustomData().get("DERELICT_RESCUED");
                    isBestRescued = (bRes != null && bRes.booleanValue());
                }

                if (bestDerelict == null) {
                    bestDerelict = ent;
                    bestDistSq = dSq;
                } else if (isBestRescued && !isThisRescued) {
                    bestDerelict = ent;
                    bestDistSq = dSq;
                } else if (isBestRescued == isThisRescued && dSq < bestDistSq) {
                    bestDerelict = ent;
                    bestDistSq = dSq;
                }
            } else if (bestDerelict == null) {
                bestDerelict = ent;
            }
        }

        if (bestDerelict == null) return;
        activeDerelictEntity = bestDerelict;

        Vector2f dLoc = bestDerelict.getLocation();
        if (dLoc == null) return;

        String hullName = (String) bestDerelict.getCustomData().get("DERELICT_NAME");
        if (hullName == null) hullName = IS_RUSSIAN ? "Остов корабля" : "Derelict ship";

        Float curT = (Float) bestDerelict.getCustomData().get("DERELICT_HOLD_TIMER");
        float holdT = (curT != null) ? curT.floatValue() : 0f;
        Boolean rescued = (Boolean) bestDerelict.getCustomData().get("DERELICT_RESCUED");
        boolean isRescued = (rescued != null && rescued.booleanValue());

        String hullSizeStr = (String) bestDerelict.getCustomData().get("DERELICT_HULL_SIZE");
        String sizeTag = "";
        if ("CAPITAL_SHIP".equals(hullSizeStr)) sizeTag = IS_RUSSIAN ? " [Линкор]" : " [Capital]";
        else if ("CRUISER".equals(hullSizeStr)) sizeTag = IS_RUSSIAN ? " [Крейсер]" : " [Cruiser]";
        else if ("DESTROYER".equals(hullSizeStr)) sizeTag = IS_RUSSIAN ? " [Эсминец]" : " [Destroyer]";
        else if ("FRIGATE".equals(hullSizeStr)) sizeTag = IS_RUSSIAN ? " [Фрегат]" : " [Frigate]";

        String countSuffix = sizeTag + ((derelictCount > 1) ? (" [" + (isRescued ? rescuedCount : (derelictCount - rescuedCount)) + "/" + derelictCount + "]") : "");
        String title;
        String data;

        if (pLoc != null) {
            float dx = dLoc.x - pLoc.x;
            float dy = dLoc.y - pLoc.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);

            float angleDeg = (float) Math.toDegrees(Math.atan2(dy, dx));
            if (angleDeg < 0) angleDeg += 360f;
            float relAngle = angleDeg - playerShip.getFacing();
            while (relAngle > 180f) relAngle -= 360f;
            while (relAngle < -180f) relAngle += 360f;

            int clock = Math.round((90f - relAngle) / 30f);
            while (clock <= 0) clock += 12;
            while (clock > 12) clock -= 12;

            String arrow = "↑";
            if (relAngle > -22.5f && relAngle <= 22.5f) arrow = "↑";
            else if (relAngle > 22.5f && relAngle <= 67.5f) arrow = "↖";
            else if (relAngle > 67.5f && relAngle <= 112.5f) arrow = "←";
            else if (relAngle > 112.5f && relAngle <= 157.5f) arrow = "↙";
            else if (relAngle < -22.5f && relAngle >= -67.5f) arrow = "↗";
            else if (relAngle < -67.5f && relAngle >= -112.5f) arrow = "→";
            else if (relAngle < -112.5f && relAngle >= -157.5f) arrow = "↘";
            else arrow = "↓";

            if (isRescued) {
                title = (IS_RUSSIAN ? ("ОСТОВ СПАСЕН: " + hullName) : ("RESCUED: " + hullName)) + countSuffix;
                data = IS_RUSSIAN ?
                    String.format("%s Дист: %d ед. (на %d ч.) | СИСТЕМЫ СТАБИЛЬНЫ", arrow, (int)dist, clock) :
                    String.format("%s Dist: %d su (%d o'clock) | SYSTEMS STABLE", arrow, (int)dist, clock);
            } else {
                int pct = Math.min(100, Math.round((holdT / derelictRescueHoldTime) * 100f));
                title = (IS_RUSSIAN ? ("СИГНАЛ SOS: " + hullName) : ("SOS SIGNAL: " + hullName)) + countSuffix;
                data = IS_RUSSIAN ?
                    String.format("%s Дист: %d ед. (на %d ч.) | Стабилизация: %d%% (%d/%d с)", arrow, (int)dist, clock, pct, (int)holdT, (int)derelictRescueHoldTime) :
                    String.format("%s Dist: %d su (%d o'clock) | Stabilizing: %d%% (%d/%d s)", arrow, (int)dist, clock, pct, (int)holdT, (int)derelictRescueHoldTime);
            }
        } else {
            title = (IS_RUSSIAN ? ("ОСТОВ: " + hullName) : ("DERELICT: " + hullName)) + countSuffix;
            data = isRescued ?
                (IS_RUSSIAN ? "Системы стабилизированы" : "Systems stabilized") :
                (IS_RUSSIAN ? String.format("Стабилизация: %d/%d с", (int)holdT, (int)derelictRescueHoldTime) : String.format("Hold: %d/%d s", (int)holdT, (int)derelictRescueHoldTime));
        }

        try {
            engine.maintainStatusForPlayerShip("DA_DERELICT_HUD", "graphics/icons/intel/log_to_derelict_ship.png", title, data, false);
        } catch (Throwable t) {}
    }

    public void summonDerelictNearPlayer() {
        if (engine == null) return;

        Vector2f spawnLoc = new Vector2f(0f, 0f);
        Vector2f driftVel = new Vector2f(0f, 0f);

        ShipAPI playerShip = engine.getPlayerShip();
        if (playerShip != null && playerShip.getLocation() != null) {
            float playerFacingRad = (float) Math.toRadians(playerShip.getFacing());
            float dist = 750f + random.nextFloat() * 250f;
            float spawnAngle = playerFacingRad + (random.nextFloat() - 0.5f) * 0.5f;
            spawnLoc.x = playerShip.getLocation().x + (float) Math.cos(spawnAngle) * dist;
            spawnLoc.y = playerShip.getLocation().y + (float) Math.sin(spawnAngle) * dist;

            float driftAngle = random.nextFloat() * 6.28f;
            float driftSpeed = 35f + random.nextFloat() * 25f;
            driftVel.x = (float) Math.cos(driftAngle) * driftSpeed;
            driftVel.y = (float) Math.sin(driftAngle) * driftSpeed;
        } else {
            spawnLoc.x = (random.nextFloat() - 0.5f) * 600f;
            spawnLoc.y = (random.nextFloat() - 0.5f) * 600f;
            driftVel.x = (random.nextFloat() - 0.5f) * 40f;
            driftVel.y = (random.nextFloat() - 0.5f) * 40f;
        }

        CombatEntityAPI derelict = spawnDerelictAtLocation(spawnLoc.x, spawnLoc.y, driftVel.x, driftVel.y);
        if (derelict != null) {
            String hullName = (String) derelict.getCustomData().get("DERELICT_NAME");
            try {
                Global.getSoundPlayer().playUISound("ui_rep_raise", 1.0f, 1.0f);
            } catch (Throwable t) {}

            if (engine.getCombatUI() != null) {
                String msg = IS_RUSSIAN ?
                    ("СЕНСОРЫ: По экстренному вызову ['] обнаружен остов (" + hullName + ")!") :
                    ("SENSORS: Summoned derelict ship via ['] detected (" + hullName + ")!");
                engine.getCombatUI().addMessage(1, msg, new Color(80, 255, 160));
            }

            engine.addSmoothParticle(spawnLoc, driftVel, 250f, 1.0f, 1.4f, new Color(80, 230, 255, 240));
        }
    }

    public void summonContainerNearPlayer() {
        if (engine == null) return;

        Vector2f spawnLoc = new Vector2f(0f, 0f);
        Vector2f driftVel = new Vector2f(0f, 0f);

        ShipAPI playerShip = engine.getPlayerShip();
        if (playerShip != null && playerShip.getLocation() != null) {
            float playerFacingRad = (float) Math.toRadians(playerShip.getFacing());
            float dist = 650f + random.nextFloat() * 200f;
            float spawnAngle = playerFacingRad + (random.nextFloat() - 0.5f) * 0.4f;
            spawnLoc.x = playerShip.getLocation().x + (float) Math.cos(spawnAngle) * dist;
            spawnLoc.y = playerShip.getLocation().y + (float) Math.sin(spawnAngle) * dist;

            float driftAngle = random.nextFloat() * 6.28f;
            float driftSpeed = 25f + random.nextFloat() * 20f;
            driftVel.x = (float) Math.cos(driftAngle) * driftSpeed;
            driftVel.y = (float) Math.sin(driftAngle) * driftSpeed;
        } else {
            spawnLoc.x = (random.nextFloat() - 0.5f) * 500f;
            spawnLoc.y = (random.nextFloat() - 0.5f) * 500f;
            driftVel.x = (random.nextFloat() - 0.5f) * 30f;
            driftVel.y = (random.nextFloat() - 0.5f) * 30f;
        }

        String[] lootTypes = new String[] { "CACHE", "PROBE", "CRYO_POD", "NAV_BUOY", "MOTHERSHIP_ENGINE", "FIGHTER" };
        String chosenLootType = lootTypes[random.nextInt(lootTypes.length)];

        CombatEntityAPI container = spawnOtherEasterEggAtLocation(spawnLoc.x, spawnLoc.y, driftVel.x, driftVel.y, chosenLootType);
        if (container != null) {
            try {
                Global.getSoundPlayer().playUISound("ui_cargo_transfer", 1.0f, 1.0f);
            } catch (Throwable t) {}

            String typeLabel;
            Color alertCol;
            if ("CACHE".equals(chosenLootType)) {
                typeLabel = IS_RUSSIAN ? "Грузовой тайник снабжения Домена" : "Domain Supply Cache";
                alertCol = new Color(255, 215, 60);
            } else if ("PROBE".equals(chosenLootType)) {
                typeLabel = IS_RUSSIAN ? "Геологоразведочный зонд Домена" : "Domain Survey Probe";
                alertCol = new Color(80, 230, 255);
            } else if ("CRYO_POD".equals(chosenLootType)) {
                typeLabel = IS_RUSSIAN ? "Аварийная криокапсула выживших" : "Emergency Cryo Stasis Pod";
                alertCol = new Color(60, 255, 140);
            } else if ("NAV_BUOY".equals(chosenLootType)) {
                typeLabel = IS_RUSSIAN ? "Навигационно-сенсорный буй" : "Navigational Sensor Buoy";
                alertCol = new Color(190, 130, 255);
            } else if ("FIGHTER".equals(chosenLootType)) {
                String fName = (String) container.getCustomData().get("FIGHTER_WING_NAME");
                typeLabel = IS_RUSSIAN ?
                    ("Дрейфующий истребитель (" + (fName != null ? fName : "Крыло") + ")") :
                    ("Drifting Fighter (" + (fName != null ? fName : "Wing") + ")");
                alertCol = new Color(100, 240, 255);
            } else {
                typeLabel = IS_RUSSIAN ? "Древний двигатель флагмана" : "Ancient Flagship Engine Debris";
                alertCol = new Color(200, 215, 230);
            }

            if (engine.getCombatUI() != null) {
                String msg = IS_RUSSIAN ?
                    ("СЕНСОРЫ: По экстренному вызову [;] обнаружен " + typeLabel + "!") :
                    ("SENSORS: Summoned container via [;] detected (" + typeLabel + ")!");
                engine.getCombatUI().addMessage(1, msg, alertCol);
            }

            engine.addSmoothParticle(spawnLoc, driftVel, 220f, 1.0f, 1.2f, alertCol);
        }
    }

    private void maintainVelocity(CombatEntityAPI ast) {
        if (ast == null) return;
        if (ast.getCustomData().containsKey("DA_NO_MAINTAIN_VELOCITY")) return;
        if (ast.getCustomData().containsKey("IS_EASTER_EGG")) return;

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

        // Rare Easter Eggs in Open Space (scaled down 5-10x to preserve quiet, atmospheric deep space)
        if (openSpaceEasterEggMult > 0f) {
            float roll = random.nextFloat();
            float openDerelictChance = derelictSpawnChance * openSpaceEasterEggMult;
            float openResourceChance = resourceEasterEggChance * openSpaceEasterEggMult;
            float openDebrisChance = debrisEasterEggChance * openSpaceEasterEggMult;

            boolean derelictAlreadyInPlay = (activeDerelictEntity != null && !activeDerelictEntity.isExpired() && engine.isEntityInPlay(activeDerelictEntity));

            if (!derelictAlreadyInPlay && roll < openDerelictChance) {
                Vector2f target = calculateEasterEggTarget(focalCX, focalCY);
                Vector2f pt = calculateEasterEggSpawnPoint(target, focalCX, focalCY, halfW, halfH, random.nextFloat() * 6.28f);
                float dx = target.x - pt.x;
                float dy = target.y - pt.y;
                float dLen = (float) Math.sqrt(dx * dx + dy * dy);
                float spd = 250.0f; // Derelict cruising speed
                float evx = (dLen > 0.001f) ? (dx / dLen) * spd : -spd;
                float evy = (dLen > 0.001f) ? (dy / dLen) * spd : 0f;
                spawnDerelictEasterEgg(pt.x, pt.y, evx, evy);
                return;
            } else if (roll < (openDerelictChance + openResourceChance)) {
                Vector2f target = calculateEasterEggTarget(focalCX, focalCY);
                Vector2f pt = calculateEasterEggSpawnPoint(target, focalCX, focalCY, halfW, halfH, random.nextFloat() * 6.28f);
                float dx = target.x - pt.x;
                float dy = target.y - pt.y;
                float dLen = (float) Math.sqrt(dx * dx + dy * dy);
                float spd = 250.0f; // Resource containers cruising speed
                float evx = (dLen > 0.001f) ? (dx / dLen) * spd : -spd;
                float evy = (dLen > 0.001f) ? (dy / dLen) * spd : 0f;
                spawnOtherEasterEgg(pt.x, pt.y, evx, evy, true);
                return;
            } else if (roll < (openDerelictChance + openResourceChance + openDebrisChance)) {
                float spd = 400.0f; // Debris and explosive tanks cruising speed
                float ratio = spd / Math.max(1f, speed);
                spawnOtherEasterEgg(spawnX, spawnY, vx * ratio, vy * ratio, false);
                return;
            }
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
    private Vector2f calculateEasterEggTarget(float focalCX, float focalCY) {
        ShipAPI player = (engine != null) ? engine.getPlayerShip() : null;
        if (player != null && player.isAlive() && !player.isHulk() && random.nextFloat() < 0.40f) {
            Vector2f pLoc = player.getLocation();
            Vector2f pVel = player.getVelocity();
            if (pLoc != null) {
                // Predict where player will be in 3.5 - 6.0 seconds
                float leadSec = 3.5f + random.nextFloat() * 2.5f;
                float leadX = (pVel != null) ? pVel.x * leadSec : 0f;
                float leadY = (pVel != null) ? pVel.y * leadSec : 0f;
                float tx = pLoc.x + leadX + (random.nextFloat() - 0.5f) * 450f;
                float ty = pLoc.y + leadY + (random.nextFloat() - 0.5f) * 450f;
                return new Vector2f(tx, ty);
            }
        }
        return new Vector2f(focalCX, focalCY);
    }

    private Vector2f calculateEasterEggSpawnPoint(Vector2f target, float focalCX, float focalCY, float halfW, float halfH, float baseAngle) {
        float off = 200f;
        switch (avalancheDirection) {
            case RIGHT_TO_LEFT:
                return new Vector2f(focalCX + halfW + off, target.y + (random.nextFloat() - 0.5f) * 400f);
            case LEFT_TO_RIGHT:
                return new Vector2f(focalCX - halfW - off, target.y + (random.nextFloat() - 0.5f) * 400f);
            case TOP_TO_BOTTOM:
                return new Vector2f(target.x + (random.nextFloat() - 0.5f) * 400f, focalCY + halfH + off);
            case BOTTOM_TO_TOP:
                return new Vector2f(target.x + (random.nextFloat() - 0.5f) * 400f, focalCY - halfH - off);
        }
        return getRectangleBoundaryPoint(focalCX, focalCY, halfW, halfH, baseAngle);
    }

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

        // Roll for Easter Egg (Derelict Ship, Resource Cache/Probe/Pod/Fighter, or Debris/Explosive Tank)
        // High-value items preferentially drift across player's operational zone for exciting gameplay!
        if (random.nextFloat() < derelictSpawnChance) {
            Vector2f target = calculateEasterEggTarget(focalCX, focalCY);
            Vector2f pt = calculateEasterEggSpawnPoint(target, focalCX, focalCY, halfW, halfH, baseEntryAngle);
            float dx = target.x - pt.x;
            float dy = target.y - pt.y;
            float dLen = (float) Math.sqrt(dx * dx + dy * dy);
            float spd = 250.0f; // Derelict cruising speed
            float vx = (dLen > 0.001f) ? (dx / dLen) * spd : -spd;
            float vy = (dLen > 0.001f) ? (dy / dLen) * spd : 0f;
            spawnDerelictEasterEgg(pt.x, pt.y, vx, vy);
        } else if (random.nextFloat() < resourceEasterEggChance) {
            Vector2f target = calculateEasterEggTarget(focalCX, focalCY);
            Vector2f pt = calculateEasterEggSpawnPoint(target, focalCX, focalCY, halfW, halfH, baseEntryAngle);
            float dx = target.x - pt.x;
            float dy = target.y - pt.y;
            float dLen = (float) Math.sqrt(dx * dx + dy * dy);
            float spd = 250.0f; // Resource containers cruising speed
            float vx = (dLen > 0.001f) ? (dx / dLen) * spd : -spd;
            float vy = (dLen > 0.001f) ? (dy / dLen) * spd : 0f;
            spawnOtherEasterEgg(pt.x, pt.y, vx, vy, true);
        } else if (random.nextFloat() < debrisEasterEggChance) {
            float entryAngle = baseEntryAngle + (random.nextFloat() - 0.5f) * 0.4f;
            Vector2f pt = getRectangleBoundaryPoint(focalCX, focalCY, halfW, halfH, entryAngle);
            float dx = focalCX - pt.x;
            float dy = focalCY - pt.y;
            float dLen = (float) Math.sqrt(dx * dx + dy * dy);
            float spd = 400.0f; // Debris and explosive tanks cruising speed
            float vx = (dLen > 0.001f) ? (dx / dLen) * spd : -spd;
            float vy = (dLen > 0.001f) ? (dy / dLen) * spd : 0f;
            spawnOtherEasterEgg(pt.x, pt.y, vx, vy, false);
        }


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
            stream.timeElapsed = 0f;
            stream.totalSpawnDuration = (rocksPerStream * stream.burstInterval) + 1.5f;
            stream.lifetime = (18000f / Math.max(streamSpeed, 400f)) + stream.totalSpawnDuration + 6f;

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
                    notifyWaveIncoming("СЕНСОРЫ: " + waveFigureType.getDisplayName().toUpperCase() + " прорезает сектор!");
                } else if (streamCount == 2) {
                    notifyWaveIncoming("СЕНСОРЫ: ДВОЙНОЙ ЗАЛП — 2 волны (" + waveFigureType.getDisplayName() + ") с разных углов!");
                } else {
                    notifyWaveIncoming("СЕНСОРЫ: ТРОЙНОЙ ШКВАЛ — 3 волны (" + waveFigureType.getDisplayName() + ") пересекают сектор!");
                }
            } else {
                if (streamCount == 1) {
                    notifyWaveIncoming("SENSORS: " + waveFigureType.getDisplayName().toUpperCase() + " sweeping across sector!");
                } else if (streamCount == 2) {
                    notifyWaveIncoming("SENSORS: DUAL CONVERGENCE — 2 " + waveFigureType.getDisplayName() + " waves converging!");
                } else {
                    notifyWaveIncoming("SENSORS: TRIPLE CROSS-BARRAGE — 3 " + waveFigureType.getDisplayName() + " waves intersecting!");
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
            if (asteroidHealthMult > 0f && asteroidHealthMult != 1.0f) {
                ast.setHitpoints(ast.getHitpoints() * asteroidHealthMult);
            }
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
                    if (!engine.isSimulation()) {
                        String presetMsg = IS_RUSSIAN ?
                            ("Dynamic Asteroids: Профиль [" + getPresetName() + "] (Скорость: " + (int)streamSpeed + " ед/с, " + minFieldRocks + " - " + maxFieldRocks + " камней, таран x" + asteroidCollisionDamageMult + ")") :
                            ("Dynamic Asteroids: Profile [" + getPresetName() + "] (Speed: " + (int)streamSpeed + " u/s, " + minFieldRocks + " - " + maxFieldRocks + " rocks, ram x" + asteroidCollisionDamageMult + ")");
                        engine.getCombatUI().addMessage(1, presetMsg, Color.CYAN);
                    }
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
                engine.getCombatUI().addMessage(1, text, new Color(255, 175, 50));
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
            boolean isSim = (engine != null && engine.isSimulation());

            if (isPlayer) {
                if (isSim) {
                    engine.addFloatingText(
                        ship.getLocation(),
                        IS_RUSSIAN ? "[СИМУЛЯЦИЯ] ВЕКТОРНЫЙ ШКВАЛ ПО ФЛАГМАНУ!" : "[SIMULATION] VECTOR BARRAGE ON FLAGSHIP!",
                        24f,
                        new Color(255, 60, 60),
                        ship,
                        0.35f,
                        3.5f
                    );
                    if (engine.getCombatUI() != null) {
                        engine.getCombatUI().addMessage(1, IS_RUSSIAN ?
                            "[СИМУЛЯЦИЯ] Проверка прочности: по курсу флагмана симулирован встречный шквал астероидов!" :
                            "[SIMULATION] Stress Test: Simulated meteor barrage spawned along flagship course!",
                            new Color(255, 70, 70)
                        );
                    }
                } else {
                    engine.addFloatingText(
                        ship.getLocation(),
                        IS_RUSSIAN ? "ОПАСНОСТЬ: МЕТЕОРИТНЫЙ ШКВАЛ ПО КУРСУ!" : "COLLISION ALERT: METEOR BARRAGE ON COURSE!",
                        26f,
                        new Color(255, 40, 40),
                        ship,
                        0.35f,
                        4.0f
                    );
                    if (engine.getCombatUI() != null) {
                        engine.getCombatUI().addMessage(1, IS_RUSSIAN ?
                            "ОПАСНОСТЬ СТОЛКНОВЕНИЯ: Прямо по курсу флагмана зафиксирован плотный град астероидов!" :
                            "COLLISION WARNING: Dense swarm of high-speed asteroids detected along flagship course!",
                            new Color(255, 50, 50)
                        );
                    }
                }
            } else {
                String shipName = ship.getName();
                String hullName = (ship.getHullSpec() != null) ? ship.getHullSpec().getHullName() : (IS_RUSSIAN ? "Корабль" : "Ship");
                if (isSim) {
                    engine.addFloatingText(
                        ship.getLocation(),
                        IS_RUSSIAN ? "[СИМУЛЯЦИЯ] ВЕКТОРНЫЙ ПОТОК ПО КОРАБЛЮ" : "[SIMULATION] VECTOR STREAM ON SHIP",
                        20f,
                        new Color(255, 100, 50),
                        ship,
                        0.35f,
                        3.0f
                    );
                    if (engine.getCombatUI() != null) {
                        engine.getCombatUI().addMessage(1, (IS_RUSSIAN ?
                            ("[СИМУЛЯЦИЯ] Симулирован встречный поток астероидов по курсу " + shipName + " (" + hullName + ")") :
                            ("[SIMULATION] Simulated meteor stream along " + shipName + " (" + hullName + ") course")),
                            new Color(255, 120, 50)
                        );
                    }
                } else {
                    engine.addFloatingText(
                        ship.getLocation(),
                        IS_RUSSIAN ? "ОПАСНОЕ СБЛИЖЕНИЕ: ГРАД АСТЕРОИДОВ!" : "HAZARD: METEOR STREAM ON COURSE!",
                        20f,
                        new Color(255, 90, 50),
                        ship,
                        0.35f,
                        3.0f
                    );
                    if (engine.getCombatUI() != null) {
                        engine.getCombatUI().addMessage(1, (IS_RUSSIAN ?
                            ("ВНИМАНИЕ: По курсу корабля " + shipName + " (" + hullName + ") движется встречный град астероидов!") :
                            ("WARNING: High-density meteor stream heading towards " + shipName + " (" + hullName + ")!")),
                            new Color(255, 110, 50)
                        );
                    }
                }
            }
        } catch (Throwable t) {
        }
    }

    // =========================================================================
    // FLEET AI COLLISION AVOIDANCE & DEFENSE SYSTEM
    // =========================================================================

    /**
     * Determines whether the specified ship is under direct manual player control.
     * In Starsector:
     * 1. Fleet ships controlled by AI have ship != engine.getPlayerShip() and an active ShipAI.
     * 2. The player's flagship has ship == engine.getPlayerShip().
     *    - In manual mode: ship.getShipAI() is null.
     *    - In automatic mode (Autopilot toggled on): ship.getShipAI() is NOT null,
     *      and engine.getCombatUI().isAutopilotOn() is true.
     *    - Note: engine.isUIAutopilotOn() in vanilla Starsector is inverted/buggy (returns true in manual mode).
     */
    private boolean isShipManualPlayerControlled(ShipAPI ship) {
        if (ship == null || engine == null) return false;
        if (ship != engine.getPlayerShip()) {
            return false; // Fleet ships are AI-controlled (automatic mode)
        }

        // The player flagship without an AI is definitively manual
        if (ship.getShipAI() == null) {
            return true;
        }

        // Direct CombatUI Autopilot check
        if (engine.getCombatUI() != null) {
            return !engine.getCombatUI().isAutopilotOn();
        }

        // Fallback: engine.isUIAutopilotOn() is inverted in vanilla (true when manual)
        return engine.isUIAutopilotOn();
    }

    private void advanceAIAvoidance(float amount) {
        if (engine == null || engine.isPaused()) return;

        advanceShipStates(amount);

        // Continuous formation wave corridor evasion while streams cross the sector
        if (enableEarlySnipeEvasion) {
            advanceActiveStreamsEvasion(amount);
        }

        // Advance imminent threats TTL
        if (!imminentThreats.isEmpty()) {
            java.util.Iterator<Map.Entry<ShipAPI, ImminentThreatData>> it = imminentThreats.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<ShipAPI, ImminentThreatData> entry = it.next();
                entry.getValue().ttl -= amount;
                if (entry.getValue().ttl <= 0f || entry.getValue().threat == null || entry.getValue().threat.isExpired()) {
                    it.remove();
                }
            }
        }

        // 1. Always refresh fast threats cache so PD and Evasion have fresh coordinates
        fastThreats.clear();
        List<CombatEntityAPI> asteroids = engine.getAsteroids();
        if (asteroids != null && !asteroids.isEmpty()) {
            final float minSpeedSq = aiMinThreatVelocity * aiMinThreatVelocity;
            for (int i = 0; i < asteroids.size(); i++) {
                CombatEntityAPI ast = asteroids.get(i);
                if (ast == null || ast.isExpired()) continue;

                Vector2f vel = ast.getVelocity();
                if (vel == null) continue;

                if (ast.getCustomData().containsKey("IS_EASTER_EGG")) continue;
                float speedSq = vel.x * vel.x + vel.y * vel.y;
                if (speedSq >= minSpeedSq) {
                    fastThreats.add(ast);
                }
            }
        }

        // 2. Ensure Point Defense weapons are decorated with AsteroidPDAutofirePlugin
        if (enablePDTargeting) {
            updateWrappedShips();
        }

        // 3. Evasion tick timer check
        aiTickTimer += amount;
        if (aiTickTimer < aiMaxCheckInterval) {
            return;
        }
        aiTickTimer = 0.0f;

        if (fastThreats.isEmpty()) return;

        List<ShipAPI> ships = engine.getShips();
        if (ships == null || ships.isEmpty()) return;

        // Dynamic lookahead distance based on current asteroid stream velocity
        float effectiveLookaheadTime = Math.max(aiLookaheadTime, 3.2f);
        float effectiveLookaheadDist = (Math.max(streamSpeed, 1200f) * effectiveLookaheadTime) + 600f;

        for (int sIdx = 0; sIdx < ships.size(); sIdx++) {
            ShipAPI ship = ships.get(sIdx);
            if (ship == null || !ship.isAlive() || ship.isHulk()) continue;

            // Never hijack direct manual control of the player ship (only evade if in automatic mode / autopilot)
            if (isShipManualPlayerControlled(ship)) {
                continue;
            }

            ShipAvoidanceState state = shipStates.get(ship);
            if (state != null && state.cooldown > 0.05f) {
                continue;
            }

            Vector2f shipLoc = ship.getLocation();
            Vector2f shipVel = ship.getVelocity();
            if (shipLoc == null || shipVel == null) continue;

            float shipRadius = ship.getCollisionRadius();
            float checkDist = shipRadius + effectiveLookaheadDist;
            float checkDistSq = checkDist * checkDist;

            CombatEntityAPI worstThreat = null;
            float lowestTimeToCollision = Float.MAX_VALUE;
            float worstDodgeDir = 0f;

            for (int tIdx = 0; tIdx < fastThreats.size(); tIdx++) {
                CombatEntityAPI threat = fastThreats.get(tIdx);
                Vector2f tLoc = threat.getLocation();
                Vector2f tVel = threat.getVelocity();

                float dx = tLoc.x - shipLoc.x;
                float dy = tLoc.y - shipLoc.y;
                float distSq = dx * dx + dy * dy;
                if (distSq > checkDistSq) continue;

                float rvx = tVel.x - shipVel.x;
                float rvy = tVel.y - shipVel.y;
                float relSpeedSq = rvx * rvx + rvy * rvy;
                if (relSpeedSq < (aiMinThreatVelocity * aiMinThreatVelocity)) continue;

                float dot = dx * rvx + dy * rvy;
                if (dot >= 0f) continue; // Moving away

                float tCpa = -dot / relSpeedSq;
                if (tCpa <= 0.01f || tCpa > effectiveLookaheadTime) continue;

                float cpaX = dx + rvx * tCpa;
                float cpaY = dy + rvy * tCpa;
                float cpaDistSq = cpaX * cpaX + cpaY * cpaY;

                float threatRadius = threat.getCollisionRadius();
                float totalSafeRadius = shipRadius + threatRadius + aiSafetyMargin;
                if (cpaDistSq < totalSafeRadius * totalSafeRadius) {
                    if (tCpa < lowestTimeToCollision) {
                        lowestTimeToCollision = tCpa;
                        worstThreat = threat;
                        worstDodgeDir = (rvx * dy - rvy * dx);
                    }
                }
            }

            if (worstThreat != null) {
                // Register in registry for PD interceptors
                imminentThreats.put(ship, new ImminentThreatData(worstThreat, lowestTimeToCollision, 0.35f));

                // Evasive maneuvering: ONLY for AI ships (never hijack player manual controls!)
                if (!isShipManualPlayerControlled(ship)) {
                    if (state == null || state.cooldown <= 0.05f) {
                        executeEvasiveReaction(ship, worstThreat, worstDodgeDir, lowestTimeToCollision);
                    }
                }
            }
        }
    }

    private void advanceShipStates(float amount) {
        if (shipStates.isEmpty()) return;

        List<ShipAPI> deadOrExpired = null;
        for (Map.Entry<ShipAPI, ShipAvoidanceState> entry : shipStates.entrySet()) {
            ShipAPI ship = entry.getKey();
            ShipAvoidanceState state = entry.getValue();

            if (!ship.isAlive() || ship.isHulk()) {
                if (deadOrExpired == null) deadOrExpired = new ArrayList<ShipAPI>();
                deadOrExpired.add(ship);
                continue;
            }

            if (state.cooldown > 0f) {
                state.cooldown -= amount;

                boolean isPlayerManual = isShipManualPlayerControlled(ship);
                if (isPlayerManual) {
                    // Instantly release control to the player if autopilot was disengaged!
                    state.activeCommand = null;
                    state.cooldown = 0f;
                    continue;
                }

                if (state.activeCommand != null && state.cooldown > 0f) {
                    ship.giveCommand(state.activeCommand, null, 0);
                }
                if (state.activeThrustCommand != null && state.cooldown > 0f) {
                    ship.giveCommand(state.activeThrustCommand, null, 0);
                }
                if (state.activeTurnCommand != null && state.cooldown > 0f) {
                    ship.giveCommand(state.activeTurnCommand, null, 0);
                }

                if (enableShieldReaction && ship.getShield() != null && ship.getShield().getType() == ShieldType.OMNI) {
                    ShieldAPI shield = ship.getShield();
                    float currentFacing = shield.getFacing();
                    float angleDiff = getShortestRotation(currentFacing, state.targetAngle);

                    float turnRate = shield.getRingRotationRate();
                    if (turnRate <= 0f) turnRate = 180f;
                    float maxTurn = turnRate * amount;

                    float step = Math.max(-maxTurn, Math.min(maxTurn, angleDiff));
                    float newFacing = normalizeAngle(currentFacing + step);
                    shield.forceFacing(newFacing);
                }
            } else {
                state.targetThreat = null;
                state.activeCommand = null;
                state.activeThrustCommand = null;
                state.activeTurnCommand = null;
            }
        }

        if (deadOrExpired != null) {
            for (ShipAPI dead : deadOrExpired) {
                shipStates.remove(dead);
            }
        }
    }

    /**
     * Translates a desired 2D world evasion vector into the ship's local coordinate system
     * and issues precise strafe, thrust, and steer commands. Works for ALL ship classes (Frigates to Capitals).
     */
    private void applyEvasiveManeuver(ShipAPI ship, float escapeWorldX, float escapeWorldY, float duration) {
        if (ship == null || isShipManualPlayerControlled(ship)) return;

        float facingRad = (float) Math.toRadians(ship.getFacing());
        float fwdX = (float) Math.cos(facingRad);
        float fwdY = (float) Math.sin(facingRad);
        float leftX = -fwdY;
        float leftY = fwdX;

        float dotLeft = escapeWorldX * leftX + escapeWorldY * leftY;
        float dotFwd = escapeWorldX * fwdX + escapeWorldY * fwdY;

        ShipAvoidanceState state = shipStates.get(ship);
        if (state == null) {
            state = new ShipAvoidanceState();
            shipStates.put(ship, state);
        }
        state.cooldown = Math.max(state.cooldown, duration);

        // Lateral thruster burn
        if (Math.abs(dotLeft) >= 0.18f) {
            ShipCommand strafeCmd = (dotLeft > 0f) ? ShipCommand.STRAFE_LEFT : ShipCommand.STRAFE_RIGHT;
            state.activeCommand = strafeCmd;
            ship.giveCommand(strafeCmd, null, 0);
        } else {
            state.activeCommand = null;
        }

        // Longitudinal thruster burn (accelerate away or reverse clear of trajectory)
        if (Math.abs(dotFwd) >= 0.22f) {
            ShipCommand thrustCmd = (dotFwd > 0f) ? ShipCommand.ACCELERATE : ShipCommand.DECELERATE;
            state.activeThrustCommand = thrustCmd;
            ship.giveCommand(thrustCmd, null, 0);
        } else {
            state.activeThrustCommand = null;
        }

        // For Cruisers and Capitals: turn towards escape heading to engage main propulsion thrusters
        if (ship.isCruiser() || ship.isCapital()) {
            float desiredEscapeAngle = (float) Math.toDegrees(Math.atan2(escapeWorldY, escapeWorldX));
            float angleDiff = getShortestRotation(ship.getFacing(), desiredEscapeAngle);
            if (Math.abs(angleDiff) > 15f && Math.abs(angleDiff) < 140f) {
                ShipCommand turnCmd = (angleDiff > 0f) ? ShipCommand.TURN_LEFT : ShipCommand.TURN_RIGHT;
                state.activeTurnCommand = turnCmd;
                ship.giveCommand(turnCmd, null, 0);
            } else {
                state.activeTurnCommand = null;
            }
        }

        ShipwideAIFlags flags = ship.getAIFlags();
        if (flags != null) {
            flags.setFlag(AIFlags.RUN_QUICKLY, duration);
            flags.setFlag(AIFlags.HAS_INCOMING_DAMAGE, duration);
        }
    }

    private void executeEvasiveReaction(ShipAPI ship, CombatEntityAPI threat, float crossDir, float timeToImpact) {
        if (stateIsNull(ship)) {
            shipStates.put(ship, new ShipAvoidanceState());
        }
        ShipAvoidanceState state = shipStates.get(ship);
        state.cooldown = Math.max(aiDodgeCooldown, 0.45f);
        state.targetThreat = threat;

        Vector2f sLoc = ship.getLocation();
        Vector2f tLoc = threat.getLocation();
        float angleToRock = computeAngle(sLoc.x, sLoc.y, tLoc.x, tLoc.y);
        state.targetAngle = angleToRock;

        boolean isPlayerManual = isShipManualPlayerControlled(ship);
        if (isPlayerManual) return;

        // Phase Cloak Reaction:
        if (enablePhaseReaction && ship.getPhaseCloak() != null && !ship.getPhaseCloak().isActive()) {
            if (ship.getFluxTracker() != null && ship.getFluxTracker().getFluxLevel() < 0.80f) {
                ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null, 0);
                ShipwideAIFlags flags = ship.getAIFlags();
                if (flags != null) {
                    flags.setFlag(AIFlags.STAY_PHASED, Math.max(0.4f, timeToImpact + 0.2f));
                }
                return;
            }
        }

        // Kinetic Thruster Evasion: works for ALL AI ships (Frigates, Destroyers, Cruisers, Capitals)!
        if (enableMicroDodge) {
            Vector2f tVel = threat.getVelocity();
            Vector2f sVel = ship.getVelocity();
            float rvx = tVel.x - sVel.x;
            float rvy = tVel.y - sVel.y;
            float rSpeed = (float) Math.sqrt(rvx * rvx + rvy * rvy);
            if (rSpeed > 10f) {
                float uX = rvx / rSpeed;
                float uY = rvy / rSpeed;
                float perpX = -uY;
                float perpY = uX;

                float dx = sLoc.x - tLoc.x;
                float dy = sLoc.y - tLoc.y;
                float signedLateral = dx * perpX + dy * perpY;
                float escapeDir = (signedLateral >= 0f) ? 1.0f : -1.0f;
                float escapeWorldX = perpX * escapeDir;
                float escapeWorldY = perpY * escapeDir;

                applyEvasiveManeuver(ship, escapeWorldX, escapeWorldY, Math.max(0.5f, timeToImpact + 0.25f));
            }
        }

        // Shields Reaction:
        if (enableShieldReaction && ship.getShield() != null) {
            ShieldAPI shield = ship.getShield();

            if (shield.getType() == ShieldType.OMNI) {
                if (shield.isOff() && ship.getFluxTracker() != null && ship.getFluxTracker().getFluxLevel() < 0.85f) {
                    ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null, 0);
                }
                ShipwideAIFlags flags = ship.getAIFlags();
                if (flags != null) {
                    flags.setFlag(AIFlags.KEEP_SHIELDS_ON, Math.max(0.35f, timeToImpact + 0.2f));
                    flags.setFlag(AIFlags.HAS_INCOMING_DAMAGE, 0.35f);
                }
            } else if (shield.getType() == ShieldType.FRONT) {
                float diff = Math.abs(getShortestRotation(ship.getFacing(), angleToRock));
                if (diff <= shield.getArc() * 0.5f + 15.0f) {
                    if (shield.isOff() && ship.getFluxTracker() != null && ship.getFluxTracker().getFluxLevel() < 0.85f) {
                        ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null, 0);
                    }
                    ShipwideAIFlags flags = ship.getAIFlags();
                    if (flags != null) {
                        flags.setFlag(AIFlags.KEEP_SHIELDS_ON, Math.max(0.35f, timeToImpact + 0.2f));
                        flags.setFlag(AIFlags.HAS_INCOMING_DAMAGE, 0.35f);
                    }
                } else {
                    updateFixedShieldFacingCommand(ship, state);
                }
            }
        }
    }

    /**
     * Continuous formation corridor clearing for ALL figure waves (Lance, Swarm, Rails, Fan, Megaliths).
     * Runs continuously during wave transit so all ships proactively sidestep the kill zone.
     */
    private void advanceActiveStreamsEvasion(float amount) {
        if (!enableEarlySnipeEvasion || activeStreams.isEmpty() || engine == null) return;
        List<ShipAPI> ships = engine.getShips();
        if (ships == null || ships.isEmpty()) return;

        for (int strIdx = 0; strIdx < activeStreams.size(); strIdx++) {
            ActiveFigureStream stream = activeStreams.get(strIdx);
            if (stream == null) continue;

            Vector2f oLoc = stream.origin;
            Vector2f vel = stream.velocity;
            float speed = (float) Math.sqrt(vel.x * vel.x + vel.y * vel.y);
            if (speed < 100f) speed = Math.max(streamSpeed, 400f);

            float dirX = vel.x / speed;
            float dirY = vel.y / speed;
            float perpX = -dirY;
            float perpY = dirX;

            for (int sIdx = 0; sIdx < ships.size(); sIdx++) {
                ShipAPI ship = ships.get(sIdx);
                if (ship == null || !ship.isAlive() || ship.isHulk()) continue;

                Vector2f sLoc = ship.getLocation();
                if (sLoc == null) continue;

                float dx = sLoc.x - oLoc.x;
                float dy = sLoc.y - oLoc.y;

                float proj = dx * dirX + dy * dirY;
                if (proj <= 50f) continue; // Behind origin

                // Precise wave timing: time until wave front arrives, and time until wave tail clears
                float timeToFront = (proj / speed) - stream.timeElapsed;
                float timeToTail = timeToFront + stream.totalSpawnDuration;

                // Active reaction window: wave is approaching within 5.5 seconds, and has not yet cleared
                if (timeToFront > 5.5f || timeToTail < -0.6f) continue;

                float signedLateral = dx * perpX + dy * perpY;
                float lateralDist = Math.abs(signedLateral);
                float shipRadius = ship.getCollisionRadius();

                float dangerHalfWidth = 0f;
                boolean inDanger = false;
                float escapeDir = (signedLateral >= 0f) ? 1.0f : -1.0f;

                switch (stream.type) {
                    case LANCE_BEAM:
                    case HEAVY_MEGALITHS:
                        dangerHalfWidth = (stream.beamSpread * 0.5f) + shipRadius + 100f;
                        inDanger = (lateralDist < dangerHalfWidth);
                        break;
                    case COMET_SWARM:
                        dangerHalfWidth = 900f + shipRadius + 100f;
                        inDanger = (lateralDist < dangerHalfWidth);
                        break;
                    case DUAL_RAILS:
                        float distToRailLeft = Math.abs(signedLateral - (-800f));
                        float distToRailRight = Math.abs(signedLateral - 800f);
                        dangerHalfWidth = 260f + shipRadius + 80f;
                        if (distToRailLeft < dangerHalfWidth) {
                            inDanger = true;
                            escapeDir = (signedLateral >= -800f) ? 1.0f : -1.0f;
                        } else if (distToRailRight < dangerHalfWidth) {
                            inDanger = true;
                            escapeDir = (signedLateral >= 800f) ? 1.0f : -1.0f;
                        }
                        break;
                    case EXPANDING_FAN:
                        dangerHalfWidth = (proj * 0.24f) + shipRadius + 100f;
                        inDanger = (lateralDist < dangerHalfWidth);
                        break;
                }

                if (!inDanger) continue;

                // Player flagship in manual control: never hijack helm!
                if (isShipManualPlayerControlled(ship)) {
                    continue;
                }

                // AI Ships: execute evasive burn out of the stream path
                float escapeWorldX = perpX * escapeDir;
                float escapeWorldY = perpY * escapeDir;

                float evadeDuration = Math.min(3.5f, Math.max(0.8f, timeToTail + 0.3f));
                applyEvasiveManeuver(ship, escapeWorldX, escapeWorldY, evadeDuration);

                // Shield orientation for incoming stream
                if (enableShieldReaction && ship.getShield() != null) {
                    float angleToOrigin = computeAngle(sLoc.x, sLoc.y, oLoc.x, oLoc.y);
                    ShipAvoidanceState state = shipStates.get(ship);
                    if (state == null) {
                        state = new ShipAvoidanceState();
                        shipStates.put(ship, state);
                    }
                    state.targetAngle = angleToOrigin;
                    if (state.cooldown < 1.0f) state.cooldown = 1.0f;

                    if (ship.getShield().isOff() && ship.getFluxTracker() != null && ship.getFluxTracker().getFluxLevel() < 0.85f) {
                        ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null, 0);
                    }
                }
            }
        }
    }

    private void updateWrappedShips() {
        if (!enablePDTargeting || engine == null) return;
        List<ShipAPI> ships = engine.getShips();
        if (ships == null || ships.isEmpty()) return;

        for (int s = 0; s < ships.size(); s++) {
            ShipAPI ship = ships.get(s);
            if (ship == null || !ship.isAlive() || ship.isHulk()) continue;

            List<WeaponGroupAPI> groups = ship.getWeaponGroupsCopy();
            if (groups == null || groups.isEmpty()) continue;

            for (int g = 0; g < groups.size(); g++) {
                WeaponGroupAPI group = groups.get(g);
                if (group == null) continue;

                List<WeaponAPI> weapons = group.getWeaponsCopy();
                List<AutofireAIPlugin> aiPlugins = group.getAIPlugins();
                if (weapons == null || aiPlugins == null) continue;

                int count = Math.min(weapons.size(), aiPlugins.size());
                for (int i = 0; i < count; i++) {
                    WeaponAPI weapon = weapons.get(i);
                    AutofireAIPlugin currentAI = aiPlugins.get(i);

                    if (currentAI instanceof AsteroidPDAutofirePlugin) {
                        continue;
                    }

                    if (weapon != null && isPDWeapon(weapon)) {
                        AsteroidPDAutofirePlugin wrapper = new AsteroidPDAutofirePlugin(currentAI, weapon);
                        try {
                            aiPlugins.set(i, wrapper);
                        } catch (Throwable t) {
                        }
                    }
                }
            }
        }
    }

    public static boolean isPDWeapon(WeaponAPI weapon) {
        if (weapon == null) return false;
        return weapon.hasAIHint(AIHints.PD) || weapon.hasAIHint(AIHints.PD_ALSO) || weapon.hasAIHint(AIHints.PD_ONLY);
    }

    /**
     * Finds the most critical incoming asteroid threat specifically reachable by this weapon mount and arc.
     */
    public static CombatEntityAPI findBestThreatForWeapon(WeaponAPI weapon) {
        if (instance == null || weapon == null) return null;
        ShipAPI ship = weapon.getShip();
        if (ship == null || !ship.isAlive() || ship.isHulk()) return null;

        List<CombatEntityAPI> threats = instance.fastThreats;
        if (threats == null || threats.isEmpty()) return null;

        Vector2f wLoc = weapon.getLocation();
        Vector2f sLoc = ship.getLocation();
        Vector2f sVel = ship.getVelocity();
        if (wLoc == null || sLoc == null || sVel == null) return null;

        float shipRadius = ship.getCollisionRadius();
        float weaponRange = weapon.getRange();
        float maxTrackRange = Math.max(weaponRange * 2.2f, 1100.0f);
        float maxTrackDistSq = maxTrackRange * maxTrackRange;

        CombatEntityAPI bestThreat = null;
        float bestScore = Float.MAX_VALUE;

        for (int i = 0; i < threats.size(); i++) {
            CombatEntityAPI ast = threats.get(i);
            if (ast == null || ast.isExpired()) continue;

            Vector2f aLoc = ast.getLocation();
            Vector2f aVel = ast.getVelocity();
            if (aLoc == null || aVel == null) continue;

            float dx = aLoc.x - wLoc.x;
            float dy = aLoc.y - wLoc.y;
            float distSq = dx * dx + dy * dy;
            if (distSq > maxTrackDistSq) continue;

            // Mount arc reachability check
            if (weapon.distanceFromArc(aLoc) > 28.0f) continue;

            // Relative closing velocity
            float rvx = aVel.x - sVel.x;
            float rvy = aVel.y - sVel.y;
            float relSpeedSq = rvx * rvx + rvy * rvy;
            if (relSpeedSq < 10000.0f) continue; // Minimum 100 u/s closing

            float sdx = aLoc.x - sLoc.x;
            float sdy = aLoc.y - sLoc.y;
            float dot = sdx * rvx + sdy * rvy;
            if (dot >= 0f) continue; // Moving away from the ship

            float tCpa = -dot / relSpeedSq;
            if (tCpa <= 0.01f || tCpa > 3.0f) continue;

            float cpaX = sdx + rvx * tCpa;
            float cpaY = sdy + rvy * tCpa;
            float cpaDistSq = cpaX * cpaX + cpaY * cpaY;

            float safeRad = shipRadius + ast.getCollisionRadius() + 80.0f;
            boolean isCollisionCourse = (cpaDistSq <= safeRad * safeRad);

            if (instance.pdCriticalThreatsOnly && !isCollisionCourse) {
                if (cpaDistSq > (safeRad + 140.0f) * (safeRad + 140.0f)) {
                    continue;
                }
            }

            float score = isCollisionCourse ? tCpa : (distSq / 1000000.0f + 5.0f);
            if (score < bestScore) {
                bestScore = score;
                bestThreat = ast;
            }
        }

        return bestThreat;
    }

    public static class AsteroidDamageModifier implements DamageTakenModifier {
        @Override
        public String modifyDamageTaken(Object paramObject, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean paramBoolean) {
            if (target == null || damage == null) return null;

            // 1. Asteroid collision damage to ships
            if (target instanceof ShipAPI && paramObject instanceof CombatEntityAPI) {
                CombatEntityAPI source = (CombatEntityAPI) paramObject;
                if (source.getCollisionClass() == CollisionClass.ASTEROID || source.getCustomData().containsKey(TRACKED_KEY)) {
                    if (!source.getCustomData().containsKey("IS_EASTER_EGG")) {
                        float mult = getAsteroidCollisionDamageMult();
                        if (Math.abs(mult - 1.0f) > 0.01f) {
                            damage.getModifier().modifyMult("da_asteroid_collision", mult);
                            return "da_asteroid_collision";
                        }
                    } else {
                        // Easter egg collided with a ship! Disable velocity correction permanently
                        source.getCustomData().put("DA_NO_MAINTAIN_VELOCITY", Boolean.TRUE);
                        if ("EXPLOSIVE_TANK".equals(source.getCustomData().get("EASTER_EGG_TYPE")) && instance != null) {
                            instance.triggerTankExplosion(source, (ShipAPI) target);
                        } else if (!"DERELICT".equals(source.getCustomData().get("EASTER_EGG_TYPE")) && instance != null) {
                            damage.getModifier().modifyMult("da_salvage_pickup", 0.0f);
                            instance.triggerSalvagePickup(source, (ShipAPI) target);
                            return "da_salvage_pickup";
                        }
                    }
                }
            }

            // 2. Arrival protection and interaction triggers for Easter Eggs & Derelicts
            if (target.getCustomData().containsKey("IS_EASTER_EGG")) {
                String eggType = (String) target.getCustomData().get("EASTER_EGG_TYPE");

                if ("DERELICT".equals(eggType)) {
                    boolean engaged = Boolean.TRUE.equals(target.getCustomData().get("DERELICT_PLAYER_ENGAGED"));
                    if (!engaged) {
                        ShipAPI sourceShip = extractShipFromSource(paramObject);
                        if (sourceShip != null && isPlayerSideShip(sourceShip)) {
                            target.getCustomData().put("DERELICT_PLAYER_ENGAGED", Boolean.TRUE);
                            engaged = true;
                        }
                    }

                    // Before player/ally engagement: immune to damage from asteroids and debris impacts
                    if (!engaged && paramObject instanceof CombatEntityAPI) {
                        CombatEntityAPI source = (CombatEntityAPI) paramObject;
                        if (source.getCollisionClass() == CollisionClass.ASTEROID || source.getCustomData().containsKey(TRACKED_KEY)) {
                            damage.getModifier().modifyMult("da_derelict_arrival_immunity", 0.0f);
                            return "da_derelict_arrival_immunity";
                        }
                    }
                } else {
                    if (paramObject instanceof ShipAPI || paramObject instanceof DamagingProjectileAPI || paramObject instanceof BeamAPI || paramObject instanceof MissileAPI) {
                        target.getCustomData().put("DA_NO_MAINTAIN_VELOCITY", Boolean.TRUE);
                        if (paramObject instanceof ShipAPI && instance != null) {
                            if ("EXPLOSIVE_TANK".equals(eggType)) {
                                instance.triggerTankExplosion(target, (ShipAPI) paramObject);
                            } else {
                                damage.getModifier().modifyMult("da_salvage_pickup", 0.0f);
                                instance.triggerSalvagePickup(target, (ShipAPI) paramObject);
                                return "da_salvage_pickup";
                            }
                        }
                    }

                    if (paramObject instanceof CombatEntityAPI) {
                        CombatEntityAPI source = (CombatEntityAPI) paramObject;
                        if (source.getCollisionClass() == CollisionClass.ASTEROID || source.getCustomData().containsKey(TRACKED_KEY)) {
                            if (!source.getCustomData().containsKey("IS_EASTER_EGG")) {
                                damage.getModifier().modifyMult("da_egg_rubbing_immunity", 0.0f);
                                return "da_egg_rubbing_immunity";
                            }
                        }
                    }
                }
            }

            // 3. Point Defense shatter bonus against normal asteroids
            if (target.getCollisionClass() == CollisionClass.ASTEROID || target.getCustomData().containsKey(TRACKED_KEY)) {
                if (!target.getCustomData().containsKey("IS_EASTER_EGG") && isPDSource(paramObject)) {
                    float mult = getPDAsteroidDamageMult();
                    if (mult > 1.0f) {
                        damage.getModifier().modifyMult("da_pd_shatter", mult);
                        return "da_pd_shatter";
                    }
                }
            }

            return null;
        }
    }

    public static float getPDAsteroidDamageMult() {
        return instance != null ? instance.pdAsteroidDamageMult : 6.0f;
    }

        public static ShipAPI extractShipFromSource(Object paramObject) {
        if (paramObject instanceof ShipAPI) {
            return (ShipAPI) paramObject;
        } else if (paramObject instanceof DamagingProjectileAPI) {
            DamagingProjectileAPI proj = (DamagingProjectileAPI) paramObject;
            if (proj.getSource() instanceof ShipAPI) return (ShipAPI) proj.getSource();
            if (proj.getWeapon() != null) return proj.getWeapon().getShip();
        } else if (paramObject instanceof BeamAPI) {
            return ((BeamAPI) paramObject).getSource();
        } else if (paramObject instanceof MissileAPI) {
            return ((MissileAPI) paramObject).getSource();
        } else if (paramObject instanceof WeaponAPI) {
            return ((WeaponAPI) paramObject).getShip();
        }
        return null;
    }

    public static WeaponAPI extractWeaponFromSource(Object paramObject) {
        if (paramObject instanceof DamagingProjectileAPI) {
            return ((DamagingProjectileAPI) paramObject).getWeapon();
        } else if (paramObject instanceof BeamAPI) {
            return ((BeamAPI) paramObject).getWeapon();
        } else if (paramObject instanceof WeaponAPI) {
            return (WeaponAPI) paramObject;
        }
        return null;
    }

    public static boolean isPDSource(Object paramObject) {
        WeaponAPI w = extractWeaponFromSource(paramObject);
        if (w != null) {
            return isPDWeapon(w);
        }
        if (paramObject instanceof MissileAPI) {
            MissileAPI m = (MissileAPI) paramObject;
            if (m.getWeaponSpec() != null && m.getWeaponSpec().getAIHints() != null) {
                return m.getWeaponSpec().getAIHints().contains(AIHints.PD) || 
                       m.getWeaponSpec().getAIHints().contains(AIHints.PD_ALSO) || 
                       m.getWeaponSpec().getAIHints().contains(AIHints.PD_ONLY);
            }
        }
        return false;
    }

    // --- Static Interop Methods for AsteroidPDAutofirePlugin ---
    public static boolean isPDTargetingEnabled() {
        return instance != null && instance.enablePDTargeting;
    }

    public static boolean isPDFluxExceeded(ShipAPI ship) {
        if (instance == null || !instance.enablePDFluxSafety || ship == null) return false;
        if (ship.getFluxTracker() == null) return false;
        return ship.getFluxTracker().getFluxLevel() >= instance.pdMaxFluxThreshold;
    }

    public static boolean isShipUnderManualPlayerControl(ShipAPI ship) {
        return instance != null && instance.isShipManualPlayerControlled(ship);
    }
    private boolean stateIsNull(ShipAPI ship) {
        return !shipStates.containsKey(ship) || shipStates.get(ship) == null;
    }

    private static float computeAngle(float fromX, float fromY, float toX, float toY) {
        float angle = (float) Math.toDegrees(Math.atan2(toY - fromY, toX - fromX));
        if (angle < 0f) angle += 360f;
        return angle;
    }

    private static float getShortestRotation(float from, float to) {
        float diff = (to - from) % 360f;
        if (diff > 180f) diff -= 360f;
        if (diff < -180f) diff += 360f;
        return diff;
    }

    private static void updateFixedShieldFacingCommand(ShipAPI ship, ShipAvoidanceState state) {
        if (ship == null || state == null) return;
        float angleDiff = getShortestRotation(ship.getFacing(), state.targetAngle);
        if (Math.abs(angleDiff) > 3f) {
            state.activeTurnCommand = angleDiff > 0f ? ShipCommand.TURN_LEFT : ShipCommand.TURN_RIGHT;
            ship.giveCommand(state.activeTurnCommand, null, 0);
        } else {
            state.activeTurnCommand = null;
        }
    }

    private static float normalizeAngle(float angle) {
        angle = angle % 360f;
        if (angle < 0f) angle += 360f;
        return angle;
    }


    public static float getAsteroidCollisionDamageMult() {
        return instance != null ? instance.asteroidCollisionDamageMult : 1.0f;
    }

    @Deprecated
    public static class EasterEggRenderer extends BaseCombatLayeredRenderingPlugin {
        public EasterEggRenderer(String spritePath, float radius, DynamicAsteroidsCombatPlugin parent) {
            super(CombatEngineLayers.ASTEROIDS_LAYER);
        }
    }

        public static class DerelictSalvageRecord {
        public String variantId;
        public String hullName;
        public float rescueChance;
        public float minDmodProb;
        public float maxDmodProb;
        public boolean outOfBounds = false;
        public boolean destroyed = false;

        public DerelictSalvageRecord(String variantId, String hullName, float rescueChance, float minDmodProb, float maxDmodProb) {
            this.variantId = variantId;
            this.hullName = hullName;
            this.rescueChance = rescueChance;
            this.minDmodProb = minDmodProb;
            this.maxDmodProb = maxDmodProb;
        }
    }

    public static class DerelictRecoveryCampaignListener extends BaseCampaignEventListener {
        private final List<DerelictSalvageRecord> records;
        private boolean processed = false;

        public DerelictRecoveryCampaignListener(List<DerelictSalvageRecord> records) {
            super(true); // transient
            this.records = new ArrayList<DerelictSalvageRecord>(records);
        }

        public DerelictRecoveryCampaignListener(String variantId, float rescueChance) {
            super(true); // transient
            this.records = new ArrayList<DerelictSalvageRecord>();
            if (variantId != null) {
                this.records.add(new DerelictSalvageRecord(variantId, variantId, rescueChance, 0.15f, 0.35f));
            }
        }

        @Override
        public void reportPlayerEngagement(EngagementResultAPI result) {
            if (processed || records == null || records.isEmpty()) return;
            processed = true;

            try {
                Random rnd = new Random();
                for (DerelictSalvageRecord rec : records) {
                    if (rec == null || rec.variantId == null) continue;
                    if (rec.rescueChance <= 0f) continue;
                    if (rnd.nextFloat() > rec.rescueChance) continue;

                    if (result != null && result.didPlayerWin() && result.getLoserResult() != null) {
                        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, rec.variantId);
                        member.setOwner(1);
                        FleetEncounterContext.prepareShipForRecovery(member, false, false, true, rec.minDmodProb, rec.maxDmodProb, rnd);
                        result.getLoserResult().getDisabled().add(member);
                    }
                }
            } catch (Throwable t) {}
        }
    }

    public void triggerTankExplosion(CombatEntityAPI tank, ShipAPI hitShip) {
        if (tank == null || tank.isExpired() || engine == null) return;
        removeEasterEggEntity(tank);

        Vector2f loc = tank.getLocation();
        if (loc == null) return;

        try {
            DamagingExplosionSpec spec = new DamagingExplosionSpec(
                0.18f, 350f, 160f, 1200f, 400f,
                CollisionClass.PROJECTILE_FF, CollisionClass.PROJECTILE_FIGHTER,
                3f, 6f, 0.8f, 50,
                new Color(255, 130, 40, 255), new Color(255, 200, 100, 200)
            );
            spec.setSoundSetId("explosion_ship");
            engine.spawnDamagingExplosion(spec, hitShip, loc);

            Global.getSoundPlayer().playSound("explosion_ship", 1.0f, 1.0f, loc, new Vector2f());

            if (engine.getCombatUI() != null && !isTitleScreen) {
                String shipLabel = (hitShip != null) ? hitShip.getName() : null;
                String msg = (shipLabel != null) ?
                    (IS_RUSSIAN ? ("ДЕТОНАЦИЯ: Взрывоопасный бак с горючим врезался в " + shipLabel + "!") : ("DETONATION: Explosive volatile tank detonated against " + shipLabel + "!")) :
                    (IS_RUSSIAN ? "ВЗРЫВ: Взрывоопасный бак сдетонировал от попадания!" : "EXPLOSION: Volatile fuel tank detonated from weapon fire!");
                engine.getCombatUI().addMessage(1, msg, new Color(255, 140, 40));
            }
        } catch (Throwable t) {}
    }

    public void triggerSalvagePickup(CombatEntityAPI entity, ShipAPI ship) {
        if (entity == null || entity.isExpired() || engine == null) return;
        String eggType = (String) entity.getCustomData().get("EASTER_EGG_TYPE");

        // Before player engagement: allow asteroid impact vector deflection but cap speed magnitude to cruising speed (250)
        if ("DERELICT".equals(eggType)) {
            boolean engaged = Boolean.TRUE.equals(entity.getCustomData().get("DERELICT_PLAYER_ENGAGED"));
            if (!engaged) {
                Vector2f vel = entity.getVelocity();
                if (vel != null) {
                    float curSpeedSq = vel.x * vel.x + vel.y * vel.y;
                    float maxCruising = 250.0f;
                    if (curSpeedSq > maxCruising * maxCruising) {
                        float curSpeed = (float) Math.sqrt(curSpeedSq);
                        vel.scale(maxCruising / curSpeed);
                    }
                }
            }
        }
        Vector2f loc = entity.getLocation();
        removeEasterEggEntity(entity);
        if (loc == null) return;

        try {
            Global.getSoundPlayer().playUISound("ui_cargo_transfer", 1.0f, 1.0f);
        } catch (Throwable t) {}

        for (int i = 0; i < 14; i++) {
            float angle = random.nextFloat() * 6.28f;
            float spd = 60f + random.nextFloat() * 120f;
            Vector2f pVel = new Vector2f((float)Math.cos(angle) * spd, (float)Math.sin(angle) * spd);
            engine.addHitParticle(loc, pVel, 8f + random.nextFloat() * 10f, 0.9f, 0.35f, new Color(255, 220, 100, 240));
        }

        if ("CACHE".equals(eggType)) {
            int supplies = 35 + random.nextInt(36);
            int fuel = 20 + random.nextInt(31);
            try {
                if (engine.isInCampaign() && Global.getSector() != null && Global.getSector().getPlayerFleet() != null) {
                    CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
                    if (cargo != null) {
                        cargo.addSupplies(supplies);
                        cargo.addFuel(fuel);
                    }
                }
            } catch (Throwable t) {}

            String txt = IS_RUSSIAN ?
                ("ТАЙНИК СОБРАН: +" + supplies + " припасов, +" + fuel + " топлива!") :
                ("CACHE RECOVERED: +" + supplies + " supplies, +" + fuel + " fuel!");
            engine.addFloatingText(loc, txt, 22f, new Color(255, 215, 60), entity, 0.3f, 3.0f);
            if (engine.getCombatUI() != null && !isTitleScreen) {
                engine.getCombatUI().addMessage(1, txt, new Color(255, 215, 60));
            }
        } else if ("PROBE".equals(eggType)) {
            int metals = 60 + random.nextInt(61);
            int rareMetals = 20 + random.nextInt(26);
            try {
                if (engine.isInCampaign() && Global.getSector() != null && Global.getSector().getPlayerFleet() != null) {
                    CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
                    if (cargo != null) {
                        cargo.addCommodity("metals", metals);
                        cargo.addCommodity("rare_metals", rareMetals);
                    }
                }
            } catch (Throwable t) {}

            String txt = IS_RUSSIAN ?
                ("ЗОНД ИССЛЕДОВАН: Данные датчиков, +" + metals + " металлов, +" + rareMetals + " редких металлов!") :
                ("PROBE SALVAGED: Sensor data, +" + metals + " metals, +" + rareMetals + " rare metals!");
            engine.addFloatingText(loc, txt, 22f, new Color(80, 230, 255), entity, 0.3f, 3.0f);
            if (engine.getCombatUI() != null && !isTitleScreen) {
                engine.getCombatUI().addMessage(1, txt, new Color(80, 230, 255));
            }
        } else if ("CRYO_POD".equals(eggType)) {
            int crew = 12 + random.nextInt(17);
            int supplies = 15 + random.nextInt(16);
            try {
                if (engine.isInCampaign() && Global.getSector() != null && Global.getSector().getPlayerFleet() != null) {
                    CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
                    if (cargo != null) {
                        cargo.addCrew(crew);
                        cargo.addSupplies(supplies);
                    }
                }
            } catch (Throwable t) {}

            String txt = IS_RUSSIAN ?
                ("КРИОКАПСУЛА СПАСЕНА: +" + crew + " выживших членов экипажа, +" + supplies + " припасов!") :
                ("CRYO POD SALVAGED: +" + crew + " rescued crew members, +" + supplies + " supplies!");
            engine.addFloatingText(loc, txt, 22f, new Color(60, 255, 140), entity, 0.3f, 3.0f);
            if (engine.getCombatUI() != null && !isTitleScreen) {
                engine.getCombatUI().addMessage(1, txt, new Color(60, 255, 140));
            }
        } else if ("NAV_BUOY".equals(eggType)) {
            int machinery = 15 + random.nextInt(16);
            int volatiles = 15 + random.nextInt(16);
            try {
                if (engine.isInCampaign() && Global.getSector() != null && Global.getSector().getPlayerFleet() != null) {
                    CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
                    if (cargo != null) {
                        cargo.addCommodity("heavy_machinery", machinery);
                        cargo.addCommodity("volatiles", volatiles);
                    }
                }
            } catch (Throwable t) {}

            String txt = IS_RUSSIAN ?
                ("БУЙ ДЕМОНТИРОВАН: +" + machinery + " тяжёлого оборудования, +" + volatiles + " летучих веществ!") :
                ("BUOY DECONSTRUCTED: +" + machinery + " heavy machinery, +" + volatiles + " volatiles!");
            engine.addFloatingText(loc, txt, 22f, new Color(190, 130, 255), entity, 0.3f, 3.0f);
            if (engine.getCombatUI() != null && !isTitleScreen) {
                engine.getCombatUI().addMessage(1, txt, new Color(190, 130, 255));
            }
        } else if ("FIGHTER".equals(eggType)) {
            String wingId = (String) entity.getCustomData().get("FIGHTER_WING_ID");
            String wingName = (String) entity.getCustomData().get("FIGHTER_WING_NAME");
            if (wingName == null && wingId != null) wingName = wingId;
            if (wingName == null) wingName = IS_RUSSIAN ? "Истребитель" : "Fighter";

            try {
                if (engine.isInCampaign() && Global.getSector() != null && Global.getSector().getPlayerFleet() != null) {
                    CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
                    if (cargo != null && wingId != null) {
                        cargo.addFighters(wingId, 1);
                    }
                }
            } catch (Throwable t) {}

            String txt = IS_RUSSIAN ?
                ("ИСТРЕБИТЕЛЬ ЭВАКУИРОВАН: " + wingName + " (чип LPC в трюме)!") :
                ("FIGHTER RECOVERED: " + wingName + " (LPC added to cargo)!");
            engine.addFloatingText(loc, txt, 22f, new Color(100, 240, 255), entity, 0.3f, 3.5f);
            if (engine.getCombatUI() != null && !isTitleScreen) {
                String logMsg = IS_RUSSIAN ?
                    ("СЕНСОРЫ: Истребитель " + wingName + " взят на борт и законсервирован в трюме!") :
                    ("SENSORS: Fighter " + wingName + " secured and stored in fleet cargo!");
                engine.getCombatUI().addMessage(1, logMsg, new Color(100, 240, 255));
            }
        } else {
            int metals = 60 + random.nextInt(41);
            int rareMetals = 15 + random.nextInt(16);
            try {
                if (engine.isInCampaign() && Global.getSector() != null && Global.getSector().getPlayerFleet() != null) {
                    CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
                    if (cargo != null) {
                        cargo.addCommodity("metals", metals);
                        cargo.addCommodity("rare_metals", rareMetals);
                    }
                }
            } catch (Throwable t) {}

            String txt = IS_RUSSIAN ?
                ("ДРЕВНИЙ ДВИГАТЕЛЬ РАЗОБРАН: +" + metals + " металлов, +" + rareMetals + " редких металлов!") :
                ("ANCIENT ENGINE DISMANTLED: +" + metals + " metals, +" + rareMetals + " rare metals!");
            engine.addFloatingText(loc, txt, 20f, new Color(200, 215, 230), entity, 0.3f, 2.5f);
            if (engine.getCombatUI() != null && !isTitleScreen) {
                engine.getCombatUI().addMessage(1, txt, new Color(200, 215, 230));
            }
        }
    }

    public void triggerSalvageDestruction(CombatEntityAPI entity) {
        if (entity == null || entity.isExpired() || engine == null) return;
        String eggType = (String) entity.getCustomData().get("EASTER_EGG_TYPE");
        Vector2f loc = entity.getLocation();
        removeEasterEggEntity(entity);
        if (loc == null) return;

        Color dustColor = new Color(140, 130, 115, 130);
        float radius = Math.max(30f, entity.getCollisionRadius());
        engine.addSmokeParticle(loc, new Vector2f(), radius * 1.2f, 0.5f, 0.8f, dustColor);

        try {
            Global.getSoundPlayer().playSound("explosion_small", 1.0f, 1.0f, loc, new Vector2f());
        } catch (Throwable t) {}

        if ("CACHE".equals(eggType)) {
            int supplies = 10 + random.nextInt(16);
            try {
                if (engine.isInCampaign() && Global.getSector() != null && Global.getSector().getPlayerFleet() != null) {
                    CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
                    if (cargo != null) cargo.addSupplies(supplies);
                }
            } catch (Throwable t) {}

            String txt = IS_RUSSIAN ?
                ("ТАЙНИК ВСКРЫТ ОГНЕМ: Уцелело +" + supplies + " припасов!") :
                ("CACHE BREACHED: Salvaged +" + supplies + " supplies!");
            engine.addFloatingText(loc, txt, 20f, new Color(255, 180, 60), entity, 0.3f, 2.5f);
            if (engine.getCombatUI() != null && !isTitleScreen) {
                engine.getCombatUI().addMessage(1, txt, new Color(255, 180, 60));
            }
        } else if ("PROBE".equals(eggType)) {
            int metals = 25 + random.nextInt(26);
            try {
                if (engine.isInCampaign() && Global.getSector() != null && Global.getSector().getPlayerFleet() != null) {
                    CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
                    if (cargo != null) cargo.addCommodity("metals", metals);
                }
            } catch (Throwable t) {}

            String txt = IS_RUSSIAN ?
                ("ЗОНД УНИЧТОЖЕН: Подобрано +" + metals + " металлов") :
                ("PROBE DESTROYED: Gathered +" + metals + " metals");
            engine.addFloatingText(loc, txt, 20f, new Color(120, 200, 240), entity, 0.3f, 2.5f);
        } else if ("CRYO_POD".equals(eggType)) {
            int supplies = 8 + random.nextInt(9);
            try {
                if (engine.isInCampaign() && Global.getSector() != null && Global.getSector().getPlayerFleet() != null) {
                    CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
                    if (cargo != null) cargo.addSupplies(supplies);
                }
            } catch (Throwable t) {}

            String txt = IS_RUSSIAN ?
                ("КРИОКАПСУЛА РАЗРУШЕНА: Экипаж погиб, уцелело +" + supplies + " припасов") :
                ("CRYO POD DESTROYED: Crew lost, salvaged +" + supplies + " supplies");
            engine.addFloatingText(loc, txt, 20f, new Color(255, 120, 100), entity, 0.3f, 2.5f);
        } else if ("NAV_BUOY".equals(eggType)) {
            int machinery = 8 + random.nextInt(9);
            try {
                if (engine.isInCampaign() && Global.getSector() != null && Global.getSector().getPlayerFleet() != null) {
                    CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
                    if (cargo != null) cargo.addCommodity("heavy_machinery", machinery);
                }
            } catch (Throwable t) {}

            String txt = IS_RUSSIAN ?
                ("БУЙ ВЗОРВАН: Подобрано +" + machinery + " оборудования") :
                ("BUOY DESTROYED: Gathered +" + machinery + " machinery");
            engine.addFloatingText(loc, txt, 20f, new Color(180, 140, 240), entity, 0.3f, 2.5f);
        } else if ("FIGHTER".equals(eggType)) {
            String wingName = (String) entity.getCustomData().get("FIGHTER_WING_NAME");
            if (wingName == null) wingName = IS_RUSSIAN ? "Истребитель" : "Fighter";
            int metals = 5 + random.nextInt(6);
            try {
                if (engine.isInCampaign() && Global.getSector() != null && Global.getSector().getPlayerFleet() != null) {
                    CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
                    if (cargo != null) cargo.addCommodity("metals", metals);
                }
            } catch (Throwable t) {}

            String txt = IS_RUSSIAN ?
                ("ИСТРЕБИТЕЛЬ РАЗРУШЕН ОГНЕМ: Чип LPC сгорел, собрано +" + metals + " металлов") :
                ("FIGHTER DESTROYED BY FIRE: LPC lost, salvaged +" + metals + " metals");
            engine.addFloatingText(loc, txt, 20f, new Color(255, 120, 90), entity, 0.3f, 2.8f);
            if (engine.getCombatUI() != null && !isTitleScreen) {
                engine.getCombatUI().addMessage(1, txt, new Color(255, 120, 90));
            }
        } else {
            int metals = 25 + random.nextInt(26);
            try {
                if (engine.isInCampaign() && Global.getSector() != null && Global.getSector().getPlayerFleet() != null) {
                    CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
                    if (cargo != null) cargo.addCommodity("metals", metals);
                }
            } catch (Throwable t) {}

            String txt = IS_RUSSIAN ?
                ("ОБЛОМКИ РАЗРУШЕНЫ: +" + metals + " металлов") :
                ("DEBRIS DESTROYED: +" + metals + " metals");
            engine.addFloatingText(loc, txt, 18f, new Color(180, 195, 210), entity, 0.3f, 2.2f);
        }
    }

    private void applyShieldMagnet(CombatEntityAPI entity, List<ShipAPI> ships, float amount) {
        if (easterEggMagnetStrength <= 0f || amount <= 0f || entity == null) return;
        if (!Boolean.TRUE.equals(entity.getCustomData().get("IS_EASTER_EGG"))) return;

        Vector2f eggLoc = entity.getLocation();
        Vector2f eggVelocity = entity.getVelocity();
        if (eggLoc == null || eggVelocity == null) return;

        ShipAPI closestShieldedShip = null;
        float closestDistanceSq = Float.MAX_VALUE;
        float activeMagnetRange = 1350f;

        for (int i = 0; i < ships.size(); i++) {
            ShipAPI ship = ships.get(i);
            if (ship == null || !ship.isAlive() || ship.isHulk() || !isPlayerSideShip(ship)) continue;
            if (ship.getShield() == null || ship.getShield().isOff()) continue;

            Vector2f shipLoc = ship.getLocation();
            if (shipLoc == null) continue;

            float dx = shipLoc.x - eggLoc.x;
            float dy = shipLoc.y - eggLoc.y;
            float distanceSq = dx * dx + dy * dy;
            float range = Math.max(1350f, ship.getCollisionRadius() * 4.5f + 550f);
            if (distanceSq <= range * range && distanceSq < closestDistanceSq) {
                closestShieldedShip = ship;
                closestDistanceSq = distanceSq;
                activeMagnetRange = range;
            }
        }

        if (closestShieldedShip == null || closestDistanceSq <= 0.01f) return;

        // Prevent velocity maintainer from resetting magnetic pull
        entity.getCustomData().put("DA_NO_MAINTAIN_VELOCITY", Boolean.TRUE);
        if ("DERELICT".equals(entity.getCustomData().get("EASTER_EGG_TYPE"))) {
            entity.getCustomData().put("DERELICT_PLAYER_ENGAGED", Boolean.TRUE);
        }

        Vector2f shipLoc = closestShieldedShip.getLocation();
        Vector2f shipVel = closestShieldedShip.getVelocity();
        float dx = shipLoc.x - eggLoc.x;
        float dy = shipLoc.y - eggLoc.y;
        float distance = (float) Math.sqrt(closestDistanceSq);
        if (distance < 1f) distance = 1f;

        float nx = dx / distance;
        float ny = dy / distance;

        // Smooth physical falloff: retains high force throughout range (100% near to 72% at edge)
        float normDist = Math.max(0f, Math.min(1f, distance / activeMagnetRange));
        float distanceFactor = 1.0f - 0.28f * (normDist * normDist);

        float effectiveAccel = easterEggMagnetStrength * distanceFactor;
        float pullDv = effectiveAccel * amount;

        // Direct inward attraction along the tractor axis
        eggVelocity.x += nx * pullDv;
        eggVelocity.y += ny * pullDv;

        // Lateral speed damping & relative velocity alignment:
        // Dampens only the perpendicular lateral speed relative to the ship, eliminating orbital slingshotting
        float sVx = (shipVel != null) ? shipVel.x : 0f;
        float sVy = (shipVel != null) ? shipVel.y : 0f;
        float relVx = eggVelocity.x - sVx;
        float relVy = eggVelocity.y - sVy;

        float radialSpeed = relVx * nx + relVy * ny; // positive = moving toward ship, negative = moving away
        float latVx = relVx - radialSpeed * nx;
        float latVy = relVy - radialSpeed * ny;

        float latDamp = Math.min(0.92f, 4.5f * amount);
        eggVelocity.x -= latVx * latDamp;
        eggVelocity.y -= latVy * latDamp;

        // Gentle braking when entering the close docking zone (< 220 units) to prevent violent hull crashes
        if (distance < 220f && radialSpeed > 280f) {
            float brake = Math.min(1.0f, 5.0f * amount);
            eggVelocity.x -= nx * (radialSpeed - 180f) * brake;
            eggVelocity.y -= ny * (radialSpeed - 180f) * brake;
        }

        // Visual tractor beam ions
        if (engine != null && random.nextFloat() < 0.50f) {
            Vector2f pVel = new Vector2f(nx * 180f, ny * 180f);
            engine.addSmoothParticle(
                eggLoc,
                pVel,
                16f + random.nextFloat() * 14f,
                0.90f,
                0.35f,
                new Color(70, 225, 255, 205)
            );
        }
    }

    private static boolean isPlayerSideShip(ShipAPI ship) {
        if (ship == null) return false;
        return ship.getOwner() == 0 || ship.isAlly();
    }

    private static float clampEasterEggSpeed(float speed) {
        if (Float.isNaN(speed) || Float.isInfinite(speed)) return 400.0f;
        return Math.max(100f, Math.min(3000f, speed));
    }

    private static float clampEasterEggMagnetStrength(float strength) {
        if (Float.isNaN(strength) || Float.isInfinite(strength)) return 3000.0f;
        return Math.max(0f, Math.min(10000f, strength));
    }

    private void setEasterEggSpeed(float newSpeed) {
        newSpeed = clampEasterEggSpeed(newSpeed);
        if (Math.abs(newSpeed - easterEggSpeed) < 1f) return;

        float speedRatio = newSpeed / easterEggSpeed;
        if (activeEasterEggEntities != null) {
            for (int i = 0; i < activeEasterEggEntities.size(); i++) {
                CombatEntityAPI egg = activeEasterEggEntities.get(i);
                if (egg == null) continue;
                if (egg.getVelocity() != null) {
                    egg.getVelocity().scale(speedRatio);
                }
                Object bvx = egg.getCustomData().get(BASE_VX_KEY);
                Object bvy = egg.getCustomData().get(BASE_VY_KEY);
                if (bvx instanceof Float && bvy instanceof Float) {
                    egg.setCustomData(BASE_VX_KEY, Float.valueOf(((Float) bvx).floatValue() * speedRatio));
                    egg.setCustomData(BASE_VY_KEY, Float.valueOf(((Float) bvy).floatValue() * speedRatio));
                }
            }
        }
        easterEggSpeed = newSpeed;
    }

    public void advanceEasterEgg(CombatEntityAPI entity, float amount) {
        if (entity == null || entity.isExpired() || engine == null) return;
        if (!engine.isEntityInPlay(entity)) {
            removeEasterEggEntity(entity);
            return;
        }

        Vector2f loc = entity.getLocation();
        if (loc == null) return;

        String eggType = (String) entity.getCustomData().get("EASTER_EGG_TYPE");

        float halfW = engine.getMapWidth() * 0.5f;
        float halfH = engine.getMapHeight() * 0.5f;

        // No soft boundary reflection / blocking: if they drift beyond the map edge,
        // it is the player's fault for losing them.
        // Deep space loss: if an easter egg drifts far beyond the sector boundaries (4000+ units), it is lost forever.
        float maxBoundaryX = halfW + 4000f;
        float maxBoundaryY = halfH + 4000f;
        if (Math.abs(loc.x) > maxBoundaryX || Math.abs(loc.y) > maxBoundaryY) {
            Boolean wasRescued = (Boolean) entity.getCustomData().get("DERELICT_RESCUED");
            String vId = (String) entity.getCustomData().get("DERELICT_VARIANT_ID");
            String dName = (String) entity.getCustomData().get("DERELICT_NAME");
            if (wasRescued != null && wasRescued.booleanValue() && vId != null) {
                DerelictSalvageRecord rec = derelictSalvageRecords.get(vId);
                if (rec != null) {
                    rec.outOfBounds = true;
                    rec.rescueChance = 0.10f; // 10% recovery chance when knocked out of bounds
                    rec.minDmodProb = 0.40f;
                    rec.maxDmodProb = 0.65f;
                }
                if (engine.getCombatUI() != null) {
                    String msg = IS_RUSSIAN ?
                        ("Остов " + dName + " унесло за границу сектора! Маяки активны (Шанс эвакуации: 10%).") :
                        ("Derelict " + dName + " drifted beyond sector perimeter! Beacons active (Recovery chance: 10%).");
                    engine.getCombatUI().addMessage(1, msg, new Color(255, 140, 60));
                }
            }
            removeEasterEggEntity(entity);
            return;
        }

        List<ShipAPI> ships = engine.getShips();
        if (ships != null && !ships.isEmpty()) {
            applyShieldMagnet(entity, ships, amount);
            float eRadius = entity.getCollisionRadius();
            for (int i = 0; i < ships.size(); i++) {
                ShipAPI ship = ships.get(i);
                if (ship == null || !ship.isAlive() || ship.isHulk()) continue;

                Vector2f sLoc = ship.getLocation();
                if (sLoc == null) continue;

                float dx = sLoc.x - loc.x;
                float dy = sLoc.y - loc.y;
                float distSq = dx * dx + dy * dy;
                float combinedRadius = ship.getCollisionRadius() + eRadius;

                if (distSq <= combinedRadius * combinedRadius) {
                    entity.getCustomData().put("DA_NO_MAINTAIN_VELOCITY", Boolean.TRUE);
                    if (isPlayerSideShip(ship) && "DERELICT".equals(eggType)) {
                        entity.getCustomData().put("DERELICT_PLAYER_ENGAGED", Boolean.TRUE);
                    }
                    if ("EXPLOSIVE_TANK".equals(eggType)) {
                        triggerTankExplosion(entity, ship);
                        return;
                    } else if (!"DERELICT".equals(eggType)) {
                        triggerSalvagePickup(entity, ship);
                        return;
                    }
                }
            }
        }

        if ("DERELICT".equals(eggType)) {
            // Proximity notification when player ship enters visual range
            ShipAPI pShip = engine.getPlayerShip();
            if (pShip != null && pShip.getLocation() != null) {
                float pdx = loc.x - pShip.getLocation().x;
                float pdy = loc.y - pShip.getLocation().y;
                float pDistSq = pdx * pdx + pdy * pdy;
                if (pDistSq <= 1800f * 1800f) {
                    Boolean nearNotified = (Boolean) entity.getCustomData().get("DA_NEAR_NOTIFIED");
                    if (nearNotified == null || !nearNotified.booleanValue()) {
                        entity.getCustomData().put("DA_NEAR_NOTIFIED", Boolean.TRUE);
                        String hName = (String) entity.getCustomData().get("DERELICT_NAME");
                        if (engine.getCombatUI() != null && !isTitleScreen) {
                            String nearTxt = IS_RUSSIAN ?
                                ("СЕНСОРЫ: Остов (" + hName + ") в зоне видимости! Защищайте его до стабилизации систем!") :
                                ("SENSORS: Derelict (" + hName + ") in visual range! Protect it until systems stabilize!");
                            engine.getCombatUI().addMessage(1, nearTxt, new Color(255, 215, 60));
                        }
                        try {
                            queueDetectionSoundBurst();
                        } catch (Throwable t) {
                            try {
                                Global.getSoundPlayer().playUISound("ui_radar_detect_vessel", 1.0f, 1.0f);
                            } catch (Throwable t2) {}
                        }
                    }
                }
            }

            // Glowing SOS beacon pulses in space
            Float lastPing = (Float) entity.getCustomData().get("DA_LAST_BEACON_PING");
            float now = totalCombatTime;
            if (lastPing == null || now - lastPing.floatValue() >= 1.35f) {
                entity.getCustomData().put("DA_LAST_BEACON_PING", Float.valueOf(now));
                Boolean rescued = (Boolean) entity.getCustomData().get("DERELICT_RESCUED");
                boolean isRescued = (rescued != null && rescued.booleanValue());
                Color pingCol = isRescued ? new Color(60, 255, 140, 210) : new Color(255, 215, 60, 230);

                engine.addSmoothParticle(loc, entity.getVelocity(), 160f, 0.9f, 1.1f, pingCol);
                for (int p = 0; p < 4; p++) {
                    float pAngle = random.nextFloat() * 6.28f;
                    float pSpd = 25f + random.nextFloat() * 45f;
                    Vector2f pVel = new Vector2f(entity.getVelocity().x + (float)Math.cos(pAngle)*pSpd, entity.getVelocity().y + (float)Math.sin(pAngle)*pSpd);
                    engine.addHitParticle(loc, pVel, 8f, 1.0f, 0.7f, pingCol);
                }
            }

            boolean inBounds = Math.abs(loc.x) <= halfW && Math.abs(loc.y) <= halfH;

            if (inBounds) {
                Float curT = (Float) entity.getCustomData().get("DERELICT_HOLD_TIMER");
                float holdTimer = (curT != null ? curT.floatValue() : 0f) + amount;
                entity.getCustomData().put("DERELICT_HOLD_TIMER", Float.valueOf(holdTimer));

                Boolean rescued = (Boolean) entity.getCustomData().get("DERELICT_RESCUED");
                if ((rescued == null || !rescued.booleanValue()) && holdTimer >= derelictRescueHoldTime) {
                    entity.getCustomData().put("DERELICT_RESCUED", Boolean.TRUE);
                    derelictRescueNotified = true;
                    rescuedDerelictHullName = (String) entity.getCustomData().get("DERELICT_NAME");

                    if (engine.isSimulation()) {
                        rescuedDerelictVariantId = null;

                        engine.addFloatingText(
                            loc,
                            IS_RUSSIAN ? "СИМУЛЯЦИЯ: СИСТЕМЫ СТАБИЛИЗИРОВАНЫ!" : "SIMULATION: SYSTEMS STABILIZED!",
                            22f,
                            new Color(80, 220, 255),
                            entity,
                            0.3f,
                            3.5f
                        );

                        if (engine.getCombatUI() != null) {
                            String msg = IS_RUSSIAN ?
                                ("Симуляция: остов " + rescuedDerelictHullName + " стабилизирован (спасение в симуляции отключено).") :
                                ("Simulation: hull " + rescuedDerelictHullName + " stabilized (recovery disabled in simulation).");
                            engine.getCombatUI().addMessage(1, msg, new Color(80, 220, 255));
                        }
                    } else {
                        String vId = (String) entity.getCustomData().get("DERELICT_VARIANT_ID");
                        rescuedDerelictVariantId = vId;
                        if (vId != null) {
                            if (!rescuedDerelictVariantIds.contains(vId)) {
                                rescuedDerelictVariantIds.add(vId);
                            }
                            derelictSalvageRecords.put(vId, new DerelictSalvageRecord(
                                vId, rescuedDerelictHullName, derelictRescueChance, 0.15f, 0.35f
                            ));
                        }

                        engine.addFloatingText(
                            loc,
                            IS_RUSSIAN ? "КОРАБЛЬ СПАСЕН! СИСТЕМЫ СТАБИЛИЗИРОВАНЫ!" : "SHIP RESCUED! SYSTEMS STABILIZED!",
                            22f,
                            new Color(60, 255, 140),
                            entity,
                            0.3f,
                            3.5f
                        );

                        if (engine.getCombatUI() != null) {
                            String msg = IS_RUSSIAN ?
                                ("Вы спасли заброшенный корабль: " + rescuedDerelictHullName + "!") :
                                ("You rescued a derelict ship: " + rescuedDerelictHullName + "!");
                            engine.getCombatUI().addMessage(1, msg, new Color(60, 255, 140));
                        }
                    }

                    try {
                        Global.getSoundPlayer().playUISound("ui_intel_positive", 1.0f, 1.0f);
                    } catch (Throwable t) {}
                }
            }

            if (entity.getHitpoints() <= 0f) {
                Boolean wasRescued = (Boolean) entity.getCustomData().get("DERELICT_RESCUED");
                String dName = (String) entity.getCustomData().get("DERELICT_NAME");
                String vId = (String) entity.getCustomData().get("DERELICT_VARIANT_ID");
                if (wasRescued != null && wasRescued.booleanValue() && vId != null) {
                    DerelictSalvageRecord rec = derelictSalvageRecords.get(vId);
                    if (rec != null) {
                        rec.destroyed = true;
                        rec.rescueChance = 0.15f; // 15% recovery chance if shattered/destroyed
                        rec.minDmodProb = 0.75f;  // Severe D-mod damage penalty
                        rec.maxDmodProb = 1.0f;
                    }
                    if (engine.getCombatUI() != null) {
                        String msg = IS_RUSSIAN ?
                            ("Захваченный остов " + dName + " расколот в бою! (Шанс сборки обломков: 15%, повышенный износ).") :
                            ("Rescued hull " + dName + " was shattered! (Scrap recovery chance: 15%, severe damage).");
                        engine.getCombatUI().addMessage(1, msg, new Color(255, 75, 75));
                    }
                }
                removeEasterEggEntity(entity);
                if (entity == activeDerelictEntity) activeDerelictEntity = null;
            }
        } else if (entity.getHitpoints() <= 0f) {
            if ("EXPLOSIVE_TANK".equals(eggType)) {
                triggerTankExplosion(entity, null);
            } else {
                triggerSalvageDestruction(entity);
            }
            return;
        }
    }

    private static List<String> cachedDerelictVariants = null;
    private static List<String> cachedFrigates = null;
    private static List<String> cachedDestroyers = null;
    private static List<String> cachedCruisers = null;
    private static List<String> cachedCapitals = null;

    public synchronized void initDerelictPool() {
        if (cachedDerelictVariants != null && !cachedDerelictVariants.isEmpty()) return;

        cachedDerelictVariants = new ArrayList<String>();
        cachedFrigates = new ArrayList<String>();
        cachedDestroyers = new ArrayList<String>();
        cachedCruisers = new ArrayList<String>();
        cachedCapitals = new ArrayList<String>();

        try {
            List<String> allVarIds = Global.getSettings().getAllVariantIds();
            if (allVarIds != null) {
                for (String vId : allVarIds) {
                    if (vId == null || vId.isEmpty()) continue;
                    ShipVariantAPI variant = null;
                    try {
                        variant = Global.getSettings().getVariant(vId);
                    } catch (Throwable t) { continue; }
                    if (variant == null) continue;

                    // 1. Direct API checks: strictly exclude stations and fighters
                    if (variant.isStation() || variant.isFighter()) continue;

                    ShipHullSpecAPI spec = variant.getHullSpec();
                    if (spec == null) continue;

                    // 2. Hull size check: only valid ship hull sizes (Frigate, Destroyer, Cruiser, Capital)
                    ShipAPI.HullSize size = spec.getHullSize();
                    if (size == null || size == ShipAPI.HullSize.FIGHTER || size == ShipAPI.HullSize.DEFAULT) continue;

                    // 3. Hints exclusion (Stations, Modules, Fighter wings, Unboardable targets)
                    EnumSet<ShipHullSpecAPI.ShipTypeHints> hints = spec.getHints();
                    if (hints != null) {
                        if (hints.contains(ShipHullSpecAPI.ShipTypeHints.STATION)) continue;
                        if (hints.contains(ShipHullSpecAPI.ShipTypeHints.MODULE)) continue;
                        if (hints.contains(ShipHullSpecAPI.ShipTypeHints.UNDER_PARENT)) continue;
                        if (hints.contains(ShipHullSpecAPI.ShipTypeHints.UNBOARDABLE)) continue;
                    }

                    // 4. Tags exclusion
                    if (spec.hasTag("station") || spec.hasTag("module") || spec.hasTag("module_hull_bar_only") || spec.hasTag("hide_in_codex")) continue;

                    // 5. Name string heuristic safety against modded station parts & platforms
                    String hIdLow = spec.getHullId().toLowerCase();
                    String vIdLow = vId.toLowerCase();
                    if (hIdLow.contains("station") || hIdLow.contains("module_") || hIdLow.startsWith("module")) continue;
                    if (vIdLow.contains("station") || vIdLow.contains("module_") || vIdLow.startsWith("module")) continue;
                    if (hIdLow.contains("platform") || hIdLow.contains("satellite") || hIdLow.contains("bastion")) continue;
                    if (hIdLow.contains("shuttlepod") || hIdLow.contains("escape_pod")) continue;

                    // 6. Ensure texture exists and is loadable
                    String spritePath = spec.getSpriteName();
                    if (spritePath == null || spritePath.isEmpty()) continue;

                    // Valid ship! Categorize by hull size
                    cachedDerelictVariants.add(vId);
                    if (size == ShipAPI.HullSize.FRIGATE) {
                        cachedFrigates.add(vId);
                    } else if (size == ShipAPI.HullSize.DESTROYER) {
                        cachedDestroyers.add(vId);
                    } else if (size == ShipAPI.HullSize.CRUISER) {
                        cachedCruisers.add(vId);
                    } else if (size == ShipAPI.HullSize.CAPITAL_SHIP) {
                        cachedCapitals.add(vId);
                    }
                }
            }
        } catch (Throwable t) {}

        if (cachedDerelictVariants.isEmpty()) {
            String[] fallbacks = new String[] {
                "hound_d_Standard", "cerberus_d_Standard", "wayfarer_Standard",
                "shepherd_Frontier", "lasher_d_CS", "buffalo_d_Standard", "dram_Light"
            };
            for (String f : fallbacks) {
                cachedDerelictVariants.add(f);
                cachedFrigates.add(f);
            }
        }
    }

    public String pickRandomDerelictVariant() {
        initDerelictPool();

        // Size weighted distribution: Frigate 45%, Destroyer 30%, Cruiser 18%, Capital 7%
        float roll = random.nextFloat();
        if (roll < 0.45f && cachedFrigates != null && !cachedFrigates.isEmpty()) {
            return cachedFrigates.get(random.nextInt(cachedFrigates.size()));
        } else if (roll < 0.75f && cachedDestroyers != null && !cachedDestroyers.isEmpty()) {
            return cachedDestroyers.get(random.nextInt(cachedDestroyers.size()));
        } else if (roll < 0.93f && cachedCruisers != null && !cachedCruisers.isEmpty()) {
            return cachedCruisers.get(random.nextInt(cachedCruisers.size()));
        } else if (cachedCapitals != null && !cachedCapitals.isEmpty()) {
            return cachedCapitals.get(random.nextInt(cachedCapitals.size()));
        }

        if (cachedDerelictVariants != null && !cachedDerelictVariants.isEmpty()) {
            return cachedDerelictVariants.get(random.nextInt(cachedDerelictVariants.size()));
        }
        return "hound_d_Standard";
    }

    private static List<String> cachedFighterWings = null;

    public synchronized void initFighterPool() {
        if (cachedFighterWings != null && !cachedFighterWings.isEmpty()) return;
        cachedFighterWings = new ArrayList<String>();
        try {
            List<FighterWingSpecAPI> allSpecs = Global.getSettings().getAllFighterWingSpecs();
            if (allSpecs != null) {
                for (FighterWingSpecAPI spec : allSpecs) {
                    if (spec == null) continue;
                    String id = spec.getId();
                    if (id == null || id.isEmpty()) continue;

                    // Exclude non-salvageable, restricted, or hidden wings
                    if (spec.hasTag("no_drop") || spec.hasTag("restricted") || spec.hasTag("hide_in_codex") || spec.hasTag("dummy") || spec.hasTag("station")) continue;

                    ShipVariantAPI var = spec.getVariant();
                    if (var == null) continue;
                    ShipHullSpecAPI hSpec = var.getHullSpec();
                    if (hSpec == null) continue;
                    String sprite = hSpec.getSpriteName();
                    if (sprite == null || sprite.isEmpty()) continue;

                    // Verify that the texture actually loads and is valid!
                    try {
                        Global.getSettings().loadTexture(sprite);
                        SpriteAPI test = Global.getSettings().getSprite(sprite);
                        if (!isValidSprite(test)) continue;
                    } catch (Throwable t) {
                        continue;
                    }

                    cachedFighterWings.add(id);
                }
            }
        } catch (Throwable t) {}
        if (cachedFighterWings.isEmpty()) {
            cachedFighterWings.add("broadsword_wing");
            cachedFighterWings.add("talon_wing");
            cachedFighterWings.add("dagger_wing");
            cachedFighterWings.add("thunder_wing");
            cachedFighterWings.add("gladius_wing");
            cachedFighterWings.add("warthog_wing");
        }
    }

    public String pickRandomFighterWing() {
        initFighterPool();
        if (cachedFighterWings != null && !cachedFighterWings.isEmpty()) {
            for (int attempts = 0; attempts < 6; attempts++) {
                String candidate = cachedFighterWings.get(random.nextInt(cachedFighterWings.size()));
                try {
                    FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(candidate);
                    if (spec != null && spec.getVariant() != null && spec.getVariant().getHullSpec() != null) {
                        String s = spec.getVariant().getHullSpec().getSpriteName();
                        if (s != null && !s.isEmpty()) return candidate;
                    }
                } catch (Throwable t) {}
            }
        }
        return "broadsword_wing";
    }

    public CombatEntityAPI spawnDerelictAtLocation(float spawnX, float spawnY, float vx, float vy) {
        initDerelictPool();
        String chosenVariant = pickRandomDerelictVariant();

        try {
            ShipVariantAPI variant = null;
            try {
                variant = Global.getSettings().getVariant(chosenVariant);
            } catch (Throwable t) {}

            ShipHullSpecAPI spec = (variant != null) ? variant.getHullSpec() : null;
            if (spec == null) {
                try {
                    spec = Global.getSettings().getHullSpec(chosenVariant);
                } catch (Throwable t) {}
            }
            if (spec == null) {
                spec = Global.getSettings().getHullSpec("hound_d");
            }

            String spritePath = (spec != null && spec.getSpriteName() != null) ? spec.getSpriteName() : "graphics/ships/hound/hound_substandard.png";
            String hullName = (spec != null) ? spec.getHullNameWithDashClass() : null;
            if (hullName == null || hullName.isEmpty()) {
                hullName = (spec != null) ? spec.getHullName() : (IS_RUSSIAN ? "Заброшенный корабль" : "Derelict Ship");
            }

            float radius = (spec != null) ? Math.max(35f, spec.getCollisionRadius()) : 50f;
            float baseHp = (spec != null) ? spec.getHitpoints() : 2000f;
            float mass = Math.max(120f, baseHp * 0.16f);
            float hp = Math.max(3200f, baseHp * 2.2f);

            try {
                Global.getSettings().loadTexture(spritePath);
            } catch (Throwable t) {}

            CombatEntityAPI entity = engine.spawnAsteroid(spritePath, spawnX, spawnY, vx, vy, false);
            if (entity != null) {
                entity.setFacing(random.nextFloat() * 360f);
                entity.setAngularVelocity((random.nextFloat() - 0.5f) * 16f);
                entity.setCollisionRadius(radius);
                entity.setMass(mass);
                entity.setHitpoints(hp);

                if (entity instanceof CombatAsteroidAPI) {
                    try {
                        CombatAsteroidAPI ast = (CombatAsteroidAPI) entity;
                        SpriteAPI s = ast.getSpriteAPI();
                        if (s != null && isValidSprite(s)) {
                            float maxDim = Math.max(s.getTextureWidth(), s.getTextureHeight());
                            if (maxDim > 0f) {
                                float scale = (radius * 2.25f) / maxDim;
                                s.setSize(s.getTextureWidth() * scale, s.getTextureHeight() * scale);
                            } else {
                                s.setSize(radius * 2.25f, radius * 2.25f);
                            }
                            s.setColor(new Color(230, 230, 240, 255));
                            s.setAlphaMult(1.0f);
                        }
                    } catch (Throwable t) {}
                }

                entity.setCustomData("IS_EASTER_EGG", Boolean.TRUE);
                entity.setCustomData(TRACKED_KEY, Boolean.TRUE);
                entity.setCustomData("EASTER_EGG_TYPE", "DERELICT");
                entity.setCustomData("DERELICT_VARIANT_ID", chosenVariant);
                entity.setCustomData("DERELICT_NAME", hullName);
                entity.setCustomData("DERELICT_HULL_SIZE", (spec != null && spec.getHullSize() != null) ? spec.getHullSize().name() : "FRIGATE");
                entity.setCustomData("DERELICT_HOLD_TIMER", Float.valueOf(0f));
                entity.setCustomData("DERELICT_RESCUED", Boolean.FALSE);
                entity.setCustomData("DERELICT_PLAYER_ENGAGED", Boolean.FALSE);
                entity.setCustomData("DA_NO_MAINTAIN_VELOCITY", Boolean.TRUE);
                try {
                    queueDetectionSoundBurst();
                } catch (Throwable t) {}

                entity.setCustomData(BASE_VX_KEY, Float.valueOf(vx));
                entity.setCustomData(BASE_VY_KEY, Float.valueOf(vy));

                activeDerelictEntity = entity;
                activeEasterEggEntities.add(entity);

                return entity;
            }
        } catch (Throwable t) {}
        return null;
    }

    private void spawnDerelictEasterEgg(float spawnX, float spawnY, float vx, float vy) {
        battleDerelictSpawned = true;
        CombatEntityAPI entity = spawnDerelictAtLocation(spawnX, spawnY, vx, vy);
        if (entity != null) {
            String hullName = (String) entity.getCustomData().get("DERELICT_NAME");
            try {
                Global.getSoundPlayer().playUISound("ui_rep_raise", 1.0f, 1.0f);
            } catch (Throwable t) {}

            if (engine.getCombatUI() != null && !isTitleScreen) {
                String alert = IS_RUSSIAN ?
                    ("СЕНСОРЫ: В потоке астероидов замечен дрейфующий остов корабля (" + hullName + ")!") :
                    ("SENSORS: Derelict ship detected drifting in the asteroid belt (" + hullName + ")!");
                engine.getCombatUI().addMessage(1, alert, new Color(255, 215, 60));
            }
        }
    }

    public CombatEntityAPI spawnOtherEasterEggAtLocation(float spawnX, float spawnY, float vx, float vy, String specificType) {
        activeOtherEasterEggs++;

        String spritePath = "graphics/stations/large_cache.png";
        String eggType = "CACHE";
        float radius = 40f;
        float mass = 160f;
        float hp = 2000f;
        float spin = 18f;
        String fighterWingId = null;
        String fighterWingName = null;

        if ("CACHE".equals(specificType)) {
            spritePath = "graphics/stations/large_cache.png";
            eggType = "CACHE";
            radius = 42f; mass = 180f; hp = 2200f; spin = 15f;
        } else if ("PROBE".equals(specificType)) {
            spritePath = "graphics/stations/survey_probe.png";
            eggType = "PROBE";
            radius = 36f; mass = 120f; hp = 1500f; spin = 20f;
        } else if ("CRYO_POD".equals(specificType)) {
            spritePath = "graphics/stations/probe.png";
            eggType = "CRYO_POD";
            radius = 34f; mass = 100f; hp = 1200f; spin = 12f;
        } else if ("NAV_BUOY".equals(specificType)) {
            spritePath = "graphics/ships/nav_buoy.png";
            eggType = "NAV_BUOY";
            radius = 38f; mass = 160f; hp = 1800f; spin = 10f;
        } else if ("MOTHERSHIP_ENGINE".equals(specificType) || "DEBRIS".equals(specificType)) {
            spritePath = "graphics/ships/derelict/derelict_mothership_pod_engine_left.png";
            eggType = "MOTHERSHIP_ENGINE";
            radius = 58f; mass = 300f; hp = 2600f; spin = 14f;
        } else if ("EXPLOSIVE_TANK".equals(specificType)) {
            spritePath = "graphics/stations/cargo_pod_special.png";
            eggType = "EXPLOSIVE_TANK";
            radius = 32f; mass = 80f; hp = 600f; spin = 15f;
        } else if ("FIGHTER".equals(specificType)) {
            initFighterPool();
            fighterWingId = pickRandomFighterWing();
            FighterWingSpecAPI fSpec = null;
            try {
                fSpec = Global.getSettings().getFighterWingSpec(fighterWingId);
            } catch (Throwable t) {}
            if (fSpec != null) {
                fighterWingName = fSpec.getWingName();
                if (fSpec.getVariant() != null && fSpec.getVariant().getHullSpec() != null) {
                    spritePath = fSpec.getVariant().getHullSpec().getSpriteName();
                    radius = Math.max(22f, fSpec.getVariant().getHullSpec().getCollisionRadius());
                }
            }
            eggType = "FIGHTER";
            mass = 65f;
            hp = 750f;
            spin = 12f;
        } else {
            int roll = random.nextInt(7);
            switch (roll) {
                case 0:
                    spritePath = "graphics/stations/large_cache.png";
                    eggType = "CACHE";
                    radius = 42f; mass = 180f; hp = 2200f; spin = 15f;
                    break;
                case 1:
                    spritePath = "graphics/stations/survey_probe.png";
                    eggType = "PROBE";
                    radius = 36f; mass = 120f; hp = 1500f; spin = 20f;
                    break;
                case 2:
                    spritePath = "graphics/stations/probe.png";
                    eggType = "CRYO_POD";
                    radius = 34f; mass = 100f; hp = 1200f; spin = 12f;
                    break;
                case 3:
                    spritePath = "graphics/ships/nav_buoy.png";
                    eggType = "NAV_BUOY";
                    radius = 38f; mass = 160f; hp = 1800f; spin = 10f;
                    break;
                case 4:
                    spritePath = "graphics/stations/cargo_pod_special.png";
                    eggType = "EXPLOSIVE_TANK";
                    radius = 32f; mass = 80f; hp = 600f; spin = 15f;
                    break;
                case 5:
                    spritePath = "graphics/ships/derelict/derelict_mothership_pod_engine_left.png";
                    eggType = "MOTHERSHIP_ENGINE";
                    radius = 58f; mass = 300f; hp = 2600f; spin = 14f;
                    break;
                default:
                    initFighterPool();
                    fighterWingId = pickRandomFighterWing();
                    FighterWingSpecAPI fSpec = null;
                    try {
                        fSpec = Global.getSettings().getFighterWingSpec(fighterWingId);
                    } catch (Throwable t) {}
                    if (fSpec != null) {
                        fighterWingName = fSpec.getWingName();
                        if (fSpec.getVariant() != null && fSpec.getVariant().getHullSpec() != null) {
                            spritePath = fSpec.getVariant().getHullSpec().getSpriteName();
                            radius = Math.max(22f, fSpec.getVariant().getHullSpec().getCollisionRadius());
                        }
                    }
                    eggType = "FIGHTER";
                    mass = 65f;
                    hp = 750f;
                    spin = 12f;
                    break;
            }
        }

        try {
            Global.getSettings().loadTexture(spritePath);
        } catch (Throwable t) {}

        try {
            CombatEntityAPI entity = engine.spawnAsteroid(spritePath, spawnX, spawnY, vx, vy, false);
            if (entity != null) {
                entity.setFacing(random.nextFloat() * 360f);
                entity.setAngularVelocity((random.nextFloat() - 0.5f) * spin);
                entity.setCollisionRadius(radius);
                entity.setMass(mass);
                entity.setHitpoints(hp);

                if (entity instanceof CombatAsteroidAPI) {
                    try {
                        CombatAsteroidAPI ast = (CombatAsteroidAPI) entity;
                        SpriteAPI s = ast.getSpriteAPI();
                        if (s != null && isValidSprite(s)) {
                            float maxDim = Math.max(s.getTextureWidth(), s.getTextureHeight());
                            if (maxDim > 0f) {
                                float scale = (radius * 2.2f) / maxDim;
                                s.setSize(s.getTextureWidth() * scale, s.getTextureHeight() * scale);
                            } else {
                                s.setSize(radius * 2.2f, radius * 2.2f);
                            }
                            s.setColor(Color.WHITE);
                            s.setAlphaMult(1.0f);
                        }
                    } catch (Throwable t) {}
                }

                entity.setCustomData("IS_EASTER_EGG", Boolean.TRUE);
                entity.setCustomData(TRACKED_KEY, Boolean.TRUE);
                entity.setCustomData("EASTER_EGG_TYPE", eggType);
                entity.setCustomData("DA_NO_MAINTAIN_VELOCITY", Boolean.TRUE);
                if ("FIGHTER".equals(eggType) && fighterWingId != null) {
                    entity.setCustomData("FIGHTER_WING_ID", fighterWingId);
                    entity.setCustomData("FIGHTER_WING_NAME", (fighterWingName != null ? fighterWingName : fighterWingId));
                }
                entity.setCustomData(BASE_VX_KEY, Float.valueOf(vx));
                entity.setCustomData(BASE_VY_KEY, Float.valueOf(vy));

                activeEasterEggEntities.add(entity);

                if ("FIGHTER".equals(eggType)) {
                    try {
                        Global.getSoundPlayer().playUISound("ui_radar_detect_vessel", 1.1f, 0.9f);
                    } catch (Throwable t) {}
                    if (engine.getCombatUI() != null && !isTitleScreen) {
                        String alert = IS_RUSSIAN ?
                            ("СЕНСОРЫ: В потоке астероидов замечен дрейфующий истребитель (" + (fighterWingName != null ? fighterWingName : "Крыло") + ")!") :
                            ("SENSORS: Drifting fighter detected in asteroid stream (" + (fighterWingName != null ? fighterWingName : "Wing") + ")!");
                        engine.getCombatUI().addMessage(1, alert, new Color(100, 240, 255));
                    }
                } else if ("EXPLOSIVE_TANK".equals(eggType)) {
                    try {
                        Global.getSoundPlayer().playUISound("ui_radar_detect_vessel", 1.2f, 0.7f);
                    } catch (Throwable t) {}
                    if (engine.getCombatUI() != null && !isTitleScreen) {
                        String alert = IS_RUSSIAN ?
                            "СЕНСОРЫ: Внимание! В потоке обнаружен взрывоопасный бак с горючим!" :
                            "SENSORS: Warning! Explosive fuel canister detected in the asteroid stream!";
                        engine.getCombatUI().addMessage(1, alert, new Color(255, 140, 40));
                    }
                }
                return entity;
            }
        } catch (Throwable t) {}
        return null;
    }

    private void spawnOtherEasterEgg(float spawnX, float spawnY, float vx, float vy, boolean isResource) {
        String specificType = null;
        if (isResource) {
            String[] resourcePool = new String[] { "CACHE", "PROBE", "CRYO_POD", "NAV_BUOY", "FIGHTER" };
            specificType = resourcePool[random.nextInt(resourcePool.length)];
        } else {
            String[] debrisPool = new String[] { "MOTHERSHIP_ENGINE", "DEBRIS", "EXPLOSIVE_TANK" };
            specificType = debrisPool[random.nextInt(debrisPool.length)];
        }
        spawnOtherEasterEggAtLocation(spawnX, spawnY, vx, vy, specificType);
    }

    private void spawnOtherEasterEgg(float spawnX, float spawnY, float vx, float vy) {
        spawnOtherEasterEgg(spawnX, spawnY, vx, vy, random.nextFloat() < 0.35f);
    }

    public void removeEasterEggEntity(CombatEntityAPI entity) {
        if (entity == null) return;
        entity.getCustomData().put("DA_EXPIRED", Boolean.TRUE);
        if (activeEasterEggEntities != null) {
            activeEasterEggEntities.remove(entity);
        }
        if (entity == activeDerelictEntity) {
            activeDerelictEntity = null;
        }
        if (engine != null) {
            engine.removeEntity(entity);
        }
    }

    @Override
    public void renderInUICoords(ViewportAPI viewport) {
        if (engine == null || viewport == null) return;
        if (activeEasterEggEntities == null || activeEasterEggEntities.isEmpty()) return;

        float sw = Global.getSettings().getScreenWidthPixels();
        float sh = Global.getSettings().getScreenHeightPixels();
        float cx = sw * 0.5f;
        float cy = sh * 0.5f;
        float totalTime = engine.getTotalElapsedTime(false);
        float pulse = (float) (0.5 + 0.5 * Math.sin(totalTime * 4.5));

        boolean isTacticalMap = engine.getCombatUI() != null && engine.getCombatUI().isShowingCommandUI();

        for (int i = activeEasterEggEntities.size() - 1; i >= 0; i--) {
            try {
                CombatEntityAPI entity = activeEasterEggEntities.get(i);
                if (entity == null || entity.isExpired() || (entity.getCustomData() != null && entity.getCustomData().containsKey("DA_EXPIRED")) || !engine.isEntityInPlay(entity)) {
                    activeEasterEggEntities.remove(i);
                    continue;
                }

                Vector2f loc = entity.getLocation();
                if (loc == null) continue;

                float sx = viewport.convertWorldXtoScreenX(loc.x);
                float sy = viewport.convertWorldYtoScreenY(loc.y);

                String eggType = (String) entity.getCustomData().get("EASTER_EGG_TYPE");
                if (eggType == null) eggType = "DEBRIS";

                float padX = 55f;
                float padY = 55f;
                boolean onScreen = (sx >= padX && sx <= sw - padX && sy >= padY && sy <= sh - padY);

                if ("DERELICT".equals(eggType)) {
                    Boolean rescued = (Boolean) entity.getCustomData().get("DERELICT_RESCUED");
                    boolean isRescued = (rescued != null && rescued.booleanValue());
                    Color themeCol = isRescued ? new Color(60, 255, 140) : new Color(255, 215, 60);

                    String hullSizeStr = (String) entity.getCustomData().get("DERELICT_HULL_SIZE");
                    float sizeScale = 1.0f;
                    if ("CAPITAL_SHIP".equals(hullSizeStr)) {
                        sizeScale = 1.35f;
                    } else if ("CRUISER".equals(hullSizeStr)) {
                        sizeScale = 1.20f;
                    } else if ("DESTROYER".equals(hullSizeStr)) {
                        sizeScale = 1.08f;
                    } else {
                        sizeScale = 0.95f;
                    }

                    if (onScreen) {
                        SpriteAPI ring = getCachedSprite("systemMap", "radar_circle", "graphics/icons/radar_circle.png");
                        if (ring != null && isValidSprite(ring)) {
                            float ringSize = (isTacticalMap ? 26f : 44f + 18f * pulse) * sizeScale;
                            ring.setSize(ringSize, ringSize);
                            ring.setColor(new Color(themeCol.getRed(), themeCol.getGreen(), themeCol.getBlue(), (int)(isTacticalMap ? 220 : 150 + 95 * pulse)));
                            ring.setAdditiveBlend();
                            ring.renderAtCenter(sx, sy);
                        }

                        SpriteAPI icon = getCachedSprite("systemMap", "icon_derelict", "graphics/icons/derelict.png");
                        if (icon != null && isValidSprite(icon)) {
                            float iconSize = (isTacticalMap ? 20f : 28f) * sizeScale;
                            icon.setSize(iconSize, iconSize);
                            icon.setColor(new Color(themeCol.getRed(), themeCol.getGreen(), themeCol.getBlue(), 245));
                            icon.renderAtCenter(sx, sy);
                        }
                    } else {
                        // Derelict is high-value: KEEP off-screen HUD arrows so player never loses track of the ship!
                        float dx = sx - cx;
                        float dy = sy - cy;
                        if (Math.abs(dx) > 0.001f || Math.abs(dy) > 0.001f) {
                            float tX = Float.MAX_VALUE;
                            float tY = Float.MAX_VALUE;
                            if (dx > 0) tX = (sw - padX - cx) / dx;
                            else if (dx < 0) tX = (padX - cx) / dx;
                            if (dy > 0) tY = (sh - padY - cy) / dy;
                            else if (dy < 0) tY = (padY - cy) / dy;
                            float t = Math.min(tX, tY);
                            float edgeX = cx + dx * t;
                            float edgeY = cy + dy * t;
                            float angleDeg = (float) Math.toDegrees(Math.atan2(dy, dx));

                            SpriteAPI arrow = getCachedSprite("systemMap", "sensor_contact", "graphics/icons/fleet_triangle.png");
                            if (arrow != null && isValidSprite(arrow)) {
                                float arrowSize = isTacticalMap ? 18f : (26f + 4f * pulse);
                                arrow.setSize(arrowSize, arrowSize);
                                arrow.setAngle(angleDeg - 90f);
                                arrow.setColor(new Color(themeCol.getRed(), themeCol.getGreen(), themeCol.getBlue(), (int)(180 + 75 * pulse)));
                                arrow.setAdditiveBlend();
                                arrow.renderAtCenter(edgeX, edgeY);
                            }

                            float len = (float) Math.sqrt(dx * dx + dy * dy);
                            float inwardOffset = isTacticalMap ? 20f : 28f;
                            float inwardX = edgeX - (dx / len) * inwardOffset;
                            float inwardY = edgeY - (dy / len) * inwardOffset;

                            SpriteAPI icon = getCachedSprite("systemMap", "icon_derelict", "graphics/icons/derelict.png");
                            if (icon != null && isValidSprite(icon)) {
                                float iconSize = isTacticalMap ? 16f : 22f;
                                icon.setSize(iconSize, iconSize);
                                icon.setColor(new Color(themeCol.getRed(), themeCol.getGreen(), themeCol.getBlue(), 230));
                                icon.renderAtCenter(inwardX, inwardY);
                            }
                        }
                    }
                } else if ("CACHE".equals(eggType) || "PROBE".equals(eggType) || "CRYO_POD".equals(eggType) || "NAV_BUOY".equals(eggType) || "MOTHERSHIP_ENGINE".equals(eggType) || "FIGHTER".equals(eggType)) {
                    // Resource containers ONLY show when on-screen (no off-screen HUD arrows to avoid screen clutter!)
                    if (onScreen) {
                        Color cCol = new Color(255, 205, 55);
                        if ("PROBE".equals(eggType)) cCol = new Color(60, 230, 255);
                        else if ("CRYO_POD".equals(eggType)) cCol = new Color(60, 255, 140);
                        else if ("NAV_BUOY".equals(eggType)) cCol = new Color(195, 130, 255);
                        else if ("FIGHTER".equals(eggType)) cCol = new Color(90, 215, 255);
                        else if ("MOTHERSHIP_ENGINE".equals(eggType)) cCol = new Color(190, 215, 235);

                        boolean isFighter = "FIGHTER".equals(eggType);

                        SpriteAPI ring = getCachedSprite("systemMap", "radar_circle", "graphics/icons/radar_circle.png");
                        if (ring != null && isValidSprite(ring)) {
                            float ringSize = isTacticalMap ? 22f : 36f + 14f * pulse;
                            ring.setSize(ringSize, ringSize);
                            ring.setColor(new Color(cCol.getRed(), cCol.getGreen(), cCol.getBlue(), (int)(isTacticalMap ? 210 : 130 + 85 * pulse)));
                            ring.setAdditiveBlend();
                            ring.renderAtCenter(sx, sy);
                        }

                        SpriteAPI icon = null;
                        if (isFighter) {
                            icon = getCachedSprite("ui", "cargoFighterChip", "graphics/icons/cargo/fighter_lpc.png");
                            if (icon == null || !isValidSprite(icon)) icon = getCachedSprite("codex", "fighters", "graphics/icons/codex/fighters.png");
                            if (icon == null || !isValidSprite(icon)) icon = getCachedSprite("systemMap", "icon_fighter", "graphics/icons/fleet_triangle.png");
                        } else {
                            icon = getCachedSprite(null, null, "graphics/icons/debris_field_icon.png");
                        }

                        if (icon != null && isValidSprite(icon)) {
                            float iconSize = isTacticalMap ? 16f : 22f;
                            icon.setSize(iconSize, iconSize);
                            icon.setColor(new Color(cCol.getRed(), cCol.getGreen(), cCol.getBlue(), 245));
                            icon.renderAtCenter(sx, sy);
                        }
                    }
                } else if ("EXPLOSIVE_TANK".equals(eggType)) {
                    // Explosive tank ONLY shows when on-screen (no off-screen HUD arrows to avoid screen clutter!)
                    if (onScreen) {
                        Color dangerCol = new Color(255, 95, 25);
                        SpriteAPI danger = getCachedSprite(null, null, "graphics/icons/danger3.png");
                        if (danger != null && isValidSprite(danger)) {
                            float dSize = isTacticalMap ? 18f : (24f + 5f * pulse);
                            danger.setSize(dSize, dSize);
                            danger.setColor(new Color(dangerCol.getRed(), dangerCol.getGreen(), dangerCol.getBlue(), (int)(180 + 75 * pulse)));
                            danger.renderAtCenter(sx, sy);
                        }
                    }
                } else {
                    // Regular debris ONLY when on-screen
                    if (onScreen) {
                        SpriteAPI icon = getCachedSprite(null, null, "graphics/icons/debris_field_icon.png");
                        if (icon != null && isValidSprite(icon)) {
                            float iconSize = isTacticalMap ? 14f : 18f;
                            icon.setSize(iconSize, iconSize);
                            icon.setColor(new Color(130, 210, 255, 190));
                            icon.renderAtCenter(sx, sy);
                        }
                    }
                }
            } catch (Throwable t) {}
        }
    }
}
