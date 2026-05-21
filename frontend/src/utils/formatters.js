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

