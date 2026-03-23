"use client";

// User-owned — BEAR will not overwrite this file

import React from 'react';
import type { UserDashboardFeatureLogic } from '../../../../build/generated/bear/types/user-dashboard/UserDashboardFeatureLogic';
// VIOLATION: @/* alias resolving to sibling feature
import { ProductCard } from '@/features/product-catalog/ProductCard';

export class UserDashboardFeatureImpl implements UserDashboardFeatureLogic {
  render() {
    return <ProductCard name="Product" />;
  }
}
