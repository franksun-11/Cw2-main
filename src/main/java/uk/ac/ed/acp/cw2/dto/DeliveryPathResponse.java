package uk.ac.ed.acp.cw2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Delivery path response DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryPathResponse {
    @JsonProperty("totalCost")
    private Double totalCost;

    @JsonProperty("totalMoves")
    private Integer totalMoves;

    @JsonProperty("dronePaths")
    private List<DronePath> dronePaths;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DronePath {
        @JsonProperty("droneId")
        private Integer droneId;

        @JsonProperty("deliveries")
        private List<Delivery> deliveries;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Delivery {
        @JsonProperty("deliveryId")
        private Integer deliveryId;

        @JsonProperty("flightPath")
        private List<LngLat> flightPath;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LngLat {
        @JsonProperty("lng")
        private Double lng;

        @JsonProperty("lat")
        private Double lat;
    }
}
