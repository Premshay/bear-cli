// User-owned — BEAR will not overwrite this file

import React from 'react';
import type { UserDashboardFeatureLogic } from '../../../../build/generated/bear/types/user-dashboard/UserDashboardFeatureLogic';
import { Button } from '../components/Button';

export class UserDashboardFeatureImpl implements UserDashboardFeatureLogic {
  render() {
    return <Button>Dashboard</Button>;
  }
}
