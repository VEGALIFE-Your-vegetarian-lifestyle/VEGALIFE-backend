# Docs Index

`docs/` is this project's permanent knowledge base — finished, accepted
material, not agent configuration (see `AGENTS.md`'s "Repository
knowledge structure"). Each subdirectory below should have its own
`index.md` once it has more than a couple of files, listing what's in
it and linking to each document, the same way this file and the indexes
under `.agents/` do.

| Directory | Contents |
|---|---|
| `docs/adrs/` | Accepted architecture decision records, once status moves to Accepted. |
| `docs/arch/` | Permanent architecture documentation — project structure, dependency matrix, design decisions. |
| `docs/apis/` | API reference documentation — endpoints as they actually exist. |
| `docs/brs/` | Business rules — constraints that exist independently of any one feature or PR. |
| `docs/feats/` | Feature specifications — product-level features with design, metrics, acceptance criteria. |

When adding a new document to any subdirectory here, add a line for it
to that subdirectory's own index (create one if it doesn't exist yet).
