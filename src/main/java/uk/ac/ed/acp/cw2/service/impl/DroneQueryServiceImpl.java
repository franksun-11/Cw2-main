package uk.ac.ed.acp.cw2.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import uk.ac.ed.acp.cw2.dto.*;
import uk.ac.ed.acp.cw2.service.DroneQueryService;

import javax.print.attribute.IntegerSyntax;
import java.lang.reflect.Field;
import java.sql.Time;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DroneQueryServiceImpl implements DroneQueryService {

    private static final Logger logger = LoggerFactory.getLogger(DroneQueryServiceImpl.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private String ilpEndpoint; // 从配置注入

    /**
     * Fetch all drones from the ILP REST service
     */
    private List<Drone> fetchAllDrones() {
        Drone[] drones = restTemplate.getForObject(ilpEndpoint + "/drones", Drone[].class);
        return drones != null ? Arrays.asList(drones) : List.of();
    }

    /**
     * Fetch drone availability at service points
     */
    private List<DroneServicePointAvailability> fetchDroneAvailability() {
        DroneServicePointAvailability[] availability = restTemplate.getForObject(
                ilpEndpoint + "/drones-for-service-points",
                DroneServicePointAvailability[].class);
        return availability != null ? Arrays.asList(availability) : List.of();
    }

    /**
     * Fetch all service points
     */
    private List<ServicePoint> fetchAllServicePoints() {
        ServicePoint[] servicePoints = restTemplate.getForObject(
                ilpEndpoint + "/service-points",
                ServicePoint[].class);
        return servicePoints != null ? Arrays.asList(servicePoints) : List.of();
    }

    /**
     * Fetch all restricted areas
     */
    private List<RestrictedArea> fetchRestrictedAreas() {
        RestrictedArea[] restrictedAreas = restTemplate.getForObject(
                ilpEndpoint + "/restricted-areas",
                RestrictedArea[].class);
        return restrictedAreas != null ? Arrays.asList(restrictedAreas) : List.of();
    }


    @Override
    public List<Integer> getDronesWithCooling(boolean coolingRequired) {
        logger.info("Querying drones with cooling={}", coolingRequired);

        // fetch data
        List<Drone> drones = fetchAllDrones();

        return drones.stream()
                .filter(drone -> drone.getCapability() != null)
                .filter(drone -> {
                    Boolean cooling = drone.getCapability().getCooling();
                    return cooling != null && cooling == coolingRequired;
                })
                .map(Drone::getId)
                .collect(Collectors.toList());
    }

    @Override
    public Drone getDroneById(Integer id) {
        logger.info("Querying drone by ID: {}", id);

        // fetch data
        List<Drone> drones = fetchAllDrones();

        return drones.stream()
                .filter(drone -> drone.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Integer> queryAsPath(String attributeName, String attributeValue) {
        logger.info("Querying drones by attribute: {}={}", attributeName, attributeValue);

        // fetch data
        List<Drone> drones = fetchAllDrones();

        return drones.stream()
                .filter(drone -> drone.getCapability() != null)
                .filter(drone -> matchesAttribute(drone.getCapability(), attributeName, attributeValue))
                .map(Drone::getId)
                .collect(Collectors.toList());
    }

    @Override
    public List<Integer> queryByConditions(List<QueryCondition> conditions) {
        logger.info("Querying drones by multiple conditions: {}", conditions);

        // fetch data
        List<Drone> drones = fetchAllDrones();

        return drones.stream()
                .filter(drone -> drone.getCapability() != null)
                .filter(drone -> matchAllConditions(drone.getCapability(), conditions))
                .map(Drone::getId)
                .collect(Collectors.toList());
    }

    @Override
    public List<Integer> queryAvailableDrones(List<MedDispatchRec> dispatches) {
        logger.info("Querying available drones for dispatches: {}", dispatches);

        // validate input
        if (dispatches == null || dispatches.isEmpty()) {
            logger.warn("No dispatches provided for availability query");
            return List.of();
        }

        // fetch data
        List<Drone> drones = fetchAllDrones();
        List<DroneServicePointAvailability> droneAvailability = fetchDroneAvailability();
        List<ServicePoint> servicePoints = fetchAllServicePoints();
        List<RestrictedArea> restrictedAreas = fetchRestrictedAreas();

        List<Integer> availableDroneIds = drones.stream()
                .filter(drone -> fulfillAllDispatches(drone, dispatches, droneAvailability, restrictedAreas))
                .map(Drone::getId)
                .toList();

        logger.info("Found {} drones that can fulfill all {} dispatches",
                availableDroneIds.size(), dispatches.size());
        logger.debug("Available drone IDs: {}", availableDroneIds);

        return availableDroneIds;
    }

    @Override
    public DeliveryPathResponse calcDeliveryPath(List<MedDispatchRec> dispatches) {
        logger.info("Calculating delivery path for dispatches: {}", dispatches);

        // Validate input
        if (dispatches == null || dispatches.isEmpty()) {
            logger.warn("No dispatches provided for path calculation");
            return createEmptyResponse();
        }
        // fetch all necessary data
        List<Drone> allDrones = fetchAllDrones();
        List<DroneServicePointAvailability> droneAvailability = fetchDroneAvailability();
        List<ServicePoint> servicePoints = fetchAllServicePoints();
        List<RestrictedArea> restrictedAreas = fetchRestrictedAreas();

        logger.info("Fetched {} drones, {} service points, {} restricted areas",
                allDrones.size(), servicePoints.size(), restrictedAreas.size());

        // find available drones
        List<Integer> availableDroneIds = queryAvailableDrones(dispatches);

        if (availableDroneIds.isEmpty()) {
            logger.warn("No available drones for dispatches: {}", dispatches);
            return createEmptyResponse();
        }
        logger.info("Found {} available drones ", availableDroneIds.size());

        try {
            // try each service point to find best path
            DeliveryPathResponse bestResponse = null;
            double bestCost = Double.MAX_VALUE;

            for (ServicePoint sp : servicePoints) {
                // Get drones available at this service point
                List<Integer> droneIdsAtSp = getDroneIdsAtServicePoint(sp.getId(), droneAvailability, availableDroneIds);

                if (droneIdsAtSp.isEmpty()) {
                    logger.debug("No available drones at service point {}", sp.getId());
                    continue;
                }

                // try each available drone at this service point
                for (Integer droneId : droneIdsAtSp) {
                    Drone drone = allDrones.stream()
                            .filter(d -> d.getId().equals(droneId))
                            .findFirst()
                            .orElse(null);

                    if (drone == null) {
                        logger.warn("Drone {} not found in allDrones list", droneId);
                        continue;
                    }

                    logger.debug("Calculating path for drone {} at service point {}",
                            drone.getId(), sp.getName());

                    // calculate delivery path for this drone
                    DeliveryPathResponse response = calculatePathForDrone(drone, sp, dispatches, restrictedAreas);

                    // select the solution with the lowest cost
                    if (response != null && response.getTotalCost() < bestCost) {
                        bestCost = response.getTotalCost();
                        bestResponse = response;
                        logger.info("New best path found with cost {} using drone {} at service point {}", bestCost, drone.getId(), sp.getName());
                    }
                }
            }

            if (bestResponse == null) {
                logger.warn("No valid delivery path found");
                return createEmptyResponse();
            }

            logger.info("Delivery path calculation completed. Total cost: {}, Total moves: {}",
                    bestResponse.getTotalCost(), bestResponse.getTotalMoves());
            return bestResponse;

        } catch (Exception e) {
            logger.error("Error calculating delivery path", e);
            return createEmptyResponse();
        }
    }

    /**
     * Check if a single attribute matches the given value
     */
    private boolean matchesAttribute(Drone.Capability capability, String attributeName, String attributeValue) {
        try {
            Field field = capability.getClass().getDeclaredField(attributeName);
            field.setAccessible(true);
            Object actualValue = field.get(capability);

            if (actualValue == null) {
                logger.debug("Attribute {} is null in capability", attributeName);
                return false;
            }

            return compareValues(actualValue, attributeValue);

        } catch (NoSuchFieldException e) {
            logger.warn("Attribute {} not found in Capability class", attributeName);
            return false;
        } catch (IllegalAccessException e) {
            logger.error("Cannot access attribute {} in Capability class", attributeName, e);
            return false;
        }
    }

    /**
     * Check if all conditions are satisfied
     */
    private boolean matchAllConditions(Drone.Capability capability, List<QueryCondition> conditions) {
        return conditions.stream()
                .allMatch(condition -> matchesCondition(capability, condition));
    }

    /**
     * Check if a drone can fulfill all dispatches
     */
    private boolean fulfillAllDispatches(Drone drone, List<MedDispatchRec> dispatches, List<DroneServicePointAvailability> droneAvailability, List<RestrictedArea> restrictedAreas) {
        if (drone.getCapability() == null) {
            logger.warn("Drone {} has no capability", drone.getName());
            return false;
        }
        return dispatches.stream()
                .allMatch(dispatch -> fulfillDispatch(drone, dispatch, droneAvailability, restrictedAreas));
    }

    /**
     * Check if a drone can fulfill a single dispatch
     */
    private boolean fulfillDispatch(Drone drone, MedDispatchRec dispatch, List<DroneServicePointAvailability> droneAvailability, List<RestrictedArea> restrictedAreas) {
        Drone.Capability capability = drone.getCapability();
        MedDispatchRec.Requirements requirements = dispatch.getRequirements();

        // 1.check capacity requirement
        if (requirements.getCapacity() != null) {
            if (capability.getCapacity() == null ||
                    capability.getCapacity() < requirements.getCapacity()) {
                logger.debug("Drone {} cannot fulfill capacity requirement: {} < {}",
                        drone.getName(), capability.getCapacity(), requirements.getCapacity());
                return false;
            }
        }

        // 2.check cooling requirement
        if (Boolean.TRUE.equals(requirements.getCooling())) {
            if (!Boolean.TRUE.equals(capability.getCooling())) {
                logger.debug("Drone {} cannot fulfill cooling requirement", drone.getName());
                return false;
            }
        }

        // 3. Check heating requirement
        if (Boolean.TRUE.equals(requirements.getHeating())) {
            if (!Boolean.TRUE.equals(capability.getHeating())) {
                logger.debug("Drone {} does not have heating capability", drone.getId());
                return false;
            }
        }

        // 4. Check cost requirement
/*        if (requirements.getMaxCost() != null) {
            double totalCost = calculateMaxCost(capability);
            if (totalCost > requirements.getMaxCost()) {
                logger.debug("Drone {} total cost {} > max cost {}",
                        drone.getId(), totalCost, requirements.getMaxCost());
                return false;
            }
        }*/

        // 5. Check time availability
        if (dispatch.getDate() != null && dispatch.getTime() != null) {
            if (!isAvailableAtTime(drone.getId(), dispatch.getDate(), dispatch.getTime(), droneAvailability)) {
                logger.debug("Drone {} not available on {} at {}",
                        drone.getId(), dispatch.getDate(), dispatch.getTime());
                return false;
            }
        }

        // 6. Check delivery location is not in restricted area
        if (dispatch.getDelivery() != null) {
            if (isInRestrictedArea(dispatch.getDelivery(), restrictedAreas)) {
                logger.debug("Delivery location ({}, {}) is in restricted area",
                        dispatch.getDelivery().getLng(), dispatch.getDelivery().getLat());
                return false;
            }
        }

        logger.debug("Drone {} can fulfill dispatch {}", drone.getId(), dispatch.getId());
        return true;
    }

    /**
     * Check if a drone is available at a specific date and time
     */
    private boolean isAvailableAtTime(Integer droneId, LocalDate date, LocalTime time, List<DroneServicePointAvailability> droneAvailability) {
        // Get day of week from date
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        String dayOfWeekStr = dayOfWeek.toString();

        // Search all service point for this drone's availability
        for (DroneServicePointAvailability sp : droneAvailability) {
            if (sp.getDrones() == null) continue;

            // Find this drone in the service point
            for (DroneServicePointAvailability.DroneAvailability da : sp.getDrones()) {
                // check if this drone is the one we are looking for
                if (!String.valueOf(droneId).equals(da.getId())) continue;
                // check availability on this day of week
                if (da.getAvailability() == null) continue;

                for (DroneServicePointAvailability.TimeSlot ts : da.getAvailability()) {
                    if (!dayOfWeekStr.equalsIgnoreCase(ts.getDayOfWeek())) continue;

                    // Check if time falls within the time slot
                    LocalTime from = ts.getFrom();
                    LocalTime until = ts.getUntil();

                    // check if time is within from-until
                    if (from != null && until != null) {
                        if (!time.isBefore(from) && !time.isAfter(until)) {
                            logger.debug("Drone {} is available on {} from {} to {}",
                                    droneId, dayOfWeekStr, from, until);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check if a location is in any restricted area
     */
    private boolean isInRestrictedArea(MedDispatchRec.Delivery delivery, List<RestrictedArea> restrictedAreas) {
        if (delivery.getLng() == null || delivery.getLat() == null) {
            return false;
        }

        for (RestrictedArea area : restrictedAreas) {
            if (isPointInPolygon(delivery.getLng(), delivery.getLat(), area.getVertices())) {
                logger.debug("Point ({}, {}) is inside restricted area: {}",
                        delivery.getLng(), delivery.getLat(), area.getName());
                return true;
            }
        }

        return false;
    }

    /**
     * Ray casting algorithm to check if a point is inside a polygon
     */
    private boolean isPointInPolygon(double lng, double lat, List<RestrictedArea.Vertex> vertices) {
        if (vertices == null || vertices.size() < 3) {
            return false;
        }

        boolean inside = false;
        int n = vertices.size();

        for (int i = 0, j = n - 1; i < n; j = i++) {
            RestrictedArea.Vertex vi = vertices.get(i);
            RestrictedArea.Vertex vj = vertices.get(j);

            if (vi.getLng() == null || vi.getLat() == null ||
                    vj.getLng() == null || vj.getLat() == null) {
                continue;
            }

            double xi = vi.getLng(), yi = vi.getLat();
            double xj = vj.getLng(), yj = vj.getLat();

            boolean intersect = ((yi > lat) != (yj > lat)) &&
                    (lng < (xj - xi) * (lat - yi) / (yj - yi) + xi);

            if (intersect) {
                inside = !inside;
            }
        }

        return inside;
    }

    /**
     * Calculate the maximum cost of a drone
     */
    private double calculateMaxCost(Drone.Capability capability) {
        double initialCost = capability.getCostInitial() != null ? capability.getCostInitial() : 0.0;
        double perMove = capability.getCostPerMove() != null ? capability.getCostPerMove() : 0.0;
        double finalCost = capability.getCostFinal() != null ? capability.getCostFinal() : 0.0;
        double maxMoves = capability.getMaxMoves() != null ? capability.getMaxMoves() : 0.0;

        return initialCost + perMove * maxMoves + finalCost;
    }

    /**
     *  Compare actual value with expected value as String
     */
    private boolean compareValues(Object actualValue, String expectedValue) {
        if (actualValue instanceof Boolean) {
            return actualValue.equals(Boolean.parseBoolean(expectedValue));
        } else if (actualValue instanceof Integer) {
            try {
                return actualValue.equals(Integer.parseInt(expectedValue));
            } catch (NumberFormatException e) {
                logger.warn("Cannot parse {} as Integer", expectedValue);
                return false;
            }
        } else if (actualValue instanceof Double) {
            try {
                double expected = Double.parseDouble(expectedValue);
                double actual = (Double) actualValue;
                return Math.abs(actual - expected) < 0.0001;
            } catch (NumberFormatException e) {
                logger.warn("Cannot parse {} as Double", expectedValue);
                return false;
            }
        } else {
            return actualValue.toString().equals(expectedValue);
        }
    }

    /**
     * Check if a single condition is satisfied
     */
    private boolean matchesCondition(Drone.Capability capability, QueryCondition condition) {
        try {
            // Get field value using reflection
            Field field = capability.getClass().getDeclaredField(condition.getAttribute());
            field.setAccessible(true);
            Object actualValue = field.get(capability);

            if (actualValue == null) {
                logger.debug("Attribute {} is null in capability", condition.getAttribute());
                return false;
            }

            // Compare based on operator
            return compareWithOperator(actualValue, condition.getOperator(), condition.getValue());

        } catch (NoSuchFieldException e) {
            logger.warn("Attribute {} not found in Capability class", condition.getAttribute());
            return false;
        } catch (IllegalAccessException e) {
            logger.error("Cannot access attribute {} in Capability class", condition.getAttribute(), e);
            return false;
        }
    }

    /**
     * Compare actual value with expected value using the specified operator
     */
    private boolean compareWithOperator(Object actualValue, String operator, String expectedValue) {
        // Handle Boolean comparison
        if (actualValue instanceof Boolean) {
            boolean actual = (Boolean) actualValue;
            boolean expected = Boolean.parseBoolean(expectedValue);
            switch (operator) {
                case "=":
                    return actual == expected;
                case "!=":
                    return actual != expected;
                default:
                    logger.warn("Unsupported operator {} for Boolean comparison", operator);
                    return false;
            }
        }

        // Handle Integer comparison
        if (actualValue instanceof Integer) {
            try {
                int actual = (Integer) actualValue;
                int expected = Integer.parseInt(expectedValue);

                switch (operator) {
                    case "=":
                        return actual == expected;
                    case "!=":
                        return actual != expected;
                    case "<":
                        return actual < expected;
                    case ">":
                        return actual > expected;
                    default:
                        logger.warn("Unsupported operator {} for Integer comparison", operator);
                        return false;
                }
            } catch (NumberFormatException e) {
                logger.warn("Cannot parse {} as Integer", expectedValue);
                return false;
            }
        }

        // Handle Double type (supports =, !=, <, >)
        if (actualValue instanceof Double) {
            try {
                double actual = (Double) actualValue;
                double expected = Double.parseDouble(expectedValue);

                switch (operator) {
                    case "=":
                        return Math.abs(actual - expected) < 0.0001; // Use epsilon for equality
                    case "!=":
                        return Math.abs(actual - expected) >= 0.0001;
                    case "<":
                        return actual < expected;
                    case ">":
                        return actual > expected;
                    default:
                        logger.warn("Unknown operator: {}", operator);
                        return false;
                }
            } catch (NumberFormatException e) {
                logger.warn("Cannot parse {} as Double", expectedValue);
                return false;
            }
        }

        // Handle String type (only supports = and !=)
        if (actualValue instanceof String) {
            String actual = (String) actualValue;

            switch (operator) {
                case "=":
                    return actual.equals(expectedValue);
                case "!=":
                    return !actual.equals(expectedValue);
                default:
                    logger.warn("Operator {} not supported for String type", operator);
                    return false;
            }
        }

        // Unknown type - use string comparison as fallback
        logger.warn("Unknown type for value: {}, using string comparison", actualValue.getClass());
        return actualValue.toString().equals(expectedValue);
    }

    /**
     * Create an empty response when no valid path is found
     */
    private DeliveryPathResponse createEmptyResponse() {
        DeliveryPathResponse response = new DeliveryPathResponse();
        response.setTotalCost(0.0);
        response.setTotalMoves(0);
        response.setDronePaths(List.of());
        return response;
    }

    /**
     * Get list of drone IDs available at a specific service point
     * Filters by both service point availability and overall availability
     */
    private List<Integer> getDroneIdsAtServicePoint(
            Integer servicePointId,
            List<DroneServicePointAvailability> droneAvailability,
            List<Integer> availableDroneIds) {

        return droneAvailability.stream()
                .filter(dsp -> dsp.getServicePointId().equals(servicePointId))
                .flatMap(dsp -> dsp.getDrones().stream())
                .map(d -> Integer.parseInt(d.getId()))
                .filter(availableDroneIds::contains)
                .distinct()
                .collect(Collectors.toList());
    }

    private DeliveryPathResponse calculatePathForDrone(Drone drone,
            ServicePoint servicePoint,
            List<MedDispatchRec> dispatches,
            List<RestrictedArea> restrictedAreas) {
        logger.info("Calculating path for drone {} from service point {}", drone.getId(), servicePoint.getName());

        // Group dispatches by date
        Map<LocalDate, List<MedDispatchRec>> dispatchByDate = dispatches.stream()
                .collect(Collectors.groupingBy(MedDispatchRec::getDate));

        List<DeliveryPathResponse.Delivery> allDeliveries = new ArrayList<>();
        int totalMoves = 0;

        // Process each day's dispatches separately
        for (Map.Entry<LocalDate, List<MedDispatchRec>> entry : dispatchByDate.entrySet()) {
            LocalDate date = entry.getKey();
            List<MedDispatchRec> dailyDispatches = entry.getValue();

            logger.debug("Processing {} dispatches for date {}", dailyDispatches.size(), date);

            // optimise delivery order
            List<MedDispatchRec> optimiseOrder = optimizeDeliveryOrder(servicePoint, dailyDispatches);

            // Generate flight path for this day's deliveries
            DeliveryPathResponse.LngLat currentLocation = new DeliveryPathResponse.LngLat(servicePoint.getLocation().getLng(), servicePoint.getLocation().getLat());

            for (int i = 0; i < optimiseOrder.size(); i++) {
                MedDispatchRec dispatch = optimiseOrder.get(i);
                DeliveryPathResponse.LngLat targetLocation = new DeliveryPathResponse.LngLat(
                        dispatch.getDelivery().getLng(), dispatch.getDelivery().getLat());

                // Generate flight path from currentLocation to targetLocation
                List<DeliveryPathResponse.LngLat> path = generateFlightPath(currentLocation, targetLocation, restrictedAreas);

                if (path == null) {
                    logger.warn("Cannot generate path for delivery {}", dispatch.getId());
                    return null;
                }

                // Add hover point (duplicate coordinate indicates delivery)
                path.add(new DeliveryPathResponse.LngLat(
                        targetLocation.getLng(), targetLocation.getLat()));

                // If this is the last delivery of the day, add return path to service point
                if (i == optimiseOrder.size() - 1) {
                    DeliveryPathResponse.LngLat servicePointLocation =
                            new DeliveryPathResponse.LngLat(
                                    servicePoint.getLocation().getLng(),
                                    servicePoint.getLocation().getLat()
                            );

                    List<DeliveryPathResponse.LngLat> returnPath = generateFlightPath(
                            targetLocation, servicePointLocation, restrictedAreas);

                    if (returnPath == null) {
                        logger.warn("Cannot generate return path to service point");
                        return null;
                    }

                    // Add return path (skip first point to avoid duplication)
                    if (returnPath.size() > 1) {
                        path.addAll(returnPath.subList(1, returnPath.size()));
                    }
                }

                // calculate moves (excluding hover - identical consecutive points)
                int movesForThisDelivery = 0;
                for (int j = 1; j < path.size(); j++) {
                    DeliveryPathResponse.LngLat prev = path.get(j - 1);
                    DeliveryPathResponse.LngLat curr = path.get(j);
                    // Only count as a move if positions are different (not hovering)
                    if (!prev.getLng().equals(curr.getLng()) || !prev.getLat().equals(curr.getLat())) {
                        movesForThisDelivery++;
                    }
                }
                totalMoves += movesForThisDelivery;

                // create Delivery object
                DeliveryPathResponse.Delivery delivery = new DeliveryPathResponse.Delivery();
                delivery.setDeliveryId(dispatch.getId());
                delivery.setFlightPath(path);
                allDeliveries.add(delivery);

                currentLocation = targetLocation;

                logger.debug("Generated path for delivery {} with {} moves",
                        dispatch.getId(), movesForThisDelivery);
            }
        }

        // Validate total moves against drone's limit
        Integer maxMoves = drone.getCapability().getMaxMoves();
        if (maxMoves != null && totalMoves > maxMoves) {
            logger.warn("Total moves ({}) exceeds drone's limit ({})", totalMoves, maxMoves);
            return null;
        }

        // calculate total cost
        double totalCost = calculateTotalCost(drone.getCapability(), totalMoves);

        // Validate total cost against each dispatch's max cost requirement
        for (MedDispatchRec dispatch : dispatches) {
            if (dispatch.getRequirements().getMaxCost() != null) {
                if (totalCost > dispatch.getRequirements().getMaxCost()) {
                    logger.debug("Path cost {} exceeds requirement {} for dispatch {}",
                            totalCost, dispatch.getRequirements().getMaxCost(), dispatch.getId());
                    return null;
                }
            }
        }

        // build response
        DeliveryPathResponse.DronePath dronePath = new DeliveryPathResponse.DronePath();
        dronePath.setDroneId(drone.getId());
        dronePath.setDeliveries(allDeliveries);

        DeliveryPathResponse response = new DeliveryPathResponse();
        response.setTotalCost(totalCost);
        response.setTotalMoves(totalMoves);
        response.setDronePaths(Arrays.asList(dronePath));

        logger.info("Path calculation completed - Cost: {}, Moves: {}, Deliveries: {}",
                totalCost, totalMoves, allDeliveries.size());

        return response;
    }
    /**
     * Optimize delivery order using TSP algorithm
     * Uses Dynamic Programming for small sets (<=12) and Greedy for larger sets
     */
    private List<MedDispatchRec> optimizeDeliveryOrder(ServicePoint startPoint, List<MedDispatchRec> dispatches) {

        // 先按时间排序
        List<MedDispatchRec> sortedByTime = dispatches.stream()
                .sorted(Comparator.comparing(MedDispatchRec::getTime))
                .collect(Collectors.toList());

        int n = sortedByTime.size();

        if (n == 0) {
            return List.of();
        }

        if (n == 1) {
            return sortedByTime;
        }

        // 如果时间跨度很小,可以进行 TSP 优化
        // 否则严格按时间顺序
        LocalTime firstTime = sortedByTime.get(0).getTime();
        LocalTime lastTime = sortedByTime.get(n - 1).getTime();

        // 如果时间跨度大于 30 分钟,必须按时间顺序
        if (java.time.Duration.between(firstTime, lastTime).toMinutes() > 30) {
            logger.debug("Time span > 30 minutes, using time-based order");
            return sortedByTime;
        }

        // 否则可以进行 TSP 优化
        if (n <= 12) {
            logger.debug("Using DP algorithm for {} dispatches", n);
            return optimizeDeliveryOrder_DP(startPoint, sortedByTime);
        }

        logger.debug("Using Greedy algorithm for {} dispatches", n);
        return optimizeDeliveryOrder_Greedy(startPoint, sortedByTime);
    }
    /**
     * Optimize delivery order using Greedy algorithm
     */
    private List<MedDispatchRec> optimizeDeliveryOrder_Greedy(ServicePoint startPoint, List<MedDispatchRec> dispatches) {

        List<MedDispatchRec> optimized = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();

        // Start from service point
        double currentLng = startPoint.getLocation().getLng();
        double currentLat = startPoint.getLocation().getLat();

        // Greedily select nearest unvisited dispatch
        while (optimized.size() < dispatches.size()) {
            MedDispatchRec nearest = null;
            double minDistance = Double.MAX_VALUE;

            for (MedDispatchRec dispatch : dispatches) {
                if (visited.contains(dispatch.getId())) {
                    continue;
                }

                double distance = calculateEuclideanDistance(
                        currentLng, currentLat,
                        dispatch.getDelivery().getLng(),
                        dispatch.getDelivery().getLat()
                );

                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = dispatch;
                }
            }

            if (nearest != null) {
                optimized.add(nearest);
                visited.add(nearest.getId());
                currentLng = nearest.getDelivery().getLng();
                currentLat = nearest.getDelivery().getLat();
            } else {
                break;
            }
        }

        logger.debug("Greedy optimization: total estimated distance = {}",
                calculateTotalDistance(startPoint, optimized));

        return optimized;
    }

    /**
     * Optimize delivery order using Dynamic Programming algorithm
     */
    private List<MedDispatchRec> optimizeDeliveryOrder_DP(ServicePoint startPoint, List<MedDispatchRec> dispatches) {

        int n = dispatches.size();

        // Build distance matrix
        double[][] dist = new double[n][n];
        double[] startDist = new double[n];

        // Calculate distances from start point to each dispatch
        for (int i = 0; i < n; i++) {
            startDist[i] = calculateEuclideanDistance(
                    startPoint.getLocation().getLng(),
                    startPoint.getLocation().getLat(),
                    dispatches.get(i).getDelivery().getLng(),
                    dispatches.get(i).getDelivery().getLat()
            );
        }

        // Calculate distances between dispatches
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    dist[i][j] = calculateEuclideanDistance(
                            dispatches.get(i).getDelivery().getLng(),
                            dispatches.get(i).getDelivery().getLat(),
                            dispatches.get(j).getDelivery().getLng(),
                            dispatches.get(j).getDelivery().getLat()
                    );
                }
            }
        }

        // DP table: dp[mask][i] = minimum distance to visit set 'mask' ending at i
        double[][] dp = new double[1 << n][n];
        int[][] parent = new int[1 << n][n];

        // Initialize with infinity
        for (double[] row : dp) {
            Arrays.fill(row, Double.MAX_VALUE);
        }

        // Base case: start from service point to each dispatch
        for (int i = 0; i < n; i++) {
            dp[1 << i][i] = startDist[i];
            parent[1 << i][i] = -1; // -1 indicates start point
        }

        // Fill DP table
        for (int mask = 0; mask < (1 << n); mask++) {
            for (int last = 0; last < n; last++) {
                if ((mask & (1 << last)) == 0) continue;
                if (dp[mask][last] == Double.MAX_VALUE) continue;

                // Try to visit each unvisited dispatch
                for (int next = 0; next < n; next++) {
                    if ((mask & (1 << next)) != 0) continue;

                    int newMask = mask | (1 << next);
                    double newDist = dp[mask][last] + dist[last][next];

                    if (newDist < dp[newMask][next]) {
                        dp[newMask][next] = newDist;
                        parent[newMask][next] = last;
                    }
                }
            }
        }

        // Find the best ending position
        int fullMask = (1 << n) - 1;
        int bestLast = -1;
        double bestDist = Double.MAX_VALUE;

        for (int i = 0; i < n; i++) {
            if (dp[fullMask][i] < bestDist) {
                bestDist = dp[fullMask][i];
                bestLast = i;
            }
        }

        // Reconstruct path
        List<Integer> path = new ArrayList<>();
        int mask = fullMask;
        int current = bestLast;

        while (current != -1) {
            path.add(current);
            int prevMask = mask ^ (1 << current);
            int prev = parent[mask][current];
            mask = prevMask;
            current = prev;
        }

        Collections.reverse(path);

        // Convert indices to dispatches
        List<MedDispatchRec> optimized = new ArrayList<>();
        for (int idx : path) {
            optimized.add(dispatches.get(idx));
        }

        logger.debug("DP optimization: optimal distance = {}", bestDist);

        return optimized;
    }

    private double calculateEuclideanDistance(double lng1, double lat1, double lng2, double lat2) {
        double dx = lng2 - lng1;
        double dy = lat2 - lat1;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Generate flight path from 'from' to 'to', avoiding restricted areas
     * Uses A* pathfinding when direct path is blocked
     * IMPORTANT: Every move must be exactly 0.00015 degrees in one of 16 compass directions
     */
    private List<DeliveryPathResponse.LngLat> generateFlightPath(DeliveryPathResponse.LngLat from, DeliveryPathResponse.LngLat to, List<RestrictedArea> restrictedAreas) {

        // Check if direct path is clear (no restricted areas blocking)
        if (isPathClear(from, to, restrictedAreas)) {
            logger.debug("Direct path is clear from ({}, {}) to ({}, {})",
                    from.getLng(), from.getLat(), to.getLng(), to.getLat());
            // Generate step-by-step path with moves of 0.00015 degrees
            return generateDirectPath(from, to, restrictedAreas);
        }

        // Direct path blocked, use A* pathfinding
        logger.debug("Direct path blocked, using A* pathfinding");
        return aStarPathfinding(from, to, restrictedAreas);
    }

    /**
     * Generate a direct path from 'from' to 'to' using greedy approach
     * Each step is exactly 0.00015 degrees in one of 16 compass directions
     * Stops when within 0.00015 degrees of target (close enough)
     */
    private List<DeliveryPathResponse.LngLat> generateDirectPath(
            DeliveryPathResponse.LngLat from,
            DeliveryPathResponse.LngLat to,
            List<RestrictedArea> restrictedAreas) {

        List<DeliveryPathResponse.LngLat> path = new ArrayList<>();
        DeliveryPathResponse.LngLat current = new DeliveryPathResponse.LngLat(from.getLng(), from.getLat());
        path.add(current);

        final double MOVE_DISTANCE = 0.00015;
        final double CLOSE_THRESHOLD = 0.00015;

        // 16 compass directions in degrees: 0, 22.5, 45, 67.5, ..., 337.5
        double[] angles = {0, 22.5, 45, 67.5, 90, 112.5, 135, 157.5, 180, 202.5, 225, 247.5, 270, 292.5, 315, 337.5};

        int maxSteps = 10000; // Safety limit to prevent infinite loops
        int steps = 0;

        while (steps < maxSteps) {
            // Check if we're close enough to target
            double distance = calculateEuclideanDistance(
                current.getLng(), current.getLat(),
                to.getLng(), to.getLat()
            );

            if (distance < CLOSE_THRESHOLD) {
                // Close enough - add final target position if not already there
                if (!current.getLng().equals(to.getLng()) || !current.getLat().equals(to.getLat())) {
                    path.add(new DeliveryPathResponse.LngLat(to.getLng(), to.getLat()));
                }
                break;
            }

            // Find best direction to move (greedy approach)
            double bestDistance = Double.MAX_VALUE;
            DeliveryPathResponse.LngLat bestNext = null;

            for (double angleDeg : angles) {
                double angleRad = Math.toRadians(angleDeg);
                double newLng = current.getLng() + MOVE_DISTANCE * Math.cos(angleRad);
                double newLat = current.getLat() + MOVE_DISTANCE * Math.sin(angleRad);

                // Calculate distance from this new position to target
                double distToTarget = calculateEuclideanDistance(newLng, newLat, to.getLng(), to.getLat());

                // Check if this move is valid (not crossing restricted areas)
                boolean valid = isValidMove(current.getLng(), current.getLat(), newLng, newLat, restrictedAreas);

                if (valid && distToTarget < bestDistance) {
                    bestDistance = distToTarget;
                    bestNext = new DeliveryPathResponse.LngLat(newLng, newLat);
                }
            }

            if (bestNext == null) {
                // No valid move found - path is blocked, fall back to A*
                logger.warn("Direct greedy path blocked at ({}, {}), using A* instead",
                    current.getLng(), current.getLat());
                return aStarPathfinding(from, to, restrictedAreas);
            }

            // Make the best move
            current = bestNext;
            path.add(current);
            steps++;
        }

        if (steps >= maxSteps) {
            logger.error("Direct path generation exceeded max steps, falling back to A*");
            return aStarPathfinding(from, to, restrictedAreas);
        }

        logger.debug("Generated direct path with {} steps", path.size() - 1);
        return path;
    }

    /**
     * Check if a direct path between two points crosses any restricted areas
     */
    private boolean isPathClear(DeliveryPathResponse.LngLat from, DeliveryPathResponse.LngLat to, List<RestrictedArea> restrictedAreas) {

        // For now, simplified check: test if endpoints are in restricted areas
        for (RestrictedArea area : restrictedAreas) {
            if (isPointInPolygon(from.getLng(), from.getLat(), area.getVertices()) ||
                    isPointInPolygon(to.getLng(), to.getLat(), area.getVertices())) {
                return false;
            }
        }

        return true;
    }

    private double calculateTotalCost(Drone.Capability capability, int totalMoves) {
        double initialCost = capability.getCostInitial() != null ?
                capability.getCostInitial() : 0.0;
        double costPerMove = capability.getCostPerMove() != null ?
                capability.getCostPerMove() : 0.0;
        double finalCost = capability.getCostFinal() != null ?
                capability.getCostFinal() : 0.0;

        double totalCost = initialCost + (costPerMove * totalMoves) + finalCost;

        logger.debug("Cost breakdown - Initial: {}, PerMove: {} × {}, Final: {}, Total: {}",
                initialCost, costPerMove, totalMoves, finalCost, totalCost);

        return totalCost;
    }

    /**
     * Inner class representing a node in A* search
     */
    private static class AStarNode {
        double lng;
        double lat;
        double g; // Cost from start
        double h; // Heuristic to goal
        double f; // Total cost (g + h)
        AStarNode parent;

        AStarNode(double lng, double lat) {
            this.lng = lng;
            this.lat = lat;
        }

        /**
         * Generate unique key for this node's position
         * Rounds to 10 decimal places to handle floating point precision
         */
        String getKey() {
            return String.format("%.10f,%.10f", lng, lat);
        }
    }

    /**
     * Calculate total distance for a delivery sequence
     * Used for TSP optimization logging
     */
    private double calculateTotalDistance(
            ServicePoint startPoint,
            List<MedDispatchRec> dispatches) {

        if (dispatches.isEmpty()) {
            return 0.0;
        }

        double totalDist = 0.0;

        // Distance from start to first dispatch
        totalDist += calculateEuclideanDistance(
                startPoint.getLocation().getLng(),
                startPoint.getLocation().getLat(),
                dispatches.get(0).getDelivery().getLng(),
                dispatches.get(0).getDelivery().getLat()
        );

        // Distances between consecutive dispatches
        for (int i = 0; i < dispatches.size() - 1; i++) {
            totalDist += calculateEuclideanDistance(
                    dispatches.get(i).getDelivery().getLng(),
                    dispatches.get(i).getDelivery().getLat(),
                    dispatches.get(i + 1).getDelivery().getLng(),
                    dispatches.get(i + 1).getDelivery().getLat()
            );
        }

        // Distance from last dispatch back to start
        totalDist += calculateEuclideanDistance(
                dispatches.get(dispatches.size() - 1).getDelivery().getLng(),
                dispatches.get(dispatches.size() - 1).getDelivery().getLat(),
                startPoint.getLocation().getLng(),
                startPoint.getLocation().getLat()
        );

        return totalDist;
    }

    private List<DeliveryPathResponse.LngLat> aStarPathfinding(
            DeliveryPathResponse.LngLat from,
            DeliveryPathResponse.LngLat to,
            List<RestrictedArea> restrictedAreas) {

        logger.debug("Starting A* pathfinding from ({}, {}) to ({}, {})",
                from.getLng(), from.getLat(), to.getLng(), to.getLat());

        // Priority queue ordered by f(n) = g(n) + h(n)
        PriorityQueue<AStarNode> openSet = new PriorityQueue<>(
                Comparator.comparingDouble(n -> n.f)
        );

        Set<String> closedSet = new HashSet<>();
        Map<String, AStarNode> allNodes = new HashMap<>();

        // Initialize start node
        AStarNode startNode = new AStarNode(from.getLng(), from.getLat());
        startNode.g = 0;
        startNode.h = calculateEuclideanDistance(from.getLng(), from.getLat(), to.getLng(), to.getLat());
        startNode.f = startNode.g + startNode.h;

        openSet.add(startNode);
        allNodes.put(startNode.getKey(), startNode);

        int iterations = 0;
        int maxIterations = 100000; // Safety limit

        while (!openSet.isEmpty() && iterations < maxIterations) {
            iterations++;

            AStarNode current = openSet.poll();

            // Check if goal reached (within 0.00015 degree tolerance)
            if (isGoalReached(current, to)) {
                logger.debug("A* found path in {} iterations", iterations);
                return reconstructPath(current);
            }

            closedSet.add(current.getKey());

            // Explore all 16 compass directions
            for (AStarNode neighbor : getValidNeighbors(current, to, restrictedAreas)) {
                if (closedSet.contains(neighbor.getKey())) {
                    continue;
                }

                // Calculate tentative g score (cost from start to neighbor)
                double tentativeG = current.g + 0.00015; // Each move is 0.00015 degrees

                AStarNode existingNode = allNodes.get(neighbor.getKey());

                if (existingNode == null || tentativeG < existingNode.g) {
                    neighbor.g = tentativeG;
                    neighbor.h = calculateEuclideanDistance(
                            neighbor.lng, neighbor.lat, to.getLng(), to.getLat()
                    );
                    neighbor.f = neighbor.g + neighbor.h;
                    neighbor.parent = current;

                    if (existingNode == null) {
                        openSet.add(neighbor);
                        allNodes.put(neighbor.getKey(), neighbor);
                    } else {
                        // Update existing node
                        existingNode.g = neighbor.g;
                        existingNode.h = neighbor.h;
                        existingNode.f = neighbor.f;
                        existingNode.parent = neighbor.parent;
                        // Re-add to priority queue with updated priority
                        openSet.remove(existingNode);
                        openSet.add(existingNode);
                    }
                }
            }
        }

        logger.warn("A* failed to find path after {} iterations", iterations);
        return null; // No path found
    }

    private List<AStarNode> getValidNeighbors(
            AStarNode current,
            DeliveryPathResponse.LngLat goal,
            List<RestrictedArea> restrictedAreas) {

        List<AStarNode> neighbors = new ArrayList<>();
        double step = 0.00015; // Fixed step size per specification

        // 16 compass directions: 0°, 22.5°, 45°, 67.5°, 90°, ..., 337.5°
        // Convention: 0° = East, 90° = North, 180° = West, 270° = South
        double[] angles = {0, 22.5, 45, 67.5, 90, 112.5, 135, 157.5, 180, 202.5, 225, 247.5, 270, 292.5, 315, 337.5};

        for (double angleDeg : angles) {
            double angleRad = Math.toRadians(angleDeg);

            // Calculate new position
            // Using standard trigonometry: dx = cos(angle), dy = sin(angle)
            double newLng = current.lng + step * Math.cos(angleRad);
            double newLat = current.lat + step * Math.sin(angleRad);

            // Create neighbor node
            AStarNode neighbor = new AStarNode(newLng, newLat);

            // Validate neighbor
            if (isValidMove(current.lng, current.lat, newLng, newLat, restrictedAreas)) {
                neighbors.add(neighbor);
            }
        }

        return neighbors;
    }

    private boolean isValidMove(
            double lng1, double lat1,
            double lng2, double lat2,
            List<RestrictedArea> restrictedAreas) {

        // Check if endpoint is in restricted area
        for (RestrictedArea area : restrictedAreas) {
            if (isPointInOrNearPolygon(lng2, lat2, area.getVertices())) {
                return false;
            }

            // Check if line segment crosses the restricted area
            if (doesLineIntersectPolygon(lng1, lat1, lng2, lat2, area.getVertices())) {
                return false;
            }
        }

        return true;
    }

    private boolean isPointInOrNearPolygon(
            double lng, double lat,
            List<RestrictedArea.Vertex> vertices) {

        if (vertices == null || vertices.size() < 3) {
            return false;
        }

        // Buffer distance to prevent corner cutting
        double buffer = 0.00015;

        // First check if point is inside polygon
        if (isPointInPolygon(lng, lat, vertices)) {
            return true;
        }

        // Check if point is too close to any edge
        for (int i = 0; i < vertices.size(); i++) {
            RestrictedArea.Vertex v1 = vertices.get(i);
            RestrictedArea.Vertex v2 = vertices.get((i + 1) % vertices.size());

            if (v1.getLng() == null || v1.getLat() == null ||
                    v2.getLng() == null || v2.getLat() == null) {
                continue;
            }

            double distToEdge = pointToLineDistance(
                    lng, lat,
                    v1.getLng(), v1.getLat(),
                    v2.getLng(), v2.getLat()
            );

            if (distToEdge < buffer) {
                return true; // Too close to edge
            }
        }

        return false;
    }

    private double pointToLineDistance(
            double px, double py,
            double x1, double y1,
            double x2, double y2) {

        double A = px - x1;
        double B = py - y1;
        double C = x2 - x1;
        double D = y2 - y1;

        double dot = A * C + B * D;
        double lenSq = C * C + D * D;

        double param = (lenSq != 0) ? (dot / lenSq) : -1;

        double xx, yy;

        if (param < 0) {
            xx = x1;
            yy = y1;
        } else if (param > 1) {
            xx = x2;
            yy = y2;
        } else {
            xx = x1 + param * C;
            yy = y1 + param * D;
        }

        double dx = px - xx;
        double dy = py - yy;

        return Math.sqrt(dx * dx + dy * dy);
    }

    private boolean doesLineIntersectPolygon(double x1, double y1, double x2, double y2, List<RestrictedArea.Vertex> vertices) {

        if (vertices == null || vertices.size() < 3) {
            return false;
        }

        // Check intersection with each edge of the polygon
        for (int i = 0; i < vertices.size(); i++) {
            RestrictedArea.Vertex v1 = vertices.get(i);
            RestrictedArea.Vertex v2 = vertices.get((i + 1) % vertices.size());

            if (v1.getLng() == null || v1.getLat() == null ||
                    v2.getLng() == null || v2.getLat() == null) {
                continue;
            }

            if (doLineSegmentsIntersect(
                    x1, y1, x2, y2,
                    v1.getLng(), v1.getLat(),
                    v2.getLng(), v2.getLat())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if two line segments intersect
     * Uses cross product method
     */
    private boolean doLineSegmentsIntersect(
            double x1, double y1, double x2, double y2,
            double x3, double y3, double x4, double y4) {

        double d = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);

        if (Math.abs(d) < 1e-10) {
            return false; // Parallel lines
        }

        double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / d;
        double u = -((x1 - x2) * (y1 - y3) - (y1 - y2) * (x1 - x3)) / d;

        return (t >= 0 && t <= 1 && u >= 0 && u <= 1);
    }

    /**
     * Check if goal is reached (within 0.00015 degree tolerance)
     */
    private boolean isGoalReached(AStarNode node, DeliveryPathResponse.LngLat goal) {
        double distance = calculateEuclideanDistance(
                node.lng, node.lat, goal.getLng(), goal.getLat()
        );
        return distance < 0.00015; // Within one move distance
    }

    /**
     * Reconstruct path from goal to start by following parent pointers
     */
    private List<DeliveryPathResponse.LngLat> reconstructPath(AStarNode goal) {
        List<DeliveryPathResponse.LngLat> path = new ArrayList<>();
        AStarNode current = goal;

        while (current != null) {
            path.add(new DeliveryPathResponse.LngLat(current.lng, current.lat));
            current = current.parent;
        }

        Collections.reverse(path);

        logger.debug("Reconstructed path with {} points", path.size());
        return path;
    }
}