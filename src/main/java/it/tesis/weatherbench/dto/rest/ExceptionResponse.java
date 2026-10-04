package it.tesis.weatherbench.dto.rest;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ExceptionResponse {
    private Date timestamp;
    private String details;
    private String reason;
    private int code;
}
