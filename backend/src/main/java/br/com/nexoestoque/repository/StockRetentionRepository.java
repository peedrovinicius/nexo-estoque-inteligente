package br.com.nexoestoque.repository;

import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;

@Repository
public class StockRetentionRepository {
    private final DataSource dataSource;

    public StockRetentionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public int archiveBefore(LocalDateTime cutoff, int limit, String actor) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             CallableStatement statement = connection.prepareCall(
                     "{call sp_archive_stock_movements(?,?,?,?)}"
             )) {
            statement.setTimestamp(1, Timestamp.valueOf(cutoff));
            statement.setInt(2, Math.max(1, Math.min(limit, 10000)));
            statement.setString(3, actor);
            statement.registerOutParameter(4, Types.INTEGER);
            statement.execute();
            return statement.getInt(4);
        }
    }
}
