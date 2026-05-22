/**
 * Formats a numeric value into a localized string.
 * Supports both currency (for stocks) and point-based (for indices) formatting.
 * High Reliability: Handles invalid ISO codes and prevents UI crashes via try-catch.
 */
export const formatPrice = (price, currencyCode = 'INR', showSymbol = true) => {
  let numericPrice;
  try {
    numericPrice = typeof price === 'string' ? parseFloat(price.replace(/,/g, '')) : Number(price);
  } catch (e) {
    return '---';
  }

  if (price === null || price === undefined || !Number.isFinite(numericPrice)) {
    return '---';
  }

  // 1. Map common display/market strings to valid ISO 4217 codes
  let isoCode = (currencyCode || 'INR').toUpperCase().trim();
  
  const currencyMap = {
    'IN': 'INR',
    'INDIA': 'INR',
    'US': 'USD',
    'USA': 'USD',
    'UK': 'GBP',
    'EU': 'EUR'
  };

  if (currencyMap[isoCode]) {
    isoCode = currencyMap[isoCode];
  }

  // 2. Production-safe formatting
  try {
    const options = {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    };

    if (showSymbol) {
      options.style = 'currency';
      options.currency = isoCode;
    }

    return new Intl.NumberFormat(isoCode === 'INR' ? 'en-IN' : 'en-US', options).format(numericPrice);
  } catch (err) {
    console.error(`Formatting error for currency: ${isoCode}`, err);
    
    // 3. Ultra-safe fallback: basic numeric string with symbol
    const symbol = isoCode === 'USD' ? '$' : '₹';
    const formattedNum = numericPrice.toLocaleString(undefined, {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });
    return showSymbol ? `${symbol}${formattedNum}` : formattedNum;
  }
};

/**
 * Safely converts a value to a fixed-point percentage string.
 * Prevents UI crashes when gain/progress values are null or undefined.
 */
export const safePct = (value, decimals = 2) => {
  const num = typeof value === 'number' ? value : parseFloat(value);
  if (!Number.isFinite(num)) return '0.00';
  const d = Number.isFinite(decimals) ? Math.max(0, Math.min(20, decimals)) : 2;
  try {
    return num.toFixed(d);
  } catch (e) {
    return '0.00';
  }
};

/**
 * Safe array max — avoids Math.max(...[]) returning -Infinity which can crash UIs.
 */
export const safeMax = (arr, fallback = 0) => {
  if (!Array.isArray(arr) || arr.length === 0) return fallback;
  let max = -Infinity;
  for (const v of arr) {
    const n = typeof v === 'number' ? v : parseFloat(v);
    if (Number.isFinite(n) && n > max) max = n;
  }
  return Number.isFinite(max) ? max : fallback;
};

/**
 * Safely formats a number with compact notation (e.g., 1.2M)
 */
export const safeCompactNumber = (value) => {
  const num = parseFloat(value);
  if (isNaN(num)) return '---';
  try {
    return new Intl.NumberFormat('en-US', {
      notation: "compact",
      maximumFractionDigits: 1
    }).format(num);
  } catch (e) {
    return num.toString();
  }
};

/**
 * Safely parses and formats any number
 */
export const safeNumber = (value, decimals = 2) => {
  const num = parseFloat(value);
  if (isNaN(num)) return '0';
  try {
    return num.toLocaleString(undefined, {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals
    });
  } catch (e) {
    return '0';
  }
};


