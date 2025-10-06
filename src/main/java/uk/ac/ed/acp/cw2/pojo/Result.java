package uk.ac.ed.acp.cw2.pojo;

import lombok.Data;
import org.springframework.http.HttpStatus;

import java.io.Serializable;

/**
 * Backend unified return result
 */
@Data
public class Result implements Serializable {

    private Integer code; // HTTP status code
    private String msg;   // Error message
    private Object data;  // Data

    /**
     * Success result with no data
     * @return Result object with success status
     */
    public static Result success() {
        Result result = new Result();
        result.code = HttpStatus.OK.value(); // 200
        result.msg = "success";
        return result;
    }

    /**
     * Success result with data
     * @param object Data to be included in the result
     * @return Result object with success status and data
     */
    public static Result success(Object object) {
        Result result = new Result();
        result.data = object;
        result.code = HttpStatus.OK.value(); // 200
        result.msg = "success";
        return result;
    }

    /**
     * Error result for bad requests
     * @param msg Error message
     * @return Result object with error status
     */
    public static Result error(String msg) {
        Result result = new Result();
        result.msg = msg;
        result.code = HttpStatus.BAD_REQUEST.value(); // 400
        return result;
    }
}
