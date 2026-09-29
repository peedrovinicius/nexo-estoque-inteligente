package br.com.nexoestoque.repository;

import br.com.nexoestoque.dto.ProductCreateRequest;
import br.com.nexoestoque.dto.ProductPage;
import br.com.nexoestoque.dto.ProductUpdateRequest;
import br.com.nexoestoque.model.Product;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Repository
public class ProductProcedureRepository {
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "name", "name",
            "sku", "sku",
            "category", "category",
            "stock", "current_stock",
            "minimumStock", "minimum_stock",
            "createdAt", "created_at"
    );

    private final DataSource dataSource;

    public ProductProcedureRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public long create(ProductCreateRequest product) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             CallableStatement statement = connection.prepareCall("{call sp_product_create(?,?,?,?,?,?,?,?,?)}")) {
            statement.setString(1, normalize(product.sku()));
            statement.setString(2, normalizeNullable(product.barcode()));
            statement.setString(3, normalize(product.name()));
            statement.setString(4, normalize(product.category()));
            statement.setBigDecimal(5, zeroIfNull(product.costPrice()));
            statement.setBigDecimal(6, zeroIfNull(product.salePrice()));
            statement.setBigDecimal(7, zeroIfNull(product.currentStock()));
            statement.setBigDecimal(8, zeroIfNull(product.minimumStock()));
            statement.registerOutParameter(9, Types.BIGINT);
            statement.execute();
            return statement.getLong(9);
        }
    }

    public Product update(long id, ProductUpdateRequest product) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             CallableStatement statement = connection.prepareCall("{call sp_product_update(?,?,?,?,?,?,?,?)}")) {
            statement.setLong(1, id);
            statement.setString(2, normalize(product.sku()));
            statement.setString(3, normalizeNullable(product.barcode()));
            statement.setString(4, normalize(product.name()));
            statement.setString(5, normalize(product.category()));
            statement.setBigDecimal(6, zeroIfNull(product.costPrice()));
            statement.setBigDecimal(7, zeroIfNull(product.salePrice()));
            statement.setBigDecimal(8, zeroIfNull(product.minimumStock()));
            statement.execute();
        }
        return findById(id);
    }

    public Product setActive(long id, boolean active) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             CallableStatement statement = connection.prepareCall("{call sp_product_set_active(?,?)}")) {
            statement.setLong(1, id);
            statement.setBoolean(2, active);
            statement.execute();
        }
        return findById(id);
    }

    public Product findById(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, sku, barcode, name, category, cost_price, sale_price,
                            current_stock, minimum_stock, active
                     FROM products
                     WHERE id = ?
                     """)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        }
        return null;
    }

    public List<Product> findAll() throws SQLException {
        return search(0, 200, null, null, true, "name", "asc").content();
    }

    public ProductPage search(
            int page,
            int size,
            String query,
            String category,
            Boolean active,
            String sort,
            String direction
    ) throws SQLException {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 200));
        String sortColumn = SORT_COLUMNS.getOrDefault(sort, "name");
        String sortDirection = "desc".equalsIgnoreCase(direction) ? "DESC" : "ASC";

        String normalizedQuery = normalizeNullable(query);
        String normalizedCategory = normalizeNullable(category);

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> parameters = new ArrayList<>();

        if (normalizedQuery != null) {
            where.append(" AND (LOWER(name) LIKE ? OR LOWER(sku) LIKE ? OR LOWER(COALESCE(barcode,'')) LIKE ?) ");
            String like = "%" + normalizedQuery.toLowerCase(Locale.ROOT) + "%";
            parameters.add(like);
            parameters.add(like);
            parameters.add(like);
        }
        if (normalizedCategory != null) {
            where.append(" AND LOWER(category) = ? ");
            parameters.add(normalizedCategory.toLowerCase(Locale.ROOT));
        }
        if (active != null) {
            where.append(" AND active = ? ");
            parameters.add(active);
        }

        long total;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement count = connection.prepareStatement("SELECT COUNT(*) FROM products" + where)) {
            bind(count, parameters);
            try (ResultSet rs = count.executeQuery()) {
                rs.next();
                total = rs.getLong(1);
            }
        }

        List<Product> products = new ArrayList<>();
        String sql = """
                SELECT id, sku, barcode, name, category, cost_price, sale_price,
                       current_stock, minimum_stock, active
                FROM products
                """ + where +
                " ORDER BY active DESC, " + sortColumn + " " + sortDirection + ", id ASC LIMIT ? OFFSET ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            statement.setInt(parameters.size() + 1, safeSize);
            statement.setInt(parameters.size() + 2, safePage * safeSize);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) products.add(map(rs));
            }
        }

        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) safeSize);
        return new ProductPage(products, safePage, safeSize, total, totalPages);
    }

    private void bind(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            Object value = parameters.get(index);
            if (value instanceof Boolean bool) statement.setBoolean(index + 1, bool);
            else statement.setObject(index + 1, value);
        }
    }

    private Product map(ResultSet rs) throws SQLException {
        return new Product(
                rs.getLong("id"),
                rs.getString("sku"),
                rs.getString("barcode"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getBigDecimal("cost_price"),
                rs.getBigDecimal("sale_price"),
                rs.getBigDecimal("current_stock"),
                rs.getBigDecimal("minimum_stock"),
                rs.getBoolean("active")
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeNullable(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? null : normalized;
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
