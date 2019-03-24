import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.Pair;

import com.fasterxml.jackson.databind.*;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource.Builder;
import com.sun.jersey.api.client.filter.ClientFilter;

import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response.Status.Family;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Scanner;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;

import java.nio.charset.StandardCharsets;

public class VCApiClient extends ApiClient {

  public VCApiClient() {
    super();
    ObjectMapper om = this.getObjectMapper();
    om.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
  }

  /**
   *
   * This extends the base io.swagger.client.ApiClient implementation by adding
   * a filter for processing cookies.
   *
   */
  public ApiClient rebuildHttpClient() {
    super.rebuildHttpClient();
    Client client = this.getHttpClient();
    client.addFilter(this.getCookieFilter());
    return this;
  }

  public ClientFilter getCookieFilter() {
    return new ClientFilter() {

      private ArrayList<Object> cookies = null;

      @Override
      public ClientResponse handle(ClientRequest request) throws ClientHandlerException {

        boolean isAuthRequest = request.getURI().getPath().contains("/login/");
        boolean authenticated = Boolean.FALSE;
        String authError = null;

        if ( !isAuthRequest && cookies != null )
          request.getHeaders().put("Cookie", cookies);

        ClientResponse response = getNext().handle(request);
        if ( isAuthRequest ) {
          List<NewCookie> responseCookies = response.getCookies();
          cookies = new ArrayList<Object>();
          for ( NewCookie c : responseCookies ) {
            String cookieName = c.getName();
            if ( cookieName.equals("velocloud.session") ) {
              cookies.add(c);
              authenticated = Boolean.TRUE;
            } else if ( cookieName.equals("velocloud.message") && c.getValue().length() > 0 ) {
              authError = c.getValue();
            }
          }
          if ( !authenticated ) {
            if ( authError == null )
              authError = "Authentication Error";
            throw new ClientHandlerException(authError);
          }
        }
        return response;
      }
    };
  }

  public String convertStreamToString(java.io.InputStream stream) {
    Scanner scanner = new Scanner(stream).useDelimiter("\\A");
    String result = scanner.hasNext() ? scanner.next() : "";
    scanner.close();
    return result;
  }

  public void generateApiException(ClientResponse r, String message) throws ApiException {
    String respBody = null;
    if (r.hasEntity()) {
      try {
        respBody = r.getEntity(String.class);
        message = respBody;
      } catch (RuntimeException e) {
        // e.printStackTrace();
      }
    }
    throw new ApiException(
      r.getStatusInfo().getStatusCode(),
      message,
      r.getHeaders(),
      respBody);
  }

  /**
   * Build full URL by concatenating base path, the given sub path and query parameters.
   *
   * @param path The sub path
   * @param queryParams The query parameters
   * @return The full URL
   */
  protected String buildUrl(String path, List<Pair> queryParams) {
    final StringBuilder url = new StringBuilder();
    url.append(this.getBasePath()).append(path);

    if (queryParams != null && !queryParams.isEmpty()) {
      // support (constant) query string in `path`, e.g. "/posts?draft=1"
      String prefix = path.contains("?") ? "&" : "?";
      for (Pair param : queryParams) {
        if (param.getValue() != null) {
          if (prefix != null) {
            url.append(prefix);
            prefix = null;
          } else {
            url.append("&");
          }
          String value = parameterToString(param.getValue());
          url.append(escapeString(param.getName())).append("=").append(escapeString(value));
        }
      }
    }

    return url.toString();
  }

  protected ClientResponse getAPIResponse(String path, String method, List<Pair> queryParams, Object body, Map<String, String> headerParams, Map<String, Object> formParams, String accept, String contentType, String[] authNames) throws ApiException {
    if (body != null && !formParams.isEmpty()) {
      throw new ApiException(500, "Cannot have body and form params");
    }

    final String url = this.buildUrl(path, queryParams);
    Builder builder;
    if (accept == null) {
      builder = this.getHttpClient().resource(url).getRequestBuilder();
    } else {
      builder = this.getHttpClient().resource(url).accept(accept);
    }

    for (String key : headerParams.keySet()) {
      builder = builder.header(key, headerParams.get(key));
    }

    ClientResponse response = null;

    if ("GET".equals(method)) {
      response = (ClientResponse) builder.get(ClientResponse.class);
    } else if ("POST".equals(method)) {
      response = builder.type(contentType).post(ClientResponse.class, serialize(body, contentType, formParams));
    } else if ("PUT".equals(method)) {
      response = builder.type(contentType).put(ClientResponse.class, serialize(body, contentType, formParams));
    } else if ("DELETE".equals(method)) {
      response = builder.type(contentType).delete(ClientResponse.class, serialize(body, contentType, formParams));
    } else if ("PATCH".equals(method)) {
      response = builder.type(contentType).header("X-HTTP-Method-Override", "PATCH").post(ClientResponse.class, serialize(body, contentType, formParams));
    }
    else {
      throw new ApiException(500, "unknown method type " + method);
    }
    return response;
  }


  /**
   * Invoke API by sending HTTP request with the given options.
   *
   * @param <T> Type
   * @param path The sub-path of the HTTP URL
   * @param method The request method, one of "GET", "POST", "PUT", and "DELETE"
   * @param queryParams The query parameters
   * @param body The request body object - if it is not binary, otherwise null
   * @param headerParams The header parameters
   * @param formParams The form parameters
   * @param accept The request's Accept header
   * @param contentType The request's Content-Type header
   * @param authNames The authentications to apply
   * @param returnType Return type
   * @return The response body in type of string
   * @throws ApiException API exception
   */
   public <T> T invokeAPI(String path, String method, List<Pair> queryParams, Object body, Map<String, String> headerParams, Map<String, Object> formParams, String accept, String contentType, String[] authNames, GenericType<T> returnType) throws ApiException {

    ClientResponse response = null;
    try {
      response = this.getAPIResponse(path, method, queryParams, body, headerParams, formParams, accept, contentType, authNames);
    } catch (ClientHandlerException che) {
      // Pass on auth errors as API exceptions
      throw new ApiException(401, che.getMessage());
    }

    InputStream responseStream = response.getEntityInputStream();
    String responseString = convertStreamToString(responseStream);

    // We explicitly check for the case where the response is null, since
    // the call to getEntity that we use to deserialize Java objects makes it
    // impossible to tell whether a response is null (b/c it returns null both
    // in the case where no object could be deserialized and in the case where
    // the response is actually the JSON value <null>)
    if (responseString.equals("null"))
      return null;

    // This API client uses RFC3369-formatted timestamps, while the VeloCloud
    // API produces timestamps that are not compatible with the RFC3369 standard.
    // In order to ensure Java can process timestamps produced by the API, we
    // dynamically replace API timestamps with RFC3369-compiant ones on each API
    // call, prior to deserializing the JSON responses.
    //
    // This follows a two-step replacement process. First we eliminate the
    // "zero" timestamps used as defaults for some database fields, replacing
    // these with the Unix time day-zero timestamps. Then we convert all
    // timestamps to an RFC3369-compatible format and replace the response
    // stream.
    responseString = responseString.replaceAll("0000-00-00 00:00:00","1970-01-01 00:00:00");
    responseString = responseString.replaceAll("([0-9]{4})-([0-9]{2})-([0-9]{2}) ([0-9]{2}):([0-9]{2}):([0-9]{2})","$1-$2-$3T$4:$5:$6+00:00");

    InputStream newStream = new ByteArrayInputStream(responseString.getBytes(StandardCharsets.UTF_8));
    response.setEntityInputStream(newStream);
    response.bufferEntity();

    String UNKNOWN_ERROR_MESSAGE = "VCApiClient Unknown Error.";
    T resultEntity = null;
    if(response.getStatusInfo().getStatusCode() == ClientResponse.Status.NO_CONTENT.getStatusCode()) {
      // Return null on HTTP 204
      return null;
    } else if (response.getStatusInfo().getFamily() == Family.SUCCESSFUL) {
      if (returnType == null)
        return null;
      else {
        try {
          // Attempt to deserialize an entity of type `returnType`
          resultEntity = response.getEntity(returnType);
        } catch (Exception e) {
          //System.out.println("Exception in getEntity:");
          //System.out.println(e);
        }
        // NOTE: When exception logging is enabled above, it may be observed
        // that older versions of the VCO (<3.0) cause deserialization
        // errors where large integers cannot be deserialized as ints.
        // This is due to the style in which REST errors were returned in
        // earlier VCO versions (wrapped in JSON-RPC responses). To mitigate,
        // we ignore these exceptions and read the response again as a string
        // making the somewhat strong assumption that the response is an error
        try {
          // Reset the stream so we can read from it again
          response.getEntityInputStream().reset();
        } catch (IOException iox) {
          // pass
        }
        if (resultEntity == null) {
          // If we were unable to deserialize an object of type `returnType`,
          // we assume it's because we've received an error from the API.
          generateApiException(response, UNKNOWN_ERROR_MESSAGE);
        }
        return resultEntity;
      }
    } else {
      // We have received an HTTP error repsonse (VCO 3+)
      generateApiException(response, UNKNOWN_ERROR_MESSAGE);
      return resultEntity;
    }
  }

}
