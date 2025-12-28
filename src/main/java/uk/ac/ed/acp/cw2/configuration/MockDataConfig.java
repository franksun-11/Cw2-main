package uk.ac.ed.acp.cw2.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.ac.ed.acp.cw2.dto.Drone;
import uk.ac.ed.acp.cw2.dto.ServicePoint;
import uk.ac.ed.acp.cw2.dto.RestrictedArea;
import uk.ac.ed.acp.cw2.dto.DroneServicePointAvailability;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Mock configuration for local development in Asia (slow network to UK Azure)
 * Enable with: spring.profiles.active=mock in application.yml
 */
@Configuration
@Profile("mock")
public class MockDataConfig {

    private final ObjectMapper objectMapper;

    public MockDataConfig() {
        this.objectMapper = new ObjectMapper();
        // Register Java 8 date/time module to handle LocalTime serialization
        this.objectMapper.findAndRegisterModules();
    }

    @Bean
    @Primary
    public RestTemplate mockRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new MockDataInterceptor());
        return restTemplate;
    }

    private class MockDataInterceptor implements ClientHttpRequestInterceptor {
        @Override
        public @org.springframework.lang.NonNull ClientHttpResponse intercept(@org.springframework.lang.NonNull HttpRequest request, @org.springframework.lang.NonNull byte[] body,
                                            @org.springframework.lang.NonNull ClientHttpRequestExecution execution) throws IOException {
            String url = request.getURI().toString();

            byte[] responseBody;
            // Check more specific URLs first to avoid partial matches
            if (url.contains("/drones-for-service-points")) {
                responseBody = objectMapper.writeValueAsBytes(getMockDroneAvailability());
            } else if (url.contains("/service-points")) {
                responseBody = objectMapper.writeValueAsBytes(getMockServicePoints());
            } else if (url.contains("/restricted-areas")) {
                responseBody = objectMapper.writeValueAsBytes(getMockRestrictedAreas());
            } else if (url.contains("/drones")) {
                responseBody = objectMapper.writeValueAsBytes(getMockDrones());
            } else {
                return execution.execute(request, body);
            }

            return new MockClientHttpResponse(responseBody, HttpStatus.OK);
        }
    }

    private static class MockClientHttpResponse implements ClientHttpResponse {
        private final byte[] responseBody;
        private final HttpStatus status;

        public MockClientHttpResponse(byte[] responseBody, HttpStatus status) {
            this.responseBody = responseBody;
            this.status = status;
        }

        @SuppressWarnings("removal")
        @Override
        public int getRawStatusCode() {
            return status.value();
        }

        @Override
        public @org.springframework.lang.NonNull HttpStatus getStatusCode() {
            return status;
        }

        @Override
        public @org.springframework.lang.NonNull String getStatusText() {
            return status.getReasonPhrase();
        }

        @Override
        public void close() {
            // No resources to close
        }

        @Override
        public @org.springframework.lang.NonNull InputStream getBody() {
            return new ByteArrayInputStream(responseBody);
        }

        @Override
        public @org.springframework.lang.NonNull HttpHeaders getHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
            return headers;
        }
    }

    private Drone[] getMockDrones() {
        List<Drone> drones = new ArrayList<>();
        drones.add(createDrone(1, "Drone 1", true, true, 4.0, 2000, 0.01, 4.3, 6.5));
        drones.add(createDrone(2, "Drone 2", false, true, 8.0, 1500, 0.015, 2.8, 4.2));
        drones.add(createDrone(3, "Drone 3", false, false, 6.0, 1200, 0.012, 3.5, 5.0));
        drones.add(createDrone(4, "Drone 4", false, true, 8.0, 1800, 0.018, 1.5, 2.5));
        drones.add(createDrone(5, "Drone 5", true, true, 12.0, 1500, 0.04, 1.8, 3.5));
        drones.add(createDrone(6, "Drone 6", false, true, 4.0, 1000, 0.025, 5.2, 7.8));
        drones.add(createDrone(7, "Drone 7", false, true, 10.0, 1400, 0.022, 3.0, 4.5));
        drones.add(createDrone(8, "Drone 8", true, false, 14.0, 1600, 0.035, 2.2, 3.8));
        drones.add(createDrone(9, "Drone 9", true, true, 16.0, 1300, 0.045, 1.2, 2.0));
        drones.add(createDrone(10, "Drone 10", false, true, 20.0, 1100, 0.05, 0.8, 1.5));
        return drones.toArray(new Drone[0]);
    }

    private Drone createDrone(int id, String name, boolean cooling, boolean heating,
                              double capacity, int maxMoves, double costPerMove,
                              double fixedCost, double variableCost) {
        Drone drone = new Drone();
        drone.setId(id);
        drone.setName(name);
        Drone.Capability capability = new Drone.Capability();
        capability.setCooling(cooling);
        capability.setHeating(heating);
        capability.setCapacity(capacity);
        capability.setMaxMoves(maxMoves);
        capability.setCostPerMove(costPerMove);
        capability.setCostInitial(fixedCost);
        capability.setCostFinal(variableCost);
        drone.setCapability(capability);
        return drone;
    }

    private ServicePoint[] getMockServicePoints() {
        List<ServicePoint> servicePoints = new ArrayList<>();
        ServicePoint sp1 = new ServicePoint();
        sp1.setId(1);
        sp1.setName("Appleton Tower");
        ServicePoint.Location loc1 = new ServicePoint.Location();
        loc1.setLng(-3.186874);
        loc1.setLat(55.944494);
        sp1.setLocation(loc1);
        servicePoints.add(sp1);

        ServicePoint sp2 = new ServicePoint();
        sp2.setId(2);
        sp2.setName("Ocean Terminal");
        ServicePoint.Location loc2 = new ServicePoint.Location();
        loc2.setLng(-3.176152);
        loc2.setLat(55.978802);
        sp2.setLocation(loc2);
        servicePoints.add(sp2);
        return servicePoints.toArray(new ServicePoint[0]);
    }

    private RestrictedArea[] getMockRestrictedAreas() {
        List<RestrictedArea> areas = new ArrayList<>();
        RestrictedArea area1 = new RestrictedArea();
        area1.setId(1);
        area1.setName("George Square Area");
        area1.setVertices(Arrays.asList(
                createVertex(-3.190578818321228, 55.94402412577528),
                createVertex(-3.1899887323379517, 55.94284650540911),
                createVertex(-3.187097311019897, 55.94328811724263),
                createVertex(-3.187682032585144, 55.944477740393744),
                createVertex(-3.190578818321228, 55.94402412577528)
        ));
        areas.add(area1);

        RestrictedArea area2 = new RestrictedArea();
        area2.setId(2);
        area2.setName("Dr Elsie Inglis Quadrangle");
        area2.setVertices(Arrays.asList(
                createVertex(-3.1907182931900024, 55.94519570234043),
                createVertex(-3.1906163692474365, 55.94498241796357),
                createVertex(-3.1900262832641597, 55.94507554227258),
                createVertex(-3.190133571624756, 55.94529783810495),
                createVertex(-3.1907182931900024, 55.94519570234043)
        ));
        areas.add(area2);

        RestrictedArea area3 = new RestrictedArea();
        area3.setId(3);
        area3.setName("Bristo Square Open Area");
        area3.setVertices(Arrays.asList(
                createVertex(-3.189543485641479, 55.94552313663306),
                createVertex(-3.189382553100586, 55.94553214854692),
                createVertex(-3.1891441345214844, 55.94539680780061),
                createVertex(-3.1891012191772456, 55.94535420498897),
                createVertex(-3.1892406940460205, 55.94534118920679),
                createVertex(-3.1892943382263184, 55.945264829136346),
                createVertex(-3.189678192138672, 55.94523329858956),
                createVertex(-3.1897103786468506, 55.94527740215124),
                createVertex(-3.189735412597656, 55.94544022906157),
                createVertex(-3.189543485641479, 55.94552313663306)
        ));
        areas.add(area3);

        RestrictedArea area4 = new RestrictedArea();
        area4.setId(4);
        area4.setName("Bayes Central Area");
        area4.setVertices(Arrays.asList(
                createVertex(-3.1876373291015625, 55.94520696160767),
                createVertex(-3.187432289123535, 55.94498391711548),
                createVertex(-3.1869113445281982, 55.94507854844829),
                createVertex(-3.1867992877960205, 55.94519119620112),
                createVertex(-3.186916708946228, 55.94524680580927),
                createVertex(-3.1870770454406738, 55.94527439876948),
                createVertex(-3.187432289123535, 55.94524981034783),
                createVertex(-3.1875550746917725, 55.94524981034783),
                createVertex(-3.1876373291015625, 55.94520696160767)
        ));
        areas.add(area4);
        return areas.toArray(new RestrictedArea[0]);
    }

    private RestrictedArea.Vertex createVertex(double lng, double lat) {
        RestrictedArea.Vertex vertex = new RestrictedArea.Vertex();
        vertex.setLng(lng);
        vertex.setLat(lat);
        return vertex;
    }

    private DroneServicePointAvailability[] getMockDroneAvailability() {
        List<DroneServicePointAvailability> availability = new ArrayList<>();
        List<DroneServicePointAvailability.DroneAvailability> sp1Drones = new ArrayList<>();

        sp1Drones.add(createDroneAvailability("1", Arrays.asList(
                createTimeSlot("MONDAY", "00:00:00", "23:59:59"),
                createTimeSlot("WEDNESDAY", "00:00:00", "23:59:59"),
                createTimeSlot("THURSDAY", "12:00:00", "23:59:59"),
                createTimeSlot("FRIDAY", "12:00:00", "23:59:59"),
                createTimeSlot("SUNDAY", "00:00:00", "23:59:59")
        )));

        sp1Drones.add(createDroneAvailability("2", Arrays.asList(
                createTimeSlot("MONDAY", "12:00:00", "23:59:59"),
                createTimeSlot("TUESDAY", "00:00:00", "23:59:59"),
                createTimeSlot("WEDNESDAY", "00:00:00", "11:59:59"),
                createTimeSlot("THURSDAY", "00:00:00", "11:59:59"),
                createTimeSlot("FRIDAY", "00:00:00", "23:59:59"),
                createTimeSlot("SATURDAY", "12:00:00", "23:59:59"),
                createTimeSlot("SUNDAY", "00:00:00", "23:59:59")
        )));

        sp1Drones.add(createDroneAvailability("3", Arrays.asList(
                createTimeSlot("MONDAY", "00:00:00", "11:59:59"),
                createTimeSlot("TUESDAY", "12:00:00", "23:59:59"),
                createTimeSlot("THURSDAY", "12:00:00", "23:59:59"),
                createTimeSlot("FRIDAY", "12:00:00", "23:59:59"),
                createTimeSlot("SUNDAY", "12:00:00", "23:59:59")
        )));

        sp1Drones.add(createDroneAvailability("4", Arrays.asList(
                createTimeSlot("MONDAY", "00:00:00", "11:59:59"),
                createTimeSlot("TUESDAY", "00:00:00", "11:59:59"),
                createTimeSlot("WEDNESDAY", "00:00:00", "23:59:59"),
                createTimeSlot("SATURDAY", "00:00:00", "23:59:59"),
                createTimeSlot("SUNDAY", "00:00:00", "23:59:59")
        )));

        sp1Drones.add(createDroneAvailability("5", Arrays.asList(
                createTimeSlot("TUESDAY", "00:00:00", "23:59:59"),
                createTimeSlot("THURSDAY", "00:00:00", "11:59:59"),
                createTimeSlot("FRIDAY", "00:00:00", "23:59:59"),
                createTimeSlot("SATURDAY", "00:00:00", "23:59:59"),
                createTimeSlot("SUNDAY", "00:00:00", "11:59:59")
        )));

        DroneServicePointAvailability sp1Availability = new DroneServicePointAvailability();
        sp1Availability.setServicePointId(1);
        sp1Availability.setDrones(sp1Drones);
        availability.add(sp1Availability);

        List<DroneServicePointAvailability.DroneAvailability> sp2Drones = new ArrayList<>();

        sp2Drones.add(createDroneAvailability("6", Arrays.asList(
                createTimeSlot("MONDAY", "00:00:00", "23:59:59"),
                createTimeSlot("WEDNESDAY", "00:00:00", "23:59:59"),
                createTimeSlot("THURSDAY", "12:00:00", "23:59:59"),
                createTimeSlot("FRIDAY", "12:00:00", "23:59:59"),
                createTimeSlot("SATURDAY", "00:00:00", "23:59:59"),
                createTimeSlot("SUNDAY", "00:00:00", "23:59:59")
        )));

        sp2Drones.add(createDroneAvailability("7", Arrays.asList(
                createTimeSlot("MONDAY", "12:00:00", "23:59:59"),
                createTimeSlot("TUESDAY", "00:00:00", "23:59:59"),
                createTimeSlot("WEDNESDAY", "00:00:00", "11:59:59"),
                createTimeSlot("THURSDAY", "00:00:00", "11:59:59"),
                createTimeSlot("FRIDAY", "00:00:00", "23:59:59"),
                createTimeSlot("SATURDAY", "00:00:00", "23:59:59"),
                createTimeSlot("SUNDAY", "00:00:00", "23:59:59")
        )));

        sp2Drones.add(createDroneAvailability("8", Arrays.asList(
                createTimeSlot("TUESDAY", "00:00:00", "23:59:59"),
                createTimeSlot("THURSDAY", "12:00:00", "23:59:59"),
                createTimeSlot("FRIDAY", "00:00:00", "23:59:59"),
                createTimeSlot("SUNDAY", "00:00:00", "23:59:59")
        )));

        sp2Drones.add(createDroneAvailability("9", Arrays.asList(
                createTimeSlot("MONDAY", "00:00:00", "23:59:59"),
                createTimeSlot("TUESDAY", "00:00:00", "11:59:59"),
                createTimeSlot("WEDNESDAY", "00:00:00", "23:59:59"),
                createTimeSlot("THURSDAY", "00:00:00", "23:59:59"),
                createTimeSlot("SATURDAY", "00:00:00", "23:59:59"),
                createTimeSlot("SUNDAY", "00:00:00", "23:59:59")
        )));

        sp2Drones.add(createDroneAvailability("10", Arrays.asList(
                createTimeSlot("MONDAY", "12:00:00", "23:59:59"),
                createTimeSlot("TUESDAY", "00:00:00", "23:59:59"),
                createTimeSlot("FRIDAY", "00:00:00", "23:59:59")
        )));

        DroneServicePointAvailability sp2Availability = new DroneServicePointAvailability();
        sp2Availability.setServicePointId(2);
        sp2Availability.setDrones(sp2Drones);
        availability.add(sp2Availability);

        return availability.toArray(new DroneServicePointAvailability[0]);
    }

    private DroneServicePointAvailability.DroneAvailability createDroneAvailability(
            String droneId, List<DroneServicePointAvailability.TimeSlot> timeSlots) {
        DroneServicePointAvailability.DroneAvailability droneAvail =
                new DroneServicePointAvailability.DroneAvailability();
        droneAvail.setId(droneId);
        droneAvail.setAvailability(timeSlots);
        return droneAvail;
    }

    private DroneServicePointAvailability.TimeSlot createTimeSlot(String day, String startTime, String endTime) {
        DroneServicePointAvailability.TimeSlot slot = new DroneServicePointAvailability.TimeSlot();
        slot.setDayOfWeek(day);
        slot.setFrom(LocalTime.parse(startTime));
        slot.setUntil(LocalTime.parse(endTime));
        return slot;
    }
}
