package fr.neoscraft.movecore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class MysqlDataStore implements DataStore {
    private final Connection connection;
    private final String table;
        private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(java.time.Instant.class, new InstantTypeAdapter())
            .create();

    public MysqlDataStore(String jdbcUrl, String username, String password, String tablePrefix) {
        try {
            connection = DriverManager.getConnection(jdbcUrl, username, password);
            table = tablePrefix + "state";
            try (PreparedStatement statement = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + table + " (id TINYINT PRIMARY KEY, payload LONGTEXT NOT NULL)")) {
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to connect to MySQL/MariaDB", exception);
        }
    }

    @Override
    public synchronized StorageState load() {
        try (PreparedStatement statement = connection.prepareStatement("SELECT payload FROM " + table + " WHERE id = 1")) {
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    StorageState state = gson.fromJson(result.getString(1), StorageState.class);
                    return state == null ? new StorageState() : state;
                }
                return new StorageState();
            }
        } catch (SQLException | RuntimeException exception) {
            throw new IllegalStateException("Unable to load MoveCore data", exception);
        }
    }

    @Override
    public synchronized void save(StorageState state) {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + table + " (id, payload) VALUES (1, ?) ON DUPLICATE KEY UPDATE payload = VALUES(payload)")) {
            statement.setString(1, gson.toJson(state));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save MoveCore data", exception);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to close database", exception);
        }
    }
}