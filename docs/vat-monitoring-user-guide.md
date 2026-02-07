# VAT Monitoring Feature - User Guide

## How to Access

1. Log in to the pklnd application
2. Navigate to the **Dashboard** (from main menu)
3. Click the **"Momsövervakning"** card (blue border with graph icon)
4. View the VAT monitoring page at `/dashboard/statistics/vat-monitoring`

## What You'll See

### Summary Section
At the top of the page, you'll see two key metrics:

**Varor spårade (Items Tracked)**
- Shows how many unique items are being monitored
- Counts items that appear in receipts both before and after April 1, 2026

**Misstänkta prisökningar (Suspicious Price Increases)**
- Shows items with prices higher than expected
- Displayed in RED if any are found
- GREEN if all prices are reasonable

### Comparison Table

The main table shows detailed information for each item:

**Column 1: Vara (Item)**
- Item name (e.g., "Mjölk Arla 1.5% 1L")
- EAN barcode number
- Purchase frequency (e.g., "5 köp i 2 butiker")

**Column 2: Butik (Store)**
- Store name where purchased
- Shows both stores if different between purchases
- Dates of purchases (before → after)

**Column 3: Pris före (Price Before)**
- Last purchase price before April 1, 2026
- In Swedish kronor (kr)

**Column 4: Förväntat pris (Expected Price)**
- Calculated expected price after VAT reduction
- Shown in GREEN
- Formula: (price_before / 1.12) × 1.06
- Represents what price SHOULD be with lower VAT

**Column 5: Pris efter (Price After)**
- First purchase price after April 1, 2026
- Shows percentage change from before

**Column 6: Ändring (Change)**
- Difference between actual and expected price
- Positive = price higher than expected
- Negative = price lower than expected (good!)

**Column 7: Status**
- ✓ OK (green badge) - Price is reasonable
- ⚠ Misstänkt (red badge) - Price increase is suspicious

### Row Highlighting

**White Background**
- Normal items where price change is acceptable
- Price is within 2% of expected

**Red Background**
- Suspicious items
- Price is MORE than 2% higher than expected
- Store may not be passing VAT savings to consumers

## Understanding the Calculations

### Expected Price Formula
When VAT drops from 12% to 6%, prices should decrease by approximately 5.4%:

```
Expected Price = (Old Price / 1.12) × 1.06
```

**Example:**
- Old price: 100.00 kr (includes 12% VAT)
- Remove 12% VAT: 100 / 1.12 = 89.29 kr (base price)
- Add 6% VAT: 89.29 × 1.06 = 94.64 kr
- Expected price: 94.64 kr
- Expected savings: 5.36 kr (5.4%)

### Suspicious Detection
An item is flagged as suspicious if:
```
(Actual Price - Expected Price) / Expected Price > 0.02
```

This means the actual price is more than 2% higher than expected.

**Example of Suspicious Increase:**
- Old price: 100.00 kr
- Expected: 94.64 kr
- Actual: 97.50 kr
- Deviation: 97.50 - 94.64 = 2.86 kr
- Deviation %: 2.86 / 94.64 = 3.02% > 2% → **SUSPICIOUS**

**Example of Normal Change:**
- Old price: 100.00 kr
- Expected: 94.64 kr
- Actual: 95.00 kr
- Deviation: 95.00 - 94.64 = 0.36 kr
- Deviation %: 0.36 / 94.64 = 0.38% < 2% → **OK**

## Requirements for Data

To see meaningful comparisons, you need:

1. **Receipts with EAN codes**
   - Most receipts from ICA and other major chains include these
   - EAN is the barcode number

2. **Receipts with 12% VAT**
   - The system only tracks food items (12% VAT)
   - Non-food items (25% VAT) are excluded

3. **Receipts before April 1, 2026**
   - At least one purchase of an item before the VAT change

4. **Receipts after April 1, 2026**
   - At least one purchase of the same item after the VAT change

5. **Same items (EAN codes match)**
   - The system uses EAN to identify identical products

## Empty State

If you see the message:
> "Inga prisändringar att visa ännu..."

This means:
- You don't have receipts both before AND after April 1, 2026
- Or items don't have EAN codes
- Or no items match between before/after periods

**Solution:** Keep uploading receipts and the system will automatically start tracking when you have data from both periods.

## Tips for Best Results

1. **Upload receipts regularly**
   - More receipts = better price tracking
   - System needs data from both before and after April 1, 2026

2. **Shop at same stores**
   - Easier to spot price changes at specific stores
   - Can compare across stores too

3. **Buy same products**
   - Items with same EAN codes are tracked automatically
   - House brands and generic items may vary

4. **Check frequently**
   - Prices may change over time
   - Some stores may adjust prices gradually

5. **Report concerns**
   - If you find suspicious increases, consider:
     - Shopping at different stores
     - Reporting to consumer protection agencies
     - Sharing findings with other consumers

## Multi-Store Tracking

When an item appears in multiple stores:
- The table shows purchase frequency: "5 köp i 2 butiker"
- Latest price BEFORE April 1 from any store
- Earliest price AFTER April 1 from any store
- Store names shown in the Butik column

This helps you:
- See which stores have the best prices
- Compare same item across stores
- Identify stores with suspicious pricing

## Admin vs Regular Users

**Regular Users:**
- See VAT monitoring for their own receipts only
- Personal price tracking

**Administrators:**
- See VAT monitoring for ALL users' receipts
- Can spot system-wide pricing trends
- Useful for consumer protection organizations

## Privacy Note

- Your receipt data is private by default
- Only you (and admins) can see your purchases
- Price comparisons are calculated automatically
- No manual data entry required

## Technical Details

**Data Sources:**
- Firestore database with parsed receipts
- Automatic extraction from PDF/image uploads
- VAT information from receipt totals

**Update Frequency:**
- Calculations run when you visit the page
- Always shows latest data from database
- No manual refresh needed

**Browser Compatibility:**
- Works on desktop and mobile
- Responsive design adapts to screen size
- Tested with modern browsers

## Support

If you encounter issues:

1. **No data showing:**
   - Ensure Firestore is configured
   - Check that receipts have been parsed successfully
   - Verify receipts have EAN codes

2. **Incorrect calculations:**
   - Report to system administrator
   - Include specific item examples

3. **Missing items:**
   - Check that item has EAN code
   - Verify purchases exist both before/after April 1, 2026

## Future Enhancements

Planned features (not yet implemented):
- Email alerts for suspicious price increases
- Historical price trends and charts
- Export to CSV/Excel
- Store reputation scores
- Category-specific analysis
- Price prediction based on trends
