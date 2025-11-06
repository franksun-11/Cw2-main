package uk.ac.ed.acp.cw2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

/**
 * Drone availability for a service point DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DroneForServicePoint {
    @JsonProperty("servicePointId")
    private Integer servicePointId;

    @JsonProperty("drones")
    private List<DronesAvailability> drones;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DronesAvailability {
        @JsonProperty("id")
        private String id;

        @JsonProperty("id")
        private List<Availability> availability;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Availability {
        @JsonProperty("dayOfWeek")
        private String dayOfWeek;

        @JsonProperty("from")
        private TimeDetail from;

        @JsonProperty("until")
        private TimeDetail until;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeDetail {
        @JsonProperty("hour")
        private Integer hour;

        @JsonProperty("minute")
        private Integer minute;

        @JsonProperty("second")
        private Integer second;

        @JsonProperty("nano")
        private Integer nano;
    }
}
