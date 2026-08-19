package dao;

import database.DatabaseConnection;
import model.FineHistory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FineDAO {

    public List<FineHistory> getAllFines() {
        List<FineHistory> list = new ArrayList<>();
        String query = "SELECT fh.*, s.full_name as student_name, b.title as book_title " +
                "FROM FineHistory fh " +
                "JOIN Students s ON fh.student_id = s.student_id " +
                "LEFT JOIN BorrowHistory bh ON fh.borrow_id = bh.borrow_id " +
                "LEFT JOIN Books b ON bh.book_id = b.book_id " +
                "ORDER BY fh.fine_id DESC";
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return list;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                list.add(mapFine(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<FineHistory> getFinesByStudent(int studentId) {
        List<FineHistory> list = new ArrayList<>();
        String query = "SELECT fh.*, s.full_name as student_name, b.title as book_title " +
                "FROM FineHistory fh " +
                "JOIN Students s ON fh.student_id = s.student_id " +
                "LEFT JOIN BorrowHistory bh ON fh.borrow_id = bh.borrow_id " +
                "LEFT JOIN Books b ON bh.book_id = b.book_id " +
                "WHERE fh.student_id = ? " +
                "ORDER BY fh.fine_id DESC";
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return list;

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, studentId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(mapFine(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean recordFine(int studentId, int borrowId, double amount, String reason) {
        String query = "INSERT INTO FineHistory (student_id, borrow_id, amount, reason, payment_status) VALUES (?, ?, ?, ?, 'UNPAID')";
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return false;

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, studentId);
            pstmt.setInt(2, borrowId);
            pstmt.setDouble(3, amount);
            pstmt.setString(4, reason);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean payFine(int fineId, int studentId, double amountPaid) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return false;

        String updateFineSql = "UPDATE FineHistory SET payment_status = 'PAID', paid_at = ? WHERE fine_id = ? AND student_id = ?";
        String updateStudentSql = "UPDATE Students SET total_fines_owed = CASE WHEN total_fines_owed >= ? THEN total_fines_owed - ? ELSE 0.0 END WHERE student_id = ?";

        try {
            conn.setAutoCommit(false);

            try (PreparedStatement pFine = conn.prepareStatement(updateFineSql);
                 PreparedStatement pStu = conn.prepareStatement(updateStudentSql)) {

                Timestamp now = new Timestamp(System.currentTimeMillis());
                pFine.setTimestamp(1, now);
                pFine.setInt(2, fineId);
                pFine.setInt(3, studentId);
                int fineRows = pFine.executeUpdate();

                pStu.setDouble(1, amountPaid);
                pStu.setDouble(2, amountPaid);
                pStu.setInt(3, studentId);
                pStu.executeUpdate();

                conn.commit();
                return fineRows > 0;
            } catch (SQLException e) {
                conn.rollback();
                e.printStackTrace();
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean payAllFinesForStudent(int studentId) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return false;

        String updateFines = "UPDATE FineHistory SET payment_status = 'PAID', paid_at = ? WHERE student_id = ? AND payment_status = 'UNPAID'";
        String clearStudentFines = "UPDATE Students SET total_fines_owed = 0.0 WHERE student_id = ?";

        try {
            conn.setAutoCommit(false);
            try (PreparedStatement pFines = conn.prepareStatement(updateFines);
                 PreparedStatement pStu = conn.prepareStatement(clearStudentFines)) {

                Timestamp now = new Timestamp(System.currentTimeMillis());
                pFines.setTimestamp(1, now);
                pFines.setInt(2, studentId);
                pFines.executeUpdate();

                pStu.setInt(1, studentId);
                pStu.executeUpdate();

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                e.printStackTrace();
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private FineHistory mapFine(ResultSet rs) throws SQLException {
        return new FineHistory(
                rs.getInt("fine_id"),
                rs.getInt("student_id"),
                rs.getString("student_name"),
                rs.getInt("borrow_id"),
                rs.getString("book_title"),
                rs.getDouble("amount"),
                rs.getString("reason"),
                rs.getString("payment_status"),
                rs.getTimestamp("paid_at")
        );
    }
}
