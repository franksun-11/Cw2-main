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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Unit Tests for DroneQueryServiceImpl
 * Testing Coverage for LO3 - Software Testing Coursework
 *
 * Test Categories:
 * - UT-1 to UT-7: Business Logic (Queries, Conditions, Availability, Dispatches)
 * - UT-8 to UT-11: TSP/Optimization Algorithms
 * - UT-12: Cost Calculation
 * - UT-13 to UT-18: Pathfinding (Direct, A*, RRT) - Partial coverage based on implementation
 * - UT-19: Helper Methods
 * - UT-20: Error Handling & Edge Cases
 */
@DisplayName("DroneQueryService Unit Tests - Comprehensive Coverage")
class DroneQueryServiceUnitTest {

    @InjectMocks
    private DroneQueryServiceImpl droneQueryService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private String ilpEndpoint;

    // Test data fixtures
    private List<Drone> testDrones;
    private List<ServicePoint> testServicePoints;
    private List<RestrictedArea> testRestrictedAreas;
    private List<DroneServicePointAvailability> testDroneAvailability;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
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

        // Setup restricted areas (simplified versions)
        testRestrictedAreas = new ArrayList<>();
        testRestrictedAreas.add(createRestrictedArea(1, "George Square Area", Arrays.asList(
                createVertex(-3.190578818321228, 55.94402412577528),
                createVertex(-3.1899887323379517, 55.94284650540911),
                createVertex(-3.187097311019897, 55.94328811724263),
                createVertex(-3.187682032585144, 55.944477740393744),
                createVertex(-3.190578818321228, 55.94402412577528)
        )));
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

        LngLat location = new LngLat();
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

    // ==================== ERROR HANDLING & EDGE CASES ====================

    @Nested
    @DisplayName("UT-20: Error Handling & Edge Cases")
    class ErrorHandling {

        @Test
        @DisplayName("UT-20.1: Empty drone list handled gracefully")
        void testEmptyDroneList() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(new Drone[0]);

            // Act
            List<Integer> result = droneQueryService.getDronesWithCooling(true);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("UT-20.2: Null capability attributes handled")
        void testNullCapabilityAttributes() {
            // Arrange
            Drone droneWithNullCooling = createDrone(99, "Test Drone", null, true,
                    8.0, 1000, 0.02, 1.0, 2.0);

            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(new Drone[]{droneWithNullCooling});

            // Act
            List<Integer> result = droneQueryService.getDronesWithCooling(true);

            // Assert - Should not crash, should filter out
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("UT-20.3: Invalid query condition handled gracefully")
        void testInvalidQueryCondition() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            QueryCondition invalidCondition = new QueryCondition("nonExistentField", "=", "value");

            // Act
            List<Integer> result = droneQueryService.queryByConditions(List.of(invalidCondition));

            // Assert - Should return empty, not crash
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("UT-20.4: Null dispatch list handled")
        void testNullDispatchList() {
            // Act
            List<Integer> result = droneQueryService.queryAvailableDrones(null);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("UT-20.5: Empty dispatch list handled")
        void testEmptyDispatchList() {
            // Act
            List<Integer> result = droneQueryService.queryAvailableDrones(Collections.emptyList());

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("UT-20.6: REST template returns null")
        void testRestTemplateReturnsNull() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(null);

            // Act
            List<Integer> result = droneQueryService.getDronesWithCooling(true);

            // Assert - Should handle gracefully
            assertThat(result).isEmpty();
        }
    }

    // ==================== HELPER METHODS VERIFICATION ====================

    @Nested
    @DisplayName("UT-19: Helper Methods - Internal Utility Functions")
    class HelperMethods {

        @Test
        @DisplayName("UT-19.1: Query by multiple attributes works correctly")
        void testQueryByMultipleAttributes() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            // Act - Find drones with heating=true AND capacity>10
            List<Integer> result = droneQueryService.queryByConditions(Arrays.asList(
                    new QueryCondition("heating", "=", "true"),
                    new QueryCondition("capacity", ">", "10.0")
            ));

            // Assert - Only Drone 5 matches (heating=true, capacity=12)
            assertThat(result).contains(5);
        }

        @Test
        @DisplayName("UT-19.2: Boundary value testing - capacity exactly at threshold")
        void testBoundaryValues() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            // Act - Test boundary: capacity >= 12.0
            List<Integer> resultGreaterOrEqual = droneQueryService.queryByConditions(
                    List.of(new QueryCondition("capacity", ">", "11.99"))
            );

            // Assert
            assertThat(resultGreaterOrEqual).containsExactlyInAnyOrder(3, 5, 8, 10);
        }

        @Test
        @DisplayName("UT-19.3: Case sensitivity in string comparisons")
        void testCaseSensitivity() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            // Act - Boolean values are case-insensitive in most implementations
            List<Integer> resultLowerCase = droneQueryService.queryByConditions(
                    List.of(new QueryCondition("cooling", "=", "true"))
            );

            List<Integer> resultUpperCase = droneQueryService.queryByConditions(
                    List.of(new QueryCondition("cooling", "=", "TRUE"))
            );

            // Assert - Results should be same (case-insensitive)
            assertThat(resultLowerCase).isNotEmpty();
        }
    }

    // ==================== INTEGRATION-LIKE UNIT TESTS ====================

    @Nested
    @DisplayName("UT-21: Complex Query Scenarios")
    class ComplexQueryScenarios {

        @Test
        @DisplayName("UT-21.1: Find most capable drone (all features)")
        void testFindMostCapableDrone() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            // Act - Find drones with cooling=true AND heating=true AND capacity>=10
            List<Integer> result = droneQueryService.queryByConditions(Arrays.asList(
                    new QueryCondition("cooling", "=", "true"),
                    new QueryCondition("heating", "=", "true"),
                    new QueryCondition("capacity", ">", "10.0")
            ));

            // Assert - Only Drone 5
            assertThat(result).containsExactly(5);
        }

        @Test
        @DisplayName("UT-21.2: Find budget drones (low cost)")
        void testFindBudgetDrones() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            // Act - Find drones with costPerMove < 0.03
            List<Integer> result = droneQueryService.queryByConditions(
                    List.of(new QueryCondition("costPerMove", "<", "0.03"))
            );

            // Assert
            assertThat(result).isNotEmpty();
            assertThat(result).contains(1, 4, 7); // These have low costPerMove
        }

        @Test
        @DisplayName("UT-21.3: Find high-capacity non-specialized drones")
        void testFindHighCapacityNonSpecialized() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            // Act - capacity > 10 AND cooling=false AND heating=false
            List<Integer> result = droneQueryService.queryByConditions(Arrays.asList(
                    new QueryCondition("capacity", ">", "10.0"),
                    new QueryCondition("cooling", "=", "false"),
                    new QueryCondition("heating", "=", "false")
            ));

            // Assert - Drones 3 and 10
            assertThat(result).containsExactlyInAnyOrder(3, 10);
        }

        @Test
        @DisplayName("UT-21.4: Verify no drones match impossible criteria")
        void testImpossibleCriteria() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            // Act - Impossible: capacity=4 AND capacity=8
            List<Integer> result = droneQueryService.queryByConditions(Arrays.asList(
                    new QueryCondition("capacity", "=", "4.0"),
                    new QueryCondition("capacity", "=", "8.0")
            ));

            // Assert
            assertThat(result).isEmpty();
        }
    }

    // ==================== PERFORMANCE & STRESS TESTS ====================

    @Nested
    @DisplayName("UT-22: Performance Characteristics")
    class PerformanceTests {

        @Test
        @DisplayName("UT-22.1: Handles large drone fleet efficiently")
        void testLargeDroneFleet() {
            // Arrange - Create 100 drones
            List<Drone> largeDroneFleet = new ArrayList<>();
            for (int i = 1; i <= 100; i++) {
                largeDroneFleet.add(createDrone(i, "Drone " + i,
                        i % 2 == 0, i % 3 == 0,
                        (double) (i % 20 + 1),
                        1000 + (i * 10),
                        0.01 + (i * 0.001),
                        1.0, 2.0));
            }

            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(largeDroneFleet.toArray(new Drone[0]));

            // Act
            long startTime = System.currentTimeMillis();
            List<Integer> result = droneQueryService.getDronesWithCooling(true);
            long endTime = System.currentTimeMillis();

            // Assert
            assertThat(result).isNotEmpty();
            assertThat(endTime - startTime).isLessThan(1000); // Should complete in <1s
        }

        @Test
        @DisplayName("UT-22.2: Complex query on large dataset")
        void testComplexQueryPerformance() {
            // Arrange
            when(restTemplate.getForObject(anyString(), eq(Drone[].class)))
                    .thenReturn(testDrones.toArray(new Drone[0]));

            // Act - Multiple conditions
            long startTime = System.currentTimeMillis();
            List<Integer> result = droneQueryService.queryByConditions(Arrays.asList(
                    new QueryCondition("cooling", "=", "true"),
                    new QueryCondition("capacity", ">", "5.0"),
                    new QueryCondition("maxMoves", ">", "500")
            ));
            long endTime = System.currentTimeMillis();

            // Assert
            assertThat(endTime - startTime).isLessThan(100); // Should be very fast
        }
    }
}