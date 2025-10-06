package uk.ac.ed.acp.cw2.service.impl;

import org.springframework.stereotype.Service;
import uk.ac.ed.acp.cw2.pojo.LngLat;
import uk.ac.ed.acp.cw2.service.GeoService;

import java.util.List;

/**
 * Geographic computing service implementation
 */
@Service
public class GeoServiceImpl implements GeoService {

    // Constants
    private static final double CLOSE_THRESHOLD = 0.00015;
    private static final double MOVE_DISTANCE = 0.00015;

    /**
     * Calculate the Euclidean distance between two positions
     * @param position1 The first position
     * @param position2 The second position
     * @return The Euclidean distance between the two positions
     */
    @Override
    public double calculateDistance(LngLat position1, LngLat position2) {
        double deltaLng = position1.getLng() - position2.getLng();
        double deltaLat = position1.getLat() - position2.getLat();
        return Math.sqrt(deltaLng * deltaLng + deltaLat * deltaLat);
    }

    /**
     * Check if two positions are close to each other (distance < 0.00015)
     */
    @Override
    public boolean isCloseTo(LngLat position1, LngLat position2) {
        return calculateDistance(position1, position2) < CLOSE_THRESHOLD;
    }

    /**
     * Return the next position as LngLat for a start position and an angle
     * @param start The start position
     * @param angle The angle in degrees (0-360)
     * @return The next position as LngLat
     */
    @Override
    public LngLat nextPosition(LngLat start, double angle) {
        // 999 indicate hover
        if (angle == 999) {
            return new LngLat(start.getLng(), start.getLat());
        }

        // change angle to radians
        double radians = Math.toRadians(angle);

        // calculate new position
        double newLng = start.getLng() + MOVE_DISTANCE * Math.cos(radians);
        double newLat = start.getLat() + MOVE_DISTANCE * Math.sin(radians);

        return new LngLat(newLng, newLat);
    }

    /**
     * Check if a position is inside a polygon region using ray casting algorithm
     * @param position The position to check
     * @param vertices The vertices of the polygon region
     * @return true if the position is inside the region, false otherwise
     */
    @Override
    public boolean isInRegion(LngLat position, List<LngLat> vertices) {
        if (vertices == null || vertices.size() < 3) {
            return false;
        }

        // check if the region is closed
        LngLat first = vertices.get(0);
        LngLat last = vertices.get(vertices.size() - 1);
        if (!first.getLng().equals(last.getLng()) || !first.getLat().equals(last.getLat())) {
            return false;
        }

        // Ray Casting Algorithm
        int intersectCount = 0;
        for (int i = 0; i < vertices.size() - 1; i++) {
            LngLat v1 = vertices.get(i);
            LngLat v2 = vertices.get(i + 1);

            if (rayIntersectsSegment(position, v1, v2)) {
                intersectCount++;
            }
        }

        // odd number of intersections means the point is inside the region
        return intersectCount % 2 == 1;
    }

    /**
     * Check if a horizontal ray from the point intersects with the line segment
     */
    private boolean rayIntersectsSegment(LngLat point, LngLat v1, LngLat v2) {
        // Check if the point's latitude is within the segment's latitude range
        if (point.getLat() > Math.max(v1.getLat(), v2.getLat())) {
            return false;
        }
        if (point.getLat() <= Math.min(v1.getLat(), v2.getLat())) {
            return false;
        }

        // Check if the point's longitude intersects with the segment's longitude
        double xIntersection = (point.getLat() - v1.getLat()) * (v2.getLng() - v1.getLng()) /
                (v2.getLat() - v1.getLat()) + v1.getLng();

        // if the point's longitude is less than the intersection point, then the point is inside the segment
        return point.getLng() < xIntersection;
    }
}