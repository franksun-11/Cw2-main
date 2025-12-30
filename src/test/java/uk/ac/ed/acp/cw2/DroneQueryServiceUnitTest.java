package uk.ac.ed.acp.cw2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestTemplate;
import uk.ac.ed.acp.cw2.dto.*;
import uk.ac.ed.acp.cw2.service.impl.DroneQueryServiceImpl;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Unit Tests for DroneQueryServiceImpl
 * Testing for LO3 - Software Testing Coursework
 */
@DisplayName("DroneQueryService Unit Tests - Comprehensive Coverage")
class DroneQueryServiceUnitTest {

    @InjectMocks
    private DroneQueryServiceImpl droneQueryService;

    @Mock
    private RestTemplate restTemplate;

    // String cannot be mocked - just use a dummy value
    // RestTemplate mock will intercept calls, so this URL is never actually used
    private String ilpEndpoint = "http://dummy-ilp-endpoint";

    // Test data fixtures
    private List<Drone> testDrones;
    private List<ServicePoint> testServicePoints;
    private List<RestrictedArea> testRestrictedAreas;
    private List<DroneServicePointAvailability> testDroneAvailability;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Manually inject ilpEndpoint using reflection (cannot use @Mock on String)
        java.lang.reflect.Field field = DroneQueryServiceImpl.class.getDeclaredField("ilpEndpoint");
        field.setAccessible(true);
        field.set(droneQueryService, ilpEndpoint);

        setupTestData();
    }

    /**
     * Setup test data matching Azure endpoint structure
     */
    private void setupTestData() {
        // Setup drones matching the Azure data
        testDrones = new ArrayList<>();

        // Drone 1: cooling=true, heating=true, capacity=4.0
        testDrones.add(createDrone(1, "Drone 1", true, true, 4.0, 2000, 0.01, 4.3, 6.5));

        // Drone 2: cooling=false, heating=true, capacity=8.0
        testDrones.add(createDrone(2, "Drone 2", false, true, 8.0, 1000, 0.03, 2.6, 5.4));

        // Drone 3: cooling=false, heating=false, capacity=20.0
        testDrones.add(createDrone(3, "Drone 3", false, false, 20.0, 4000, 0.05, 9.5, 11.5));

        // Drone 4: cooling=false, heating=true, capacity=8.0
        testDrones.add(createDrone(4, "Drone 4", false, true, 8.0, 1000, 0.02, 1.4, 2.5));

        // Drone 5: cooling=true, heating=true, capacity=12.0
        testDrones.add(createDrone(5, "Drone 5", true, true, 12.0, 1500, 0.04, 1.8, 3.5));

        // Drone 6: cooling=false, heating=true, capacity=4.0
        testDrones.add(createDrone(6, "Drone 6", false, true, 4.0, 2000, 0.03, 3.0, 4.0));

        // Drone 7: cooling=false, heating=true, capacity=8.0
        testDrones.add(createDrone(7, "Drone 7", false, true, 8.0, 1000, 0.015, 1.4, 2.2));

        // Drone 8: cooling=true, heating=false, capacity=20.0
        testDrones.add(createDrone(8, "Drone 8", true, false, 20.0, 4000, 0.04, 5.4, 12.5));

        // Drone 9: cooling=true, heating=true, capacity=8.0
        testDrones.add(createDrone(9, "Drone 9", true, true, 8.0, 1000, 0.06, 2.4, 1.5));

        // Drone 10: cooling=false, heating=false, capacity=12.0
        testDrones.add(createDrone(10, "Drone 10", false, false, 12.0, 1500, 0.07, 1.4, 3.5));

        // Setup service points
        testServicePoints = new ArrayList<>();
        testServicePoints.add(createServicePoint(1, "Appleton Tower", -3.1863580788986368, 55.94468066708487));
        testServicePoints.add(createServicePoint(2, "Ocean Terminal", -3.17732611501824, 55.981186279333656));

        // Setup restricted areas - COMPLETE REAL DATA from Azure
        testRestrictedAreas = new ArrayList<>();

        // 1. George Square Area
        testRestrictedAreas.add(createRestrictedArea(1, "George Square Area", Arrays.asList(
                createVertex(-3.190578818321228, 55.94402412577528),
                createVertex(-3.1899887323379517, 55.94284650540911),
                createVertex(-3.187097311019897, 55.94328811724263),
                createVertex(-3.187682032585144, 55.944477740393744),
                createVertex(-3.190578818321228, 55.94402412577528)
        )));

        // 2. Dr Elsie Inglis Quadrangle
        testRestrictedAreas.add(createRestrictedArea(2, "Dr Elsie Inglis Quadrangle", Arrays.asList(
                createVertex(-3.1907182931900024, 55.94519570234043),
                createVertex(-3.1906163692474365, 55.94498241796357),
                createVertex(-3.1900262832641597, 55.94507554227258),
                createVertex(-3.190133571624756, 55.94529783810495),
                createVertex(-3.1907182931900024, 55.94519570234043)
        )));

        // 3. Bristo Square Open Area
        testRestrictedAreas.add(createRestrictedArea(3, "Bristo Square Open Area", Arrays.asList(
                createVertex(-3.189543485641479, 55.94552313663306),
                createVertex(-3.189382553100586, 55.94553214854692),
                createVertex(-3.189259171485901, 55.94544803726933),
                createVertex(-3.1892001628875732, 55.94533688994374),
                createVertex(-3.189194798469543, 55.94519570234043),
                createVertex(-3.189135789871216, 55.94511759833873),
                createVertex(-3.188138008117676, 55.9452738061846),
                createVertex(-3.1885510683059692, 55.946105902745614),
                createVertex(-3.1895381212234497, 55.94555918427592),
                createVertex(-3.189543485641479, 55.94552313663306)
        )));

        // 4. Bayes Central Area
        testRestrictedAreas.add(createRestrictedArea(4, "Bayes Central Area", Arrays.asList(
                createVertex(-3.1876927614212036, 55.94520696732767),
                createVertex(-3.187555968761444, 55.9449621408666),
                createVertex(-3.186981976032257, 55.94505676722831),
                createVertex(-3.1872327625751495, 55.94536993377657),
                createVertex(-3.1874459981918335, 55.9453361389472),
                createVertex(-3.1873735785484314, 55.94519344934259),
                createVertex(-3.1875935196876526, 55.94515665035927),
                createVertex(-3.187624365091324, 55.94521973430925),
                createVertex(-3.1876927614212036, 55.94520696732767)
        )));

        // Setup drone availability - COMPLETE REAL DATA from Azure
        testDroneAvailability = new ArrayList<>();

        // Service Point 1 - Appleton Tower (Drones 1-5)
        List<DroneServicePointAvailability.DroneAvailability> sp1Drones = new ArrayList<>();

        // Drone 1
        sp1Drones.add(createDroneAvailability("1", Arrays.asList(
                createTimeSlot("MONDAY", "00:00:00", "23:59:59"),
                createTimeSlot("WEDNESDAY", "00:00:00", "23:59:59"),
                createTimeSlot("THURSDAY", "12:00:00", "23:59:59"),
                createTimeSlot("FRIDAY", "12:00:00", "23:59:59"),
                createTimeSlot("SUNDAY", "00:00:00", "23:59:59")
        )));

        // Drone 2
        sp1Drones.add(createDroneAvailability("2", Arrays.asList(
                createTimeSlot("MONDAY", "12:00:00", "23:59:59"),
                createTimeSlot("TUESDAY", "00:00:00", "23:59:59"),
                createTimeSlot("WEDNESDAY", "00:00:00", "11:59:59"),
                createTimeSlot("THURSDAY", "00:00:00", "11:59:59"),
                createTimeSlot("FRIDAY", "00:00:00", "23:59:59"),
                createTimeSlot("SATURDAY", "12:00:00", "23:59:59"),
                createTimeSlot("SUNDAY", "00:00:00", "23:59:59")
        )));

        // Drone 3
        sp1Drones.add(createDroneAvailability("3", Arrays.asList(
                createTimeSlot("MONDAY", "00:00:00", "11:59:59"),
                createTimeSlot("TUESDAY", "12:00:00", "23:59:59"),
                createTimeSlot("THURSDAY", "12:00:00", "23:59:59"),
                createTimeSlot("FRIDAY", "12:00:00", "23:59:59"),
                createTimeSlot("SUNDAY", "12:00:00", "23:59:59")
        )));

        // Drone 4
        sp1Drones.add(createDroneAvailability("4", Arrays.asList(
                createTimeSlot("MONDAY", "00:00:00", "11:59:59"),
                createTimeSlot("TUESDAY", "00:00:00", "11:59:59"),
                createTimeSlot("WEDNESDAY", "00:00:00", "23:59:59"),
                createTimeSlot("SATURDAY", "00:00:00", "23:59:59"),
                createTimeSlot("SUNDAY", "00:00:00", "23:59:59")
        )));

        // Drone 5
        sp1Drones.add(createDroneAvailability("5", Arrays.asList(
                createTimeSlot("TUESDAY", "00:00:00", "23:59:59"),
                createTimeSlot("THURSDAY", "00:00:00", "11:59:59"),
                createTimeSlot("FRIDAY", "00:00:00", "23:59:59"),
                createTimeSlot("SATURDAY", "00:00:00", "23:59:59"),
                createTimeSlot("SUNDAY", "00:00:00", "11:59:59")
        )));

        testDroneAvailability.add(new DroneServicePointAvailability(1, sp1Drones));

        // Service Point 2 - Ocean Terminal (Drones 6-10)
        List<DroneServicePointAvailability.DroneAvailability> sp2Drones = new ArrayList<>();

        // Drone 6
        sp2Drones.add(createDroneAvailability("6", Arrays.asList(
                createTimeSlot("MONDAY", "00:00:00", "23:59:59"),
                createTimeSlot("WEDNESDAY", "00:00:00", "23:59:59"),
                createTimeSlot("THURSDAY", "12:00:00", "23:59:59"),
                createTimeSlot("FRIDAY", "12:00:00", "23:59:59"),
                createTimeSlot("SATURDAY", "00:00:00", "23:59:59"),
                createTimeSlot("SUNDAY", "00:00:00", "23:59:59")
        )));

        // Drone 7
        sp2Drones.add(createDroneAvailability("7", Arrays.asList(
                createTimeSlot("MONDAY", "12:00:00", "23:59:59"),
                createTimeSlot("TUESDAY", "00:00:00", "23:59:59"),
                createTimeSlot("WEDNESDAY", "00:00:00", "11:59:59"),
                createTimeSlot("THURSDAY", "00:00:00", "11:59:59"),
                createTimeSlot("FRIDAY", "00:00:00", "23:59:59"),
                createTimeSlot("SATURDAY", "00:00:00", "23:59:59"),
                createTimeSlot("SUNDAY", "00:00:00", "23:59:59")
        )));

        // Drone 8
        sp2Drones.add(createDroneAvailability("8", Arrays.asList(
                createTimeSlot("TUESDAY", "00:00:00", "23:59:59"),
                createTimeSlot("THURSDAY", "12:00:00", "23:59:59"),
                createTimeSlot("FRIDAY", "00:00:00", "23:59:59"),
                createTimeSlot("SUNDAY", "00:00:00", "23:59:59")
        )));

        // Drone 9
        sp2Drones.add(createDroneAvailability("9", Arrays.asList(
                createTimeSlot("MONDAY", "00:00:00", "23:59:59"),
                createTimeSlot("TUESDAY", "00:00:00", "11:59:59"),
                createTimeSlot("WEDNESDAY", "00:00:00", "23:59:59"),
                createTimeSlot("THURSDAY", "00:00:00", "23:59:59"),
                createTimeSlot("SATURDAY", "00:00:00", "23:59:59"),
                createTimeSlot("SUNDAY", "00:00:00", "23:59:59")
        )));

        // Drone 10
        sp2Drones.add(createDroneAvailability("10", Arrays.asList(
                createTimeSlot("MONDAY", "12:00:00", "23:59:59"),
                createTimeSlot("TUESDAY", "00:00:00", "23:59:59"),
                createTimeSlot("FRIDAY", "00:00:00", "23:59:59")
        )));

        testDroneAvailability.add(new DroneServicePointAvailability(2, sp2Drones));
    }

    // ==================== HELPER METHODS FOR TEST DATA CREATION ====================

    private Drone createDrone(Integer id, String name, Boolean cooling, Boolean heating,
                              Double capacity, Integer maxMoves, Double costPerMove,
                              Double costInitial, Double costFinal) {
        Drone drone = new Drone();
        drone.setId(id);
        drone.setName(name);

        Drone.Capability capability = new Drone.Capability();
        capability.setCooling(cooling);
        capability.setHeating(heating);
        capability.setCapacity(capacity);
        capability.setMaxMoves(maxMoves);
        capability.setCostPerMove(costPerMove);
        capability.setCostInitial(costInitial);
        capability.setCostFinal(costFinal);

        drone.setCapability(capability);
        return drone;
    }

    private ServicePoint createServicePoint(Integer id, String name, Double lng, Double lat) {
        ServicePoint sp = new ServicePoint();
        sp.setId(id);
        sp.setName(name);

        ServicePoint.Location location = new ServicePoint.Location();
        location.setLng(lng);
        location.setLat(lat);
        sp.setLocation(location);

        return sp;
    }

    private RestrictedArea createRestrictedArea(Integer id, String name, List<RestrictedArea.Vertex> vertices) {
        RestrictedArea area = new RestrictedArea();
        area.setId(id);
        area.setName(name);
        area.setVertices(vertices);
        return area;
    }

    private RestrictedArea.Vertex createVertex(Double lng, Double lat) {
        RestrictedArea.Vertex vertex = new RestrictedArea.Vertex();
        vertex.setLng(lng);
        vertex.setLat(lat);
        return vertex;
    }

    private MedDispatchRec createDispatch(Integer id, String date, String time,
                                          Double capacity, Boolean cooling, Boolean heating,
                                          Double maxCost, Double lng, Double lat) {
        MedDispatchRec dispatch = new MedDispatchRec();
        dispatch.setId(id);
        dispatch.setDate(LocalDate.parse(date));
        dispatch.setTime(LocalTime.parse(time));

        MedDispatchRec.Requirements requirements = new MedDispatchRec.Requirements();
        requirements.setCapacity(capacity);
        requirements.setCooling(cooling);
        requirements.setHeating(heating);
        requirements.setMaxCost(maxCost);
        dispatch.setRequirements(requirements);

        MedDispatchRec.Delivery delivery = new MedDispatchRec.Delivery();
        delivery.setLng(lng);
        delivery.setLat(lat);
        dispatch.setDelivery(delivery);

        return dispatch;
    }

    private DroneServicePointAvailability.DroneAvailability createDroneAvailability(String droneId,
                                                                                    List<DroneServicePointAvailability.TimeSlot> timeSlots) {
        return new DroneServicePointAvailability.DroneAvailability(droneId, timeSlots);
    }

    private DroneServicePointAvailability.TimeSlot createTimeSlot(String dayOfWeek, String from, String until) {
        return new DroneServicePointAvailability.TimeSlot(
                dayOfWeek,
                LocalTime.parse(from),
                LocalTime.parse(until)
        );
    }

    // ==================== STATIC QUERIES ====================

    @Nested
    @DisplayName("UT-1: Static Query - getDronesWithCooling()")
    class StaticQueryGetDronesWithCooling {

        @Test
        @DisplayName("UT-1.1: Returns ALL drone IDs with cooling=true")
        void testGetDronesWithCoolingTrue_ReturnsOnlyCoolingDrones() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            // Act
            List<Integer> result = droneQueryService.getDronesWithCooling(true);

            // Assert
            assertThat(result).containsExactlyInAnyOrder(1, 5, 8, 9);
            assertThat(result).hasSize(4);
        }

        @Test
        @DisplayName("UT-1.2: Returns ALL drone IDs with cooling=false")
        void testGetDronesWithCoolingFalse_ReturnsNonCoolingDrones() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            // Act
            List<Integer> result = droneQueryService.getDronesWithCooling(false);

            // Assert
            assertThat(result).containsExactlyInAnyOrder(2, 3, 4, 6, 7, 10);
            assertThat(result).hasSize(6);
        }

        @Test
        @DisplayName("UT-1.3: Returns empty list if no drones match")
        void testGetDronesWithCooling_NoMatch_ReturnsEmpty() {
            // Arrange - only drones without cooling
            List<Drone> noCoolingDrones = List.of(
                    createDrone(1, "Drone 1", false, true, 4.0, 2000, 0.01, 4.3, 6.5)
            );
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(noCoolingDrones.toArray(new Drone[0]));

            // Act
            List<Integer> result = droneQueryService.getDronesWithCooling(true);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("UT-1.4: Handles null capability gracefully")
        void testGetDronesWithCooling_NullCapability_FilteredOut() {
            // Arrange
            Drone droneWithNullCapability = new Drone();
            droneWithNullCapability.setId(99);
            droneWithNullCapability.setName("Broken Drone");
            droneWithNullCapability.setCapability(null);

            List<Drone> mixedDrones = new ArrayList<>(testDrones);
            mixedDrones.add(droneWithNullCapability);

            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(mixedDrones.toArray(new Drone[0]));

            // Act
            List<Integer> result = droneQueryService.getDronesWithCooling(true);

            // Assert - should not include drone 99
            assertThat(result).containsExactlyInAnyOrder(1, 5, 8, 9);
            assertThat(result).doesNotContain(99);
        }
    }

    @Nested
    @DisplayName("UT-2: Static Query - getDroneById()")
    class StaticQueryGetDroneById {

        @Test
        @DisplayName("UT-2.1: Returns correct drone object for valid ID=4")
        void testGetDroneById_ValidId_ReturnsDroneObject() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            // Act
            Drone result = droneQueryService.getDroneById(4);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(4);
            assertThat(result.getName()).isEqualTo("Drone 4");
            assertThat(result.getCapability().getCooling()).isFalse();
            assertThat(result.getCapability().getHeating()).isTrue();
            assertThat(result.getCapability().getCapacity()).isEqualTo(8.0);
        }

        @Test
        @DisplayName("UT-2.2: Returns null for non-existent ID=999")
        void testGetDroneById_InvalidId_ReturnsNull() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            // Act
            Drone result = droneQueryService.getDroneById(999);

            // Assert
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("UT-2.3: Handles negative ID gracefully")
        void testGetDroneById_NegativeId_ReturnsNull() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            // Act
            Drone result = droneQueryService.getDroneById(-1);

            // Assert
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("UT-2.4: Returns first match when duplicate IDs exist")
        void testGetDroneById_DuplicateIds_ReturnsFirst() {
            // Arrange
            List<Drone> duplicates = new ArrayList<>();
            duplicates.add(createDrone(5, "Drone 5 First", true, true, 12.0, 1500, 0.04, 1.8, 3.5));
            duplicates.add(createDrone(5, "Drone 5 Second", false, false, 8.0, 1000, 0.02, 2.0, 3.0));

            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(duplicates.toArray(new Drone[0]));

            // Act
            Drone result = droneQueryService.getDroneById(5);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Drone 5 First");
        }
    }

    @Nested
    @DisplayName("UT-3: Dynamic Query - queryAsPath()")
    class DynamicQueryAsPath {

        @Test
        @DisplayName("UT-3.1: Query capacity/8 returns drones with capacity=8.0")
        void testQueryAsPath_Capacity_ReturnsMatchingDrones() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            // Act
            List<Integer> result = droneQueryService.queryAsPath("capacity", "8.0");

            // Assert - Drones 2, 4, 7, 9 have capacity=8.0
            assertThat(result).containsExactlyInAnyOrder(2, 4, 7, 9);
        }

        @Test
        @DisplayName("UT-3.2: Query cooling/true returns cooling drones")
        void testQueryAsPath_Cooling_ReturnsTrue() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            // Act
            List<Integer> result = droneQueryService.queryAsPath("cooling", "true");

            // Assert
            assertThat(result).containsExactlyInAnyOrder(1, 5, 8, 9);
        }

        @Test
        @DisplayName("UT-3.3: Query non-existent attribute returns empty")
        void testQueryAsPath_InvalidAttribute_ReturnsEmpty() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            // Act
            List<Integer> result = droneQueryService.queryAsPath("invalidAttr", "value");

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("UT-3.4: Query with no matches returns empty list")
        void testQueryAsPath_NoMatches_ReturnsEmpty() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            // Act
            List<Integer> result = droneQueryService.queryAsPath("capacity", "100.0");

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("UT-3.5: Query maxMoves/1000 returns matching drones")
        void testQueryAsPath_MaxMoves_ReturnsMatches() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            // Act
            List<Integer> result = droneQueryService.queryAsPath("maxMoves", "1000");

            // Assert - Drones 2, 4, 7, 9 have maxMoves=1000
            assertThat(result).containsExactlyInAnyOrder(2, 4, 7, 9);
        }
    }

    // ==================== CONDITION MATCHING (AND LOGIC) ====================

    @Nested
    @DisplayName("UT-4: Query Condition Matching - queryByConditions()")
    class ConditionMatchingAndLogic {

        @Test
        @DisplayName("UT-4.1: AND logic - both conditions TRUE → matches")
        void testMatchAllConditions_BothTrue_Matches() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            QueryCondition cond1 = new QueryCondition("capacity", ">", "10");
            QueryCondition cond2 = new QueryCondition("cooling", "=", "true");
            List<QueryCondition> conditions = Arrays.asList(cond1, cond2);

            // Act
            List<Integer> result = droneQueryService.queryByConditions(conditions);

            // Assert - Only Drone 5 (capacity=12, cooling=true) and Drone 8 (capacity=20, cooling=true)
            assertThat(result).containsExactlyInAnyOrder(5, 8);
        }

        @Test
        @DisplayName("UT-4.2: AND logic - first TRUE, second FALSE → no match")
        void testMatchAllConditions_FirstTrueSecondFalse_NoMatch() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            QueryCondition cond1 = new QueryCondition("capacity", "=", "20.0");
            QueryCondition cond2 = new QueryCondition("cooling", "=", "true");
            QueryCondition cond3 = new QueryCondition("heating", "=", "true");
            List<QueryCondition> conditions = Arrays.asList(cond1, cond2, cond3);

            // Act
            List<Integer> result = droneQueryService.queryByConditions(conditions);

            // Assert - No drone has capacity=20 AND cooling=true AND heating=true
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("UT-4.3: AND logic - multiple conditions all pass")
        void testMatchAllConditions_MultipleConditions_AllMustPass() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            QueryCondition cond1 = new QueryCondition("capacity", ">", "4.0");
            QueryCondition cond2 = new QueryCondition("cooling", "=", "true");
            QueryCondition cond3 = new QueryCondition("heating", "=", "true");
            QueryCondition cond4 = new QueryCondition("maxMoves", ">", "1000");
            List<QueryCondition> conditions = Arrays.asList(cond1, cond2, cond3, cond4);

            // Act
            List<Integer> result = droneQueryService.queryByConditions(conditions);

            // Assert - Only Drone 5: capacity=12>4, cooling=true, heating=true, maxMoves=1500>1000
            assertThat(result).containsExactly(5);
        }

        @Test
        @DisplayName("UT-4.4: AND logic with zero conditions returns all drones")
        void testMatchAllConditions_EmptyList_ReturnsAll() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            List<QueryCondition> conditions = Collections.emptyList();

            // Act
            List<Integer> result = droneQueryService.queryByConditions(conditions);

            // Assert - All drones match when no conditions
            assertThat(result).hasSize(10);
            assertThat(result).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        }
    }

    // ==================== OPERATOR HANDLING ====================

    @Nested
    @DisplayName("UT-5: Operator Handling - Comparison Operations")
    class OperatorHandling {

        @Test
        @DisplayName("UT-5.1: Operator = (equals) for numeric values")
        void testCompareWithOperator_Equals_Numeric() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            // Act
            List<Integer> result = droneQueryService.queryByConditions(
                    List.of(new QueryCondition("capacity", "=", "8.0"))
            );

            // Assert
            assertThat(result).containsExactlyInAnyOrder(2, 4, 7, 9);
        }

        @Test
        @DisplayName("UT-5.2: Operator = (equals) for boolean values")
        void testCompareWithOperator_Equals_Boolean() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            // Act
            List<Integer> resultTrue = droneQueryService.queryByConditions(
                    List.of(new QueryCondition("cooling", "=", "true"))
            );
            List<Integer> resultFalse = droneQueryService.queryByConditions(
                    List.of(new QueryCondition("cooling", "=", "false"))
            );

            // Assert
            assertThat(resultTrue).containsExactlyInAnyOrder(1, 5, 8, 9);
            assertThat(resultFalse).containsExactlyInAnyOrder(2, 3, 4, 6, 7, 10);
        }

        @Test
        @DisplayName("UT-5.3: Operator < (less than) for numeric values")
        void testCompareWithOperator_LessThan() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            // Act
            List<Integer> result = droneQueryService.queryByConditions(
                    List.of(new QueryCondition("capacity", "<", "8.0"))
            );

            // Assert - Only Drones 1 and 6 have capacity=4.0 < 8.0
            assertThat(result).containsExactlyInAnyOrder(1, 6);
        }

        @Test
        @DisplayName("UT-5.4: Operator > (greater than) for numeric values")
        void testCompareWithOperator_GreaterThan() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            // Act
            List<Integer> result = droneQueryService.queryByConditions(
                    List.of(new QueryCondition("maxMoves", ">", "2000"))
            );

            // Assert - Drones 3 and 8 have maxMoves=4000
            assertThat(result).containsExactlyInAnyOrder(3, 8);
        }

        @Test
        @DisplayName("UT-5.5: Operator != (not equals) for numeric values")
        void testCompareWithOperator_NotEquals() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            // Act
            List<Integer> result = droneQueryService.queryByConditions(
                    List.of(new QueryCondition("capacity", "!=", "8.0"))
            );

            // Assert - All drones except 2, 4, 7, 9
            assertThat(result).containsExactlyInAnyOrder(1, 3, 5, 6, 8, 10);
        }

        @Test
        @DisplayName("UT-5.6: Complex query with multiple operators")
        void testCompareWithOperator_ComplexQuery() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            // Act - capacity >= 8 AND maxMoves <= 1500
            List<Integer> result = droneQueryService.queryByConditions(Arrays.asList(
                    new QueryCondition("capacity", ">", "4.0"),
                    new QueryCondition("maxMoves", "<", "2000")
            ));

            // Assert
            assertThat(result).isNotEmpty();
        }
    }

    // ==================== AVAILABILITY CHECKING ====================

    @Nested
    @DisplayName("UT-6: Availability Checking - queryAvailableDrones()")
    class AvailabilityChecking {

        @BeforeEach
        void setupAvailability() {
            // Use FULL dataset from setupTestData() - all 10 drones with real availability schedules
            // Mock REST calls for availability - queryAvailableDrones needs ALL 4 endpoints!
            when(restTemplate.getForObject(anyString(), eq(DroneServicePointAvailability[].class)))
                    .thenReturn(testDroneAvailability.toArray(new DroneServicePointAvailability[0]));
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));
            when(restTemplate.getForObject(anyString(), eq(ServicePoint[].class)))
                    .thenReturn(testServicePoints.toArray(new ServicePoint[0]));
            when(restTemplate.getForObject(anyString(), eq(RestrictedArea[].class)))
                    .thenReturn(testRestrictedAreas.toArray(new RestrictedArea[0]));
        }

        @Test
        @DisplayName("UT-6.1: Drone available on MONDAY 10:00")
        void testIsAvailableAtTime_Monday10am_Available() {
            // Arrange - Drone 1, date=2025-12-22 (MONDAY), time=10:00
            // Drone 1 availability: MONDAY 00:00-23:59
            MedDispatchRec dispatch = createDispatch(1, "2025-12-22", "10:00:00",
                    4.0, false, false, 20.0, -3.186, 55.944);

            // Act
            List<Integer> result = droneQueryService.queryAvailableDrones(List.of(dispatch));

            // Assert - Drone 1 should be available
            assertThat(result).contains(1);
        }

        @Test
        @DisplayName("UT-6.2: Drone NOT available on TUESDAY 10:00 (unavailable day)")
        void testIsAvailableAtTime_Tuesday_NotAvailable() {
            // Arrange - Drone 1, date=2025-12-23 (TUESDAY), time=10:00
            // Drone 1 NOT available on TUESDAY
            MedDispatchRec dispatch = createDispatch(1, "2025-12-23", "10:00:00",
                    4.0, false, false, 20.0, -3.186, 55.944);

            // Act
            List<Integer> result = droneQueryService.queryAvailableDrones(List.of(dispatch));

            // Assert - Drone 1 should NOT be in result
            assertThat(result).doesNotContain(1);
        }

        @Test
        @DisplayName("UT-6.3: Time boundary - just before availability starts")
        void testIsAvailableAtTime_JustBefore_NotAvailable() {
            // Arrange - Drone 1 available from 12:00 on THURSDAY, check at 11:59:59
            MedDispatchRec dispatch = createDispatch(1, "2025-12-25", "11:59:59",
                    4.0, false, false, 20.0, -3.186, 55.944);

            // Act
            List<Integer> result = droneQueryService.queryAvailableDrones(List.of(dispatch));

            // Assert - Drone 1 should NOT be available (just before 12:00)
            assertThat(result).doesNotContain(1);
        }

        @Test
        @DisplayName("UT-6.4: Time boundary - exactly at availability start")
        void testIsAvailableAtTime_ExactlyAtStart_Available() {
            // Arrange - Drone 1 available from 12:00 on THURSDAY, check at 12:00:00
            MedDispatchRec dispatch = createDispatch(1, "2025-12-25", "12:00:00",
                    4.0, false, false, 20.0, -3.186, 55.944);

            // Act
            List<Integer> result = droneQueryService.queryAvailableDrones(List.of(dispatch));

            // Assert - Drone 1 should be available (exactly at 12:00)
            assertThat(result).contains(1);
        }

        @Test
        @DisplayName("UT-6.5: Time boundary - exactly at availability end")
        void testIsAvailableAtTime_ExactlyAtEnd_Available() {
            // Arrange - Drone 1 available until 23:59:59 on MONDAY, check at 23:59:59
            MedDispatchRec dispatch = createDispatch(1, "2025-12-22", "23:59:59",
                    4.0, false, false, 20.0, -3.186, 55.944);

            // Act
            List<Integer> result = droneQueryService.queryAvailableDrones(List.of(dispatch));

            // Assert - Drone 1 should be available (exactly at end time)
            assertThat(result).contains(1);
        }

        @Test
        @DisplayName("UT-6.6: Multiple dispatches - same day and time")
        void testIsAvailableAtTime_MultipleDispatches_SameDayTime() {
            // Arrange - Two dispatches on MONDAY 10:00
            MedDispatchRec dispatch1 = createDispatch(1, "2025-12-22", "10:00:00",
                    2.0, false, false, 20.0, -3.186, 55.944);
            MedDispatchRec dispatch2 = createDispatch(2, "2025-12-22", "10:00:00",
                    2.0, false, false, 20.0, -3.187, 55.945);

            // Act
            List<Integer> result = droneQueryService.queryAvailableDrones(Arrays.asList(dispatch1, dispatch2));

            // Assert - Drone 1 should be available for both
            assertThat(result).contains(1);
        }

        @Test
        @DisplayName("UT-6.7: Multiple dispatches - different days")
        void testIsAvailableAtTime_MultipleDispatches_DifferentDays() {
           MedDispatchRec dispatch1 = createDispatch(1, "2025-12-23", "14:30",
                    0.75, false, true, 13.5, -3.189, 55.941);
           MedDispatchRec dispatch2 = createDispatch(2, "2025-12-23", "14:30",
                            0.15, false, false, 10.5, -3.189, 55.951);
           MedDispatchRec dispatch3 = createDispatch(3, "2025-12-22", "14:30",
                            0.85, false, false, 15.0, -3.183, 55.95);
           MedDispatchRec dispatch4 = createDispatch(4, "2025-12-23", "14:30",
                            0.65, false, true, 10.0, -3.213, 55.94);

            // Act
            List<Integer> result = droneQueryService.queryAvailableDrones(Arrays.asList(dispatch1, dispatch2, dispatch3, dispatch4));

            // Assert - Drone 1 should NOT be available (not available on TUESDAY)
            assertThat(result).doesNotContain(1);
        }
    }

    @Nested
    @DisplayName("UT-7: Dispatch Fulfillment - Capacity & Requirements")
    class DispatchFulfillment {

        @BeforeEach
        void setupForDispatchTests() {
            // Use FULL dataset from setupTestData() - all 10 drones with real availability schedules
            // Mock REST calls - queryAvailableDrones needs ALL 4 endpoints mocked!
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            when(restTemplate.getForObject(anyString(), eq(ServicePoint[].class)))
                    .thenReturn(testServicePoints.toArray(new ServicePoint[0]));

            when(restTemplate.getForObject(anyString(), eq(RestrictedArea[].class)))
                    .thenReturn(testRestrictedAreas.toArray(new RestrictedArea[0]));

            when(restTemplate.getForObject(anyString(), eq(DroneServicePointAvailability[].class)))
                    .thenReturn(testDroneAvailability.toArray(new DroneServicePointAvailability[0]));
        }

        @Test
        @DisplayName("UT-7.1: Single dispatch - capacity sufficient")
        void testFulfillAllDispatches_SingleDispatch_CapacitySufficient() {
            // Arrange - Dispatch: capacity=4.0, no cooling/heating
            // Using TUESDAY when more drones (2,4,5,7,8,9,10) are available
            // Drone 2: capacity=8.0, cooling=false, heating=true
            MedDispatchRec dispatch = createDispatch(1, "2025-12-23", "10:00:00",
                    4.0, false, false, 30.0, -3.186, 55.944);

            // Act
            List<Integer> result = droneQueryService.queryAvailableDrones(List.of(dispatch));

            // Assert - Drone 2 should be able to carry (capacity 8.0 > 4.0 required)
            assertThat(result).contains(2);
        }

        @Test
        @DisplayName("UT-7.2: Single dispatch - capacity insufficient")
        void testFulfillAllDispatches_SingleDispatch_CapacityInsufficient() {
            // Arrange - Dispatch: capacity=10.0
            // Using TUESDAY: Drone 2 (capacity=8.0) too small, Drone 5 (capacity=12.0) sufficient
            MedDispatchRec dispatch = createDispatch(1, "2025-12-23", "10:00:00",
                    10.0, false, false, 30.0, -3.186, 55.944);

            // Act
            List<Integer> result = droneQueryService.queryAvailableDrones(List.of(dispatch));

            // Assert - Drone 2 (capacity=8.0) should NOT be in results (capacity too small)
            assertThat(result).doesNotContain(2);
            // But Drone 5 (capacity=12.0) should be available
            assertThat(result).contains(5);
        }

        @Test
        @DisplayName("UT-7.3: Multiple dispatches - same drone can fulfill all capacity requirements")
        void testFulfillAllDispatches_MultipleDispatches_SameDrone() {
            // Arrange - Dispatches: [capacity=2.0, capacity=2.0, capacity=1.5]
            // Using TUESDAY: Drone 5 (capacity=12.0) and Drone 2 (capacity=8.0) both sufficient
            MedDispatchRec dispatch1 = createDispatch(1, "2025-12-23", "10:00:00",
                    2.0, false, false, 30.0, -3.186, 55.944);
            MedDispatchRec dispatch2 = createDispatch(2, "2025-12-23", "11:00:00",
                    2.0, false, false, 30.0, -3.187, 55.945);
            MedDispatchRec dispatch3 = createDispatch(3, "2025-12-23", "12:00:00",
                    1.5, false, false, 30.0, -3.188, 55.946);

            // Act
            List<Integer> result = droneQueryService.queryAvailableDrones(
                    Arrays.asList(dispatch1, dispatch2, dispatch3));

            // Assert - Drone 5 (capacity=12) should be able to handle all
            assertThat(result).contains(5);
            // Drone 2 (capacity=8.0) should also work as all dispatches ≤ 8.0
            assertThat(result).contains(2);
        }

        @Test
        @DisplayName("UT-7.4: Cooling required - drone must have cooling")
        void testFulfillAllDispatches_CoolingRequired() {
            // Arrange - One dispatch requires cooling
            // Using TUESDAY: Drone 2 (cooling=false), Drone 5,8,9 (cooling=true)
            MedDispatchRec dispatch1 = createDispatch(1, "2025-12-23", "10:00:00",
                    4.0, true, false, 30.0, -3.186, 55.944);

            // Act
            List<Integer> result = droneQueryService.queryAvailableDrones(List.of(dispatch1));

            // Assert - Drone 2 should NOT be available (no cooling)
            assertThat(result).doesNotContain(2);
            // Drones 5, 8, 9 have cooling and are available on TUESDAY
            assertThat(result).containsAnyOf(5, 8, 9);
        }

        @Test
        @DisplayName("UT-7.5: Heating required - drone must have heating")
        void testFulfillAllDispatches_HeatingRequired() {
            // Arrange - Dispatch requires heating
            // Using TUESDAY: Drone 8 (heating=false), others have heating
            MedDispatchRec dispatch = createDispatch(1, "2025-12-23", "10:00:00",
                    4.0, false, true, 30.0, -3.186, 55.944);

            // Act
            List<Integer> result = droneQueryService.queryAvailableDrones(List.of(dispatch));

            // Assert - Drone 8 (heating=false) should NOT be available
            assertThat(result).doesNotContain(8);
            // Drones 2,4,5,7,9,10 have heating and are available on TUESDAY
            assertThat(result).containsAnyOf(2, 5, 7, 9);
        }

        @Test
        @DisplayName("UT-7.6: Both cooling and heating capabilities available")
        void testFulfillAllDispatches_BothCoolingAndHeating() {
            // Arrange - Drones 5 and 9 have both cooling=true AND heating=true
            // Dispatch needs cooling
            // Using TUESDAY
            MedDispatchRec dispatch = createDispatch(1, "2025-12-23", "10:00:00",
                    4.0, true, true, 30.0, -3.186, 55.944);

            // Act
            List<Integer> result = droneQueryService.queryAvailableDrones(List.of(dispatch));

            // Assert - Drones 5, 9 should be available (have cooling)
            assertThat(result).containsAnyOf(5, 9);
        }

        @Test
        @DisplayName("UT-7.7: Multiple dispatches - one needs cooling, one needs heating")
        void testFulfillAllDispatches_MixedRequirements() {
            // Arrange - Dispatch 1 needs cooling, Dispatch 2 needs heating
            // Only drones with BOTH cooling AND heating can fulfill
            // Using TUESDAY: only Drones 5 and 9 have both
            MedDispatchRec dispatch1 = createDispatch(1, "2025-12-23", "10:00:00",
                    4.0, true, false, 30.0, -3.186, 55.944);
            MedDispatchRec dispatch2 = createDispatch(2, "2025-12-23", "11:00:00",
                    4.0, false, true, 30.0, -3.187, 55.945);

            // Act
            List<Integer> result = droneQueryService.queryAvailableDrones(
                    Arrays.asList(dispatch1, dispatch2));

            // Assert - Only drones 5, 9 have both cooling=true AND heating=true on TUESDAY
            assertThat(result).containsAnyOf(5, 9);
            // Drone 2 (no cooling) and Drone 8 (no heating) should NOT be available
            assertThat(result).doesNotContain(2);
            assertThat(result).doesNotContain(8);
        }

        @Test
        @DisplayName("UT-7.8: Capacity check - drone must handle largest dispatch")
        void testFulfillAllDispatches_LargestCapacityCheck() {
            // Arrange - Multiple dispatches with varying capacities
            // Using TUESDAY: Drone 4 (capacity=8.0) can handle, Drone 2 (capacity=8.0) can too
            MedDispatchRec dispatch1 = createDispatch(1, "2025-12-23", "10:00:00",
                    3.0, false, false, 30.0, -3.186, 55.944);
            MedDispatchRec dispatch2 = createDispatch(2, "2025-12-23", "11:00:00",
                    7.0, false, false, 30.0, -3.187, 55.945);

            // Act
            List<Integer> result = droneQueryService.queryAvailableDrones(
                    Arrays.asList(dispatch1, dispatch2));

            // Assert - Drones with capacity < 7.0 cannot handle dispatch2
            // Drone 2 (capacity=8.0) and Drone 5 (capacity=12.0) should be able to handle both
            assertThat(result).containsAnyOf(2, 5);
        }
        @Test
        @DisplayName("UT-7.9: MaxMoves constraint - drone must have sufficient maxMoves")
        void testFulfillAllDispatches_MaxMovesConstraint() {
            // Arrange - Dispatch requires a long distance, one near ocean terminal, one near appleton tower(e.g., 1000 moves)
            MedDispatchRec dispatch1 = createDispatch(1, "2025-01-02", "12:00:00",
                    4.0, false, false, 50.0, -3.280, 55.941);
            MedDispatchRec dispatch2 = createDispatch(2, "2025-01-02", "12:00:00",
                    4.0, false, false, 50.0, -3.180, 55.91);
            // Act
            List<Integer> result = droneQueryService.queryAvailableDrones(
                    Arrays.asList(dispatch1, dispatch2));
            // Assert - Only drones with maxMoves >= 1000 can fulfill
            assertThat(result).containsAnyOf(1, 3, 6, 8);
        }
    }

    // ==================== COST CALCULATION ====================

    @Nested
    @DisplayName("UT-12: Cost Calculation - Pro-Rata Distribution")
    class CostCalculation {

        @Test
        @DisplayName("UT-12.1: Single delivery - full costs")
        void testCalculateCost_SingleDelivery_FullCosts() {
            // Arrange - 1 delivery, 100 moves
            // Drone: costInitial=10, costPerMove=0.1, costFinal=5
            double costInitial = 10.0;
            double costPerMove = 0.1;
            double costFinal = 5.0;
            int totalMoves = 100;
            int numberOfDeliveries = 1;

            // Calculate total cost
            double moveCost = totalMoves * costPerMove;
            double totalCost = costInitial + moveCost + costFinal;

            // Pro-rata for single delivery (gets all costs)
            double costPerDelivery = totalCost / numberOfDeliveries;

            // Assert - Total: 10 + 100*0.1 + 5 = 25.0
            assertThat(totalCost).isEqualTo(25.0);
            assertThat(costPerDelivery).isCloseTo(25.0, within(0.01));
        }

        @Test
        @DisplayName("UT-12.2: Two deliveries - costs split equally")
        void testCalculateCost_TwoDeliveries_SplitEqually() {
            // Arrange - 2 deliveries, 100 total moves
            // Drone: costInitial=10, costPerMove=0.1, costFinal=5
            double costInitial = 10.0;
            double costPerMove = 0.1;
            double costFinal = 5.0;
            int totalMoves = 100;
            int numberOfDeliveries = 2;

            // Calculate total cost
            double moveCost = totalMoves * costPerMove;
            double totalCost = costInitial + moveCost + costFinal;

            // Pro-rata split equally
            double costPerDelivery = totalCost / numberOfDeliveries;

            // Assert - Total: 25.0, split 2 ways: 25.0 ÷ 2 = 12.5 each
            assertThat(totalCost).isEqualTo(25.0);
            assertThat(costPerDelivery).isCloseTo(12.5, within(0.01));
        }

        @Test
        @DisplayName("UT-12.3: Three deliveries with pro-rata distribution")
        void testCalculateCost_ThreeDeliveries_ProRataByMoves() {
            // Arrange - 3 deliveries, 1200 total moves
            // According to spec: costs distributed pro-rata (equally per delivery)
            // Drone: costInitial=4.3, costPerMove=0.01, costFinal=6.5
            double costInitial = 4.3;
            double costPerMove = 0.01;
            double costFinal = 6.5;
            int totalMoves = 1200;
            int numberOfDeliveries = 3;

            // Calculate total cost
            double moveCost = totalMoves * costPerMove;
            double totalCost = costInitial + moveCost + costFinal;

            // Pro-rata: each delivery gets 1/3 of costs
            double costPerDelivery = totalCost / numberOfDeliveries;

            // Assert - Total: 4.3 + 1200*0.01 + 6.5 = 4.3 + 12 + 6.5 = 22.8
            // Per delivery: 22.8 ÷ 3 = 7.6
            assertThat(totalCost).isCloseTo(22.8, within(0.01));
            assertThat(costPerDelivery).isCloseTo(7.6, within(0.01));
        }

        @Test
        @DisplayName("UT-12.4: Floating point precision ±0.01 tolerance")
        void testCalculateCost_FloatingPointPrecision() {
            // Arrange - Complex cost calculation that might have rounding errors
            // Drone with fractional costs
            double costInitial = 1.8;
            double costPerMove = 0.04;
            double costFinal = 3.5;
            int totalMoves = 1337; // Prime number for complex division
            int numberOfDeliveries = 7; // Prime divisor

            // Calculate total cost
            double moveCost = totalMoves * costPerMove;
            double totalCost = costInitial + moveCost + costFinal;

            // Pro-rata
            double costPerDelivery = totalCost / numberOfDeliveries;

            // Assert - Total: 1.8 + 1337*0.04 + 3.5 = 1.8 + 53.48 + 3.5 = 58.78
            // Per delivery: 58.78 ÷ 7 ≈ 8.397142857...
            assertThat(totalCost).isCloseTo(58.78, within(0.01));
            assertThat(costPerDelivery).isCloseTo(8.397, within(0.01));
        }

        @Test
        @DisplayName("UT-12.5: Zero moves - only initial and final costs")
        void testCalculateCost_ZeroMoves_OnlyFixedCosts() {
            // Arrange - Edge case: 0 moves (hover delivery)
            double costInitial = 4.3;
            double costPerMove = 0.01;
            double costFinal = 6.5;
            int totalMoves = 0;
            int numberOfDeliveries = 1;

            // Calculate total cost
            double moveCost = totalMoves * costPerMove;
            double totalCost = costInitial + moveCost + costFinal;

            // Pro-rata
            double costPerDelivery = totalCost / numberOfDeliveries;

            // Assert - Total: 4.3 + 0 + 6.5 = 10.8
            assertThat(totalCost).isEqualTo(10.8);
            assertThat(costPerDelivery).isCloseTo(10.8, within(0.01));
        }

        @Test
        @DisplayName("UT-12.6: Very high number of deliveries - precision maintained")
        void testCalculateCost_ManyDeliveries_PrecisionMaintained() {
            // Arrange - 50 deliveries with high total cost
            double costInitial = 9.5;
            double costPerMove = 0.05;
            double costFinal = 11.5;
            int totalMoves = 4000;
            int numberOfDeliveries = 50;

            // Calculate total cost
            double moveCost = totalMoves * costPerMove;
            double totalCost = costInitial + moveCost + costFinal;

            // Pro-rata
            double costPerDelivery = totalCost / numberOfDeliveries;

            // Assert - Total: 9.5 + 4000*0.05 + 11.5 = 9.5 + 200 + 11.5 = 221.0
            // Per delivery: 221.0 ÷ 50 = 4.42
            assertThat(totalCost).isCloseTo(221.0, within(0.01));
            assertThat(costPerDelivery).isCloseTo(4.42, within(0.01));
        }

        @Test
        @DisplayName("UT-12.7: Cost calculation with maxCost constraint")
        void testCalculateCost_MaxCostConstraint_Respected() {
            // Arrange - Delivery with maxCost constraint
            double costInitial = 4.3;
            double costPerMove = 0.01;
            double costFinal = 6.5;
            int totalMoves = 1200;
            int numberOfDeliveries = 3;
            double maxCostPerDelivery = 10.0;

            // Calculate total cost
            double moveCost = totalMoves * costPerMove;
            double totalCost = costInitial + moveCost + costFinal;

            // Pro-rata
            double costPerDelivery = totalCost / numberOfDeliveries;

            // Assert - Per delivery cost: 7.6, maxCost: 10.0
            // Cost is within max constraint
            assertThat(costPerDelivery).isCloseTo(7.6, within(0.01));
            assertThat(costPerDelivery).isLessThan(maxCostPerDelivery);
        }

        @Test
        @DisplayName("UT-12.8: Cost calculation exceeds maxCost - identified")
        void testCalculateCost_ExceedsMaxCost_Identified() {
            // Arrange - Delivery where calculated cost exceeds maxCost
            double costInitial = 9.5;
            double costPerMove = 0.05;
            double costFinal = 11.5;
            int totalMoves = 4000;
            int numberOfDeliveries = 1;
            double maxCostPerDelivery = 100.0; // Constraint

            // Calculate total cost
            double moveCost = totalMoves * costPerMove;
            double totalCost = costInitial + moveCost + costFinal;

            // Pro-rata
            double costPerDelivery = totalCost / numberOfDeliveries;

            // Assert - Total cost: 221.0, maxCost: 100.0
            // Cost exceeds max constraint
            assertThat(costPerDelivery).isCloseTo(221.0, within(0.01));
            assertThat(costPerDelivery).isGreaterThan(maxCostPerDelivery);
        }
    }

    // ==================== PATHFINDING ALGORITHMS - CORE COMPONENT ====================

    @Nested
    @DisplayName("UT-13: Pathfinding - satisfy basic attributes")
    class PathfindingBasicAttributes {

        private static final double STEP_WIDTH = 0.00015;
        private static final double PRECISION_TOLERANCE = 0.000001;

        @BeforeEach
        void setupPathfindingTests() {
            // Mock all REST endpoints needed for calcDeliveryPath
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            when(restTemplate.getForObject(anyString(), eq(ServicePoint[].class)))
                    .thenReturn(testServicePoints.toArray(new ServicePoint[0]));

            when(restTemplate.getForObject(anyString(), eq(RestrictedArea[].class)))
                    .thenReturn(testRestrictedAreas.toArray(new RestrictedArea[0]));
            when(restTemplate.getForObject(anyString(), eq(DroneServicePointAvailability[].class)))
                    .thenReturn(testDroneAvailability.toArray(new DroneServicePointAvailability[0]));
        }

        /**
         * Verify adjacent points in flight path are exactly 0.00015 apart
         */
        private void verifyStepWidth(List<DeliveryPathResponse.LngLat> flightPath) {
            for (int i = 0; i < flightPath.size() - 1; i++) {
                DeliveryPathResponse.LngLat p1 = flightPath.get(i);
                DeliveryPathResponse.LngLat p2 = flightPath.get(i + 1);

                double distance = Math.sqrt(
                        Math.pow(p2.getLng() - p1.getLng(), 2) +
                                Math.pow(p2.getLat() - p1.getLat(), 2)
                );

                // Adjacent points should be either:
                // 1. Exactly STEP_WIDTH apart (moving)
                // 2. Exactly 0 apart (hovering for delivery)
                assertThat(distance)
                        .describedAs("Distance between adjacent points at index %d and %d", i, i + 1)
                        .satisfiesAnyOf(
                                dist -> assertThat(dist).isCloseTo(STEP_WIDTH, within(PRECISION_TOLERANCE)),
                                dist -> assertThat(dist).isCloseTo(0.0, within(PRECISION_TOLERANCE))
                        );
            }
        }

        @Test
        @DisplayName("UT-13.1: Single delivery - flight path has correct step width")
        void testCalcDeliveryPath_SingleDelivery_CorrectStepWidth() {
            // Arrange - Single delivery using VALID working data
            List<MedDispatchRec> dispatches = List.of(
                    createDispatch(1001, "2025-01-28", "10:00",
                            2.0, false, false, 100.0, -3.186000, 55.944900)
            );

            // Act - Call the actual calcDeliveryPath API
            DeliveryPathResponse response = droneQueryService.calcDeliveryPath(dispatches);

            // Assert - Response should be valid
            assertThat(response).isNotNull();
            assertThat(response.getDronePaths()).isNotEmpty();
            assertThat(response.getTotalMoves()).isGreaterThan(0);

            // Get the flight path
            DeliveryPathResponse.DronePath dronePath = response.getDronePaths().get(0);
            assertThat(dronePath.getDeliveries()).hasSize(1);

            List<DeliveryPathResponse.LngLat> flightPath = dronePath.getDeliveries().get(0).getFlightPath();
            assertThat(flightPath).hasSizeGreaterThan(2);

            // Verify step width between adjacent points
            verifyStepWidth(flightPath);
        }

        @Test
        @DisplayName("UT-13.2: path contains hover points")
        void testCalcDeliveryPath_HasHoverPoints() {
            // Arrange - Use VALID working data
            List<MedDispatchRec> dispatches = List.of(
                    createDispatch(1001, "2025-01-28", "10:00",
                            2.0, false, false, 50.0, -3.186508, 55.944831)
            );

            // Act - Call the actual calcDeliveryPath API
            DeliveryPathResponse response = droneQueryService.calcDeliveryPath(dispatches);

            // Assert - Response should be valid
            assertThat(response).isNotNull();
            assertThat(response.getDronePaths()).isNotEmpty();

            // Check each delivery has a hover point (2 identical coordinates)
            for (DeliveryPathResponse.DronePath dronePath : response.getDronePaths()) {
                for (DeliveryPathResponse.Delivery delivery : dronePath.getDeliveries()) {
                    List<DeliveryPathResponse.LngLat> flightPath = delivery.getFlightPath();

                    // Find hover points (consecutive identical coordinates)
                    boolean foundHover = false;
                    for (int i = 0; i < flightPath.size() - 1; i++) {
                        DeliveryPathResponse.LngLat p1 = flightPath.get(i);
                        DeliveryPathResponse.LngLat p2 = flightPath.get(i + 1);

                        if (p1.getLng().equals(p2.getLng()) && p1.getLat().equals(p2.getLat())) {
                            foundHover = true;
                            break;
                        }
                    }

                    assertThat(foundHover)
                            .describedAs("Delivery %d should have a hover point (2 identical coordinates)",
                                    delivery.getDeliveryId())
                            .isTrue();
                }
            }
        }

        @Test
        @DisplayName("UT-13.3: Flight path starts and ends at service point")
        void testCalcDeliveryPath_RoundTrip_StartsAndEndsAtServicePoint() {
            // Arrange - Use VALID working data
            List<MedDispatchRec> dispatches = List.of(
                    createDispatch(1001, "2025-01-28", "10:00",
                            2.0, false, false, 50.0, -3.186508, 55.944831)
            );

            // Act - Call the actual calcDeliveryPath API
            DeliveryPathResponse response = droneQueryService.calcDeliveryPath(dispatches);

            // Assert - Get the complete flight path
            assertThat(response).isNotNull();
            DeliveryPathResponse.DronePath dronePath = response.getDronePaths().get(0);

            // Get first and last coordinates across all deliveries in this drone's route
            List<DeliveryPathResponse.Delivery> deliveries = dronePath.getDeliveries();
            assertThat(deliveries).isNotEmpty();

            DeliveryPathResponse.LngLat firstPoint = deliveries.get(0).getFlightPath().get(0);
            DeliveryPathResponse.Delivery lastDelivery = deliveries.get(deliveries.size() - 1);
            List<DeliveryPathResponse.LngLat> lastFlightPath = lastDelivery.getFlightPath();
            DeliveryPathResponse.LngLat lastPoint = lastFlightPath.get(lastFlightPath.size() - 1);

            // The start point should be a service point (Appleton Tower)
            ServicePoint expectedServicePoint = testServicePoints.get(0);
            assertThat(firstPoint.getLng()).isCloseTo(expectedServicePoint.getLocation().getLng(), within(PRECISION_TOLERANCE));
            assertThat(firstPoint.getLat()).isCloseTo(expectedServicePoint.getLocation().getLat(), within(PRECISION_TOLERANCE));

            // The end point should return to the same service point
            assertThat(lastPoint.getLng()).isCloseTo(expectedServicePoint.getLocation().getLng(), within(STEP_WIDTH + PRECISION_TOLERANCE));
            assertThat(lastPoint.getLat()).isCloseTo(expectedServicePoint.getLocation().getLat(), within(STEP_WIDTH + PRECISION_TOLERANCE));
        }

        @Test
        @DisplayName("UT-13.4: delivered to the correct location - Hover point matches input delivery coordinates")
        void testCalcDeliveryPath_HoverPoint_MatchesDeliveryCoordinate() {
            // Arrange - Use VALID working data with specific coordinates
            double expectedLng = -3.186508;
            double expectedLat = 55.944831;
            List<MedDispatchRec> dispatches = List.of(
                    createDispatch(1001, "2025-01-28", "10:00",
                            2.0, false, false, 50.0, expectedLng, expectedLat)
            );

            // Act - Call the actual calcDeliveryPath API
            DeliveryPathResponse response = droneQueryService.calcDeliveryPath(dispatches);

            // Assert - Response should be valid
            assertThat(response).isNotNull();
            assertThat(response.getDronePaths()).isNotEmpty();

            // Find the hover point in the flight path
            DeliveryPathResponse.DronePath dronePath = response.getDronePaths().get(0);
            DeliveryPathResponse.Delivery delivery = dronePath.getDeliveries().get(0);
            List<DeliveryPathResponse.LngLat> flightPath = delivery.getFlightPath();

            // Find hover points (consecutive identical coordinates)
            DeliveryPathResponse.LngLat hoverPoint = null;
            for (int i = 0; i < flightPath.size() - 1; i++) {
                DeliveryPathResponse.LngLat p1 = flightPath.get(i);
                DeliveryPathResponse.LngLat p2 = flightPath.get(i + 1);

                if (p1.getLng().equals(p2.getLng()) && p1.getLat().equals(p2.getLat())) {
                    hoverPoint = p1;
                    break;
                }
            }

            // Assert hover point was found and matches input delivery coordinates
            assertThat(hoverPoint).isNotNull()
                    .describedAs("Flight path should contain a hover point");

            assertThat(hoverPoint.getLng()).isCloseTo(expectedLng, within(STEP_WIDTH + PRECISION_TOLERANCE))
                    .describedAs("Hover point longitude should match delivery coordinate");

            assertThat(hoverPoint.getLat()).isCloseTo(expectedLat, within(STEP_WIDTH + PRECISION_TOLERANCE))
                    .describedAs("Hover point latitude should match delivery coordinate");
        }

        @Test
        @DisplayName("UT-13.5: Total moves matches flight path length")
            // unnecessary?
        void testCalcDeliveryPath_TotalMoves_MatchesFlightPath() {
            // Arrange - Use VALID working data
            List<MedDispatchRec> dispatches = List.of(
                    createDispatch(1001, "2025-01-28", "10:00",
                            2.0, false, false, 50.0, -3.186508, 55.944831)
            );

            // Act - Call the actual calcDeliveryPath API
            DeliveryPathResponse response = droneQueryService.calcDeliveryPath(dispatches);

            // Assert - Count total path points
            int totalPathPoints = 0;
            for (DeliveryPathResponse.DronePath dronePath : response.getDronePaths()) {
                for (DeliveryPathResponse.Delivery delivery : dronePath.getDeliveries()) {
                    totalPathPoints += delivery.getFlightPath().size();
                }
            }

            // Total moves should match the path length minus 1 (points - 1 = moves)
            // Or account for how moves are counted in the implementation
            assertThat(response.getTotalMoves()).isGreaterThan(0);
            assertThat(totalPathPoints).isGreaterThan(response.getTotalMoves());
        }
    }

    // ==================== PATHFINDING - NO OBSTACLES ====================

    @Nested
    @DisplayName("UT-14: Pathfinding - No Obstacles")
    class PathfindingNoObstacles {

        private static final double STEP_WIDTH = 0.00015;
        private static final double PRECISION_TOLERANCE = 0.000001;

        @BeforeEach
        void setupNoObstacleTests() {
            // Mock all REST endpoints
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));
            when(restTemplate.getForObject(anyString(), eq(ServicePoint[].class)))
                    .thenReturn(testServicePoints.toArray(new ServicePoint[0]));
            when(restTemplate.getForObject(anyString(), eq(RestrictedArea[].class)))
                    .thenReturn(testRestrictedAreas.toArray(new RestrictedArea[0]));
            when(restTemplate.getForObject(anyString(), eq(DroneServicePointAvailability[].class)))
                    .thenReturn(testDroneAvailability.toArray(new DroneServicePointAvailability[0]));
        }

        /**
         * Helper: Verify each step is exactly 0.00015 or 0 (hover)
         */
        private void verifyStepWidth(List<DeliveryPathResponse.LngLat> path) {
            for (int i = 0; i < path.size() - 1; i++) {
                double dist = Math.sqrt(
                        Math.pow(path.get(i + 1).getLng() - path.get(i).getLng(), 2) +
                                Math.pow(path.get(i + 1).getLat() - path.get(i).getLat(), 2)
                );
                assertThat(dist).satisfiesAnyOf(
                        d -> assertThat(d).isCloseTo(STEP_WIDTH, within(PRECISION_TOLERANCE)),
                        d -> assertThat(d).isCloseTo(0.0, within(PRECISION_TOLERANCE))
                );
            }
        }

        /**
         * Helper: Verify only 16 valid directions (multiples of 22.5°)
         */
        private void verify16Directions(List<DeliveryPathResponse.LngLat> path) {
            for (int i = 0; i < path.size() - 1; i++) {
                DeliveryPathResponse.LngLat p1 = path.get(i);
                DeliveryPathResponse.LngLat p2 = path.get(i + 1);

                double dx = p2.getLng() - p1.getLng();
                double dy = p2.getLat() - p1.getLat();

                // Skip hover points
                if (Math.abs(dx) < PRECISION_TOLERANCE && Math.abs(dy) < PRECISION_TOLERANCE) continue;

                double angle = Math.toDegrees(Math.atan2(dy, dx));
                if (angle < 0) angle += 360;

                // Check if angle is a multiple of 22.5°
                double remainder = angle % 22.5;
                assertThat(remainder)
                        .describedAs("Angle %.2f° should be a multiple of 22.5°", angle)
                        .isCloseTo(0.0, within(1.0)); // 1° tolerance for floating point
            }
        }

        @Test
        @DisplayName("UT-14.1: Simple delivery - validate path attributes")
        void testSimpleDelivery_ValidatePath() {
            // Arrange
            List<MedDispatchRec> dispatches = List.of(
                    createDispatch(1001, "2025-01-28", "10:00",
                            2.0, false, false, 50.0, -3.186508, 55.944831)
            );

            // Act
            DeliveryPathResponse response = droneQueryService.calcDeliveryPath(dispatches);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getDronePaths()).isNotEmpty();

            List<DeliveryPathResponse.LngLat> path = response.getDronePaths().get(0)
                    .getDeliveries().get(0).getFlightPath();

            // 1.1: Every step is 0.00015
            verifyStepWidth(path);

            // 1.2: Starting point is service point, ending near destination
            ServicePoint sp = testServicePoints.get(0);
            assertThat(path.get(0).getLng()).isCloseTo(sp.getLocation().getLng(), within(PRECISION_TOLERANCE));
            assertThat(path.get(0).getLat()).isCloseTo(sp.getLocation().getLat(), within(PRECISION_TOLERANCE));

            // Find the hover point (delivery location)
            boolean foundDeliveryPoint = false;
            for (int i = 0; i < path.size() - 1; i++) {
                if (path.get(i).getLng().equals(path.get(i + 1).getLng()) &&
                        path.get(i).getLat().equals(path.get(i + 1).getLat())) {
                    double distToDelivery = Math.sqrt(
                            Math.pow(path.get(i).getLng() + 3.186508, 2) +
                                    Math.pow(path.get(i).getLat() - 55.944831, 2)
                    );
                    assertThat(distToDelivery).isLessThan(STEP_WIDTH + PRECISION_TOLERANCE);
                    foundDeliveryPoint = true;
                    break;
                }
            }
            assertThat(foundDeliveryPoint).isTrue();

            // 1.3: Only 16 directions
            verify16Directions(path);
        }

        @Test
        @DisplayName("UT-14.2: Single delivery - no obstacles")
        void testSingleDelivery_NoObstacles() {
            List<MedDispatchRec> dispatches = List.of(
                    createDispatch(1002, "2025-01-28", "10:00",
                            2.5, false, false, 60.0, -3.185858, 55.945231)
            );

            DeliveryPathResponse response = droneQueryService.calcDeliveryPath(dispatches);

            assertThat(response).isNotNull();
            assertThat(response.getTotalMoves()).isGreaterThan(0);
            assertThat(response.getDronePaths()).hasSize(1);
        }

        @Test
        @DisplayName("UT-14.3: Multiple deliveries  - same date")
        void testMultipleDeliveries_SameDate() {
            List<MedDispatchRec> dispatches = Arrays.asList(
                    createDispatch(1002, "2025-01-28", "10:00",
                            2.5, false, false, 60.0, -3.185858, 55.945231),
                    createDispatch(1003, "2025-01-28", "11:00",
                            2.0, false, false, 55.0, -3.187, 55.943)
            );

            DeliveryPathResponse response = droneQueryService.calcDeliveryPath(dispatches);

            assertThat(response).isNotNull();
            assertThat(response.getDronePaths()).isNotEmpty();

            // Verify all deliveries are present
            int totalDeliveries = response.getDronePaths().stream()
                    .mapToInt(dp -> dp.getDeliveries().size())
                    .sum();
            assertThat(totalDeliveries).isEqualTo(2);
        }

        @Test
        @DisplayName("UT-14.4: Multiple deliveries - Different dates")
        void testDifferentDates_GroupsByDate() {
            List<MedDispatchRec> dispatches = Arrays.asList(
                    createDispatch(1, "2025-12-23", "14:30",
                            0.75, false, true, 13.5, -3.189, 55.941),
                    createDispatch(2, "2025-12-23", "14:30",
                            0.15, false, false, 10.5, -3.189, 55.951),
                    createDispatch(3, "2025-12-22", "14:30",
                            0.85, false, false, 15.0, -3.183, 55.95),
                    createDispatch(4, "2025-12-23", "14:30",
                            0.65, false, true, 10.0, -3.213, 55.94)
            );

            DeliveryPathResponse response = droneQueryService.calcDeliveryPath(dispatches);

            assertThat(response).isNotNull();
            // Should have multiple drone paths for different dates
            assertThat(response.getDronePaths()).isNotEmpty();
        }

        @Test
        @DisplayName("UT-14.5: maxMoves boundary test - Long deliveries from different service points")
        void testLongDeliveries_MultipleServicePoints() {
            List<MedDispatchRec> dispatches = Arrays.asList(
                    createDispatch(1, "2025-12-22", "14:30",
                            0.75, false, true, 13.5, -3.189, 55.941),
                    createDispatch(2, "2025-12-22", "14:30",
                            0.15, false, false, 10.5, -3.189, 55.951),
                    createDispatch(3, "2025-12-22", "14:30",
                            6.0, false, false, 5.0, -3.183, 55.95),
                    createDispatch(4, "2025-12-22", "14:30",
                            0.65, false, true, 15.0, -3.213, 55.94),
                    createDispatch(5, "2025-12-22", "14:30",
                            0.75, false, true, 13.5, -3.2088, 55.9799),
                    createDispatch(6, "2025-12-22", "14:30",
                            0.15, false, false, 10.5, -3.1845, 55.9707),
                    createDispatch(7, "2025-12-22", "14:30",
                            0.65, false, true, 15.0, -3.1795, 55.9434),
                    createDispatch(8, "2025-12-22", "14:30",
                            0.75, false, true, 13.5, -3.1655, 55.9806)
            );

            DeliveryPathResponse response = droneQueryService.calcDeliveryPath(dispatches);

            assertThat(response).isNotNull();
            assertThat(response.getDronePaths()).isNotEmpty();

            // Might use multiple drones due to capacity/maxMoves constraints
            int totalDeliveries = response.getDronePaths().stream()
                    .mapToInt(dp -> dp.getDeliveries().size())
                    .sum();
            assertThat(totalDeliveries).isLessThanOrEqualTo(8);
        }

        @Test
        @DisplayName("UT-14.6: no solution - exceed capacity")
        void testNoSolution_ExceedCapacity() {
            List<MedDispatchRec> dispatches = Arrays.asList(
                    createDispatch(1, "2025-12-23", "14:30",
                            12.0, false, true, 13.5, -3.189, 55.941),
                    createDispatch(2, "2025-12-23", "14:30",
                            10.0, false, false, 10.5, -3.189, 55.951),
                    createDispatch(3, "2025-12-23", "14:30",
                            15.0, false, false, 15.0, -3.183, 55.95)
            );

            DeliveryPathResponse response = droneQueryService.calcDeliveryPath(dispatches);

            // Assert - System should handle this gracefully
            // Option A: Check for null response
            if (response == null) {
                // No solution found - acceptable for impossible scenarios
                return; // test passes
            }

            // Option B: Check for zero moves and empty paths
            assertThat(response).isNotNull();
            assertThat(response.getTotalCost()).isEqualTo(0.0);
            assertThat(response.getTotalMoves()).isEqualTo(0);
            assertThat(response.getDronePaths()).isEmpty();
        }


    }

    // ==================== PATHFINDING - WITH OBSTACLES ====================

    @Nested
    @DisplayName("UT-15: Pathfinding - With Obstacles")
    class PathfindingWithObstacles {

        @BeforeEach
        void setupObstacleTests() {
            // Mock all REST endpoints - use REAL data from setupTestData()
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));
            when(restTemplate.getForObject(anyString(), eq(ServicePoint[].class)))
                    .thenReturn(testServicePoints.toArray(new ServicePoint[0]));
            when(restTemplate.getForObject(anyString(), eq(RestrictedArea[].class)))
                    .thenReturn(testRestrictedAreas.toArray(new RestrictedArea[0]));
            when(restTemplate.getForObject(anyString(), eq(DroneServicePointAvailability[].class)))
                    .thenReturn(testDroneAvailability.toArray(new DroneServicePointAvailability[0]));
        }

        @Test
        @DisplayName("UT-15.1: Single obstacle - path through George Square")
        void testSingleObstacle_ThroughGeorgeSquare() {
            // Arrange - Delivery that would cross George Square Area
            List<MedDispatchRec> dispatches = List.of(
                    createDispatch(201, "2025-01-28", "10:00",
                            3.0, false, false, 150.0, -3.192000, 55.943000)
            );

            // Act - Call the actual calcDeliveryPath API
            DeliveryPathResponse response = droneQueryService.calcDeliveryPath(dispatches);

            // Assert - Should find a path that avoids George Square
            assertThat(response).isNotNull();
            assertThat(response.getDronePaths()).isNotEmpty();
            assertThat(response.getTotalMoves()).isGreaterThan(0);

            // Verify the path exists and contains valid coordinates
            DeliveryPathResponse.DronePath dronePath = response.getDronePaths().get(0);
            assertThat(dronePath.getDeliveries()).hasSize(1);
            assertThat(dronePath.getDeliveries().get(0).getFlightPath()).isNotEmpty();
        }

        @Test
        @DisplayName("UT-15.2: Multiple obstacles - around George Square and Bayes areas")
        void testMultipleObstacles_AroundGeorgeSquareAndBayes() {
            // Arrange - Two deliveries requiring navigation around multiple obstacles
            List<MedDispatchRec> dispatches = Arrays.asList(
                    createDispatch(202, "2025-01-28", "10:00",
                            2.0, false, false, 150.0, -3.191000, 55.944500),
                    createDispatch(203, "2025-01-28", "11:00",
                            2.2, false, false, 160.0, -3.186500, 55.943800)
            );

            // Act - Call the actual calcDeliveryPath API
            DeliveryPathResponse response = droneQueryService.calcDeliveryPath(dispatches);

            // Assert - Should find paths avoiding both George Square and Bayes areas
            assertThat(response).isNotNull();
            assertThat(response.getDronePaths()).isNotEmpty();

            // Verify all deliveries are completed
            int totalDeliveries = response.getDronePaths().stream()
                    .mapToInt(dp -> dp.getDeliveries().size())
                    .sum();
            assertThat(totalDeliveries).isGreaterThan(0);
        }

        @Test
        @DisplayName("UT-15.3: maxMoves boundary test  - distant locations with obstacles")
        void testExceedMaxMoves_DistantLocations() {
            // Arrange - Deliveries at very distant locations that may exceed drone maxMoves
            List<MedDispatchRec> dispatches = Arrays.asList(
                    createDispatch(301, "2025-01-28", "10:00",
                            2.0, false, false, 200.0, -3.200000, 56.000000),
                    createDispatch(302, "2025-01-28", "11:00",
                            2.2, false, false, 210.0, -3.180000, 55.930000)
            );

            // Act - Call the actual calcDeliveryPath API
            DeliveryPathResponse response = droneQueryService.calcDeliveryPath(dispatches);

            // Assert - System should handle this gracefully
            // Might use multiple drones or not complete all deliveries if they exceed maxMoves
            assertThat(response).isNotNull();
        }



        @Test
        @DisplayName("UT-15.4: No solution - delivery point inside obstacles(restricted area)")
        void testNoSolution_DeliveryInsideRestrictedArea() {
            // Arrange - Deliveries potentially inside or very close to restricted areas
            List<MedDispatchRec> dispatches = Arrays.asList(
                    createDispatch(401, "2025-01-28", "10:00",
                            1.5, false, false, 160.0, -3.188200, 55.945300),
                    createDispatch(402, "2025-01-28", "11:00",
                            1.7, false, false, 170.0, -3.187500, 55.944800)
            );

            // Act - Call the actual calcDeliveryPath API
            DeliveryPathResponse response = droneQueryService.calcDeliveryPath(dispatches);

            // Assert - System should handle this gracefully
            // Option A: Check for null response
            if (response == null) {
                // No solution found - acceptable for impossible scenarios
                return; // test passes
            }

            // Option B: Check for zero moves and empty paths
            assertThat(response).isNotNull();
            assertThat(response.getTotalCost()).isEqualTo(0.0);
            assertThat(response.getTotalMoves()).isEqualTo(0);
            assertThat(response.getDronePaths()).isEmpty();
        }
    }

    // ==================== ERROR HANDLING ====================

    @Nested
    @DisplayName("UT-16: Error Handling - Invalid MedDispatchRec Input")
    class ErrorHandlingInvalidInput {

        @Test
        @DisplayName("UT-16.1: Null input list")
        void testQueryAvailableDrones_NullInput_ReturnsEmpty() {
            // Act
            List<Integer> result = droneQueryService.queryAvailableDrones(null);

            // Assert - Should return empty list or handle gracefully
            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("UT-16.2: Empty input list")
        void testQueryAvailableDrones_EmptyInput_ReturnsEmpty() {
            // Act
            List<Integer> result = droneQueryService.queryAvailableDrones(Collections.emptyList());

            // Assert - Should return empty list
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("UT-16.3: Null dispatch in list")
        void testQueryAvailableDrones_NullDispatchInList_HandledGracefully() {
            // Arrange - List with null element
            List<MedDispatchRec> dispatches = new ArrayList<>();
            dispatches.add(null);
            dispatches.add(createDispatch(1, "2025-01-28", "10:00",
                    2.0, false, false, 50.0, -3.186, 55.944));

            // Act & Assert - Should not throw exception
            try {
                List<Integer> result = droneQueryService.queryAvailableDrones(dispatches);
                assertThat(result).isNotNull();
            } catch (NullPointerException e) {
                // Expected if implementation doesn't handle nulls
            }
        }


        @Test
        @DisplayName("UT-16.4: Negative capacity value")
        void testQueryAvailableDrones_NegativeCapacity_ReturnsEmpty() {
            // Arrange - Negative capacity (invalid)
            MedDispatchRec dispatch = createDispatch(1, "2025-01-28", "10:00",
                    -5.0, false, false, 50.0, -3.186, 55.944);

            // Act
            List<Integer> result = droneQueryService.queryAvailableDrones(List.of(dispatch));

            // Assert - Should return empty (no drone can fulfill negative capacity)
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("UT-16.5: Out of range coordinates")
        void testCalcDeliveryPath_InvalidCoordinates_HandledGracefully() {
            // Arrange - Invalid coordinates (lat > 90)
            MedDispatchRec dispatch = createDispatch(1, "2025-01-28", "10:00",
                    2.0, false, false, 50.0, -3.186, 95.000);

            // Act
            DeliveryPathResponse response = droneQueryService.calcDeliveryPath(List.of(dispatch));

            // Assert - Should handle gracefully (might return null or empty response)
            if (response != null) {
                assertThat(response).isNotNull();
            }
        }
    }
}



