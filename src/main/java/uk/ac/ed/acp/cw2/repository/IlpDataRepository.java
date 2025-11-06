package uk.ac.ed.acp.cw2.repository;

import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;
import uk.ac.ed.acp.cw2.dto.Drone;
import uk.ac.ed.acp.cw2.dto.DroneForServicePoint;

import java.util.Arrays;
import java.util.List;

/**
 * Repository for fetching data from the ILP REST endpoint
 */
@Repository
public class IlpDataRepository {
    /**
     * Rest template for making HTTP requests
     */
    private final RestTemplate restTemplate;
    private final String ilpEndpoint;

    public IlpDataRepository(RestTemplate restTemplate, String ilpEndpoint) {
        this.restTemplate = restTemplate;
        this.ilpEndpoint = ilpEndpoint;
    }

    /**
     * get all drones from ILP endpoint
     */
    public List<Drone> getAllDrones() {
        Drone[] drones = restTemplate.getForObject(
                ilpEndpoint + "/drones",
                Drone[].class
        );
        return drones != null ? Arrays.asList(drones) : List.of();
    }

    /**
     * get drones availability for service points from ILP endpoint
     */
    public List<DroneForServicePoint> getDronesForServicePoints() {
        DroneForServicePoint[] data = restTemplate.getForObject(
                ilpEndpoint + "/drones-for-service-points",
                DroneForServicePoint[].class
        );
        return data != null ? Arrays.asList(data) : List.of();
    }
}