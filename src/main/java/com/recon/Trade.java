package com.recon;

/** One economic trade record from the internal ledger or the custodian feed. */
public class Trade {
    public String tradeId;
    public String side;        // BUY or SELL after normalization
    public double quantity;
    public double price;
    public String settleDate;  // ISO yyyy-MM-dd after normalization
    public boolean parsed = true;
    public double notional() { return quantity * price; }
}
