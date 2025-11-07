package uk.ac.ed.acp.cw2.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import uk.ac.ed.acp.cw2.dto.Drone;
import uk.ac.ed.acp.cw2.dto.QueryCondition;
import uk.ac.ed.acp.cw2.service.DroneQueryService;

import java.lang.reflect.Field;
import java.util.Arrays;
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
        logger.info("Fetching all drones from ILP REST service: {}/drones", ilpEndpoint);
        Drone[] drones = restTemplate.getForObject(ilpEndpoint + "/drones", Drone[].class);
        return drones != null ? Arrays.asList(drones) : List.of();
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