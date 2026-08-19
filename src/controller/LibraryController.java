package controller;

import dao.*;
import model.*;
import service.*;
import utils.ReportExporter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class LibraryController {

    private final AuthService authService;
    private final BookService bookService;
    private final BorrowService borrowService;
    private final StudentService studentService;
    private final AnalyticsService analyticsService;
    private final CategoryDAO categoryDAO;
    private final ReservationDAO reservationDAO;
    private final ReviewDAO reviewDAO;
    private final NotificationDAO notificationDAO;
    private final FineDAO fineDAO;
    private final AchievementDAO achievementDAO;

    public LibraryController() {
        this.authService = new AuthService();
        this.bookService = new BookService();
        this.borrowService = new BorrowService();
        this.studentService = new StudentService();
        this.analyticsService = new AnalyticsService();
        this.categoryDAO = new CategoryDAO();
        this.reservationDAO = new ReservationDAO();
        this.reviewDAO = new ReviewDAO();
        this.notificationDAO = new NotificationDAO();
        this.fineDAO = new FineDAO();
        this.achievementDAO = new AchievementDAO();
    }

    // ── Auth ──────────────────────────────────────────────────────────────────────
    public User loginAdmin(String username, String password) {
        return authService.loginAdmin(username, password);
    }

    public Student loginStudent(String identifier, String password) {
        return authService.loginStudent(identifier, password);
    }

    public boolean registerStudent(Student student, String password) {
        return authService.registerStudent(student, password);
    }

    public User getCurrentUser() {
        return authService.getCurrentUser();
    }

    public void logout() {
        authService.logout();
    }

    public boolean changePassword(String usernameOrEmail, String currentPassword, String newPassword) {
        return authService.changePassword(usernameOrEmail, currentPassword, newPassword);
    }

    // ── Books ─────────────────────────────────────────────────────────────────────
    public List<Book> getAllBooks() {
        return bookService.getAllBooks();
    }

    public Book getBookById(int bookId) {
        return bookService.getBookByIdFast(bookId);
    }

    public boolean addBook(Book book) {
        return bookService.addBook(book);
    }

    public boolean updateBook(Book book) {
        return bookService.updateBook(book);
    }

    public boolean deleteBook(int bookId) {
        return bookService.deleteBook(bookId);
    }

    public List<Book> getSortedBooks(String criteria) {
        switch (criteria.toLowerCase()) {
            case "author":
                return bookService.getBooksSortedByAuthor();
            case "availability":
                return bookService.getBooksSortedByAvailability();
            case "title":
            default:
                return bookService.getBooksSortedByTitle();
        }
    }

    public Book searchBookByTitleBinary(String title) {
        return bookService.binarySearchByTitle(title);
    }

    // ── Borrow / Return ───────────────────────────────────────────────────────────
    public boolean issueBook(int studentId, int bookId, int loanDays) {
        boolean success = borrowService.issueBook(studentId, bookId, loanDays);
        if (success) {
            achievementDAO.evaluateAndAwardBadges(studentId);
            notificationDAO.createNotification("STUDENT", studentId, "Book Issued 📖", "You have successfully borrowed a book. Please return it within " + loanDays + " days.");
        }
        return success;
    }

    public boolean returnBook(int borrowId, int studentId, int bookId, java.sql.Timestamp dueDate) {
        boolean success = borrowService.returnBook(borrowId, studentId, bookId, dueDate);
        if (success) {
            achievementDAO.evaluateAndAwardBadges(studentId);
            notificationDAO.createNotification("STUDENT", studentId, "Book Returned ✅", "Your borrowed book has been returned successfully.");
        }
        return success;
    }

    public List<BorrowHistory> getAllBorrowHistory() {
        return borrowService.getAllBorrowHistory();
    }

    public List<BorrowHistory> getStudentBorrowHistory(int studentId) {
        return borrowService.getBorrowHistoryByStudent(studentId);
    }

    // ── Students ──────────────────────────────────────────────────────────────────
    public List<Student> getAllStudents() {
        return studentService.getAllStudents();
    }

    public Student getStudentById(int studentId) {
        return studentService.getStudentById(studentId);
    }

    public boolean updateStudentStatus(int studentId, String status) {
        return studentService.updateStatus(studentId, status);
    }

    // ── Categories ────────────────────────────────────────────────────────────────
    public List<Category> getAllCategories() {
        return categoryDAO.getAllCategories();
    }

    public Category getCategoryById(int categoryId) {
        return categoryDAO.getCategoryById(categoryId);
    }

    public boolean addCategory(Category category) {
        return categoryDAO.addCategory(category);
    }

    public boolean deleteCategory(int categoryId) {
        return categoryDAO.deleteCategory(categoryId);
    }

    // ── Reservations & Waiting Queue ──────────────────────────────────────────────
    public boolean reserveBook(int studentId, int bookId) {
        boolean success = reservationDAO.reserveBook(studentId, bookId);
        if (success) {
            notificationDAO.createNotification("STUDENT", studentId, "Reservation Confirmed 📋", "You have reserved a book and joined the waiting queue.");
        }
        return success;
    }

    public List<Reservation> getAllReservations() {
        return reservationDAO.getAllReservations();
    }

    public List<Reservation> getReservationsByStudent(int studentId) {
        return reservationDAO.getReservationsByStudent(studentId);
    }

    public boolean cancelReservation(int reservationId) {
        return reservationDAO.cancelReservation(reservationId);
    }

    // ── Reviews & Ratings ─────────────────────────────────────────────────────────
    public boolean addReview(Review review) {
        return reviewDAO.addReview(review);
    }

    public List<Review> getReviewsByBook(int bookId) {
        return reviewDAO.getReviewsByBook(bookId);
    }

    public List<Review> getAllReviews() {
        return reviewDAO.getAllReviews();
    }

    public double getAverageRatingForBook(int bookId) {
        return reviewDAO.getAverageRatingForBook(bookId);
    }

    // ── Notifications ─────────────────────────────────────────────────────────────
    public boolean createNotification(String userType, int userId, String title, String message) {
        return notificationDAO.createNotification(userType, userId, title, message);
    }

    public List<Notification> getNotificationsForUser(String userType, int userId) {
        return notificationDAO.getNotificationsForUser(userType, userId);
    }

    public boolean markNotificationAsRead(int notificationId) {
        return notificationDAO.markAsRead(notificationId);
    }

    public boolean markAllNotificationsAsRead(String userType, int userId) {
        return notificationDAO.markAllAsRead(userType, userId);
    }

    public int getUnreadNotificationCount(String userType, int userId) {
        return notificationDAO.getUnreadCount(userType, userId);
    }

    // ── Fines Management ──────────────────────────────────────────────────────────
    public List<FineHistory> getAllFines() {
        return fineDAO.getAllFines();
    }

    public List<FineHistory> getFinesByStudent(int studentId) {
        return fineDAO.getFinesByStudent(studentId);
    }

    public boolean payFine(int fineId, int studentId, double amount) {
        boolean success = fineDAO.payFine(fineId, studentId, amount);
        if (success) {
            notificationDAO.createNotification("STUDENT", studentId, "Fine Payment Received 💰", String.format("Fine payment of ₹%.2f recorded.", amount));
        }
        return success;
    }

    public boolean payAllFinesForStudent(int studentId) {
        boolean success = fineDAO.payAllFinesForStudent(studentId);
        if (success) {
            notificationDAO.createNotification("STUDENT", studentId, "Fines Cleared 💰", "All outstanding fines have been settled.");
        }
        return success;
    }

    // ── Achievements & Badges ─────────────────────────────────────────────────────
    public List<Achievement> getStudentAchievements(int studentId) {
        achievementDAO.evaluateAndAwardBadges(studentId);
        return achievementDAO.getAchievementsForStudent(studentId);
    }

    public boolean awardAchievement(int studentId, String title, String badgeType) {
        return achievementDAO.awardAchievement(studentId, title, badgeType);
    }

    // ── Analytics & Reports ───────────────────────────────────────────────────────
    public Map<String, Object> getDashboardStats() {
        return analyticsService.getDashboardStats();
    }

    public String exportBooksCsv(String outputPath) throws IOException {
        return ReportExporter.exportBooksToCSV(getAllBooks(), outputPath);
    }

    public String exportStudentsCsv(String outputPath) throws IOException {
        return ReportExporter.exportStudentsToCSV(getAllStudents(), outputPath);
    }

    public String exportBorrowHistoryCsv(String outputPath) throws IOException {
        return ReportExporter.exportBorrowHistoryToCSV(getAllBorrowHistory(), outputPath);
    }
}
