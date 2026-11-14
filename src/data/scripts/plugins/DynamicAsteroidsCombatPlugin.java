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
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.terrain.AsteroidSource;
import com.fs.starfarer.api.util.Misc;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;

import com.fs.starfarer.api.combat.CombatEngineAPI;

import com.fs.starfarer.api.combat.CombatEntityAPI;

import com.fs.starfarer.api.combat.CombatAsteroidAPI;

import com.fs.starfarer.api.combat.ShipAPI;

import com.fs.starfarer.api.combat.ViewportAPI;

import com.fs.starfarer.api.input.InputEventAPI;



import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

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
import com.fs.starfarer.api.combat.listeners.FleetMemberDeploymentListener;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;

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

import com.fs.starfarer.api.impl.campaign.ids.Tags;





/**

 * Dynamic Asteroids 2.0 (Cyclone Edition)

 */

public class DynamicAsteroidsCombatPlugin extends BaseEveryFrameCombatPlugin implements FleetMemberDeploymentListener {



    // Configurable compilation flags

    private static final boolean IS_RUSSIAN = true;

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

    private int maxFieldRocks = 650;

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

    private float asteroidCollisionDamageMult = 1.20f;

    private float playerKillResourceDropChance = 0.005f;

    private float playerKillFighterDropChance = 0.001f;

    // Solitary Giant Megalith Boulders & Arc Flight
    private boolean enableMegalithBoulders = true;
    private int maxMegaliths = 20;
    private int currentMegalithCount = 0;
    private boolean megalithsPreSeeded = false;
    private float megalithSpeed = 90.0f;
    private float megalithMinSpeed = 60.0f;
    private float megalithMaxSpeed = 120.0f;
    private float megalithTimer = 12.0f;
    private float megalithSpawnInterval = 42.0f;
    private float megalithMinRadius = 125.0f;
    private float megalithMaxRadius = 550.0f;
    private boolean enableDeploymentSafety = true;
    private float deploymentSafetyDuration = 7.0f;
    private boolean megalithArcFlight = true;
    private float megalithDerelictDropChance = 0.001f;
    private float summonMegalithKeyCooldown = 0f;

    // Station Anti-Ramming Protection & Deflection Dome
    private static final float STATION_DOME_OFFLINE_DURATION = 60.0f;
    private boolean enableStationAsteroidProtection = true;
    private float stationAsteroidDamageMult = 0.02f;
    private boolean enableStationDeflectionDome = true;
    private float stationDomeMargin = 140.0f;
    private float stationDomeScanTimer = 0f;
    private final List<StationDomeData> activeStationDomes = new ArrayList<StationDomeData>();
    private final List<ShipAPI> activeShipDomes = new ArrayList<ShipAPI>();
    private final Map<ShipAPI, Float> shipSpawnTimes = new HashMap<ShipAPI, Float>();
    private final Set<ShipAPI> shipDomesActivated = new HashSet<ShipAPI>();
    private final Set<ShipAPI> shipDomesDeactivated = new HashSet<ShipAPI>();

    public static class StationDomeData {
        public ShipAPI stationCore;
        public Vector2f center = new Vector2f();
        public float domeRadius = 550f;
        public boolean isOffline = false;
        public boolean wasOffline = false;
        public float offlineTimer = 0f;
        public boolean coreFailureActive = false;
        public final List<ShipAPI> failedSections = new ArrayList<ShipAPI>();
    }

    private float derelictSpawnChance = 0.015f;

    private float debrisEasterEggChance = 0.160f;

    private float resourceEasterEggChance = 0.040f;

    private float otherEasterEggChance = 0.060f;

    private float easterEggSpeed = 400.0f;

    private float easterEggMagnetStrength = 1400.0f;

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

    private boolean simulationEnabled = true;

    private boolean simulationHintShown = false;



    // Active figure streams list (supports 1, 2, or 3 concurrent angles!)

    private List<ActiveFigureStream> activeStreams = new ArrayList<ActiveFigureStream>();



    // Dynamic Battle Capacity (Tuned on the fly via Shift + Plus / Minus)

    private int battleTargetCount = 500;

    private int maxBackgroundRocks = 250;

    private int activeBackgroundCount = 0;

    private boolean backgroundPreSeeded = false;

    private float backgroundSpawnTimer = 0f;

    private float backgroundSpawnInterval = 0.055f;



    // Dynamic Asteroid Director (Living Cycles: Torrent -> Wave(s) -> Lull)
    public enum DirectorState {
        TORRENT,    // Continuous dense rolling river (26-42s)
        BURST,      // Brisk medium flow (12-20s)
        FORMATION,  // Geometrical wave bursts (chained 1-2 waves)
        LULL        // Serene lull / silence (6-12s, spawning paused)
    }

    private DirectorState currentDirectorState = DirectorState.TORRENT;
    private float directorStateTimer = 0f;
    private float directorStateDuration = 32f;
    private int formationWavesInChain = 0;
    private int directorCycleMode = 0; // 0 = Living cycles, 1 = Continuous torrent, 2 = Waves & lulls



    // Open Space settings (Quiet, rare solitary stray meteors)

    private int openSpaceMinRocks = 3;

    private int openSpaceMaxRocks = 7;

    private float openSpaceMinDelay = 2.0f;

    private float openSpaceMaxDelay = 5.5f;

    private int openSpaceTargetCount = 7;

    private float openSpaceTimer = 0f;

    private float openSpaceCurrentDelay = 2.5f;

    private float openSpaceEasterEggMult = 0.14f;

    // Open Space megaliths & rare transit swarms
    private boolean enableOpenSpaceMegaliths = true;
    private int openSpaceTargetMegaliths = 2; // 1 to 3 boulders
    private boolean openSpacePreSeeded = false;
    private float openSpaceMegalithTimer = 0f;
    private boolean enableOpenSpaceSwarm = true;
    private float openSpaceSwarmTimer = 0f;
    private float openSpaceSwarmDelay = 75.0f;
    private float openSpaceSwarmChance = 0.12f; // Rare chance per interval
    private float summonSwarmKeyCooldown = 0f;



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

    private float asteroidHealthMult = 0.51f;

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

        this.activeStreams.clear();

        this.notifiedPlayer = false;

        this.notifyTimer = 0f;

        this.simulationHintShown = false;

        this.trailTickTimer = 0f;

        this.megalithsPreSeeded = false;
        this.currentMegalithCount = 0;
        this.openSpacePreSeeded = false;
        this.openSpaceTargetMegaliths = 1 + random.nextInt(3); // 1, 2, or 3
        this.openSpaceMegalithTimer = 0f;
        this.openSpaceSwarmTimer = 0f;
        this.openSpaceSwarmDelay = 60.0f + random.nextFloat() * 45.0f;
        this.backgroundPreSeeded = false;
        this.activeBackgroundCount = 0;

        this.directorCycleMode = 0;
        this.currentDirectorState = (random.nextFloat() < 0.70f) ? DirectorState.TORRENT : DirectorState.BURST;
        this.directorStateTimer = 0f;
        this.directorStateDuration = 25.0f + random.nextFloat() * 15.0f;
        this.formationWavesInChain = 0;

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

            Global.getSettings().loadTexture("graphics/icons/cargo/fuel.png");

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



        this.activeStationDomes.clear();
        this.activeShipDomes.clear();
        this.shipSpawnTimes.clear();
        this.shipDomesActivated.clear();
        this.shipDomesDeactivated.clear();

        if (engine.getListenerManager() != null) {

            engine.getListenerManager().addListener(new AsteroidDamageModifier());
            engine.getListenerManager().addListener(this);

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

                        for (CampaignTerrainAPI terrain : terrains) {

                            if (terrain == null) continue;

                            String type = terrain.getType();

                            String terrainId = (terrain.getPlugin() != null) ? terrain.getPlugin().getTerrainId() : null;



                            boolean isAsteroidTerrain = false;

                            if (terrain.getPlugin() instanceof AsteroidSource) {

                                isAsteroidTerrain = true;

                            } else {

                                if (type != null && type.toLowerCase().contains("asteroid")) {

                                    isAsteroidTerrain = true;

                                }

                                if (terrainId != null && terrainId.toLowerCase().contains("asteroid")) {

                                    isAsteroidTerrain = true;

                                }

                            }



                            // Strict campaign check: only register true asteroid fields where the ship physically

                            // collides with asteroids on the map (containsEntity without artificial expansion buffers)

                            if (isAsteroidTerrain && terrain.getPlugin() != null) {

                                if (terrain.getPlugin().containsEntity(playerFleet)) {

                                    campaignTerrainHasAsteroids = true;

                                    break;

                                }

                            }

                        }

                    }



                    // Also check direct physical proximity/collision with orbiting campaign asteroids

                    if (!campaignTerrainHasAsteroids && loc.getAsteroids() != null) {

                        Vector2f playerLoc = playerFleet.getLocation();

                        float fleetRadius = playerFleet.getRadius();

                        for (SectorEntityToken astToken : loc.getAsteroids()) {

                            if (astToken == null || astToken.getLocation() == null) continue;

                            float dist = Misc.getDistance(playerLoc, astToken.getLocation());

                            if (dist <= fleetRadius + astToken.getRadius() + 100f) {

                                campaignTerrainHasAsteroids = true;

                                break;

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
        if (engine.isSimulation()) {
            simulationEnabled = enableInSimulation;
            if (!simulationEnabled) {
                activeStreams.clear();
                clearAllAsteroids(false);
            }
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

            maxBackgroundRocks = Math.max(160, Math.min(450, (int)(battleTargetCount * 0.60f)));

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

                megalithSpeed = 70.0f;
                megalithMinSpeed = 50.0f;
                megalithMaxSpeed = 90.0f;
                megalithMinRadius = 125.0f;
                megalithMaxRadius = 450.0f;

                minFieldRocks = 120;

                maxFieldRocks = 350;

                asteroidLifespan = 75.0f;

                targetedSnipeChance = 0.02f;

                figureEventInterval = 38.0f;

                enableMicroDodge = true;

                enableShieldReaction = true;

                enablePDTargeting = true;

                pdCriticalThreatsOnly = true;

                asteroidHealthMult = 0.425f;

                pdAsteroidDamageMult = 7.0f;

                asteroidCollisionDamageMult = 0.42f;

                derelictSpawnChance = 0.005f;

                debrisEasterEggChance = 0.080f;

                resourceEasterEggChance = 0.020f;

                otherEasterEggChance = 0.025f;

                easterEggSpeed = 300.0f;

                easterEggMagnetStrength = 1400.0f;

                derelictRescueHoldTime = 45.0f;

                derelictRescueChance = 1.0f;

                openSpaceEasterEggMult = 0.10f;
                openSpaceSwarmChance = 0.08f;

                break;

            case 2: // Balanced Standard (600 speed)

                setStreamSpeed(600.0f);

                megalithSpeed = 90.0f;
                megalithMinSpeed = 60.0f;
                megalithMaxSpeed = 120.0f;
                megalithMinRadius = 125.0f;
                megalithMaxRadius = 550.0f;

                minFieldRocks = 200;

                maxFieldRocks = 650;

                asteroidLifespan = 60.0f;

                targetedSnipeChance = 0.05f;

                figureEventInterval = 28.0f;

                enableMicroDodge = true;

                enableShieldReaction = true;

                enablePDTargeting = true;

                pdCriticalThreatsOnly = true;

                asteroidHealthMult = 0.51f;

                pdAsteroidDamageMult = 6.0f;

                asteroidCollisionDamageMult = 1.20f;

                derelictSpawnChance = 0.015f;

                debrisEasterEggChance = 0.160f;

                resourceEasterEggChance = 0.040f;

                otherEasterEggChance = 0.060f;

                easterEggSpeed = 400.0f;

                easterEggMagnetStrength = 1400.0f;

                derelictRescueHoldTime = 60.0f;

                derelictRescueChance = 1.0f;

                openSpaceEasterEggMult = 0.14f;
                openSpaceSwarmChance = 0.12f;

                break;

            case 3: // Asteroid Storm (1200 speed)

                setStreamSpeed(1200.0f);

                megalithSpeed = 110.0f;
                megalithMinSpeed = 80.0f;
                megalithMaxSpeed = 135.0f;
                megalithMinRadius = 125.0f;
                megalithMaxRadius = 550.0f;

                minFieldRocks = 300;

                maxFieldRocks = 850;

                asteroidLifespan = 45.0f;

                targetedSnipeChance = 0.08f;

                figureEventInterval = 18.0f;

                enableMicroDodge = true;

                enableShieldReaction = true;

                enablePDTargeting = true;

                pdCriticalThreatsOnly = false;

                asteroidHealthMult = 0.595f;

                pdAsteroidDamageMult = 6.5f;

                asteroidCollisionDamageMult = 2.16f;

                derelictSpawnChance = 0.035f;

                debrisEasterEggChance = 0.280f;

                resourceEasterEggChance = 0.080f;

                otherEasterEggChance = 0.120f;

                easterEggSpeed = 500.0f;

                easterEggMagnetStrength = 1600.0f;

                derelictRescueHoldTime = 60.0f;

                derelictRescueChance = 1.0f;

                openSpaceEasterEggMult = 0.16f;
                openSpaceSwarmChance = 0.18f;

                break;

            case 4: // Extreme Chaos: High Risk / High Reward (3000 speed, deadly collision, generous loot)

                setStreamSpeed(3000.0f);

                megalithSpeed = 120.0f;
                megalithMinSpeed = 90.0f;
                megalithMaxSpeed = 150.0f;
                megalithMinRadius = 125.0f;
                megalithMaxRadius = 550.0f;

                minFieldRocks = 450;

                maxFieldRocks = 1000;

                asteroidLifespan = 35.0f;

                targetedSnipeChance = 0.15f;

                figureEventInterval = 12.0f;

                enableMicroDodge = true;

                enableShieldReaction = true;

                enablePDTargeting = true;

                pdCriticalThreatsOnly = false;

                asteroidHealthMult = 0.68f;

                pdAsteroidDamageMult = 8.0f;

                asteroidCollisionDamageMult = 4.20f;

                derelictSpawnChance = 0.070f;

                debrisEasterEggChance = 0.450f;

                resourceEasterEggChance = 0.160f;

                otherEasterEggChance = 0.250f;

                easterEggSpeed = 650.0f;

                easterEggMagnetStrength = 2000.0f;

                derelictRescueHoldTime = 75.0f;

                derelictRescueChance = 1.0f;

                openSpaceEasterEggMult = 0.20f;
                openSpaceSwarmChance = 0.28f;

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

                playerKillResourceDropChance = (float) config.optDouble("playerKillResourceDropChance", playerKillResourceDropChance);

                playerKillFighterDropChance = (float) config.optDouble("playerKillFighterDropChance", playerKillFighterDropChance);

                enableMegalithBoulders = config.optBoolean("enableMegalithBoulders", enableMegalithBoulders);
                maxMegaliths = config.optInt("maxMegaliths", maxMegaliths);
                enableDeploymentSafety = config.optBoolean("enableDeploymentSafety", enableDeploymentSafety);
                deploymentSafetyDuration = (float) config.optDouble("deploymentSafetyDuration", deploymentSafetyDuration);
                megalithSpeed = (float) config.optDouble("megalithSpeed", megalithSpeed);
                megalithMinSpeed = (float) config.optDouble("megalithMinSpeed", megalithMinSpeed);
                megalithMaxSpeed = (float) config.optDouble("megalithMaxSpeed", megalithMaxSpeed);
                megalithSpawnInterval = (float) config.optDouble("megalithSpawnInterval", megalithSpawnInterval);
                megalithMinRadius = (float) config.optDouble("megalithMinRadius", megalithMinRadius);
                megalithMaxRadius = (float) config.optDouble("megalithMaxRadius", megalithMaxRadius);
                megalithArcFlight = config.optBoolean("megalithArcFlight", megalithArcFlight);
                megalithDerelictDropChance = (float) config.optDouble("megalithDerelictDropChance", megalithDerelictDropChance);
                enableStationAsteroidProtection = config.optBoolean("enableStationAsteroidProtection", enableStationAsteroidProtection);
                stationAsteroidDamageMult = (float) config.optDouble("stationAsteroidDamageMult", stationAsteroidDamageMult);
                enableStationDeflectionDome = config.optBoolean("enableStationDeflectionDome", enableStationDeflectionDome);
                stationDomeMargin = (float) config.optDouble("stationDomeMargin", stationDomeMargin);

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

                loadLunaSettingNumber("da_directorCycleMode", directorCycleMode);
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
                Boolean bOpenMeg = getLunaBoolean("da_enableOpenSpaceMegaliths");
                if (bOpenMeg != null) enableOpenSpaceMegaliths = bOpenMeg.booleanValue();
                Boolean bOpenSwarm = getLunaBoolean("da_enableOpenSpaceSwarm");
                if (bOpenSwarm != null) enableOpenSpaceSwarm = bOpenSwarm.booleanValue();
                loadLunaSettingNumber("da_openSpaceSwarmChance", openSpaceSwarmChance);



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

                loadLunaSettingNumber("da_playerKillResourceDropChance", playerKillResourceDropChance);

                loadLunaSettingNumber("da_playerKillFighterDropChance", playerKillFighterDropChance);

                Boolean bMegalith = getLunaBoolean("da_enableMegalithBoulders");
                if (bMegalith != null) enableMegalithBoulders = bMegalith.booleanValue();
                Boolean bArc = getLunaBoolean("da_megalithArcFlight");
                if (bArc != null) megalithArcFlight = bArc.booleanValue();
                Integer nMaxMeg = getLunaInt("da_maxMegaliths");
                if (nMaxMeg != null) maxMegaliths = nMaxMeg.intValue();
                Boolean bDepSafe = getLunaBoolean("da_enableDeploymentSafety");
                if (bDepSafe != null) enableDeploymentSafety = bDepSafe.booleanValue();
                loadLunaSettingNumber("da_deploymentSafetyDuration", deploymentSafetyDuration);
                loadLunaSettingNumber("da_megalithMinSpeed", megalithMinSpeed);
                loadLunaSettingNumber("da_megalithMaxSpeed", megalithMaxSpeed);
                loadLunaSettingNumber("da_megalithSpeed", megalithSpeed);
                loadLunaSettingNumber("da_megalithSpawnInterval", megalithSpawnInterval);
                loadLunaSettingNumber("da_megalithMinRadius", megalithMinRadius);
                loadLunaSettingNumber("da_megalithMaxRadius", megalithMaxRadius);
                loadLunaSettingNumber("da_megalithDerelictDropChance", megalithDerelictDropChance);
                loadLunaSettingNumber("da_stationAsteroidDamageMult", stationAsteroidDamageMult);
                Boolean bStnProt = getLunaBoolean("da_enableStationAsteroidProtection");
                if (bStnProt != null) enableStationAsteroidProtection = bStnProt.booleanValue();
                Boolean bStnDome = getLunaBoolean("da_enableStationDeflectionDome");
                if (bStnDome != null) enableStationDeflectionDome = bStnDome.booleanValue();
                loadLunaSettingNumber("da_stationDomeMargin", stationDomeMargin);

                loadLunaSettingNumber("da_derelictSpawnChance", derelictSpawnChance);

                loadLunaSettingNumber("da_otherEasterEggChance", otherEasterEggChance);

                loadLunaSettingNumber("da_derelictRescueHoldTime", derelictRescueHoldTime);

                loadLunaSettingNumber("da_derelictRescueChance", derelictRescueChance);

                loadLunaSettingNumber("da_easterEggMagnetStrength", easterEggMagnetStrength);

                loadLunaSettingNumber("da_easterEggSpeed", easterEggSpeed);



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

        if (minFieldRocks < 20) minFieldRocks = 50;

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

        if (playerKillResourceDropChance < 0f) playerKillResourceDropChance = 0f;

        if (playerKillResourceDropChance > 1f) playerKillResourceDropChance = 1f;

        if (playerKillFighterDropChance < 0f) playerKillFighterDropChance = 0f;

        if (playerKillFighterDropChance > 1f) playerKillFighterDropChance = 1f;

        if (megalithMinRadius < 30f) megalithMinRadius = 80f;
        if (megalithMaxRadius < megalithMinRadius) megalithMaxRadius = megalithMinRadius + 50f;
        if (megalithSpawnInterval < 5f) megalithSpawnInterval = 20f;
        if (maxMegaliths < 1) maxMegaliths = 1;
        if (maxMegaliths > 60) maxMegaliths = 60;
        if (megalithMinSpeed < 10f) megalithMinSpeed = 60f;
        if (megalithMaxSpeed < megalithMinSpeed) megalithMaxSpeed = megalithMinSpeed + 20f;
        if (megalithSpeed < 20f) megalithSpeed = 90f;
        if (megalithDerelictDropChance < 0f) megalithDerelictDropChance = 0f;
        if (megalithDerelictDropChance > 0.05f) megalithDerelictDropChance = 0.05f;
        if (stationAsteroidDamageMult < 0f) stationAsteroidDamageMult = 0f;
        if (stationAsteroidDamageMult > 1f) stationAsteroidDamageMult = 1f;
        if (stationDomeMargin < 40f) stationDomeMargin = 140f;

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

        if (fieldId.equals("da_directorCycleMode")) directorCycleMode = num.intValue();
        else if (fieldId.equals("da_minFieldRocks")) minFieldRocks = num.intValue();

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
        else if (fieldId.equals("da_openSpaceSwarmChance")) openSpaceSwarmChance = num.floatValue();

        else if (fieldId.equals("da_activePreset")) activePreset = num.intValue();

        else if (fieldId.equals("da_pdMaxFluxThreshold")) pdMaxFluxThreshold = num.floatValue();

        else if (fieldId.equals("da_asteroidHealthMult")) asteroidHealthMult = num.floatValue();

        else if (fieldId.equals("da_pdAsteroidDamageMult")) pdAsteroidDamageMult = num.floatValue();

        else if (fieldId.equals("da_asteroidCollisionDamageMult")) asteroidCollisionDamageMult = num.floatValue();

        else if (fieldId.equals("da_playerKillResourceDropChance")) playerKillResourceDropChance = num.floatValue();

        else if (fieldId.equals("da_playerKillFighterDropChance")) playerKillFighterDropChance = num.floatValue();
        else if (fieldId.equals("da_maxMegaliths")) maxMegaliths = num.intValue();
        else if (fieldId.equals("da_megalithSpeed")) megalithSpeed = num.floatValue();
        else if (fieldId.equals("da_megalithSpawnInterval")) megalithSpawnInterval = num.floatValue();
        else if (fieldId.equals("da_megalithMinRadius")) megalithMinRadius = num.floatValue();
        else if (fieldId.equals("da_megalithMaxRadius")) megalithMaxRadius = num.floatValue();
        else if (fieldId.equals("da_megalithDerelictDropChance")) megalithDerelictDropChance = num.floatValue();
        else if (fieldId.equals("da_stationAsteroidDamageMult")) stationAsteroidDamageMult = num.floatValue();
        else if (fieldId.equals("da_stationDomeMargin")) stationDomeMargin = num.floatValue();

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

                Boolean simRefVal = getLunaBoolean("da_enableInSimulation");
                if (simRefVal != null) {
                    boolean newSim = simRefVal.booleanValue();
                    if (newSim != enableInSimulation) {
                        enableInSimulation = newSim;
                        if (engine.isSimulation()) {
                            simulationEnabled = newSim;
                            if (!simulationEnabled) {
                                activeStreams.clear();
                                clearAllAsteroids(true);
                            }
                        }
                    }
                }



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

                            maxBackgroundRocks = Math.max(160, Math.min(450, (int)(battleTargetCount * 0.60f)));

                            backgroundSpawnInterval = Math.max(0.015f, 0.055f * (300f / Math.max(300, battleTargetCount)));

                            // Silenced preset switch message in simulation

                        } else {

                            if (engine != null && engine.getCombatUI() != null && engine.isSimulation()) {

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



                Number nPkRes = getLunaNumber("da_playerKillResourceDropChance");

                if (nPkRes != null) playerKillResourceDropChance = nPkRes.floatValue();

                Number nPkFighter = getLunaNumber("da_playerKillFighterDropChance");
                if (nPkFighter != null) playerKillFighterDropChance = nPkFighter.floatValue();

                Boolean bMegalith = getLunaBoolean("da_enableMegalithBoulders");
                if (bMegalith != null) enableMegalithBoulders = bMegalith.booleanValue();
                Boolean bArc = getLunaBoolean("da_megalithArcFlight");
                if (bArc != null) megalithArcFlight = bArc.booleanValue();
                Number nInt = getLunaNumber("da_megalithSpawnInterval");
                if (nInt != null) megalithSpawnInterval = nInt.floatValue();
                Boolean bDepSafe = getLunaBoolean("da_enableDeploymentSafety");
                if (bDepSafe != null) enableDeploymentSafety = bDepSafe.booleanValue();
                Number nDepDur = getLunaNumber("da_deploymentSafetyDuration");
                if (nDepDur != null) deploymentSafetyDuration = nDepDur.floatValue();
                Number nMinSpd = getLunaNumber("da_megalithMinSpeed");
                if (nMinSpd != null) megalithMinSpeed = nMinSpd.floatValue();
                Number nMaxSpd = getLunaNumber("da_megalithMaxSpeed");
                if (nMaxSpd != null) megalithMaxSpeed = nMaxSpd.floatValue();
                Number nMinR = getLunaNumber("da_megalithMinRadius");
                if (nMinR != null) megalithMinRadius = nMinR.floatValue();
                Number nMaxR = getLunaNumber("da_megalithMaxRadius");
                if (nMaxR != null) megalithMaxRadius = nMaxR.floatValue();
                Integer nMaxMeg = getLunaInt("da_maxMegaliths");
                if (nMaxMeg != null) maxMegaliths = nMaxMeg.intValue();
                Number nMgSpd = getLunaNumber("da_megalithSpeed");
                if (nMgSpd != null) megalithSpeed = nMgSpd.floatValue();
                Number nMgDer = getLunaNumber("da_megalithDerelictDropChance");
                if (nMgDer != null) megalithDerelictDropChance = nMgDer.floatValue();
                Boolean bOpenMeg = getLunaBoolean("da_enableOpenSpaceMegaliths");
                if (bOpenMeg != null) enableOpenSpaceMegaliths = bOpenMeg.booleanValue();
                Boolean bOpenSwarm = getLunaBoolean("da_enableOpenSpaceSwarm");
                if (bOpenSwarm != null) enableOpenSpaceSwarm = bOpenSwarm.booleanValue();
                Number nSwarmChance = getLunaNumber("da_openSpaceSwarmChance");
                if (nSwarmChance != null) openSpaceSwarmChance = nSwarmChance.floatValue();
                Number nStnMult = getLunaNumber("da_stationAsteroidDamageMult");
                if (nStnMult != null) stationAsteroidDamageMult = nStnMult.floatValue();
                Boolean bStnProt = getLunaBoolean("da_enableStationAsteroidProtection");
                if (bStnProt != null) enableStationAsteroidProtection = bStnProt.booleanValue();
                Boolean bStnDome = getLunaBoolean("da_enableStationDeflectionDome");
                if (bStnDome != null) enableStationDeflectionDome = bStnDome.booleanValue();
                Number nStnMargin = getLunaNumber("da_stationDomeMargin");
                if (nStnMargin != null) stationDomeMargin = nStnMargin.floatValue();

                if (nPkFighter != null) playerKillFighterDropChance = nPkFighter.floatValue();

                if (activePreset == 0) {

                    // Preset 0 = Custom mode: user manually tunes individual sliders!

                    Number nSpeed = getLunaNumber("da_fieldSurgeSpeed");

                    if (nSpeed != null) {

                        float newSpeed = nSpeed.floatValue();

                        if (Math.abs(newSpeed - streamSpeed) > 10f) {

                            setStreamSpeed(newSpeed);

                        }

                    }



                    Number nDirectorMode = getLunaNumber("da_directorCycleMode");
if (nDirectorMode != null) directorCycleMode = nDirectorMode.intValue();
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

                        // Do NOT call f.getGenericType()! In obfuscated game it throws ClassFormatError: Illegal field name
                        // The vanilla asteroid field generator list in com.fs.starfarer.combat.A.B is field "for"
                        List<?> list = (List<?>) f.get(combatMap);

                        if (list != null) {

                            boolean isAsteroidFieldList = "for".equals(f.getName());

                            if (!isAsteroidFieldList && !list.isEmpty()) {

                                Object first = list.get(0);

                                if (first != null && first.getClass().getName().endsWith(".combat.A.E")) {

                                    isAsteroidFieldList = true;

                                }

                            }

                            if (isAsteroidFieldList) {

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



    private void registerNewShipDeployment(ShipAPI s) {
        if (s == null || !s.isAlive()) return;
        if (s.isFighter() && s != engine.getPlayerShip()) return;
        if (s.isDrone()) return;
        if (s.isStation() || s.isStationModule() || s.getParentStation() != null) return;

        if (shipSpawnTimes.containsKey(s) || shipDomesActivated.contains(s)) return;

        float spawnTime = totalCombatTime;
        shipSpawnTimes.put(s, Float.valueOf(spawnTime));
        shipDomesActivated.add(s);
        s.setCustomData("DA_SPAWN_TIME", Float.valueOf(spawnTime));

        triggerShipDomeActivation(s);
    }

    @Override
    public void reportFleetMemberDeployed(DeployedFleetMemberAPI member) {
        if (member != null && member.getShip() != null) {
            registerNewShipDeployment(member.getShip());
        }
    }

    @Override

    public void advance(float amount, List<InputEventAPI> events) {

        if (!active || engine == null) return;



        totalCombatTime += amount;

        // Track per-ship deployment spawn time for kinetic deployment safety and ship deflection dome
        activeShipDomes.clear();
        if (enableDeploymentSafety && engine.getShips() != null) {
            List<ShipAPI> sList = engine.getShips();
            for (int i = 0; i < sList.size(); i++) {
                ShipAPI s = sList.get(i);
                if (s == null || !s.isAlive()) continue;
                if (s.isFighter() && s != engine.getPlayerShip()) continue;
                if (s.isDrone()) continue;
                if (s.isStation() || s.isStationModule() || s.getParentStation() != null) continue;

                if (!shipSpawnTimes.containsKey(s) && !shipDomesActivated.contains(s)) {
                    registerNewShipDeployment(s);
                }

                Float spawnTime = shipSpawnTimes.get(s);
                float timeSinceSpawn = (spawnTime != null) ? (totalCombatTime - spawnTime.floatValue()) : 0f;
                if (timeSinceSpawn >= 0f && timeSinceSpawn < deploymentSafetyDuration) {
                    activeShipDomes.add(s);
                } else if (!shipDomesDeactivated.contains(s)) {
                    shipDomesDeactivated.add(s);
                    s.setCustomData("DA_DOME_EXPIRED_NOTIFIED", Boolean.TRUE);
                    triggerShipDomeDeactivation(s);
                }
            }
        }

        advanceDetectionSound(amount);

        updateStationDomes(amount);



        lunaCheckTimer += amount;

        if (lunaCheckTimer >= 0.5f) {

            lunaCheckTimer = 0f;

            refreshLunaSettings();

        }

        // Process Simulation Hotkeys:
        // ONLY ACTIVE IN SIMULATION! In regular campaign battles, hotkeys do nothing.
        if (engine.isSimulation()) {
            if (summonKeyCooldown > 0f) {
                summonKeyCooldown -= amount;
            }
            if (summonContainerKeyCooldown > 0f) {
                summonContainerKeyCooldown -= amount;
            }
            if (summonMegalithKeyCooldown > 0f) {
                summonMegalithKeyCooldown -= amount;
            }
            if (summonSwarmKeyCooldown > 0f) {
                summonSwarmKeyCooldown -= amount;
            }

            boolean summonTriggered = false;
            boolean summonContainerTriggered = false;
            boolean summonMegalithTriggered = false;
            boolean summonSwarmTriggered = false;

            if (events != null) {
                boolean shiftHeld = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                boolean ctrlHeld = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);

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
                        } else if (key == Keyboard.KEY_L) {
                            event.consume();
                            if (summonMegalithKeyCooldown <= 0f) summonMegalithTriggered = true;
                            break;
                        } else if (key == Keyboard.KEY_K) {
                            event.consume();
                            if (summonSwarmKeyCooldown <= 0f) summonSwarmTriggered = true;
                            break;
                        } else if (key == Keyboard.KEY_SLASH) {
                            event.consume();
                            simulationEnabled = !simulationEnabled;
                            if (!simulationEnabled) {
                                activeStreams.clear();
                                clearAllAsteroids(true);
                                if (engine.getCombatUI() != null) {
                                    engine.getCombatUI().addMessage(1, IS_RUSSIAN ? "Dynamic Asteroids: Поток астероидов ОТКЛЮЧЕН (Нажмите [/] для включения)" : "Dynamic Asteroids: Stream DISABLED (Press [/] to enable)", Color.YELLOW);
                                }
                            } else {
                                if (engine.getCombatUI() != null) {
                                    engine.getCombatUI().addMessage(1, IS_RUSSIAN ? "Dynamic Asteroids: Поток астероидов ВКЛЮЧЕН (Нажмите [/] для отключения)" : "Dynamic Asteroids: Stream ENABLED (Press [/] to disable)", Color.GREEN);
                                }
                            }
                        } else if (shiftHeld && (key == Keyboard.KEY_EQUALS || key == Keyboard.KEY_ADD)) {
                            event.consume();
                            battleTargetCount = Math.min(10000, battleTargetCount + 50);
                            maxBackgroundRocks = Math.max(160, Math.min(1500, (int)(battleTargetCount * 0.60f)));
                            backgroundSpawnInterval = Math.max(0.005f, 0.055f * (300f / Math.max(300, battleTargetCount)));
                            showLimitFeedback(IS_RUSSIAN ? "Лимит астероидов УВЕЛИЧЕН: " + battleTargetCount + " шт." : "Asteroid Limit INCREASED: " + battleTargetCount + " rocks", Color.GREEN);
                        } else if (shiftHeld && (key == Keyboard.KEY_MINUS || key == Keyboard.KEY_SUBTRACT)) {
                            event.consume();
                            battleTargetCount = Math.max(50, battleTargetCount - 50);
                            maxBackgroundRocks = Math.max(50, Math.min(1500, (int)(battleTargetCount * 0.60f)));
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
            if (!summonMegalithTriggered && summonMegalithKeyCooldown <= 0f) {
                if (Keyboard.isKeyDown(Keyboard.KEY_L)) {
                    summonMegalithTriggered = true;
                }
            }
            if (!summonSwarmTriggered && summonSwarmKeyCooldown <= 0f) {
                if (Keyboard.isKeyDown(Keyboard.KEY_K)) {
                    summonSwarmTriggered = true;
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
            if (summonMegalithTriggered) {
                summonMegalithKeyCooldown = 0.5f;
                summonMegalithNearPlayer();
            }
            if (summonSwarmTriggered) {
                summonSwarmKeyCooldown = 0.8f;
                summonTransitSwarmInSimulation();
            }
        }

        // Show simulation one-time hint

        if (engine.isSimulation() && !simulationHintShown) {

            simulationHintShown = true;

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

                    if (currentDirectorState == DirectorState.LULL) {

                        sub += IS_RUSSIAN ? " | Затишье" : " | Calm Lull";

                    } else if (!activeStreams.isEmpty()) {

                        sub += IS_RUSSIAN ? (" | Формаций: " + activeStreams.size()) : (" | Formations: " + activeStreams.size() + " active");

                    } else {

                        sub += IS_RUSSIAN ? (" | Поток: " + activeBackgroundCount) : (" | Stream: " + activeBackgroundCount + " rocks");

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

                    if (ast == null || ast.isExpired()) {

                    if (ast != null && !ast.getCustomData().containsKey("IS_EASTER_EGG")) {

                        Object lastHitObj = ast.getCustomData().get("DA_LAST_PLAYER_HIT_TIME");

                        if (lastHitObj instanceof Float) {

                            float lastHit = ((Float) lastHitObj).floatValue();

                            if (totalCombatTime - lastHit < 0.45f) {

                                processPlayerAsteroidKill(ast, getPlayerShipSafely());

                            }

                        }

                    }

                    continue;

                }

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



        // Simulation stream control handled dynamically in spawner loop
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



        // Strict border margin (replaced by 5-second out of bounds exit despawn)



        // Advance existing asteroids

        List<CombatEntityAPI> asteroids = engine.getAsteroids();

        int activeCount = 0;
        activeBackgroundCount = 0;



        if (asteroids != null && !asteroids.isEmpty()) {

            List<CombatEntityAPI> toRemove = null;

            List<CombatEntityAPI> toPoof = null;

            currentMegalithCount = 0;



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

                    // Re-disable vanilla generators if an untracked rock leaked through
                    if (!vanillaFieldsDisabled || totalCombatTime < 10.0f) {

                        disableVanillaAsteroidFields();

                    }

                    continue;

                }



                boolean isMegalith = ast.getCustomData().containsKey("DA_IS_MEGALITH");

                // Strict map bounds check (borders where repellor forcefields push ships):
                boolean outside = (loc.x < focalCX - halfW ||
                                   loc.x > focalCX + halfW ||
                                   loc.y < focalCY - halfH ||
                                   loc.y > focalCY + halfH);

                boolean hasEntered = ast.getCustomData().containsKey("DA_ENTERED_MAP");

                if (!hasEntered) {
                    if (!outside) {
                        // Asteroid crossed into the playable map area or was pre-seeded inside!
                        ast.getCustomData().put("DA_ENTERED_MAP", Boolean.TRUE);
                        ast.getCustomData().remove("DA_OUT_OF_BOUNDS_TIMER");
                    } else {
                        // Asteroid is currently outside. Check if it is flying inward into the map bounds:
                        Vector2f vel = ast.getVelocity();
                        boolean isHeadingInward = false;
                        if (vel != null) {
                            if (loc.x < focalCX - halfW && vel.x > 0f) isHeadingInward = true;
                            if (loc.x > focalCX + halfW && vel.x < 0f) isHeadingInward = true;
                            if (loc.y < focalCY - halfH && vel.y > 0f) isHeadingInward = true;
                            if (loc.y > focalCY + halfH && vel.y < 0f) isHeadingInward = true;
                            float dx = focalCX - loc.x;
                            float dy = focalCY - loc.y;
                            if ((vel.x * dx + vel.y * dy) > 0f) isHeadingInward = true;
                        }

                        if (!isHeadingInward) {
                            // Flying away from map or stationary outside: despawn after 5 seconds
                            Object timerObj = ast.getCustomData().get("DA_OUT_OF_BOUNDS_TIMER");
                            float timer = (timerObj instanceof Float) ? ((Float) timerObj).floatValue() + amount : amount;
                            ast.getCustomData().put("DA_OUT_OF_BOUNDS_TIMER", timer);
                            if (timer >= 5.0f) {
                                if (toRemove == null) toRemove = new ArrayList<CombatEntityAPI>();
                                toRemove.add(ast);
                                continue;
                            }
                        } else {
                            // Flying inward toward the map: let it enter, do not delete!
                            ast.getCustomData().remove("DA_OUT_OF_BOUNDS_TIMER");
                            Object birthObj = ast.getCustomData().get(SPAWN_TIME_KEY);
                            if (birthObj instanceof Float && (totalCombatTime - ((Float) birthObj).floatValue()) > 30.0f) {
                                if (toRemove == null) toRemove = new ArrayList<CombatEntityAPI>();
                                toRemove.add(ast);
                                continue;
                            }
                        }
                    }
                } else {
                    // Asteroid has entered the map. If it now flies outside across the border:
                    if (outside) {
                        Object timerObj = ast.getCustomData().get("DA_OUT_OF_BOUNDS_TIMER");
                        float timer = (timerObj instanceof Float) ? ((Float) timerObj).floatValue() + amount : amount;
                        ast.getCustomData().put("DA_OUT_OF_BOUNDS_TIMER", timer);
                        if (timer >= 5.0f) {
                            // 5 seconds past the border -> delete!
                            if (toRemove == null) toRemove = new ArrayList<CombatEntityAPI>();
                            toRemove.add(ast);
                            continue;
                        }
                    } else {
                        ast.getCustomData().remove("DA_OUT_OF_BOUNDS_TIMER");
                    }
                }

                // Lifespan expiration:
                // Megaliths NEVER evaporate or poof from asteroidLifespan! They sail until crossing out of bounds.
                // 1200s (20 minutes) timeout is kept only as an emergency gc failsafe.
                Object birthObj = ast.getCustomData().get(SPAWN_TIME_KEY);
                if (birthObj instanceof Float) {
                    float age = totalCombatTime - ((Float) birthObj).floatValue();
                    if (isMegalith) {
                        if (age >= 1200.0f) {
                            if (toRemove == null) toRemove = new ArrayList<CombatEntityAPI>();
                            toRemove.add(ast);
                            continue;
                        }
                    } else {
                        if (age >= Math.max(asteroidLifespan, 120.0f)) {
                            if (toPoof == null) toPoof = new ArrayList<CombatEntityAPI>();
                            toPoof.add(ast);
                            continue;
                        }
                    }
                }



                // Kinetic Cruise Control

                // Advance Megalith Arc Trajectory & Station Deflection
                // If untethered (touched or shot by ships), becomes a 100% free Newtonian body that can be pushed!
                if (ast.getCustomData().containsKey("DA_IS_MEGALITH")) {
                    if (!ast.getCustomData().containsKey("DA_NO_MAINTAIN_VELOCITY")) {
                        Object arcObj = ast.getCustomData().get("DA_ARC_RATE");
                        if (arcObj instanceof Float) {
                            float arcRate = ((Float) arcObj).floatValue();
                            if (Math.abs(arcRate) > 0.00001f) {
                                Object accumObj = ast.getCustomData().get("DA_ACCUM_ARC");
                                float accumulated = (accumObj instanceof Float) ? ((Float) accumObj).floatValue() : 0f;
                                float maxDeflection = (float) Math.toRadians(25.0f); // Gentle celestial arc: max 25 degrees total deflection across entire sector
                                if (Math.abs(accumulated) < maxDeflection) {
                                    Vector2f vel = ast.getVelocity();
                                    if (vel != null) {
                                        float curAngle = (float) Math.atan2(vel.y, vel.x);
                                        float step = arcRate * amount;
                                        float newAccum = accumulated + step;
                                        if (Math.abs(newAccum) > maxDeflection) {
                                            step = (newAccum > 0 ? maxDeflection : -maxDeflection) - accumulated;
                                            newAccum = (newAccum > 0 ? maxDeflection : -maxDeflection);
                                        }
                                        ast.setCustomData("DA_ACCUM_ARC", Float.valueOf(newAccum));
                                        float newAngle = curAngle + step;
                                        float curSpeed = vel.length();
                                        Object origSpdObj = ast.getCustomData().get("DA_MEGALITH_SPEED");
                                        if (origSpdObj instanceof Float) {
                                            float origSpd = ((Float) origSpdObj).floatValue();
                                            if (curSpeed < origSpd * 0.75f) curSpeed = origSpd;
                                        }
                                        vel.x = (float) Math.cos(newAngle) * curSpeed;
                                        vel.y = (float) Math.sin(newAngle) * curSpeed;
                                        ast.setCustomData(BASE_VX_KEY, Float.valueOf(vel.x));
                                        ast.setCustomData(BASE_VY_KEY, Float.valueOf(vel.y));
                                    }
                                }
                            }
                        }
                    }
                }

                // Station Deflection Dome: All asteroids part and flow smoothly around station perimeters
                applyStationDeflectionToAsteroid(ast, amount);

                // Ship Deployment Deflection Dome: All asteroids part and flow smoothly around newly deployed ships for 7 seconds
                applyShipDeflectionToAsteroid(ast, amount);

                maintainVelocity(ast);

                if (ast.getCustomData().containsKey("DA_IS_MEGALITH")) {
                    currentMegalithCount++;
                }
                if (ast.getCustomData().containsKey("DA_IS_BACKGROUND")) {
                    activeBackgroundCount++;
                }

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



        // Dynamic Asteroids stream spawner governor: when disabled in simulator, skip stream rock generation
        boolean allowStreamSpawning = true;
        if (engine.isSimulation() && !simulationEnabled) {
            allowStreamSpawning = false;
        }

        if (allowStreamSpawning) {

            // =========================================================================
            // OPEN SPACE SPAWN LOGIC (Quiet, rare solitary stray meteors, 1-3 boulders, transit swarm)
            // =========================================================================
            if (!isInAsteroidField && !isTitleScreen) {

                // 1. Initial battle pre-seed: populate open space with 1-2 starting boulders immediately
                if (!openSpacePreSeeded && enableMegalithBoulders && enableOpenSpaceMegaliths) {
                    openSpacePreSeeded = true;
                    int count = Math.min(openSpaceTargetMegaliths, 2);
                    for (int i = 0; i < count; i++) {
                        spawnPreSeededMegalith(halfW, halfH);
                    }
                }

                // 2. Solitary stray meteors
                openSpaceTimer += amount;
                if (openSpaceTimer >= openSpaceCurrentDelay) {
                    openSpaceTimer = 0f;
                    openSpaceCurrentDelay = openSpaceMinDelay + random.nextFloat() * (openSpaceMaxDelay - openSpaceMinDelay);
                    if (activeCount < openSpaceTargetCount) {
                        spawnOpenSpaceMeteor(focalCX, focalCY, halfW, halfH);
                    }
                }

                // 3. Maintain 1-3 colossal boulders in open space
                if (enableMegalithBoulders && enableOpenSpaceMegaliths) {
                    openSpaceMegalithTimer += amount;
                    if (currentMegalithCount < openSpaceTargetMegaliths && openSpaceMegalithTimer >= 12.0f) {
                        openSpaceMegalithTimer = 0f;
                        spawnNaturalMegalith(focalCX, focalCY, halfW, halfH);
                    }
                }

                // 4. Rare transit swarm streaking across open space
                if (enableOpenSpaceSwarm && activeStreams.isEmpty()) {
                    openSpaceSwarmTimer += amount;
                    if (openSpaceSwarmTimer >= openSpaceSwarmDelay) {
                        openSpaceSwarmTimer = 0f;
                        openSpaceSwarmDelay = 60.0f + random.nextFloat() * 60.0f;
                        if (random.nextFloat() < openSpaceSwarmChance) {
                            triggerOpenSpaceTransitSwarm(focalCX, focalCY, halfW, halfH);
                        }
                    }
                }

            } else {

                // =========================================================================
                // ASTEROID FIELD SPAWN LOGIC (300 ROCKS CAPACITY):
                // 1. Constant Baseline Avalanche (Steady flow in constant direction)
                // 2. Paced Formation Waves (Every 25-35s: Massive dense shapes, 1-3 angles, 5% Snipe)
                // =========================================================================

                // Initial battle pre-seed: populate sector with starting baseline avalanche rocks immediately
                if (!backgroundPreSeeded && isInAsteroidField) {
                    backgroundPreSeeded = true;
                    int initialCount = Math.min(maxBackgroundRocks, 90);
                    for (int bi = 0; bi < initialCount; bi++) {
                        spawnPreSeededBackgroundRock(focalCX, focalCY, halfW, halfH);
                    }
                }

                // 1. Dynamic Director: Orchestrates living cycles (Torrent -> Wave(s) -> Lull)
                advanceDirector(amount, focalCX, focalCY, halfW, halfH);

                // 1.2. Baseline Avalanche Spawner (active during TORRENT & BURST, paused during LULL & FORMATION)
                boolean allowBaselineSpawn = (currentDirectorState == DirectorState.TORRENT || currentDirectorState == DirectorState.BURST);
                if (directorCycleMode == 1) {
                    allowBaselineSpawn = true; // Mode 1: Always continuous
                } else if (directorCycleMode == 2) {
                    allowBaselineSpawn = false; // Mode 2: Only waves & lulls
                }

                if (allowBaselineSpawn) {
                    backgroundSpawnTimer += amount;

                    if (backgroundSpawnTimer >= backgroundSpawnInterval) {
                        backgroundSpawnTimer = 0f;
                        if (activeBackgroundCount < maxBackgroundRocks) {
                            spawnBackgroundAsteroid(focalCX, focalCY, halfW, halfH);
                            activeBackgroundCount++;
                            activeCount++;
                        }
                    }
                }

                // 1.5. Colossal Megalith Spawner (Maintains up to maxMegaliths, sailing steadily across all phases)
                if (enableMegalithBoulders) {
                    // Initial battle pre-seed: populate sector with starting megaliths smoothly
                    if (!megalithsPreSeeded && isInAsteroidField) {
                        megalithsPreSeeded = true;
                        int initialCount = Math.min(maxMegaliths, 4);
                        for (int mi = 0; mi < initialCount; mi++) {
                            spawnPreSeededMegalith(halfW, halfH);
                        }
                    }

                    megalithTimer += amount;
                    // Cosmic pacing: maintain a majestic presence of 4-8 colossal boulders without crowding
                    float targetMegaliths = Math.min(maxMegaliths, 8);
                    float interval = (currentMegalithCount < 3) ? 14.0f : ((currentMegalithCount < targetMegaliths) ? 28.0f : megalithSpawnInterval);
                    if (currentMegalithCount < maxMegaliths && megalithTimer >= interval) {
                        megalithTimer = 0f;
                        spawnNaturalMegalith(focalCX, focalCY, halfW, halfH);
                    }
                }

            } // End of else (Asteroid Field & Title screen)

            // 3. Process Active Formation Streams (Dense heavy bursts across 1, 2, or 3 concurrent angles, or open space transit swarms)
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
                            if (activeCount < battleTargetCount + 80) {
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

        } // end if (allowStreamSpawning)

        // Fleet AI Collision Avoidance & Interception

        advanceAIAvoidance(amount);



        // Advance Easter Eggs (Derelicts, Caches, Probes, Tanks)

        checkPlayerDestroyedAsteroids();



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

            // Task 3: If combat ended while player was stabilizing an active derelict on map, finalize its stabilization!
            if (activeEasterEggEntities != null && !activeEasterEggEntities.isEmpty()) {
                for (int eIdx = 0; eIdx < activeEasterEggEntities.size(); eIdx++) {
                    CombatEntityAPI egg = activeEasterEggEntities.get(eIdx);
                    if (egg == null || egg.isExpired()) continue;
                    String eggType = (String) egg.getCustomData().get("EASTER_EGG_TYPE");
                    if ("DERELICT".equals(eggType) && egg.getHitpoints() > 0f) {
                        Boolean rescued = (Boolean) egg.getCustomData().get("DERELICT_RESCUED");
                        if (rescued == null || !rescued.booleanValue()) {
                            Float holdT = (Float) egg.getCustomData().get("DERELICT_HOLD_TIMER");
                            boolean engaged = Boolean.TRUE.equals(egg.getCustomData().get("DERELICT_PLAYER_ENGAGED"));
                            if (engaged || (holdT != null && holdT.floatValue() > 0f)) {
                                egg.getCustomData().put("DERELICT_RESCUED", Boolean.TRUE);
                                String vId = (String) egg.getCustomData().get("DERELICT_VARIANT_ID");
                                String dName = (String) egg.getCustomData().get("DERELICT_NAME");
                                if (vId != null) {
                                    if (!rescuedDerelictVariantIds.contains(vId)) {
                                        rescuedDerelictVariantIds.add(vId);
                                    }
                                    derelictSalvageRecords.put(vId, new DerelictSalvageRecord(
                                        vId, dName, derelictRescueChance, 0.15f, 0.35f
                                    ));
                                }
                                if (engine.getCombatUI() != null) {
                                    String msg = IS_RUSSIAN ?
                                        ("БОЙ ЗАВЕРШЕН: Экстренная стабилизация остова (" + dName + ") завершена успешно!") :
                                        ("COMBAT OVER: Emergency derelict (" + dName + ") stabilization completed successfully!");
                                    engine.getCombatUI().addMessage(1, msg, new Color(60, 255, 140));
                                }
                            }
                        }
                    }
                }
            }

            // Task 2: Check if player has at least one ship alive on the battlefield at the end of combat
            boolean playerAlive = false;
            if (engine.getShips() != null) {
                for (ShipAPI s : engine.getShips()) {
                    if (s != null && s.isAlive() && !s.isHulk() && s.getOwner() == 0 && !s.isFighter()) {
                        playerAlive = true;
                        break;
                    }
                }
            }

            if (!derelictSalvageRecords.isEmpty() && !engine.isSimulation() && engine.isInCampaign() && Global.getSector() != null && playerAlive) {

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

        if (engine == null || !engine.isSimulation()) return;



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

        if (engine == null || !engine.isSimulation()) return;



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

    private void triggerOpenSpaceTransitSwarm(float focalCX, float focalCY, float halfW, float halfH) {
        if (engine == null) return;

        // Rare transit swarm streaking across open deep space
        ActiveFigureStream stream = new ActiveFigureStream();
        // 65% Comet swarm (cyan glowing comets) or 35% Lance beam (golden cosmic spark trail)
        stream.type = (random.nextFloat() < 0.65f) ? FigureType.COMET_SWARM : FigureType.LANCE_BEAM;
        int count = 18 + random.nextInt(12); // 18 to 29 rocks
        stream.rocksRemaining = count;
        stream.burstInterval = (stream.type == FigureType.COMET_SWARM) ? 0.010f : 0.018f;
        stream.beamSpread = 450f + random.nextFloat() * 150f;
        stream.timeElapsed = 0f;
        stream.totalSpawnDuration = (count * stream.burstInterval) + 1.2f;

        float swarmSpeed = Math.max(600f, streamSpeed * 1.15f);
        stream.lifetime = (18000f / swarmSpeed) + stream.totalSpawnDuration + 5f;

        // Random entry angle across the boundary
        float entryAngle = random.nextFloat() * (float) (Math.PI * 2.0);
        Vector2f startPt = getRectangleBoundaryPoint(focalCX, focalCY, halfW, halfH, entryAngle);
        stream.origin.set(startPt.x, startPt.y);

        // Transit trajectory passing across the sector towards opposite side
        float exitAngle = entryAngle + (float) Math.PI + (random.nextFloat() - 0.5f) * 0.5f;
        Vector2f endPt = getRectangleBoundaryPoint(focalCX, focalCY, halfW, halfH, exitAngle);

        float dx = endPt.x - startPt.x;
        float dy = endPt.y - startPt.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist > 0.001f) {
            stream.velocity.set((dx / dist) * swarmSpeed, (dy / dist) * swarmSpeed);
        } else {
            stream.velocity.set(-swarmSpeed, 0f);
        }

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

    public void summonTransitSwarmInSimulation() {
        if (engine == null || !engine.isSimulation()) return;
        ShipAPI playerShip = getPlayerShipSafely();
        if (playerShip == null || playerShip.getLocation() == null) return;

        float halfW = engine.getMapWidth() * 0.5f;
        float halfH = engine.getMapHeight() * 0.5f;
        float focalCX = 0f;
        float focalCY = 0f;

        triggerOpenSpaceTransitSwarm(focalCX, focalCY, halfW, halfH);
        showLimitFeedback(IS_RUSSIAN ? "ТЕСТ: Запущен транзитный рой!" : "TEST: Transit swarm launched!", Color.CYAN);
    }

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

                float spd = 75.0f; // Derelict cruising speed (gentle 60-100 u/s drift)

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



        // Natural space debris and ship wreckage tumbling among the flowing asteroids
        if (debrisEasterEggChance > 0f && random.nextFloat() < (debrisEasterEggChance * 0.12f)) {
            spawnOtherEasterEgg(spawnX, spawnY, vx, vy, false);
            return;
        }

        int type = rollAsteroidType(false);

        CombatEntityAPI ast = spawnSingleAsteroidEntity(type, spawnX, spawnY, vx, vy, 0);
        if (ast != null) {
            ast.setCustomData("DA_IS_BACKGROUND", Boolean.TRUE);
        }

    }

    public void spawnPreSeededBackgroundRock(float focalCX, float focalCY, float halfW, float halfH) {
        if (engine == null) return;

        // Full battlefield distribution including top and bottom edges
        float spawnX = focalCX + (random.nextFloat() - 0.5f) * (halfW * 2.0f);
        float spawnY = focalCY + (random.nextFloat() - 0.5f) * (halfH * 2.0f);

        float vx = 0f;
        float vy = 0f;
        switch (avalancheDirection) {
            case RIGHT_TO_LEFT: vx = -streamSpeed; vy = (random.nextFloat() - 0.5f) * 40f; break;
            case LEFT_TO_RIGHT: vx = streamSpeed; vy = (random.nextFloat() - 0.5f) * 40f; break;
            case BOTTOM_TO_TOP: vx = (random.nextFloat() - 0.5f) * 40f; vy = streamSpeed; break;
            case TOP_TO_BOTTOM:
            default: vx = (random.nextFloat() - 0.5f) * 40f; vy = -streamSpeed; break;
        }

        int type = rollAsteroidType(false);
        CombatEntityAPI ast = spawnSingleAsteroidEntity(type, spawnX, spawnY, vx, vy, 0);
        if (ast != null) {
            ast.setCustomData("DA_IS_BACKGROUND", Boolean.TRUE);
        }
    }



    /**

     * RULE 1: Spawns ambient background avalanche rocks in the CONSTANT battle direction.

     * Places spawn coordinates directly along the border rectangle (120 units offscreen).

     */


    // =========================================================================
    // COLOSSAL MEGALITH BOULDERS & GRAVITATIONAL ARC TRAJECTORY
    // =========================================================================

    public CombatEntityAPI spawnMegalithBoulder(float spawnX, float spawnY, float vx, float vy, float radius, float arcRateDeg) {
        if (engine == null) return null;

        String[] bigSprites = new String[] {
            "graphics/asteroids/asteroid_big00.png",
            "graphics/asteroids/asteroid_big01.png",
            "graphics/asteroids/asteroid_big02.png"
        };
        String spritePath = bigSprites[random.nextInt(bigSprites.length)];
        try {
            Global.getSettings().loadTexture(spritePath);
        } catch (Throwable t) {}

        CombatEntityAPI entity = null;
        try {
            entity = engine.spawnAsteroid(spritePath, spawnX, spawnY, vx, vy, false);
        } catch (Throwable t) {
            try {
                entity = engine.spawnAsteroid(3, spawnX, spawnY, vx, vy);
            } catch (Throwable t2) {}
        }
        if (entity == null) return null;

        float dispW = radius * 2.0f;
        float dispH = radius * 2.0f;
        if (entity instanceof CombatAsteroidAPI) {
            try {
                CombatAsteroidAPI ast = (CombatAsteroidAPI) entity;
                SpriteAPI s = ast.getSpriteAPI();
                if (s != null && isValidSprite(s)) {
                    float maxDim = Math.max(s.getTextureWidth(), s.getTextureHeight());
                    if (maxDim > 0f) {
                        // Enlarged sprite size (+40% scale) so the visible stone completely covers the collision circle
                        float scale = (radius * 3.1f) / maxDim;
                        dispW = s.getTextureWidth() * scale;
                        dispH = s.getTextureHeight() * scale;
                        s.setSize(dispW, dispH);
                    } else {
                        dispW = radius * 3.1f;
                        dispH = radius * 3.1f;
                        s.setSize(dispW, dispH);
                    }
                    s.setColor(new Color(225, 220, 215, 255));
                }
            } catch (Throwable t) {}
        }

        // Collision radius tightly follows the stone outline so ships visibly touch the rock with zero invisible air gap
        float megalithCollisionRadius = Math.max(40f, radius * 1.02f);
        entity.setCollisionRadius(megalithCollisionRadius);
        // Mass scales with radius squared: colossal planetary bedrock inertia!
        // Massive bedrock: ensures vanilla BasicShipAI.avoidAsteroidCollisions recognizes
        // megaliths as immovable giants exceeding even Battleships and Stations!
        float mass = Math.max(120000f, (radius * radius) * 2.5f + 25000f);
        entity.setMass(mass);

        // Durability: Colossal bedrock fortress (+500% base HP pool)!
        // Massive planetary bedrock: ~30,000 to ~52,000 HP! Immune to normal asteroid collisions.
        float baseHp = (9000f + radius * 120f) * 1.5f;
        entity.setHitpoints(baseHp);

        entity.setFacing(random.nextFloat() * 360f);
        entity.setAngularVelocity((random.nextFloat() - 0.5f) * 12.0f); // majestic slow tumble

        entity.setCustomData(TRACKED_KEY, Boolean.TRUE);
        entity.setCustomData(SPAWN_TIME_KEY, Float.valueOf(totalCombatTime));
        entity.setCustomData(BASE_VX_KEY, Float.valueOf(vx));
        entity.setCustomData(BASE_VY_KEY, Float.valueOf(vy));
        entity.setCustomData("DA_IS_MEGALITH", Boolean.TRUE);
        entity.setCustomData("DA_MEGALITH_SPEED", Float.valueOf((float) Math.sqrt(vx * vx + vy * vy)));
        entity.setCustomData("DA_ARC_RATE", Float.valueOf((float) Math.toRadians(arcRateDeg)));

        return entity;
    }

    public void spawnPreSeededMegalith(float halfW, float halfH) {
        if (engine == null) return;

        float radius = megalithMinRadius + random.nextFloat() * (megalithMaxRadius - megalithMinRadius);
        float speed = megalithMinSpeed + random.nextFloat() * (megalithMaxSpeed - megalithMinSpeed);

        // Full battlefield coverage from top to bottom edge
        float spawnX = (random.nextFloat() - 0.5f) * (halfW * 2.0f);
        float spawnY = (random.nextFloat() - 0.5f) * (halfH * 2.0f);

        float vx = 0f;
        float vy = 0f;
        float arcRate = 0f;

        switch (avalancheDirection) {
            case RIGHT_TO_LEFT:
                vx = -speed;
                vy = (random.nextFloat() - 0.5f) * 25f;
                if (megalithArcFlight) {
                    float sign = (spawnY > 0f) ? -1f : 1f;
                    arcRate = sign * (0.06f + random.nextFloat() * 0.08f);
                }
                break;
            case LEFT_TO_RIGHT:
                vx = speed;
                vy = (random.nextFloat() - 0.5f) * 25f;
                if (megalithArcFlight) {
                    float sign = (spawnY > 0f) ? -1f : 1f;
                    arcRate = sign * (0.06f + random.nextFloat() * 0.08f);
                }
                break;
            case BOTTOM_TO_TOP:
                vx = (random.nextFloat() - 0.5f) * 25f;
                vy = speed;
                if (megalithArcFlight) {
                    float sign = (spawnX > 0f) ? 1f : -1f;
                    arcRate = sign * (0.06f + random.nextFloat() * 0.08f);
                }
                break;
            case TOP_TO_BOTTOM:
            default:
                vx = (random.nextFloat() - 0.5f) * 25f;
                vy = -speed;
                if (megalithArcFlight) {
                    float sign = (spawnX > 0f) ? -1f : 1f;
                    arcRate = sign * (0.06f + random.nextFloat() * 0.08f);
                }
                break;
        }

        spawnMegalithBoulder(spawnX, spawnY, vx, vy, radius, arcRate);
    }

    public void spawnNaturalMegalith(float focalCX, float focalCY, float halfW, float halfH) {
        if (engine == null) return;

        float radius = megalithMinRadius + random.nextFloat() * (megalithMaxRadius - megalithMinRadius);
        float speed = megalithMinSpeed + random.nextFloat() * (megalithMaxSpeed - megalithMinSpeed);

        // Spawn safely outside visible map boundary so boulders do not pop in visibly
        float off = Math.max(radius + 450f, 650f);
        float spawnX = 0f;
        float spawnY = 0f;
        float vx = 0f;
        float vy = 0f;
        float arcRate = 0f;

        switch (avalancheDirection) {
            case RIGHT_TO_LEFT:
                spawnX = focalCX + halfW + off;
                spawnY = focalCY + (random.nextFloat() - 0.5f) * (halfH * 2.0f);
                vx = -speed;
                vy = (random.nextFloat() - 0.5f) * 25f;
                if (megalithArcFlight) {
                    float sign = (spawnY > focalCY) ? -1f : 1f;
                    arcRate = sign * (0.06f + random.nextFloat() * 0.08f);
                }
                break;
            case LEFT_TO_RIGHT:
                spawnX = focalCX - halfW - off;
                spawnY = focalCY + (random.nextFloat() - 0.5f) * (halfH * 2.0f);
                vx = speed;
                vy = (random.nextFloat() - 0.5f) * 25f;
                if (megalithArcFlight) {
                    float sign = (spawnY > focalCY) ? -1f : 1f;
                    arcRate = sign * (0.06f + random.nextFloat() * 0.08f);
                }
                break;
            case BOTTOM_TO_TOP:
                spawnX = focalCX + (random.nextFloat() - 0.5f) * (halfW * 2.0f);
                spawnY = focalCY - halfH - off;
                vx = (random.nextFloat() - 0.5f) * 25f;
                vy = speed;
                if (megalithArcFlight) {
                    float sign = (spawnX > focalCX) ? 1f : -1f;
                    arcRate = sign * (0.06f + random.nextFloat() * 0.08f);
                }
                break;
            case TOP_TO_BOTTOM:
            default:
                spawnX = focalCX + (random.nextFloat() - 0.5f) * (halfW * 2.0f);
                spawnY = focalCY + halfH + off;
                vx = (random.nextFloat() - 0.5f) * 25f;
                vy = -speed;
                if (megalithArcFlight) {
                    float sign = (spawnX > focalCX) ? -1f : 1f;
                    arcRate = sign * (0.06f + random.nextFloat() * 0.08f);
                }
                break;
        }

        spawnMegalithBoulder(spawnX, spawnY, vx, vy, radius, arcRate);
    }

    public void summonMegalithNearPlayer() {
        if (engine == null || !engine.isSimulation()) return;
        ShipAPI playerShip = getPlayerShipSafely();
        if (playerShip == null || playerShip.getLocation() == null) return;

        float playerFacingRad = (float) Math.toRadians(playerShip.getFacing());
        float dist = 800f + random.nextFloat() * 250f;
        float spawnAngle = playerFacingRad + (random.nextFloat() - 0.5f) * 0.4f;

        float spawnX = playerShip.getLocation().x + (float) Math.cos(spawnAngle) * dist;
        float spawnY = playerShip.getLocation().y + (float) Math.sin(spawnAngle) * dist;

        float moveAngle = spawnAngle + 3.14159f + (random.nextFloat() - 0.5f) * 0.6f;
        float speed = megalithMinSpeed + random.nextFloat() * (megalithMaxSpeed - megalithMinSpeed);
        float vx = (float) Math.cos(moveAngle) * speed;
        float vy = (float) Math.sin(moveAngle) * speed;

        float radius = megalithMinRadius + random.nextFloat() * (megalithMaxRadius - megalithMinRadius);
        float arcRate = (random.nextBoolean() ? 1f : -1f) * (0.08f + random.nextFloat() * 0.08f);

        CombatEntityAPI megalith = spawnMegalithBoulder(spawnX, spawnY, vx, vy, radius, arcRate);
        if (megalith != null && engine.getCombatUI() != null) {
            try {
                Global.getSoundPlayer().playUISound("ui_radar_detect_vessel", 0.9f, 0.7f);
            } catch (Throwable t) {}
            float arcDisplay = ((int)(Math.abs(arcRate) * 100)) / 100.0f;
            String msg = IS_RUSSIAN ?
                ("СИМУЛЯТОР: Создан колоссальный мегалит (Радиус: " + (int)radius + " ед, дуга: " + arcDisplay + " град/с)!") :
                ("SIMULATOR: Spawned colossal megalith (Radius: " + (int)radius + " u, arc: " + arcDisplay + " deg/s)!");
            engine.getCombatUI().addMessage(1, msg, new Color(240, 180, 80));
        }
    }

    private void spawnBackgroundAsteroid(float focalCX, float focalCY, float halfW, float halfH) {
        float spawnX = 0f;
        float spawnY = 0f;
        float vx = 0f;
        float vy = 0f;

        // Spawn safely outside visible map boundary
        float off = 450f;

        // Spans across the entire battle grid (from top to bottom borders) without artificial inward contraction
        switch (avalancheDirection) {
            case RIGHT_TO_LEFT:
                spawnX = focalCX + halfW + off;
                spawnY = focalCY + (random.nextFloat() - 0.5f) * (halfH * 2.05f);
                vx = -streamSpeed;
                vy = (random.nextFloat() - 0.5f) * 35.0f;
                break;
            case LEFT_TO_RIGHT:
                spawnX = focalCX - halfW - off;
                spawnY = focalCY + (random.nextFloat() - 0.5f) * (halfH * 2.05f);
                vx = streamSpeed;
                vy = (random.nextFloat() - 0.5f) * 35.0f;
                break;
            case BOTTOM_TO_TOP:
                spawnX = focalCX + (random.nextFloat() - 0.5f) * (halfW * 2.05f);
                spawnY = focalCY - halfH - off;
                vx = (random.nextFloat() - 0.5f) * 35.0f;
                vy = streamSpeed;
                break;
            case TOP_TO_BOTTOM:
            default:
                spawnX = focalCX + (random.nextFloat() - 0.5f) * (halfW * 2.05f);
                spawnY = focalCY + halfH + off;
                vx = (random.nextFloat() - 0.5f) * 35.0f;
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




    // =========================================================================
    // DYNAMIC ASTEROID DIRECTOR: LIVING CYCLES
    // =========================================================================

    private void advanceDirector(float amount, float focalCX, float focalCY, float halfW, float halfH) {
        if (!isInAsteroidField) return;

        directorStateTimer += amount;

        if (directorCycleMode == 1) {
            currentDirectorState = DirectorState.TORRENT;
            return;
        }

        switch (currentDirectorState) {
            case TORRENT:
                // Spawner runs at full continuous capacity ("будто не кончается")
                if (directorStateTimer >= directorStateDuration) {
                    directorStateTimer = 0f;
                    float roll = random.nextFloat();
                    if (roll < 0.45f) {
                        transitionToDirectorState(DirectorState.FORMATION, focalCX, focalCY, halfW, halfH);
                    } else if (roll < 0.85f) {
                        transitionToDirectorState(DirectorState.LULL, focalCX, focalCY, halfW, halfH);
                    } else {
                        transitionToDirectorState(DirectorState.BURST, focalCX, focalCY, halfW, halfH);
                    }
                }
                break;

            case BURST:
                // Fast brisk wave
                if (directorStateTimer >= directorStateDuration) {
                    directorStateTimer = 0f;
                    if (random.nextFloat() < 0.55f) {
                        transitionToDirectorState(DirectorState.LULL, focalCX, focalCY, halfW, halfH);
                    } else {
                        transitionToDirectorState(DirectorState.FORMATION, focalCX, focalCY, halfW, halfH);
                    }
                }
                break;

            case FORMATION:
                // Geometric formation wave is active
                if (activeStreams.isEmpty()) {
                    // Chaining chance ("волна, волна"): 42% chance to immediately spawn a second wave!
                    if (formationWavesInChain < 1 && random.nextFloat() < 0.42f) {
                        formationWavesInChain++;
                        triggerMultiAngleFormationWave(focalCX, focalCY, halfW, halfH);
                    } else if (directorStateTimer >= 3.5f) {
                        directorStateTimer = 0f;
                        if (directorCycleMode == 2) {
                            transitionToDirectorState(DirectorState.LULL, focalCX, focalCY, halfW, halfH);
                        } else {
                            // After wave(s): 60% lull / silence, 40% seamless return to torrent
                            if (random.nextFloat() < 0.60f) {
                                transitionToDirectorState(DirectorState.LULL, focalCX, focalCY, halfW, halfH);
                            } else {
                                transitionToDirectorState(DirectorState.TORRENT, focalCX, focalCY, halfW, halfH);
                            }
                        }
                    }
                }
                break;

            case LULL:
                // Spawner paused: old rocks clear out, pure silence / calm reigns (only megaliths drift by)
                if (directorStateTimer >= directorStateDuration) {
                    directorStateTimer = 0f;
                    if (directorCycleMode == 2) {
                        transitionToDirectorState(DirectorState.FORMATION, focalCX, focalCY, halfW, halfH);
                    } else {
                        // After lull: 65% long rolling torrent, 35% sudden formation wave
                        if (random.nextFloat() < 0.65f) {
                            transitionToDirectorState(DirectorState.TORRENT, focalCX, focalCY, halfW, halfH);
                        } else {
                            transitionToDirectorState(DirectorState.FORMATION, focalCX, focalCY, halfW, halfH);
                        }
                    }
                }
                break;
        }
    }

    private void transitionToDirectorState(DirectorState newState, float focalCX, float focalCY, float halfW, float halfH) {
        currentDirectorState = newState;
        directorStateTimer = 0f;

        switch (newState) {
            case TORRENT:
                directorStateDuration = 26.0f + random.nextFloat() * 16.0f; // 26 - 42 seconds
                break;
            case BURST:
                directorStateDuration = 12.0f + random.nextFloat() * 8.0f;  // 12 - 20 seconds
                break;
            case LULL:
                directorStateDuration = 6.0f + random.nextFloat() * 6.0f;   // 6 - 12 seconds of serene calm
                break;
            case FORMATION:
                directorStateDuration = 4.0f;
                formationWavesInChain = 0;
                triggerMultiAngleFormationWave(focalCX, focalCY, halfW, halfH);
                break;
        }
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



        float baseEntryAngle = random.nextFloat() * (float) (Math.PI * 2.0);



        // Roll for Easter Egg (Derelict Ship, Resource Cache/Probe/Pod/Fighter, or Debris/Explosive Tank)

        // High-value items preferentially drift across player's operational zone for exciting gameplay!

        if (random.nextFloat() < derelictSpawnChance) {

            Vector2f target = calculateEasterEggTarget(focalCX, focalCY);

            Vector2f pt = calculateEasterEggSpawnPoint(target, focalCX, focalCY, halfW, halfH, baseEntryAngle);

            float dx = target.x - pt.x;

            float dy = target.y - pt.y;

            float dLen = (float) Math.sqrt(dx * dx + dy * dy);

            float spd = 75.0f; // Derelict cruising speed (gentle 60-100 u/s drift)

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

        // Heavy rock budget for 300-capacity battles:
        // 1 stream  -> 90 rocks (massive continuous torrent / cloud)
        // 2 streams -> 60 rocks each (total 120)
        // 3 streams -> 45 rocks each (total 135)
        float capScale = Math.max(1.0f, battleTargetCount / 300.0f);
        int rocksPerStream = Math.round(90 * capScale);
        if (streamCount == 2) rocksPerStream = Math.round(60 * capScale);
        else if (streamCount == 3) rocksPerStream = Math.round(45 * capScale);

        // Boost rock count for split (Dual Rails) or wide spreading (Expanding Fan) formations by +20 rocks
        if (waveFigureType == FigureType.DUAL_RAILS || waveFigureType == FigureType.EXPANDING_FAN) {
            rocksPerStream += Math.round(20 * capScale);
        }

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

            float bInterval = 0.016f;
            if (waveFigureType == FigureType.COMET_SWARM) {
                bInterval = 0.008f;
            } else if (waveFigureType == FigureType.DUAL_RAILS || waveFigureType == FigureType.EXPANDING_FAN) {
                bInterval = 0.012f; // Dense rapid salvo for dual pillars and shotgun blast
            }
            stream.burstInterval = bInterval;

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



        CombatEntityAPI ast = spawnSingleAsteroidEntity(type, spawnX, spawnY, vx, vy, trailType);
        if (ast != null) {
            ast.setCustomData("DA_IS_STREAM", Boolean.TRUE);
        }

    }



    private CombatEntityAPI spawnSingleAsteroidEntity(int type, float x, float y, float vx, float vy, int trailType) {

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

        return ast;

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
        if (engine == null || !engine.isSimulation()) return;

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

                // Silenced: only floating text is shown above ship, matching normal campaign behavior
            }

        } catch (Throwable t) {

        }

    }



    private void notifyWaveIncoming(String text) {
        // Silenced: no extra clutter messages during combat
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

                    IS_RUSSIAN ? "ОПАСНОСТЬ: МЕТЕОРИТНЫЙ ШКВАЛ ПО КУРСУ!" : "COLLISION ALERT: METEOR BARRAGE ON COURSE!",

                    26f,

                    new Color(255, 40, 40),

                    ship,

                    0.35f,

                    4.0f

                );

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

        // Gentle contour avoidance for colossal megaliths and derelict hulks
        if (enableMicroDodge) {

            advanceColossalObstacleAvoidance(amount);

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

                boolean isMegalith = ast.getCustomData().containsKey("DA_IS_MEGALITH");
                float speedSq = vel.x * vel.x + vel.y * vel.y;

                if (speedSq >= minSpeedSq || isMegalith) {

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

                boolean isMegalith = threat.getCustomData().containsKey("DA_IS_MEGALITH");

                if (!isMegalith && relSpeedSq < (aiMinThreatVelocity * aiMinThreatVelocity)) continue;



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



        // Ships only use translational thrusters (strafe/throttle) to dodge, NEVER hijacking heading!
        state.activeTurnCommand = null;

        ShipwideAIFlags flags = ship.getAIFlags();
        if (flags != null) {
            // Do NOT set RUN_QUICKLY (which causes AI to abandon pursuit/attack orders and flee)
            flags.setFlag(AIFlags.HAS_INCOMING_DAMAGE, Math.min(0.35f, duration));

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

                    // Rock approaching outside front shield arc (flank/rear). Never turn the ship towards the stream!
                    state.activeTurnCommand = null;

                }

            }

        }

    }



    /**

     * Continuous formation corridor clearing for ALL figure waves (Lance, Swarm, Rails, Fan, Megaliths).

     * Runs continuously during wave transit so all ships proactively sidestep the kill zone.

     */


    private void advanceColossalObstacleAvoidance(float amount) {
        if (engine == null || engine.isPaused()) return;

        List<CombatEntityAPI> asteroids = engine.getAsteroids();
        if (asteroids == null || asteroids.isEmpty()) return;

        List<CombatEntityAPI> colossals = null;
        for (int i = 0; i < asteroids.size(); i++) {
            CombatEntityAPI ast = asteroids.get(i);
            if (ast == null || ast.isExpired() || !engine.isEntityInPlay(ast)) continue;
            boolean isMegalith = ast.getCustomData().containsKey("DA_IS_MEGALITH");
            boolean isDerelict = "DERELICT".equals(ast.getCustomData().get("EASTER_EGG_TYPE"));
            if (isMegalith || isDerelict) {
                if (colossals == null) colossals = new ArrayList<CombatEntityAPI>();
                colossals.add(ast);
            }
        }

        if (colossals == null || colossals.isEmpty()) return;

        List<ShipAPI> ships = engine.getShips();
        if (ships == null || ships.isEmpty()) return;

        for (int sIdx = 0; sIdx < ships.size(); sIdx++) {
            ShipAPI ship = ships.get(sIdx);
            if (ship == null || !ship.isAlive() || ship.isHulk()) continue;

            // Never hijack direct manual control of the player ship
            if (isShipManualPlayerControlled(ship)) continue;

            ShipAvoidanceState state = shipStates.get(ship);
            if (state != null && state.cooldown > 0.08f) continue;

            Vector2f shipLoc = ship.getLocation();
            Vector2f shipVel = ship.getVelocity();
            if (shipLoc == null || shipVel == null) continue;

            float shipRadius = ship.getCollisionRadius();

            CombatEntityAPI closestColossal = null;
            float bestSteerX = 0f;
            float bestSteerY = 0f;
            float lowestTimeToImpact = Float.MAX_VALUE;
            boolean isProximityEscape = false;

            for (int cIdx = 0; cIdx < colossals.size(); cIdx++) {
                CombatEntityAPI obs = colossals.get(cIdx);
                Vector2f obsLoc = obs.getLocation();
                if (obsLoc == null) continue;

                float obsRadius = obs.getCollisionRadius();
                float toObsX = obsLoc.x - shipLoc.x;
                float toObsY = obsLoc.y - shipLoc.y;
                float distSq = toObsX * toObsX + toObsY * toObsY;

                // Generous safety contour buffer: ship radius + obstacle radius + 280 units
                float safeDist = shipRadius + obsRadius + 280f;
                float safeDistSq = safeDist * safeDist;

                float dist = (float) Math.sqrt(distSq);

                // Proximity safety: ship is within safe contour or dangerously close
                if (distSq < safeDistSq) {
                    float awayX = (dist > 0.001f) ? -toObsX / dist : 1f;
                    float awayY = (dist > 0.001f) ? -toObsY / dist : 0f;

                    closestColossal = obs;
                    bestSteerX = awayX;
                    bestSteerY = awayY;
                    lowestTimeToImpact = 0.4f;
                    isProximityEscape = true;
                    break;
                }

                // Lookahead zone: up to 2200 units past safe buffer
                float maxScanDist = safeDist + 2200f;
                if (distSq > maxScanDist * maxScanDist) continue;

                Vector2f obsVel = obs.getVelocity();
                float ovX = (obsVel != null) ? obsVel.x : 0f;
                float ovY = (obsVel != null) ? obsVel.y : 0f;

                float rvx = ovX - shipVel.x;
                float rvy = ovY - shipVel.y;
                float relSpdSq = rvx * rvx + rvy * rvy;
                if (relSpdSq < 64f) continue; // Relative speed < 8 u/s, effectively static

                float dot = toObsX * rvx + toObsY * rvy;
                if (dot >= 0f) continue; // Diverging / moving away

                float tCpa = -dot / relSpdSq;
                if (tCpa <= 0.02f || tCpa > 6.0f) continue; // Up to 6.0s lookahead window for capital/cruiser safety

                float cpaX = toObsX + rvx * tCpa;
                float cpaY = toObsY + rvy * tCpa;
                float cpaDistSq = cpaX * cpaX + cpaY * cpaY;

                if (cpaDistSq < safeDistSq) {
                    if (tCpa < lowestTimeToImpact) {
                        lowestTimeToImpact = tCpa;
                        closestColossal = obs;

                        float rSpd = (float) Math.sqrt(relSpdSq);
                        float uX = rvx / rSpd;
                        float uY = rvy / rSpd;
                        float perpX = -uY;
                        float perpY = uX;

                        // Ship position relative to obstacle at CPA is (-cpaX, -cpaY)
                        // Project onto perpendicular to determine which side ship is on
                        float lateralOffset = (-cpaX) * perpX + (-cpaY) * perpY;
                        float steerSign = (lateralOffset >= 0f) ? 1.0f : -1.0f;
                        bestSteerX = perpX * steerSign;
                        bestSteerY = perpY * steerSign;
                    }
                }
            }

            if (closestColossal != null) {
                // Burn Drive & Mobility System Interceptor:
                // If ship is using a system (like Burn Drive) that accelerates it into the megalith, cancel it!
                if (ship.getSystem() != null && ship.getSystem().isActive()) {
                    float facingRad = (float) Math.toRadians(ship.getFacing());
                    float fwdX = (float) Math.cos(facingRad);
                    float fwdY = (float) Math.sin(facingRad);
                    Vector2f obsLoc = closestColossal.getLocation();
                    if (obsLoc != null) {
                        float toObsX = obsLoc.x - shipLoc.x;
                        float toObsY = obsLoc.y - shipLoc.y;
                        float dotNose = toObsX * fwdX + toObsY * fwdY;
                        if (dotNose > 0f) { // Nose pointed towards obstacle
                            ship.giveCommand(ShipCommand.USE_SYSTEM, null, 0); // Disengage Burn Drive / system!
                            ship.giveCommand(ShipCommand.DECELERATE, null, 0);
                        }
                    }
                }

                // AI Flags: Prevent pursuit charges through the megalith and back-off hazards
                ShipwideAIFlags flags = ship.getAIFlags();
                if (flags != null) {
                    flags.setFlag(AIFlags.DO_NOT_PURSUE, 1.2f);
                    flags.setFlag(AIFlags.WANTED_TO_SLOW_DOWN, 0.8f);
                    flags.setFlag(AIFlags.DO_NOT_BACK_OFF, 0.5f);
                }

                float duration = isProximityEscape ? 0.50f : Math.max(0.40f, Math.min(0.85f, lowestTimeToImpact * 0.40f));
                applyColossalGlidingManeuver(ship, bestSteerX, bestSteerY, duration, lowestTimeToImpact);

                // Omni and front shield reactions when closing with colossal rocks
                if (enableShieldReaction && ship.getShield() != null) {
                    Vector2f obsLoc = closestColossal.getLocation();
                    if (obsLoc != null) {
                        float angleToObs = computeAngle(shipLoc.x, shipLoc.y, obsLoc.x, obsLoc.y);
                        if (state == null) {
                            state = new ShipAvoidanceState();
                            shipStates.put(ship, state);
                        }
                        state.targetAngle = angleToObs;

                        if (ship.getShield().getType() == ShieldType.OMNI) {
                            if (ship.getShield().isOff() && ship.getFluxTracker() != null && ship.getFluxTracker().getFluxLevel() < 0.85f) {
                                ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null, 0);
                            }
                        } else if (ship.getShield().getType() == ShieldType.FRONT) {
                            float diff = Math.abs(getShortestRotation(ship.getFacing(), angleToObs));
                            if (diff <= ship.getShield().getArc() * 0.5f + 25.0f) {
                                if (ship.getShield().isOff() && ship.getFluxTracker() != null && ship.getFluxTracker().getFluxLevel() < 0.85f) {
                                    ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null, 0);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void applyColossalGlidingManeuver(ShipAPI ship, float escapeWorldX, float escapeWorldY, float duration, float timeToImpact) {
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

        // Lateral thruster burn to gently glide around contour
        if (Math.abs(dotLeft) >= 0.08f) {
            ShipCommand strafeCmd = (dotLeft > 0f) ? ShipCommand.STRAFE_LEFT : ShipCommand.STRAFE_RIGHT;
            state.activeCommand = strafeCmd;
            ship.giveCommand(strafeCmd, null, 0);
        } else {
            state.activeCommand = null;
        }

        // Longitudinal throttle: decelerate if heading directly towards obstacle to give room to clear
        if (dotFwd < -0.15f) {
            state.activeThrustCommand = ShipCommand.DECELERATE;
            ship.giveCommand(ShipCommand.DECELERATE, null, 0);
        } else if (dotFwd > 0.35f) {
            state.activeThrustCommand = ShipCommand.ACCELERATE;
            ship.giveCommand(ShipCommand.ACCELERATE, null, 0);
        } else {
            state.activeThrustCommand = null;
        }

        // Polite, smooth avoidance steering:
        // Cruisers & Capital ships have very slow lateral strafe; a gentle turn aligns their main engines with escape route
        boolean isHeavyShip = (ship.getHullSize() == ShipAPI.HullSize.CRUISER || ship.getHullSize() == ShipAPI.HullSize.CAPITAL_SHIP);
        if (isHeavyShip && timeToImpact < 5.0f && dotFwd < -0.12f && Math.abs(dotLeft) > 0.15f) {
            ShipCommand turnCmd = (dotLeft > 0f) ? ShipCommand.TURN_LEFT : ShipCommand.TURN_RIGHT;
            state.activeTurnCommand = turnCmd;
            ship.giveCommand(turnCmd, null, 0);
        } else {
            state.activeTurnCommand = null;
        }

        // Polite AI coordination: cooperate with vanilla Starsector fleet AI
        ShipwideAIFlags flags = ship.getAIFlags();
        if (flags != null) {
            flags.setFlag(AIFlags.DO_NOT_BACK_OFF, 0.45f); // Do not reverse blindly into hazards behind
            flags.setFlag(AIFlags.HAS_INCOMING_DAMAGE, 0.35f);
            if (timeToImpact < 3.5f) {
                flags.setFlag(AIFlags.KEEP_SHIELDS_ON, Math.min(0.6f, duration));
            }
        }
    }

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

            if (target instanceof ShipAPI) {

                ShipAPI targetShip = (ShipAPI) target;
                CombatEntityAPI source = (paramObject instanceof CombatEntityAPI) ? (CombatEntityAPI) paramObject : null;
                boolean isAsteroidOrCollision = (source != null && (source.getCollisionClass() == CollisionClass.ASTEROID || source.getCustomData().containsKey(TRACKED_KEY)))
                    || (paramObject == null && damage.getType() == com.fs.starfarer.api.combat.DamageType.KINETIC);

                if (isAsteroidOrCollision) {

                    if (source != null && (source.getCustomData().containsKey("DA_IS_MEGALITH") || source.getCustomData().containsKey("IS_EASTER_EGG"))) {
                        // Ship collided with megalith or derelict hulk: untether from scripted rails so it becomes a free physical body!
                        source.getCustomData().put("DA_NO_MAINTAIN_VELOCITY", Boolean.TRUE);
                    }

                    // Deployment Safety & Grace Period (Ship Deflection Dome):
                    // While the deflection field is active (strictly 7 seconds after spawn on the battlefield),
                    // provide 98% asteroid collision damage protection (0.02f multiplier, matching station dome defense)!
                    boolean isStation = targetShip.isStation() || targetShip.isStationModule() || targetShip.getParentStation() != null;
                    if (!isStation && instance != null && instance.enableDeploymentSafety) {
                        Float spawnTime = instance.shipSpawnTimes.get(targetShip);
                        if (spawnTime == null) {
                            Object sData = targetShip.getCustomData().get("DA_SPAWN_TIME");
                            if (sData instanceof Float) spawnTime = (Float) sData;
                        }
                        float timeSinceSpawn = (spawnTime != null) ? (instance.totalCombatTime - spawnTime.floatValue()) : instance.totalCombatTime;

                        if (timeSinceSpawn >= 0f && timeSinceSpawn < instance.deploymentSafetyDuration) {
                            damage.getModifier().modifyMult("da_deployment_safety", instance.stationAsteroidDamageMult);
                            if (source != null && source.getCustomData().containsKey("DA_IS_MEGALITH")) {
                                source.setCustomData("DA_NO_MAINTAIN_VELOCITY", Boolean.TRUE);
                            }
                            if (instance.engine != null && point != null) {
                                instance.engine.addHitParticle(point, new Vector2f(0f, 0f), 80f, 0.6f, 0.4f, new Color(100, 220, 255, 220));
                                instance.engine.addSmoothParticle(point, new Vector2f(0f, 0f), 90f, 0.6f, 0.45f, new Color(80, 180, 255, 150));
                            }
                            return "da_deployment_safety";
                        }
                    }

                    if (source == null || !source.getCustomData().containsKey("IS_EASTER_EGG")) {

                        // Anti-Cheese: Station protection from asteroid collisions
                        if (isStation && instance != null && instance.enableStationAsteroidProtection) {
                            if (instance.isStationDomeOffline(targetShip)) {
                                // Overloaded section: dome collapsed, take substantial asteroid collision damage!
                                damage.getModifier().modifyMult("da_station_asteroid_defense", 0.65f);
                                return "da_station_asteroid_defense";
                            }
                            damage.getModifier().modifyMult("da_station_asteroid_defense", instance.stationAsteroidDamageMult);
                            if (source != null && source.getCustomData().containsKey("DA_IS_MEGALITH")) {
                                source.setHitpoints(0f);
                                if (instance.engine != null && point != null) {
                                    instance.engine.addSmoothParticle(point, new Vector2f(0f, 0f), 220f, 1.2f, 1.6f, new Color(240, 200, 140, 240));
                                    instance.engine.addHitParticle(point, new Vector2f(0f, 0f), 160f, 0.8f, 0.6f, new Color(255, 230, 180, 255));
                                }
                            }
                            return "da_station_asteroid_defense";
                        }

                        // Soft-Scrape Cushioning for Colossal Megaliths:
                        // Prevent ships from instantly dying when ramming or scraping against giant Megaliths!
                        boolean contactingMegalith = false;
                        if (source != null && source.getCustomData().containsKey("DA_IS_MEGALITH")) {
                            contactingMegalith = true;
                            source.getCustomData().put("DA_NO_MAINTAIN_VELOCITY", Boolean.TRUE);
                        } else if (source == null && instance != null && instance.engine != null) {
                            List<CombatEntityAPI> asts = instance.engine.getAsteroids();
                            if (asts != null) {
                                Vector2f sLoc = targetShip.getLocation();
                                float sRad = targetShip.getCollisionRadius();
                                for (int i = 0; i < asts.size(); i++) {
                                    CombatEntityAPI a = asts.get(i);
                                    if (a != null && a.getCustomData().containsKey("DA_IS_MEGALITH")) {
                                        float d = Misc.getDistance(sLoc, a.getLocation());
                                        if (d < sRad + a.getCollisionRadius() + 60f) {
                                            contactingMegalith = true;
                                            a.getCustomData().put("DA_NO_MAINTAIN_VELOCITY", Boolean.TRUE);
                                            break;
                                        }
                                    }
                                }
                            }
                        }

                        // Universal Fair Megalith Collision Scaling (Equal for ALL ships - Player, Allies, and Enemies):
                        // Clean, honest 30% reduction of kinetic collision damage from Megaliths (0.70x multiplier).
                        // Collision damage remains heavy, dangerous and physical, just giving 30% more survivability.
                        if (contactingMegalith) {
                            damage.getModifier().modifyMult("da_megalith_cushion", 0.70f);

                            if (instance != null && instance.engine != null && point != null) {
                                instance.engine.addHitParticle(point, new Vector2f(0f, 0f), 80f, 0.5f, 0.35f, new Color(130, 220, 255, 220));
                            }
                            return "da_megalith_cushion";
                        }

                        float mult = getAsteroidCollisionDamageMult();

                        if (Math.abs(mult - 1.0f) > 0.01f) {

                            damage.getModifier().modifyMult("da_asteroid_collision", mult);

                            return "da_asteroid_collision";

                        }

                    } else {

                        // Easter egg collided with a ship! Disable velocity correction permanently
                        source.getCustomData().put("DA_NO_MAINTAIN_VELOCITY", Boolean.TRUE);

                        String eggType = (String) source.getCustomData().get("EASTER_EGG_TYPE");
                        if ("EXPLOSIVE_TANK".equals(eggType) && instance != null) {

                            instance.triggerTankExplosion(source, (ShipAPI) target);

                        } else if ("DERELICT".equals(eggType)) {

                            // Derelict hulk soft-scrape protection: 90% collision damage reduction so ships don't wreck themselves!
                            damage.getModifier().modifyMult("da_derelict_scrape", 0.10f);
                            return "da_derelict_scrape";

                        } else if (instance != null) {

                            damage.getModifier().modifyMult("da_salvage_pickup", 0.0f);

                            instance.triggerSalvagePickup(source, (ShipAPI) target);

                            return "da_salvage_pickup";

                        }

                    }

                }

            }



            // 1.5. Megalith Asteroid Immunity & Interactive Physical Untethering
            if (target.getCustomData().containsKey("DA_IS_MEGALITH")) {
                if (paramObject instanceof CombatEntityAPI) {
                    CombatEntityAPI source = (CombatEntityAPI) paramObject;
                    if (source.getCollisionClass() == CollisionClass.ASTEROID || source.getCustomData().containsKey(TRACKED_KEY)) {
                        damage.getModifier().modifyMult("da_megalith_asteroid_immunity", 0.0f);
                        return "da_megalith_asteroid_immunity";
                    }
                }
                // Untether megalith from scripted velocity when pushed or shot by ships/weapons!
                if (paramObject instanceof ShipAPI || paramObject instanceof DamagingProjectileAPI || 
                    paramObject instanceof BeamAPI || paramObject instanceof MissileAPI) {
                    target.getCustomData().put("DA_NO_MAINTAIN_VELOCITY", Boolean.TRUE);
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

                if (!target.getCustomData().containsKey("IS_EASTER_EGG") && !target.getCustomData().containsKey("DA_IS_MEGALITH") && isPDSource(paramObject)) {

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
        if (state != null) state.activeTurnCommand = null;
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

                CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

                Random rnd = new Random();

                for (DerelictSalvageRecord rec : records) {

                    if (rec == null || rec.variantId == null) continue;

                    if (rec.rescueChance <= 0f) continue;

                    if (rnd.nextFloat() > rec.rescueChance) continue;



                    FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, rec.variantId);

                    member.setOwner(1);

                    // Ensure always_recoverable tag so Starsector recovery screen NEVER drops this ship!
                    member.getVariant().addTag("always_recoverable");
                    member.getVariant().addTag(Tags.VARIANT_ALWAYS_RECOVERABLE);
                    if (member.getHullSpec() != null) {
                        member.getHullSpec().addTag("always_recoverable");
                        member.getHullSpec().addTag(Tags.VARIANT_ALWAYS_RECOVERABLE);
                    }

                    FleetEncounterContext.prepareShipForRecovery(member, false, false, true, rec.minDmodProb, rec.maxDmodProb, rnd);

                    member.getVariant().addTag("always_recoverable");
                    member.getVariant().addTag(Tags.VARIANT_ALWAYS_RECOVERABLE);

                    if (rec.destroyed) {
                        try {
                            com.fs.starfarer.api.impl.campaign.DModManager.addDMods(member, true, 2 + rnd.nextInt(3), rnd);
                        } catch (Throwable t2) {}
                    }

                    boolean addedToScreen = false;

                    if (result != null && result.getLoserResult() != null) {

                        result.getLoserResult().getDisabled().add(member);

                        addedToScreen = true;

                    }

                    // If not added to enemy loser disabled list (or if player disengaged / non-standard battle),
                    // guarantee the salvaged ship is transferred directly to player fleet!
                    if (!addedToScreen && playerFleet != null) {

                        member.setOwner(0);

                        member.getRepairTracker().setCR(0.40f);

                        playerFleet.getFleetData().addFleetMember(member);

                        String label = (rec.hullName != null) ? rec.hullName : member.getHullSpec().getHullName();

                        Global.getSector().getCampaignUI().addMessage(

                            (IS_RUSSIAN ? ("Остов \"" + label + "\" успешно эвакуирован и присоединён к флоту!") :

                                          ("Derelict \"" + label + "\" recovered and added to your fleet!")),

                            new Color(60, 255, 140)

                        );

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
                0.25f, 480f, 220f, 3200f, 1500f,
                CollisionClass.HITS_SHIPS_AND_ASTEROIDS, CollisionClass.HITS_SHIPS_AND_ASTEROIDS,
                4f, 8f, 1.0f, 80,
                new Color(255, 70, 20, 255), new Color(255, 190, 60, 230)
            );

            spec.setDamageType(com.fs.starfarer.api.combat.DamageType.HIGH_EXPLOSIVE);
            spec.setMaxEMPDamage(1800f);
            spec.setMinEMPDamage(800f);
            spec.setSoundSetId("explosion_ship");

            // Pass null as source ship so hitShip is NOT immune to its own explosion!
            engine.spawnDamagingExplosion(spec, null, loc);

            // Direct guaranteed blast against the colliding ship (if one collided directly)
            if (hitShip != null && hitShip.isAlive()) {
                engine.applyDamage(hitShip, loc, 2500f, com.fs.starfarer.api.combat.DamageType.HIGH_EXPLOSIVE, 1200f, false, false, null);
            }

            Global.getSoundPlayer().playSound("explosion_ship", 1.0f, 1.0f, loc, new Vector2f());



            if (engine.getCombatUI() != null && !isTitleScreen) {

                String shipLabel = (hitShip != null) ? hitShip.getName() : null;

                String msg = (shipLabel != null) ?
                    (IS_RUSSIAN ? ("ДЕТОНАЦИЯ: Взрывоопасный бак с горючим разнёс " + shipLabel + "!") : ("DETONATION: Explosive volatile fuel tank detonated against " + shipLabel + "!")) :
                    (IS_RUSSIAN ? "ВЗРЫВ: Взрывоопасный бак сдетонировал мощным фугасным зарядом!" : "EXPLOSION: Volatile fuel tank detonated in a massive high-explosive blast!");

                engine.getCombatUI().addMessage(1, msg, new Color(255, 70, 30));

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



        private static boolean isEntityWithinShieldArc(ShipAPI ship, Vector2f targetLoc) {
        if (ship == null || targetLoc == null) return false;
        if (ship.isPhased()) return true; // Phase cloak envelops entire hull (360 degrees)

        ShieldAPI shield = ship.getShield();
        if (shield == null || !shield.isOn() || shield.isOff()) return false;

        float arc = shield.getArc();
        if (arc >= 359f) return true; // Omnidirectional 360 degree shield

        Vector2f shieldCenter = shield.getLocation();
        if (shieldCenter == null) shieldCenter = ship.getLocation();
        if (shieldCenter == null) return false;

        float toTargetX = targetLoc.x - shieldCenter.x;
        float toTargetY = targetLoc.y - shieldCenter.y;
        float angleToTarget = (float) Math.toDegrees(Math.atan2(toTargetY, toTargetX));
        if (angleToTarget < 0f) angleToTarget += 360f;

        float shieldFacing = shield.getFacing();
        float diff = Math.abs(getShortestRotation(shieldFacing, angleToTarget));

        // Half-arc plus fringe margin (10 degrees) for smooth field edge
        return diff <= (arc * 0.5f + 10.0f);
    }

    private void applyShieldMagnet(CombatEntityAPI entity, List<ShipAPI> ships, float amount) {
        if (easterEggMagnetStrength <= 0f || amount <= 0f || entity == null) return;
        if (!Boolean.TRUE.equals(entity.getCustomData().get("IS_EASTER_EGG"))) return;

        Vector2f eggLoc = entity.getLocation();
        Vector2f eggVelocity = entity.getVelocity();
        if (eggLoc == null || eggVelocity == null) return;

        ShipAPI closestShieldedShip = null;
        float closestDistanceSq = Float.MAX_VALUE;
        float closestShipRange = 1350f;

        for (int i = 0; i < ships.size(); i++) {
            ShipAPI ship = ships.get(i);
            if (ship == null || !ship.isAlive() || ship.isHulk() || !isPlayerSideShip(ship)) continue;

            boolean shieldActive = (ship.getShield() != null && ship.getShield().isOn() && !ship.getShield().isOff());
            boolean phaseActive = ship.isPhased();
            if (!shieldActive && !phaseActive) continue;

            Vector2f shipLoc = ship.getLocation();
            if (shipLoc == null) continue;

            // Directional Shield Vector Check:
            // If shield has directional arc, only magnetize if target is within active shield arc cone!
            if (!isEntityWithinShieldArc(ship, eggLoc)) continue;

            float dx = shipLoc.x - eggLoc.x;
            float dy = shipLoc.y - eggLoc.y;
            float distanceSq = dx * dx + dy * dy;
            float range = Math.max(1350f, ship.getCollisionRadius() * 4.5f + 550f);
            if (distanceSq <= range * range && distanceSq < closestDistanceSq) {
                closestShieldedShip = ship;
                closestDistanceSq = distanceSq;
                closestShipRange = range;
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

        // Safe hover & mooring boundary: keeps the object hovering gracefully outside the shield bubble
        float shipEdge = (closestShieldedShip.getShield() != null && closestShieldedShip.getShield().isOn())
            ? closestShieldedShip.getShield().getRadius()
            : closestShieldedShip.getCollisionRadius();
        float targetRadius = entity.getCollisionRadius();
        float safeHoverDistance = shipEdge + targetRadius + 45f;

        // Newtonian mass contrast tensor:
        float shipMass = Math.max(80f, closestShieldedShip.getMass());
        float targetMass = Math.max(50f, entity.getMass());

        float massRatioTarget;
        float massRatioShip;

        if (targetMass >= shipMass) {
            float ratio = shipMass / targetMass; // <= 1.0 (e.g. 200 / 2240 = 0.089)
            massRatioTarget = ratio * ratio * 0.35f; // Strongly reduced for heavy targets (< 0.3%)
            massRatioShip = Math.min(1.0f, 1.0f - massRatioTarget);
        } else {
            float ratio = targetMass / shipMass; // < 1.0 (e.g. 150 / 2000 = 0.075)
            massRatioShip = ratio * ratio * 0.20f; // Barely any effect on big ships (< 0.1%)
            massRatioTarget = Math.min(1.0f, 1.0f - massRatioShip);
        }

        // Relative velocity: v_rel = v_egg - v_ship
        float sVx = (shipVel != null) ? shipVel.x : 0f;
        float sVy = (shipVel != null) ? shipVel.y : 0f;
        float relVx = eggVelocity.x - sVx;
        float relVy = eggVelocity.y - sVy;

        // Radial closing speed along n: positive = closing in towards each other, negative = separating
        float vRadial = relVx * nx + relVy * ny;

        // =========================================================================
        // 1. NON-LINEAR DISTANCE CURVE: [Edge = 10%] -> [10% Path = 20%] -> [Target = 100%]
        // Formula: F(p) = 0.10 + 0.90 * p^k, where k = log10(9) = 0.9542425f
        // =========================================================================
        float moorDistance = safeHoverDistance + 20f;
        float effectiveMaxRange = Math.max(moorDistance + 80f, closestShipRange);

        // Size contrast factor: larger ship pulling smaller object allows higher closing speed
        float sizeContrast = Math.max(0.70f, Math.min(2.50f, shipEdge / Math.max(25f, targetRadius)));
        float maxClosingSpeed = 300.0f + 60.0f * (sizeContrast - 1.0f); // 300 - 390 u/s

        // Base max force from LunaLib/config (default 1400 u/s2)
        float maxConfigForce = Math.max(400.0f, easterEggMagnetStrength);

        if (distance > moorDistance) {
            // =====================================================================
            // APPROACH PHASE (Outside mooring perimeter):
            // Smooth non-linear tractor beam pull with velocity governor
            // =====================================================================
            float progress = (effectiveMaxRange - distance) / (effectiveMaxRange - moorDistance);
            if (progress < 0f) progress = 0f;
            else if (progress > 1f) progress = 1f;

            float forceNorm = 0.10f + 0.90f * (float) Math.pow(progress, 0.9542425);
            if (forceNorm > 1.0f) forceNorm = 1.0f;

            float currentTractiveForce = maxConfigForce * forceNorm;
            float maxAccelLimit = Math.max(300.0f, currentTractiveForce * Math.max(0.85f, sizeContrast * 0.65f));

            float desiredClosingSpeed = Math.min(maxClosingSpeed, (distance - moorDistance) * 0.70f);
            float speedDeficit = desiredClosingSpeed - vRadial;

            if (speedDeficit > 0f) {
                // Controlled inward closing step
                float pullStep = Math.min(speedDeficit, maxAccelLimit * amount);
                eggVelocity.x += nx * (pullStep * massRatioTarget);
                eggVelocity.y += ny * (pullStep * massRatioTarget);
                if (shipVel != null) {
                    shipVel.x -= nx * (pullStep * massRatioShip);
                    shipVel.y -= ny * (pullStep * massRatioShip);
                }
            } else if (vRadial > desiredClosingSpeed + 15.0f) {
                // Approach braking if closing in faster than desired
                float brakeStep = Math.min(vRadial - desiredClosingSpeed, maxAccelLimit * 0.65f * amount);
                eggVelocity.x -= nx * (brakeStep * massRatioTarget);
                eggVelocity.y -= ny * (brakeStep * massRatioTarget);
                if (shipVel != null) {
                    shipVel.x += nx * (brakeStep * massRatioShip);
                    shipVel.y += ny * (brakeStep * massRatioShip);
                }
            }

            // Lateral (perpendicular) relative velocity damping
            float latVx = relVx - (vRadial * nx);
            float latVy = relVy - (vRadial * ny);
            float latDamp = Math.min(0.90f, 4.0f * amount);

            eggVelocity.x -= latVx * (latDamp * massRatioTarget);
            eggVelocity.y -= latVy * (latDamp * massRatioTarget);
            if (shipVel != null) {
                shipVel.x += latVx * (latDamp * massRatioShip);
                shipVel.y += latVy * (latDamp * massRatioShip);
            }
        } else {
            // =====================================================================
            // MOORING & DAMPING PHASE (At or inside shield mooring perimeter):
            // Zero tractor pull! Pure velocity dissipation and speed synchronization.
            // Eliminates perpetual motion and resonance oscillations completely.
            // =====================================================================

            // 1. Synchronize relative velocity to match ship flight path
            float syncDamp = Math.min(1.0f, 6.0f * amount);
            eggVelocity.x -= relVx * (syncDamp * massRatioTarget);
            eggVelocity.y -= relVy * (syncDamp * massRatioTarget);

            // 2. Extinguish any remaining inward closing speed immediately
            if (vRadial > 0f) {
                float radialDamp = Math.min(vRadial, 8.0f * vRadial * amount);
                eggVelocity.x -= nx * (radialDamp * massRatioTarget);
                eggVelocity.y -= ny * (radialDamp * massRatioTarget);
                // Ship closing speed is also absorbed
                if (shipVel != null && massRatioShip > 0.5f) {
                    shipVel.x += nx * (radialDamp * massRatioShip * 0.5f);
                    shipVel.y += ny * (radialDamp * massRatioShip * 0.5f);
                }
            }

            // 3. Gentle non-recoil boundary buffer (Ship velocity is NEVER boosted forward)
            if (distance < safeHoverDistance) {
                float depthRatio = (safeHoverDistance - distance) / Math.max(1f, safeHoverDistance);
                float softDrift = Math.min(1.0f, depthRatio) * 60.0f * amount;
                eggVelocity.x -= nx * (softDrift * massRatioTarget);
                eggVelocity.y -= ny * (softDrift * massRatioTarget);
            }

            // 4. Perpendicular lateral stabilization
            float latVx = relVx - (vRadial * nx);
            float latVy = relVy - (vRadial * ny);
            float latDamp = Math.min(0.95f, 6.0f * amount);
            eggVelocity.x -= latVx * (latDamp * massRatioTarget);
            eggVelocity.y -= latVy * (latDamp * massRatioTarget);
        }

        // Visual tractor beam particles along active shield vector
        if (engine != null && random.nextFloat() < 0.38f) {
            Vector2f pVel = new Vector2f(nx * 160f, ny * 160f);
            engine.addSmoothParticle(
                eggLoc,
                pVel,
                14f + random.nextFloat() * 14f,
                0.85f,
                0.30f,
                new Color(70, 225, 255, 195)
            );
        }

        // Visual feedback when a lighter ship is being reeled towards a heavy derelict
        if (engine != null && massRatioShip > 0.35f && random.nextFloat() < 0.35f) {
            Vector2f sLoc = closestShieldedShip.getLocation();
            if (sLoc != null) {
                Vector2f sPvel = new Vector2f(-nx * 160f, -ny * 160f);
                engine.addSmoothParticle(
                    sLoc,
                    sPvel,
                    15f + random.nextFloat() * 12f,
                    0.85f,
                    0.30f,
                    new Color(90, 215, 255, 185)
                );
            }
        }
    }

    private static boolean isPlayerSideShip(ShipAPI ship) {

        if (ship == null || !ship.isAlive()) return false;

        if (instance != null && instance.engine != null && instance.engine.getPlayerShip() != null) {

            if (ship == instance.engine.getPlayerShip()) return true;

            if (ship.getOwner() == instance.engine.getPlayerShip().getOwner()) return true;

        }

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



        float focalCX = 0f;
        float focalCY = 0f;
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

        boolean outside = (loc.x < focalCX - halfW ||
                           loc.x > focalCX + halfW ||
                           loc.y < focalCY - halfH ||
                           loc.y > focalCY + halfH);

        boolean hasEntered = entity.getCustomData().containsKey("DA_ENTERED_MAP");
        boolean shouldDespawn = false;

        if (!hasEntered) {
            if (!outside) {
                entity.getCustomData().put("DA_ENTERED_MAP", Boolean.TRUE);
                entity.getCustomData().remove("DA_OUT_OF_BOUNDS_TIMER");
            } else {
                Vector2f vel = entity.getVelocity();
                float dx = focalCX - loc.x;
                float dy = focalCY - loc.y;
                float dot = (vel != null) ? (vel.x * dx + vel.y * dy) : 0f;
                if (dot <= 0f) {
                    Object timerObj = entity.getCustomData().get("DA_OUT_OF_BOUNDS_TIMER");
                    float timer = (timerObj instanceof Float) ? ((Float) timerObj).floatValue() + amount : amount;
                    entity.getCustomData().put("DA_OUT_OF_BOUNDS_TIMER", timer);
                    if (timer >= 5.0f) {
                        shouldDespawn = true;
                    }
                } else {
                    entity.getCustomData().remove("DA_OUT_OF_BOUNDS_TIMER");
                }
            }
        } else {
            if (outside) {
                Object timerObj = entity.getCustomData().get("DA_OUT_OF_BOUNDS_TIMER");
                float timer = (timerObj instanceof Float) ? ((Float) timerObj).floatValue() + amount : amount;
                entity.getCustomData().put("DA_OUT_OF_BOUNDS_TIMER", timer);
                if (timer >= 5.0f) {
                    shouldDespawn = true;
                }
            } else {
                entity.getCustomData().remove("DA_OUT_OF_BOUNDS_TIMER");
            }
        }

        if (shouldDespawn) {

            Boolean wasRescued = (Boolean) entity.getCustomData().get("DERELICT_RESCUED");

            String vId = (String) entity.getCustomData().get("DERELICT_VARIANT_ID");

            String dName = (String) entity.getCustomData().get("DERELICT_NAME");

            if (wasRescued != null && wasRescued.booleanValue() && vId != null) {

                DerelictSalvageRecord rec = derelictSalvageRecords.get(vId);

                if (rec != null) {

                    rec.outOfBounds = true;

                    rec.rescueChance = 0.50f; // 50% recovery chance when knocked out of bounds

                    rec.minDmodProb = 0.40f;

                    rec.maxDmodProb = 0.65f;

                }

                if (engine.getCombatUI() != null) {

                    String msg = IS_RUSSIAN ?

                        ("Остов " + dName + " унесло за границу сектора! Маяки активны (Шанс эвакуации: 50%).") :

                        ("Derelict " + dName + " drifted beyond sector perimeter! Beacons active (Recovery chance: 50%).");

                    engine.getCombatUI().addMessage(1, msg, new Color(255, 140, 60));

                }

            } else if (vId != null) {

                // If not stabilized and flew out of bounds, permanently lost!
                derelictSalvageRecords.remove(vId);

                if (engine.getCombatUI() != null) {

                    String msg = IS_RUSSIAN ?

                        ("Остов " + dName + " унесён в глубокий космос без стабилизации! Корабль безвозвратно потерян.") :

                        ("Derelict " + dName + " drifted into deep space unstabilized! Ship is permanently lost.");

                    engine.getCombatUI().addMessage(1, msg, new Color(255, 80, 80));

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



                // Active shields expand the effective gathering / contact radius

                float shipEffectiveRadius = ship.getCollisionRadius();

                if (ship.getShield() != null && ship.getShield().isOn()) {

                    shipEffectiveRadius = Math.max(shipEffectiveRadius, ship.getShield().getRadius());

                }

                float combinedRadius = shipEffectiveRadius + eRadius + 20f;



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

        if ("EXPLOSIVE_TANK".equals(eggType)) {
            Float lastTankPing = (Float) entity.getCustomData().get("DA_LAST_TANK_PING");
            float now = totalCombatTime;
            if (lastTankPing == null || now - lastTankPing.floatValue() >= 0.75f) {
                entity.getCustomData().put("DA_LAST_TANK_PING", Float.valueOf(now));
                Color tankRed = new Color(255, 45, 30, 220);
                engine.addSmoothParticle(loc, entity.getVelocity(), 90f, 0.7f, 0.7f, tankRed);
                engine.addHitParticle(loc, entity.getVelocity(), 14f, 0.9f, 0.4f, new Color(255, 170, 40, 255));
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

            // Softened mass for derelicts so collisions gently nudge rather than pulverize friendly ships
            float mass = Math.max(60f, baseHp * 0.08f);

            float hp = Math.max(3200f, baseHp * 2.2f);



            try {

                Global.getSettings().loadTexture(spritePath);

            } catch (Throwable t) {}



            CombatEntityAPI entity = engine.spawnAsteroid(spritePath, spawnX, spawnY, vx, vy, false);

            if (entity != null) {

                entity.setFacing(random.nextFloat() * 360f);

                entity.setAngularVelocity((random.nextFloat() - 0.5f) * 16f);

                entity.setMass(mass);

                entity.setHitpoints(hp);

                float dispW = radius * 2.0f;
                float dispH = radius * 2.0f;

                if (entity instanceof CombatAsteroidAPI) {

                    try {

                        CombatAsteroidAPI ast = (CombatAsteroidAPI) entity;

                        SpriteAPI s = ast.getSpriteAPI();

                        if (s != null && isValidSprite(s)) {

                            float maxDim = Math.max(s.getTextureWidth(), s.getTextureHeight());

                            if (maxDim > 0f) {

                                float scale = (radius * 2.25f) / maxDim;

                                dispW = s.getTextureWidth() * scale;
                                dispH = s.getTextureHeight() * scale;
                                s.setSize(dispW, dispH);

                            } else {

                                s.setSize(radius * 2.25f, radius * 2.25f);

                            }

                            s.setColor(new Color(230, 230, 240, 255));

                            s.setAlphaMult(1.0f);

                        }

                    } catch (Throwable t) {}

                }

                // Tight hitbox: ship hulls are elongated. The collision circle MUST fit snugly inside
                // the visible hull silhouette and never extend into thin air past the flanks!
                float minDim = Math.min(dispW, dispH);
                float maxDimD = Math.max(dispW, dispH);
                float tightRadius = Math.max(25f, Math.min(minDim * 0.46f, maxDimD * 0.30f));
                entity.setCollisionRadius(tightRadius);



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

        } else if ("DEBRIS_LRG".equals(specificType)) {

            String[] lrg = new String[] { "graphics/debris/debris_lrg0.png", "graphics/debris/debris_lrg1.png" };
            spritePath = lrg[random.nextInt(lrg.length)];
            eggType = "DEBRIS";
            radius = 48f; mass = 220f; hp = 1800f; spin = 12f;

        } else if ("DEBRIS_MED".equals(specificType)) {

            String[] med = new String[] { "graphics/debris/debris_med0.png", "graphics/debris/debris_med1.png", "graphics/debris/debris_med2.png" };
            spritePath = med[random.nextInt(med.length)];
            eggType = "DEBRIS";
            radius = 32f; mass = 120f; hp = 1200f; spin = 16f;

        } else if ("DEBRIS_SML".equals(specificType)) {

            String[] sml = new String[] { "graphics/debris/debris_sml0.png", "graphics/debris/debris_sml1.png", "graphics/debris/debris_sml2.png", "graphics/debris/debris_sml3.png" };
            spritePath = sml[random.nextInt(sml.length)];
            eggType = "DEBRIS";
            radius = 20f; mass = 50f; hp = 600f; spin = 22f;

        } else if ("MOTHERSHIP_POD".equals(specificType)) {

            String[] pods = new String[] {
                "graphics/ships/derelict/derelict_mothership_pod_m_left.png",
                "graphics/ships/derelict/derelict_mothership_pod_m_right.png",
                "graphics/ships/derelict/derelict_mothership_pod_s_right.png",
                "graphics/ships/derelict/derelict_mothership_bow_module.png"
            };
            spritePath = pods[random.nextInt(pods.length)];
            eggType = "DEBRIS";
            radius = 52f; mass = 260f; hp = 2400f; spin = 10f;

        } else if ("MOTHERSHIP_ENGINE".equals(specificType) || "DEBRIS".equals(specificType)) {

            spritePath = "graphics/ships/derelict/derelict_mothership_pod_engine_left.png";

            eggType = "MOTHERSHIP_ENGINE";

            radius = 58f; mass = 300f; hp = 2600f; spin = 14f;

        } else if ("EXPLOSIVE_TANK".equals(specificType)) {

            spritePath = "graphics/icons/cargo/fuel.png";

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

                    spritePath = "graphics/icons/cargo/fuel.png";

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

                    // Silenced: only derelict ships show sensor alerts
                } else if ("EXPLOSIVE_TANK".equals(eggType)) {
                    // Silenced
                }

                return entity;

            }

        } catch (Throwable t) {}

        return null;

    }




    // =========================================================================
    // PLAYER ASTEROID KILL REWARD SYSTEM (Easter Egg & Fighter Drops)
    // =========================================================================

    private void checkPlayerDestroyedAsteroids() {
        if (engine == null || isTitleScreen) return;
        ShipAPI playerShip = getPlayerShipSafely();
        if (playerShip == null || !playerShip.isAlive()) return;

        // 1. Projectiles (Ballistic, Energy, Plasma, Flak, etc.)
        List<DamagingProjectileAPI> projs = engine.getProjectiles();
        if (projs != null && !projs.isEmpty()) {
            for (int i = 0; i < projs.size(); i++) {
                DamagingProjectileAPI proj = projs.get(i);
                if (proj == null) continue;
                if (!proj.didDamage()) continue;
                if (proj.getCustomData().containsKey("DA_HIT_PROCESSED")) continue;
                proj.getCustomData().put("DA_HIT_PROCESSED", Boolean.TRUE);

                ShipAPI sourceShip = extractShipFromSource(proj);
                if (!isPlayerOrPlayerFighter(sourceShip, playerShip)) continue;

                CombatEntityAPI target = proj.getDamageTarget();
                if (isValidAsteroidTarget(target)) {
                    target.getCustomData().put("DA_LAST_PLAYER_HIT_TIME", totalCombatTime);
                    if (target.getHitpoints() <= 0f || target.isExpired()) {
                        processPlayerAsteroidKill(target, playerShip);
                    }
                }
            }
        }

        // 2. Missiles & Torpedoes
        List<MissileAPI> missiles = engine.getMissiles();
        if (missiles != null && !missiles.isEmpty()) {
            for (int i = 0; i < missiles.size(); i++) {
                MissileAPI m = missiles.get(i);
                if (m == null) continue;
                if (!m.didDamage()) continue;
                if (m.getCustomData().containsKey("DA_HIT_PROCESSED")) continue;
                m.getCustomData().put("DA_HIT_PROCESSED", Boolean.TRUE);

                ShipAPI sourceShip = extractShipFromSource(m);
                if (!isPlayerOrPlayerFighter(sourceShip, playerShip)) continue;

                CombatEntityAPI target = m.getDamageTarget();
                if (isValidAsteroidTarget(target)) {
                    target.getCustomData().put("DA_LAST_PLAYER_HIT_TIME", totalCombatTime);
                    if (target.getHitpoints() <= 0f || target.isExpired()) {
                        processPlayerAsteroidKill(target, playerShip);
                    }
                }
            }
        }

        // 3. Continuous Beams (Mining Lasers, Phase Lances, Tachyon Lances, etc.)
        List<BeamAPI> beams = engine.getBeams();
        if (beams != null && !beams.isEmpty()) {
            for (int i = 0; i < beams.size(); i++) {
                BeamAPI beam = beams.get(i);
                if (beam == null) continue;
                if (!beam.didDamageThisFrame()) continue;

                ShipAPI sourceShip = beam.getSource();
                if (!isPlayerOrPlayerFighter(sourceShip, playerShip)) continue;

                CombatEntityAPI target = beam.getDamageTarget();
                if (isValidAsteroidTarget(target)) {
                    target.getCustomData().put("DA_LAST_PLAYER_HIT_TIME", totalCombatTime);
                    if (target.getHitpoints() <= 0f || target.isExpired()) {
                        processPlayerAsteroidKill(target, playerShip);
                    }
                }
            }
        }
    }

    private boolean isPlayerOrPlayerFighter(ShipAPI ship, ShipAPI playerShip) {
        if (ship == null || playerShip == null) return false;
        if (ship == playerShip) return true;
        if (ship.isFighter() && ship.getWing() != null && ship.getWing().getSourceShip() == playerShip) {
            return true;
        }
        return false;
    }

    private boolean isValidAsteroidTarget(CombatEntityAPI target) {
        if (target == null) return false;
        if (target.getCustomData().containsKey("IS_EASTER_EGG")) return false;
        if (target.getCollisionClass() == CollisionClass.ASTEROID || target instanceof CombatAsteroidAPI || target.getCustomData().containsKey(TRACKED_KEY)) {
            return true;
        }
        return false;
    }

    private void processPlayerAsteroidKill(CombatEntityAPI target, ShipAPI playerShip) {
        if (target == null) return;
        if (target.getCustomData().containsKey("DA_KILL_LOOT_PROCESSED")) return;
        target.getCustomData().put("DA_KILL_LOOT_PROCESSED", Boolean.TRUE);

        Vector2f loc = target.getLocation();
        if (loc == null) return;

        Vector2f vel = target.getVelocity();
        float vx = vel != null ? vel.x * 0.7f : 0f;
        float vy = vel != null ? vel.y * 0.7f : 0f;

        boolean isMegalith = target.getCustomData().containsKey("DA_IS_MEGALITH");
        float derelictChance = isMegalith ? megalithDerelictDropChance : 0f;
        float fighterChance = isMegalith ? 0.015f : playerKillFighterDropChance;
        float resourceChance = isMegalith ? 0.200f : playerKillResourceDropChance;

        // 1. Derelict ship drop (Megalith exclusive! Independent roll)
        boolean derelictAlreadyInPlay = (activeDerelictEntity != null && !activeDerelictEntity.isExpired() && engine.isEntityInPlay(activeDerelictEntity));
        if (isMegalith && !derelictAlreadyInPlay && derelictChance > 0f) {
            float rollDerelict = random.nextFloat();
            if (rollDerelict < derelictChance) {
                CombatEntityAPI derelict = spawnDerelictAtLocation(loc.x, loc.y, vx, vy);
                if (derelict != null) {
                    String dName = (String) derelict.getCustomData().get("DERELICT_NAME");
                    if (dName == null) dName = IS_RUSSIAN ? "Заброшенный корабль" : "Derelict Vessel";
                    try {
                        Global.getSoundPlayer().playUISound("ui_rep_raise", 1.0f, 1.0f);
                    } catch (Throwable t) {}
                    String alert = IS_RUSSIAN ?
                        ("ИЗ НЕДР МЕГАЛИТА: В обломках найден замурованный остов (" + dName + ")! Защитите его для эвакуации!") :
                        ("FROM MEGALITH INTERIOR: Entombing rock shattered, revealing derelict vessel (" + dName + ")! Protect it for salvage!");
                    if (engine.getCombatUI() != null && !isTitleScreen) {
                        engine.getCombatUI().addMessage(1, alert, new Color(255, 215, 60));
                    }
                    engine.addFloatingText(loc, IS_RUSSIAN ? ("ОСТОВ: " + dName) : ("DERELICT: " + dName), 24f, new Color(255, 215, 60), derelict, 0.4f, 4.0f);
                    engine.addSmoothParticle(loc, new Vector2f(vx, vy), 350f, 1.2f, 2.0f, new Color(255, 220, 100, 255));
                }
            }
        }

        // 2. Fighter LPC drop (Independent roll)
        float rollFighter = random.nextFloat();
        if (rollFighter < fighterChance) {
            CombatEntityAPI egg = spawnOtherEasterEggAtLocation(loc.x + (random.nextFloat() - 0.5f) * 60f, loc.y + (random.nextFloat() - 0.5f) * 60f, vx, vy, "FIGHTER");
            if (egg != null) {
                String fName = (String) egg.getCustomData().get("FIGHTER_WING_NAME");
                if (fName == null) fName = IS_RUSSIAN ? "Истребитель" : "Fighter";
                String alert = IS_RUSSIAN ?
                    ("ИЗ ОБЛОМКОВ АСТЕРОИДА: Найден дрейфующий истребитель (" + fName + ")!") :
                    ("FROM ASTEROID DEBRIS: Drifting fighter discovered (" + fName + ")!");
                if (engine.getCombatUI() != null && !isTitleScreen) {
                    engine.getCombatUI().addMessage(1, alert, new Color(100, 240, 255));
                }
                engine.addFloatingText(egg.getLocation(), IS_RUSSIAN ? ("ИСТРЕБИТЕЛЬ: " + fName) : ("FIGHTER: " + fName), 22f, new Color(100, 240, 255), egg, 0.3f, 3.5f);
            }
        }

        // 3. Resource Easter Egg (Independent roll)
        float rollResource = random.nextFloat();
        if (rollResource < resourceChance) {
            String[] resPool = new String[] { "CACHE", "PROBE", "CRYO_POD", "NAV_BUOY" };
            String specificType = resPool[random.nextInt(resPool.length)];
            CombatEntityAPI egg = spawnOtherEasterEggAtLocation(loc.x + (random.nextFloat() - 0.5f) * 60f, loc.y + (random.nextFloat() - 0.5f) * 60f, vx, vy, specificType);
            if (egg != null) {
                String nameRu = "Тайник Домена";
                String nameEn = "Domain Cache";
                if ("PROBE".equals(specificType)) { nameRu = "Зонд Домена"; nameEn = "Survey Probe"; }
                else if ("CRYO_POD".equals(specificType)) { nameRu = "Криокапсула"; nameEn = "Cryo Pod"; }
                else if ("NAV_BUOY".equals(specificType)) { nameRu = "Навигационный буй"; nameEn = "Nav Buoy"; }

                String alert = IS_RUSSIAN ?
                    ("ИЗ ОБЛОМКОВ АСТЕРОИДА: Выбит ценный контейнер (" + nameRu + ")!") :
                    ("FROM ASTEROID DEBRIS: Valuable salvage container dropped (" + nameEn + ")!");
                if (engine.getCombatUI() != null && !isTitleScreen) {
                    engine.getCombatUI().addMessage(1, alert, new Color(255, 215, 60));
                }
                engine.addFloatingText(egg.getLocation(), IS_RUSSIAN ? ("НАХОДКА: " + nameRu) : ("SALVAGE: " + nameEn), 22f, new Color(255, 215, 60), egg, 0.3f, 3.0f);
            }
        }
    }

    private void spawnOtherEasterEgg(float spawnX, float spawnY, float vx, float vy, boolean isResource) {

        String specificType = null;

        if (isResource) {

            String[] resourcePool = new String[] { "CACHE", "PROBE", "CRYO_POD", "NAV_BUOY", "FIGHTER" };

            specificType = resourcePool[random.nextInt(resourcePool.length)];

        } else {

            String[] debrisPool = new String[] {
                "DEBRIS_LRG", "DEBRIS_MED", "DEBRIS_SML",
                "MOTHERSHIP_POD", "MOTHERSHIP_ENGINE", "DEBRIS", "EXPLOSIVE_TANK"
            };

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



    // =========================================================================
    // STATION DEFLECTION DOME: LAMINAR FLOW & VISUAL DEFLECTOR BARRIER
    // =========================================================================

    private void updateStationDomes(float amount) {
        if (!enableStationDeflectionDome || engine == null) {
            activeStationDomes.clear();
            return;
        }

        // Count down offline timers every frame, not only on the 0.35s dome scan.
        for (int i = 0; i < activeStationDomes.size(); i++) {
            StationDomeData d = activeStationDomes.get(i);
            if (d.offlineTimer > 0f) {
                d.offlineTimer -= amount;
                if (d.offlineTimer < 0f) d.offlineTimer = 0f;
            }
        }

        stationDomeScanTimer += amount;
        if (stationDomeScanTimer < 0.35f && !activeStationDomes.isEmpty()) {
            for (int i = 0; i < activeStationDomes.size(); i++) {
                StationDomeData d = activeStationDomes.get(i);
                if (d.stationCore != null && d.stationCore.getLocation() != null) {
                    d.center.set(d.stationCore.getLocation());
                }
            }
            return;
        }
        stationDomeScanTimer = 0f;

        // Keep existing dome state (offline timer and failed-section memory) alive
        // between scans instead of recreating it and losing the edge-detection state.
        for (int i = activeStationDomes.size() - 1; i >= 0; i--) {
            StationDomeData d = activeStationDomes.get(i);
            if (d.stationCore == null || !d.stationCore.isAlive() || d.stationCore.getLocation() == null) {
                activeStationDomes.remove(i);
            }
        }

        List<ShipAPI> ships = engine.getShips();
        if (ships == null) return;
        for (int i = 0; i < ships.size(); i++) {
            ShipAPI ship = ships.get(i);
            if (ship == null || !ship.isAlive()) continue;

            if (ship.isStation() && ship.getParentStation() == null) {
                StationDomeData dome = null;
                for (int d = 0; d < activeStationDomes.size(); d++) {
                    if (activeStationDomes.get(d).stationCore == ship) {
                        dome = activeStationDomes.get(d);
                        break;
                    }
                }
                if (dome == null) {
                    dome = new StationDomeData();
                    dome.stationCore = ship;
                    activeStationDomes.add(dome);
                }
                dome.center.set(ship.getLocation());

                float maxModuleDist = ship.getCollisionRadius();
                List<ShipAPI> childModules = ship.getChildModulesCopy();
                if (childModules != null) {
                    for (int m = 0; m < childModules.size(); m++) {
                        ShipAPI mod = childModules.get(m);
                        if (mod == null || mod.getLocation() == null) continue;
                        float dist = (float) Math.hypot(mod.getLocation().x - ship.getLocation().x, mod.getLocation().y - ship.getLocation().y);
                        float totalR = dist + mod.getCollisionRadius();
                        if (totalR > maxModuleDist) maxModuleDist = totalR;
                    }
                }
                dome.domeRadius = maxModuleDist + stationDomeMargin;
            }
        }

        // Section Overload Detection & Emergency Circuit Breaker
        for (int i = 0; i < activeStationDomes.size(); i++) {
            StationDomeData dome = activeStationDomes.get(i);
            if (dome.stationCore == null || !dome.stationCore.isAlive()) continue;

            boolean hasNewSectionFailure = false;
            ShipAPI failedSection = null;

            // Edge detection: only a fresh failure starts/restarts the outage.
            // A section that stays destroyed must not refresh the timer every scan.
            boolean coreFailing = isStationSectionFailing(dome.stationCore);
            if (coreFailing && !dome.coreFailureActive) {
                hasNewSectionFailure = true;
                failedSection = dome.stationCore;
            }
            dome.coreFailureActive = coreFailing;

            List<ShipAPI> failingSections = new ArrayList<ShipAPI>();
            List<ShipAPI> childModules = dome.stationCore.getChildModulesCopy();
            if (childModules != null) {
                for (int m = 0; m < childModules.size(); m++) {
                    ShipAPI mod = childModules.get(m);
                    if (mod == null) continue;
                    if (isStationSectionFailing(mod)) {
                        failingSections.add(mod);
                        if (!containsShipIdentity(dome.failedSections, mod)) {
                            hasNewSectionFailure = true;
                            if (failedSection == null) failedSection = mod;
                        }
                    }
                }
            }
            dome.failedSections.clear();
            dome.failedSections.addAll(failingSections);

            if (hasNewSectionFailure) {
                // Any new break/overload/vent cycle restarts the full one-minute outage.
                dome.offlineTimer = STATION_DOME_OFFLINE_DURATION;
            }

            if (dome.offlineTimer > 0f) {
                if (!dome.wasOffline) {
                    dome.wasOffline = true;
                    dome.isOffline = true;
                    if (engine != null && failedSection != null && failedSection.getLocation() != null) {
                        Vector2f fLoc = failedSection.getLocation();
                        engine.addHitParticle(fLoc, new Vector2f(0f, 0f), 280f, 1.2f, 1.8f, new Color(120, 220, 255, 240));
                        engine.addNegativeParticle(fLoc, new Vector2f(0f, 0f), 220f, 0.8f, 1.5f, new Color(80, 160, 255, 200));
                        engine.addFloatingText(fLoc, IS_RUSSIAN ? "СБОЙ СЕКЦИИ: КУПОЛ ОТКЛЮЧЕН!" : "SECTION FAILURE: DEFLECTION DOME OFFLINE!", 24f, new Color(255, 120, 60), failedSection, 0.4f, 3.5f);
                    }
                }
                dome.isOffline = true;
                if (engine != null && random.nextFloat() < 0.12f) {
                    float randAngle = random.nextFloat() * 6.283f;
                    Vector2f sparkLoc = new Vector2f(dome.center.x + (float) Math.cos(randAngle) * dome.domeRadius, dome.center.y + (float) Math.sin(randAngle) * dome.domeRadius);
                    engine.addHitParticle(sparkLoc, new Vector2f(0f, 0f), 45f, 0.5f, 0.25f, new Color(255, 140, 60, 200));
                }
            } else {
                if (dome.wasOffline) {
                    dome.wasOffline = false;
                    dome.isOffline = false;
                    if (engine != null) {
                        engine.addSmoothParticle(dome.center, new Vector2f(0f, 0f), dome.domeRadius * 1.5f, 1.2f, 2.0f, new Color(80, 200, 255, 180));
                        engine.addFloatingText(dome.center, IS_RUSSIAN ? "СИСТЕМЫ ВОССТАНОВЛЕНЫ: КУПОЛ АКТИВЕН" : "SYSTEMS RESTORED: DOME ONLINE", 24f, new Color(80, 230, 255), dome.stationCore, 0.4f, 3.0f);
                        try {
                            Global.getSoundPlayer().playUISound("ui_rep_raise", 1.0f, 1.0f);
                        } catch (Throwable t) {}
                    }
                }
                dome.isOffline = false;
            }
        }
    }

    private boolean isStationSectionFailing(ShipAPI section) {
        if (section == null) return false;
        if (!section.isAlive()) return true;
        if (section.getFluxTracker() != null) {
            return section.getFluxTracker().isOverloaded() || section.getFluxTracker().isVenting();
        }
        return false;
    }

    private boolean containsShipIdentity(List<ShipAPI> ships, ShipAPI ship) {
        if (ships == null || ship == null) return false;
        for (int i = 0; i < ships.size(); i++) {
            if (ships.get(i) == ship) return true;
        }
        return false;
    }

    public boolean isStationDomeOffline(ShipAPI ship) {
        if (ship == null || activeStationDomes == null) return false;
        ShipAPI core = (ship.isStation() && ship.getParentStation() == null) ? ship : ship.getParentStation();
        if (core == null) return false;
        for (int i = 0; i < activeStationDomes.size(); i++) {
            StationDomeData d = activeStationDomes.get(i);
            if (d.stationCore == core) {
                return d.isOffline;
            }
        }
        return false;
    }

    private void applyStationDeflectionToAsteroid(CombatEntityAPI ast, float amount) {
        if (!enableStationDeflectionDome || activeStationDomes == null || activeStationDomes.isEmpty()) return;
        Vector2f aLoc = ast.getLocation();
        Vector2f aVel = ast.getVelocity();
        if (aLoc == null || aVel == null) return;

        float aSpeed = aVel.length();
        if (aSpeed < 1.0f) return;

        float astRadius = ast.getCollisionRadius();

        for (int i = 0; i < activeStationDomes.size(); i++) {
            StationDomeData dome = activeStationDomes.get(i);
            if (dome.isOffline) continue; // Section failure: dome offline, asteroids penetrate!
            float dx = aLoc.x - dome.center.x;
            float dy = aLoc.y - dome.center.y;
            float distSq = dx * dx + dy * dy;

            float influenceDist = dome.domeRadius + astRadius + 350f;
            if (distSq > influenceDist * influenceDist || distSq < 1f) continue;

            float dist = (float) Math.sqrt(distSq);
            float nx = dx / dist;
            float ny = dy / dist;

            // Dot product: closing rate towards station center
            float closingRate = -(aVel.x * nx + aVel.y * ny);
            if (closingRate <= -20f && dist > dome.domeRadius) {
                // Moving away from station into downstream wake
                continue;
            }

            // Cross product to choose optimal slip direction around the perimeter
            float cross = dx * aVel.y - dy * aVel.x;
            float sign = (cross >= 0f) ? 1.0f : -1.0f;
            float tx = -ny * sign;
            float ty = nx * sign;

            // Normalized penetration depth: 0 at outer boundary, 1 at dome boundary
            float p = (influenceDist - dist) / 350f;
            if (p < 0f) p = 0f;
            if (p > 1.8f) p = 1.8f;

            // Blend velocity towards tangential laminar flow
            float blend = Math.min(1.0f, (p * 2.8f + 0.4f) * amount);
            aVel.x = aVel.x * (1.0f - blend) + tx * aSpeed * blend;
            aVel.y = aVel.y * (1.0f - blend) + ty * aSpeed * blend;

            // Outward repulsive force if approaching dome surface
            float surfaceDist = dome.domeRadius + astRadius + 40f;
            if (dist < surfaceDist) {
                float pushForce = (surfaceDist - dist) * 7.5f * amount;
                aVel.x += nx * pushForce;
                aVel.y += ny * pushForce;

                // Visual contact sparks on dome surface
                if (random.nextFloat() < 0.18f && engine != null) {
                    Vector2f sparkLoc = new Vector2f(dome.center.x + nx * (dome.domeRadius + 10f), dome.center.y + ny * (dome.domeRadius + 10f));
                    engine.addHitParticle(sparkLoc, new Vector2f(tx * 80f, ty * 80f), 35f + random.nextFloat() * 25f, 0.4f, 0.25f, new Color(100, 210, 255, 230));
                    engine.addSmoothParticle(sparkLoc, new Vector2f(0f, 0f), 70f, 0.6f, 0.4f, new Color(80, 160, 255, 120));
                }
            }

            // Conserve kinetic speed magnitude
            float newSpeed = aVel.length();
            if (newSpeed > 0.001f) {
                aVel.x = (aVel.x / newSpeed) * aSpeed;
                aVel.y = (aVel.y / newSpeed) * aSpeed;
            }

            ast.setCustomData(BASE_VX_KEY, Float.valueOf(aVel.x));
            ast.setCustomData(BASE_VY_KEY, Float.valueOf(aVel.y));
        }
    }

    private void applyShipDeflectionToAsteroid(CombatEntityAPI ast, float amount) {
        if (!enableDeploymentSafety || activeShipDomes == null || activeShipDomes.isEmpty()) return;
        Vector2f aLoc = ast.getLocation();
        Vector2f aVel = ast.getVelocity();
        if (aLoc == null || aVel == null) return;

        float aSpeed = aVel.length();
        if (aSpeed < 1.0f) return;

        float astRadius = ast.getCollisionRadius();

        for (int i = 0; i < activeShipDomes.size(); i++) {
            ShipAPI ship = activeShipDomes.get(i);
            if (ship == null || !ship.isAlive() || ship.getLocation() == null) continue;

            Vector2f sLoc = ship.getLocation();
            float dx = aLoc.x - sLoc.x;
            float dy = aLoc.y - sLoc.y;
            float distSq = dx * dx + dy * dy;

            float fieldRadius = ship.getCollisionRadius() * 1.15f + 15f;
            float influenceMargin = Math.max(70f, fieldRadius * 0.5f);
            float influenceDist = fieldRadius + astRadius + influenceMargin;
            if (distSq > influenceDist * influenceDist || distSq < 1f) continue;

            float dist = (float) Math.sqrt(distSq);
            float nx = dx / dist;
            float ny = dy / dist;

            // Closing rate towards ship center
            float closingRate = -(aVel.x * nx + aVel.y * ny);
            if (closingRate <= -15f && dist > fieldRadius) {
                // Moving away from ship into wake
                continue;
            }

            // Cross product to choose optimal slip direction around the perimeter
            float cross = dx * aVel.y - dy * aVel.x;
            float sign = (cross >= 0f) ? 1.0f : -1.0f;
            float tx = -ny * sign;
            float ty = nx * sign;

            // Normalized penetration depth: 0 at outer boundary, 1 at dome boundary
            float p = (influenceDist - dist) / influenceMargin;
            if (p < 0f) p = 0f;
            if (p > 1.8f) p = 1.8f;

            // Blend velocity towards tangential laminar flow
            float blend = Math.min(1.0f, (p * 3.0f + 0.5f) * amount);
            aVel.x = aVel.x * (1.0f - blend) + tx * aSpeed * blend;
            aVel.y = aVel.y * (1.0f - blend) + ty * aSpeed * blend;

            // Outward repulsive force if approaching field surface
            float surfaceDist = fieldRadius + astRadius + 15f;
            if (dist < surfaceDist) {
                float pushForce = (surfaceDist - dist) * 8.5f * amount;
                aVel.x += nx * pushForce;
                aVel.y += ny * pushForce;

                if (ast.getCustomData().containsKey("DA_IS_MEGALITH")) {
                    ast.getCustomData().put("DA_NO_MAINTAIN_VELOCITY", Boolean.TRUE);
                }

                // Visual contact sparks on field surface
                if (random.nextFloat() < 0.22f && engine != null) {
                    Vector2f sparkLoc = new Vector2f(sLoc.x + nx * (fieldRadius + 5f), sLoc.y + ny * (fieldRadius + 5f));
                    engine.addHitParticle(sparkLoc, new Vector2f(tx * 70f, ty * 70f), 25f + random.nextFloat() * 20f, 0.4f, 0.25f, new Color(100, 210, 255, 230));
                    engine.addSmoothParticle(sparkLoc, new Vector2f(0f, 0f), 50f, 0.5f, 0.35f, new Color(80, 160, 255, 120));
                }
            }

            // Conserve kinetic speed magnitude
            float newSpeed = aVel.length();
            if (newSpeed > 0.001f) {
                aVel.x = (aVel.x / newSpeed) * aSpeed;
                aVel.y = (aVel.y / newSpeed) * aSpeed;
            }

            ast.setCustomData(BASE_VX_KEY, Float.valueOf(aVel.x));
            ast.setCustomData(BASE_VY_KEY, Float.valueOf(aVel.y));
        }
    }

    private void triggerShipDomeActivation(ShipAPI ship) {
        if (engine == null || ship == null || ship.getLocation() == null) return;
        Vector2f loc = ship.getLocation();
        float r = ship.getCollisionRadius() * 1.15f + 15f;
        engine.addSmoothParticle(loc, new Vector2f(0f, 0f), r * 1.4f, 0.7f, 1.0f, new Color(100, 220, 255, 190));
        engine.addHitParticle(loc, new Vector2f(0f, 0f), r * 0.8f, 0.5f, 0.6f, new Color(120, 240, 255, 220));
    }

    private void triggerShipDomeDeactivation(ShipAPI ship) {
        // Deflection field naturally fades out over the final 1.2 seconds; no notifications or sounds needed
    }

    @Override
    public void renderInWorldCoords(ViewportAPI viewport) {
        if (engine == null || viewport == null) return;

        float totalTime = engine.getTotalElapsedTime(false);

        // 1. Station Deflection Domes
        if (enableStationDeflectionDome && activeStationDomes != null && !activeStationDomes.isEmpty()) {
            for (int i = 0; i < activeStationDomes.size(); i++) {
                StationDomeData dome = activeStationDomes.get(i);
                if (dome.stationCore == null || !dome.stationCore.isAlive()) continue;
                if (dome.isOffline) continue; // Section failure: collapsed dome does not render active barrier!

                float cx = dome.center.x;
                float cy = dome.center.y;

                if (!viewport.isNearViewport(dome.center, dome.domeRadius + 250f)) continue;

                float diameter = dome.domeRadius * 2.0f;
                float pulse = (float) (0.5 + 0.5 * Math.sin(totalTime * 1.8f));
                float alpha = 0.22f + 0.08f * pulse;

                // 1. Outer shimmering deflector ring
                SpriteAPI ring = getCachedSprite(null, null, "graphics/fx/shields256ring.png");
                if (ring != null && isValidSprite(ring)) {
                    ring.setSize(diameter, diameter);
                    ring.setColor(new Color(70, 175, 255, (int)(alpha * 255)));
                    ring.setAdditiveBlend();
                    ring.setAngle(totalTime * 4.0f);
                    ring.renderAtCenter(cx, cy);
                }

                // 2. Counter-rotating inner energy lattice
                SpriteAPI ring2 = getCachedSprite(null, null, "graphics/fx/shields256ringb.png");
                if (ring2 != null && isValidSprite(ring2)) {
                    float d2 = diameter * 0.96f;
                    ring2.setSize(d2, d2);
                    ring2.setColor(new Color(50, 140, 240, (int)((alpha * 0.7f) * 255)));
                    ring2.setAdditiveBlend();
                    ring2.setAngle(-totalTime * 6.0f);
                    ring2.renderAtCenter(cx, cy);
                }

                // 3. Faint ambient translucent protective shield disc
                SpriteAPI disc = getCachedSprite(null, null, "graphics/fx/shields256.png");
                if (disc != null && isValidSprite(disc)) {
                    disc.setSize(diameter * 0.98f, diameter * 0.98f);
                    disc.setColor(new Color(40, 110, 220, (int)(0.06f * 255)));
                    disc.setAdditiveBlend();
                    disc.renderAtCenter(cx, cy);
                }
            }
        }

        // 2. Ship Deployment Deflection Domes (strictly 7 seconds after spawn)
        if (enableDeploymentSafety && activeShipDomes != null && !activeShipDomes.isEmpty()) {
            SpriteAPI shipRing = getCachedSprite(null, null, "graphics/fx/shields256ring.png");
            SpriteAPI shipRing2 = getCachedSprite(null, null, "graphics/fx/shields256ringb.png");
            SpriteAPI shipDisc = getCachedSprite(null, null, "graphics/fx/shields256.png");

            for (int i = 0; i < activeShipDomes.size(); i++) {
                ShipAPI ship = activeShipDomes.get(i);
                if (ship == null || !ship.isAlive() || ship.getLocation() == null) continue;

                Vector2f loc = ship.getLocation();
                float fieldRadius = ship.getCollisionRadius() * 1.15f + 15f;
                if (!viewport.isNearViewport(loc, fieldRadius + 150f)) continue;

                Float spawnTime = shipSpawnTimes.get(ship);
                if (spawnTime == null) {
                    Object sData = ship.getCustomData().get("DA_SPAWN_TIME");
                    if (sData instanceof Float) spawnTime = (Float) sData;
                }
                float timeSinceSpawn = (spawnTime != null) ? (totalCombatTime - spawnTime.floatValue()) : 0f;
                float timeRemaining = deploymentSafetyDuration - timeSinceSpawn;
                if (timeRemaining <= 0f) continue;

                float fadeFactor = 1.0f;
                if (timeRemaining < 1.2f) {
                    fadeFactor = Math.max(0.0f, timeRemaining / 1.2f);
                }

                float diameter = fieldRadius * 2.0f;
                float pulse = (float) (0.5 + 0.5 * Math.sin(totalTime * 3.2f));
                float alpha = (0.28f + 0.12f * pulse) * fadeFactor;

                // 1. Outer shimmering deflector ring
                if (shipRing != null && isValidSprite(shipRing)) {
                    shipRing.setSize(diameter, diameter);
                    shipRing.setColor(new Color(80, 205, 255, (int)(alpha * 255)));
                    shipRing.setAdditiveBlend();
                    shipRing.setAngle(totalTime * 5.0f);
                    shipRing.renderAtCenter(loc.x, loc.y);
                }

                // 2. Counter-rotating inner energy lattice
                if (shipRing2 != null && isValidSprite(shipRing2)) {
                    float d2 = diameter * 0.95f;
                    shipRing2.setSize(d2, d2);
                    shipRing2.setColor(new Color(60, 160, 255, (int)((alpha * 0.75f) * 255)));
                    shipRing2.setAdditiveBlend();
                    shipRing2.setAngle(-totalTime * 7.5f);
                    shipRing2.renderAtCenter(loc.x, loc.y);
                }

                // 3. Faint ambient translucent protective shield disc
                if (shipDisc != null && isValidSprite(shipDisc)) {
                    shipDisc.setSize(diameter * 0.97f, diameter * 0.97f);
                    shipDisc.setColor(new Color(50, 140, 250, (int)(0.08f * alpha * 255)));
                    shipDisc.setAdditiveBlend();
                    shipDisc.renderAtCenter(loc.x, loc.y);
                }
            }
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

                } else {
                    // Resource easter eggs, explosive tanks and debris float naturally as physical objects in space
                    // without glowing radar rings or UI overlay markers
                    continue;
                }

            } catch (Throwable t) {}

        }

    }

}

