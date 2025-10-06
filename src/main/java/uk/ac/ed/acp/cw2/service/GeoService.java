package uk.ac.ed.acp.cw2.service;

import org.springframework.stereotype.Service;
import uk.ac.ed.acp.cw2.pojo.LngLat;

import java.util.List;

/**
 * Geographic computing service interface
 */
@Service
public interface GeoService {
    /**
     * Calculate the Euclidean distance between two positions
     * @param position1 The first position
     * @param position2 The second position
     * @return The Euclidean distance between the two positions
     */
    double calculateDistance(LngLat position1, LngLat position2);

    /**
     * Check if two positions are close to each other (distance < 0.00015)
     * @param position1 The first position
     * @param position2 The second position
     * @return true if the positions are close to each other, false otherwise
     */
    boolean isCloseTo(LngLat position1, LngLat position2);

    /**
     * Calculate the next position based on the starting position and angle
     * @param start The starting position
     * @param angle The angle (0-359 degrees)
     * @return The new position
     */
    LngLat nextPosition(LngLat start, double angle);

    /**
     * Check if a position is inside a polygon region (including the boundary)
     * @param position The position to check
     * @param vertices The list of vertices of the polygon
     * @return true if the position is inside the region (including the boundary), false otherwise
     */
    boolean isInRegion(LngLat position, List<LngLat> vertices);
}