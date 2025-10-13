package uk.ac.ed.acp.cw2;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.within;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
class ApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String createUrl(String path) {
        return "http://localhost:" + port + "/api/v1" + path;
    }

    // distanceTo
    @Test
    void distanceTo_ShouldReturnDistance_WhenValidRequest() {
        // Given
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // When
        ResponseEntity<Double> response = restTemplate.postForEntity(
            createUrl("/distanceTo"), entity, Double.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isGreaterThan(0.0);
    }

    @Test
    void distanceTo_ShouldReturnBadRequest_WhenMissingField() {
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // When
        ResponseEntity<Double> response = restTemplate.postForEntity(
            createUrl("/distanceTo"), entity, Double.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void distanceTo_ShouldReturnBadRequest_WhenOutOfRange() {
        // Given
        String requestBody = """
        {
          "position1": {
            "lng": -200.0,
            "lat": 55.946233
          },
          "position2": {
            "lng": -3.192473,
            "lat": 55.942617
          }
        }
        """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // When
        ResponseEntity<Double> response = restTemplate.postForEntity(
                createUrl("/distanceTo"), entity, Double.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void distanceTo_ShouldReturnBadRequest_WhenPositiveLongitude() {
        // Given
        String requestBody = """
        {
          "position1": {
            "lng": 3.192473,
            "lat": 55.946233
          },
          "position2": {
            "lng": -3.192473,
            "lat": 55.942617
          }
        }
        """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // When
        ResponseEntity<Double> response = restTemplate.postForEntity(
                createUrl("/distanceTo"), entity, Double.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }


    // closeTo
    @Test
    void closeTo_ShouldReturnTrue_WhenPositionsAreClose() {
        // Given
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // When
        ResponseEntity<Boolean> response = restTemplate.postForEntity(
            createUrl("/closeTo"), entity, Boolean.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isTrue();
    }

    @Test
    void closeTo_ShouldReturnFalse_WhenPositionsAreFar() {
        // Given
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // When
        ResponseEntity<Boolean> response = restTemplate.postForEntity(
            createUrl("/closeTo"), entity, Boolean.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isFalse();
    }

    @Test
    void closeTo_ShouldReturnBadRequest_WhenMissingField() {
        // Given
        String requestBody = """
        {
          "position1": {
            "lng": -3.992473,
            "lat": 35.946233
          },
          "position2": {
            "lng": -32.192473
          }
        }
        """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // When
        ResponseEntity<Boolean> response = restTemplate.postForEntity(
                createUrl("/closeTo"), entity, Boolean.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void closeTo_ShouldReturnBadRequest_WhenNegativeLatitude() {
        // Given
        String requestBody = """
        {
          "position1": {
            "lng": -3.192473,
            "lat": -55.946233
          },
          "position2": {
            "lng": -3.192473,
            "lat": 55.942617
          }
        }
        """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // When
        ResponseEntity<Boolean> response = restTemplate.postForEntity(
                createUrl("/closeTo"), entity, Boolean.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }


    // nextPosition
    @Test
    void nextPosition_ShouldReturnNewPosition_WhenValidRequest() {
        // Given
        String requestBody = """
        {
          "start": {
            "lng": -3.192473,
            "lat": 55.946233
          },
          "angle": 45.0
        }
        """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // Calculate expected values
        double moveDistance = 0.00015;
        double radians = Math.toRadians(45.0);
        double expectedLng = -3.192473 + moveDistance * Math.cos(radians);
        double expectedLat = 55.946233 + moveDistance * Math.sin(radians);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                createUrl("/nextPosition"), entity, String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Parse response and check values
        assertThat(response.getBody()).contains("\"lng\":");
        assertThat(response.getBody()).contains("\"lat\":");

        // Extract values from response (simplified parsing)
        String body = response.getBody();
        int lngIndex = body.indexOf("\"lng\":");
        int latIndex = body.indexOf("\"lat\":");
        int lngStart = lngIndex + 6; // length of "\"lng\":"
        int latStart = latIndex + 6; // length of "\"lat\":"

        int lngEnd = body.indexOf(",", lngStart);
        if (lngEnd == -1) lngEnd = body.indexOf("}", lngStart);

        int latEnd = body.indexOf("}", latStart);
        if (latEnd == -1) latEnd = body.indexOf(",", latStart);

        double actualLng = Double.parseDouble(body.substring(lngStart, lngEnd).trim());
        double actualLat = Double.parseDouble(body.substring(latStart, latEnd).trim());

        assertThat(actualLng).isCloseTo(expectedLng, within(0.000001));
        assertThat(actualLat).isCloseTo(expectedLat, within(0.000001));
    }

    @Test
    void nextPosition_ShouldReturnBadRequest_WhenInvalidAngle() {
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
            createUrl("/nextPosition"), entity, String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void nextPosition_ShouldReturnNewPosition_WhenHoverAngle() {
        // Given
        String requestBody = """
        {
          "start": {
            "lng": -3.192473,
            "lat": 55.946233
          },
          "angle": 999.0
        }
        """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                createUrl("/nextPosition"), entity, String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("lng");
        assertThat(response.getBody()).contains("lat");
    }

    @Test
    void nextPosition_ShouldReturnBadRequest_WhenOutOfRange() {
        // Given
        String requestBody = """
        {
          "start": {
            "lng": -181.0,
            "lat": 55.946233
          },
          "angle": 45.0
        }
        """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                createUrl("/nextPosition"), entity, String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void nextPosition_ShouldReturnBadRequest_WhenAngleIsNull() {
        // Given
        String requestBody = """
        {
          "start": {
            "lng": -3.192473,
            "lat": 55.946233
          },
          "angle": null
        }
        """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                createUrl("/nextPosition"), entity, String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void nextPosition_ShouldReturnBadRequest_WhenMissingField() {
        // Given
        String requestBody = """
        {
          "start": {
            "lng": -3.192473,
            "lat": 55.946233
          }
        }
        """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                createUrl("/nextPosition"), entity, String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void nextPosition_ShouldReturnNewPosition_WithZeroAngle() {
        // Given
        String requestBody = """
        {
          "start": {
            "lng": -3.192473,
            "lat": 55.946233
          },
          "angle": 0.0
        }
        """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // Calculate expected values for 0 degree angle
        double moveDistance = 0.00015;
        double radians = Math.toRadians(0.0);
        double expectedLng = -3.192473 + moveDistance * Math.cos(radians); // Should increase
        double expectedLat = 55.946233 + moveDistance * Math.sin(radians);  // Should stay same

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                createUrl("/nextPosition"), entity, String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Parse response and check values
        assertThat(response.getBody()).contains("\"lng\":");
        assertThat(response.getBody()).contains("\"lat\":");

        // Extract values from response (simplified parsing)
        String body = response.getBody();
        int lngIndex = body.indexOf("\"lng\":");
        int latIndex = body.indexOf("\"lat\":");
        int lngStart = lngIndex + 6; // length of "\"lng\":"
        int latStart = latIndex + 6; // length of "\"lat\":"

        int lngEnd = body.indexOf(",", lngStart);
        if (lngEnd == -1) lngEnd = body.indexOf("}", lngStart);

        int latEnd = body.indexOf("}", latStart);
        if (latEnd == -1) latEnd = body.indexOf(",", latStart);

        double actualLng = Double.parseDouble(body.substring(lngStart, lngEnd).trim());
        double actualLat = Double.parseDouble(body.substring(latStart, latEnd).trim());

        assertThat(actualLng).isCloseTo(expectedLng, within(0.000001));
        assertThat(actualLat).isCloseTo(expectedLat, within(0.000001));
    }


    // isInRegion
    @Test
    void isInRegion_ShouldReturnTrue_WhenPositionIsInside() {
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
                  },
                  {
                    "lng": -3.192473,
                    "lat": 55.946233
                  }
                ]
              }
            }
            """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // When
        ResponseEntity<Boolean> response = restTemplate.postForEntity(
            createUrl("/isInRegion"), entity, Boolean.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isTrue();
    }

    @Test
    void isInRegion_ShouldReturnFalse_WhenPositionIsOutside() {
        // Given
        String requestBody = """
            {
              "position": {
                "lng": -3.180,
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // When
        ResponseEntity<Boolean> response = restTemplate.postForEntity(
            createUrl("/isInRegion"), entity, Boolean.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isFalse();
    }

    @Test
    void isInRegion_ShouldReturnBadRequest_WhenRegionIsNotClosed() {
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // When
        ResponseEntity<Boolean> response = restTemplate.postForEntity(
            createUrl("/isInRegion"), entity, Boolean.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void isInRegion_ShouldReturnBadRequest_WhenInvalidPosition() {
        // Given
        String requestBody = """
        {
          "position": {
            "lng": -3.188
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // When
        ResponseEntity<Boolean> response = restTemplate.postForEntity(
                createUrl("/isInRegion"), entity, Boolean.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void isInRegion_ShouldReturnBadRequest_WhenInvalidVertices() {
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
                "lng": -3.192473
              },
              {
                "lng": -3.192473,
                "lat": 55.942617
              },
              {
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // When
        ResponseEntity<Boolean> response = restTemplate.postForEntity(
                createUrl("/isInRegion"), entity, Boolean.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void isInRegion_ShouldReturnBadRequest_WhenInadequateVertices() {
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
              }
            ]
          }
        }
        """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // When
        ResponseEntity<Boolean> response = restTemplate.postForEntity(
                createUrl("/isInRegion"), entity, Boolean.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void isInRegion_ShouldReturnBadRequest_WhenPositiveLongitude() {
        // Given
        String requestBody = """
        {
          "position": {
            "lng": 3.188,
            "lat": 55.944
          },
          "region": {
            "name": "central",
            "vertices": [
              {
                "lng": 3.192473,
                "lat": 55.946233
              },
              {
                "lng": 3.192473,
                "lat": 55.942617
              },
              {
                "lng": 3.184319,
                "lat": 55.942617
              },
              {
                "lng": 3.184319,
                "lat": 55.946233
              },
              {
                "lng": 3.192473,
                "lat": 55.946233
              }
            ]
          }
        }
        """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // When
        ResponseEntity<Boolean> response = restTemplate.postForEntity(
                createUrl("/isInRegion"), entity, Boolean.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }


}




