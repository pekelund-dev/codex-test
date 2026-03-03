# Standardise exception handling — replace catch-and-swallow patterns

**Labels:** code-quality, refactoring

**Description**
Multiple `try/catch` blocks return empty or default values without logging
the cause. Replace with proper error propagation or explicit logging.

**Tasks**
- [x] Audit all `catch` blocks across web and receipt-parser modules
- [x] Replace silent swallowing with proper logging at WARN level
- [x] Narrow broad `catch(Exception e)` to specific exception types

**Acceptance criteria**
- [x] No catch block silently swallows exceptions
- [x] Errors are logged with sufficient context for debugging

**Completion summary**
- `CategoryStatisticsService`: narrowed `catch(Exception e)` to `catch(DateTimeParseException e)` in 2 date-filter lambdas, added `log.warn()`. Added `log.warn()` to `catch(NumberFormatException e)`.
- `StatisticsController`: narrowed `catch(Exception e)` to `catch(DateTimeException e)` in `resolveMonthName`, added `LOGGER.warn()`, added Logger field.
- All 77 Java tests pass.
