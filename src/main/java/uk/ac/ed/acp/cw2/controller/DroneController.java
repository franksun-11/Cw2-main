package uk.ac.ed.acp.cw2.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.dto.DeliveryPathResponse;
import uk.ac.ed.acp.cw2.dto.Drone;
import uk.ac.ed.acp.cw2.dto.MedDispatchRec;
import uk.ac.ed.acp.cw2.dto.QueryCondition;
import uk.ac.ed.acp.cw2.service.DroneQueryService;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1")
public class DroneController {

    private static final Logger logger = LoggerFactory.getLogger(DroneController.class);

    @Autowired
    private DroneQueryService droneQueryService;


    /**
     * 2a) GET /api/v1/dronesWithCooling/{state}
     * return a list of drone IDs that have cooling state matching the path variable
     */
    @GetMapping("/dronesWithCooling/{state}")
    public ResponseEntity<List<Integer>> getDronesWithCooling(@PathVariable boolean state) {
        logger.info("Request: GET /dronesWithCooling/{}", state);
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
        logger.info("Request: GET /droneDetails/{}", id);
        Drone drone = droneQueryService.getDroneById(id);
        if (drone == null) {
            logger.warn("Drone with ID {} not found", id);
            return ResponseEntity.notFound().build();
        }
        logger.info("Returning drone details for ID {}", id);
        return ResponseEntity.ok(drone);
    }

    /**
     * 3a) GET /api/v1/queryAsPath/{attributeName}/{attributeValue}
     * query drones by attribute name and value, returning their IDs as a path
     */
    @GetMapping("/queryAsPath/{attributeName}/{attributeValue}")
    public ResponseEntity<List<Integer>> queryDronesByAttribute(@PathVariable String attributeName, @PathVariable String attributeValue) {
        logger.info("Request: GET /queryAsPath/{}/{}", attributeName, attributeValue);

        List<Integer> droneIds = droneQueryService.queryAsPath(
                attributeName, attributeValue);

        logger.info("Found {} drones matching {}={}",
                droneIds.size(), attributeName, attributeValue);

        return ResponseEntity.ok(droneIds);
    }

    /**
     * 3b) POST /api/v1/query
     * query drones by multiple conditions
     */
    @PostMapping("/query")
    public ResponseEntity<List<Integer>> queryDronesByConditions(@RequestBody List<QueryCondition> conditions) {

        logger.info("Request: POST /query with {} conditions", conditions.size());
        logger.debug("Conditions: {}", conditions);

        List<Integer> droneIds = droneQueryService.queryByConditions(conditions);

        logger.info("Found {} drones matching all conditions", droneIds.size());
        logger.debug("Matching drone IDs: {}", droneIds);

        return ResponseEntity.ok(droneIds);
    }

    /**
     * 4) POST /api/v1/queryAvailableDrones
     * query drones that are available for dispatching
     */
    @PostMapping("/queryAvailableDrones")
    public ResponseEntity<List<Integer>> queryAvailableDrones(@RequestBody List<MedDispatchRec> dispatches) {
        logger.info("Request: POST /queryAvailableDrones with {} dispatches", dispatches.size());
        logger.debug("Dispatches: {}", dispatches);

        List<Integer> droneIds = droneQueryService.queryAvailableDrones(dispatches);

        logger.info("Found {} available drones for the given dispatches", droneIds.size());
        logger.debug("Available drone IDs: {}", droneIds);

        return ResponseEntity.ok(droneIds);
    }

    /**
     * 5) POST /api/v1/calcDeliveryPath
     * Calculate optimal delivery path for given dispatches
     */
    @PostMapping("/calcDeliveryPath")
    public ResponseEntity<DeliveryPathResponse> calculateDeliveryPath(
            @RequestBody List<MedDispatchRec> dispatches) {

        logger.info("Request: POST /calcDeliveryPath with {} dispatches", dispatches.size());
        logger.debug("Dispatches: {}", dispatches);

        DeliveryPathResponse response = droneQueryService.calcDeliveryPath(dispatches);

        if (response == null) {
            logger.warn("Failed to calculate delivery path");
            return ResponseEntity.badRequest().build();
        }

        logger.info("Successfully calculated delivery path - Cost: {}, Moves: {}, Drones: {}",
                response.getTotalCost(), response.getTotalMoves(),
                response.getDronePaths() != null ? response.getDronePaths().size() : 0);

        return ResponseEntity.ok(response);
    }

    /**
     * 5) POST /api/v1/calcDeliveryPathAsGeoJson
     * Calculate optimal delivery path for given dispatches and return as GeoJSON
     */
    @PostMapping("/calcDeliveryPathAsGeoJson")
    public ResponseEntity<String> calcDeliveryPathAsGeoJson(
            @RequestBody List<MedDispatchRec> dispatches) {

        logger.info("Request: POST /calcDeliveryPathAsGeoJson with {} dispatches", dispatches.size());
        logger.debug("Dispatches: {}", dispatches);

        DeliveryPathResponse response = droneQueryService.calcDeliveryPath(dispatches);

        if (response == null || response.getDronePaths() == null || response.getDronePaths().isEmpty()) {
            logger.warn("Failed to calculate delivery path for GeoJSON");
            return ResponseEntity.badRequest().build();
        }

        // Convert to GeoJSON format
        Map<String, Object> geoJson = new HashMap<>();
        geoJson.put("type", "FeatureCollection");

        List<Map<String, Object>> features = new ArrayList<>();

        // 首先添加服务点标记（起点）
        DeliveryPathResponse.DronePath dronePath = response.getDronePaths().get(0);
        if (!dronePath.getDeliveries().isEmpty() && !dronePath.getDeliveries().get(0).getFlightPath().isEmpty()) {
            // 获取路径的起点（服务点位置）
            DeliveryPathResponse.LngLat startPoint = dronePath.getDeliveries().get(0).getFlightPath().get(0);

            Map<String, Object> servicePointFeature = new HashMap<>();
            servicePointFeature.put("type", "Feature");

            Map<String, Object> spProperties = new HashMap<>();
            spProperties.put("name", "Service Point");
            spProperties.put("type", "servicePoint");
            spProperties.put("description", "starting point");
            servicePointFeature.put("properties", spProperties);

            Map<String, Object> spGeometry = new HashMap<>();
            spGeometry.put("type", "Point");
            spGeometry.put("coordinates", Arrays.asList(startPoint.getLng(), startPoint.getLat()));

            servicePointFeature.put("geometry", spGeometry);
            features.add(servicePointFeature);
        }

        // Add path as LineString feature
        Map<String, Object> lineFeature = new HashMap<>();
        lineFeature.put("type", "Feature");

        Map<String, Object> lineProperties = new HashMap<>();
        lineProperties.put("name", "Drone Flight Path");
        lineProperties.put("totalMoves", response.getTotalMoves());
        lineProperties.put("totalCost", response.getTotalCost());
        lineFeature.put("properties", lineProperties);

        Map<String, Object> geometry = new HashMap<>();
        geometry.put("type", "LineString");

        List<List<Double>> coordinates = new ArrayList<>();

        // Collect all coordinates from the first drone path (assuming single drone as per spec)
        for (DeliveryPathResponse.Delivery delivery : dronePath.getDeliveries()) {
            for (DeliveryPathResponse.LngLat point : delivery.getFlightPath()) {
                coordinates.add(Arrays.asList(point.getLng(), point.getLat()));
            }
        }

        geometry.put("coordinates", coordinates);
        lineFeature.put("geometry", geometry);
        features.add(lineFeature);

        // 修复：直接从输入数据中获取配送点坐标
        // Add delivery points as Point features - USE ORIGINAL INPUT COORDINATES
        for (int i = 0; i < dronePath.getDeliveries().size(); i++) {
            DeliveryPathResponse.Delivery delivery = dronePath.getDeliveries().get(i);

            // 找到对应的输入配送记录
            MedDispatchRec originalDispatch = findOriginalDispatch(dispatches, delivery.getDeliveryId());

            if (originalDispatch != null && originalDispatch.getDelivery() != null) {
                Map<String, Object> pointFeature = new HashMap<>();
                pointFeature.put("type", "Feature");

                Map<String, Object> pointProperties = new HashMap<>();
                pointProperties.put("deliveryId", delivery.getDeliveryId());
                pointProperties.put("type", "delivery");
                pointProperties.put("name", "Delivery Point " + delivery.getDeliveryId());
                pointProperties.put("originalLng", originalDispatch.getDelivery().getLng());
                pointProperties.put("originalLat", originalDispatch.getDelivery().getLat());
                pointFeature.put("properties", pointProperties);

                Map<String, Object> pointGeometry = new HashMap<>();
                pointGeometry.put("type", "Point");
                // 🔧 关键修复：使用原始输入坐标，而不是路径中的坐标
                pointGeometry.put("coordinates", Arrays.asList(
                        originalDispatch.getDelivery().getLng(),
                        originalDispatch.getDelivery().getLat()
                ));

                pointFeature.put("geometry", pointGeometry);
                features.add(pointFeature);

                logger.debug("Added delivery point for ID {} at ({}, {})",
                        delivery.getDeliveryId(),
                        originalDispatch.getDelivery().getLng(),
                        originalDispatch.getDelivery().getLat());
            } else {
                logger.warn("Could not find original dispatch for delivery ID {}", delivery.getDeliveryId());
            }
        }

        geoJson.put("features", features);

        // Convert to JSON string
        try {
            ObjectMapper mapper = new ObjectMapper();
            String geoJsonString = mapper.writeValueAsString(geoJson);

            logger.info("Successfully generated GeoJSON delivery path with service point");
            return ResponseEntity.ok(geoJsonString);
        } catch (Exception e) {
            logger.error("Error generating GeoJSON", e);
            return ResponseEntity.status(500).build();
        }
    }

    // 添加辅助方法：根据deliveryId找到原始配送记录
    private MedDispatchRec findOriginalDispatch(List<MedDispatchRec> dispatches, Integer deliveryId) {
        if (dispatches == null || deliveryId == null) {
            return null;
        }

        return dispatches.stream()
                .filter(dispatch -> deliveryId.equals(dispatch.getId()))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Find the delivery point in a flight path (where the drone hovers to deliver)
     */
    private DeliveryPathResponse.LngLat findDeliveryPoint(List<DeliveryPathResponse.LngLat> flightPath) {
        // Look for consecutive duplicate points which indicate a hover/delivery
        for (int i = 0; i < flightPath.size() - 1; i++) {
            DeliveryPathResponse.LngLat point1 = flightPath.get(i);
            DeliveryPathResponse.LngLat point2 = flightPath.get(i + 1);
            
            if (point1.getLng().equals(point2.getLng()) && point1.getLat().equals(point2.getLat())) {
                return point1;
            }
        }
        
        // If no hover point found, return the last point
        return flightPath.get(flightPath.size() - 1);
    }
}
