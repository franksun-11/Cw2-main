package uk.ac.ed.acp.cw2.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.pojo.LngLat;
import uk.ac.ed.acp.cw2.pojo.Result;
import uk.ac.ed.acp.cw2.pojo.TwoPositionsRequest;
import uk.ac.ed.acp.cw2.service.GeoService;

import java.net.URL;

/**
 * Controller class that handles various HTTP endpoints for the application.
 * Provides functionality for serving the index page, retrieving a static UUID,
 * and managing key-value pairs through POST requests.
 */
@RestController()
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
    @PostMapping
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
    @PostMapping
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

            logger.info("Close to check result: {}", isClose);
            return Result.success(isClose);

        } catch (Exception e) {
            logger.error("Error checking close to", e);
            return Result.error("Error checking close to");
        }
    }


    @GetMapping("/demo")
    public String demo() {
        return "demo";
    }
}
