package br.com.nexoestoque.repository;

import br.com.nexoestoque.dto.StockLocationRequest;
import br.com.nexoestoque.dto.WarehouseRequest;
import br.com.nexoestoque.model.StockLocation;
import br.com.nexoestoque.model.Warehouse;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class WarehouseRepository {
    private final DataSource dataSource;

    public WarehouseRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Warehouse> findWarehouses(Boolean active) throws SQLException {
        List<Warehouse> items = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, code, name, branch_name, address, active, created_at, updated_at
                       FROM warehouses
                      WHERE (? IS NULL OR active = ?)
                      ORDER BY active DESC, name, id
                     """)) {
            bindNullableBoolean(statement, active);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) items.add(mapWarehouse(rs));
            }
        }
        return items;
    }

    public Warehouse findWarehouse(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, code, name, branch_name, address, active, created_at, updated_at
                       FROM warehouses
                      WHERE id = ?
                     """)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapWarehouse(rs) : null;
            }
        }
    }

    public Warehouse createWarehouse(WarehouseRequest request) throws SQLException {
        long id;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO warehouses(code, name, branch_name, address)
                     VALUES(?,?,?,?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, request.code().trim().toUpperCase(Locale.ROOT));
            statement.setString(2, request.name().trim());
            setNullable(statement, 3, request.branchName());
            setNullable(statement, 4, request.address());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Depósito criado sem identificador");
                id = keys.getLong(1);
            }
        }
        return findWarehouse(id);
    }

    public Warehouse updateWarehouse(long id, WarehouseRequest request) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE warehouses
                        SET code = ?, name = ?, branch_name = ?, address = ?
                      WHERE id = ?
                     """)) {
            statement.setString(1, request.code().trim().toUpperCase(Locale.ROOT));
            statement.setString(2, request.name().trim());
            setNullable(statement, 3, request.branchName());
            setNullable(statement, 4, request.address());
            statement.setLong(5, id);
            if (statement.executeUpdate() == 0) return null;
        }
        return findWarehouse(id);
    }

    public Warehouse setWarehouseActive(long id, boolean active) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!active) {
                    try (PreparedStatement check = connection.prepareStatement("""
                            SELECT COUNT(*)
                              FROM stock_batches b
                              JOIN stock_locations l ON l.id = b.location_id
                             WHERE l.warehouse_id = ?
                               AND b.quantity > 0
                            """)) {
                        check.setLong(1, id);
                        try (ResultSet rs = check.executeQuery()) {
                            rs.next();
                            if (rs.getLong(1) > 0) {
                                throw business("Não é possível inativar depósito com estoque disponível");
                            }
                        }
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE warehouses SET active = ? WHERE id = ?
                        """)) {
                    statement.setBoolean(1, active);
                    statement.setLong(2, id);
                    if (statement.executeUpdate() == 0) {
                        connection.rollback();
                        return null;
                    }
                }

                if (!active) {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE stock_locations SET active = FALSE WHERE warehouse_id = ?
                            """)) {
                        statement.setLong(1, id);
                        statement.executeUpdate();
                    }
                }

                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
        return findWarehouse(id);
    }

    public List<StockLocation> findLocations(Long warehouseId, Boolean active) throws SQLException {
        List<StockLocation> items = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT l.id, l.warehouse_id, w.code AS warehouse_code,
                            w.name AS warehouse_name, w.branch_name,
                            w.address AS warehouse_address,
                            l.code, l.aisle, l.shelf, l.bin_code,
                            l.active, l.created_at, l.updated_at
                       FROM stock_locations l
                       JOIN warehouses w ON w.id = l.warehouse_id
                      WHERE (? IS NULL OR l.warehouse_id = ?)
                        AND (? IS NULL OR l.active = ?)
                      ORDER BY w.name, l.code, l.id
                     """)) {
            if (warehouseId == null) {
                statement.setNull(1, Types.BIGINT);
                statement.setNull(2, Types.BIGINT);
            } else {
                statement.setLong(1, warehouseId);
                statement.setLong(2, warehouseId);
            }
            if (active == null) {
                statement.setNull(3, Types.BOOLEAN);
                statement.setNull(4, Types.BOOLEAN);
            } else {
                statement.setBoolean(3, active);
                statement.setBoolean(4, active);
            }

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) items.add(mapLocation(rs));
            }
        }
        return items;
    }

    public StockLocation findLocation(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT l.id, l.warehouse_id, w.code AS warehouse_code,
                            w.name AS warehouse_name, w.branch_name,
                            w.address AS warehouse_address,
                            l.code, l.aisle, l.shelf, l.bin_code,
                            l.active, l.created_at, l.updated_at
                       FROM stock_locations l
                       JOIN warehouses w ON w.id = l.warehouse_id
                      WHERE l.id = ?
                     """)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapLocation(rs) : null;
            }
        }
    }

    public StockLocation createLocation(StockLocationRequest request) throws SQLException {
        ensureActiveWarehouse(request.warehouseId());
        long id;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO stock_locations(warehouse_id, code, aisle, shelf, bin_code)
                     VALUES(?,?,?,?,?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, request.warehouseId());
            statement.setString(2, request.code().trim().toUpperCase(Locale.ROOT));
            setNullable(statement, 3, request.aisle());
            setNullable(statement, 4, request.shelf());
            setNullable(statement, 5, request.binCode());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Posição criada sem identificador");
                id = keys.getLong(1);
            }
        }
        return findLocation(id);
    }

    public StockLocation updateLocation(long id, StockLocationRequest request) throws SQLException {
        ensureActiveWarehouse(request.warehouseId());
        try (Connection connection = dataSource.getConnection()) {
            Long currentWarehouse = null;
            try (PreparedStatement current = connection.prepareStatement(
                    "SELECT warehouse_id FROM stock_locations WHERE id = ?")) {
                current.setLong(1, id);
                try (ResultSet rs = current.executeQuery()) {
                    if (rs.next()) currentWarehouse = rs.getLong(1);
                }
            }
            if (currentWarehouse == null) return null;

            if (!currentWarehouse.equals(request.warehouseId())) {
                try (PreparedStatement check = connection.prepareStatement("""
                        SELECT COUNT(*) FROM stock_batches
                         WHERE location_id = ? AND quantity > 0
                        """)) {
                    check.setLong(1, id);
                    try (ResultSet rs = check.executeQuery()) {
                        rs.next();
                        if (rs.getLong(1) > 0) {
                            throw business("Não é possível mover uma posição para outro depósito enquanto houver estoque");
                        }
                    }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE stock_locations
                       SET warehouse_id = ?, code = ?, aisle = ?, shelf = ?, bin_code = ?
                     WHERE id = ?
                    """)) {
                statement.setLong(1, request.warehouseId());
                statement.setString(2, request.code().trim().toUpperCase(Locale.ROOT));
                setNullable(statement, 3, request.aisle());
                setNullable(statement, 4, request.shelf());
                setNullable(statement, 5, request.binCode());
                statement.setLong(6, id);
                statement.executeUpdate();
            }
        }
        return findLocation(id);
    }

    public StockLocation setLocationActive(long id, boolean active) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            if (!active) {
                try (PreparedStatement check = connection.prepareStatement("""
                        SELECT COUNT(*) FROM stock_batches
                         WHERE location_id = ? AND quantity > 0
                        """)) {
                    check.setLong(1, id);
                    try (ResultSet rs = check.executeQuery()) {
                        rs.next();
                        if (rs.getLong(1) > 0) {
                            throw business("Não é possível inativar posição com estoque disponível");
                        }
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE stock_locations SET active = ? WHERE id = ?")) {
                statement.setBoolean(1, active);
                statement.setLong(2, id);
                if (statement.executeUpdate() == 0) return null;
            }
        }
        return findLocation(id);
    }

    private void ensureActiveWarehouse(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT active FROM warehouses WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw business("Depósito não encontrado");
                if (!rs.getBoolean(1)) throw business("Depósito inativo");
            }
        }
    }

    private void bindNullableBoolean(PreparedStatement statement, Boolean active) throws SQLException {
        if (active == null) {
            statement.setNull(1, Types.BOOLEAN);
            statement.setNull(2, Types.BOOLEAN);
        } else {
            statement.setBoolean(1, active);
            statement.setBoolean(2, active);
        }
    }

    private void setNullable(PreparedStatement statement, int index, String value) throws SQLException {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) statement.setNull(index, Types.VARCHAR);
        else statement.setString(index, normalized);
    }

    private Warehouse mapWarehouse(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        return new Warehouse(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("branch_name"),
                rs.getString("address"),
                rs.getBoolean("active"),
                created == null ? null : created.toLocalDateTime(),
                updated == null ? null : updated.toLocalDateTime()
        );
    }

    private StockLocation mapLocation(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        return new StockLocation(
                rs.getLong("id"),
                rs.getLong("warehouse_id"),
                rs.getString("warehouse_code"),
                rs.getString("warehouse_name"),
                rs.getString("branch_name"),
                rs.getString("warehouse_address"),
                rs.getString("code"),
                rs.getString("aisle"),
                rs.getString("shelf"),
                rs.getString("bin_code"),
                rs.getBoolean("active"),
                created == null ? null : created.toLocalDateTime(),
                updated == null ? null : updated.toLocalDateTime()
        );
    }

    private SQLException business(String message) {
        return new SQLException(message, "45000");
    }
}
