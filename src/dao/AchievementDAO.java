package dao;

import database.DatabaseConnection;
import model.Achievement;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AchievementDAO {

    public List<Achievement> getAchievementsForStudent(int studentId) {
        List<Achievement> list = new ArrayList<>();
        String query = "SELECT * FROM Achievements WHERE student_id = ? ORDER BY achievement_id DESC";
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return list;

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, studentId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(new Achievement(
                        rs.getInt("achievement_id"),
                        rs.getInt("student_id"),
                        rs.getString("title"),
                        rs.getString("badge_type"),
                        rs.getTimestamp("unlocked_at")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean awardAchievement(int studentId, String title, String badgeType) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return false;

        // Check if already awarded
        String checkQuery = "SELECT COUNT(*) FROM Achievements WHERE student_id = ? AND title = ?";
        try (PreparedStatement pCheck = conn.prepareStatement(checkQuery)) {
            pCheck.setInt(1, studentId);
            pCheck.setString(2, title);
            ResultSet rs = pCheck.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                return false; // Already has badge
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String insertQuery = "INSERT INTO Achievements (student_id, title, badge_type) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {
            pstmt.setInt(1, studentId);
            pstmt.setString(2, title);
            pstmt.setString(3, badgeType);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void evaluateAndAwardBadges(int studentId) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return;

        try {
            // 1. Total Borrows Milestone
            String countBorrows = "SELECT COUNT(*) FROM BorrowHistory WHERE student_id = ?";
            try (PreparedStatement pCount = conn.prepareStatement(countBorrows)) {
                pCount.setInt(1, studentId);
                ResultSet rs = pCount.executeQuery();
                if (rs.next()) {
                    int count = rs.getInt(1);
                    if (count >= 1) {
                        awardAchievement(studentId, "First Page - Borrowed 1st Book", "BRONZE");
                    }
                    if (count >= 5) {
                        awardAchievement(studentId, "Bookworm - Borrowed 5+ Books", "SILVER");
                    }
                    if (count >= 10) {
                        awardAchievement(studentId, "Scholar - Borrowed 10+ Books", "GOLD");
                    }
                }
            }

            // 2. Attendance Milestone
            String countVisits = "SELECT COUNT(*) FROM Attendance WHERE student_id = ?";
            try (PreparedStatement pVisits = conn.prepareStatement(countVisits)) {
                pVisits.setInt(1, studentId);
                ResultSet rs = pVisits.executeQuery();
                if (rs.next()) {
                    int visits = rs.getInt(1);
                    if (visits >= 1) {
                        awardAchievement(studentId, "Door Opener - First Library Visit", "BRONZE");
                    }
                    if (visits >= 10) {
                        awardAchievement(studentId, "Library Regular - 10+ Visits", "SILVER");
                    }
                    if (visits >= 25) {
                        awardAchievement(studentId, "Library Legend - 25+ Visits", "GOLD");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
