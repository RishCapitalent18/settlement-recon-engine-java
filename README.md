# Settlement Break Reconciliation and Repair Engine

A Java engine that takes a broken post-trade settlement process, where an
internal booking ledger and a custodian statement disagree, and rebuilds it:
it repairs the drifted custodian data, matches on economics rather than string
formatting, clears the noise automatically, and isolates the genuine breaks
with the money at risk behind them.

Built with Java 11 and a small Python reference implementation used to validate
the engine's numbers. Data is synthetic, generated with a fixed seed and
committed under `data/`, so every figure below reproduces on a clean checkout.

## The problem

Every day a firm's own books and its custodians send overlapping records of the
same trades, and they rarely agree. In `data/custodian_feed.csv` the same trades
arrive with drifted formats (dates as `08/14/2026`, `14-Aug-2026`, or
`20260814`; sides as `B`, `buy`, or `Buy`; quantities with thousands
separators; prices at 2 or 4 decimals; currency as `USD`, `$`, or `US Dollar`),
alongside missing confirmations, duplicate confirms, unbooked trades, malformed
rows, and a minority of genuine economic breaks. A naive exact-match process
clears almost nothing and dumps the rest on an operations desk.

## What it does

| Stage | Class | What happens |
| --- | --- | --- |
| Ingest | `App` | Reads the internal ledger and custodian feed from `data/`. |
| Baseline | `ReconEngine` | Runs a legacy exact-match pass for the before picture. |
| Repair | `Normalizer` | Canonicalises side, quantity, price, currency, and multi-format dates; rejects malformed rows. |
| Match | `ReconEngine` | Dedupes, matches on economics with a 1 basis point price tolerance, classifies breaks. |
| Score | `ReconEngine` | Weights residual breaks by notional and flags critical exposure. |
| Report | `App` | Writes `reports/reconciliation_report.txt` and `reports/run_summary.json`. |

## Headline results (committed dataset, seed 42)

- Lifted the straight-through match rate from **29.30%** under the legacy exact-match process to **57.23%** after the rebuild, across a 6,000-trade book.
- Cut the manual exception queue by **32.44%** by auto-clearing format and rounding differences that were never real breaks.
- Isolated **$479.96M** of genuine breaks for review, of which **$156.81M** is critical (side flips and quantity breaks), instead of leaving them buried in noise.
- Rejected 85 malformed rows at load and dropped 130 duplicate confirmations, so a single bad file cannot corrupt the day's numbers.

Residual breaks routed for review: 289 price, 217 settle-date, 167 quantity, 81 side, plus 1,852 missing confirmations and 300 unbooked custodian rows.

## Run it

```
./run.sh                       # javac + run over data/
# or manually:
javac -d out src/main/java/com/recon/*.java
java -cp out com.recon.App data
```

Regenerate the synthetic feeds (optional, requires Python):

```
python3 tools/generate_data.py     # rewrites data/*.csv
python3 tools/recon_oracle.py      # reference run used to validate the engine
```

## Layout

```
settlement-recon-engine-java/
|-- run.sh
|-- src/main/java/com/recon/
|   |-- App.java            # entry point, IO, reporting
|   |-- Normalizer.java     # format repair
|   |-- ReconEngine.java    # baseline, matching, break classification, scoring
|   `-- Trade.java          # record model
|-- tools/
|   |-- generate_data.py    # synthetic messy feed generator
|   `-- recon_oracle.py     # Python reference used to validate the engine
|-- data/                   # committed internal_ledger.csv and custodian_feed.csv
`-- reports/                # reconciliation_report.txt, run_summary.json
```

Data is fully synthetic and generated locally. No proprietary or firm data is used.

## Sample run

~~~
================ Settlement Reconciliation Report ================
Internal ledger trades       : 6000
Custodian rows ingested      : 4663
Rows repaired and parsed     : 4578
Malformed rows rejected      : 85
Duplicate confirms dropped   : 130

----- Before: legacy exact-match process -----
Straight-through matches     : 1758
Legacy straight-through rate : 29.30%

----- After: rebuilt repair-and-match engine -----
Straight-through matches     : 3434
Engine straight-through rate : 57.23%
Manual review queue cut by   : 32.44%

----- Residual genuine breaks (routed for review) -----
Missing confirmations        : 1852
Unbooked (custodian only)    : 300
PRICE_BREAK                  : 289
QUANTITY_BREAK               : 167
SETTLEDATE_BREAK             : 217
SIDE_BREAK                   : 81
Notional at risk             : $479,962,457.00
Critical notional            : $156,807,351.00
=================================================================
~~~
