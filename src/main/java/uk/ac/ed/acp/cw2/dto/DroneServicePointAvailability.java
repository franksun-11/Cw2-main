package uk.ac.ed.acp.cw2.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

/**
 * Represents drone availability at a specific service point
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DroneServicePointAvailability {

    @JsonProperty("servicePointId")
    private Integer servicePointId;

    @JsonProperty("drones")
    private List<DroneAvailability> drones;

    /**
     * Nested class for individual drone availability
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DroneAvailability {
        @JsonProperty("id")
        private String id; // Note: String in API response

        @JsonProperty("availability")
        private List<TimeSlot> availability;
    }

    /**
     * Nested class for time slot availability
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSlot {
        @JsonProperty("dayOfWeek")
        private String dayOfWeek; // e.g., "MONDAY"

        @JsonProperty("from")
        @JsonFormat(pattern = "HH:mm:ss")
        private LocalTime from;

        @JsonProperty("until")
        @JsonFormat(pattern = "HH:mm:ss")
        private LocalTime until;
    }
}