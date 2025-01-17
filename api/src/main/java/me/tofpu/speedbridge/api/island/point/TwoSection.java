package me.tofpu.speedbridge.api.island.point;

import org.bukkit.Location;

/**
 * This represents a two-way location point that extends Point. Used for bordering the island.
 * @see Point
 */
public interface TwoSection extends Point {
    /**
     * The pointB defined location
     *
     * @return location b
     */
    Location pointB();

    /**
     * The availability of pointB location
     *
     * @return true if pointB is defined, otherwise false
     */
    boolean hasPointB();

    /**
     * Setting the pointB location
     *
     * @param pointB defining the pointB location
     */
    void pointB(final Location pointB);
}
