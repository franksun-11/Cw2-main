package uk.ac.ed.acp.cw2.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.dto.Drone;
import uk.ac.ed.acp.cw2.service.DroneQueryService;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class DroneController {

    private static final Logger logger = LoggerFactory.getLogger(DroneController.class);

    private final DroneQueryService droneQueryService;

    public DroneController(DroneQueryService droneQueryService) {
        this.droneQueryService = droneQueryService;
    }

    /**
     * 2a) GET /api/v1/dronesWithCooling/{state}
     * return a list of drone IDs that have cooling state matching the path variable
     */
    @GetMapping("/dronesWithCooling/{state}")
    public ResponseEntity<List<Integer>> getDronesWithCooling(
            @PathVariable boolean state) {
        List<Integer> droneIds = droneQueryService.getDronesWithCooling(state);
        logger.info("Returning {} drones with cooling state {}", droneIds.size(), state);
        return ResponseEntity.ok(droneIds);
    }

    /**
     * 2b) GET /api/v1/droneDetails/{id}
     * return the full details of the drone with the given ID
     */
    @GetMapping("/droneDetails/{id}")
    public ResponseEntity<Drone> getDroneDetails(@PathVariable Integer id) {
        Drone drone = droneQueryService.getDroneById(id);
        if (drone == null) {
            logger.warn("Drone with ID {} not found", id);
            return ResponseEntity.notFound().build();
        }
        logger.info("Returning drone details for ID {}", id);
        return ResponseEntity.ok(drone);
    }
}
