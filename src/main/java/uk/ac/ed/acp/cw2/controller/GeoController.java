package uk.ac.ed.acp.cw2.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw2.dto.IsInRegionRequest;
import uk.ac.ed.acp.cw2.pojo.LngLat;
import uk.ac.ed.acp.cw2.dto.NextPositionRequest;
import uk.ac.ed.acp.cw2.dto.TwoPositionsRequest;
import uk.ac.ed.acp.cw2.service.GeoService;

import java.net.URL;

/**
 * Controller class that handles various HTTP endpoints for the application
 */
@RestController
@RequestMapping("/api/v1")
public class GeoController {

    private static final Logger logger = LoggerFactory.getLogger(GeoController.class);

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
    public ResponseEntity<Double> distanceTo(@RequestBody TwoPositionsRequest request) {
        try {
            // Validate input
            if (request == null ||
                    request.getPosition1() == null ||
                    request.getPosition2() == null ||
                    !request.getPosition1().isValid() ||
                    !request.getPosition2().isValid()) {
                logger.warn("Invalid distanceTo request: {}", request);
                return ResponseEntity.badRequest().build(); // 400 status
            }

            // Calculate distance
            double distance = geoService.calculateDistance(
                    request.getPosition1(),
                    request.getPosition2()
            );

            logger.info("Distance calculated: {}", distance);
            return ResponseEntity.ok(distance); // 200 status

        } catch (Exception e) {
            logger.error("Error calculating distance", e);
            return ResponseEntity.badRequest().build(); // 400 status
        }
    }

    /**
     * 4.check if two positions are close to each other
     */
    @PostMapping("/closeTo")
    public ResponseEntity<Boolean> closeTo(@RequestBody TwoPositionsRequest request) {
        try {
            // Validate input
            if (request == null ||
                    request.getPosition1() == null ||
                    request.getPosition2() == null ||
                    !request.getPosition1().isValid() ||
                    !request.getPosition2().isValid()) {
                logger.warn("Invalid closeTo request: {}", request);
                return ResponseEntity.badRequest().build(); // 400 status
            }

            // Check if close to each other
            boolean isClose = geoService.isCloseTo(
                    request.getPosition1(),
                    request.getPosition2()
            );

            logger.info("CloseTo check result: {}", isClose);
            return ResponseEntity.ok(isClose); // 200 status

        } catch (Exception e) {
            logger.error("Error checking closeTo", e);
            return ResponseEntity.badRequest().build(); // 400 status
        }
    }

    /**
     * 5.calculate the next position based on the starting position and angle
     */
    @PostMapping("/nextPosition")
    public ResponseEntity<LngLat> nextPosition(@RequestBody NextPositionRequest request) {
        try {
            // Validate input
            if (request == null ||
                    request.getStart() == null ||
                    !request.getStart().isValid() ||
                    request.getAngle() == null) {
                logger.warn("Invalid nextPosition request: {}", request);
                return ResponseEntity.badRequest().build(); // 400 status
            }

            // Validate angle is multiple of 22.5 or 999 for hover
            double angle = request.getAngle();
            if (angle != 999 && Math.abs(angle % 22.5) > 1e-12 && angle <= 360.0 && angle >= 0.0) {
                logger.warn("Invalid angle value: {}. Angle must be a multiple of 22.5 or 999 for hover", angle);
                return ResponseEntity.badRequest().build(); // 400 status
            }

            // Calculate next position
            LngLat nextPos = geoService.nextPosition(
                    request.getStart(),
                    request.getAngle()
            );

            logger.info("Next position calculated: {}", nextPos);
            return ResponseEntity.ok(nextPos); // 200 status

        } catch (Exception e) {
            logger.error("Error calculating nextPosition", e);
            return ResponseEntity.badRequest().build(); // 400 status
        }
    }

    /**
     * 6. Check if a position is inside a polygon region
     */
    @PostMapping("/isInRegion")
    public ResponseEntity<Boolean> isInRegion(@RequestBody IsInRegionRequest request) {
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
                return ResponseEntity.badRequest().build(); // 400 status
            }

            // Check if region is closed
            if (!request.getRegion().isClosed()) {
                logger.warn("Region is not closed: {}", request.getRegion().getName());
                return ResponseEntity.badRequest().build(); // 400 status
            }

            // Check if position is in region
            boolean isInRegion = geoService.isInRegion(
                    request.getPosition(),
                    request.getRegion().getVertices()
            );

            logger.info("isInRegion check result: {}", isInRegion);
            return ResponseEntity.ok(isInRegion); // 200 status

        } catch (Exception e) {
            logger.error("Error checking if position is in region", e);
            return ResponseEntity.badRequest().build(); // 400 status
        }
    }

    @GetMapping("/demo")
    public String demo() {
        return "demo";
    }
}
