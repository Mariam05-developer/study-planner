package studyplanner.gui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.Function;

import studyplanner.ApiClient;
import studyplanner.Config;

// Planner stored on the server
class PlannerService implements Planner {
    private final Config config;
    private final ZoneId zone = ZoneId.systemDefault();
    private volatile String server;

    PlannerService(Config config) {
        this.config = config;
        this.server = config.server(null);
    }

    String server() {
        return server;
    }

    boolean hasSavedLogin() {
        return config.token(server) != null;
    }

    static String normalizeServer(String input) {
        String address = input.strip().replaceAll("/+$", "");
        return address.isEmpty() || address.contains("://") ? address : "http://" + address;
    }

    // accounts

    void signIn(String address, String username, String password, boolean register)
            throws IOException {
        String chosen = config.server(
                address.isBlank() ? null : normalizeServer(address));
        Map<?, ?> reply = (Map<?, ?>) new ApiClient(chosen, null).post(
                register ? "/api/auth/register" : "/api/auth/login",
                Map.of("username", username, "password", password));
        Models.User user = Models.user(reply.get("user"));
        config.saveLogin(chosen, user.username(), (String) reply.get("token"));
        server = chosen;
    }

    @Override
    public boolean isGuest() {
        return false;
    }

    @Override
    public String description(Models.User user) {
        return user.username() + " on " + server;
    }

    @Override
    public void signOut() throws IOException {
        try {
            if (hasSavedLogin()) {
                client().post("/api/auth/logout", null);
            }
        } catch (ApiClient.ApiException e) {
            // log out locally even if the server is down
        } finally {
            config.clearToken();
        }
    }

    @Override
    public void saveSettings(Map<String, ?> changes) {
        send(api -> api.patch("/api/me", changes));
    }

    // loading

    @Override
    public Models.Snapshot load(LocalDate weekOf) {
        String zoneName = zone.getId().equals("Z") ? "UTC" : zone.getId();
        String week = "/api/planner/week?tz="
                + URLEncoder.encode(zoneName, StandardCharsets.UTF_8)
                + (weekOf == null ? "" : "&start=" + weekOf);
        return new Models.Snapshot(
                Models.user(get("/api/me")),
                Models.list(get("/api/courses"), Models::course),
                Models.list(get("/api/assignments?status=all"), Models::assignment),
                Models.list(get("/api/planner/next?limit=100"), Models::ranked),
                Models.week(get(week)),
                Models.list(get("/api/reminders"), Models::reminder));
    }

    // courses

    @Override
    public void addCourse(String code, String name) {
        send(api -> api.post("/api/courses", Map.of("code", code, "name", name)));
    }

    @Override
    public void updateCourse(long id, String code, String name) {
        send(api -> api.patch("/api/courses/" + id, Map.of("code", code, "name", name)));
    }

    @Override
    public void deleteCourse(long id) {
        send(api -> api.delete("/api/courses/" + id));
    }

    // assignments

    @Override
    public void addAssignment(Map<String, ?> body) {
        send(api -> api.post("/api/assignments", body));
    }

    @Override
    public void updateAssignment(long id, Map<String, ?> changes) {
        if (!changes.isEmpty()) {
            send(api -> api.patch("/api/assignments/" + id, changes));
        }
    }

    @Override
    public void logHours(long id, double hours) {
        send(api -> api.post("/api/assignments/" + id + "/log", Map.of("hours", hours)));
    }

    @Override
    public void dismissReminder(long id) {
        send(api -> api.post("/api/assignments/" + id + "/dismiss-reminder", null));
    }

    @Override
    public void deleteAssignment(long id) {
        send(api -> api.delete("/api/assignments/" + id));
    }

    // plumbing

    private ApiClient client() {
        return new ApiClient(server, config.token(server));
    }

    private Object get(String path) {
        return send(api -> api.get(path));
    }

    private Object send(Function<ApiClient, Object> request) {
        try {
            return request.apply(client());
        } catch (ApiClient.ApiException e) {
            if (e.status == 401) {
                // token expired, delete it
                try {
                    config.clearToken();
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            }
            throw e;
        }
    }
}
