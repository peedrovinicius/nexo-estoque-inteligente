package br.com.nexoestoque.repository;

import br.com.nexoestoque.model.Product;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class ProductProcedureRepository {
    private final DataSource dataSource;

    public ProductProcedureRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public long create(Product product) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             CallableStatement statement = connection.prepareCall("{call sp_product_create(?,?,?,?,?,?,?,?,?)}")) {
            statement.setString(1, product.sku());
            statement.setString(2, product.barcode());
            statement.setString(3, product.name());
            statement.setString(4, product.category());
            statement.setBigDecimal(5, product.costPrice());
            statement.setBigDecimal(6, product.salePrice());
            statement.setBigDecimal(7, product.currentStock());
            statement.setBigDecimal(8, product.minimumStock());
            statement.registerOutParameter(9, Types.BIGINT);
            statement.execute();
            return statement.getLong(9);
        }
    }

    public List<Product> findAll() throws SQLException {
        List<Product> products = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             CallableStatement statement = connection.prepareCall("{call sp_product_list()}");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                products.add(new Product(
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
                ));
            }
        }
        return products;
    }
}
