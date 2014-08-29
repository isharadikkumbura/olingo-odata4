/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.olingo.server.core;

import org.apache.olingo.commons.api.ODataRuntimeException;
import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.format.ODataFormat;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataHttpHandler;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ODataResponse;
import org.apache.olingo.server.api.ODataServerError;
import org.apache.olingo.server.api.ODataTranslatedException;
import org.apache.olingo.server.api.processor.Processor;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.ODataSerializerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;

public class ODataHttpHandlerImpl implements ODataHttpHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ODataHttpHandlerImpl.class);

  private ODataHandler handler;
  private int split = 0;

  public ODataHttpHandlerImpl(final OData odata, final Edm edm) {
    handler = new ODataHandler(odata, edm);
  }

  @Override
  public void process(final HttpServletRequest request, final HttpServletResponse response) {
    ODataRequest odRequest = null;
    ODataResponse odResponse = null;
    try {
      odRequest = createODataRequest(request, split);
      odResponse = handler.process(odRequest);
      // ALL future methods after process must not throw exceptions!
    } catch (Exception e) {
      odResponse = handleException(e);
    }

    convertToHttp(response, odResponse);
  }

  @Override
  public void setSplit(int split) {
    this.split = split;
  }

  private ODataResponse handleException(Exception e) {
    ODataResponse resp = new ODataResponse();
    if (e instanceof ODataHandlerException) {
      ODataHandlerException exp = (ODataHandlerException) e;
      if (exp.getMessageKey() == ODataHandlerException.MessageKeys.AMBIGUOUS_XHTTP_METHOD) {
        resp.setStatusCode(HttpStatusCode.BAD_REQUEST.getStatusCode());
      } else if (exp.getMessageKey() == ODataHandlerException.MessageKeys.HTTP_METHOD_NOT_IMPLEMENTED) {
        resp.setStatusCode(HttpStatusCode.NOT_IMPLEMENTED.getStatusCode());
      }
    }
    
    ODataServerError error = new ODataServerError();
    if (e instanceof ODataTranslatedException) {
      error.setMessage(((ODataTranslatedException) e).getTranslatedMessage(Locale.ENGLISH).getMessage());
    } else {
      error.setMessage(e.getMessage());
    }

    try {
      ODataSerializer serializer = OData.newInstance().createSerializer(ODataFormat.JSON);
      resp.setContent(serializer.error(error));
    } catch (final ODataSerializerException e1) {
      // This should never happen but to be sure we have this catch here to prevent sending a stacktrace to a client.
      String responseContent =
          "{\"error\":{\"code\":null,\"message\":\"An unexpected exception occurred in the ODataHttpHandler during " +
              "error processing with message: " + e.getMessage() + "\"}}";
      resp.setContent(new ByteArrayInputStream(responseContent.getBytes()));
      resp.setStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode());
    }
    // Set header
    resp.setHeader(HttpHeader.CONTENT_TYPE, ContentType.APPLICATION_JSON.toContentTypeString());
    return resp;
  }

  static void convertToHttp(final HttpServletResponse response, final ODataResponse odResponse) {
    response.setStatus(odResponse.getStatusCode());

    for (Entry<String, String> entry : odResponse.getHeaders().entrySet()) {
      response.setHeader(entry.getKey(), entry.getValue());
    }

    InputStream input = odResponse.getContent();
    if (input != null) {
      OutputStream output;
      try {
        output = response.getOutputStream();
        byte[] buffer = new byte[1024];
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
          output.write(buffer, 0, n);
        }
      } catch (IOException e) {
        LOG.error(e.getMessage(), e);
        throw new ODataRuntimeException(e);
      } finally {
        if (input != null) {
          try {
            input.close();
          } catch (IOException e) {
            throw new ODataRuntimeException(e);
          }
        }
      }
    }
  }

  private ODataRequest createODataRequest(final HttpServletRequest httpRequest, final int split)
      throws ODataTranslatedException {
    try {
      ODataRequest odRequest = new ODataRequest();

      odRequest.setBody(httpRequest.getInputStream());
      extractHeaders(odRequest, httpRequest);
      extractMethod(odRequest, httpRequest);
      extractUri(odRequest, httpRequest, split);

      return odRequest;
    } catch (final IOException e) {
      throw new ODataSerializerException("An I/O exception occurred.", e,
          ODataSerializerException.MessageKeys.IO_EXCEPTION);
    }
  }

  static void extractMethod(final ODataRequest odRequest, final HttpServletRequest httpRequest)
      throws ODataTranslatedException {
    try {
      HttpMethod httpRequestMethod = HttpMethod.valueOf(httpRequest.getMethod());

      if (httpRequestMethod == HttpMethod.POST) {
        String xHttpMethod = httpRequest.getHeader(HttpHeader.X_HTTP_METHOD);
        String xHttpMethodOverride = httpRequest.getHeader(HttpHeader.X_HTTP_METHOD_OVERRIDE);

        if (xHttpMethod == null && xHttpMethodOverride == null) {
          odRequest.setMethod(httpRequestMethod);
        } else if (xHttpMethod == null && xHttpMethodOverride != null) {
          odRequest.setMethod(HttpMethod.valueOf(xHttpMethodOverride));
        } else if (xHttpMethod != null && xHttpMethodOverride == null) {
          odRequest.setMethod(HttpMethod.valueOf(xHttpMethod));
        } else {
          if (!xHttpMethod.equalsIgnoreCase(xHttpMethodOverride)) {
            throw new ODataHandlerException("Ambiguous X-HTTP-Methods",
                ODataHandlerException.MessageKeys.AMBIGUOUS_XHTTP_METHOD, xHttpMethod, xHttpMethodOverride);
          }
          odRequest.setMethod(HttpMethod.valueOf(xHttpMethod));
        }
      } else {
        odRequest.setMethod(httpRequestMethod);
      }
    } catch (IllegalArgumentException e) {
      throw new ODataHandlerException("Invalid HTTP method" + httpRequest.getMethod(),
          ODataHandlerException.MessageKeys.HTTP_METHOD_NOT_IMPLEMENTED, httpRequest.getMethod());
    }
  }

  static void extractUri(final ODataRequest odRequest, final HttpServletRequest httpRequest, final int split) {
    String rawRequestUri = httpRequest.getRequestURL().toString();

    String rawODataPath;
    if (!"".equals(httpRequest.getServletPath())) {
      int beginIndex;
      beginIndex = rawRequestUri.indexOf(httpRequest.getServletPath());
      beginIndex += httpRequest.getServletPath().length();
      rawODataPath = rawRequestUri.substring(beginIndex);
    } else if (!"".equals(httpRequest.getContextPath())) {
      int beginIndex;
      beginIndex = rawRequestUri.indexOf(httpRequest.getContextPath());
      beginIndex += httpRequest.getContextPath().length();
      rawODataPath = rawRequestUri.substring(beginIndex);
    } else {
      rawODataPath = httpRequest.getRequestURI();
    }

    String rawServiceResolutionUri;
    if (split > 0) {
      rawServiceResolutionUri = rawODataPath;
      for (int i = 0; i < split; i++) {
        int e = rawODataPath.indexOf("/", 1);
        if (-1 == e) {
          rawODataPath = "";
        } else {
          rawODataPath = rawODataPath.substring(e);
        }
      }
      int end = rawServiceResolutionUri.length() - rawODataPath.length();
      rawServiceResolutionUri = rawServiceResolutionUri.substring(0, end);
    } else {
      rawServiceResolutionUri = null;
    }

    String rawBaseUri = rawRequestUri.substring(0, rawRequestUri.length() - rawODataPath.length());

    odRequest.setRawQueryPath(httpRequest.getQueryString());
    odRequest.setRawRequestUri(rawRequestUri
        + (httpRequest.getQueryString() == null ? "" : "?" + httpRequest.getQueryString()));
    odRequest.setRawODataPath(rawODataPath);
    odRequest.setRawBaseUri(rawBaseUri);
    odRequest.setRawServiceResolutionUri(rawServiceResolutionUri);
  }

  static void extractHeaders(final ODataRequest odRequest, final HttpServletRequest req) {
    for (Enumeration<?> headerNames = req.getHeaderNames(); headerNames.hasMoreElements();) {
      String headerName = (String) headerNames.nextElement();
      List<String> headerValues = new ArrayList<String>();
      for (Enumeration<?> headers = req.getHeaders(headerName); headers.hasMoreElements();) {
        String value = (String) headers.nextElement();
        headerValues.add(value);
      }
      odRequest.addHeader(headerName, headerValues);
    }
  }

  @Override
  public void register(final Processor processor) {
    handler.register(processor);
  }
}
