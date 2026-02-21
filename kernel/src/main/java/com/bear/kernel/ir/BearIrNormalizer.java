package com.bear.kernel.ir;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BearIrNormalizer {
    public BearIr normalize(BearIr ir) {
        BearIr.Block block = ir.block();

        List<BearIr.Field> inputs = sortFields(block.contract().inputs());
        List<BearIr.Field> outputs = sortFields(block.contract().outputs());
        BearIr.Contract contract = new BearIr.Contract(inputs, outputs);

        List<BearIr.EffectPort> ports = sortPorts(block.effects().allow());
        BearIr.Effects effects = new BearIr.Effects(ports);
        BearIr.Impl impl = sortImpl(block.impl());

        BearIr.Idempotency idempotency = normalizeIdempotency(block.idempotency());
        List<BearIr.Invariant> invariants = block.invariants();
        if (invariants != null) {
            invariants = normalizeInvariants(invariants);
            if (invariants.isEmpty()) {
                invariants = null;
            }
        }

        BearIr.Block normalizedBlock = new BearIr.Block(
            block.name(),
            block.kind(),
            contract,
            effects,
            impl,
            idempotency,
            invariants
        );
        return new BearIr(ir.version(), normalizedBlock);
    }

    private List<BearIr.Field> sortFields(List<BearIr.Field> fields) {
        List<BearIr.Field> list = new ArrayList<>(fields);
        list.sort(Comparator.comparing(BearIr.Field::name));
        return list;
    }

    private List<BearIr.EffectPort> sortPorts(List<BearIr.EffectPort> ports) {
        List<BearIr.EffectPort> list = new ArrayList<>();
        for (BearIr.EffectPort port : ports) {
            List<String> ops = new ArrayList<>(port.ops());
            ops.sort(String::compareTo);
            list.add(new BearIr.EffectPort(port.port(), ops));
        }
        list.sort(Comparator.comparing(BearIr.EffectPort::port));
        return list;
    }

    private List<BearIr.Invariant> normalizeInvariants(List<BearIr.Invariant> invariants) {
        ArrayList<BearIr.Invariant> list = new ArrayList<>();
        for (BearIr.Invariant invariant : invariants) {
            BearIr.InvariantParams params = invariant.params() == null
                ? new BearIr.InvariantParams(null, List.of())
                : new BearIr.InvariantParams(
                    invariant.params().value(),
                    invariant.params().values() == null ? List.of() : List.copyOf(invariant.params().values())
                );
            list.add(new BearIr.Invariant(invariant.kind(), invariant.scope(), invariant.field(), params));
        }
        return List.copyOf(list);
    }

    private BearIr.Idempotency normalizeIdempotency(BearIr.Idempotency idempotency) {
        if (idempotency == null) {
            return null;
        }
        List<String> keyFromInputs = idempotency.keyFromInputs() == null
            ? null
            : List.copyOf(idempotency.keyFromInputs());
        return new BearIr.Idempotency(idempotency.key(), keyFromInputs, idempotency.store());
    }

    private BearIr.Impl sortImpl(BearIr.Impl impl) {
        if (impl == null || impl.allowedDeps() == null || impl.allowedDeps().isEmpty()) {
            return new BearIr.Impl(List.of());
        }
        List<BearIr.AllowedDep> sorted = new ArrayList<>(impl.allowedDeps());
        sorted.sort(Comparator.comparing(BearIr.AllowedDep::maven));
        return new BearIr.Impl(sorted);
    }
}

