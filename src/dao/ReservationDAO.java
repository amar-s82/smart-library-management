package dao;

import database.DatabaseConnection;
import model.Reservation;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReservationDAO {

    public boolean reserveBook(int studentId, int bookId) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return false;

        // Check if student already has a pending reservation for this book
        String checkQuery = "SELECT COUNT(*) FROM Reservations WHERE student_id = ? AND book_id = ? AND status = 'PENDING'";
        try (PreparedStatement pCheck = conn.prepareStatement(checkQuery)) {
            pCheck.setInt(1, studentId);
            pCheck.setInt(2, bookId);
            ResultSet rs = pCheck.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                return false; // Already in queue
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Get current queue length for this book
        int position = 1;
        String countQuery = "SELECT COUNT(*) FROM Reservations WHERE book_id = ? AND status = 'PENDING'";
        try (PreparedStatement pCount = conn.prepareStatement(countQuery)) {
            pCount.setInt(1, bookId);
            ResultSet rs = pCount.executeQuery();
            if (rs.next()) {
                position = rs.getInt(1) + 1;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String insertQuery = "INSERT INTO Reservations (student_id, book_id, status, queue_position) VALUES (?, ?, 'PENDING', ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {
            pstmt.setInt(1, studentId);
            pstmt.setInt(2, bookId);
            pstmt.setInt(3, position);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<Reservation> getAllReservations() {
        List<Reservation> list = new ArrayList<>();
        String query = "SELECT r.*, s.full_name as student_name, b.title as book_title " +
                "FROM Reservations r " +
                "JOIN Students s ON r.student_id = s.student_id " +
                "JOIN Books b ON r.book_id = b.book_id " +
                "ORDER BY r.reservation_id DESC";
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return list;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                list.add(mapReservation(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<Reservation> getReservationsByStudent(int studentId) {
        List<Reservation> list = new ArrayList<>();
        String query = "SELECT r.*, s.full_name as student_name, b.title as book_title " +
                "FROM Reservations r " +
                "JOIN Students s ON r.student_id = s.student_id " +
                "JOIN Books b ON r.book_id = b.book_id " +
                "WHERE r.student_id = ? " +
                "ORDER BY r.reservation_id DESC";
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return list;

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, studentId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(mapReservation(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean cancelReservation(int reservationId) {
        String query = "UPDATE Reservations SET status = 'CANCELLED' WHERE reservation_id = ?";
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return false;

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, reservationId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean fulfillReservation(int reservationId) {
        String query = "UPDATE Reservations SET status = 'FULFILLED' WHERE reservation_id = ?";
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return false;

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, reservationId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private Reservation mapReservation(ResultSet rs) throws SQLException {
        return new Reservation(
                rs.getInt("reservation_id"),
                rs.getInt("student_id"),
                rs.getString("student_name"),
                rs.getInt("book_id"),
                rs.getString("book_title"),
                rs.getTimestamp("reservation_date"),
                rs.getString("status"),
                rs.getInt("queue_position")
        );
    }
}
