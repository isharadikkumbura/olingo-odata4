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
package org.apache.olingo.odata4.client.core.edm.v4.annotation;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.math.BigInteger;

@JsonDeserialize(using = IsOfDeserializer.class)
public class IsOf extends AnnotatedDynExprConstruct {

  private static final long serialVersionUID = 6958304670385303776L;

  private String type;

  private String maxLength;

  private BigInteger precision;

  private BigInteger scale;

  private String srid;

  private DynExprConstructImpl value;

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public String getMaxLength() {
    return maxLength;
  }

  public void setMaxLength(final String maxLength) {
    this.maxLength = maxLength;
  }

  public BigInteger getPrecision() {
    return precision;
  }

  public void setPrecision(final BigInteger precision) {
    this.precision = precision;
  }

  public BigInteger getScale() {
    return scale;
  }

  public void setScale(final BigInteger scale) {
    this.scale = scale;
  }

  public String getSrid() {
    return srid;
  }

  public void setSrid(final String srid) {
    this.srid = srid;
  }

  public DynExprConstructImpl getValue() {
    return value;
  }

  public void setValue(final DynExprConstructImpl value) {
    this.value = value;
  }

}
