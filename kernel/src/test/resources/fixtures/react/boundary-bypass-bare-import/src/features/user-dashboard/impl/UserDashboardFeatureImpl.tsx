// User-owned — BEAR will not overwrite this file

import React from 'react';
import type { UserDashboardFeatureLogic } from '../../../../build/generated/bear/types/user-dashboard/UserDashboardFeatureLogic';
// VIOLATION: Bare package import (not react or react-dom)
import _ from 'lodash';

export class UserDashboardFeatureImpl implements UserDashboardFeatureLogic {
  render() {
    return <div>{_.capitalize('hello')}</div>;
  }
}
