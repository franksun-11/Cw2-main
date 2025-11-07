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
import java.util.Arrays;
import java.util.Date;
import java.util.List;
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
        if (requirements.getMaxCost() != null) {
            double totalCost = calculateMaxCost(capability);
            if (totalCost > requirements.getMaxCost()) {
                logger.debug("Drone {} total cost {} > max cost {}",
                        drone.getId(), totalCost, requirements.getMaxCost());
                return false;
            }
        }

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

        return initialCost + perMove + finalCost;
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
}