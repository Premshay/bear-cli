package com.bear.app;

import com.bear.kernel.ir.BearIr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

final class PrDeltaClassifier {
    private PrDeltaClassifier() {
    }

    static List<BoundarySignal> computeBoundarySignals(BoundaryManifest baseline, BoundaryManifest candidate) {
        List<BoundarySignal> signals = new ArrayList<>();
        for (String capability : candidate.capabilities().keySet()) {
            if (!baseline.capabilities().containsKey(capability)) {
                signals.add(new BoundarySignal(BoundaryType.CAPABILITY_ADDED, capability));
            }
        }
        for (Map.Entry<String, String> dep : candidate.allowedDeps().entrySet()) {
            String ga = dep.getKey();
            if (!baseline.allowedDeps().containsKey(ga)) {
                signals.add(new BoundarySignal(BoundaryType.PURE_DEP_ADDED, ga + "@" + dep.getValue()));
                continue;
            }
            String oldVersion = baseline.allowedDeps().get(ga);
            if (!oldVersion.equals(dep.getValue())) {
                signals.add(new BoundarySignal(BoundaryType.PURE_DEP_VERSION_CHANGED, ga + "@" + oldVersion + "->" + dep.getValue()));
            }
        }
        for (Map.Entry<String, TreeSet<String>> entry : candidate.capabilities().entrySet()) {
            String capability = entry.getKey();
            if (!baseline.capabilities().containsKey(capability)) {
                continue;
            }
            TreeSet<String> baselineOps = baseline.capabilities().get(capability);
            for (String op : entry.getValue()) {
                if (!baselineOps.contains(op)) {
                    signals.add(new BoundarySignal(BoundaryType.CAPABILITY_OP_ADDED, capability + "." + op));
                }
            }
        }
        for (String invariant : baseline.invariants()) {
            if (!candidate.invariants().contains(invariant)) {
                signals.add(new BoundarySignal(BoundaryType.INVARIANT_RELAXED, invariant));
            }
        }
        signals.sort(Comparator
            .comparing((BoundarySignal signal) -> signal.type().order)
            .thenComparing(BoundarySignal::key));
        return signals;
    }

    static List<PrDelta> computePrDeltas(BearIr baseIr, BearIr headIr) {
        PrSurface base = baseIr == null ? emptyPrSurface() : toPrSurface(baseIr);
        PrSurface head = toPrSurface(headIr);

        List<PrDelta> deltas = new ArrayList<>();

        for (String port : head.ports()) {
            if (!base.ports().contains(port)) {
                deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.PORTS, PrChange.ADDED, port));
            }
        }
        for (String port : base.ports()) {
            if (!head.ports().contains(port)) {
                deltas.add(new PrDelta(PrClass.ORDINARY, PrCategory.PORTS, PrChange.REMOVED, port));
            }
        }

        TreeSet<String> commonPorts = new TreeSet<>(head.ports());
        commonPorts.retainAll(base.ports());
        for (String port : commonPorts) {
            TreeSet<String> headOps = head.opsByPort().getOrDefault(port, new TreeSet<>());
            TreeSet<String> baseOps = base.opsByPort().getOrDefault(port, new TreeSet<>());
            for (String op : headOps) {
                if (!baseOps.contains(op)) {
                    deltas.add(new PrDelta(PrClass.ORDINARY, PrCategory.OPS, PrChange.ADDED, port + "." + op));
                }
            }
            for (String op : baseOps) {
                if (!headOps.contains(op)) {
                    deltas.add(new PrDelta(PrClass.ORDINARY, PrCategory.OPS, PrChange.REMOVED, port + "." + op));
                }
            }
        }

        addIdempotencyDeltas(deltas, base.idempotency(), head.idempotency());
        addAllowedDepDeltas(deltas, base.allowedDeps(), head.allowedDeps());
        addContractDeltas(deltas, base.inputs(), head.inputs(), true);
        addContractDeltas(deltas, base.outputs(), head.outputs(), false);

        for (String invariant : head.invariants()) {
            if (!base.invariants().contains(invariant)) {
                deltas.add(new PrDelta(PrClass.ORDINARY, PrCategory.INVARIANTS, PrChange.ADDED, invariant));
            }
        }
        for (String invariant : base.invariants()) {
            if (!head.invariants().contains(invariant)) {
                deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.INVARIANTS, PrChange.REMOVED, invariant));
            }
        }

        deltas.sort(Comparator
            .comparing((PrDelta delta) -> delta.clazz().order)
            .thenComparing(delta -> delta.category().order)
            .thenComparing(delta -> delta.change().order)
            .thenComparing(PrDelta::key));
        return deltas;
    }

    static void addAllowedDepDeltas(List<PrDelta> deltas, Map<String, String> base, Map<String, String> head) {
        TreeSet<String> names = new TreeSet<>();
        names.addAll(base.keySet());
        names.addAll(head.keySet());
        for (String ga : names) {
            boolean inBase = base.containsKey(ga);
            boolean inHead = head.containsKey(ga);
            if (!inBase) {
                deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.ALLOWED_DEPS, PrChange.ADDED, ga + "@" + head.get(ga)));
                continue;
            }
            if (!inHead) {
                deltas.add(new PrDelta(PrClass.ORDINARY, PrCategory.ALLOWED_DEPS, PrChange.REMOVED, ga + "@" + base.get(ga)));
                continue;
            }
            if (!base.get(ga).equals(head.get(ga))) {
                deltas.add(new PrDelta(
                    PrClass.BOUNDARY_EXPANDING,
                    PrCategory.ALLOWED_DEPS,
                    PrChange.CHANGED,
                    ga + "@" + base.get(ga) + "->" + head.get(ga)
                ));
            }
        }
    }

    static void addIdempotencyDeltas(
        List<PrDelta> deltas,
        BearIr.Idempotency base,
        BearIr.Idempotency head
    ) {
        if (base == null && head == null) {
            return;
        }
        if (base == null) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.ADDED, "idempotency"));
            return;
        }
        if (head == null) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.REMOVED, "idempotency"));
            return;
        }

        if (!Objects.equals(base.key(), head.key())) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.CHANGED, "idempotency.key"));
        }
        if (!Objects.equals(base.keyFromInputs(), head.keyFromInputs())) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.CHANGED, "idempotency.keyFromInputs"));
        }
        if (!base.store().port().equals(head.store().port())) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.CHANGED, "idempotency.store.port"));
        }
        if (!base.store().getOp().equals(head.store().getOp())) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.CHANGED, "idempotency.store.getOp"));
        }
        if (!base.store().putOp().equals(head.store().putOp())) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.CHANGED, "idempotency.store.putOp"));
        }
    }

    static void addContractDeltas(
        List<PrDelta> deltas,
        Map<String, BearIr.FieldType> base,
        Map<String, BearIr.FieldType> head,
        boolean input
    ) {
        TreeSet<String> names = new TreeSet<>();
        names.addAll(base.keySet());
        names.addAll(head.keySet());
        for (String name : names) {
            boolean inBase = base.containsKey(name);
            boolean inHead = head.containsKey(name);
            String prefix = input ? "input." : "output.";

            if (!inBase) {
                PrClass clazz = input ? PrClass.ORDINARY : PrClass.BOUNDARY_EXPANDING;
                deltas.add(new PrDelta(
                    clazz,
                    PrCategory.CONTRACT,
                    PrChange.ADDED,
                    prefix + name + ":" + typeToken(head.get(name))
                ));
                continue;
            }
            if (!inHead) {
                deltas.add(new PrDelta(
                    PrClass.BOUNDARY_EXPANDING,
                    PrCategory.CONTRACT,
                    PrChange.REMOVED,
                    prefix + name + ":" + typeToken(base.get(name))
                ));
                continue;
            }
            if (base.get(name) != head.get(name)) {
                deltas.add(new PrDelta(
                    PrClass.BOUNDARY_EXPANDING,
                    PrCategory.CONTRACT,
                    PrChange.CHANGED,
                    prefix + name + ":" + typeToken(base.get(name)) + "->" + typeToken(head.get(name))
                ));
            }
        }
    }

    static String typeToken(BearIr.FieldType type) {
        return type.name().toLowerCase();
    }

    static PrSurface toPrSurface(BearIr ir) {
        TreeSet<String> ports = new TreeSet<>();
        Map<String, TreeSet<String>> opsByPort = new TreeMap<>();
        for (BearIr.EffectPort port : ir.block().effects().allow()) {
            ports.add(port.port());
            opsByPort.put(port.port(), new TreeSet<>(port.ops()));
        }
        Map<String, String> allowedDeps = new TreeMap<>();
        if (ir.block().impl() != null && ir.block().impl().allowedDeps() != null) {
            for (BearIr.AllowedDep dep : ir.block().impl().allowedDeps()) {
                allowedDeps.put(dep.maven(), dep.version());
            }
        }

        Map<String, BearIr.FieldType> inputs = new TreeMap<>();
        for (BearIr.Field input : ir.block().contract().inputs()) {
            inputs.put(input.name(), input.type());
        }
        Map<String, BearIr.FieldType> outputs = new TreeMap<>();
        for (BearIr.Field output : ir.block().contract().outputs()) {
            outputs.put(output.name(), output.type());
        }

        TreeSet<String> invariants = new TreeSet<>();
        if (ir.block().invariants() != null) {
            for (BearIr.Invariant invariant : ir.block().invariants()) {
                invariants.add(invariant.kind().name().toLowerCase() + ":" + invariant.field());
            }
        }
        return new PrSurface(ports, opsByPort, allowedDeps, inputs, outputs, ir.block().idempotency(), invariants);
    }

    static PrSurface emptyPrSurface() {
        return new PrSurface(
            new TreeSet<>(),
            new TreeMap<>(),
            new TreeMap<>(),
            new TreeMap<>(),
            new TreeMap<>(),
            null,
            new TreeSet<>()
        );
    }
}
