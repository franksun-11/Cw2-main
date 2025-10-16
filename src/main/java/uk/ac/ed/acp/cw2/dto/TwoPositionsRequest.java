package uk.ac.ed.acp.cw2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * distanceTo and isCloseTo RequestBody
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TwoPositionsRequest {
    @NotNull(message = "Position1 cannot be null")
    @Valid
    @JsonProperty("position1")
    private LngLat position1;

    @NotNull(message = "Position2 cannot be null")
    @Valid
    @JsonProperty("position2")
    private LngLat position2;
}