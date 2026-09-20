# Reference: Performance Review

## When this applies

The diff touches a hot path, a loop over potentially large data, a
database query, or anything else where latency or resource use under
real load matters — not for code that clearly only ever runs on tiny,
fixed-size input.

## Checklist

- **N+1 queries**: does a loop issue one DB/network call per item
  instead of batching?
- **Algorithmic complexity**: does this scale linearly with realistic
  input size, or does it degrade badly (nested loops over the same
  large collection, repeated full scans)?
- **Caching**: is anything computed or fetched repeatedly that could be
  cached or memoized — and if a cache is added, does it have a clear
  invalidation story?
- **Unnecessary work in a hot path**: logging, serialization, or
  allocation happening on every call that could be lazy or skipped.
- **Blocking calls**: does this introduce a synchronous/blocking call
  in a path that's supposed to be async or concurrent?

## What good coverage looks like

- The change includes or references a benchmark/measurement for
  anything claiming to be a performance improvement — not just an
  assertion that it's faster.

## Common mistakes to avoid

- Optimizing a path that isn't actually hot, at the cost of readability.
- Adding a cache without a plan for staleness or invalidation.
- Trusting "it works fine locally" as evidence for production-scale
  performance.
