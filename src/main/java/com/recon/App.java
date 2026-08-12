package com.recon;

import java.io.*;
import java.util.*;

/**
 * Settlement Break Reconciliation and Repair Engine.
 *
 * Reads a clean internal ledger and a damaged custodian feed, rebuilds the
 * broken custodian data, matches on economics, and reports how much of the
 * manual exception load the rebuild removes plus the notional it isolates.
 */
public class App {

    public static void main(String[] args) throws IOException {
        String dir = args.length > 0 ? args[0] : "data";
        Map<String,Trade> internal = readInternal(dir + "/internal_ledger.csv");
        List<String[]> custodian = readCustodian(dir + "/custodian_feed.csv");

        ReconEngine.Result r = new ReconEngine().run(internal, custodian);

        StringBuilder sb = new StringBuilder();
        line(sb, "================ Settlement Reconciliation Report ================");
        line(sb, String.format("Internal ledger trades       : %d", r.internalTrades));
        line(sb, String.format("Custodian rows ingested      : %d", r.custodianRows));
        line(sb, String.format("Rows repaired and parsed     : %d", r.parsed));
        line(sb, String.format("Malformed rows rejected      : %d", r.rejected));
        line(sb, String.format("Duplicate confirms dropped   : %d", r.duplicates));
        line(sb, "");
        line(sb, "----- Before: legacy exact-match process -----");
        line(sb, String.format("Straight-through matches     : %d", r.legacyMatches));
        line(sb, String.format("Legacy straight-through rate : %.2f%%", r.legacyStp));
        line(sb, "");
        line(sb, "----- After: rebuilt repair-and-match engine -----");
        line(sb, String.format("Straight-through matches     : %d", r.engineMatches));
        line(sb, String.format("Engine straight-through rate : %.2f%%", r.engineStp));
        line(sb, String.format("Manual review queue cut by   : %.2f%%", r.queueCut));
        line(sb, "");
        line(sb, "----- Residual genuine breaks (routed for review) -----");
        line(sb, String.format("Missing confirmations        : %d", r.missing));
        line(sb, String.format("Unbooked (custodian only)    : %d", r.unbooked));
        for (Map.Entry<String,Integer> e : r.breakTypes.entrySet())
            line(sb, String.format("%-29s: %d", e.getKey(), e.getValue()));
        line(sb, String.format("Notional at risk             : $%,.2f", r.notionalAtRisk));
        line(sb, String.format("Critical notional            : $%,.2f", r.criticalNotional));
        line(sb, "=================================================================");

        String report = sb.toString();
        System.out.print(report);
        new File("reports").mkdirs();
        write("reports/reconciliation_report.txt", report);
        write("reports/run_summary.json", json(r));
    }

    private static Map<String,Trade> readInternal(String path) throws IOException {
        Map<String,Trade> m = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            br.readLine(); // header
            String ln;
            while ((ln = br.readLine()) != null) {
                if (ln.isEmpty()) continue;
                String[] c = ln.split(",", -1);
                Trade t = new Trade();
                t.tradeId = c[0].trim();
                t.side = c[1].trim().toUpperCase();
                t.quantity = Double.parseDouble(c[2].trim());
                t.price = Double.parseDouble(c[3].trim());
                t.settleDate = c[5].trim();
                m.put(t.tradeId, t);
            }
        }
        return m;
    }

    private static List<String[]> readCustodian(String path) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            br.readLine(); // header
            String ln;
            while ((ln = br.readLine()) != null) {
                if (ln.isEmpty()) continue;
                rows.add(ln.split(";", -1));
            }
        }
        return rows;
    }

    private static String json(ReconEngine.Result r) {
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append("  \"internal_trades\": ").append(r.internalTrades).append(",\n");
        b.append("  \"custodian_rows\": ").append(r.custodianRows).append(",\n");
        b.append("  \"rejected\": ").append(r.rejected).append(",\n");
        b.append("  \"duplicates\": ").append(r.duplicates).append(",\n");
        b.append("  \"legacy_stp_pct\": ").append(String.format("%.2f", r.legacyStp)).append(",\n");
        b.append("  \"engine_stp_pct\": ").append(String.format("%.2f", r.engineStp)).append(",\n");
        b.append("  \"manual_queue_cut_pct\": ").append(String.format("%.2f", r.queueCut)).append(",\n");
        b.append("  \"missing_confirmations\": ").append(r.missing).append(",\n");
        b.append("  \"unbooked\": ").append(r.unbooked).append(",\n");
        b.append("  \"notional_at_risk\": ").append(r.notionalAtRisk).append(",\n");
        b.append("  \"critical_notional\": ").append(r.criticalNotional).append("\n}");
        return b.toString();
    }

    private static void line(StringBuilder sb, String s) { sb.append(s).append("\n"); }
    private static void write(String path, String s) throws IOException {
        try (PrintWriter w = new PrintWriter(new FileWriter(path))) { w.print(s); }
    }
}
