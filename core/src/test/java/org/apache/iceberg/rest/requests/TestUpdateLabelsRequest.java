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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.iceberg.rest.RequestResponseTestBase;
import org.junit.jupiter.api.Test;

public class TestUpdateLabelsRequest extends RequestResponseTestBase<UpdateLabelsRequest> {

  @Override
  public String[] allFieldsFromSpec() {
    return new String[] {"updates", "removals"};
  }

  @Override
  public UpdateLabelsRequest createExampleInstance() {
    return UpdateLabelsRequest.builder()
        .update("domain", "customer")
        .update(7, "pii-type", "email")
        .remove("tier")
        .remove(7, "sensitivity")
        .build();
  }

  @Override
  public void assertEquals(UpdateLabelsRequest actual, UpdateLabelsRequest expected) {
    assertThat(actual.updates()).isEqualTo(expected.updates());
    assertThat(actual.removals()).isEqualTo(expected.removals());
  }

  @Override
  public UpdateLabelsRequest deserialize(String json) throws JsonProcessingException {
    UpdateLabelsRequest request = mapper().readValue(json, UpdateLabelsRequest.class);
    request.validate();
    return request;
  }

  @Test
  public void testRoundTripSerDe() throws JsonProcessingException {
    // Mixed table + column updates and removals — the canonical shape.
    String fullJson =
        "{\"updates\":["
            + "{\"key\":\"domain\",\"value\":\"customer\"},"
            + "{\"field-id\":7,\"key\":\"pii-type\",\"value\":\"email\"}"
            + "],"
            + "\"removals\":["
            + "{\"key\":\"tier\"},"
            + "{\"field-id\":7,\"key\":\"sensitivity\"}"
            + "]}";
    assertRoundTripSerializesEquallyFrom(fullJson, createExampleInstance());

    // Only table-scoped updates.
    String tableOnly = "{\"updates\":[{\"key\":\"domain\",\"value\":\"customer\"}],\"removals\":[]}";
    assertRoundTripSerializesEquallyFrom(
        tableOnly, UpdateLabelsRequest.builder().update("domain", "customer").build());

    // Only column-scoped removal.
    String columnRemoval = "{\"updates\":[],\"removals\":[{\"field-id\":7,\"key\":\"pii-type\"}]}";
    assertRoundTripSerializesEquallyFrom(
        columnRemoval, UpdateLabelsRequest.builder().remove(7, "pii-type").build());

    // Empty request.
    String empty = "{\"updates\":[],\"removals\":[]}";
    assertRoundTripSerializesEquallyFrom(empty, UpdateLabelsRequest.builder().build());
  }
}
