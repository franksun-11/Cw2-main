// src/main/java/uk/ac/ed/acp/cw2/dto/MedDispatchRec.java
package uk.ac.ed.acp.cw2.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Represents a medical dispatch record containing requirements for drone delivery
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MedDispatchRec {

    @NotNull(message = "Dispatch ID cannot be null")
    @JsonProperty("id")
    private Integer id;

    @NotNull(message = "Date cannot be null")
    @JsonProperty("date")
    private LocalDate date;

    @NotNull(message = "Time cannot be null")
    @JsonProperty("time")
    private LocalTime time;

    @NotNull(message = "Requirements cannot be null")
    @Valid
    @JsonProperty("requirements")
    private Requirements requirements;

    @NotNull(message = "Delivery location cannot be null")
    @Valid
    @JsonProperty("delivery")
    private Delivery delivery;

    /**
     * Nested class for delivery requirements
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Requirements {
        @JsonProperty("capacity")
        private Double capacity;

        @JsonProperty("cooling")
        private Boolean cooling;

        @JsonProperty("heating")
        private Boolean heating;

        @JsonProperty("maxCost")
        private Double maxCost;
    }

    /**
     * Nested class for delivery location
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Delivery {
        @JsonProperty("lng")
        private Double lng;

        @JsonProperty("lat")
        private Double lat;
    }
}
