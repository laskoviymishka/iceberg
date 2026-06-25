# Collation Support — Spec Change Draft

**Status:** DRAFT / for discussion. Not yet a spec change.
**Dev list:** https://lists.apache.org/thread/zdvc0gmqjgws8whwxv0ztn7h1jqclzyk (Alexander Löser, Snowflake)
**Reference implementation:** `iceberg-go` branch `prototype/collation-support` (working code + Avro round-trip + version-gated pruning tests).

This draft proposes how to add **collations** to the Iceberg spec: an annotation on string
columns that changes how values are *compared and ordered* (case-insensitive, accent-insensitive,
locale-aware) without changing how they are *stored*. It folds in lessons from Databricks' Delta
implementation — most importantly, **store original values, not sort keys**, and **version the
bounds per file**.

---

## 1. Goals / non-goals

**Goals**
- Annotate string fields with a collation, exposed to engines through the schema.
- Define how to store bounds for collated columns so files can still be pruned.
- Comparison-only: data is returned exactly as inserted (`'appLE'` stays `'appLE'`).
- Collation-unaware engines must still read collated tables (case-sensitive, no wrong results).

**Non-goals (this draft)**
- User-defined collations.
- Restricting which codepoints a column may store.
- Sub-file (e.g. Parquet page) metadata representation.

---

## 2. Design overview

Two independent pieces:

1. **Schema annotation** — a string field carries an (unversioned) collation name.
2. **Collation bounds** — a new per-file `data_file` field carrying collation-aware min/max,
   stored as **original values** and tagged with the **collation implementation version** used to
   compute them.

The original UTF-8 `lower_bounds` / `upper_bounds` continue to be written for every column,
including collated ones. They are the byte-order min/max and remain valid for collation-unaware
engines (which read the column case-sensitively).

### Why original values, not sort keys

The proposal's first sketch stored ICU collation keys (sort keys) as the bounds. UCA/CLDR/ICU
collation keys are explicitly **not stable across versions** (UTS #10 §Non-Goals). Storing sort
keys couples every reader to one exact ICU/CLDR version, and a version bump silently invalidates
all stored metrics. Delta instead stores the **original string values** chosen as the min/max under
the collation order, and tags them with the version they were selected under. A reader compares
those original values with its own collator, and only trusts them when its version matches. This is
the approach this draft adopts.

Note the bounds are the min/max under **collation order**, which can be different *values* than the
byte-order min/max. For `{"Banana", "apple"}` under a case-insensitive collation the min is
`"apple"` and the max is `"Banana"`, whereas the byte-order min is `"Banana"` and max is `"apple"`.

---

## 3. Schema change: collation annotation on string fields

A string field may carry a collation. The collation **name** is stored in the schema **unversioned**
(so any compatible version can read the table); the version lives with the per-file bounds (§4).

Conceptually, on the field:

```json
{
  "id": 3,
  "name": "product_name",
  "required": true,
  "type": "string",
  "collation": "icu.en_US-ci"
}
```

- The Iceberg type remains `string`; collation is a sibling annotation, so a collation-unaware
  reader sees a normal string column and falls back to byte-order, case-sensitive comparison.
- Absence of the annotation means the default UTF-8 byte-order collation.
- Adding / changing / dropping the annotation is allowed but has cost: it invalidates collation
  bounds (which must be recomputed/backfilled); the new comparison semantics take effect
  immediately.

**Open:** the exact JSON key (`collation` vs `collation_spec`) and the collation **identifier
grammar** (provider + name, e.g. `icu.en_US-ci`, plus optional case/accent/trim/casing modifiers)
need a formal definition. See §7.

---

## 4. Manifest change: `data_file.collation_bounds`

A new optional field on `data_file` (v3+), keyed by source field id, holding collation-aware
original-value bounds tagged with the collation and version they were computed under.

Proposed `data_file` table row (matching the existing `lower_bounds` / `upper_bounds` style):

| v1 | v2 | v3 | Field | Type | Notes |
|----|----|----|-------|------|-------|
|    |    | _optional_ | **`<reserved>  collation_bounds`** | `map<int, list<collation_bound>>` | Map from column id to a list of collation-aware bounds, one entry per collation version the file carries bounds for [a] |

where `collation_bound` is a struct:

| Field | Type | Notes |
|-------|------|-------|
| `collation` | `string` | Collation identifier, e.g. `icu.en_US-ci` |
| `version` | `string` | Collation implementation version the bounds were selected under (e.g. an ICU collator version) |
| `lower_bound` | `binary` | Original value (serialized as for `lower_bounds`) that is the **collation-order** minimum [b] |
| `upper_bound` | `binary` | Original value that is the **collation-order** maximum [b] |

[a] A `list` lets one file carry bounds for several versions at once, so a table can be read by
engines pinned to different versions during a rollout (this is Delta's `validForCollations`,
collapsed here to one version per entry). A reader selects the entry whose `collation` + `version`
match its own.

[b] Original values, not sort keys (§2). Bounds use the same binary serialization and truncation
rules as `lower_bounds` / `upper_bounds`, except that for an upper bound, truncation must append the
highest value **in collation order** (not the byte-order successor).

### Field-id reservation

The reference implementation uses an experimental range (9000–9006) **only** to avoid colliding
with reserved ids while prototyping. In particular, **field id 146 is already reserved for the v4
`content_stats` struct**, and the spec reserves ids globally across versions, so collation bounds
must **not** reuse 146 or the `10_000 + 200 * field-id` `content_stats` range. This proposal needs
an officially reserved range for `collation_bounds`, its map key/value, and the `collation_bound`
sub-fields.

**Open:** whether collation bounds should instead be expressed inside the v4 `content_stats`
typed-stats framework rather than as a standalone map. If `content_stats` becomes the home for all
field metrics in v4, collation bounds may belong there as a typed per-field stat. This draft keeps
them standalone so the feature can land on v3 independently of v4.

---

## 5. Reader and writer rules

**Writer (collation-aware)**
- MUST continue to write UTF-8 `lower_bounds` / `upper_bounds` for collated columns (byte-order
  min/max), so collation-unaware engines keep working.
- SHOULD write `collation_bounds`: select min/max under the collation order, store the original
  values, and tag them with the collation identifier and the exact version used.
- If the writer does not support the column's collation/version, it MUST NOT write
  `collation_bounds` for that column (but still writes UTF-8 bounds).

**Reader (collation-aware)**
- MAY use a `collation_bounds` entry for pruning **only** when both the collation identifier and
  version match the column's collation as resolved by the reader. Otherwise it MUST NOT prune using
  it.
- MUST NOT use the UTF-8 `lower_bounds` / `upper_bounds` to prune ordering/equality/prefix
  predicates on a collated column — byte order disagrees with collation order (`'a' > 'B'` by bytes
  but `'a' < 'B'` case-insensitively). This is the bounds-pruning analogue of disabling Parquet
  predicate pushdown for collated columns. When no valid `collation_bounds` entry exists, the file
  is conservatively kept.

**Reader (collation-unaware)**
- Ignores the `collation` annotation and the `collation_bounds` field (an unknown optional Avro
  field), reads the column as a plain UTF-8 string, and compares/sorts case-sensitively using the
  existing UTF-8 bounds. Results differ from a collation-aware engine, but no data is corrupted.

**Recommended fallback order** when a reader can't honor the exact collation/version: (1) read
case-sensitive using UTF-8 bounds, (2) use an older supported collator version *without* writing new
collated bounds, or (3) fail — engines should document and prefer a consistent order to avoid
divergent results.

---

## 6. Versioning and compatibility

- **Schema** stores the collation **name only** (unversioned) — any compatible version can read.
- **`collation_bounds`** are **versioned per file**; readers prune only on an exact match. A version
  bump doesn't corrupt anything — old bounds are simply ignored by readers on a different version,
  costing pruning effectiveness until bounds are rewritten (e.g. via compaction / `ANALYZE`).
- **Table feature:** collations can be a writer-mostly feature — they don't change data encoding
  (always UTF-8), and unaware readers degrade to case-sensitive. Dropping the feature is a
  metadata-only change (set columns back to the default collation, remove `collation_bounds`).

---

## 7. Open questions for the thread

1. **Identifier grammar.** Provider + name (`icu.en_US-ci`, `spark.utf8_lcase`) vs the proposal's
   `en_US-ci` ICU-only form. A provider/namespace layer avoids collisions and admits non-ICU
   collations (Spark's `UTF8_LCASE`). Formal grammar for locale + ci/cs + ai/as + trim + casing.
2. **Single entry vs list** (`validForCollations`) per column in `collation_bounds` — the list
   enables multi-version coexistence during rollouts.
3. **Field-id range**, and whether to fold into v4 `content_stats` instead of a standalone field.
4. **Pinned baseline version.** The proposal suggested pinning UCA/CLDR/ICU to an Iceberg version
   for interop; Delta argues a single shared version across engines is unrealistic, hence per-file
   versioning. Which model?
5. **Operational surface** to scope: partition transforms on collated columns (collation-equal but
   byte-distinct values land in different partitions), sort-order semantics, equality deletes under
   collation, `StartsWith` semantics (byte-prefix vs collation-aware), and nested-type (list/map
   element) collation.

---

## 8. Differences from the current proposal

The prototype intentionally diverges from the current proposal in a few places, mostly to adopt
Delta's hard-won lessons. These are the points to settle on the dev list.

| Aspect | Current proposal | This prototype (Delta-aligned) | Why |
|--------|------------------|--------------------------------|-----|
| **Bounds encoding** | Collation **keys** (sort keys) | **Original values** | Sort keys aren't stable across UCA/CLDR/ICU versions (UTS #10); storing them couples every reader to one exact version and a bump silently invalidates all metrics |
| **Where bounds live** | A hidden **pseudo-field** (`collation_metrics_id`) reusing the existing `lower_bounds`/`upper_bounds` maps | A dedicated **`data_file.collation_bounds`** field carrying value + collation + version | Self-describing; no overloading of the byte-order bounds maps; room for a per-version list |
| **Version model** | Pin one UCA/DUCET/CLDR (or ICU) version **per Iceberg version** | **Per-file** version tag + exact-match read gate | Requiring all engines to converge on one version is unrealistic in a multi-engine lake; per-file versioning degrades gracefully instead of breaking |
| **Where the version lives** | In the schema (`icu_collator_version` in `collation_spec`, per column) | Schema name is **unversioned**; version travels with the **per-file** bounds | Lets any compatible version read the table; only pruning needs the exact version |
| **Provider** | ICU only | Provider-qualified id (`icu.en_US-ci`, room for `spark.utf8_lcase`) | Spark ships non-ICU collations (`UTF8_LCASE`) that are widely used; a namespace avoids future name collisions |
| **UTF-8 bounds on collated columns** | UTF-8 bounds "should" be written; "must not" be used for collation-aware pruning | UTF-8 bounds **must** be written, and an engine **must not** prune collation predicates with them | Backward-compat for unaware engines depends on the bounds existing; byte-order pruning of a collated predicate is silently wrong (`'a' > 'B'` by bytes, `'a' < 'B'` case-insensitively) |

Smaller items also worth nailing down: a recommended engine **fallback order** when the exact
collation/version isn't supported (case-sensitive vs older collator vs fail), and a **formal grammar**
for the collation specifier.

## 9. Reference implementation status (`iceberg-go`)

The `prototype/collation-support` branch implements, end to end:

- A `collation` package: specifier parsing + comparison/sort-key via `golang.org/x/text/collate`
  (CLDR/UCA).
- `StringType` collation annotation with `collation_spec` schema-JSON round-trip (NestedField level).
- `CollationBoundEntry` (original values + version) + `ComputeCollatedBounds` (collation-order
  min/max), persisted in a prototype `data_file.collation_bounds` Avro field with a full
  manifest write/read round-trip.
- Version-gated, collator-based data-file pruning, with tests proving: byte-order vs collation-order
  divergence, correct pruning under a matching version, and conservative keep on version mismatch /
  unversioned / strict-evaluator / row-group paths.

Known prototype gaps (documented in code): writer auto-compute of bounds, the `validForCollations`
list, nested-type collation round-trip, collation schema evolution, partition/equality-delete
handling, and an officially reserved field-id range.
