package io.github.commonpay.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Standard response body used by the optional REST controller. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayApiResponse<T> {

    private int code;
    private String message;
    private T data;

    public static <T> PayApiResponse<T> success(T data) {
        return new PayApiResponse<>(0, "success", data);
    }
}
