package uk.ac.ed.acp.cw2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a restricted/no-fly zone
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RestrictedArea {

    @JsonProperty("name")
    private String name;

    @JsonProperty("id")
    private Integer id;

    @JsonProperty("limits")
    private Limits limits;

    @JsonProperty("vertices")
    private List<Vertex> vertices;

    /**
     * Nested class for altitude limits
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Limits {
        @JsonProperty("lower")
        private Integer lower;

        @JsonProperty("upper")
        private Integer upper;
    }

    /**
     * Nested class for polygon vertices
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Vertex {
        @JsonProperty("lng")
        private Double lng;

        @JsonProperty("lat")
        private Double lat;

        @JsonProperty("alt")
        private Double alt;
    }
}