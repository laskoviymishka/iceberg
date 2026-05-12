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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.rest.RequestResponseTestBase;
import org.junit.jupiter.api.Test;

public class TestLoadLabelsResponse extends RequestResponseTestBase<LoadLabelsResponse> {

  @Override
  public String[] allFieldsFromSpec() {
    return new String[] {"labels", "column-labels"};
  }

  @Override
  public LoadLabelsResponse createExampleInstance() {
    return LoadLabelsResponse.builder()
        .label("domain", "customer")
        .label("tier", "gold")
        .columnLabels(7, ImmutableMap.of("pii-type", "email"))
        .build();
  }

  @Override
  public void assertEquals(LoadLabelsResponse actual, LoadLabelsResponse expected) {
    assertThat(actual.labels()).isEqualTo(expected.labels());
    assertThat(actual.columnLabels()).isEqualTo(expected.columnLabels());
  }

  @Override
  public LoadLabelsResponse deserialize(String json) throws JsonProcessingException {
    LoadLabelsResponse response = mapper().readValue(json, LoadLabelsResponse.class);
    response.validate();
    return response;
  }

  @Test
  public void testRoundTripSerDe() throws JsonProcessingException {
    String fullJson =
        "{\"labels\":{\"domain\":\"customer\",\"tier\":\"gold\"},"
            + "\"column-labels\":[{\"field-id\":7,\"labels\":{\"pii-type\":\"email\"}}]}";
    assertRoundTripSerializesEquallyFrom(fullJson, createExampleInstance());

    // Table-level only (column-labels empty array still emitted by builder).
    String tableOnly = "{\"labels\":{\"domain\":\"customer\"},\"column-labels\":[]}";
    assertRoundTripSerializesEquallyFrom(
        tableOnly, LoadLabelsResponse.builder().label("domain", "customer").build());

    // Empty response (no labels at all).
    String empty = "{\"labels\":{},\"column-labels\":[]}";
    assertRoundTripSerializesEquallyFrom(empty, LoadLabelsResponse.builder().build());
  }
}
