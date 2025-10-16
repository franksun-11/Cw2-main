package uk.ac.ed.acp.cw2.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Longitude and latitude position class
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LngLat {
    @NotNull(message = "Longitude cannot be null")
    @JsonProperty("lng")
    private Double lng;

    @NotNull(message = "Latitude cannot be null")
    @JsonProperty("lat")
    private Double lat;

    /**
     * Verify if latitude and longitude are valid for Edinburgh area
     * Longitude should be negative (West) and latitude should be positive (North)
     */
    @JsonIgnore
    public boolean isValid() {
        return lng != null && lat != null &&
                lng >= -180 && lng <= 0 &&    // longitude must be negative (West)
                lat >= 0 && lat <= 90 &&      // latitude must be positive (North)
                lng >= -180 && lng <= 180 &&
                lat >= -90 && lat <= 90;
    }

}
