package studyplanner.gui;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javafx.application.Platform;

// Runs server calls on another thread so the window doesn't freeze
class Tasks {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "api-calls");
        thread.setDaemon(true);
        return thread;
    });
    private int generation;
    private int pending;

    <T> void run(Callable<T> work, Consumer<T> success, Consumer<Throwable> failure) {
        int startedIn = generation;
        pending++;
        executor.execute(() -> {
            T result = null;
            Throwable error = null;
            try {
                result = work.call();
            } catch (Throwable e) {
                error = e;
            }
            T value = result;
            Throwable problem = error;
            Platform.runLater(() -> {
                pending--;
                if (startedIn != generation) {
                    return;
                }
                if (problem == null) {
                    success.accept(value);
                } else {
                    failure.accept(problem);
                }
            });
        });
    }

    void discardPending() {
        generation++;
    }

    boolean idle() {
        return pending == 0;
    }
}
