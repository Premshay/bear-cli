package com.bear.kernel.target;

import com.bear.kernel.ir.BearIr;

public interface Target {
    void compile(BearIr ir);
}
