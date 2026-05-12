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
package org.apache.iceberg.rest.requests;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.rest.RESTRequest;

/**
 * A REST request to add and/or remove labels on a table or its columns.
 *
 * <p>Subjects are inferred from shape: entries without {@code field-id} target the table; entries
 * with {@code field-id} target the column. This aligns with the read-side split shape returned by
 * {@link org.apache.iceberg.rest.responses.LoadLabelsResponse}.
 *
 * <p>Labels are catalog-scoped enrichment and do NOT modify {@code metadata.json}. The catalog
 * applies updates atomically: if any entry targets a catalog-managed (read-only) key the entire
 * request is rejected with {@code 403 LabelKeyNotWritable} before any mutation.
 */
public class UpdateLabelsRequest implements RESTRequest {

  private List<Entry> updates;
  private List<Entry> removals;

  public UpdateLabelsRequest() {
    // Required for Jackson deserialization.
  }

  private UpdateLabelsRequest(List<Entry> updates, List<Entry> removals) {
    this.updates = updates;
    this.removals = removals;
    validate();
  }

  @Override
  public void validate() {
    for (Entry update : updates()) {
      Preconditions.checkArgument(update.key() != null, "Invalid update: key is null");
      Preconditions.checkArgument(update.value() != null, "Invalid update: value is null");
    }
    for (Entry removal : removals()) {
      Preconditions.checkArgument(removal.key() != null, "Invalid removal: key is null");
    }
  }

  public List<Entry> updates() {
    return updates == null ? ImmutableList.of() : updates;
  }

  public List<Entry> removals() {
    return removals == null ? ImmutableList.of() : removals;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("updates", updates)
        .add("removals", removals)
        .toString();
  }

  /**
   * A label entry. {@code fieldId} is null for table-scoped entries and set for column-scoped
   * entries. {@code value} is null for removals.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Entry {
    @JsonProperty("field-id")
    private Integer fieldId;

    private String key;
    private String value;

    public Entry() {
      // Required for Jackson deserialization.
    }

    private Entry(Integer fieldId, String key, String value) {
      this.fieldId = fieldId;
      this.key = key;
      this.value = value;
    }

    public Integer fieldId() {
      return fieldId;
    }

    public String key() {
      return key;
    }

    public String value() {
      return value;
    }

    public boolean isColumnScoped() {
      return fieldId != null;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Entry)) {
        return false;
      }
      Entry that = (Entry) o;
      return Objects.equals(fieldId, that.fieldId)
          && Objects.equals(key, that.key)
          && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(fieldId, key, value);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("field-id", fieldId)
          .add("key", key)
          .add("value", value)
          .toString();
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final ImmutableList.Builder<Entry> updatesBuilder = ImmutableList.builder();
    private final ImmutableList.Builder<Entry> removalsBuilder = ImmutableList.builder();

    private Builder() {}

    public Builder update(String key, String value) {
      Preconditions.checkNotNull(key, "Invalid update key: null");
      Preconditions.checkNotNull(value, "Invalid update value for key [%s]: null", key);
      updatesBuilder.add(new Entry(null, key, value));
      return this;
    }

    public Builder update(int fieldId, String key, String value) {
      Preconditions.checkNotNull(key, "Invalid update key: null");
      Preconditions.checkNotNull(value, "Invalid update value for key [%s]: null", key);
      updatesBuilder.add(new Entry(fieldId, key, value));
      return this;
    }

    public Builder remove(String key) {
      Preconditions.checkNotNull(key, "Invalid removal key: null");
      removalsBuilder.add(new Entry(null, key, null));
      return this;
    }

    public Builder remove(int fieldId, String key) {
      Preconditions.checkNotNull(key, "Invalid removal key: null");
      removalsBuilder.add(new Entry(fieldId, key, null));
      return this;
    }

    public UpdateLabelsRequest build() {
      return new UpdateLabelsRequest(updatesBuilder.build(), removalsBuilder.build());
    }
  }
}
