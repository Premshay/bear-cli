// User-owned — BEAR will not overwrite this file

import React from 'react';
import type { UserDashboardFeatureLogic } from '../../../../build/generated/bear/types/user-dashboard/UserDashboardFeatureLogic';
// VIOLATION: This import reaches into a sibling feature
import { ProductCard } from '../../product-catalog/ProductCard';

export class UserDashboardFeatureImpl implements UserDashboardFeatureLogic {
  render() {
    return <ProductCard name="Product" />;
  }
}
