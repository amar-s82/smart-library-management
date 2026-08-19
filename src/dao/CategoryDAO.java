package dao;

import database.DatabaseConnection;
import model.Category;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CategoryDAO {

    public List<Category> getAllCategories() {
        List<Category> list = new ArrayList<>();
        String query = "SELECT * FROM Categories ORDER BY category_name ASC";
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return list;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                list.add(new Category(
                        rs.getInt("category_id"),
                        rs.getString("category_name"),
                        rs.getString("description"),
                        rs.getString("icon_name")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public Category getCategoryById(int categoryId) {
        String query = "SELECT * FROM Categories WHERE category_id = ?";
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return null;

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, categoryId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Category(
                        rs.getInt("category_id"),
                        rs.getString("category_name"),
                        rs.getString("description"),
                        rs.getString("icon_name")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean addCategory(Category category) {
        String query = "INSERT INTO Categories (category_name, description, icon_name) VALUES (?, ?, ?)";
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return false;

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, category.getCategoryName());
            pstmt.setString(2, category.getDescription());
            pstmt.setString(3, category.getIconName());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteCategory(int categoryId) {
        String query = "DELETE FROM Categories WHERE category_id = ?";
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return false;

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, categoryId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
