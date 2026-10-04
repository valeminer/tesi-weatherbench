package it.tesis.weatherbench.exception;

public class WrongInputParameterException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public WrongInputParameterException(String message) {
        super(message);
    }
}
