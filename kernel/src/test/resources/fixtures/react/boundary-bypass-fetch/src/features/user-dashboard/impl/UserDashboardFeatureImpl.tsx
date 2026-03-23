// User-owned — BEAR will not overwrite this file

import React, { useEffect, useState } from 'react';
import type { UserDashboardFeatureLogic } from '../../../../build/generated/bear/types/user-dashboard/UserDashboardFeatureLogic';

// VIOLATION: Direct fetch() call in governed .tsx component file
// Expected: check fails exit 7, BOUNDARY_BYPASS (API boundary scanner)
export class UserDashboardFeatureImpl implements UserDashboardFeatureLogic {
  async loadUsers() {
    const data = await fetch('/api/users');
    return data.json();
  }

  render() {
    return <div>User Dashboard</div>;
  }
}
