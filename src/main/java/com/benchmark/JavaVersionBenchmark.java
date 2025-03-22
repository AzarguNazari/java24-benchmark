package com.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

@BenchmarkMode({Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgsAppend = {"-Xms1g", "-Xmx1g"})
@Warmup(iterations = 2)
@Measurement(iterations = 3)
public class JavaVersionBenchmark {

    private List<Integer> numbers;
    private String[] strings;
    private static final int ARRAY_SIZE = 100_000;
    private static final int STRING_SIZE = 1_000;
    private File tempFile;
    private ExecutorService executorService;

    @Setup
    public void setup() throws IOException {
        // Initialize data for benchmarks
        numbers = new ArrayList<>(ARRAY_SIZE);
        Random random = new Random(42);
        for (int i = 0; i < ARRAY_SIZE; i++) {
            numbers.add(random.nextInt());
        }

        strings = new String[STRING_SIZE];
        for (int i = 0; i < STRING_SIZE; i++) {
            strings[i] = UUID.randomUUID().toString();
        }

        // Create temp file for I/O benchmarks with fewer lines
        tempFile = File.createTempFile("benchmark", ".txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
            for (int i = 0; i < 100; i++) {
                writer.write(UUID.randomUUID().toString());
                writer.newLine();
            }
        }

        executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    @TearDown
    public void tearDown() {
        executorService.shutdown();
        tempFile.delete();
    }

    @Benchmark
    public void streamProcessing(Blackhole blackhole) {
        long sum = numbers.stream()
                .filter(n -> n > 0)
                .mapToLong(Integer::longValue)
                .sum();
        blackhole.consume(sum);
    }

    @Benchmark
    public void parallelStreamProcessing(Blackhole blackhole) {
        long sum = numbers.parallelStream()
                .filter(n -> n > 0)
                .mapToLong(Integer::longValue)
                .sum();
        blackhole.consume(sum);
    }

    @Benchmark
    public void stringConcatenation(Blackhole blackhole) {
        StringBuilder sb = new StringBuilder();
        for (String s : strings) {
            sb.append(s);
        }
        blackhole.consume(sb.toString());
    }

    @Benchmark
    public void sorting(Blackhole blackhole) {
        List<Integer> copyList = new ArrayList<>(numbers);
        Collections.sort(copyList);
        blackhole.consume(copyList);
    }

    @Benchmark
    public void fileIO() throws IOException {
        File tempOutFile = new File(tempFile.getPath() + ".tmp");
        try (BufferedReader reader = new BufferedReader(new FileReader(tempFile));
             BufferedWriter writer = new BufferedWriter(new FileWriter(tempOutFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
            }
        }
        tempOutFile.delete();
    }

    @Benchmark
    public void concurrentProcessing(Blackhole blackhole) throws InterruptedException, ExecutionException {
        List<Future<Long>> futures = new ArrayList<>();
        int batchSize = ARRAY_SIZE / Runtime.getRuntime().availableProcessors();
        
        for (int i = 0; i < numbers.size(); i += batchSize) {
            final int start = i;
            final int end = Math.min(i + batchSize, numbers.size());
            futures.add(executorService.submit(() -> {
                return numbers.subList(start, end).stream()
                        .mapToLong(Integer::longValue)
                        .sum();
            }));
        }

        long totalSum = 0;
        for (Future<Long> future : futures) {
            totalSum += future.get();
        }
        blackhole.consume(totalSum);
    }

    @Benchmark
    public void memoryAllocation(Blackhole blackhole) {
        List<byte[]> allocations = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            allocations.add(new byte[1024]);
        }
        blackhole.consume(allocations);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(JavaVersionBenchmark.class.getSimpleName())
                .resultFormat(ResultFormatType.JSON)
                .result("benchmark-results.json")
                .build();

        new Runner(opt).run();
    }
} 