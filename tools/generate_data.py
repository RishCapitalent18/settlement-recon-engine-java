"""
Synthesizes two disagreeing feeds that mimic a broken post-trade settlement
process: a clean internal booking ledger and a damaged custodian statement.
The custodian side carries format drift (date styles, currency spellings,
side casing, thousands separators, 2 or 4 dp prices), roughly 30% missing
confirmations, duplicate confirms, unbooked trades, malformed rows, and a
minority of genuine economic breaks (price, quantity, side, settle date).

Output is written to data/ and committed, so the engine's results are fully
reproducible by anyone who compiles and runs the Java. Seed is fixed.
"""
import csv, random, os

SEED=42; N=6000
MISSING=0.30; PRICE_REAL=0.07; SUBBP=0.10; QTYB=0.04; SIDEB=0.02
DATEB=0.05; MALFORMED=0.02; DUP=0.03; UNBOOKED=0.05

rnd=random.Random(SEED)
CUSIPS=["037833100","594918104","68389X105","023135106","30303M102",
        "88160R101","46625H100","478160104","91324P102","92826C839"]
CCY=["USD","US Dollar","usd","$","USD "]
BUY=["BUY","buy","B","Buy "," bought"]; SELL=["SELL","sell","S","Sell "," sold"]
MON=["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"]
r2=lambda v: round(v,2)
def refmt(iso):
    y,m,d=(int(x) for x in iso.split("-")); k=rnd.randint(0,3)
    if k==0: return f"{m:02d}/{d:02d}/{y:04d}"
    if k==1: return f"{d:02d}-{MON[m-1]}-{y:04d}"
    if k==2: return f"{y:04d}{m:02d}{d:02d}"
    return iso
def thou(q):
    q=int(q); return f"{q:,}" if rnd.random()<0.5 else str(q)
def pad(p):
    return f"{p:.4f}" if rnd.random()<0.5 else f"{p:.2f}"

internal=[]
for i in range(N):
    internal.append([f"TRD{i:06d}","BUY" if rnd.random()<0.5 else "SELL",
        (1+rnd.randint(0,49))*100, r2(20+rnd.random()*480),"USD",
        f"2026-08-{3+rnd.randint(0,19):02d}", CUSIPS[rnd.randint(0,9)]])
os.makedirs("data",exist_ok=True)
with open("data/internal_ledger.csv","w",newline="") as f:
    w=csv.writer(f); w.writerow(["trade_id","side","quantity","price","currency","settle_date","cusip"])
    w.writerows(internal)

cust=[]
for tid,side,qty,price,ccy,settle,cusip in internal:
    if rnd.random()<MISSING: continue
    p=price;q=qty;s=side;st=settle;roll=rnd.random()
    if roll<PRICE_REAL: p=r2(p*(1+(0.04 if rnd.random()<0.5 else -0.05)))
    elif roll<PRICE_REAL+SUBBP: p=r2(p+(0.004 if rnd.random()<0.5 else -0.003))
    if rnd.random()<QTYB: q+=100
    if rnd.random()<SIDEB: s=("SELL" if s=="BUY" else "BUY")
    if rnd.random()<DATEB:
        dd=int(st.split("-")[2])+1; st=f"2026-08-{min(dd,28):02d}"
    dirv=(rnd.choice(BUY) if s=="BUY" else rnd.choice(SELL))
    qs=thou(q); ps=pad(p); ds=refmt(st)
    if rnd.random()<MALFORMED: qs="N/A"
    cust.append([tid,dirv,qs,ps,rnd.choice(CCY),ds,cusip])
    if rnd.random()<DUP: cust.append([tid,dirv,qs,ps,rnd.choice(CCY),ds,cusip])
for i in range(int(N*UNBOOKED)):
    cust.append([f"CUS{i:06d}",rnd.choice(BUY),thou((1+rnd.randint(0,49))*100),
                 pad(r2(20+rnd.random()*480)),rnd.choice(CCY),refmt("2026-08-10"),
                 CUSIPS[rnd.randint(0,9)]])
rnd.shuffle(cust)
with open("data/custodian_feed.csv","w",newline="") as f:
    w=csv.writer(f,delimiter=";"); w.writerow(["id","dir","qty","px","ccy","settlement","isin_or_cusip"])
    w.writerows(cust)
print(f"internal={len(internal)} custodian={len(cust)}")
