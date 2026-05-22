/**
 * Formats a numeric price into a localized currency string.
 * High Reliability: Handles invalid ISO codes and prevents UI crashes via try-catch.
 */
export const formatPrice = (price, currencyCode = 'INR') => {
  const numericPrice = typeof price === 'string' ? parseFloat(price.replace(/,/g, '')) : price;
  
  if (isNaN(numericPrice) || numericPrice === null || numericPrice === undefined) {
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
    return new Intl.NumberFormat(isoCode === 'INR' ? 'en-IN' : 'en-US', {
      style: 'currency',
      currency: isoCode,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(numericPrice);
  } catch (err) {
    console.error(`Formatting error for currency: ${isoCode}`, err);
    
    // 3. Ultra-safe fallback: basic numeric string with symbol
    const symbol = isoCode === 'USD' ? '$' : '₹';
    return `${symbol}${numericPrice.toLocaleString(undefined, {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    })}`;
  }
};

/**
 * Safely converts a value to a fixed-point percentage string.
 * Prevents UI crashes when gain/progress values are null or undefined.
 */
export const safePct = (value, decimals = 2) => {
  const num = parseFloat(value);
  if (isNaN(num)) return '0.00';
  try {
    return num.toFixed(decimals);
  } catch (e) {
    return '0.00';
  }
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


