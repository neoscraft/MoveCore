package fr.neoscraft.movecore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JsonDataStore implements DataStore {
    private final Path file;
        private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(java.time.Instant.class, new InstantTypeAdapter())
            .setPrettyPrinting()
            .create();

    public JsonDataStore(Path file) {
        this.file = file;
    }

    @Override
    public synchronized StorageState load() {
        if (!Files.exists(file)) {
            return new StorageState();
        }
        try {
            StorageState state = gson.fromJson(Files.readString(file), StorageState.class);
            return state == null ? new StorageState() : state;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Unable to read " + file, exception);
        }
    }

    @Override
    public synchronized void save(StorageState state) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(state));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write " + file, exception);
        }
    }

    @Override
    public void close() {
    }
}