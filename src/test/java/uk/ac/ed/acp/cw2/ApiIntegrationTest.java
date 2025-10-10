package uk.ac.ed.acp.cw2;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

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
    void distanceTo_ShouldReturnBadRequest_WhenInvalidRequest() {
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

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
            createUrl("/nextPosition"), entity, String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("lng");
        assertThat(response.getBody()).contains("lat");
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
}
