import React from 'react';

export function ProductCard({ name }: { name: string }) {
  return <div className="product-card">{name}</div>;
}
