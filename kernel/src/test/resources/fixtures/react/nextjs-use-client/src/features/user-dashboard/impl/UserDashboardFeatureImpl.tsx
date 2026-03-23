"use client";

// User-owned — BEAR will not overwrite this file

import React, { useState } from 'react';
import type { UserDashboardFeatureLogic } from '../../../../build/generated/bear/types/user-dashboard/UserDashboardFeatureLogic';

export class UserDashboardFeatureImpl implements UserDashboardFeatureLogic {
  render() {
    const [count, setCount] = useState(0);
    return (
      <div>
        <button onClick={() => setCount(c => c + 1)}>Count: {count}</button>
      </div>
    );
  }
}
