/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iceberg.catalog;

import java.util.List;
import java.util.Map;

/**
 * Optional catalog capability for reading and writing per-table labels.
 *
 * <p>Labels are catalog-scoped key/value annotations on tables and columns. They do NOT modify
 * {@code metadata.json}, do NOT create snapshots, and are not part of Iceberg table state. They are
 * API-layer enrichment maintained by the catalog.
 *
 * <p>Catalogs that implement this interface MUST advertise {@code V1_LOAD_LABELS} (and
 * {@code V1_UPDATE_LABELS} for the write path) in the {@code endpoints} array returned by
 * {@code GET /v1/config}. Catalogs that implement reads but not writes are conformant.
 *
 * <p>Write semantics:
 *
 * <ul>
 *   <li>All updates and removals in a single {@link #updateLabels} call are applied atomically.
 *   <li>Catalog-managed (read-only) keys MUST be rejected with {@code 403 LabelKeyNotWritable}
 *       before any mutation.
 *   <li>Column-scoped entries MUST use {@code field-id}; engines resolve column names to field-ids
 *       at request-construction time.
 *   <li>{@code metadata.json} is not modified. No snapshot is created.
 * </ul>
 */
public interface SupportsLabels {

  /** Read the current label set for a table. */
  Labels loadLabels(TableIdentifier identifier);

  /**
   * Apply updates and removals atomically.
   *
   * @throws org.apache.iceberg.exceptions.ForbiddenException if any entry targets a
   *     catalog-managed key
   * @throws org.apache.iceberg.exceptions.NoSuchTableException if the table does not exist
   */
  Labels updateLabels(TableIdentifier identifier, List<Entry> updates, List<Entry> removals);

  /**
   * Snapshot of a table's labels in split shape.
   *
   * <p>{@code table()} returns the flat table-level k/v map. {@code columns()} returns one entry
   * per column that carries any labels, keyed by Iceberg field-id (stable across schema
   * evolution).
   */
  interface Labels {
    Map<String, String> table();

    List<ColumnLabels> columns();
  }

  /** Per-column label entry. */
  interface ColumnLabels {
    int fieldId();

    Map<String, String> labels();
  }

  /** Update or removal entry. {@code fieldId} is null for table-scoped entries. */
  interface Entry {
    Integer fieldId();

    String key();

    /** Null for removals. */
    String value();
  }
}
