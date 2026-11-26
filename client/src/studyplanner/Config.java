package studyplanner;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

// Remembers the login (server + token) or guest mode between runs
public class Config {
    public static final String DEFAULT_SERVER = "http://127.0.0.1:8000";

    private final Path file;
    private final Properties props = new Properties();

    public Config(Path folder) throws IOException {
        file = folder.resolve("session.properties");
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                props.load(reader);
            }
        }
    }

    public static Path defaultDirectory() {
        // the tests set this so they don't touch the real folder
        String folder = System.getenv("STUDYPLANNER_HOME");
        if (folder != null && !folder.isBlank()) {
            return Path.of(folder);
        }
        return Path.of(System.getProperty("user.home"), ".studyplanner");
    }

    // priority: the address given, then the environment variable, then the saved one
    public String server(String address) {
        String result = address;
        if (result == null) {
            result = System.getenv("STUDYPLANNER_URL");
        }
        if (result == null || result.isBlank()) {
            result = props.getProperty("server", DEFAULT_SERVER);
        }
        return result.strip().replaceAll("/+$", "");
    }

    public String token(String server) {
        // never send the token to a different server
        if (!server.equals(props.getProperty("server"))) {
            return null;
        }
        return props.getProperty("token");
    }

    public boolean isGuest() {
        return "true".equals(props.getProperty("guest"));
    }

    public void setGuest(boolean guest) throws IOException {
        if (guest) {
            props.setProperty("guest", "true");
        } else {
            props.remove("guest");
        }
        save();
    }

    public void saveLogin(String server, String username, String token) throws IOException {
        props.remove("guest");
        props.setProperty("server", server);
        props.setProperty("username", username);
        props.setProperty("token", token);
        save();
    }

    public void clearToken() throws IOException {
        props.remove("token");
        save();
    }

    private void save() throws IOException {
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file)) {
            props.store(writer, "Study Planner login - don't share this file");
        }
    }
}
