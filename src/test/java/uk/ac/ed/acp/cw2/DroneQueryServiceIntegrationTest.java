package uk.ac.ed.acp.cw2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Integration Tests for DroneQueryService REST API
 *
 * Focus:
 *   HTTP request/response validation
 *   API endpoint contracts (200/404 status codes)
 *   JSON format validation
 *
 * Mock:
 *   - ILP REST endpoint (external service)
 *
 * Real:
 *   - DroneQueryService
 *   - GeoService
 *   - Spring MVC
 */

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Integration Tests - DroneQueryService REST API")
class DroneQueryServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestTemplate restTemplate;

    private static final String BASE_URL = "/api/v1";

    // ==================== IR-001: GET /dronesWithCooling/{state} ====================

    @Nested
    @DisplayName("IR-001: GET /dronesWithCooling/{state}")
    class DronesCoolingEndpoint {

        @Test
        @DisplayName("IR-001.1: state=true returns 200 with all cooling-capable drones")
        void testGetDronesWithCoolingTrue_Returns200() throws Exception {
            mockMvc.perform(
                            get(BASE_URL + "/dronesWithCooling/true")
                                    .contentType(MediaType.APPLICATION_JSON)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", isA(java.util.List.class)))
                    .andExpect(jsonPath("$.length()", greaterThan(0)))
                    // Verify expected cooling drones (1, 5, 8, 9) are in the result
                    .andExpect(jsonPath("$[*]", hasItems(1, 5, 8, 9)));
        }

        @Test
        @DisplayName("IR-001.2: state=false returns 200 with all non-cooling drones")
        void testGetDronesWithCoolingFalse_Returns200() throws Exception {
            mockMvc.perform(
                            get(BASE_URL + "/dronesWithCooling/false")
                                    .contentType(MediaType.APPLICATION_JSON)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", isA(java.util.List.class)));
        }

        @Test
        @DisplayName("IR-001.3: state=invalid returns 400 Bad Request")
        void testGetDronesWithCoolingInvalid_Returns400() throws Exception {
            mockMvc.perform(
                            get(BASE_URL + "/dronesWithCooling/invalid")
                                    .contentType(MediaType.APPLICATION_JSON)
                    )
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== IR-002: GET /droneDetails/{id} ====================

    @Nested
    @DisplayName("IR-002: GET /droneDetails/{id}")
    class DroneDetailsEndpoint {

        @Test
        @DisplayName("IR-002.1: id=1 (existing) returns 200 with drone object")
        void testGetDroneDetails_ValidId_Returns200WithDrone() throws Exception {
            mockMvc.perform(
                            get(BASE_URL + "/droneDetails/1")
                                    .contentType(MediaType.APPLICATION_JSON)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(1)))
                    .andExpect(jsonPath("$.name", notNullValue()))
                    .andExpect(jsonPath("$.capability.capacity", notNullValue()))
                    .andExpect(jsonPath("$.capability.cooling", notNullValue()));
        }

        @Test
        @DisplayName("IR-002.2: id=999 (non-existent) returns 404 Not Found")
        void testGetDroneDetails_InvalidId_Returns404() throws Exception {
            mockMvc.perform(
                            get(BASE_URL + "/droneDetails/999")
                                    .contentType(MediaType.APPLICATION_JSON)
                    )
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("IR-002.3: id=0 (invalid) returns 404 Not Found")
        void testGetDroneDetails_IdZero_Returns404() throws Exception {
            mockMvc.perform(
                            get(BASE_URL + "/droneDetails/0")
                                    .contentType(MediaType.APPLICATION_JSON)
                    )
                    .andExpect(status().isNotFound());
        }
    }

    // ==================== IR-003: GET /queryAsPath/{attribute}/{value} ====================

    @Nested
    @DisplayName("IR-003: GET /queryAsPath/{attribute}/{value}")
    class QueryAsPathEndpoint {

        @Test
        @DisplayName("IR-003.1: /queryAsPath/capacity/8 returns 200 with matching drone IDs")
        void testQueryAsPath_CapacityAttribute_Returns200() throws Exception {
            mockMvc.perform(
                            get(BASE_URL + "/queryAsPath/capacity/8")
                                    .contentType(MediaType.APPLICATION_JSON)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", isA(java.util.List.class)));
        }

        @Test
        @DisplayName("IR-003.2: /queryAsPath/cooling/true returns 200 with cooling drones")
        void testQueryAsPath_CoolingAttribute_Returns200() throws Exception {
            mockMvc.perform(
                            get(BASE_URL + "/queryAsPath/cooling/true")
                                    .contentType(MediaType.APPLICATION_JSON)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", isA(java.util.List.class)));
        }

        @Test
        @DisplayName("IR-003.3: /queryAsPath/invalidAttribute/value returns 200 with empty list")
        void testQueryAsPath_InvalidAttribute_Returns200Empty() throws Exception {
            mockMvc.perform(
                            get(BASE_URL + "/queryAsPath/invalidAttribute/someValue")
                                    .contentType(MediaType.APPLICATION_JSON)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", empty()));
        }
    }

    // ==================== IR-004: POST /query ====================

    @Nested
    @DisplayName("IR-004: POST /query")
    class QueryEndpoint {

        @Test
        @DisplayName("IR-004.1: Single condition query returns 200 with matching drones")
        void testPostQuery_SingleCondition_Returns200() throws Exception {
            String requestBody = """
                {
                    "conditions": [
                        {
                            "attribute": "capacity",
                            "operator": ">",
                            "value": "8"
                        }
                    ]
                }
                """;

            mockMvc.perform(
                            post(BASE_URL + "/query")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", isA(java.util.List.class)));
        }

        @Test
        @DisplayName("IR-004.2: Multiple conditions (AND logic) returns 200 with all conditions met")
        void testPostQuery_MultipleConditions_AND_Returns200() throws Exception {
            String requestBody = """
                {
                    "conditions": [
                        {
                            "attribute": "capacity",
                            "operator": ">",
                            "value": "8"
                        },
                        {
                            "attribute": "cooling",
                            "operator": "=",
                            "value": "true"
                        }
                    ]
                }
                """;

            mockMvc.perform(
                            post(BASE_URL + "/query")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", isA(java.util.List.class)));
        }

        @Test
        @DisplayName("IR-004.3: More than 2 conditions (AND logic) with all conditions met")
        void testPostQuery_MoreThanTwoConditions_AllMet_Returns200() throws Exception {
            // Test 4 conditions all satisfied
            String requestBody = """
                {
                    "conditions": [
                        {
                            "attribute": "capacity",
                            "operator": ">",
                            "value": "4"
                        },
                        {
                            "attribute": "cooling",
                            "operator": "=",
                            "value": "true"
                        },
                        {
                            "attribute": "heating",
                            "operator": "=",
                            "value": "true"
                        },
                        {
                            "attribute": "maxMoves",
                            "operator": ">",
                            "value": "1000"
                        }
                    ]
                }
                """;

            mockMvc.perform(
                            post(BASE_URL + "/query")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", isA(java.util.List.class)));
        }

        @Test
        @DisplayName("IR-004.4: More than 2 conditions (AND logic) but at least one condition fails")
        void testPostQuery_MoreThanTwoConditions_OneFails_ReturnsEmpty() throws Exception {
            // Test 4 conditions where at least one is impossible (AND fails)
            String requestBody = """
                {
                    "conditions": [
                        {
                            "attribute": "capacity",
                            "operator": ">",
                            "value": "4"
                        },
                        {
                            "attribute": "cooling",
                            "operator": "=",
                            "value": "true"
                        },
                        {
                            "attribute": "heating",
                            "operator": "=",
                            "value": "true"
                        },
                        {
                            "attribute": "maxMoves",
                            "operator": "=",
                            "value": "9999"
                        }
                    ]
                }
                """;

            mockMvc.perform(
                            post(BASE_URL + "/query")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", empty()));
        }

        @Test
        @DisplayName("IR-004.5: Empty conditions list returns 200 with all 10 drones")
        void testPostQuery_EmptyConditions_ReturnsAll10() throws Exception {
            String requestBody = """
                {
                    "conditions": []
                }
                """;

            mockMvc.perform(
                            post(BASE_URL + "/query")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()", equalTo(10)));
        }

        @Test
        @DisplayName("IR-004.6: No matches returns 200 with empty list")
        void testPostQuery_NoMatches_ReturnsEmpty() throws Exception {
            String requestBody = """
                {
                    "conditions": [
                        {
                            "attribute": "capacity",
                            "operator": "=",
                            "value": "999"
                        }
                    ]
                }
                """;

            mockMvc.perform(
                            post(BASE_URL + "/query")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", empty()));
        }

        @Test
        @DisplayName("IR-004.7: Missing required field (operator) returns 400 Bad Request")
        void testPostQuery_MissingRequiredField_Returns400() throws Exception {
            // Missing "operator" field - should fail validation
            String requestBody = """
                {
                    "conditions": [
                        {
                            "attribute": "capacity",
                            "value": "8"
                        }
                    ]
                }
                """;

            mockMvc.perform(
                            post(BASE_URL + "/query")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== IR-005: POST /queryAvailableDrones ====================

    @Nested
    @DisplayName("IR-005: POST /queryAvailableDrones")
    class QueryAvailableDronesEndpoint {

        @Test
        @DisplayName("IR-005.1: Single valid dispatch returns 200 with available drones")
        void testQueryAvailableDrones_SingleDispatch_Returns200() throws Exception {
            String requestBody = """
                [
                    {
                        "id": 201,
                        "date": "2025-01-28",
                        "time": "10:00",
                        "requirements": {
                            "capacity": 2.0,
                            "cooling": false,
                            "heating": false
                        },
                        "delivery": {
                            "lng": -3.190000,
                            "lat": 55.944000
                        }
                    }
                ]
                """;

            mockMvc.perform(
                            post(BASE_URL + "/queryAvailableDrones")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", isA(java.util.List.class)));
        }

        @Test
        @DisplayName("IR-005.2: Multiple dispatches (AND logic) all requirements met")
        void testQueryAvailableDrones_MultipleDispatches_AllMet_Returns200() throws Exception {
            String requestBody = """
                [
                    {
                        "id": 201,
                        "date": "2025-01-28",
                        "time": "10:00",
                        "requirements": {
                            "capacity": 2.0,
                            "cooling": false,
                            "heating": false
                        },
                        "delivery": {
                            "lng": -3.190000,
                            "lat": 55.944000
                        }
                    },
                    {
                        "id": 202,
                        "date": "2025-01-28",
                        "time": "10:00",
                        "requirements": {
                            "capacity": 3.0,
                            "cooling": false,
                            "heating": false
                        },
                        "delivery": {
                            "lng": -3.189000,
                            "lat": 55.945000
                        }
                    }
                ]
                """;

            mockMvc.perform(
                            post(BASE_URL + "/queryAvailableDrones")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", isA(java.util.List.class)));
        }

        @Test
        @DisplayName("IR-005.3: Multiple dispatches (AND logic) requirements NOT all satisfied returns empty")
        void testQueryAvailableDrones_MultipleDispatches_NotAllMet_ReturnsEmpty() throws Exception {
            // First dispatch needs cooling, second needs heating
            // Impossible for a drone to have both AND satisfy AND logic
            String requestBody = """
                [
                    {
                        "id": 201,
                        "date": "2025-01-28",
                        "time": "10:00",
                        "requirements": {
                            "capacity": 2.0,
                            "cooling": true,
                            "heating": false
                        },
                        "delivery": {
                            "lng": -3.190000,
                            "lat": 55.944000
                        }
                    },
                    {
                        "id": 202,
                        "date": "2025-01-28",
                        "time": "10:00",
                        "requirements": {
                            "capacity": 2.0,
                            "cooling": false,
                            "heating": true
                        },
                        "delivery": {
                            "lng": -3.189000,
                            "lat": 55.945000
                        }
                    }
                ]
                """;

            mockMvc.perform(
                            post(BASE_URL + "/queryAvailableDrones")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", empty()));
        }

        @Test
        @DisplayName("IR-005.4: Capacity too large returns 200 with empty list")
        void testQueryAvailableDrones_CapacityTooLarge_ReturnsEmpty() throws Exception {
            // Request capacity larger than any drone can carry
            String requestBody = """
                [
                    {
                        "id": 203,
                        "date": "2025-01-28",
                        "time": "10:00",
                        "requirements": {
                            "capacity": 50.0,
                            "cooling": false,
                            "heating": false
                        },
                        "delivery": {
                            "lng": -3.190000,
                            "lat": 55.944000
                        }
                    }
                ]
                """;

            mockMvc.perform(
                            post(BASE_URL + "/queryAvailableDrones")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", empty()));
        }

        @Test
        @DisplayName("IR-005.5: Missing required field returns 400 Bad Request")
        void testQueryAvailableDrones_MissingRequired_Returns400() throws Exception {
            String requestBody = """
                [
                    {
                        "id": 201
                    }
                ]
                """;

            mockMvc.perform(
                            post(BASE_URL + "/queryAvailableDrones")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== IR-006: POST /calcDeliveryPath ====================

    @Nested
    @DisplayName("IR-006: POST /calcDeliveryPath")
    class CalcDeliveryPathEndpoint {

        @Test
        @DisplayName("IR-006.1: Single dispatch returns 200 with complete path")
        void testCalcDeliveryPath_SingleDispatch_Returns200() throws Exception {
            String requestBody = """
                      [
                          {
                            "id": 201,
                            "date": "2025-01-28",
                            "time": "10:00",
                            "requirements": {
                              "capacity": 2.0,
                              "cooling": false,
                              "heating": false,
                              "maxCost": 100.0
                            },
                            "delivery": {
                              "lng": -3.186000,
                              "lat": 55.944900
                            }
                          }
                        ]
                    
                    """;

            mockMvc.perform(
                            post(BASE_URL + "/calcDeliveryPath")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCost", notNullValue()))
                    .andExpect(jsonPath("$.totalMoves", notNullValue()))
                    .andExpect(jsonPath("$.dronePaths", isA(java.util.List.class)))
                    .andExpect(jsonPath("$.dronePaths[0].droneId", notNullValue()))
                    .andExpect(jsonPath("$.dronePaths[0].deliveries", isA(java.util.List.class)))
                    // Verify flight path validity
                    .andExpect(jsonPath("$.dronePaths[0].deliveries[0].flightPath", isA(java.util.List.class)))
                    .andExpect(jsonPath("$.dronePaths[0].deliveries[0].flightPath[0].lng", notNullValue()))
                    .andExpect(jsonPath("$.dronePaths[0].deliveries[0].flightPath[0].lat", notNullValue()));
        }

        @Test
        @DisplayName("IR-006.2: Multiple dispatches single drone returns 200 with TSP optimization")
        void testCalcDeliveryPath_MultipleDispatches_SingleDrone_Returns200() throws Exception {
            String requestBody = """
                    [
                        {
                            "id": 201,
                            "date": "2025-01-28",
                            "time": "10:00",
                            "requirements": {"capacity": 1.0},
                            "delivery": {"lng": -3.190000, "lat": 55.944000}
                        },
                        {
                            "id": 202,
                            "date": "2025-01-28",
                            "time": "10:00",
                            "requirements": {"capacity": 1.0},
                            "delivery": {"lng": -3.189000, "lat": 55.945000}
                        }
                    ]
                    """;

            mockMvc.perform(
                            post(BASE_URL + "/calcDeliveryPath")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCost", greaterThan(0)))
                    .andExpect(jsonPath("$.dronePaths[0].deliveries.length()", equalTo(2)));
        }

        @Test
        @DisplayName("IR-006.3: Exceeds capacity returns 200 with empty dronePaths")
        void testCalcDeliveryPath_ExceedsCapacity_Returns200Empty() throws Exception {
            String requestBody = """
                [
                    {
                        "id": 201,
                        "date": "2025-01-28",
                        "time": "10:00",
                        "requirements": {"capacity": 50.0},
                        "delivery": {"lng": -3.190000, "lat": 55.944000}
                    }
                ]
                """;

            mockMvc.perform(
                            post(BASE_URL + "/calcDeliveryPath")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dronePaths", empty()));
        }

        @Test
        @DisplayName("IR-006.4: Exceeds maxCost returns 200 with appropriate handling")
        void testCalcDeliveryPath_ExceedsMaxCost_Returns200() throws Exception {
            String requestBody = """
                [
                    {
                        "id": 201,
                        "date": "2025-01-28",
                        "time": "10:00",
                        "requirements": {
                            "capacity": 2.0,
                            "maxCost": 0.01
                        },
                        "delivery": {"lng": -3.190000, "lat": 55.944000}
                    }
                ]
                """;

            mockMvc.perform(
                            post(BASE_URL + "/calcDeliveryPath")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("IR-006.5: Missing required field returns 400 Bad Request")
        void testCalcDeliveryPath_MissingRequired_Returns400() throws Exception {
            String requestBody = """
                [
                    {
                        "id": 201
                    }
                ]
                """;

            mockMvc.perform(
                            post(BASE_URL + "/calcDeliveryPath")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== IR-007: POST /calcDeliveryPathAsGeoJson ====================

    @Nested
    @DisplayName("IR-007: POST /calcDeliveryPathAsGeoJson")
    class CalcDeliveryPathAsGeoJsonEndpoint {

        @Test
        @DisplayName("IR-007.1: Single drone delivery returns 200 with valid GeoJSON LineString")
        void testCalcDeliveryPathAsGeoJson_SingleDrone_Returns200GeoJson() throws Exception {
            String requestBody = """
                [
                    {
                        "id": 201,
                        "date": "2025-01-28",
                        "time": "10:00",
                        "requirements": {"capacity": 2.0},
                        "delivery": {"lng": -3.186000, "lat": 55.944900}
                    }
                ]
                """;

            mockMvc.perform(
                            post(BASE_URL + "/calcDeliveryPathAsGeoJson")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type", is("FeatureCollection")))
                    .andExpect(jsonPath("$.features", isA(java.util.List.class)))
                    .andExpect(jsonPath("$.features[0].type", is("Feature")))
                    .andExpect(jsonPath("$.features[0].geometry.type", is("LineString")))
                    .andExpect(jsonPath("$.features[0].geometry.coordinates", isA(java.util.List.class)))
                    .andExpect(jsonPath("$.features[0].geometry.coordinates[0]", isA(java.util.List.class)));
            // Verify GeoJSON RFC 7946 FeatureCollection format:
            // - type = "FeatureCollection"
            // - features = array of Feature objects
            // - Each feature has geometry with type = "LineString"
            // - coordinates = [[lng, lat], [lng, lat], ...]
            // - Longitude first, latitude second
        }

        @Test
        @DisplayName("IR-007.2: Multiple dispatches returns 200 with single complete path GeoJSON")
        void testCalcDeliveryPathAsGeoJson_MultipleDispatches_Returns200() throws Exception {
            String requestBody = """
                    [
                        {
                            "id": 1,
                            "date": "2025-01-01",
                            "time": "14:30",
                            "requirements": {
                                "capacity": 0.01,
                                "cooling": false,
                                "heating": false,
                                "maxCost": 1000.0
                            },
                            "delivery": {
                                    "lng": -3.189,
                                    "lat": 55.941
                            }
                        },
                        {
                            "id": 2,
                            "date": "2025-01-02",
                            "time": "14:30",
                            "requirements": {
                                "capacity": 0.01,
                                "cooling": false,
                                "heating": false,
                                "maxCost": 1000.0
                            },
                            "delivery": {
                                    "lng": -3.180,
                                    "lat": 55.941
                            }
                        }
                    ]
                """;

            mockMvc.perform(
                            post(BASE_URL + "/calcDeliveryPathAsGeoJson")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type", is("FeatureCollection")))
                    .andExpect(jsonPath("$.features[0].geometry.type", is("LineString")))
                    .andExpect(jsonPath("$.features[0].geometry.coordinates.length()", greaterThan(2)));
        }

        @Test
        @DisplayName("IR-007.3: no valid solution - returns 200 with empty FeatureCollection")
        void testCalcDeliveryPathAsGeoJson_CapacityTooLarge_ReturnsEmpty() throws Exception {
            // Capacity 100.0 exceeds max drone capacity of 20.0
            String requestBody = """
                [
                    {
                        "id": 201,
                        "date": "2025-01-28",
                        "time": "10:00",
                        "requirements": {"capacity": 100.0},
                        "delivery": {"lng": -3.190000, "lat": 55.944000}
                    }
                ]
                """;

            // Should return 200 OK with empty FeatureCollection (no solution found)
            mockMvc.perform(
                            post(BASE_URL + "/calcDeliveryPathAsGeoJson")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type", is("FeatureCollection")))
                    .andExpect(jsonPath("$.features", empty()));
            // Graceful handling: returns empty FeatureCollection instead of error
        }
    }
}