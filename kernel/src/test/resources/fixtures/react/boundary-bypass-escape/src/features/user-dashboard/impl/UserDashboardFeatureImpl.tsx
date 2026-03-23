// User-owned — BEAR will not overwrite this file

import React from 'react';
import type { UserDashboardFeatureLogic } from '../../../../build/generated/bear/types/user-dashboard/UserDashboardFeatureLogic';
// VIOLATION: This import escapes the feature root
import { outsideModule } from '../../../../outside/module';

export class UserDashboardFeatureImpl implements UserDashboardFeatureLogic {
  render() {
    return <div>{outsideModule()}</div>;
  }
}
