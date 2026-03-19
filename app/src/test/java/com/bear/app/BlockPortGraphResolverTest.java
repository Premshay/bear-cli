package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockPortGraphResolverTest {
    @Test
    void inboundTargetWrapperSetIsDeterministic(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot.resolve("bear-ir"));
        Files.writeString(
            repoRoot.resolve("bear.blocks.yaml"),
            "version: v1\n"
                + "blocks:\n"
                + "  - name: account\n"
                + "    ir: bear-ir/account.bear.yaml\n"
                + "    projectRoot: .\n"
                + "  - name: transaction-log\n"
                + "    ir: bear-ir/transaction-log.bear.yaml\n"
                + "    projectRoot: .\n",
            StandardCharsets.UTF_8
        );

        Files.writeString(repoRoot.resolve("bear-ir/account.bear.yaml"), accountIr(), StandardCharsets.UTF_8);
        Files.writeString(repoRoot.resolve("bear-ir/transaction-log.bear.yaml"), txIr(), StandardCharsets.UTF_8);

        BlockPortGraph graph = BlockPortGraphResolver.resolveAndValidate(
            repoRoot,
            repoRoot.resolve("bear.blocks.yaml")
        );
        TreeSet<String> wrappers = BlockPortGraphResolver.inboundTargetWrapperFqcns(graph);

        assertEquals(
            List.of(
                "com.bear.generated.transaction.log.TransactionLog_AppendTransaction",
                "com.bear.generated.transaction.log.TransactionLog_GetTransactions"
            ),
            wrappers.stream().toList()
        );
    }

    @Test
    void cycleDetectionUsesCanonicalLeastRotation(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot.resolve("bear-ir"));
        Files.writeString(
            repoRoot.resolve("bear.blocks.yaml"),
            "version: v1\n"
                + "blocks:\n"
                + "  - name: account\n"
                + "    ir: bear-ir/account.bear.yaml\n"
                + "    projectRoot: .\n"
                + "  - name: transaction-log\n"
                + "    ir: bear-ir/transaction-log.bear.yaml\n"
                + "    projectRoot: .\n",
            StandardCharsets.UTF_8
        );

        Files.writeString(repoRoot.resolve("bear-ir/account.bear.yaml"), accountIrWithCycle(), StandardCharsets.UTF_8);
        Files.writeString(repoRoot.resolve("bear-ir/transaction-log.bear.yaml"), txIrWithCycle(), StandardCharsets.UTF_8);

        BlockIndexValidationException ex = org.junit.jupiter.api.Assertions.assertThrows(
            BlockIndexValidationException.class,
            () -> BlockPortGraphResolver.resolveAndValidate(repoRoot, repoRoot.resolve("bear.blocks.yaml"))
        );

        assertEquals("bear.blocks.yaml", ex.path());
        assertEquals("BLOCK_PORT_CYCLE_DETECTED: cycle=account->transaction-log->account", ex.getMessage());
    }
    // --- Multi-root fixture helper ---

    /**
     * Builds a two-root BlockPortGraph where account (module-a) has a block-port edge
     * to transaction-log (module-b).
     */
    private static BlockPortGraph multiRootGraph(Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("multi-root-repo");
        Files.createDirectories(repoRoot.resolve("bear-ir"));
        Files.writeString(
            repoRoot.resolve("bear.blocks.yaml"),
            "version: v1\n"
                + "blocks:\n"
                + "  - name: account\n"
                + "    ir: bear-ir/account.bear.yaml\n"
                + "    projectRoot: module-a\n"
                + "  - name: transaction-log\n"
                + "    ir: bear-ir/transaction-log.bear.yaml\n"
                + "    projectRoot: module-b\n",
            StandardCharsets.UTF_8
        );
        Files.writeString(repoRoot.resolve("bear-ir/account.bear.yaml"), accountIr(), StandardCharsets.UTF_8);
        Files.writeString(repoRoot.resolve("bear-ir/transaction-log.bear.yaml"), txIr(), StandardCharsets.UTF_8);
        return BlockPortGraphResolver.resolveAndValidate(repoRoot, repoRoot.resolve("bear.blocks.yaml"));
    }

    // --- Sub-task 1.1: inboundWrapperFqcnsAreScopedToSourceRoot ---

    @Test
    void inboundWrapperFqcnsAreScopedToSourceRoot(@TempDir Path tempDir) throws Exception {
        BlockPortGraph graph = multiRootGraph(tempDir);

        TreeSet<String> accountWrappers = BlockPortGraphResolver.inboundTargetWrapperFqcns(graph, Set.of("account"));
        assertEquals(
            new TreeSet<>(Set.of(
                BlockPortGraphResolver.wrapperFqcn("transaction-log", "AppendTransaction"),
                BlockPortGraphResolver.wrapperFqcn("transaction-log", "GetTransactions")
            )),
            accountWrappers
        );

        TreeSet<String> txWrappers = BlockPortGraphResolver.inboundTargetWrapperFqcns(graph, Set.of("transaction-log"));
        assertEquals(new TreeSet<>(), txWrappers);
    }

    // --- Sub-task 1.2: inboundWrapperFqcnsForTargetRootAreEmpty ---

    @Test
    void inboundWrapperFqcnsForTargetRootAreEmpty(@TempDir Path tempDir) throws Exception {
        BlockPortGraph graph = multiRootGraph(tempDir);

        TreeSet<String> txWrappers = BlockPortGraphResolver.inboundTargetWrapperFqcns(graph, Set.of("transaction-log"));
        assertEquals(new TreeSet<String>(), txWrappers);
    }

    // --- Sub-task 1.3: graphResolutionIsOrderIndependent (property-style, plain JUnit 5) ---

    @Test
    void graphResolutionIsOrderIndependent(@TempDir Path tempDir) throws Exception {
        // Ordering A: account first, transaction-log second
        Path repoA = tempDir.resolve("order-a");
        Files.createDirectories(repoA.resolve("bear-ir"));
        Files.writeString(
            repoA.resolve("bear.blocks.yaml"),
            "version: v1\n"
                + "blocks:\n"
                + "  - name: account\n"
                + "    ir: bear-ir/account.bear.yaml\n"
                + "    projectRoot: module-a\n"
                + "  - name: transaction-log\n"
                + "    ir: bear-ir/transaction-log.bear.yaml\n"
                + "    projectRoot: module-b\n",
            StandardCharsets.UTF_8
        );
        Files.writeString(repoA.resolve("bear-ir/account.bear.yaml"), accountIr(), StandardCharsets.UTF_8);
        Files.writeString(repoA.resolve("bear-ir/transaction-log.bear.yaml"), txIr(), StandardCharsets.UTF_8);

        // Ordering B: transaction-log first, account second
        Path repoB = tempDir.resolve("order-b");
        Files.createDirectories(repoB.resolve("bear-ir"));
        Files.writeString(
            repoB.resolve("bear.blocks.yaml"),
            "version: v1\n"
                + "blocks:\n"
                + "  - name: transaction-log\n"
                + "    ir: bear-ir/transaction-log.bear.yaml\n"
                + "    projectRoot: module-b\n"
                + "  - name: account\n"
                + "    ir: bear-ir/account.bear.yaml\n"
                + "    projectRoot: module-a\n",
            StandardCharsets.UTF_8
        );
        Files.writeString(repoB.resolve("bear-ir/account.bear.yaml"), accountIr(), StandardCharsets.UTF_8);
        Files.writeString(repoB.resolve("bear-ir/transaction-log.bear.yaml"), txIr(), StandardCharsets.UTF_8);

        BlockPortGraph graphA = BlockPortGraphResolver.resolveAndValidate(repoA, repoA.resolve("bear.blocks.yaml"));
        BlockPortGraph graphB = BlockPortGraphResolver.resolveAndValidate(repoB, repoB.resolve("bear.blocks.yaml"));

        // Same number of edges
        assertEquals(graphA.edges().size(), graphB.edges().size());

        // Each edge has identical sourceBlockKey, targetBlockKey, targetOps
        for (int i = 0; i < graphA.edges().size(); i++) {
            BlockPortEdge edgeA = graphA.edges().get(i);
            BlockPortEdge edgeB = graphB.edges().get(i);
            assertEquals(edgeA.sourceBlockKey(), edgeB.sourceBlockKey());
            assertEquals(edgeA.targetBlockKey(), edgeB.targetBlockKey());
            assertEquals(edgeA.targetOps(), edgeB.targetOps());
        }

        // Identical inbound wrapper FQCN sets for the source block
        assertEquals(
            BlockPortGraphResolver.inboundTargetWrapperFqcns(graphA, Set.of("account")),
            BlockPortGraphResolver.inboundTargetWrapperFqcns(graphB, Set.of("account"))
        );
    }

    private static String accountIr() {
        return """
            version: v1
            block:
              name: Account
              kind: logic
              operations:
                - name: Deposit
                  contract:
                    inputs:
                      - name: accountId
                        type: string
                      - name: amountCents
                        type: int
                      - name: requestId
                        type: string
                    outputs:
                      - name: balanceCents
                        type: int
                  uses:
                    allow:
                      - port: transactionLog
                        kind: block
                        targetOps: [AppendTransaction]
                - name: GetBalance
                  contract:
                    inputs:
                      - name: accountId
                        type: string
                    outputs:
                      - name: balanceCents
                        type: int
                  uses:
                    allow: []
              effects:
                allow:
                  - port: transactionLog
                    kind: block
                    targetBlock: transaction-log
                    targetOps: [GetTransactions, AppendTransaction]
            """;
    }

    private static String accountIrWithCycle() {
        return """
            version: v1
            block:
              name: Account
              kind: logic
              operations:
                - name: Deposit
                  contract:
                    inputs:
                      - name: accountId
                        type: string
                    outputs:
                      - name: balanceCents
                        type: int
                  uses:
                    allow:
                      - port: transactionLog
                        kind: block
                        targetOps: [AppendTransaction]
              effects:
                allow:
                  - port: transactionLog
                    kind: block
                    targetBlock: transaction-log
                    targetOps: [AppendTransaction]
            """;
    }

    private static String txIrWithCycle() {
        return """
            version: v1
            block:
              name: TransactionLog
              kind: logic
              operations:
                - name: AppendTransaction
                  contract:
                    inputs:
                      - name: accountId
                        type: string
                    outputs:
                      - name: seq
                        type: int
                  uses:
                    allow:
                      - port: accountBoundary
                        kind: block
                        targetOps: [Deposit]
              effects:
                allow:
                  - port: accountBoundary
                    kind: block
                    targetBlock: account
                    targetOps: [Deposit]
            """;
    }
    private static String txIr() {
        return """
            version: v1
            block:
              name: TransactionLog
              kind: logic
              operations:
                - name: AppendTransaction
                  contract:
                    inputs:
                      - name: accountId
                        type: string
                    outputs:
                      - name: seq
                        type: int
                  uses:
                    allow:
                      - port: txStore
                        ops: [append]
                - name: GetTransactions
                  contract:
                    inputs:
                      - name: accountId
                        type: string
                    outputs:
                      - name: transactionsJson
                        type: string
                  uses:
                    allow:
                      - port: txStore
                        ops: [listSince]
              effects:
                allow:
                  - port: txStore
                    ops: [append, listSince]
            """;
    }
}



