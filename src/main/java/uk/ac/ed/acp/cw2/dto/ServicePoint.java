package uk.ac.ed.acp.cw2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a service point where drones can be dispatched from
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServicePoint {

    @JsonProperty("name")
    private String name;

    @JsonProperty("id")
    private Integer id;

    @JsonProperty("location")
    private Location location;

    /**
     * Nested class for location coordinates
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        @JsonProperty("lng")
        private Double lng;

        @JsonProperty("lat")
        private Double lat;

        @JsonProperty("alt")
        private Double alt;
    }
}