package uk.ac.ed.acp.cw2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.ac.ed.acp.cw2.controller.GeoController;
import uk.ac.ed.acp.cw2.dto.LngLat;
import uk.ac.ed.acp.cw2.service.GeoService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class GeoControllerWebTest {

    private MockMvc mockMvc;

    @Mock
    private GeoService geoService;

    @InjectMocks
    private GeoController geoController;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(geoController).build();
    }

    @Test
    void index_ShouldReturnWelcomeMessage() throws Exception {
        mockMvc.perform(get("/api/v1/"))
                .andExpect(status().isOk());
    }

    @Test
    void uid_ShouldReturnStaticUid() throws Exception {
        mockMvc.perform(get("/api/v1/uid"))
                .andExpect(status().isOk())
                .andExpect(content().string("s2564099"));
    }

    // distanceTo
    @Test
    void distanceTo_ShouldReturnDistance_WhenValidRequest() throws Exception {
        // Given
        when(geoService.calculateDistance(any(), any())).thenReturn(0.003616);

        String requestBody = """
            {
                "position1": {
                    "lng": -3.192473,
                    "lat": 55.946233
                },
                "position2": {
                    "lng": -3.192473,
                    "lat": 55.942617
                }
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/v1/distanceTo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().string("0.003616"));
    }

    @Test
    void distanceTo_ShouldReturnBadRequest_WhenMissingField() throws Exception {
        // Given
        String requestBody = """
            {
                "position1": {
                    "lng": -3.192473
                },
                "position2": {
                    "lng": -3.192473,
                    "lat": 55.942617
                }
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/v1/distanceTo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void distanceTo_ShouldReturnBadRequest_WhenEmptyRequestBody() throws Exception {
        mockMvc.perform(post("/api/v1/distanceTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    // isCloseTo
    @Test
    void isCloseTo_ShouldReturnTrue_WhenPositionsAreClose() throws Exception {
        // Given
        when(geoService.isCloseTo(any(), any())).thenReturn(true);

        String requestBody = """
            {
                "position1": {
                    "lng": -3.192473,
                    "lat": 55.946233
                },
                "position2": {
                    "lng": -3.192473,
                    "lat": 55.946233
                }
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/v1/isCloseTo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void isCloseTo_ShouldReturnFalse_WhenPositionsAreFar() throws Exception {
        // Given
        when(geoService.isCloseTo(any(), any())).thenReturn(false);

        String requestBody = """
            {
                "position1": {
                    "lng": -3.192473,
                    "lat": 55.946233
                },
                "position2": {
                    "lng": -3.192473,
                    "lat": 55.942617
                }
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/v1/isCloseTo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    void isCloseTo_ShouldReturnBadRequest_WhenEmptyRequestBody() throws Exception {
        mockMvc.perform(post("/api/v1/isCloseTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void isCloseTo_ShouldReturnBadRequest_WhenMissingField() throws Exception {
        String requestBody = """
        {
            "position1": {
                "lng": -3.192473,
                "lat": 55.946233
            }
        }
        """;

        mockMvc.perform(post("/api/v1/isCloseTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }


    // nextPosition
    @Test
    void nextPosition_ShouldReturnNewPosition_WhenValidRequest() throws Exception {
        // Given
        LngLat expectedPosition = new LngLat(-3.192367, 55.946339);
        when(geoService.nextPosition(any(), anyDouble())).thenReturn(expectedPosition);

        String requestBody = """
            {
                "start": {
                    "lng": -3.192473,
                    "lat": 55.946233
                },
                "angle": 45.0
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/v1/nextPosition")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lng").value(-3.192367))
                .andExpect(jsonPath("$.lat").value(55.946339));
    }

    @Test
    void nextPosition_ShouldReturnBadRequest_WhenInvalidAngle() throws Exception {
        // Given
        String requestBody = """
            {
                "start": {
                    "lng": -3.192473,
                    "lat": 55.946233
                },
                "angle": 45.5
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/v1/nextPosition")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nextPosition_ShouldReturnBadRequest_WhenNullAngle() throws Exception {
        String requestBody = """
        {
            "start": {
                "lng": -3.192473,
                "lat": 55.946233
            },
            "angle": null
        }
        """;

        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nextPosition_ShouldReturnBadRequest_WhenMissingField() throws Exception {
        String requestBody = """
        {
            "angle": 45.0
        }
        """;

        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }


    // isInRegion
    @Test
    void isInRegion_ShouldReturnTrue_WhenPositionIsInside() throws Exception {
        // Given
        when(geoService.isInRegion(any(), any())).thenReturn(true);

        String requestBody = """
            {
              "position": {
                "lng": -3.188,
                "lat": 55.944
              },
              "region": {
                "name": "central",
                "vertices": [
                  {
                    "lng": -3.192473,
                    "lat": 55.946233
                  },
                  {
                    "lng": -3.192473,
                    "lat": 55.942617
                  },
                  {
                    "lng": -3.184319,
                    "lat": 55.942617
                  },
                  {
                    "lng": -3.184319,
                    "lat": 55.946233
                  },
                  {
                    "lng": -3.192473,
                    "lat": 55.946233
                  }
                ]
              }
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/v1/isInRegion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }
    @Test
    void isInRegion_ShouldReturnBadRequest_WhenRegionIsNotClosed() throws Exception {
        // Given
        String requestBody = """
            {
              "position": {
                "lng": -3.188,
                "lat": 55.944
              },
              "region": {
                "name": "central",
                "vertices": [
                  {
                    "lng": -3.192473,
                    "lat": 55.946233
                  },
                  {
                    "lng": -3.192473,
                    "lat": 55.942617
                  },
                  {
                    "lng": -3.184319,
                    "lat": 55.942617
                  },
                  {
                    "lng": -3.184319,
                    "lat": 55.946233
                  }
                ]
              }
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/v1/isInRegion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void isInRegion_ShouldReturnBadRequest_WhenEmptyRequestBody() throws Exception {
        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void isInRegion_ShouldReturnBadRequest_WhenMissingField() throws Exception {
        String requestBody = """
        {
          "region": {
            "name": "central",
            "vertices": [
              {
                "lng": -3.192473,
                "lat": 55.946233
              },
              {
                "lng": -3.192473,
                "lat": 55.942617
              },
              {
                "lng": -3.184319,
                "lat": 55.942617
              },
              {
                "lng": -3.184319,
                "lat": 55.946233
              },
              {
                "lng": -3.192473,
                "lat": 55.946233
              }
            ]
          }
        }
        """;

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

}
