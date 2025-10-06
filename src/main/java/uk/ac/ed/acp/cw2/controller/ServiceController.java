package uk.ac.ed.acp.cw2.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.dto.IsInRegionRequest;
import uk.ac.ed.acp.cw2.pojo.LngLat;
import uk.ac.ed.acp.cw2.dto.NextPositionRequest;
import uk.ac.ed.acp.cw2.pojo.Result;
import uk.ac.ed.acp.cw2.dto.TwoPositionsRequest;
import uk.ac.ed.acp.cw2.service.GeoService;

import java.net.URL;

/**
 * Controller class that handles various HTTP endpoints for the application
 */
@RestController
@RequestMapping("/api/v1")
public class ServiceController {

    private static final Logger logger = LoggerFactory.getLogger(ServiceController.class);

    @Value("${ilp.service.url}")
    public URL serviceUrl;

    @Autowired
    private GeoService geoService;


    @GetMapping("/")
    public String index() {
        return "<html><body>" +
                "<h1>Welcome from ILP</h1>" +
                "<h4>ILP-REST-Service-URL:</h4> <a href=\"" + serviceUrl + "\" target=\"_blank\"> " + serviceUrl+ " </a>" +
                "</body></html>";
    }

    /**
     * 2. return a static uuid
     */
    @GetMapping("/uid")
    public String uid() {
        return "s2564099";
    }

    /**
     * 3.calculate the Euclidian distance between two positions
     */
    @PostMapping("/distanceTo")
    public Result distanceTo(@RequestBody TwoPositionsRequest request) {
        try {
            // Validate input
            if (request == null ||
                    request.getPosition1() == null ||
                    request.getPosition2() == null ||
                    !request.getPosition1().isValid() ||
                    !request.getPosition2().isValid()) {
                logger.warn("Invalid distanceTo request: {}", request);
                return Result.error("Invalid position data");
            }

            // Calculate distance
            double distance = geoService.calculateDistance(
                    request.getPosition1(),
                    request.getPosition2()
            );

            logger.info("Distance calculated: {}", distance);
            return Result.success(distance);

        } catch (Exception e) {
            logger.error("Error calculating distance", e);
            return Result.error("Error calculating distance");
        }
    }

    /**
     * 4.check if two positions are close to each other
     */
    @PostMapping("/closeTo")
    public Result closeTo(@RequestBody TwoPositionsRequest request) {
        try {
            // Validate input
            if (request == null ||
                    request.getPosition1() == null ||
                    request.getPosition2() == null ||
                    !request.getPosition1().isValid() ||
                    !request.getPosition2().isValid()) {
                logger.warn("Invalid closeTo request: {}", request);
                return Result.error("Invalid position data");
            }

            // Check if close to each other
            boolean isClose = geoService.isCloseTo(
                    request.getPosition1(),
                    request.getPosition2()
            );

            logger.info("CloseTo check result: {}", isClose);
            return Result.success(isClose);

        } catch (Exception e) {
            logger.error("Error checking closeTo", e);
            return Result.error("Error checking closeTo");
        }
    }

    /**
     * 5.calculate the next position based on the starting position and angle
     */
    @PostMapping("/nextPosition")
    public Result nextPosition(@RequestBody NextPositionRequest request) {
        try {
            // Validate input
            if (request == null ||
                    request.getStart() == null ||
                    !request.getStart().isValid() ||
                    request.getAngle() == null) {
                logger.warn("Invalid nextPosition request: {}", request);
                return Result.error("Invalid position data");
            }

            // Calculate next position
            LngLat nextPos = geoService.nextPosition(
                    request.getStart(),
                    request.getAngle()
            );

            logger.info("Next position calculated: {}", nextPos);
            return Result.success(nextPos);

        } catch (Exception e) {
            logger.error("Error calculating nextPosition", e);
            return Result.error("Error calculating nextPosition");
        }
    }

    /**
     * 6. Check if a position is inside a polygon region
     */
    @PostMapping("/isInRegion")
    public Result isInRegion(@RequestBody IsInRegionRequest request) {
        try {
            // Validate input
            if (request == null ||
                    request.getPosition() == null ||
                    request.getRegion() == null ||
                    request.getPosition().getLng() == null ||
                    request.getPosition().getLat() == null ||
                    !request.getPosition().isValid() ||
                    request.getRegion().getVertices() == null) {
                logger.warn("Invalid isInRegion request: {}", request);
                return Result.error("Invalid position or region data");
            }

            // Check if region is closed
            if (!request.getRegion().isClosed()) {
                logger.warn("Region is not closed: {}", request.getRegion().getName());
                return Result.error("Region is not closed");
            }

            // Check if position is in region
            boolean isInRegion = geoService.isInRegion(
                    request.getPosition(),
                    request.getRegion().getVertices()
            );

            logger.info("isInRegion check result: {}", isInRegion);
            return Result.success(isInRegion);

        } catch (Exception e) {
            logger.error("Error checking if position is in region", e);
            return Result.error("Error checking if position is in region");
        }
    }






    @GetMapping("/demo")
    public String demo() {
        return "demo";
    }
}
