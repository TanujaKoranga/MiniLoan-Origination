package com.miniloan.dao;

import com.miniloan.model.BusinessException;
import com.miniloan.model.ReportQuery;
import com.miniloan.model.ReportResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads the parameterized queries from reports.sql and runs them with PreparedStatement. */
public class ReportDao {
    private static final Pattern REPORT = Pattern.compile("^--\\s+@REPORT\\s+(\\d+)\\.(\\d+)\\s*\\|\\s*(.*)$");
    private static final Pattern PARAMS = Pattern.compile("^--\\s+@PARAMS\\s*(.*)$");
    private static final Set<String> GRANULARITIES = Set.of("DAILY", "MONTHLY", "QUARTERLY");

    public List<ReportQuery> loadQueries(Path file) throws IOException {
        List<ReportQuery> out = new ArrayList<>();
        String id = null, title = null;
        int part = 0;
        List<String> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            Matcher m = REPORT.matcher(line);
            if (m.matches()) {
                addIfComplete(out, id, part, title, params, sql);
                id = m.group(1);
                part = Integer.parseInt(m.group(2));
                title = m.group(3).trim();
                params = new ArrayList<>();
                sql = new StringBuilder();
                continue;
            }
            if (id == null) continue;
            Matcher pm = PARAMS.matcher(line);
            if (pm.matches()) {
                for (String p : pm.group(1).split(",")) if (!p.isBlank()) params.add(p.trim());
                continue;
            }
            if (line.isEmpty() || line.startsWith("--")) continue;
            sql.append(line).append('\n');
        }
        addIfComplete(out, id, part, title, params, sql);
        return out;
    }

    private static void addIfComplete(List<ReportQuery> out, String id, int part, String title,
                                      List<String> params, StringBuilder sql) {
        if (id == null) return;
        String s = sql.toString().trim();
        if (s.isEmpty()) return;
        if (s.endsWith(";")) s = s.substring(0, s.length() - 1);
        out.add(new ReportQuery(id, part, title, List.copyOf(params), s));
    }

    /** Distinct parameter names in first-use order (what the console should prompt for). */
    public static List<String> distinctParams(ReportQuery q) { return new ArrayList<>(new LinkedHashSet<>(q.params())); }

    public ReportResult execute(Connection conn, ReportQuery q, Map<String, String> values) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(q.sql())) {
            int i = 1;
            for (String name : q.params()) bind(ps, i++, name, values.get(name));
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                List<String> cols = new ArrayList<>();
                for (int c = 1; c <= md.getColumnCount(); c++) cols.add(md.getColumnLabel(c));
                List<List<Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    List<Object> row = new ArrayList<>();
                    for (int c = 1; c <= cols.size(); c++) row.add(rs.getObject(c));
                    rows.add(row);
                }
                return new ReportResult(q.title(), cols, rows);
            }
        }
    }

    private static void bind(PreparedStatement ps, int idx, String name, String v) throws SQLException {
        if (v == null || v.isBlank()) {
            if (name.endsWith("_date") || name.equals("granularity"))
                throw new BusinessException("Parameter '" + name + "' is required.");
            ps.setNull(idx, Types.VARCHAR);          // optional filter -> "? IS NULL" branch
            return;
        }
        String val = v.trim();
        if (name.endsWith("_date")) {
            try { ps.setObject(idx, LocalDate.parse(val)); }
            catch (DateTimeParseException e) { throw new BusinessException("Invalid date for '" + name + "': '" + val + "' (use yyyy-MM-dd)."); }
        } else if (name.equals("granularity")) {
            String g = val.toUpperCase();
            if (!GRANULARITIES.contains(g)) throw new BusinessException("Granularity must be DAILY, MONTHLY or QUARTERLY.");
            ps.setString(idx, g);
        } else if (name.equals("product") || name.equals("status")) {
            ps.setString(idx, val.toUpperCase());
        } else {
            ps.setString(idx, val);
        }
    }
}
