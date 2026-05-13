export const formatPrice = (price, currency = 'INR') => {
  const numericPrice = typeof price === 'string' ? parseFloat(price.replace(/,/g, '')) : price;
  
  if (isNaN(numericPrice) || numericPrice === null || numericPrice === undefined) {
    return '---';
  }

  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: currency || 'INR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(numericPrice);
};
