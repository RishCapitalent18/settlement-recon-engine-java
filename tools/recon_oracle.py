"""
Reference reconciliation identical in behaviour to the Java engine. Reads the
committed CSVs, runs the legacy exact-match baseline and the rebuilt engine,
and writes the report the Java App reproduces when compiled and run.
"""
import csv, json
from datetime import datetime

def side_n(x):
    x=x.strip().upper()
    return "BUY" if x.startswith("B") else ("SELL" if x.startswith("S") else None)
def num(x): return float(x.strip().replace(",",""))
def parse_date(s, fmts):
    for f in fmts:
        try: return datetime.strptime(s.strip(),f).strftime("%Y-%m-%d")
        except ValueError: continue
    return None
LEGACY_FMTS=("%Y-%m-%d","%m/%d/%Y")
ENGINE_FMTS=("%Y-%m-%d","%m/%d/%Y","%d-%b-%Y","%Y%m%d")

internal={}
with open("data/internal_ledger.csv") as f:
    r=csv.reader(f); next(r)
    for c in r:
        internal[c[0]]={"side":c[1].upper(),"qty":float(c[2]),"price":float(c[3]),"settle":c[5]}
N=len(internal)

cust=[]
with open("data/custodian_feed.csv") as f:
    r=csv.reader(f,delimiter=";"); next(r)
    for rc in r: cust.append(rc)
cust_rows=len(cust)

# legacy exact-match baseline
legacy=0
for rc in cust:
    tid,dirv,qs,ps,ccy,ds=rc[0],rc[1],rc[2],rc[3],rc[4],rc[5]
    ld=parse_date(ds,LEGACY_FMTS); ls=side_n(dirv)
    try: lq=num(qs); lp=num(ps)
    except ValueError: continue
    inn=internal.get(tid)
    if inn and ld and ls==inn["side"] and abs(lq-inn["qty"])<0.5 and lp==inn["price"] and ld==inn["settle"]:
        legacy+=1

# rebuilt engine
parsed=rejected=dupes=0; seen=set(); engmap={}
for rc in cust:
    tid,dirv,qs,ps,ds=rc[0],rc[1],rc[2],rc[3],rc[5]
    es=side_n(dirv); ed=parse_date(ds,ENGINE_FMTS)
    try: eq=num(qs); ep=num(ps)
    except ValueError: rejected+=1; continue
    if es is None or ed is None: rejected+=1; continue
    parsed+=1
    key=f"{tid}|{ed}|{int(eq)}"
    if key in seen: dupes+=1; continue
    seen.add(key); engmap[tid]={"side":es,"qty":eq,"price":ep,"settle":ed}

matched=missing=unbooked=0; breaks={}; at_risk=crit=0.0
for tid,inn in internal.items():
    cu=engmap.get(tid)
    if cu is None: missing+=1; continue
    bl=[]
    if inn["side"]!=cu["side"]: bl.append("SIDE_BREAK")
    if abs(inn["qty"]-cu["qty"])>0.5: bl.append("QUANTITY_BREAK")
    d=max(abs(inn["price"]),abs(cu["price"]))
    if d and abs(inn["price"]-cu["price"])/d>0.0001: bl.append("PRICE_BREAK")
    if inn["settle"]!=cu["settle"]: bl.append("SETTLEDATE_BREAK")
    if not bl: matched+=1
    else:
        for b in bl: breaks[b]=breaks.get(b,0)+1
        risk=inn["qty"]*inn["price"]; at_risk+=risk
        if "SIDE_BREAK" in bl or "QUANTITY_BREAK" in bl: crit+=risk
for tid in engmap:
    if tid not in internal: unbooked+=1

broken=sum(1 for tid,inn in internal.items() if tid in engmap and engmap[tid] and (
    inn["side"]!=engmap[tid]["side"] or abs(inn["qty"]-engmap[tid]["qty"])>0.5 or
    (max(abs(inn["price"]),abs(engmap[tid]["price"])) and abs(inn["price"]-engmap[tid]["price"])/max(abs(inn["price"]),abs(engmap[tid]["price"]))>0.0001) or
    inn["settle"]!=engmap[tid]["settle"]))
legacy_stp=100*legacy/N; engine_stp=100*matched/N
manual_before=N-legacy; manual_after=broken+missing+unbooked
queue_cut=100*(manual_before-manual_after)/manual_before

L=[]
def p(s): L.append(s)
p("================ Settlement Reconciliation Report ================")
p(f"Internal ledger trades       : {N}")
p(f"Custodian rows ingested      : {cust_rows}")
p(f"Rows repaired and parsed     : {parsed}")
p(f"Malformed rows rejected      : {rejected}")
p(f"Duplicate confirms dropped   : {dupes}")
p("")
p("----- Before: legacy exact-match process -----")
p(f"Straight-through matches     : {legacy}")
p(f"Legacy straight-through rate : {legacy_stp:.2f}%")
p("")
p("----- After: rebuilt repair-and-match engine -----")
p(f"Straight-through matches     : {matched}")
p(f"Engine straight-through rate : {engine_stp:.2f}%")
p(f"Manual review queue cut by   : {queue_cut:.2f}%")
p("")
p("----- Residual genuine breaks (routed for review) -----")
p(f"Missing confirmations        : {missing}")
p(f"Unbooked (custodian only)    : {unbooked}")
for k in sorted(breaks): p(f"{k:29}: {breaks[k]}")
p(f"Notional at risk             : ${at_risk:,.2f}")
p(f"Critical notional            : ${crit:,.2f}")
p("=================================================================")
report="\n".join(L)
print(report)
open("reports/reconciliation_report.txt","w").write(report+"\n")
json.dump({"internal_trades":N,"custodian_rows":cust_rows,"rejected":rejected,
    "duplicates":dupes,"legacy_stp_pct":round(legacy_stp,2),
    "engine_stp_pct":round(engine_stp,2),"manual_queue_cut_pct":round(queue_cut,2),
    "missing_confirmations":missing,"unbooked":unbooked,"break_types":breaks,
    "notional_at_risk":round(at_risk,2),"critical_notional":round(crit,2)},
    open("reports/run_summary.json","w"), indent=2)
