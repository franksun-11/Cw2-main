package uk.ac.ed.acp.cw2.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.ed.acp.cw2.dto.Drone;
import uk.ac.ed.acp.cw2.repository.IlpDataRepository;
import uk.ac.ed.acp.cw2.service.DroneQueryService;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Drone query service implementation
 */
@Service
public class DroneQueryServiceImpl implements DroneQueryService {

    private static final Logger logger = LoggerFactory.getLogger(DroneQueryServiceImpl.class);

    @Autowired
    private IlpDataRepository repository;

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

    /**
     * query as path
     */
    public List<Integer> queryAsPath(String attibuteName, String attributeValue) {
        return repository.getAllDrones().stream()
                .filter(drone -> drone.getCapability() != null)
                .filter(drone -> matchesAttribute(drone.getCapability(), attibuteName, attributeValue))
                .map(Drone::getId)
                .collect(Collectors.toList());
    }

    /**
     * Check if a drone capability matches a given attribute
     */
    private boolean matchesAttribute(Drone.Capability capability, String attributeName, String attributeValue) {
        try {
            // use reflection to get the field
            Field field = capability.getClass().getDeclaredField(attributeName);
            field.setAccessible(true); // allow access to private field
            Object actualValue = field.get(capability);

            if (actualValue == null) {
                logger.debug("Attribute {} is null in capability", attributeName);
                return false;
            }

            // compare the actual value with the expected value
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
     * Compare actual value with expected value, handling different types.
     */
    private boolean compareValues(Object actualValue, String expectedValue) {
        if (actualValue instanceof Boolean) {
            // handle boolean
            return actualValue.equals(Boolean.parseBoolean(expectedValue));
        } else if (actualValue instanceof Integer) {
            // handle integer
            try {
                return actualValue.equals(Integer.parseInt(expectedValue));
            } catch (NumberFormatException e) {
                logger.error("Invalid integer format for value {}", expectedValue, e);
                return false;
            }
        } else if (actualValue instanceof Double) {
            // handle double
            try {
                double expected = Double.parseDouble(expectedValue);
                double actual = (double) actualValue;
                return Math.abs(expected - actual) < 0.001;
            } catch (NumberFormatException e) {
                logger.error("Invalid double format for value {}", expectedValue, e);
                return false;
            }
        } else {
            // other types, use equals method
            return actualValue.toString().equals(expectedValue);
        }
    }


}
