package com.recon;

import java.util.*;

/**
 * Runs the legacy exact-match baseline and the rebuilt repair-and-match engine
 * over the same feeds, classifies genuine breaks, and isolates notional at risk.
 * Behaviour is identical to tools/recon_oracle.py, so the committed dataset
 * yields the same figures shown in the README.
 */
public class ReconEngine {

    public static final double PRICE_TOL = 0.0001; // 1 basis point, relative

    public static class Result {
        public int internalTrades, custodianRows, parsed, rejected, duplicates;
        public int legacyMatches, engineMatches, broken, missing, unbooked;
        public Map<String,Integer> breakTypes = new TreeMap<>();
        public double notionalAtRisk, criticalNotional;
        public double legacyStp, engineStp, queueCut;
    }

    private final Normalizer norm = new Normalizer();

    public Result run(Map<String,Trade> internal, List<String[]> custodian) {
        Result r = new Result();
        r.internalTrades = internal.size();
        r.custodianRows = custodian.size();

        // Legacy exact-match baseline: case and numeric tolerant, ISO and US
        // dates only, zero price tolerance.
        for (String[] rc : custodian) {
            String ld = norm.date(rc[5], Normalizer.LEGACY_FORMATS);
            String ls = norm.side(rc[1]);
            double lq, lp;
            try { lq = norm.number(rc[2]); lp = norm.number(rc[3]); }
            catch (NumberFormatException e) { continue; }
            Trade in = internal.get(rc[0]);
            if (in != null && ld != null && ls != null
                    && ls.equals(in.side) && Math.abs(lq - in.quantity) < 0.5
                    && lp == in.price && ld.equals(in.settleDate)) {
                r.legacyMatches++;
            }
        }

        // Rebuilt engine: full format repair, dedup, reject malformed.
        Set<String> seen = new HashSet<>();
        Map<String,Trade> engine = new HashMap<>();
        for (String[] rc : custodian) {
            String es = norm.side(rc[1]);
            String ed = norm.date(rc[5], Normalizer.ENGINE_FORMATS);
            double eq, ep;
            try { eq = norm.number(rc[2]); ep = norm.number(rc[3]); }
            catch (NumberFormatException e) { r.rejected++; continue; }
            if (es == null || ed == null) { r.rejected++; continue; }
            r.parsed++;
            String key = rc[0] + "|" + ed + "|" + (long) eq;
            if (seen.contains(key)) { r.duplicates++; continue; }
            seen.add(key);
            Trade t = new Trade();
            t.tradeId = rc[0]; t.side = es; t.quantity = eq; t.price = ep; t.settleDate = ed;
            engine.put(rc[0], t);
        }

        // Compare economics for ids on both sides.
        for (Map.Entry<String,Trade> e : internal.entrySet()) {
            Trade in = e.getValue();
            Trade cu = engine.get(in.tradeId);
            if (cu == null) { r.missing++; continue; }
            List<String> bl = new ArrayList<>();
            if (!in.side.equals(cu.side)) bl.add("SIDE_BREAK");
            if (Math.abs(in.quantity - cu.quantity) > 0.5) bl.add("QUANTITY_BREAK");
            if (relDiff(in.price, cu.price) > PRICE_TOL) bl.add("PRICE_BREAK");
            if (!in.settleDate.equals(cu.settleDate)) bl.add("SETTLEDATE_BREAK");
            if (bl.isEmpty()) {
                r.engineMatches++;
            } else {
                r.broken++;
                for (String b : bl) r.breakTypes.merge(b, 1, Integer::sum);
                double risk = in.notional();
                r.notionalAtRisk += risk;
                if (bl.contains("SIDE_BREAK") || bl.contains("QUANTITY_BREAK"))
                    r.criticalNotional += risk;
            }
        }
        for (String id : engine.keySet())
            if (!internal.containsKey(id)) r.unbooked++;

        int manualBefore = r.internalTrades - r.legacyMatches;
        int manualAfter = r.broken + r.missing + r.unbooked;
        r.legacyStp = pct(r.legacyMatches, r.internalTrades);
        r.engineStp = pct(r.engineMatches, r.internalTrades);
        r.queueCut = manualBefore == 0 ? 0 : 100.0 * (manualBefore - manualAfter) / manualBefore;
        r.notionalAtRisk = round2(r.notionalAtRisk);
        r.criticalNotional = round2(r.criticalNotional);
        return r;
    }

    private double relDiff(double a, double b) {
        double d = Math.max(Math.abs(a), Math.abs(b));
        return d == 0 ? 0 : Math.abs(a - b) / d;
    }
    private double pct(int a, int b) { return b == 0 ? 0 : 100.0 * a / b; }
    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
