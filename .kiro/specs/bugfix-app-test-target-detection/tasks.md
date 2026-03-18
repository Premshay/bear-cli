# Tasks: App Test JVM Target Pin Fix

## Task 1: Add pinJvmTarget to BearCliTest
- [x] 1.1 Add `pinJvmTarget(Path)` static helper method to BearCliTest
- [x] 1.2 Add pin calls to all failing test methods (use automation script)
- [x] 1.3 Add pin calls to `createMultiBlockFixture` for all 3 project roots
- [x] 1.4 Run BearCliTest — target 0 failures

## Task 2: Add pinJvmTarget to BearCliAgentModeTest
- [x] 2.1 Add `pinJvmTarget(Path)` static helper method
- [x] 2.2 Add pin calls to all 16 failing test methods (including repo subdirs)
- [x] 2.3 Run BearCliAgentModeTest — target 0 failures

## Task 3: Verify already-fixed tests
- [x] 3.1 Confirm SingleFileIndexInferenceTest passes (already fixed)
- [x] 3.2 Confirm TargetSeamParityTest passes (already fixed)

## Task 4: Full verification
- [x] 4.1 Run full app test suite — 0 failures ✅
- [x] 4.2 Run full kernel test suite — 0 regressions ✅

## Task 5: Cleanup
- [x] 5.1 Delete throwaway automation scripts (add-jvm-pin-to-tests.py, add-jvm-pin.py, fix-remaining-pins.py, fix-service-pins.py)
