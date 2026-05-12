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
package org.apache.iceberg.rest.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.rest.RESTResponse;

/**
 * Response shape for label read/write operations.
 *
 * <p>The catalog returns the full post-operation label set in split shape: {@code labels} is the
 * flat key/value map at the table level; {@code column-labels} is the array of per-column entries
 * keyed by Iceberg {@code field-id} (stable across schema evolution).
 *
 * <p>This is the same shape served by {@code GET /v1/{prefix}/namespaces/{ns}/tables/{tbl}/labels}
 * (label reads) and returned by {@code POST .../labels} (label writes).
 */
public class LoadLabelsResponse implements RESTResponse {

  private Map<String, String> labels;

  @JsonProperty("column-labels")
  private List<ColumnLabels> columnLabels;

  public LoadLabelsResponse() {
    // Required for Jackson deserialization.
  }

  private LoadLabelsResponse(Map<String, String> labels, List<ColumnLabels> columnLabels) {
    this.labels = labels;
    this.columnLabels = columnLabels;
    validate();
  }

  @Override
  public void validate() {
    for (ColumnLabels entry : columnLabels()) {
      Preconditions.checkArgument(entry != null, "Invalid column-labels entry: null");
      Preconditions.checkArgument(
          entry.labels() != null, "Invalid column-labels entry: missing labels map");
    }
  }

  public Map<String, String> labels() {
    return labels == null ? ImmutableMap.of() : labels;
  }

  public List<ColumnLabels> columnLabels() {
    return columnLabels == null ? ImmutableList.of() : columnLabels;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("labels", labels)
        .add("column-labels", columnLabels)
        .toString();
  }

  /** Per-column label entry, keyed by Iceberg field-id. */
  public static class ColumnLabels {
    @JsonProperty("field-id")
    private int fieldId;

    private Map<String, String> labels;

    public ColumnLabels() {
      // Required for Jackson deserialization.
    }

    private ColumnLabels(int fieldId, Map<String, String> labels) {
      this.fieldId = fieldId;
      this.labels = labels;
    }

    public int fieldId() {
      return fieldId;
    }

    public Map<String, String> labels() {
      return labels == null ? ImmutableMap.of() : labels;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ColumnLabels)) {
        return false;
      }
      ColumnLabels that = (ColumnLabels) o;
      return fieldId == that.fieldId && Objects.equals(labels, that.labels);
    }

    @Override
    public int hashCode() {
      return Objects.hash(fieldId, labels);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("field-id", fieldId)
          .add("labels", labels)
          .toString();
    }

    public static ColumnLabels of(int fieldId, Map<String, String> labels) {
      Preconditions.checkArgument(fieldId >= 0, "Invalid field-id: %s (must be >= 0)", fieldId);
      return new ColumnLabels(fieldId, labels == null ? ImmutableMap.of() : labels);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final ImmutableMap.Builder<String, String> labelsBuilder = ImmutableMap.builder();
    private final ImmutableList.Builder<ColumnLabels> columnLabelsBuilder = ImmutableList.builder();

    private Builder() {}

    public Builder label(String key, String value) {
      Preconditions.checkNotNull(key, "Invalid label key: null");
      Preconditions.checkNotNull(value, "Invalid label value for key [%s]: null", key);
      labelsBuilder.put(key, value);
      return this;
    }

    public Builder labels(Map<String, String> labels) {
      Preconditions.checkNotNull(labels, "Invalid labels map: null");
      labelsBuilder.putAll(labels);
      return this;
    }

    public Builder columnLabels(int fieldId, Map<String, String> labels) {
      columnLabelsBuilder.add(ColumnLabels.of(fieldId, labels));
      return this;
    }

    public LoadLabelsResponse build() {
      return new LoadLabelsResponse(labelsBuilder.build(), columnLabelsBuilder.build());
    }
  }
}
