package studyplanner;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Sends requests to the REST API
public class ApiClient {

    // status is 0 if we couldn't reach the server at all
    public static class ApiException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public final int status;

        ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1) // uvicorn doesn't like the HTTP/2 upgrade
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String baseUrl;
    private final String token;

    public ApiClient(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
    }

    // used to warn people before they send a password over plain http
    public static boolean isUnencryptedRemote(String server) {
        String s = server.toLowerCase();
        if (!s.startsWith("http://")) {
            return false;
        }
        try {
            String host = URI.create(s).getHost();
            return host != null && !host.equals("localhost") && !host.equals("127.0.0.1")
                    && !host.equals("[::1]");
        } catch (IllegalArgumentException e) {
            return false; // still typing
        }
    }

    public Object get(String path) {
        return send("GET", path, null);
    }

    public Object post(String path, Map<String, ?> body) {
        return send("POST", path, body);
    }

    public Object patch(String path, Map<String, ?> body) {
        return send("PATCH", path, body);
    }

    public Object delete(String path) {
        return send("DELETE", path, null);
    }

    private Object send(String method, String path, Map<String, ?> body) {
        HttpResponse<String> response;
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(20));
            if (token != null) {
                request.header("Authorization", "Bearer " + token);
            }
            if (body == null) {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json");
                request.method(method, HttpRequest.BodyPublishers.ofString(Json.write(body)));
            }
            response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IllegalArgumentException e) {
            throw new ApiException(0, "Not a valid server address: " + baseUrl);
        } catch (IOException e) {
            throw new ApiException(0, "Cannot reach the server at " + baseUrl
                    + ". Is it running?");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "Interrupted");
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new ApiException(status, errorMessage(status, response.body()));
        }
        if (response.body().isEmpty()) {
            return null;
        }
        try {
            return Json.parse(response.body());
        } catch (IllegalArgumentException e) {
            throw new ApiException(status, "The server did not send back JSON. "
                    + "Is " + baseUrl + " the right address?");
        }
    }

    // FastAPI puts errors in "detail", either a string or a list of problems
    static String errorMessage(int status, String body) {
        try {
            Object detail = ((Map<?, ?>) Json.parse(body)).get("detail");
            if (detail instanceof String) {
                return (String) detail;
            }
            if (detail instanceof List) {
                List<String> messages = new ArrayList<>();
                for (Object item : (List<?>) detail) {
                    Map<?, ?> problem = (Map<?, ?>) item;
                    List<?> location = (List<?>) problem.get("loc");
                    Object field = location.get(location.size() - 1);
                    if (field.equals("body")) {
                        messages.add(String.valueOf(problem.get("msg")));
                    } else {
                        messages.add(field + ": " + problem.get("msg"));
                    }
                }
                return String.join("; ", messages);
            }
        } catch (RuntimeException e) {
            // wasn't the format we expected
        }
        return "The server returned error " + status;
    }
}
