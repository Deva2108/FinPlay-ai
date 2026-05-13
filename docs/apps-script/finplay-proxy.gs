/**
 * FinPlay Google Sheets Proxy — Apps Script
 *
 * Deploy:
 *   1. Open https://script.google.com/, create a new project, name it "FinPlay-Proxy".
 *   2. Bind it to a fresh Google Sheet (Resources → Cloud project → ... or just Tools → Script editor on a sheet).
 *   3. Paste this whole file.
 *   4. Deploy → New deployment → Web app
 *      - Execute as: me
 *      - Who has access: Anyone (or "Anyone with link" if your account allows)
 *   5. Copy the Web App URL — looks like https://script.google.com/macros/s/AKfy.../exec
 *   6. Set GOOGLE_SCRIPT_URL=<that URL> in your FinPlay backend env.
 *
 * Endpoints (all GET):
 *   ?type=chart      &symbol=AAPL          &range=1Y       → historical OHLC
 *   ?type=financials &symbol=AAPL                          → P/E, marketcap, EPS, revenue history
 *   ?type=index      &code=^NSEI           &top=10         → index value + top constituents
 *
 * Symbol formats this script accepts:
 *   AAPL, MSFT                 → US (passes through)
 *   RELIANCE.NS, TCS.NS        → India NSE  (rewritten to NSE:RELIANCE for GOOGLEFINANCE)
 *   RELIANCE.BO                → India BSE  (rewritten to BOM:RELIANCE)
 *   NSE:RELIANCE, BOM:RELIANCE → already-prefixed forms accepted as-is
 */

// ---------- HTTP entrypoint ---------------------------------------------------

function doGet(e) {
  try {
    var type = (e.parameter.type || 'chart').toLowerCase();
    var payload;

    if (type === 'chart') {
      payload = handleChart(e.parameter);
    } else if (type === 'financials') {
      payload = handleFinancials(e.parameter);
    } else if (type === 'index') {
      payload = handleIndex(e.parameter);
    } else {
      payload = { error: 'unknown type: ' + type };
    }
    return jsonOut(payload);
  } catch (err) {
    return jsonOut({ error: String(err && err.message || err) });
  }
}

function jsonOut(obj) {
  return ContentService
      .createTextOutput(JSON.stringify(obj))
      .setMimeType(ContentService.MimeType.JSON);
}

// ---------- Symbol normalization ---------------------------------------------

function toGoogle(symbol) {
  if (!symbol) return null;
  var s = symbol.trim().toUpperCase();
  if (s.indexOf(':') > 0) return s;          // already prefixed
  if (s.endsWith('.NS')) return 'NSE:' + s.slice(0, -3);
  if (s.endsWith('.BO')) return 'BOM:' + s.slice(0, -3);
  return s;                                   // US ticker
}

// ---------- Worker-cell helpers ----------------------------------------------
//
// We use the *active sheet* of the spreadsheet bound to this script.
// If you'd rather isolate per request (avoid stomping while a sheet is open),
// create dedicated tabs named "chart", "financials", "index" and use those.
//
// We also clear cells on entry, not on exit, so a crashed run doesn't leak
// stale data into the next request.

function activeSheet() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  if (!ss) throw new Error('Apps Script is not bound to a spreadsheet. Open this script via Tools → Script editor on a Sheet.');
  return ss.getActiveSheet();
}

function setFormulaAndWait(cell, formula, waitMs) {
  var sheet = activeSheet();
  sheet.getRange('A1:Z2000').clearContent();   // wipe any prior run
  sheet.getRange(cell).setFormula(formula);
  SpreadsheetApp.flush();
  Utilities.sleep(waitMs);
}

// ---------- Handlers ----------------------------------------------------------

/**
 * Chart: writes =GOOGLEFINANCE("AAPL","all", T-N, T, "DAILY") at A1, reads expansion.
 * Returns: [{timestamp, open, high, low, price (close), volume}].
 */
function handleChart(p) {
  var symbol = p.symbol;
  var range  = (p.range || '1Y').toUpperCase();
  var google = toGoogle(symbol);
  if (!google) return { error: 'symbol required' };

  var days, interval = 'DAILY';
  switch (range) {
    case '1W': days = 7;    interval = 'DAILY'; break;
    case '1M': days = 31;   interval = 'DAILY'; break;
    case '3M': days = 95;   interval = 'DAILY'; break;
    case '6M': days = 190;  interval = 'DAILY'; break;
    case '1Y': days = 365;  interval = 'DAILY'; break;
    case '5Y': days = 1825; interval = 'WEEKLY'; break;
    case 'MAX':days = 5475; interval = 'WEEKLY'; break;
    default:   days = 365;  interval = 'DAILY';
  }

  var formula =
      '=GOOGLEFINANCE("' + google + '","all",TODAY()-' + days + ',TODAY(),"' + interval + '")';
  setFormulaAndWait('A1', formula, 2500);

  var sheet = activeSheet();
  var values = sheet.getRange('A1:F' + (days + 5)).getValues();
  // Row 0 = headers: Date | Open | High | Low | Close | Volume
  // Filter out empty rows + #N/A rows.
  var out = [];
  for (var i = 1; i < values.length; i++) {
    var row = values[i];
    var d = row[0];
    if (!d || !(d instanceof Date)) continue;
    var close = row[4];
    if (typeof close !== 'number' || !isFinite(close)) continue;
    out.push({
      timestamp: d.toISOString().slice(0, 10),  // YYYY-MM-DD
      open:   numOrNull(row[1]),
      high:   numOrNull(row[2]),
      low:    numOrNull(row[3]),
      price:  close,                              // alias: "value"
      value:  close,                              //         frontend uses either
      volume: numOrNull(row[5])
    });
  }
  return { symbol: symbol, range: range, source: 'google-sheets', count: out.length, data: out };
}

/**
 * Financials: writes a 4×N grid of formulas, reads back, packages as ratios + history.
 *
 * GOOGLEFINANCE attributes used:
 *   "name", "marketcap", "pe", "eps", "yieldpct" (dividend yield %), "currency"
 *
 * Revenue + net income history is harder — GOOGLEFINANCE doesn't expose them
 * directly in the live formula. We fall back to a simple manually-curated table
 * if you want it; otherwise the financials array is empty and the frontend
 * gracefully hides that section.
 */
function handleFinancials(p) {
  var symbol = p.symbol;
  var google = toGoogle(symbol);
  if (!google) return { error: 'symbol required' };

  // Build a one-row formula matrix: [name, marketcap, pe, eps, yieldpct, currency]
  var sheet = activeSheet();
  sheet.getRange('A1:Z2000').clearContent();
  var attrs = ['name','marketcap','pe','eps','yieldpct','currency'];
  for (var i = 0; i < attrs.length; i++) {
    sheet.getRange(1, i + 1)
         .setFormula('=GOOGLEFINANCE("' + google + '","' + attrs[i] + '")');
  }
  SpreadsheetApp.flush();
  Utilities.sleep(2000);

  var row = sheet.getRange('A1:F1').getValues()[0];

  return {
    symbol: symbol,
    name: stringOrNull(row[0]),
    ratios: {
      marketCap: numOrNull(row[1]),
      pe:        numOrNull(row[2]),
      eps:       numOrNull(row[3]),
      dividend:  numOrNull(row[4]),
      currency:  stringOrNull(row[5])
    },
    // Revenue / net income history is not available via GOOGLEFINANCE live
    // formulas. Leave empty; the backend can backfill from a static table or
    // SECTOR_DESCRIPTIONS in CompanyDetailsService.java.
    financials: []
  };
}

/**
 * Index: returns the index value + a list of top N constituents you've
 * pre-listed in the script (GOOGLEFINANCE doesn't expose "members of an index"
 * either). Edit INDEX_CONSTITUENTS below to taste.
 */
var INDEX_CONSTITUENTS = {
  '^NSEI': {
    name: 'Nifty 50',
    members: [
      'NSE:RELIANCE','NSE:TCS','NSE:HDFCBANK','NSE:INFY','NSE:ICICIBANK',
      'NSE:HINDUNILVR','NSE:SBIN','NSE:BAJFINANCE','NSE:BHARTIARTL','NSE:ITC'
    ]
  },
  '^GSPC': {
    name: 'S&P 500',
    members: ['AAPL','MSFT','GOOGL','AMZN','NVDA','META','TSLA','BRK-B','JPM','V']
  },
  '^IXIC': {
    name: 'Nasdaq Composite',
    members: ['AAPL','MSFT','NVDA','AMZN','META','GOOGL','TSLA','AVGO','COST','NFLX']
  }
};

function handleIndex(p) {
  var code = p.code;
  var top  = parseInt(p.top || '10', 10);
  if (!code || !INDEX_CONSTITUENTS[code]) {
    return { error: 'unknown index code; supported: ' + Object.keys(INDEX_CONSTITUENTS).join(', ') };
  }
  var meta = INDEX_CONSTITUENTS[code];
  var members = meta.members.slice(0, top);

  var sheet = activeSheet();
  sheet.getRange('A1:Z2000').clearContent();
  sheet.getRange('A1').setFormula('=GOOGLEFINANCE("' + code + '","price")');
  sheet.getRange('B1').setFormula('=GOOGLEFINANCE("' + code + '","changepct")');
  for (var i = 0; i < members.length; i++) {
    sheet.getRange(i + 2, 1).setValue(members[i]);
    sheet.getRange(i + 2, 2).setFormula('=GOOGLEFINANCE("' + members[i] + '","name")');
    sheet.getRange(i + 2, 3).setFormula('=GOOGLEFINANCE("' + members[i] + '","price")');
    sheet.getRange(i + 2, 4).setFormula('=GOOGLEFINANCE("' + members[i] + '","changepct")');
  }
  SpreadsheetApp.flush();
  Utilities.sleep(2500);

  var indexValue  = numOrNull(sheet.getRange('A1').getValue());
  var indexChange = numOrNull(sheet.getRange('B1').getValue());

  var rows = sheet.getRange(2, 1, members.length, 4).getValues();
  var constituents = rows.map(function (r) {
    return {
      symbol: googleToCanonical(r[0]),
      name:   stringOrNull(r[1]),
      price:  numOrNull(r[2]),
      change: numOrNull(r[3])
    };
  }).filter(function (c) { return c.price !== null; });

  return {
    code: code,
    name: meta.name,
    value: indexValue,
    change: indexChange,
    constituents: constituents
  };
}

// ---------- helpers ----------------------------------------------------------

function numOrNull(v) {
  if (v === '' || v === null || v === undefined) return null;
  if (typeof v === 'number' && isFinite(v)) return v;
  var n = Number(v);
  return isFinite(n) ? n : null;
}

function stringOrNull(v) {
  if (v === '' || v === null || v === undefined) return null;
  return String(v);
}

function googleToCanonical(s) {
  if (!s) return s;
  if (s.indexOf('NSE:') === 0) return s.slice(4) + '.NS';
  if (s.indexOf('BOM:') === 0) return s.slice(4) + '.BO';
  return s;
}
