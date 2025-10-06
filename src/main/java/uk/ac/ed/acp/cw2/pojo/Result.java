package uk.ac.ed.acp.cw2.pojo;

import lombok.Data;

import java.io.Serializable;

/**
 * Backend unified return result
 */
@Data
public class Result implements Serializable {

    private Integer code; // Encoding: 1 for success, 0 for failure
    private String msg;   // Error message
    private Object data;  // Data

    /**
     * Success result with no data
     * @return Result object with success status
     */
    public static Result success() {
        Result result = new Result();
        result.code = 1;
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
        result.code = 1;
        result.msg = "success";
        return result;
    }

    /**
     * Error result
     * @param msg Error message
     * @return Result object with error status
     */
    public static Result error(String msg) {
        Result result = new Result();
        result.msg = msg;
        result.code = 0;
        return result;
    }
}
