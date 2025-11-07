package uk.ac.ed.acp.cw2.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import uk.ac.ed.acp.cw2.dto.Drone;
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
     * 直接从 ILP REST 服务获取所有无人机数据
     * 每次调用都重新获取,不缓存
     */
    private List<Drone> fetchAllDrones() {
        logger.info("Fetching all drones from ILP REST service: {}/drones", ilpEndpoint);
        Drone[] drones = restTemplate.getForObject(ilpEndpoint + "/drones", Drone[].class);
        return drones != null ? Arrays.asList(drones) : List.of();
    }

    @Override
    public List<Integer> getDronesWithCooling(boolean coolingRequired) {
        logger.info("Querying drones with cooling={}", coolingRequired);

        // 每次都重新获取数据
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

        // 每次都重新获取数据
        List<Drone> drones = fetchAllDrones();

        return drones.stream()
                .filter(drone -> drone.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Integer> queryAsPath(String attributeName, String attributeValue) {
        logger.info("Querying drones by attribute: {}={}", attributeName, attributeValue);

        // 每次都重新获取数据
        List<Drone> drones = fetchAllDrones();

        return drones.stream()
                .filter(drone -> drone.getCapability() != null)
                .filter(drone -> matchesAttribute(drone.getCapability(), attributeName, attributeValue))
                .map(Drone::getId)
                .collect(Collectors.toList());
    }

    // matchesAttribute 和 compareValues 方法保持不变
    private boolean matchesAttribute(Drone.Capability capability,
                                     String attributeName,
                                     String attributeValue) {
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
}