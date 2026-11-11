package data.scripts.plugins;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.AutofireAIPlugin;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponGroupAPI;

/**
 * Asteroid Point Defense Autofire Decorator (Dynamic Asteroids 2.0.5)
 * 
 * High-performance, zero-allocation AutofireAIPlugin wrapper that seamlessly integrates
 * kinetic asteroid threat interception into Starsector's native Point Defense pipeline.
 * 
 * Features:
 * - Mount-Specific Tracking: Evaluates threats approaching each weapon's physical arc.
 * - Pre-Tracking / Slewing: Smoothly rotates mounts towards incoming threats prior to entry.
 * - 100% preserves vanilla weapon AI: delegates entirely when no collision threat is present.
 * - Strict threat hierarchy: NEVER distracts the weapon away from incoming enemy missiles,
 *   torpedoes, or active attacking strike-craft.
 * - Native turn rate & LOS: Starsector's internal weapon controller smoothly turns the mount
 *   and executes line-of-sight & friendly-fire safety checks natively.
 * - Flux conservation: dynamically obeys the user's configurable flux limit (prevents overloads).
 */
public class AsteroidPDAutofirePlugin implements AutofireAIPlugin {

    private final AutofireAIPlugin delegate;
    private final WeaponAPI weapon;
    private CombatEntityAPI targetAsteroid = null;
    private final Vector2f leadPoint = new Vector2f();
    private boolean shouldFire = false;

    public AsteroidPDAutofirePlugin(AutofireAIPlugin delegate, WeaponAPI weapon) {
        this.delegate = delegate;
        this.weapon = weapon;
    }

    public AutofireAIPlugin getDelegate() {
        return delegate;
    }

    @Override
    public void advance(float amount) {
        // 1. Advance the underlying vanilla/mod AI first to maintain its internal timers & states
        if (delegate != null) {
            delegate.advance(amount);
        }

        shouldFire = false;
        targetAsteroid = null;

        // 2. Global enable check from mod settings
        if (!DynamicAsteroidsCombatPlugin.isPDTargetingEnabled()) {
            return;
        }

        ShipAPI ship = weapon.getShip();
        if (ship == null || !ship.isAlive() || ship.isHulk()) {
            return;
        }

        // Never hijack manual player control unless weapon group is on autofire
        if (DynamicAsteroidsCombatPlugin.isShipUnderManualPlayerControl(ship)) {
            WeaponGroupAPI group = ship.getWeaponGroupFor(weapon);
            if (group == null || !group.isAutofiring()) {
                return;
            }
        }

        // Weapon ready check
        if (weapon.isDisabled() || weapon.isPermanentlyDisabled() || weapon.getAmmo() == 0) {
            return;
        }

        // 3. Flux Conservation Check: If flux exceeds the safety threshold, save flux for shields
        if (DynamicAsteroidsCombatPlugin.isPDFluxExceeded(ship)) {
            return;
        }

        // 4. Strict Threat Hierarchy: If the vanilla AI is actively targeting a real missile or fighter,
        // NEVER divert point defense away from actual hostile ordnance!
        if (delegate != null) {
            MissileAPI targetMissile = delegate.getTargetMissile();
            if (targetMissile != null && !targetMissile.isExpired()) {
                return; // Let vanilla PD eliminate the incoming missile/torpedo!
            }
            ShipAPI targetShip = delegate.getTargetShip();
            if (targetShip != null && !targetShip.isHulk() && targetShip.isFighter()) {
                return; // Let vanilla PD shoot down the enemy fighter/bomber!
            }
        }

        // 5. Find best incoming threat specifically for THIS weapon mount and firing arc
        CombatEntityAPI threat = DynamicAsteroidsCombatPlugin.findBestThreatForWeapon(weapon);
        if (threat == null || threat.isExpired()) {
            return;
        }

        // Never target easter eggs (derelicts, wreckage, fuel tanks)
        if (threat.getCustomData().containsKey("IS_EASTER_EGG")) {
            return;
        }

        Vector2f wLoc = weapon.getLocation();
        Vector2f tLoc = threat.getLocation();
        Vector2f tVel = threat.getVelocity();
        Vector2f sVel = ship.getVelocity();
        if (wLoc == null || tLoc == null || tVel == null || sVel == null) {
            return;
        }

        float dx = tLoc.x - wLoc.x;
        float dy = tLoc.y - wLoc.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        targetAsteroid = threat;
        boolean inRange = false;

        // 6. Target Aim Point & Ballistic Intercept Calculation
        if (weapon.isBeam()) {
            // BEAM WEAPONS: Instantaneous hitscan rays! Aim directly at the target entity center!
            leadPoint.set(tLoc.x, tLoc.y);
            inRange = (dist <= weapon.getRange() + threat.getCollisionRadius() * 1.15f);
        } else {
            // PROJECTILE WEAPONS: Solve exact relative quadratic intercept
            float rvx = tVel.x - sVel.x;
            float rvy = tVel.y - sVel.y;
            float projSpeed = weapon.getProjectileSpeed();
            if (projSpeed <= 100.0f) {
                projSpeed = 850.0f;
            }

            // Quadratic equation: (|v_rel|^2 - s^2)*t^2 + 2*(r . v_rel)*t + |r|^2 = 0
            float a = (rvx * rvx + rvy * rvy) - (projSpeed * projSpeed);
            float b = 2.0f * (dx * rvx + dy * rvy);
            float c = dx * dx + dy * dy;

            float tIntercept = -1.0f;
            float disc = b * b - 4.0f * a * c;

            if (disc >= 0f) {
                float sqrtDisc = (float) Math.sqrt(disc);
                if (Math.abs(a) > 0.0001f) {
                    float t1 = (-b - sqrtDisc) / (2.0f * a);
                    float t2 = (-b + sqrtDisc) / (2.0f * a);
                    if (t1 > 0.01f && t2 > 0.01f) {
                        tIntercept = Math.min(t1, t2);
                    } else if (t1 > 0.01f) {
                        tIntercept = t1;
                    } else if (t2 > 0.01f) {
                        tIntercept = t2;
                    }
                } else if (Math.abs(b) > 0.0001f) {
                    float t = -c / b;
                    if (t > 0.01f) tIntercept = t;
                }
            }

            if (tIntercept <= 0.01f || tIntercept > 3.0f) {
                float relSpeed = (float) Math.sqrt(rvx * rvx + rvy * rvy);
                float closing = Math.max(projSpeed, projSpeed + relSpeed * 0.5f);
                tIntercept = dist / closing;
            }

            // Projectile aim point accounting for firing platform velocity
            leadPoint.set(wLoc.x + dx + rvx * tIntercept, wLoc.y + dy + rvy * tIntercept);

            float interceptTravelDist = projSpeed * tIntercept;
            inRange = (interceptTravelDist <= weapon.getRange() + threat.getCollisionRadius() * 1.15f);
        }

        // 7. Weapon Ready & Firing Checks
        // - Mount arc check for lead point
        boolean inArc = (weapon.distanceFromArc(leadPoint) <= 6.0f);

        // - Turret crosshairs alignment check: STRICT CONE (prevents spraying off-target)
        float desiredAngle = computeAngle(wLoc.x, wLoc.y, leadPoint.x, leadPoint.y);
        float currentAngle = weapon.getCurrAngle();
        float angleDiff = Math.abs(getShortestRotation(currentAngle, desiredAngle));
        float maxAngleDiff = weapon.isBeam() ? 4.0f : 3.5f;
        boolean isAligned = (angleDiff <= maxAngleDiff);

        if (inRange && isAligned && inArc) {
            shouldFire = true;
        }
    }

    @Override
    public boolean shouldFire() {
        if (targetAsteroid != null) {
            return shouldFire;
        }
        return delegate != null && delegate.shouldFire();
    }

    @Override
    public void forceOff() {
        if (delegate != null) {
            delegate.forceOff();
        }
        targetAsteroid = null;
        shouldFire = false;
    }

    @Override
    public Vector2f getTarget() {
        if (targetAsteroid != null) {
            return leadPoint;
        }
        return delegate != null ? delegate.getTarget() : null;
    }

    @Override
    public ShipAPI getTargetShip() {
        if (targetAsteroid != null) {
            return null; // Target is an asteroid, not a ship
        }
        return delegate != null ? delegate.getTargetShip() : null;
    }

    @Override
    public WeaponAPI getWeapon() {
        return weapon;
    }

    @Override
    public MissileAPI getTargetMissile() {
        if (targetAsteroid != null) {
            return null;
        }
        return delegate != null ? delegate.getTargetMissile() : null;
    }

    private static float computeAngle(float fromX, float fromY, float toX, float toY) {
        float angle = (float) Math.toDegrees(Math.atan2(toY - fromY, toX - fromX));
        if (angle < 0f) angle += 360f;
        return angle;
    }

    private static float getShortestRotation(float from, float to) {
        float diff = to - from;
        while (diff > 180f) diff -= 360f;
        while (diff < -180f) diff += 360f;
        return diff;
    }
}
