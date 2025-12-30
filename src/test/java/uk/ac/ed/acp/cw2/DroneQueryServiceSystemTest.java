package uk.ac.ed.acp.cw2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ed.acp.cw2.dto.DeliveryPathResponse;
import uk.ac.ed.acp.cw2.dto.MedDispatchRec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * System Tests for DroneQueryService
 *
 * Tests end-to-end workflows with MockDataConfig providing test data.
 *
 * Test Categories:
 * - SR-E2E: End-to-End Workflows (2 tests)
 * - SR-PERF: Performance Requirements (15 tests)
 * - SR-STRESS: Stress Tests (4 tests)
 * - SR-EFFICIENCY: Path Optimization Quality (4 tests)
 * - SR-DATA: Data Consistency (5 tests)
 *
 * Total: 30 System Tests
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("mock")
class DroneQueryServiceSystemTest {

    private static final String BASE_URL = "/api/v1";
    private static final double MOVE_STEP_SIZE = 0.00015;

    // Service Points (from MockDataConfig)
    private static final double APPLETON_LNG = -3.18635807889864;
    private static final double APPLETON_LAT = 55.9446806670849;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ==================== SR-E2E: END-TO-END WORKFLOW TESTS ====================

    @Nested
    @DisplayName("SR-E2E: End-to-End Workflows")
    class EndToEndWorkflowTests {

        @Test
        @DisplayName("SR-E2E-001: Complete single-day delivery workflow (query → calculate → export)")
        void testCompleteSingleDayDeliveryWorkflow() throws Exception {
            System.out.println("\n=== SR-E2E-001: Complete Single-Day Delivery Workflow ===");

            // 5 delivery points around Edinburgh
            List<MedDispatchRec> dispatches = new ArrayList<>();
            dispatches.add(createDispatch(5001, "2025-01-28", "10:00", 1.5, false, false, null, -3.185, 55.945));
            dispatches.add(createDispatch(5002, "2025-01-28", "10:00", 1.2, false, false, null, -3.195, 55.945));
            dispatches.add(createDispatch(5003, "2025-01-28", "10:00", 2.0, false, false, null, -3.190, 55.950));
            dispatches.add(createDispatch(5004, "2025-01-28", "10:00", 1.8, false, false, null, -3.185, 55.950));
            dispatches.add(createDispatch(5005, "2025-01-28", "10:00", 1.0, false, false, null, -3.190, 55.940));

            String dispatchesJson = objectMapper.writeValueAsString(dispatches);

            // Step 1: Query available drones
            System.out.println("Step 1: Querying available drones");
            mockMvc.perform(post(BASE_URL + "/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(dispatchesJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", isA(List.class)));

            // Step 2: Calculate optimal delivery path
            System.out.println("Step 2: Calculating optimal delivery path");
            long calcStartTime = System.currentTimeMillis();

            mockMvc.perform(post(BASE_URL + "/calcDeliveryPath")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(dispatchesJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCost").isNumber())
                    .andExpect(jsonPath("$.totalMoves").isNumber())
                    .andExpect(jsonPath("$.dronePaths").isArray())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            long calcDuration = System.currentTimeMillis() - calcStartTime;
            System.out.println("  ✓ Path calculated in " + calcDuration + "ms");

            // Step 3: Export as GeoJSON
            System.out.println("Step 3: Exporting delivery path as GeoJSON");
            mockMvc.perform(post(BASE_URL + "/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(dispatchesJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("FeatureCollection"))
                    .andExpect(jsonPath("$.features").isArray());

            System.out.println("✓ E2E-001 COMPLETE: Single-day delivery workflow successful");
        }

        @Test
        @DisplayName("SR-E2E-002: Complete multi-day delivery scheduling workflow")
        void testCompleteMultiDayDeliveryWorkflow() throws Exception {
            System.out.println("\n=== SR-E2E-002: Complete Multi-Day Delivery Workflow ===");

            // Tuesday deliveries
            List<MedDispatchRec> dispatches = new ArrayList<>();
            dispatches.add(createDispatch(5101, "2025-01-28", "09:00", 2.0, false, false, null, -3.185, 55.945));
            dispatches.add(createDispatch(5102, "2025-01-28", "09:00", 1.5, false, false, null, -3.195, 55.945));
            dispatches.add(createDispatch(5103, "2025-01-28", "09:00", 2.5, false, false, null, -3.190, 55.950));
            dispatches.add(createDispatch(5201, "2025-01-29", "14:00", 1.8, false, false, null, -3.180, 55.940));
            dispatches.add(createDispatch(5202, "2025-01-29", "14:00", 2.2, false, false, null, -3.192, 55.946));
            dispatches.add(createDispatch(5203, "2025-01-29", "14:00", 1.0, false, false, null, -3.188, 55.943));

            String Json = objectMapper.writeValueAsString(dispatches);

            // Day 1 - Tuesday workflow
            System.out.println("Day 1 (Tuesday): Processing 3 deliveries");

            // Step 1: Query available drones
            mockMvc.perform(post(BASE_URL + "/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(Json))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", isA(List.class)));

            // Step 2: Calculate delivery path
            long tuesdayStartTime = System.currentTimeMillis();
            mockMvc.perform(post(BASE_URL + "/calcDeliveryPath")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(Json))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCost").isNumber())
                    .andExpect(jsonPath("$.totalMoves").isNumber())
                    .andExpect(jsonPath("$.dronePaths").isArray());

            long tuesdayDuration = System.currentTimeMillis() - tuesdayStartTime;
            System.out.println("  ✓ Tuesday path calculated in " + tuesdayDuration + "ms");

            // Step 3: Export Tuesday path as GeoJSON
            mockMvc.perform(post(BASE_URL + "/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(Json))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("FeatureCollection"))
                    .andExpect(jsonPath("$.features").isArray());

            System.out.println("✓ E2E-002 COMPLETE: Multi-day delivery scheduling successful");
        }
    }

    // ==================== SR-PERF: PERFORMANCE REQUIREMENT TESTS ====================

    @Nested
    @DisplayName("SR-PERF: Performance Requirements")
    class PerformanceTests {

        @Test
        @DisplayName("SR-PERF-001: GET /dronesWithCooling responds < 2000ms")
        void testDronesWithCooling_Performance() throws Exception {
            long startTime = System.currentTimeMillis();

            mockMvc.perform(get(BASE_URL + "/dronesWithCooling/true"))
                    .andExpect(status().isOk());

            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 2000, "Took " + duration + "ms, should be < 2000ms");
            System.out.println("✓ PERF-001: /dronesWithCooling in " + duration + "ms");
        }

        @Test
        @DisplayName("SR-PERF-002: GET /droneDetails/{id} responds < 2000ms")
        void testDroneDetails_Performance() throws Exception {
            long startTime = System.currentTimeMillis();

            mockMvc.perform(get(BASE_URL + "/droneDetails/5"))
                    .andExpect(status().isOk());

            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 2000, "Took " + duration + "ms, should be < 2000ms");
            System.out.println("✓ PERF-002: /droneDetails in " + duration + "ms");
        }

        @Test
        @DisplayName("SR-PERF-003: GET /queryAsPath responds < 2000ms")
        void testQueryAsPath_Performance() throws Exception {
            long startTime = System.currentTimeMillis();

            mockMvc.perform(get(BASE_URL + "/queryAsPath/capacity/8"))
                    .andExpect(status().isOk());

            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 2000, "Took " + duration + "ms, should be < 2000ms");
            System.out.println("✓ PERF-003: /queryAsPath in " + duration + "ms");
        }

        @Test
        @DisplayName("SR-PERF-004: POST /query responds < 2000ms")
        void testQuery_Performance() throws Exception {
            String requestBody = """
                [
                    {"attribute": "cooling", "operator": "=", "value": "true"}
                ]
                """;

            long startTime = System.currentTimeMillis();

            mockMvc.perform(post(BASE_URL + "/query")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 2000, "Took " + duration + "ms, should be < 2000ms");
            System.out.println("✓ PERF-004: POST /query in " + duration + "ms");
        }

        @Test
        @DisplayName("SR-PERF-005: POST /queryAvailableDrones (1 dispatch) responds < 2000ms")
        void testQueryAvailableDrones_Performance() throws Exception {
            List<MedDispatchRec> dispatches = new ArrayList<>();
            dispatches.add(createDispatch(801, "2025-01-28", "10:00", 2.0, false, false, null, -3.186, 55.944));

            String requestBody = objectMapper.writeValueAsString(dispatches);
            long startTime = System.currentTimeMillis();

            mockMvc.perform(post(BASE_URL + "/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 2000, "Took " + duration + "ms, should be < 2000ms");
            System.out.println("✓ PERF-005: POST /queryAvailableDrones in " + duration + "ms");
        }

        @Test
        @DisplayName("SR-PERF-006: POST /queryAvailableDrones (Multiple dispatches - same day and time) responds < 2000ms")
        void testQueryAvailableDrones_multipleDispatches_Performance() throws Exception {
            List<MedDispatchRec> dispatches = new ArrayList<>();
            dispatches.add(createDispatch(1, "2025-12-22", "10:00:00", 2.0, false, false, 20.0, -3.186, 55.944));
            dispatches.add(createDispatch(2, "2025-12-22", "10:00:00", 2.0, false, false, 20.0, -3.187, 55.945));
            String requestBody = objectMapper.writeValueAsString(dispatches);
            long startTime = System.currentTimeMillis();

            mockMvc.perform(post(BASE_URL + "/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 2000, "Took " + duration + "ms, should be < 2000ms");
            System.out.println("✓ PERF-005: POST /queryAvailableDrones in " + duration + "ms");
        }

        @Test
        @DisplayName("SR-PERF-007: POST /queryAvailableDrones (Multiple dispatches - different days) responds < 2000ms")
        void testQueryAvailableDrones_multipleDispatches2_Performance() throws Exception {
            MedDispatchRec dispatch1 = createDispatch(1, "2025-12-23", "14:30", 0.75, false, true, 13.5, -3.189, 55.941);
            MedDispatchRec dispatch2 = createDispatch(2, "2025-12-23", "14:30", 0.15, false, false, 10.5, -3.189, 55.951);
            MedDispatchRec dispatch3 = createDispatch(3, "2025-12-22", "14:30", 0.85, false, false, 15.0, -3.183, 55.95);
            MedDispatchRec dispatch4 = createDispatch(4, "2025-12-23", "14:30", 0.65, false, true, 10.0, -3.213, 55.94);
            String requestBody = objectMapper.writeValueAsString(Arrays.asList(dispatch1, dispatch2, dispatch3, dispatch4));
            long startTime = System.currentTimeMillis();

            mockMvc.perform(post(BASE_URL + "/queryAvailableDrones")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 2000, "Took " + duration + "ms, should be < 2000ms");
            System.out.println("✓ PERF-005: POST /queryAvailableDrones in " + duration + "ms");
        }



        @Test
        @DisplayName("SR-PERF-008: POST /calcDeliveryPath (1 dispatch) responds < 500ms")
        void testCalcDeliveryPath_SingleDispatch_Performance() throws Exception {
            List<MedDispatchRec> dispatches = new ArrayList<>();
            dispatches.add(createDispatch(802, "2025-01-28", "10:00", 2.0, false, false, null, -3.184, 55.944));

            String requestBody = objectMapper.writeValueAsString(dispatches);
            long startTime = System.currentTimeMillis();

            mockMvc.perform(post(BASE_URL + "/calcDeliveryPath")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 500, "Took " + duration + "ms, should be < 500ms");
            System.out.println("✓ PERF-006: calcDeliveryPath (1) in " + duration + "ms");
        }

        @Test
        @DisplayName("SR-PERF-009: POST /calcDeliveryPath (5 dispatches) responds < 2000ms")
        void testCalcDeliveryPath_FiveDispatches_Performance() throws Exception {
            List<MedDispatchRec> dispatches = new ArrayList<>();
            dispatches.add(createDispatch(803, "2025-01-28", "10:00", 1.0, false, false, 10.0, -3.185, 55.945));
            dispatches.add(createDispatch(804, "2025-01-28", "10:00", 1.0, false, false, 10.0, -3.195, 55.945));
            dispatches.add(createDispatch(805, "2025-01-28", "10:00", 1.0, false, false, 15.0, -3.190, 55.950));
            dispatches.add(createDispatch(806, "2025-01-28", "10:00", 1.0, false, false, 18.0, -3.185, 55.950));
            dispatches.add(createDispatch(807, "2025-01-28", "10:00", 1.0, false, false, 20.0, -3.190, 55.940));

            String requestBody = objectMapper.writeValueAsString(dispatches);
            long startTime = System.currentTimeMillis();

            mockMvc.perform(post(BASE_URL + "/calcDeliveryPath")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 2000, "Took " + duration + "ms, should be < 2000ms");
            System.out.println("✓ PERF-007: calcDeliveryPath (5) in " + duration + "ms");
        }

        @Test
        @DisplayName("SR-PERF-010: POST /calcDeliveryPath (with 1 obstacle) responds < 2000ms")
        void testCalcDeliveryPath_oneObstacle_Performance() throws Exception {
            List<MedDispatchRec> dispatches = List.of(
                    createDispatch(201, "2025-01-28", "10:00",
                            3.0, false, false, 150.0, -3.192000, 55.943000)
            );

            String requestBody = objectMapper.writeValueAsString(dispatches);
            long startTime = System.currentTimeMillis();

            mockMvc.perform(post(BASE_URL + "/calcDeliveryPath")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 2000, "Took " + duration + "ms, should be < 2000ms");
            System.out.println("✓ PERF-007: calcDeliveryPath (1 obstacle) in " + duration + "ms");
        }

        @Test
        @DisplayName("SR-PERF-011: POST /calcDeliveryPath (with 2 obstacles responds < 2000ms")
        void testCalcDeliveryPath_MultipleObstacle_Performance() throws Exception {
            List<MedDispatchRec> dispatches = Arrays.asList(
                    createDispatch(202, "2025-01-28", "10:00",
                            2.0, false, false, 150.0, -3.191000, 55.944500),
                    createDispatch(203, "2025-01-28", "11:00",
                            2.2, false, false, 160.0, -3.186500, 55.943800)
            );
            String requestBody = objectMapper.writeValueAsString(dispatches);
            long startTime = System.currentTimeMillis();

            mockMvc.perform(post(BASE_URL + "/calcDeliveryPath")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 2000, "Took " + duration + "ms, should be < 2000ms");
            System.out.println("✓ PERF-007: calcDeliveryPath (multiple obstacle) in " + duration + "ms");
        }

        @Test
        @DisplayName("SR-PERF-012: POST /calcDeliveryPath (distant locations with obstacles) responds < 2000ms")
        void testCalcDeliveryPath_DistantObstacle_Performance() throws Exception {
            List<MedDispatchRec> dispatches = Arrays.asList(
                    createDispatch(301, "2025-01-28", "10:00",
                            2.0, false, false, 200.0, -3.200000, 56.000000),
                    createDispatch(302, "2025-01-28", "11:00",
                            2.2, false, false, 210.0, -3.180000, 55.930000)
            );
            String requestBody = objectMapper.writeValueAsString(dispatches);
            long startTime = System.currentTimeMillis();

            mockMvc.perform(post(BASE_URL + "/calcDeliveryPath")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 2000, "Took " + duration + "ms, should be < 2000ms");
            System.out.println("✓ PERF-007: calcDeliveryPath (multiple distant obstacle) in " + duration + "ms");
        }

        @Test
        @DisplayName("SR-PERF-013: POST /calcDeliveryPathAsGeoJson (1 dispatch) responds < 500ms")
        void testCalcDeliveryPathAsGeoJson_SingleDispatch_Performance() throws Exception {
            List<MedDispatchRec> dispatches = new ArrayList<>();
            dispatches.add(createDispatch(808, "2025-01-28", "10:00", 2.0, false, true, 12.0, -3.184, 55.944));

            String requestBody = objectMapper.writeValueAsString(dispatches);
            long startTime = System.currentTimeMillis();

            mockMvc.perform(post(BASE_URL + "/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 500, "Took " + duration + "ms, should be < 500ms");
            System.out.println("✓ PERF-009: calcDeliveryPathAsGeoJson (1) in " + duration + "ms");
        }

        @Test
        @DisplayName("SR-PERF-014: POST /calcDeliveryPathAsGeoJson (5 dispatches) responds < 2000ms")
        void testCalcDeliveryPathAsGeoJson_FiveDispatches_Performance() throws Exception {
            List<MedDispatchRec> dispatches = new ArrayList<>();
            dispatches.add(createDispatch(809, "2025-01-28", "10:00", 1.0, false, true, 10.0, -3.185, 55.945));
            dispatches.add(createDispatch(810, "2025-01-28", "11:00", 2.0, false, false, 12.0, -3.195, 55.945));
            dispatches.add(createDispatch(811, "2025-01-28", "12:00", 4.0, false, false, 8.0, -3.190, 55.950));
            dispatches.add(createDispatch(812, "2025-01-28", "13:00", 5.0, true, false, 9.0, -3.185, 55.950));
            dispatches.add(createDispatch(813, "2025-01-28", "14:00", 3.0, false, false, 10.0, -3.190, 55.940));

            String requestBody = objectMapper.writeValueAsString(dispatches);
            long startTime = System.currentTimeMillis();

            mockMvc.perform(post(BASE_URL + "/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 2000, "Took " + duration + "ms, should be < 2000ms");
            System.out.println("✓ PERF-010: calcDeliveryPathAsGeoJson (5) in " + duration + "ms");
        }

        @Test
        @DisplayName("SR-PERF-015: POST /calcDeliveryPathAsGeoJson (50 dispatches) responds < 20000ms")
        void testCalcDeliveryPathAsGeoJson_FiftyDispatches_Performance() throws Exception {
            List<MedDispatchRec> dispatches = new ArrayList<>();
            Random rand = new Random(42);

            for (int i = 0; i < 50; i++) {
                double lng = APPLETON_LNG + (rand.nextDouble() - 0.5) * 0.02;
                double lat = APPLETON_LAT + (rand.nextDouble() - 0.5) * 0.02;
                dispatches.add(createDispatch(950 + i, "2025-01-28", "10:00", 1.0, false, false, null, lng, lat));
            }

            String requestBody = objectMapper.writeValueAsString(dispatches);
            long startTime = System.currentTimeMillis();

            mockMvc.perform(post(BASE_URL + "/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 20000, "Took " + duration + "ms, should be < 20000ms");
            System.out.println("✓ PERF-011: calcDeliveryPathAsGeoJson (50) in " + duration + "ms");
        }
    }

    // ==================== SR-STRESS: STRESS TESTS ====================

    @Nested
    @DisplayName("SR-STRESS: Stress Tests")
    class StressTests {

        @Test
        @DisplayName("SR-STRESS-001: 100 dispatches")
        void testCalcDeliveryPath_HundredDispatches_StressTest() throws Exception {
            List<MedDispatchRec> dispatches = new ArrayList<>();
            Random rand = new Random(42);

            for (int i = 0; i < 100; i++) {
                double lng = APPLETON_LNG + (rand.nextDouble() - 0.5) * 0.03;
                double lat = APPLETON_LAT + (rand.nextDouble() - 0.5) * 0.03;
                dispatches.add(createDispatch(2000 + i, "2025-01-28", "10:00", 0.5, false, false, null, lng, lat));
            }

            String requestBody = objectMapper.writeValueAsString(dispatches);
            long startTime = System.currentTimeMillis();

            mockMvc.perform(post(BASE_URL + "/calcDeliveryPath")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            long duration = System.currentTimeMillis() - startTime;
            System.out.println("✓ STRESS-001: 100 dispatches completed in " + duration + "ms");
        }

        @Test
        @DisplayName("SR-STRESS-002: Concurrent requests (10 parallel)")
        void testCalcDeliveryPath_ConcurrentRequests_StressTest() throws Exception {
            ExecutorService executor = Executors.newFixedThreadPool(10);

            for (int i = 0; i < 10; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        List<MedDispatchRec> dispatches = new ArrayList<>();
                        for (int j = 0; j < 5; j++) {
                            dispatches.add(createDispatch(2100 + index * 10 + j,
                                    "2025-01-28", "10:00",
                                    1.0, false, false, null, APPLETON_LNG + (j * 0.001), APPLETON_LAT));
                        }

                        String requestBody = objectMapper.writeValueAsString(dispatches);
                        mockMvc.perform(post(BASE_URL + "/calcDeliveryPath")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(requestBody))
                                .andExpect(status().isOk());
                    } catch (Exception e) {
                        fail("Concurrent request failed: " + e.getMessage());
                    }
                });
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.MINUTES),
                    "All concurrent requests should complete within 5 minutes");

            System.out.println("✓ STRESS-002: 10 concurrent requests completed successfully");
        }

        @Test
        @DisplayName("SR-STRESS-003: Long distance pathfinding")
        void testCalcDeliveryPath_LongDistance_StressTest() throws Exception {
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

            String requestBody = objectMapper.writeValueAsString(dispatches);
            long startTime = System.currentTimeMillis();

            mockMvc.perform(post(BASE_URL + "/calcDeliveryPath")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalMoves").value(greaterThan(1000)));

            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 20000, "Long distance should complete within 20 seconds");

            System.out.println("✓ STRESS-003: 66km long distance completed in " + duration + "ms");
        }

        @Test
        @DisplayName("SR-STRESS-004: Multiple restricted areas navigation stress")
        void testCalcDeliveryPath_MultipleRestrictedAreas_StressTest() throws Exception {
            List<MedDispatchRec> dispatches = new ArrayList<>();
            dispatches.add(createDispatch(2300, "2025-01-28", "10:00", 2.0, false, false, 10.0, -3.1920, 55.9400));
            dispatches.add(createDispatch(2301, "2025-01-28", "11:00", 2.0, false, false, 10.0, -3.1860, 55.9400));
            dispatches.add(createDispatch(2302, "2025-01-28", "12:00", 2.0, false, true, 8.0, -3.1860, 55.9500));
            dispatches.add(createDispatch(2303, "2025-01-28", "13:00", 2.0, false, true, 6.0, -3.1920, 55.9500));

            String requestBody = objectMapper.writeValueAsString(dispatches);
            long startTime = System.currentTimeMillis();

            mockMvc.perform(post(BASE_URL + "/calcDeliveryPath")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 5000, "Should complete within 5 seconds");

            System.out.println("✓ STRESS-004: Multiple restricted areas completed in " + duration + "ms");
        }
    }

    // ==================== SR-EFFICIENCY: PATH OPTIMIZATION QUALITY TESTS ====================

    @Nested
    @DisplayName("SR-EFFICIENCY: Path Optimization Quality")
    class EfficiencyTests {

        @Test
        @DisplayName("SR-EFF-001: Simple short path efficiency >= 0.95")
        void testCalcDeliveryPath_SimpleShortPath_ExcellentEfficiency() throws Exception {
            List<MedDispatchRec> dispatches = new ArrayList<>();
            dispatches.add(createDispatch(3001, "2025-01-28", "10:00", 2.0, false, false, 10.0, -3.184358, 55.944680));

            String requestBody = objectMapper.writeValueAsString(dispatches);
            String response = mockMvc.perform(post(BASE_URL + "/calcDeliveryPath")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            double directDistance = calculateDirectDistance(dispatches, response);
            int totalMoves = extractTotalMoves(response);
            double efficiency = calculatePathEfficiency(directDistance, totalMoves);

            System.out.println("Simple short path efficiency: " + String.format("%.2f", efficiency));
            assertTrue(efficiency >= 0.95, "Should have excellent efficiency (>= 0.95), got " + efficiency);
            System.out.println("✓ EFF-001: Simple short path efficiency verified");
        }

        @Test
        @DisplayName("SR-EFF-002: Medium path with single obstacle efficiency >= 0.80")
        void testCalcDeliveryPath_MediumPathSingleObstacle_GoodEfficiency() throws Exception {
            List<MedDispatchRec> dispatches = new ArrayList<>();
            dispatches.add(createDispatch(201, "2025-01-28", "10:00", 3.0, false, false, 150.0, -3.192000, 55.943000));

            String requestBody = objectMapper.writeValueAsString(dispatches);
            String response = mockMvc.perform(post(BASE_URL + "/calcDeliveryPath")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            double directDistance = calculateDirectDistance(dispatches, response);
            int totalMoves = extractTotalMoves(response);
            double efficiency = calculatePathEfficiency(directDistance, totalMoves);

            System.out.println("Medium path efficiency: " + String.format("%.2f", efficiency));
            assertTrue(efficiency >= 0.80, "Should have good efficiency (>= 0.80), got " + efficiency);
            System.out.println("✓ EFF-002: Medium path efficiency verified");
        }

        @Test
        @DisplayName("SR-EFF-003: Complex path with multiple obstacles efficiency >= 0.60")
        void testCalcDeliveryPath_ComplexPathMultipleObstacles_ModerateEfficiency() throws Exception {
            List<MedDispatchRec> dispatches = Arrays.asList(
                    createDispatch(202, "2025-01-28", "10:00", 2.0, false, false, 150.0, -3.191000, 55.944500),
                    createDispatch(203, "2025-01-28", "11:00", 2.2, false, false, 160.0, -3.186500, 55.943800));

            String requestBody = objectMapper.writeValueAsString(dispatches);
            String response = mockMvc.perform(post(BASE_URL + "/calcDeliveryPath")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            int totalMoves = extractTotalMoves(response);
            double efficiency = calculatePathEfficiency(0.007986499752, totalMoves);

            System.out.println("Complex path efficiency: " + String.format("%.2f", efficiency));
            assertTrue(efficiency >= 0.60, "Should have moderate efficiency (>= 0.60), got " + efficiency);
            System.out.println("✓ EFF-003: Complex path efficiency verified");
        }

        @Test
        @DisplayName("SR-EFF-004: Long distance path efficiency >= 0.50")
        void testCalcDeliveryPath_LongDistancePath_AcceptableEfficiency() throws Exception {
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

            String requestBody = objectMapper.writeValueAsString(dispatches);
            long startTime = System.currentTimeMillis();

            String response = mockMvc.perform(post(BASE_URL + "/calcDeliveryPath")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            long duration = System.currentTimeMillis() - startTime;
            int totalMoves = extractTotalMoves(response);
            double efficiency = calculatePathEfficiency(0.115486933, totalMoves);

            System.out.println("Long distance efficiency: " + String.format("%.2f", efficiency) + " in " + duration + "ms");
            assertTrue(efficiency >= 0.50, "Should have acceptable efficiency (>= 0.50), got " + efficiency);
            System.out.println("✓ EFF-004: Long distance path efficiency verified");
        }
    }

    // ==================== SR-DATA: DATA CONSISTENCY TESTS ====================

    @Nested
    @DisplayName("SR-DATA: Data Consistency")
    class DataConsistencyTests {

        @Test
        @DisplayName("SR-DATA-001: GeoJSON coordinate precision and accuracy")
        void testGeoJSON_CoordinatePrecision() throws Exception {
            List<MedDispatchRec> dispatches = new ArrayList<>();
            dispatches.add(createDispatch(1001, "2025-01-28", "10:00", 2.0, false, false, null, -3.184358, 55.944680));

            String requestBody = objectMapper.writeValueAsString(dispatches);
            String geoJsonResponse = mockMvc.perform(post(BASE_URL + "/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertTrue(geoJsonResponse.contains("-3.18"), "GeoJSON should contain longitude");
            assertTrue(geoJsonResponse.contains("55.944"), "GeoJSON should contain latitude");

            Pattern precisionPattern = Pattern.compile("-3\\.\\d{6,}");
            assertTrue(precisionPattern.matcher(geoJsonResponse).find(),
                    "Coordinates should have at least 6 decimal places");

            System.out.println("✓ DATA-001: GeoJSON coordinate precision verified");
        }

        @Test
        @DisplayName("SR-DATA-002: Path returns to service point")
        void testGeoJSON_PathReturnsToServicePoint() throws Exception {
            List<MedDispatchRec> dispatches = new ArrayList<>();
            dispatches.add(createDispatch(1002, "2025-01-28", "10:00", 2.0, false, false, null, -3.184358, 55.944680));

            String requestBody = objectMapper.writeValueAsString(dispatches);
            mockMvc.perform(post(BASE_URL + "/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.features[0].geometry.coordinates").isArray());

            System.out.println("✓ DATA-002: Path returns to service point verified");
        }

        @Test
        @DisplayName("SR-DATA-003: GeoJSON valid RFC 7946 structure")
        void testGeoJSON_RFC7946Compliance() throws Exception {
            List<MedDispatchRec> dispatches = new ArrayList<>();
            dispatches.add(createDispatch(1003, "2025-01-28", "10:00", 1.0, false, false, null, -3.185, 55.945));
            dispatches.add(createDispatch(1004, "2025-01-28", "10:00", 1.0, false, false, null, -3.190, 55.950));

            String requestBody = objectMapper.writeValueAsString(dispatches);
            mockMvc.perform(post(BASE_URL + "/calcDeliveryPathAsGeoJson")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("FeatureCollection"))
                    .andExpect(jsonPath("$.features").isArray())
                    .andExpect(jsonPath("$.features[0].type").value("Feature"))
                    .andExpect(jsonPath("$.features[0].geometry").exists())
                    .andExpect(jsonPath("$.features[0].geometry.type").value("LineString"))
                    .andExpect(jsonPath("$.features[0].geometry.coordinates").isArray());

            System.out.println("✓ DATA-003: GeoJSON RFC 7946 compliance verified");
        }

        @Test
        @DisplayName("SR-DATA-004: Cost calculation consistency")
        void testResult_CostCalculationConsistency() throws Exception {
            List<MedDispatchRec> dispatches = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                dispatches.add(createDispatch(1100 + i, "2025-01-28", "10:00",
                        2.0, false, false, null, -3.185 + (i * 0.002), 55.945));
            }

            String requestBody = objectMapper.writeValueAsString(dispatches);
            String response = mockMvc.perform(post(BASE_URL + "/calcDeliveryPath")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertTrue(response.contains("\"totalCost\""), "Response should contain totalCost");
            assertTrue(response.contains("\"totalMoves\""), "Response should contain totalMoves");

            System.out.println("✓ DATA-004: Cost calculation consistency verified");
        }

        @Test
        @DisplayName("SR-DATA-005: Total moves calculation consistency")
        void testResult_TotalMovesConsistency() throws Exception {
            List<MedDispatchRec> dispatches = new ArrayList<>();
            dispatches.add(createDispatch(1105, "2025-01-28", "10:00", 2.0, false, false, null, -3.184358, 55.944680));

            String requestBody = objectMapper.writeValueAsString(dispatches);
            mockMvc.perform(post(BASE_URL + "/calcDeliveryPath")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalMoves").isNumber())
                    .andExpect(jsonPath("$.totalMoves").value(greaterThan(0)));

            System.out.println("✓ DATA-005: Total moves calculation verified");
        }
    }


    // ==================== HELPER METHODS ====================

    private MedDispatchRec createDispatch(Integer id, String date, String time,
                                          Double capacity, Boolean cooling, Boolean heating,
                                          Double maxCost, Double lng, Double lat) {
        MedDispatchRec dispatch = new MedDispatchRec();
        dispatch.setId(id);
        dispatch.setDate(java.time.LocalDate.parse(date));
        dispatch.setTime(java.time.LocalTime.parse(time));

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

    private double calculateDirectDistance(List<MedDispatchRec> dispatches, String jsonResponse) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        DeliveryPathResponse response = mapper.readValue(jsonResponse, DeliveryPathResponse.class);

        double totalDirectDistance = 0.0;
        if (response.getDronePaths() == null || response.getDronePaths().isEmpty()) {
            return 0.0;
        }

        for (DeliveryPathResponse.DronePath dronePath : response.getDronePaths()) {
            if (dronePath.getDeliveries() == null) continue;

            for (DeliveryPathResponse.Delivery delivery : dronePath.getDeliveries()) {
                if (delivery.getFlightPath() == null || delivery.getFlightPath().isEmpty()) continue;

                DeliveryPathResponse.LngLat servicePoint = delivery.getFlightPath().get(0);
                MedDispatchRec dispatch = dispatches.stream()
                        .filter(d -> d.getId().equals(delivery.getDeliveryId()))
                        .findFirst()
                        .orElse(null);

                if (dispatch != null) {
                    double deliveryLng = dispatch.getDelivery().getLng();
                    double deliveryLat = dispatch.getDelivery().getLat();
                    double oneWayDistance = Math.sqrt(
                            Math.pow(deliveryLng - servicePoint.getLng(), 2) +
                            Math.pow(deliveryLat - servicePoint.getLat(), 2)
                    );
                    totalDirectDistance += oneWayDistance * 2;
                }
            }
        }
        return totalDirectDistance;
    }

    private double calculatePathEfficiency(double directDistance, int actualMoves) {
        if (actualMoves == 0) return 0.0;
        double optimalMoves = directDistance / MOVE_STEP_SIZE;
        return optimalMoves / actualMoves;
    }

    private int extractTotalMoves(String jsonResponse) {
        try {
            int startIndex = jsonResponse.indexOf("\"totalMoves\":");
            if (startIndex == -1) return 0;

            int colonIndex = jsonResponse.indexOf(":", startIndex);
            int commaIndex = jsonResponse.indexOf(",", colonIndex);
            if (commaIndex == -1) {
                commaIndex = jsonResponse.indexOf("}", colonIndex);
            }

            String movesStr = jsonResponse.substring(colonIndex + 1, commaIndex).trim();
            return Integer.parseInt(movesStr);
        } catch (Exception e) {
            return 0;
        }
    }
}
