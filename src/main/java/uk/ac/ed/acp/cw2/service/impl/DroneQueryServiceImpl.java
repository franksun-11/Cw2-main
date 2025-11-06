package uk.ac.ed.acp.cw2.service.impl;

import org.springframework.stereotype.Service;
import uk.ac.ed.acp.cw2.dto.Drone;
import uk.ac.ed.acp.cw2.repository.IlpDataRepository;
import uk.ac.ed.acp.cw2.service.DroneQueryService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Drone query service implementation
 */
@Service
public class DroneQueryServiceImpl implements DroneQueryService {

    private final IlpDataRepository repository;

    public DroneQueryServiceImpl(IlpDataRepository repository) {
        this.repository = repository;
    }

    /**
     * return a list of drone IDs that have cooling state matching the path variable
     */
    @Override
    public List<Integer> getDronesWithCooling(boolean coolingRequired) {
        return repository.getAllDrones().stream()
                .filter(drone -> drone.getCapability() != null)
                .filter(drone -> {
                    Boolean cooling = drone.getCapability().getCooling();
                    return cooling != null && cooling == coolingRequired;
                })
                .map(Drone::getId)
                .collect(Collectors.toList());
    }

    /**
     * return a drone by its ID
     */
    @Override
    public Drone getDroneById(Integer id) {
        return repository.getAllDrones().stream()
                .filter(drone -> drone.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}
