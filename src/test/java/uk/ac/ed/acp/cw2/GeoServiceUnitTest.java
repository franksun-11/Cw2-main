package uk.ac.ed.acp.cw2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.ed.acp.cw2.dto.LngLat;
import uk.ac.ed.acp.cw2.service.impl.GeoServiceImpl;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.within;

@ExtendWith(MockitoExtension.class)
class GeoServiceUnitTest {

    @InjectMocks
    private GeoServiceImpl geoService;

    // distanceTo
    @Test
    void distanceTo_ShouldReturnCorrectDistance() {
        // Given
        LngLat pos1 = new LngLat(-3.192473, 55.946233);
        LngLat pos2 = new LngLat(-3.192473, 55.942617);

        // When
        double distance = geoService.calculateDistance(pos1, pos2);

        // Then
        assertThat(distance).isCloseTo(0.003616, within(0.000001));
    }

    @Test
    void distanceTo_ShouldReturnZero_WhenSamePosition() {
        // Given
        LngLat pos = new LngLat(-3.192473, 55.946233);

        // When
        double distance = geoService.calculateDistance(pos, pos);

        // Then
        assertThat(distance).isCloseTo(0.0, within(0.000001));
    }


    // isCloseTo
    @Test
    void isCloseTo_ShouldReturnTrue_WhenPositionsAreClose() {
        // Given
        LngLat pos1 = new LngLat(-3.192473, 55.946233);
        LngLat pos2 = new LngLat(-3.192473, 55.946233);

        // When
        boolean result = geoService.isCloseTo(pos1, pos2);

        // Then
        assertTrue(result);
    }

    @Test
    void isCloseTo_ShouldReturnFalse_WhenPositionsAreFar() {
        // Given
        LngLat pos1 = new LngLat(-3.192473, 55.946233);
        LngLat pos2 = new LngLat(-3.192473, 55.942617);

        // When
        boolean result = geoService.isCloseTo(pos1, pos2);

        // Then
        assertFalse(result);
    }

    @Test
    void isCloseTo_ShouldReturnFalse_WhenDistanceEqualToThreshold() {
        // Given
        LngLat pos1 = new LngLat(-3.192473, 55.946233);
        LngLat pos2 = new LngLat(-3.192473 - 0.00015, 55.946233); // exactly 0.00015

        // When
        boolean result = geoService.isCloseTo(pos1, pos2);

        // Then
        assertFalse(result);
    }


    // nextPosition
    @Test
    void nextPosition_ShouldCalculateNewPosition_WhenValidAngle() {
        // Given
        LngLat start = new LngLat(-3.192473, 55.946233);
        double angle = 45.0;

        // When
        LngLat result = geoService.nextPosition(start, angle);

        // Then
        assertThat(result.getLng()).isNotEqualTo(start.getLng());
        assertThat(result.getLat()).isNotEqualTo(start.getLat());
        assertThat(result.getLng()).isNotNull();
        assertThat(result.getLat()).isNotNull();
    }

    @Test
    void nextPosition_ShouldReturnNewPosition_WithZeroAngle() {
        // Given
        LngLat start = new LngLat(-3.192473, 55.946233);
        double angle = 0.0;

        // When
        LngLat result = geoService.nextPosition(start, angle);

        // Then
        assertThat(result.getLng()).isGreaterThan(start.getLng());
        assertThat(result.getLat()).isCloseTo(start.getLat(), within(0.000001));
    }


    // isInRegion
    @Test
    void isInRegion_ShouldReturnTrue_WhenPositionIsInside() {
        // Given
        LngLat position = new LngLat(-3.188, 55.944);
        List<LngLat> vertices = Arrays.asList(
                new LngLat(-3.192473, 55.946233),
                new LngLat(-3.192473, 55.942617),
                new LngLat(-3.184319, 55.942617),
                new LngLat(-3.184319, 55.946233),
                new LngLat(-3.192473, 55.946233)
        );

        // When
        boolean result = geoService.isInRegion(position, vertices);

        // Then
        assertTrue(result);
    }
    @Test
    void isInRegion_ShouldReturnFalse_WhenPositionIsOutside() {
        // Given
        LngLat position = new LngLat(-3.180, 55.944); // Outside the region
        List<LngLat> vertices = Arrays.asList(
                new LngLat(-3.192473, 55.946233),
                new LngLat(-3.192473, 55.942617),
                new LngLat(-3.184319, 55.942617),
                new LngLat(-3.184319, 55.946233),
                new LngLat(-3.192473, 55.946233)
        );

        // When
        boolean result = geoService.isInRegion(position, vertices);

        // Then
        assertFalse(result);
    }
    @Test
    void isInRegion_EdgeCase_WhenPositionOnBoundary() {
        // Given
        LngLat position = new LngLat(-3.192473, 55.944); // On the boundary
        List<LngLat> vertices = Arrays.asList(
                new LngLat(-3.192473, 55.946233),
                new LngLat(-3.192473, 55.942617),
                new LngLat(-3.184319, 55.942617),
                new LngLat(-3.184319, 55.946233),
                new LngLat(-3.192473, 55.946233)
        );

        // When
        boolean result = geoService.isInRegion(position, vertices);

        // This test ensures the method handles boundary cases without exception
        assertTrue(result);
    }
    @Test
    void isInRegion_ShouldReturnFalse_WhenRegionIsNotClosed() {
        // Given
        LngLat position = new LngLat(-3.188, 55.944);
        List<LngLat> vertices = Arrays.asList(
                new LngLat(-3.192473, 55.946233),
                new LngLat(-3.192473, 55.942617),
                new LngLat(-3.184319, 55.942617),
                new LngLat(-3.184319, 55.946233)
        );

        // When
        boolean result = geoService.isInRegion(position, vertices);

        // Then
        assertFalse(result);
    }
    @Test
    void isInRegion_ShouldReturnFalse_WhenVerticesAreNull() {
        // Given
        LngLat position = new LngLat(-3.188, 55.944);
        List<LngLat> vertices = null;

        // When
        boolean result = geoService.isInRegion(position, vertices);

        // Then
        assertFalse(result);
    }
    @Test
    void isInRegion_ShouldReturnFalse_WhenVerticesAreInsufficient() {
        // Given
        LngLat position = new LngLat(-3.188, 55.944);
        List<LngLat> vertices = Arrays.asList(
                new LngLat(-3.192473, 55.946233),
                new LngLat(-3.192473, 55.942617)
        );

        // When
        boolean result = geoService.isInRegion(position, vertices);

        // Then
        assertFalse(result);
    }


}
