package com.warframemarket.collector.store;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Reads and writes the local JSON cache that makes data survive restarts. */
public final class StateStore {

    private static final Logger LOG = Logger.getLogger(StateStore.class.getName());

    private final Path file;
    private final ObjectMapper mapper;

    public StateStore() {
        this(AppPaths.stateFile());
    }

    public StateStore(Path file) {
        this.file = file;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Path file() {
        return file;
    }

    /**
     * Loads the cache, or returns an empty state when there is nothing usable on disk.
     * A corrupt or outdated file is reported and treated as absent rather than fatal.
     */
    public PersistedState load() {
        if (!Files.isRegularFile(file)) {
            return PersistedState.empty();
        }
        try {
            PersistedState state = mapper.readValue(file.toFile(), PersistedState.class);
            if (state == null || state.schemaVersion() != PersistedState.CURRENT_SCHEMA_VERSION) {
                LOG.log(Level.WARNING, "Ignoring cache with unsupported schema version at {0}", file);
                return PersistedState.empty();
            }
            return new PersistedState(
                    state.schemaVersion(),
                    state.catalogFetchedAt(),
                    state.items() == null ? java.util.List.of() : state.items(),
                    state.prices() == null ? java.util.Map.of() : state.prices());
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not read cache at " + file + ", starting empty", e);
            return PersistedState.empty();
        }
    }

    /**
     * Writes the cache to a temporary file and moves it into place, so an interrupted
     * save (or a crash mid-refresh) cannot leave a half-written file behind.
     */
    public synchronized void save(PersistedState state) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = Files.createTempFile(parent, "market-data", ".json.tmp");
        try {
            mapper.writeValue(temp.toFile(), state);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
