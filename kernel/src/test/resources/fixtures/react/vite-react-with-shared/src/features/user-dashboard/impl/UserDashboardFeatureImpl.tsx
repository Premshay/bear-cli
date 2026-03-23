// User-owned — BEAR will not overwrite this file

import React from 'react';
import type { UserDashboardFeatureLogic } from '../../../../build/generated/bear/types/user-dashboard/UserDashboardFeatureLogic';
import { formatDate } from '../../../shared/utils/formatDate';

export class UserDashboardFeatureImpl implements UserDashboardFeatureLogic {
  render() {
    return <div>Last updated: {formatDate(new Date())}</div>;
  }
}
