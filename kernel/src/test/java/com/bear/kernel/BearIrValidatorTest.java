package com.bear.kernel;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrValidator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

class BearIrValidatorTest {
    @Test
    void invalidWhenNameIsMissing() {
        BearIrValidator validator = new BearIrValidator();
        BearIrValidator.ValidationResult result = validator.validate(new BearIr(Map.of()));

        assertFalse(result.isValid());
    }
}
