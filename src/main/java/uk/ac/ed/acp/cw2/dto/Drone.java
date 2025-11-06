package uk.ac.ed.acp.cw2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Drone DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Drone {
    @JsonProperty("name")
    private String name;

    @JsonProperty("id")
    private Integer id;

    @JsonProperty("capability")
    private Capability capability;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Capability {
        @JsonProperty("cooling")
        private Boolean cooling;

        @JsonProperty("heating")
        private Boolean heating;

        @JsonProperty("capacity")
        private Double capacity;

        @JsonProperty("maxMoves")
        private Integer maxMoves;

        @JsonProperty("costPerMove")
        private Double costPerMove;

        @JsonProperty("costInitial")
        private Double costInitial;

        @JsonProperty("costFinal")
        private Double costFinal;
    }
}
