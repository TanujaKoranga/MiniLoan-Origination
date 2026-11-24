package com.miniloan.utility;

import com.miniloan.model.PendingReviewRow;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class CsvExporter {

    /** Writes pending_reviews_YYYYMMDD.csv (business date) and returns its path. Overwrites same-day file. */
    public Path exportPendingReviews(List<PendingReviewRow> rows, int overdueDays,
                                     LocalDate businessDate, Path outDir) throws IOException {
        Files.createDirectories(outDir);
        Path file = outDir.resolve("pending_reviews_" + businessDate.format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv");
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("ApplicationNumber,CustomerName,Product,RequestedAmount,SubmittedAt,DaysWaiting,OverdueFlag");
            w.newLine();
            for (PendingReviewRow r : rows) {
                w.write(String.join(",",
                        esc(r.applicationNumber()), esc(r.customerName()), r.product().name(),
                        r.requestedAmount().toPlainString(), r.submittedAt().toString(),
                        String.valueOf(r.daysWaiting()), r.daysWaiting() > overdueDays ? "Y" : "N"));
                w.newLine();
            }
        }
        return file;
    }

    private static String esc(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
