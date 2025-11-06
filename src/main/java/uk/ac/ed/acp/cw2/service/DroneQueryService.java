package uk.ac.ed.acp.cw2.service;

import org.springframework.stereotype.Service;
import uk.ac.ed.acp.cw2.dto.Drone;

import java.util.List;

/**
 * Drone query service interface for static queries
 */
@Service
public interface DroneQueryService {

    /**
     * Get drones with or without cooling capability
     * @param coolingRequired true for drones with cooling, false for drones without cooling
     * @return List of drone IDs that match the cooling requirement
     */
    List<Integer> getDronesWithCooling(boolean coolingRequired);

    /**
     * Get drone details by ID
     * @param id The drone ID
     * @return The drone object if found, null otherwise
     */
    Drone getDroneById(Integer id);
}