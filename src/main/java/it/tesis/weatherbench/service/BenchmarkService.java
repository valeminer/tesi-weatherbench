package it.tesis.weatherbench.service;

import java.util.List;
import java.util.Map;

import it.tesis.weatherbench.dto.benchmark.BenchmarkReportDTO;
import it.tesis.weatherbench.enumeration.StorageEngineType;
import it.tesis.weatherbench.enumeration.WriteMode;

public interface BenchmarkService {

    BenchmarkReportDTO writeBatch(StorageEngineType engine, int records, WriteMode mode, Integer chunkSize, boolean purgeBefore);

    BenchmarkReportDTO readRange(StorageEngineType engine, int datasetSize, int iterations, int windowHours, int warmup);

    BenchmarkReportDTO aggregate(StorageEngineType engine, int datasetSize, int iterations, int warmup);

    BenchmarkReportDTO readById(StorageEngineType engine, int datasetSize, int iterations, int warmup);

    Map<String, Object> datasetPreview(int records, int limit);

    long count(StorageEngineType engine);

    void purge(StorageEngineType engine);

    List<BenchmarkReportDTO> reports();

    String reportsAsCsv();

    void clearReports();
}
