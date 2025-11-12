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

        // find available drones that can handle ALL dispatches in a single flight
        List<Integer> availableDroneIds = queryAvailableDrones(dispatches);

        logger.info("Found {} drones that can handle all dispatches in single flight", availableDroneIds.size());

        try {
            // STRATEGY 1: Try single drone solution first (most efficient)
            // Note: Same drone CAN deliver on multiple days - it returns to service point each day
            DeliveryPathResponse bestResponse = null;
            int bestMoves = Integer.MAX_VALUE;

            // Try single-drone solution if there are drones that can handle all dispatches
            if (!availableDroneIds.isEmpty()) {
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

                    // Select best solution: 1) Fewer moves wins, 2) Same moves -> lower cost wins
                    if (response != null) {
                        boolean isBetter = false;
                        if (response.getTotalMoves() < bestMoves) {
                            isBetter = true;
                        } else if (response.getTotalMoves() == bestMoves &&
                                   (bestResponse == null || response.getTotalCost() < bestResponse.getTotalCost())) {
                            isBetter = true;
                        }

                        if (isBetter) {
                            bestMoves = response.getTotalMoves();
                            bestResponse = response;
                            logger.info("New best single-drone path found: {} moves, cost {} using drone {} at service point {}",
                                    bestMoves, String.format("%.2f", response.getTotalCost()), drone.getId(), sp.getName());
                        }
                    }
                }
                }
            }

            // Check if dispatches span multiple dates
            Set<LocalDate> uniqueDates = dispatches.stream()
                    .map(MedDispatchRec::getDate)
                    .collect(Collectors.toSet());

            boolean hasMultipleDates = uniqueDates.size() > 1;

            // If single drone solution found
            if (bestResponse != null) {
                logger.info("Single drone solution found - Cost: {}, Moves: {}",
                        bestResponse.getTotalCost(), bestResponse.getTotalMoves());

                // If only one date, return immediately - no need to try multi-drone
                if (!hasMultipleDates) {
                    logger.info("✓ Single date scenario - returning single-drone solution");
                    return bestResponse;
                }

                // Multiple dates: Compare with using different drones per date
                logger.info("Multiple dates detected ({}). Comparing single-drone (reused across days) vs multi-drone (different drones per date)",
                        uniqueDates.size());
            }

            // STRATEGY 2: Try multi-drone solution
            // Multi-drone is needed for:
            // 1. Conflicting requirements (cooling vs heating) when no drone has both
            // 2. MaxMoves exceeded - need multiple drones to split deliveries
            // 3. Multiple dates - might be more efficient to use different drones per date

            if (bestResponse == null) {
                logger.info("Single-drone failed, trying multi-drone solution");
            }

            // For multi-drone, we need ALL available drones
            List<Integer> allAvailableDroneIds = allDrones.stream()
                    .map(Drone::getId)
                    .collect(Collectors.toList());

            DeliveryPathResponse multiDroneResponse = calculateMultiDronePath(
                    dispatches, allDrones, servicePoints, droneAvailability, allAvailableDroneIds, restrictedAreas);

            // Compare solutions: 1) Fewer moves wins, 2) Same moves -> lower cost wins
            if (bestResponse != null && multiDroneResponse != null) {
                if (bestResponse.getTotalMoves() < multiDroneResponse.getTotalMoves()) {
                    logger.info("✓ Choosing single-drone (reused across {} days): {} moves < {} moves (multi-drone)",
                            uniqueDates.size(), bestResponse.getTotalMoves(), multiDroneResponse.getTotalMoves());
                    return bestResponse;
                } else if (bestResponse.getTotalMoves().equals(multiDroneResponse.getTotalMoves())) {
                    // Same moves - compare cost
                    if (bestResponse.getTotalCost() <= multiDroneResponse.getTotalCost()) {
                        logger.info("✓ Choosing single-drone: {} moves (cost: ${} ≤ ${} multi-drone)",
                                bestResponse.getTotalMoves(),
                                String.format("%.2f", bestResponse.getTotalCost()),
                                String.format("%.2f", multiDroneResponse.getTotalCost()));
                        return bestResponse;
                    } else {
                        logger.info("✓ Choosing multi-drone: {} moves (cost: ${} < ${} single-drone)",
                                multiDroneResponse.getTotalMoves(),
                                String.format("%.2f", multiDroneResponse.getTotalCost()),
                                String.format("%.2f", bestResponse.getTotalCost()));
                        return multiDroneResponse;
                    }
                } else {
                    logger.info("✓ Choosing multi-drone (different drones per date): {} moves < {} moves (single-drone)",
                            multiDroneResponse.getTotalMoves(), bestResponse.getTotalMoves());
                    return multiDroneResponse;
                }
            } else if (bestResponse != null) {
                logger.info("✓ Using single-drone solution (multi-drone failed)");
                return bestResponse;
            } else if (multiDroneResponse != null) {
                logger.info("✓ Using multi-drone solution (single-drone not possible)");
                return multiDroneResponse;
            }

            logger.warn("No valid delivery path found (neither single nor multi-drone)");
            return null;

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
     * IMPORTANT:
     * 1. Total capacity of all dispatches must not exceed drone capacity
     * 2. If dispatches require both cooling AND heating, the drone must have BOTH capabilities
     */
    private boolean fulfillAllDispatches(Drone drone, List<MedDispatchRec> dispatches, List<DroneServicePointAvailability> droneAvailability, List<RestrictedArea> restrictedAreas) {
        if (drone.getCapability() == null) {
            logger.warn("Drone {} has no capability", drone.getName());
            return false;
        }

        // Check total capacity for all dispatches combined
        double totalCapacity = dispatches.stream()
                .mapToDouble(d -> d.getRequirements().getCapacity())
                .sum();

        if (totalCapacity > drone.getCapability().getCapacity()) {
            logger.debug("Drone {} cannot fulfill all dispatches: total capacity {} exceeds drone capacity {}",
                    drone.getId(), totalCapacity, drone.getCapability().getCapacity());
            return false;
        }

        // Check if dispatches require both cooling AND heating
        boolean needsCooling = dispatches.stream()
                .anyMatch(d -> Boolean.TRUE.equals(d.getRequirements().getCooling()));
        boolean needsHeating = dispatches.stream()
                .anyMatch(d -> Boolean.TRUE.equals(d.getRequirements().getHeating()));

        // If both are needed, drone must have BOTH capabilities
        if (needsCooling && needsHeating) {
            boolean hasBothCapabilities = Boolean.TRUE.equals(drone.getCapability().getCooling())
                    && Boolean.TRUE.equals(drone.getCapability().getHeating());

            if (!hasBothCapabilities) {
                logger.debug("Drone {} cannot fulfill deliveries requiring both cooling and heating (drone has: cooling={}, heating={})",
                        drone.getId(), drone.getCapability().getCooling(), drone.getCapability().getHeating());
                return false;
            }

            logger.debug("Drone {} can fulfill deliveries requiring both cooling and heating", drone.getId());
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

        // Group dispatches by date and sort by date
        Map<LocalDate, List<MedDispatchRec>> dispatchByDate = dispatches.stream()
                .collect(Collectors.groupingBy(MedDispatchRec::getDate));

        List<DeliveryPathResponse.Delivery> allDeliveries = new ArrayList<>();
        int totalMoves = 0;

        // Process each day's dispatches separately, IN DATE ORDER
        List<LocalDate> sortedDates = dispatchByDate.keySet().stream()
                .sorted()
                .collect(Collectors.toList());

        for (LocalDate date : sortedDates) {
            List<MedDispatchRec> dailyDispatches = dispatchByDate.get(date);

            logger.debug("Processing {} dispatches for date {}", dailyDispatches.size(), date);

            // Optimize delivery order (respects time ordering within the day)
            List<MedDispatchRec> optimiseOrder = optimizeDeliveryOrder(servicePoint, dailyDispatches);

            // Generate flight path for this day's deliveries
            DeliveryPathResponse.LngLat currentLocation = new DeliveryPathResponse.LngLat(servicePoint.getLocation().getLng(), servicePoint.getLocation().getLat());

            for (int i = 0; i < optimiseOrder.size(); i++) {
                MedDispatchRec dispatch = optimiseOrder.get(i);

                // Validate capacity for this dispatch
                Double droneCapacity = drone.getCapability().getCapacity();
                Double requiredCapacity = dispatch.getRequirements().getCapacity();
                if (droneCapacity != null && requiredCapacity != null) {
                    if (requiredCapacity > droneCapacity) {
                        logger.debug("Dispatch {} requires capacity {} but drone {} only has {}",
                                dispatch.getId(), requiredCapacity, drone.getId(), droneCapacity);
                        return null;
                    }
                }

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

                // calculate moves (INCLUDING hover - identical consecutive points count as 1 move)
                // Each step in the path counts as 1 move, including the hover
                int movesForThisDelivery = path.size() - 1; // Total positions minus 1 = total moves
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

        // Check if endpoints are in or near restricted areas
        for (RestrictedArea area : restrictedAreas) {
            if (isPointInOrNearPolygon(from.getLng(), from.getLat(), area.getVertices()) ||
                    isPointInOrNearPolygon(to.getLng(), to.getLat(), area.getVertices())) {
                return false;
            }

            // Check if the line segment crosses the restricted area
            if (doesLineIntersectPolygon(from.getLng(), from.getLat(), to.getLng(), to.getLat(), area.getVertices())) {
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

    /**
     * A* pathfinding algorithm to find path from 'from' to 'to' avoiding restricted areas
     */
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
        int maxIterations = 50000; // Safety limit

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

    /**
     * Calculate multi-drone delivery path when single drone solution fails.
     * Handles conflicting requirements (cooling/heating), maxMoves constraints, and multiple service points.
     */
    private DeliveryPathResponse calculateMultiDronePath(
            List<MedDispatchRec> dispatches,
            List<Drone> allDrones,
            List<ServicePoint> servicePoints,
            List<DroneServicePointAvailability> droneAvailability,
            List<Integer> availableDroneIds,
            List<RestrictedArea> restrictedAreas) {

        logger.info("Starting multi-drone path calculation for {} deliveries", dispatches.size());

        // Step 0: Validate that no dispatch exceeds maximum available drone capacity
        // If any dispatch exceeds capacity, we CANNOT split it - return null
        double maxAvailableCapacity = allDrones.stream()
                .filter(d -> availableDroneIds.contains(d.getId()))
                .filter(d -> d.getCapability() != null && d.getCapability().getCapacity() != null)
                .mapToDouble(d -> d.getCapability().getCapacity())
                .max()
                .orElse(0.0);

        for (MedDispatchRec dispatch : dispatches) {
            Double requiredCapacity = dispatch.getRequirements().getCapacity();
            if (requiredCapacity != null && requiredCapacity > maxAvailableCapacity) {
                logger.error("Dispatch {} requires capacity {} which exceeds maximum available drone capacity {}. Cannot split - returning null.",
                        dispatch.getId(), requiredCapacity, maxAvailableCapacity);
                return null;
            }
        }

        // Partition by conflicting requirements (cooling/heating/standard)
        // This is the ONLY reason we need multi-drone: conflicting requirements or maxMoves exceeded
        Map<String, List<MedDispatchRec>> partitionMap = partitionByRequirements(dispatches);

        logger.debug("Partitioned into: {} cooling, {} heating, {} standard",
                partitionMap.get("cooling").size(),
                partitionMap.get("heating").size(),
                partitionMap.get("standard").size());

        List<DeliveryPathResponse.DronePath> allDronePaths = new ArrayList<>();
        double totalCost = 0.0;
        int totalMoves = 0;
        Set<Integer> usedDroneIds = new HashSet<>();

        // Process each partition separately
        for (Map.Entry<String, List<MedDispatchRec>> entry : partitionMap.entrySet()) {
            String requirementType = entry.getKey();
            List<MedDispatchRec> partition = entry.getValue();

            if (partition.isEmpty()) continue;

            logger.debug("Processing {} partition with {} deliveries", requirementType, partition.size());

            // Group by date
            Map<LocalDate, List<MedDispatchRec>> byDate = partition.stream()
                    .collect(Collectors.groupingBy(MedDispatchRec::getDate));

            for (Map.Entry<LocalDate, List<MedDispatchRec>> dateEntry : byDate.entrySet()) {
                LocalDate date = dateEntry.getKey();
                List<MedDispatchRec> dailyDispatches = dateEntry.getValue();
                dailyDispatches.sort(Comparator.comparing(MedDispatchRec::getTime));

                logger.debug("Processing {} {} dispatches for {}", dailyDispatches.size(), requirementType, date);

                // Find drones that can handle this requirement type
                List<Integer> suitableDroneIds = filterDronesByRequirement(
                        availableDroneIds, allDrones, requirementType, usedDroneIds,
                        dailyDispatches, droneAvailability);

                logger.debug("Found {} suitable drones for {} requirement: {}",
                        suitableDroneIds.size(), requirementType, suitableDroneIds);

                if (suitableDroneIds.isEmpty()) {
                    logger.error("No suitable drones for {} requirement", requirementType);
                    return null;
                }

                // Try single drone solution first for this partition
                DeliveryPathResponse singleDroneResult = trySingleDroneSolution(
                        dailyDispatches, suitableDroneIds, allDrones, servicePoints,
                        droneAvailability, restrictedAreas, new HashSet<>(usedDroneIds));

                if (singleDroneResult != null) {
                    logger.info("Single-drone handles {} partition: {} moves, cost: {}",
                            requirementType, singleDroneResult.getTotalMoves(),
                            String.format("%.2f", singleDroneResult.getTotalCost()));
                }

                // If single drone fails (maxMoves exceeded), split into batches
                DeliveryPathResponse multiDroneResult = null;
                if (singleDroneResult == null) {
                    logger.info("Single-drone failed (likely maxMoves exceeded), trying multi-drone batches");
                    multiDroneResult = splitIntoBatches(
                            dailyDispatches, suitableDroneIds, allDrones, servicePoints,
                            droneAvailability, restrictedAreas, new HashSet<>(usedDroneIds));
                }

                // Step 7: Pick the best solution (prefer single drone if available, otherwise use multi)
                DeliveryPathResponse bestResult = singleDroneResult != null ? singleDroneResult : multiDroneResult;

                if (bestResult == null) {
                    logger.error("Failed to assign {} {} dispatches - no valid solution found",
                            dailyDispatches.size(), requirementType);
                    return null;
                }

                logger.info("✓ Using {} solution: {} moves, {} drones, cost: {}",
                        singleDroneResult != null ? "single-drone" : "multi-drone",
                        bestResult.getTotalMoves(),
                        bestResult.getDronePaths().size(),
                        String.format("%.2f", bestResult.getTotalCost()));

                // Update used drones based on best result
                bestResult.getDronePaths().forEach(path -> usedDroneIds.add(path.getDroneId()));

                allDronePaths.addAll(bestResult.getDronePaths());
                totalCost += bestResult.getTotalCost();
                totalMoves += bestResult.getTotalMoves();
            }
        }

        DeliveryPathResponse response = new DeliveryPathResponse();
        response.setTotalCost(totalCost);
        response.setTotalMoves(totalMoves);
        response.setDronePaths(allDronePaths);

        logger.info("Multi-drone solution: {} drones, {} moves, cost {}, Drones used: {}",
                usedDroneIds.size(), totalMoves, String.format("%.2f", totalCost), usedDroneIds);


        return response;
    }


    /**
     * Check if drone meets dispatch requirements (cooling/heating)
     */
    private boolean meetsRequirements(Drone drone, MedDispatchRec dispatch) {
        MedDispatchRec.Requirements req = dispatch.getRequirements();
        Drone.Capability cap = drone.getCapability();

        if (Boolean.TRUE.equals(req.getCooling()) && !Boolean.TRUE.equals(cap.getCooling())) {
            return false;
        }
        if (Boolean.TRUE.equals(req.getHeating()) && !Boolean.TRUE.equals(cap.getHeating())) {
            return false;
        }
        return true;
    }

    /**
     * Check if drone is available at a specific day/time
     */
    private boolean isDroneAvailableAtTime(
            Integer droneId,
            DayOfWeek dayOfWeek,
            LocalTime time,
            List<DroneServicePointAvailability> droneAvailability) {

        String dayOfWeekStr = dayOfWeek.toString();

        for (DroneServicePointAvailability sp : droneAvailability) {
            if (sp.getDrones() == null) continue;

            for (DroneServicePointAvailability.DroneAvailability da : sp.getDrones()) {
                if (!String.valueOf(droneId).equals(da.getId())) continue;
                if (da.getAvailability() == null) continue;

                for (DroneServicePointAvailability.TimeSlot ts : da.getAvailability()) {
                    if (!dayOfWeekStr.equalsIgnoreCase(ts.getDayOfWeek())) continue;

                    LocalTime from = ts.getFrom();
                    LocalTime until = ts.getUntil();

                    if (from != null && until != null) {
                        if (!time.isBefore(from) && !time.isAfter(until)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Partition dispatches by requirement type (cooling/heating/standard)
     */
    private Map<String, List<MedDispatchRec>> partitionByRequirements(List<MedDispatchRec> dispatches) {
        Map<String, List<MedDispatchRec>> partitions = new HashMap<>();
        partitions.put("cooling", new ArrayList<>());
        partitions.put("heating", new ArrayList<>());
        partitions.put("standard", new ArrayList<>());

        for (MedDispatchRec d : dispatches) {
            if (Boolean.TRUE.equals(d.getRequirements().getCooling())) {
                partitions.get("cooling").add(d);
            } else if (Boolean.TRUE.equals(d.getRequirements().getHeating())) {
                partitions.get("heating").add(d);
            } else {
                partitions.get("standard").add(d);
            }
        }

        return partitions;
    }


    /**
     * Filter drones by requirement type and exclude already used ones.
     * Also checks basic capability (capacity) for at least one dispatch.
     */
    private List<Integer> filterDronesByRequirement(
            List<Integer> droneIds,
            List<Drone> allDrones,
            String requirementType,
            Set<Integer> usedDroneIds,
            List<MedDispatchRec> dispatches,
            List<DroneServicePointAvailability> droneAvailability) {

        logger.debug("Filtering {} drones for {} requirement, {} already used",
                droneIds.size(), requirementType, usedDroneIds.size());

        List<Integer> result = droneIds.stream()
                .filter(id -> !usedDroneIds.contains(id))
                .filter(id -> {
                    Drone drone = allDrones.stream()
                            .filter(d -> d.getId().equals(id))
                            .findFirst()
                            .orElse(null);

                    if (drone == null || drone.getCapability() == null) {
                        logger.debug("Drone {} has no capability", id);
                        return false;
                    }

                    Drone.Capability cap = drone.getCapability();

                    // Check if drone has required temperature control
                    boolean hasRequiredCapability;
                    switch (requirementType) {
                        case "cooling":
                            hasRequiredCapability = Boolean.TRUE.equals(cap.getCooling());
                            if (!hasRequiredCapability) {
                                logger.debug("Drone {} cooling={}, need cooling=true",
                                        id, cap.getCooling());
                            }
                            break;
                        case "heating":
                            hasRequiredCapability = Boolean.TRUE.equals(cap.getHeating());
                            if (!hasRequiredCapability) {
                                logger.debug("Drone {} heating={}, need heating=true",
                                        id, cap.getHeating());
                            }
                            break;
                        case "standard":
                            hasRequiredCapability = true;
                            break;
                        default:
                            hasRequiredCapability = false;
                    }

                    if (!hasRequiredCapability) return false;

                    // Check if drone is available for ALL dispatches' date/time
                    boolean availableForAll = true;
                    for (MedDispatchRec dispatch : dispatches) {
                        if (!isDroneAvailableForDispatch(id, dispatch, droneAvailability)) {
                            // Log once for first unavailable dispatch
                            logger.debug("Drone {} not available for dispatch {} on {} at {}",
                                    id, dispatch.getId(), dispatch.getDate(), dispatch.getTime());
                            availableForAll = false;
                            break; // No need to check remaining dispatches
                        }
                    }

                    return availableForAll;
                })
                .collect(Collectors.toList());

        logger.debug("After filtering: {} suitable drones", result.size());
        return result;
    }

    /**
     * Check if drone is available for a specific dispatch
     */
    private boolean isDroneAvailableForDispatch(Integer droneId, MedDispatchRec dispatch,
                                                 List<DroneServicePointAvailability> droneAvailability) {
        if (dispatch.getDate() == null || dispatch.getTime() == null) {
            return true; // No time constraint
        }
        return isAvailableAtTime(droneId, dispatch.getDate(), dispatch.getTime(), droneAvailability);
    }

    /**
     * Try to assign all dispatches to a single drone
     */
    private DeliveryPathResponse trySingleDroneSolution(
            List<MedDispatchRec> dispatches,
            List<Integer> suitableDroneIds,
            List<Drone> allDrones,
            List<ServicePoint> servicePoints,
            List<DroneServicePointAvailability> droneAvailability,
            List<RestrictedArea> restrictedAreas,
            Set<Integer> usedDroneIds) {

        DeliveryPathResponse bestResult = null;
        int bestMoves = Integer.MAX_VALUE;
        Integer bestDroneId = null;

        // Try all suitable drones and pick the one with minimum moves
        for (ServicePoint sp : servicePoints) {
            List<Integer> spDroneIds = getDroneIdsAtServicePoint(
                    sp.getId(), droneAvailability, suitableDroneIds);

            for (Integer droneId : spDroneIds) {
                Drone drone = allDrones.stream()
                        .filter(d -> d.getId().equals(droneId))
                        .findFirst()
                        .orElse(null);

                if (drone == null) continue;

                // Check if drone is available for all dispatches' date/time
                boolean availableForAll = true;
                for (MedDispatchRec dispatch : dispatches) {
                    if (!isDroneAvailableForDispatch(droneId, dispatch, droneAvailability)) {
                        availableForAll = false;
                        break;
                    }
                }

                if (!availableForAll) continue;

                // Calculate path for this drone
                DeliveryPathResponse result = calculatePathForDrone(
                        drone, sp, dispatches, restrictedAreas);

                if (result != null) {
                    // Compare: 1) Fewer moves wins, 2) Same moves -> lower cost wins
                    boolean isBetter = false;
                    if (result.getTotalMoves() < bestMoves) {
                        isBetter = true;
                    } else if (result.getTotalMoves() == bestMoves &&
                               (bestResult == null || result.getTotalCost() < bestResult.getTotalCost())) {
                        isBetter = true;
                    }

                    if (isBetter) {
                        bestResult = result;
                        bestMoves = result.getTotalMoves();
                        bestDroneId = droneId;
                        logger.debug("Found better drone {} at {} with {} moves (cost: {})",
                                droneId, sp.getName(), result.getTotalMoves(),
                                String.format("%.2f", result.getTotalCost()));
                    }
                }
            }
        }

        if (bestResult != null) {
            logger.info("Successfully assigned {} dispatches to single drone {} with minimum {} moves (cost: {})",
                    dispatches.size(), bestDroneId, bestMoves, String.format("%.2f", bestResult.getTotalCost()));
            usedDroneIds.add(bestDroneId);
        }

        return bestResult;
    }



    /**
     * Try assigning dispatches using drones from multiple service points (globally cheapest)
     */
    private DeliveryPathResponse tryMixedServicePoints(
            List<MedDispatchRec> dispatches,
            List<Integer> suitableDroneIds,
            List<Drone> allDrones,
            List<ServicePoint> servicePoints,
            List<DroneServicePointAvailability> droneAvailability,
            List<RestrictedArea> restrictedAreas,
            Set<Integer> usedDroneIds) {

        List<Integer> availableDroneIds = new ArrayList<>(suitableDroneIds);
        availableDroneIds.removeAll(usedDroneIds);

        if (availableDroneIds.size() < dispatches.size()) {
            return null;
        }

        // Map each drone to its service point
        Map<Integer, ServicePoint> droneToServicePoint = new HashMap<>();
        for (ServicePoint sp : servicePoints) {
            List<Integer> spDroneIds = getDroneIdsAtServicePoint(sp.getId(), droneAvailability, availableDroneIds);
            for (Integer droneId : spDroneIds) {
                droneToServicePoint.put(droneId, sp);
            }
        }

        // Group by capacity
        Map<Double, List<MedDispatchRec>> byCapacity = new HashMap<>();
        for (MedDispatchRec dispatch : dispatches) {
            byCapacity.computeIfAbsent(dispatch.getRequirements().getCapacity(), k -> new ArrayList<>()).add(dispatch);
        }

        // Find capable drones across ALL service points, sorted by estimated moves (minimize moves is the goal)
        Map<Double, List<DroneWithServicePoint>> dronesByCapacity = new HashMap<>();
        for (Double requiredCap : byCapacity.keySet()) {
            List<DroneWithServicePoint> capableDrones = availableDroneIds.stream()
                    .map(id -> {
                        Drone drone = allDrones.stream().filter(d -> d.getId().equals(id)).findFirst().orElse(null);
                        ServicePoint sp = droneToServicePoint.get(id);
                        return (drone != null && sp != null) ? new DroneWithServicePoint(drone, sp) : null;
                    })
                    .filter(Objects::nonNull)
                    .filter(dsp -> dsp.drone.getCapability() != null && dsp.drone.getCapability().getCapacity() >= requiredCap)
                    .sorted(Comparator.comparingDouble(dsp -> estimateDroneMoves(dsp.drone, dsp.servicePoint, dispatches.get(0).getDelivery())))
                    .collect(Collectors.toList());

            if (capableDrones.size() < byCapacity.get(requiredCap).size()) {
                return null;
            }
            dronesByCapacity.put(requiredCap, capableDrones);
        }

        // Assign cheapest available drone for each dispatch
        List<DeliveryPathResponse.DronePath> paths = new ArrayList<>();
        double totalCost = 0.0;
        int totalMoves = 0;
        Set<Integer> assignedDrones = new HashSet<>();
        List<Integer> dronesCombination = new ArrayList<>();
        List<String> droneServicePointInfo = new ArrayList<>();

        for (MedDispatchRec dispatch : dispatches) {
            Double requiredCap = dispatch.getRequirements().getCapacity();
            List<DroneWithServicePoint> capableDrones = dronesByCapacity.get(requiredCap);

            DroneWithServicePoint selectedDsp = null;
            for (DroneWithServicePoint dsp : capableDrones) {
                if (!assignedDrones.contains(dsp.drone.getId())) {
                    selectedDsp = dsp;
                    break;
                }
            }

            if (selectedDsp == null) {
                return null;
            }

            DeliveryPathResponse result = calculatePathForDrone(
                    selectedDsp.drone, selectedDsp.servicePoint, Arrays.asList(dispatch), restrictedAreas);

            if (result == null) {
                return null;
            }

            paths.addAll(result.getDronePaths());
            totalCost += result.getTotalCost();
            totalMoves += result.getTotalMoves();
            assignedDrones.add(selectedDsp.drone.getId());
            dronesCombination.add(selectedDsp.drone.getId());
            droneServicePointInfo.add(selectedDsp.drone.getId() + "@" + selectedDsp.servicePoint.getName());
        }

        logger.info("Greedy selection result: Drones {} from {} - Moves: {}, Cost: {}",
                dronesCombination, droneServicePointInfo, totalMoves, String.format("%.2f", totalCost));

        DeliveryPathResponse solution = new DeliveryPathResponse();
        solution.setDronePaths(paths);
        solution.setTotalCost(totalCost);
        solution.setTotalMoves(totalMoves);

        return solution;
    }

    /**
     * Helper class to pair a drone with its service point
     */
    private static class DroneWithServicePoint {
        Drone drone;
        ServicePoint servicePoint;

        DroneWithServicePoint(Drone drone, ServicePoint servicePoint) {
            this.drone = drone;
            this.servicePoint = servicePoint;
        }
    }


    /**
     * Estimate the number of moves for a drone to make a delivery to a specific location.
     * Used for sorting drones to minimize total moves (prime goal per instructor).
     */
    private double estimateDroneMoves(Drone drone, ServicePoint sp, MedDispatchRec.Delivery delivery) {
        Drone.Capability cap = drone.getCapability();
        if (cap == null) return Double.MAX_VALUE;

        // Estimate distance
        double distance = calculateEuclideanDistance(
                sp.getLocation().getLng(), sp.getLocation().getLat(),
                delivery.getLng(), delivery.getLat());

        // Estimate moves (round trip)
        int estimatedMoves = (int) Math.ceil(distance / 0.00015) * 2;

        return estimatedMoves;
    }

    /**
     * Estimate the cost for a drone to make a delivery to a specific location.
     * Used for sorting drones by cost efficiency.
     */
    private double estimateDroneCost(Drone drone, ServicePoint sp, MedDispatchRec.Delivery delivery) {
        Drone.Capability cap = drone.getCapability();
        if (cap == null) return Double.MAX_VALUE;

        // Estimate distance
        double distance = calculateEuclideanDistance(
                sp.getLocation().getLng(), sp.getLocation().getLat(),
                delivery.getLng(), delivery.getLat());

        // Estimate moves (round trip)
        int estimatedMoves = (int) Math.ceil(distance / 0.00015) * 2;

        // Estimate cost
        double costInitial = cap.getCostInitial() != null ? cap.getCostInitial() : 0.0;
        double costPerMove = cap.getCostPerMove() != null ? cap.getCostPerMove() : 0.0;
        double costFinal = cap.getCostFinal() != null ? cap.getCostFinal() : 0.0;

        return costInitial + (costPerMove * estimatedMoves) + costFinal;
    }

    /**
     * Generate all permutations of selecting k items from a list
     */
    private List<List<Integer>> generatePermutations(List<Integer> items, int k) {
        List<List<Integer>> result = new ArrayList<>();
        if (k > items.size()) return result;

        generatePermutationsHelper(items, k, new ArrayList<>(), new HashSet<>(), result);
        return result;
    }

    private void generatePermutationsHelper(List<Integer> items, int k, List<Integer> current,
                                           Set<Integer> used, List<List<Integer>> result) {
        if (current.size() == k) {
            result.add(new ArrayList<>(current));
            return;
        }

        for (Integer item : items) {
            if (used.contains(item)) continue;

            current.add(item);
            used.add(item);
            generatePermutationsHelper(items, k, current, used, result);
            current.remove(current.size() - 1);
            used.remove(item);
        }
    }

    /**
     * Split dispatches into batches and assign to multiple drones
     */
    private DeliveryPathResponse splitIntoBatches(
            List<MedDispatchRec> dispatches,
            List<Integer> suitableDroneIds,
            List<Drone> allDrones,
            List<ServicePoint> servicePoints,
            List<DroneServicePointAvailability> droneAvailability,
            List<RestrictedArea> restrictedAreas,
            Set<Integer> usedDroneIds) {

        logger.info("splitIntoBatches called with {} dispatches (IDs: {})",
                dispatches.size(),
                dispatches.stream().map(d -> d.getId() + ":" + d.getRequirements().getCapacity()).collect(Collectors.toList()));

        // Find best service point (closest to deliveries)
        ServicePoint bestSp = findClosestServicePoint(dispatches, servicePoints);

        List<DeliveryPathResponse.DronePath> allPaths = new ArrayList<>();
        double totalCost = 0.0;
        int totalMoves = 0;
        List<MedDispatchRec> remaining = new ArrayList<>(dispatches);

        while (!remaining.isEmpty()) {
            // Get available drones at service point
            List<Integer> spDroneIds = getDroneIdsAtServicePoint(
                    bestSp.getId(), droneAvailability, suitableDroneIds);
            spDroneIds.removeAll(usedDroneIds);

            if (spDroneIds.isEmpty()) {
                logger.error("No more drones available at service point {}", bestSp.getName());
                return null;
            }

            // Find best drone and batch
            BestBatchResult best = findBestBatch(
                    remaining, spDroneIds, allDrones, bestSp, restrictedAreas);

            if (best == null || best.batch.isEmpty()) {
                logger.error("Cannot find valid batch for remaining {} dispatches", remaining.size());
                return null;
            }

            // Assign this batch
            allPaths.addAll(best.response.getDronePaths());
            totalCost += best.response.getTotalCost();
            totalMoves += best.response.getTotalMoves();
            usedDroneIds.add(best.droneId);

            // Remove only the FIRST occurrence (not all equal instances)
            // This is critical for sub-dispatches that have the same ID
            for (MedDispatchRec dispatch : best.batch) {
                remaining.remove(dispatch); // Removes first occurrence only
            }

            logger.info("Assigned {} deliveries to drone {}, {} remaining",
                    best.batch.size(), best.droneId, remaining.size());
        }

        DeliveryPathResponse response = new DeliveryPathResponse();
        response.setTotalCost(totalCost);
        response.setTotalMoves(totalMoves);
        response.setDronePaths(allPaths);
        return response;
    }

    /**
     * Find the service point closest to a group of dispatches
     */
    private ServicePoint findClosestServicePoint(
            List<MedDispatchRec> dispatches,
            List<ServicePoint> servicePoints) {

        return servicePoints.stream()
                .min(Comparator.comparingDouble(sp -> {
                    double totalDist = 0;
                    for (MedDispatchRec d : dispatches) {
                        totalDist += calculateEuclideanDistance(
                                sp.getLocation().getLng(),
                                sp.getLocation().getLat(),
                                d.getDelivery().getLng(),
                                d.getDelivery().getLat());
                    }
                    return totalDist;
                }))
                .orElse(servicePoints.get(0));
    }

    /**
     * Find the best drone and batch size for remaining dispatches
     */
    private BestBatchResult findBestBatch(
            List<MedDispatchRec> remaining,
            List<Integer> droneIds,
            List<Drone> allDrones,
            ServicePoint servicePoint,
            List<RestrictedArea> restrictedAreas) {

        logger.debug("Finding best batch for {} remaining dispatches", remaining.size());
        logger.debug("Evaluating drones: {}", droneIds);
        BestBatchResult best = null;

        for (Integer droneId : droneIds) {
            Drone drone = allDrones.stream()
                    .filter(d -> d.getId().equals(droneId))
                    .findFirst()
                    .orElse(null);

            if (drone == null) continue;

            // Try to fit as many dispatches as possible based on capacity, maxMoves, and requirements
            List<MedDispatchRec> maxBatch = selectMaxBatchForDrone(
                    drone, servicePoint, remaining, restrictedAreas);

            if (maxBatch.isEmpty()) {
                continue;
            }

            // Try progressively smaller batches (from largest to single delivery)
            // This handles maxCost constraints that prevent larger batches
            for (int batchSize = maxBatch.size(); batchSize >= 1; batchSize--) {
                List<MedDispatchRec> batch = maxBatch.subList(0, batchSize);

                // Reuse existing calculatePathForDrone method
                DeliveryPathResponse result = calculatePathForDrone(
                        drone, servicePoint, batch, restrictedAreas);

                if (result != null) {
                    // Found a valid batch for this drone
                    // Prefer: 1) larger batch size, 2) fewer moves, 3) lower cost
                    boolean isBetter = false;

                    if (best == null) {
                        isBetter = true;
                    } else if (batch.size() > best.batch.size()) {
                        isBetter = true; // More deliveries in one trip is better
                    } else if (batch.size() == best.batch.size()) {
                        // Same batch size - compare moves first, then cost
                        if (result.getTotalMoves() < best.response.getTotalMoves()) {
                            isBetter = true;
                        } else if (result.getTotalMoves().equals(best.response.getTotalMoves()) &&
                                   result.getTotalCost() < best.response.getTotalCost()) {
                            isBetter = true;
                        }
                    }

                    if (isBetter) {
                        best = new BestBatchResult(droneId, new ArrayList<>(batch), result);
                    }
                    break; // Found valid batch, try next drone
                }
            }
        }

        if (best != null) {
            logger.info("Best batch: drone {} with {} dispatches", best.droneId, best.batch.size());
        } else {
            logger.warn("No drone could handle any of the {} dispatches", remaining.size());
        }

        return best;
    }

    /**
     * Select maximum batch of dispatches that fit within drone's capacity, maxMoves, and requirements
     */
    private List<MedDispatchRec> selectMaxBatchForDrone(
            Drone drone,
            ServicePoint servicePoint,
            List<MedDispatchRec> dispatches,
            List<RestrictedArea> restrictedAreas) {

        List<MedDispatchRec> batch = new ArrayList<>();
        double currentCapacity = 0.0;
        int estimatedMoves = 0;
        double MOVE_DISTANCE = 0.00015;

        // Check if batch has cooling/heating requirements
        boolean hasCooling = false;
        boolean hasHeating = false;

        Double droneCapacity = drone.getCapability().getCapacity();
        Integer maxMoves = drone.getCapability().getMaxMoves();
        Boolean droneCooling = drone.getCapability().getCooling();
        Boolean droneHeating = drone.getCapability().getHeating();

        for (MedDispatchRec dispatch : dispatches) {
            // Check capacity constraint
            Double dispatchCapacity = dispatch.getRequirements().getCapacity();
            if (dispatchCapacity != null && droneCapacity != null) {
                if (currentCapacity + dispatchCapacity > droneCapacity) {
                    break; // Capacity exceeded
                }
            }

            // Check cooling/heating constraints
            Boolean needsCooling = dispatch.getRequirements().getCooling();
            Boolean needsHeating = dispatch.getRequirements().getHeating();

            // Cannot mix cooling and heating in same batch
            if (Boolean.TRUE.equals(needsCooling)) {
                if (hasHeating || (droneCooling == null || !droneCooling)) {
                    break;
                }
                hasCooling = true;
            }

            if (Boolean.TRUE.equals(needsHeating)) {
                if (hasCooling || (droneHeating == null || !droneHeating)) {
                    break;
                }
                hasHeating = true;
            }

            // Estimate moves for this delivery
            if (maxMoves != null) {
                double distance;
                if (batch.isEmpty()) {
                    // From service point to delivery
                    distance = calculateEuclideanDistance(
                            servicePoint.getLocation().getLng(),
                            servicePoint.getLocation().getLat(),
                            dispatch.getDelivery().getLng(),
                            dispatch.getDelivery().getLat());
                    estimatedMoves = (int) Math.ceil(distance / MOVE_DISTANCE) * 2; // Round trip
                } else {
                    // From last delivery to this one
                    MedDispatchRec last = batch.get(batch.size() - 1);
                    distance = calculateEuclideanDistance(
                            last.getDelivery().getLng(),
                            last.getDelivery().getLat(),
                            dispatch.getDelivery().getLng(),
                            dispatch.getDelivery().getLat());
                    estimatedMoves += (int) Math.ceil(distance / MOVE_DISTANCE);
                }

                if (estimatedMoves > maxMoves) {
                    break; // Can't fit more moves
                }
            }

            // Add to batch
            batch.add(dispatch);
            if (dispatchCapacity != null) {
                currentCapacity += dispatchCapacity;
            }
        }

        return batch;
    }

    /**
     * Helper class to store best batch result
     */
    private static class BestBatchResult {
        Integer droneId;
        List<MedDispatchRec> batch;
        DeliveryPathResponse response;

        BestBatchResult(Integer droneId, List<MedDispatchRec> batch, DeliveryPathResponse response) {
            this.droneId = droneId;
            this.batch = batch;
            this.response = response;
        }
    }
    /**
     * Assign a group of deliveries to minimal number of drones.
     * Strategy: Greedily fit as many deliveries as possible per drone.
     */
    private DeliveryPathResponse assignDeliveriesToMinimalDrones(
            List<MedDispatchRec> deliveries,
            String requirementType,
            List<Drone> allDrones,
            List<ServicePoint> servicePoints,
            List<DroneServicePointAvailability> droneAvailability,
            List<Integer> availableDroneIds,
            List<RestrictedArea> restrictedAreas,
            Set<Integer> usedDroneIds) {

        if (deliveries.isEmpty()) {
            DeliveryPathResponse response = new DeliveryPathResponse();
            response.setTotalCost(0.0);
            response.setTotalMoves(0);
            response.setDronePaths(new ArrayList<>());
            return response;
        }

        logger.debug("Assigning {} {} deliveries to minimal drones", deliveries.size(), requirementType);

        // Get suitable drones for this requirement type
        List<Integer> suitableDroneIds = filterDronesByRequirement(availableDroneIds, allDrones, requirementType)
                .stream()
                .filter(id -> !usedDroneIds.contains(id)) // Exclude already used drones
                .collect(Collectors.toList());

        if (suitableDroneIds.isEmpty()) {
            logger.warn("No suitable drones available for {} requirement", requirementType);
            return null;
        }

        List<DeliveryPathResponse.DronePath> dronePaths = new ArrayList<>();
        double totalCost = 0.0;
        int totalMoves = 0;
        List<MedDispatchRec> remaining = new ArrayList<>(deliveries);

        // Greedily assign deliveries to drones
        while (!remaining.isEmpty()) {
            // Find best drone that can handle the most remaining deliveries
            DroneAssignment best = findBestDroneForDeliveries(
                    remaining, suitableDroneIds, allDrones, servicePoints,
                    droneAvailability, restrictedAreas);

            if (best == null || best.deliveries.isEmpty()) {
                logger.warn("Cannot assign remaining {} deliveries", remaining.size());
                return null;
            }

            // Add this drone's path
            dronePaths.addAll(best.response.getDronePaths());
            totalCost += best.response.getTotalCost();
            totalMoves += best.response.getTotalMoves();
            usedDroneIds.add(best.droneId);
            suitableDroneIds.remove(best.droneId);

            // Remove assigned deliveries from remaining
            remaining.removeAll(best.deliveries);

            logger.debug("Assigned {} deliveries to drone {}, {} remaining",
                    best.deliveries.size(), best.droneId, remaining.size());
        }

        DeliveryPathResponse response = new DeliveryPathResponse();
        response.setTotalCost(totalCost);
        response.setTotalMoves(totalMoves);
        response.setDronePaths(dronePaths);

        return response;
    }

    /**
     * Find the best drone that can handle the most deliveries from the remaining set.
     */
    private DroneAssignment findBestDroneForDeliveries(
            List<MedDispatchRec> deliveries,
            List<Integer> suitableDroneIds,
            List<Drone> allDrones,
            List<ServicePoint> servicePoints,
            List<DroneServicePointAvailability> droneAvailability,
            List<RestrictedArea> restrictedAreas) {

        DroneAssignment best = null;
        int maxDeliveries = 0;

        // Try each suitable drone at each service point
        for (ServicePoint sp : servicePoints) {
            List<Integer> dronesAtSp = getDroneIdsAtServicePoint(sp.getId(), droneAvailability, suitableDroneIds);

            for (Integer droneId : dronesAtSp) {
                Drone drone = allDrones.stream()
                        .filter(d -> d.getId().equals(droneId))
                        .findFirst()
                        .orElse(null);

                if (drone == null) continue;

                // Try to fit as many deliveries as possible with this drone
                // Start with all deliveries, reduce if needed
                for (int numToTry = deliveries.size(); numToTry > 0; numToTry--) {
                    List<MedDispatchRec> subset = deliveries.subList(0, numToTry);

                    DeliveryPathResponse response = calculatePathForDrone(drone, sp, subset, restrictedAreas);

                    if (response != null) {
                        // This drone can handle this many deliveries
                        if (numToTry > maxDeliveries) {
                            best = new DroneAssignment();
                            best.droneId = droneId;
                            best.servicePoint = sp;
                            best.deliveries = new ArrayList<>(subset);
                            best.response = response;
                            maxDeliveries = numToTry;
                        }
                        break; // Found max for this drone, try next drone
                    }
                }
            }
        }

        return best;
    }

    /**
     * Filter drones by requirement type (cooling, heating, or standard).
     */
    private List<Integer> filterDronesByRequirement(
            List<Integer> droneIds,
            List<Drone> allDrones,
            String requirementType) {

        return droneIds.stream()
                .map(id -> allDrones.stream()
                        .filter(d -> d.getId().equals(id))
                        .findFirst()
                        .orElse(null))
                .filter(Objects::nonNull)
                .filter(drone -> {
                    if (drone.getCapability() == null) return false;

                    switch (requirementType) {
                        case "cooling":
                            return Boolean.TRUE.equals(drone.getCapability().getCooling());
                        case "heating":
                            return Boolean.TRUE.equals(drone.getCapability().getHeating());
                        case "standard":
                            return true; // Any drone can handle standard deliveries
                        default:
                            return false;
                    }
                })
                .map(Drone::getId)
                .collect(Collectors.toList());
    }

    /**
     * Helper class to store drone assignment results.
     */
    private static class DroneAssignment {
        Integer droneId;
        ServicePoint servicePoint;
        List<MedDispatchRec> deliveries;
        DeliveryPathResponse response;
    }
}