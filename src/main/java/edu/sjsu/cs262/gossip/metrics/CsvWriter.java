package edu.sjsu.cs262.gossip.metrics;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Writes rows of any record type to a CSV file. The header comes from the record's component names. */
public final class CsvWriter<T extends Record> implements AutoCloseable {

    private final BufferedWriter out;
    private final RecordComponent[] cols;

    public CsvWriter(Path file, Class<T> type) throws IOException {
        cols = type.getRecordComponents();
        Files.createDirectories(file.toAbsolutePath().getParent());
        out = Files.newBufferedWriter(file);
        out.write(Arrays.stream(cols).map(RecordComponent::getName).collect(Collectors.joining(",")));
        out.newLine();
    }

    /** Appends one row and flushes, so a run that is killed halfway still leaves usable data. */
    public synchronized void write(T row) throws IOException {
        var cells = new String[cols.length];
        try {
            for (int i = 0; i < cols.length; i++) cells[i] = String.valueOf(cols[i].getAccessor().invoke(row));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        out.write(String.join(",", cells));
        out.newLine();
        out.flush();
    }

    @Override
    public synchronized void close() throws IOException {
        out.close();
    }
}