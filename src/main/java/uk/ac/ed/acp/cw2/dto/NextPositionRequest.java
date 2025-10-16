package uk.ac.ed.acp.cw2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * nextPosition RequestBody
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NextPositionRequest {
    @NotNull(message = "Start position cannot be null")
    @Valid
    @JsonProperty("start")
    private LngLat start;

    @NotNull(message = "Angle cannot be null")
    @JsonProperty("angle")
    private Double angle;
}