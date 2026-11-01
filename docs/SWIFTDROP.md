# SwiftDrop — System Design & Architecture Pack

> **Enterprise Application Development (EAD) Project**
> **Team SwiftDrop**

---

## This document has moved

The design pack now lives in **[`docs/DESIGN.md`](./DESIGN.md)**, which carries all six
assignment deliverables in one place, with every diagram verified against the
implementation.

| Looking for | Now in |
|---|---|
| Use case diagrams (customer, merchant, courier, dispatcher) | [`DESIGN.md` §1](./DESIGN.md#1-use-case-diagrams) |
| ER diagram and the data-ownership model | [`DESIGN.md` §2](./DESIGN.md#2-entity-relationship-model) |
| Architecture proposal, layers and justification | [`DESIGN.md` §3](./DESIGN.md#3-architecture-proposal) |
| Integration patterns and event topology | [`DESIGN.md` §4](./DESIGN.md#4-integration-design) |
| Sequence and activity diagrams for the key flows | [`DESIGN.md` §5](./DESIGN.md#5-sequence-and-activity-diagrams) |
| SOLID, DRY, Strategy, Repository, Observer | [`DESIGN.md` §6](./DESIGN.md#6-design-patterns-and-principles) |
| Known defects found while verifying the design | [`DESIGN.md` §7](./DESIGN.md#7-known-gaps) |
| **Per-service design notes** — notification, identity, dispatch, pricing | [`DESIGN.md` §8](./DESIGN.md#8-service-level-design-notes) |

Diagram sources (`.mmd`, `.puml`) and rendered images (`.png`, `.svg`) are in
[`docs/diagrams/`](./diagrams/).

The per-service sections previously held here were moved into `DESIGN.md` §8 with
their authorship intact. A few statements were corrected against the running code
during the move and are marked `[corrected]` in place.
