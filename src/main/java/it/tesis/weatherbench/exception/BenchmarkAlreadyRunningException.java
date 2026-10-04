package it.tesis.weatherbench.exception;

public class BenchmarkAlreadyRunningException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BenchmarkAlreadyRunningException(String message) {
        super(message);
    }
}
