package com.bear.kernel.ir;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BearIrValidator {
    public ValidationResult validate(BearIr ir) {
        List<String> errors = new ArrayList<>();

        validateSchema(ir.document(), errors);
        validateSemantics(ir.document(), errors);

        return new ValidationResult(errors);
    }

    private void validateSchema(Map<String, Object> doc, List<String> errors) {
        if (!doc.containsKey("name") || !(doc.get("name") instanceof String)) {
            errors.add("schema: required string field 'name' is missing");
        }
    }

    private void validateSemantics(Map<String, Object> doc, List<String> errors) {
        Object name = doc.get("name");
        if (name instanceof String value && value.isBlank()) {
            errors.add("semantic: 'name' must not be blank");
        }
    }

    public record ValidationResult(List<String> errors) {
        public boolean isValid() {
            return errors.isEmpty();
        }
    }
}
