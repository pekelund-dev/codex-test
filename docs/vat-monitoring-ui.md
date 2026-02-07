# VAT Monitoring UI Design

## Page Layout

### Header Section
```
┌─────────────────────────────────────────────────────────────┐
│ Momsövervakning                                             │
│                                                              │
│ Sverige sänker momsen på livsmedel från 12% till 6% den     │
│ 2026-04-01. Den här sidan visar hur priser på varor ändras  │
│ och flaggar om butiker höjer priserna mer än förväntat.     │
└─────────────────────────────────────────────────────────────┘
```

### Summary Cards
```
┌──────────────────┐  ┌──────────────────────────────┐
│ Varor spårade    │  │ Misstänkta prisökningar      │
│                  │  │                              │
│      42          │  │           3                  │
│                  │  │        (RED if > 0)          │
└──────────────────┘  └──────────────────────────────┘
```

### Comparison Table

The main table displays items with columns:
- **Vara** - Item name and EAN code
- **Butik** - Store name(s) and dates
- **Pris före** - Price before VAT change
- **Förväntat pris** - Expected price after VAT reduction (in green)
- **Pris efter** - Actual price after VAT change
- **Ändring** - Difference between actual and expected
- **Status** - Badge: ✓ OK (green) or ⚠ Misstänkt (red)

#### Example Row (Normal)
```
┌─────────────────────────────────────────────────────────────────────────┐
│ Mjölk Arla 1.5% 1L                                                      │
│ EAN: 7310867501583                                                      │
│ 2 köp i 1 butiker                                                       │
├──────────┬──────────┬──────────┬──────────┬──────────┬─────────────────┤
│ ICA      │ 15.90 kr │ 15.05 kr │ 14.90 kr │ -0.15 kr │ ✓ OK           │
│ Kvantum  │          │ (-5.3%)  │ (-6.3%)  │          │ (green badge)  │
│ 2026-    │          │          │          │          │                │
│ 03-15→   │          │          │          │          │                │
│ 04-05    │          │          │          │          │                │
└──────────┴──────────┴──────────┴──────────┴──────────┴─────────────────┘
```

#### Example Row (Suspicious)
```
┌─────────────────────────────────────────────────────────────────────────┐
│ Bröd Pågen Levain                                     (RED BACKGROUND)  │
│ EAN: 7310350113194                                                      │
│ 5 köp i 2 butiker                                                       │
├──────────┬──────────┬──────────┬──────────┬──────────┬─────────────────┤
│ ICA →    │ 35.90 kr │ 33.99 kr │ 36.50 kr │ +2.51 kr │ ⚠ Misstänkt    │
│ ICA      │          │ (-5.3%)  │ (+1.7%)  │ (RED)    │ (red badge)    │
│ Super-   │          │          │          │          │                │
│ market   │          │          │          │          │                │
│ 2026-    │          │          │          │          │                │
│ 03-20→   │          │          │          │          │                │
│ 04-10    │          │          │          │          │                │
└──────────┴──────────┴──────────┴──────────┴──────────┴─────────────────┘
```

### Footer Explanation Box
```
┌─────────────────────────────────────────────────────────────┐
│ Förklaring:                                                 │
│                                                              │
│ • Pris före: Senaste inköpspriset före momssänkningen       │
│ • Förväntat pris: Beräknat pris efter momssänkning:         │
│   (pris före / 1.12) × 1.06                                 │
│ • Pris efter: Första inköpspriset efter momssänkningen      │
│ • Ändring: Skillnad mellan faktiskt pris och förväntat     │
│ • Status: Flaggas som "Misstänkt" om priset ökat mer än    │
│   2% över förväntat                                         │
└─────────────────────────────────────────────────────────────┘
```

## Color Scheme

- **Normal items**: White background
- **Suspicious items**: Light red background (table-danger class)
- **Expected price**: Green text (text-success)
- **OK status**: Green badge with checkmark icon
- **Suspicious status**: Red badge with warning icon
- **Card borders**: Info blue for the VAT monitoring card on dashboard

## Key UI Elements

1. **Responsive design** - Uses Bootstrap 5 grid system
2. **Icon usage** - Bootstrap Icons for visual indicators
3. **Badges** - Color-coded status indicators
4. **Tables** - Responsive table with hover effects
5. **Cards** - Shadow effects for depth
6. **Swedish language** - All UI text in Swedish

## Navigation

From the dashboard:
1. User sees a new card: "Momsövervakning" with graph icon
2. Clicking opens `/dashboard/statistics/vat-monitoring`
3. Back button returns to dashboard

## Empty State

When no data is available:
```
┌─────────────────────────────────────────────────────────────┐
│ ℹ Inga prisändringar att visa ännu. Vi behöver kvitton    │
│   både före och efter momssänkningen (2026-04-01) för att  │
│   jämföra priser.                                           │
└─────────────────────────────────────────────────────────────┘
```

## Accessibility

- Semantic HTML structure
- Color is not the only indicator (icons + text)
- Descriptive button text
- Proper heading hierarchy (h1, h2, etc.)
- Table headers for screen readers
