// User-owned — BEAR will not overwrite this file

import React from 'react';
import type { ProductCatalogFeatureLogic } from '../../../../build/generated/bear/types/product-catalog/ProductCatalogFeatureLogic';
import { ProductCard } from '../components/ProductCard';

export class ProductCatalogFeatureImpl implements ProductCatalogFeatureLogic {
  render() {
    return <ProductCard name="Product" />;
  }
}
