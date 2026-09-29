package br.com.nexoestoque.repository;

import br.com.nexoestoque.dto.SupplierRequest;
import br.com.nexoestoque.model.Supplier;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class SupplierRepository {
    private final DataSource dataSource;

    public SupplierRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Supplier> findAll(Boolean active) throws SQLException {
        List<Supplier> items = new ArrayList<>();
        String sql = """
                SELECT id, name, tax_id, contact_name, email, phone,
                       lead_time_days, active, created_at, updated_at
                  FROM suppliers
                 WHERE (? IS NULL OR active = ?)
                 ORDER BY active DESC, name ASC, id ASC
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (active == null) {
                statement.setNull(1, Types.BOOLEAN);
                statement.setNull(2, Types.BOOLEAN);
            } else {
                statement.setBoolean(1, active);
                statement.setBoolean(2, active);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) items.add(map(rs));
            }
        }
        return items;
    }

    public Supplier findById(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, name, tax_id, contact_name, email, phone,
                            lead_time_days, active, created_at, updated_at
                       FROM suppliers
                      WHERE id = ?
                     """)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public Supplier create(SupplierRequest request) throws SQLException {
        long id;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO suppliers(
                         name, tax_id, contact_name, email, phone, lead_time_days
                     )
                     VALUES(?,?,?,?,?,?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, request.name().trim());
            setNullable(statement, 2, request.taxId());
            setNullable(statement, 3, request.contactName());
            setNullable(statement, 4, request.email());
            setNullable(statement, 5, request.phone());
            statement.setInt(6, request.leadTimeDays());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Fornecedor criado sem identificador");
                id = keys.getLong(1);
            }
        }
        return findById(id);
    }

    public Supplier update(long id, SupplierRequest request) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE suppliers
                        SET name = ?,
                            tax_id = ?,
                            contact_name = ?,
                            email = ?,
                            phone = ?,
                            lead_time_days = ?
                      WHERE id = ?
                     """)) {
            statement.setString(1, request.name().trim());
            setNullable(statement, 2, request.taxId());
            setNullable(statement, 3, request.contactName());
            setNullable(statement, 4, request.email());
            setNullable(statement, 5, request.phone());
            statement.setInt(6, request.leadTimeDays());
            statement.setLong(7, id);
            if (statement.executeUpdate() == 0) return null;
        }
        return findById(id);
    }

    public Supplier setActive(long id, boolean active) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE suppliers SET active = ? WHERE id = ?
                     """)) {
            statement.setBoolean(1, active);
            statement.setLong(2, id);
            if (statement.executeUpdate() == 0) return null;
        }
        return findById(id);
    }

    private void setNullable(PreparedStatement statement, int index, String value) throws SQLException {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) statement.setNull(index, Types.VARCHAR);
        else statement.setString(index, normalized);
    }

    private Supplier map(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        return new Supplier(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("tax_id"),
                rs.getString("contact_name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getInt("lead_time_days"),
                rs.getBoolean("active"),
                created == null ? null : created.toLocalDateTime(),
                updated == null ? null : updated.toLocalDateTime()
        );
    }
}
