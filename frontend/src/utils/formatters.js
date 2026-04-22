export const formatPrice = (price, market) => {
  const symbol = market === 'US' ? '$' : '₹';
  const numericPrice = typeof price === 'string' ? parseFloat(price.replace(/,/g, '')) : price;
  
  if (isNaN(numericPrice) || numericPrice === null || numericPrice === undefined) {
    return `${symbol}0.00`;
  }

  return `${symbol}${numericPrice.toFixed(2)}`;
};
