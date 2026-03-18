# Tasks: App Test JVM Target Pin Fix

## Task 1: Create shared TestTargetPins utility
- [x] 1.1 Create `TestTargetPins.pinJvm(Path)` in `app/src/test/java/com/bear/app/`
- [x] 1.2 Use `TargetId.JVM.value()` for the token (no hard-coded string)

## Task 2: Pin JVM target in BearCliTest
- [x] 2.1 Add `TestTargetPins.pinJvm(...)` calls to all failing test methods
- [x] 2.2 Add pin calls to `createMultiBlockFixture` for all 3 project roots
- [x] 2.3 Run BearCliTest — target 0 failures

## Task 3: Pin JVM target in BearCliAgentModeTest
- [x] 3.1 Add `TestTargetPins.pinJvm(...)` calls to all 16 failing test methods (including repo subdirs)
- [x] 3.2 Run BearCliAgentModeTest — target 0 failures

## Task 4: Verify already-fixed tests
- [x] 4.1 Confirm SingleFileIndexInferenceTest passes (already uses TestTargetPins.pinJvm)
- [x] 4.2 Confirm TargetSeamParityTest passes (already uses TestTargetPins.pinJvm)

## Task 5: Full verification
- [x] 5.1 Run full app test suite — 0 failures ✅
- [x] 5.2 Run full kernel test suite — 0 regressions ✅

## Task 6: Cleanup
- [x] 6.1 Delete throwaway automation scripts
