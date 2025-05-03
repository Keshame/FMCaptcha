package org.FunMine.FMCaptcha.manager;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.*;

public class DatabaseManager {
    private final JavaPlugin plugin;
    private Connection connection;
    private final ExecutorService databaseWorker = Executors.newSingleThreadExecutor();

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        setupDatabase();
    }

    private void setupDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");

            String dbPath = plugin.getDataFolder() + "/players.db";
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            connection.setAutoCommit(true);

            String createTableSQL = "CREATE TABLE IF NOT EXISTS players (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "last_check TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "passed BOOLEAN DEFAULT FALSE)";

            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createTableSQL);
                plugin.getLogger().info("База данных успешно подключена: " + dbPath);
            }
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("Не удалось найти SQLite драйвер");
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка подключения к базе данных: " + e.getMessage());
        }
    }

    public boolean shouldCheckPlayer(UUID playerId) {
        Future<Boolean> checkTask = databaseWorker.submit(() -> {
            String sql = "SELECT passed FROM players WHERE uuid = ?";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());

                ResultSet result = pstmt.executeQuery();

                return !result.next() || !result.getBoolean("passed");
            } catch (SQLException e) {
                plugin.getLogger().warning("Ошибка проверки игрока " + playerId + ": " + e.getMessage());
                return true;
            }
        });

        try {
            return checkTask.get(2, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            plugin.getLogger().warning("Проверка игрока " + playerId + " заняла слишком много времени");
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Неизвестная ошибка при проверке игрока " + playerId);
            return true;
        }
    }

    public void savePlayerResult(UUID playerId, boolean passed) {
        databaseWorker.execute(() -> {
            String sql = "INSERT OR REPLACE INTO players (uuid, passed) VALUES (?, ?)";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                stmt.setBoolean(2, passed);

                int updated = stmt.executeUpdate();

                if (updated > 0) {
                    plugin.getLogger().info("Результат игрока " + playerId + " сохранен: " +
                            (passed ? "капча пройдена" : "капча не пройдена"));
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Не удалось сохранить результат для " + playerId + ": " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        try {
            databaseWorker.shutdown();

            if (!databaseWorker.awaitTermination(5, TimeUnit.SECONDS)) {
                databaseWorker.shutdownNow();
            }

            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Соединение с базой данных закрыто");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка при закрытии соединения: " + e.getMessage());
        } catch (InterruptedException e) {
            plugin.getLogger().warning("Принудительное завершение работы с базой");
            databaseWorker.shutdownNow();
        }
    }
}