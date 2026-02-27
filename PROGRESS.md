# Standardise exception handling — replace catch-and-swallow patterns

**Labels:** code-quality, refactoring

**Description**
Multiple `try/catch` blocks return empty or default values without logging
the cause. Replace with proper error propagation or explicit logging.

**Tasks**
- [ ] Audit all `catch` blocks across web and receipt-parser modules
- [ ] Replace silent swallowing with proper logging at WARN/ERROR level
- [ ] Narrow broad `catch(Exception e)` to specific exception types
- [ ] Add tests for error handling paths

**Acceptance criteria**
- No catch block silently swallows exceptions
- Errors are logged with sufficient context for debugging
