package web.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import controller.LibraryController;
import model.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.util.*;

public class LibraWebServer {

    private static final int PORT = 8080;
    private final LibraryController controller;
    private final dao.AttendanceDAO attendanceDAO;
    private final dao.UserDAO userDAO;
    private HttpServer server;

    public LibraWebServer() {
        this.controller = new LibraryController();
        this.attendanceDAO = new dao.AttendanceDAO();
        this.userDAO = new dao.UserDAO();
    }

    public static void main(String[] args) {
        database.SchemaInitializer.initializeDatabase();
        LibraWebServer webServer = new LibraWebServer();
        webServer.start();
    }

    public void start() {
        try {
            int portToUse = PORT;
            try {
                server = HttpServer.create(new InetSocketAddress(portToUse), 0);
            } catch (IOException e) {
                portToUse = 8085;
                server = HttpServer.create(new InetSocketAddress(portToUse), 0);
            }

            // Bind API handlers
            server.createContext("/api/stats", new StatsHandler());
            server.createContext("/api/auth/login", new LoginHandler());
            server.createContext("/api/auth/register", new RegisterHandler());
            server.createContext("/api/auth/change-password", new ChangePasswordHandler());
            server.createContext("/api/books", new BooksHandler());
            server.createContext("/api/categories", new CategoriesHandler());
            server.createContext("/api/reservations", new ReservationsHandler());
            server.createContext("/api/reviews", new ReviewsHandler());
            server.createContext("/api/notifications", new NotificationsHandler());
            server.createContext("/api/fines", new FinesHandler());
            server.createContext("/api/fines/pay", new PayFineHandler());
            server.createContext("/api/achievements", new AchievementsHandler());
            server.createContext("/api/reports/export", new ReportExportHandler());
            server.createContext("/api/borrow/issue", new IssueBookHandler());
            server.createContext("/api/borrow/return", new ReturnBookHandler());
            server.createContext("/api/borrow/history", new BorrowHistoryHandler());
            server.createContext("/api/borrow/overdue", new OverdueHandler());
            server.createContext("/api/students", new StudentsHandler());
            server.createContext("/api/students/status", new StudentStatusHandler());
            server.createContext("/api/attendance/logs", new AttendanceLogsHandler());
            server.createContext("/api/attendance/scan", new AttendanceScanHandler());
            server.createContext("/api/health", new HealthHandler());
            server.createContext("/api/analytics/top-visitors", new TopVisitorsHandler());

            // Static file handler (handles src/web, src/web/html, and root assets/)
            server.createContext("/", new StaticFileHandler());

            server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
            server.start();
            System.out.println("==================================================");
            System.out.println("🌐 LibraAI Web Backend running at: http://localhost:" + portToUse);
            System.out.println("🚀 Web Dashboard UI available at: http://localhost:" + portToUse + "/index.html");
            System.out.println("==================================================");

            // Automatically open default browser
            openWebBrowser("http://localhost:" + portToUse + "/index.html");

        } catch (Exception e) {
            System.err.println("❌ Failed to start Web Server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void openWebBrowser(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            } else {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
                } else if (os.contains("mac")) {
                    Runtime.getRuntime().exec(new String[]{"open", url});
                } else if (os.contains("nix") || os.contains("nux")) {
                    Runtime.getRuntime().exec(new String[]{"xdg-open", url});
                } else {
                    System.out.println("ℹ️ Please open your browser and navigate to: " + url);
                }
            }
        } catch (Exception e) {
            System.err.println("ℹ️ Could not open default web browser automatically: " + e.getMessage());
        }
    }

    // CORS & Response Helpers
    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonResponse) throws IOException {
        byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        addCorsHeaders(exchange);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendOptionsResponse(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        exchange.sendResponseHeaders(204, -1);
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) != -1) {
                baos.write(buffer, 0, length);
            }
            return baos.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length > 1) {
                params.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            } else if (pair.length == 1) {
                params.put(pair[0], "");
            }
        }
        return params;
    }

    private static Map<String, String> parseSimpleJson(String jsonStr) {
        Map<String, String> map = new HashMap<>();
        if (jsonStr == null || jsonStr.trim().isEmpty()) return map;
        String cleanStr = jsonStr.trim();
        if (cleanStr.startsWith("{")) cleanStr = cleanStr.substring(1);
        if (cleanStr.endsWith("}")) cleanStr = cleanStr.substring(0, cleanStr.length() - 1);

        String[] tokens = cleanStr.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String token : tokens) {
            String[] kv = token.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replaceAll("^\"|\"$", "");
                String value = kv[1].trim().replaceAll("^\"|\"$", "");
                map.put(key, value);
            }
        }
        return map;
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ── Handlers ──────────────────────────────────────────────────────────────────

    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            String json = "{\"status\":\"UP\",\"system\":\"LibraAI Web API\",\"serverTime\":\"" + new Timestamp(System.currentTimeMillis()) + "\"}";
            sendJsonResponse(exchange, 200, json);
        }
    }

    private class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            Map<String, Object> stats = controller.getDashboardStats();
            StringBuilder sb = new StringBuilder("{");
            int count = 0;
            for (Map.Entry<String, Object> entry : stats.entrySet()) {
                sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
                if (++count < stats.size()) sb.append(",");
            }
            sb.append("}");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    private class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String body = readRequestBody(exchange);
            Map<String, String> json = parseSimpleJson(body);

            String role = json.getOrDefault("role", "ADMIN").toUpperCase();
            String username = json.getOrDefault("username", "").trim();
            String password = json.getOrDefault("password", "");

            if (username.isEmpty() || password.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Username and password are required.\"}");
                return;
            }

            if ("STUDENT".equals(role)) {
                Student student = controller.loginStudent(username, password);
                if (student != null) {
                    sendStudentSuccess(exchange, student);
                } else {
                    sendJsonResponse(exchange, 401, "{\"success\":false,\"message\":\"Invalid Student ID or password.\"}");
                }
            } else {
                User admin = controller.loginAdmin(username, password);
                if (admin != null) {
                    sendAdminSuccess(exchange, admin);
                } else {
                    sendJsonResponse(exchange, 401, "{\"success\":false,\"message\":\"Invalid credentials. Please check your username/student code and password.\"}");
                }
            }
        }

        private void sendStudentSuccess(HttpExchange exchange, Student s) throws IOException {
            String json = String.format(
                    "{\"success\":true,\"user\":{\"id\":%d,\"studentCode\":\"%s\",\"fullName\":\"%s\",\"email\":\"%s\",\"department\":\"%s\",\"yearOfStudy\":%d,\"role\":\"STUDENT\",\"maxBorrowLimit\":%d,\"currentBorrowed\":%d,\"totalFinesOwed\":%.2f}}",
                    s.getId(), escapeJson(s.getStudentCode()), escapeJson(s.getFullName()), escapeJson(s.getEmail()),
                    escapeJson(s.getDepartment()), s.getYearOfStudy(), s.getMaxBorrowLimit(), s.getCurrentBorrowed(), s.getTotalFinesOwed()
            );
            sendJsonResponse(exchange, 200, json);
        }

        private void sendAdminSuccess(HttpExchange exchange, User admin) throws IOException {
            String json = String.format(
                    "{\"success\":true,\"user\":{\"id\":%d,\"username\":\"%s\",\"fullName\":\"%s\",\"email\":\"%s\",\"role\":\"ADMIN\"}}",
                    admin.getId(), escapeJson(admin.getUsername()), escapeJson(admin.getFullName()), escapeJson(admin.getEmail())
            );
            sendJsonResponse(exchange, 200, json);
        }
    }

    private class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String body = readRequestBody(exchange);
            Map<String, String> json = parseSimpleJson(body);

            Student s = new Student();
            s.setStudentCode(json.getOrDefault("studentCode", "").trim());
            s.setFullName(json.getOrDefault("fullName", "").trim());
            s.setEmail(json.getOrDefault("email", "").trim());
            s.setPhone(json.getOrDefault("phone", "").trim());
            s.setDepartment(json.getOrDefault("department", "").trim());
            try {
                s.setYearOfStudy(Integer.parseInt(json.getOrDefault("yearOfStudy", "1")));
            } catch (Exception e) {
                s.setYearOfStudy(1);
            }
            String password = json.getOrDefault("password", "student123");

            if (s.getStudentCode().isEmpty() || s.getFullName().isEmpty() || s.getEmail().isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Student Code, Full Name, and Email are required.\"}");
                return;
            }

            boolean success = controller.registerStudent(s, password);
            if (success) {
                sendJsonResponse(exchange, 201, "{\"success\":true,\"message\":\"Student registered successfully!\"}");
            } else {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Registration failed. Student Code or Email may already exist.\"}");
            }
        }
    }

    private class ChangePasswordHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String body = readRequestBody(exchange);
            Map<String, String> json = parseSimpleJson(body);
            String username = json.getOrDefault("username", "");
            String currentPassword = json.getOrDefault("currentPassword", "");
            String newPassword = json.getOrDefault("newPassword", "");

            if (username.isEmpty() || currentPassword.isEmpty() || newPassword.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"All fields are required.\"}");
                return;
            }

            boolean success = controller.changePassword(username, currentPassword, newPassword);
            if (success) {
                sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Password updated successfully!\"}");
            } else {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Failed to update password. Verify current password.\"}");
            }
        }
    }

    private class BooksHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }

            String method = exchange.getRequestMethod();

            if ("GET".equalsIgnoreCase(method)) {
                Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
                String sort = query.getOrDefault("sort", "title");
                String search = query.getOrDefault("search", "");

                List<Book> books;
                if (!search.isEmpty()) {
                    Book match = controller.searchBookByTitleBinary(search);
                    books = new ArrayList<>();
                    if (match != null) {
                        books.add(match);
                    } else {
                        for (Book b : controller.getAllBooks()) {
                            if (b.getTitle().toLowerCase().contains(search.toLowerCase()) ||
                                    b.getAuthor().toLowerCase().contains(search.toLowerCase()) ||
                                    (b.getIsbn() != null && b.getIsbn().toLowerCase().contains(search.toLowerCase()))) {
                                books.add(b);
                            }
                        }
                    }
                } else {
                    books = controller.getSortedBooks(sort);
                }

                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < books.size(); i++) {
                    Book b = books.get(i);
                    sb.append(formatBookJson(b));
                    if (i < books.size() - 1) sb.append(",");
                }
                sb.append("]");
                sendJsonResponse(exchange, 200, sb.toString());

            } else if ("POST".equalsIgnoreCase(method)) {
                String body = readRequestBody(exchange);
                Map<String, String> json = parseSimpleJson(body);

                // Check if updating existing book
                if (json.containsKey("bookId") && !json.get("bookId").isEmpty()) {
                    Book b = new Book();
                    b.setBookId(Integer.parseInt(json.get("bookId")));
                    b.setTitle(json.getOrDefault("title", ""));
                    b.setAuthor(json.getOrDefault("author", ""));
                    b.setIsbn(json.getOrDefault("isbn", ""));
                    b.setPublisher(json.getOrDefault("publisher", ""));
                    b.setCategoryName(json.getOrDefault("categoryName", "General"));
                    b.setLanguage(json.getOrDefault("language", "English"));
                    b.setEdition(json.getOrDefault("edition", "1st"));
                    b.setDescription(json.getOrDefault("description", ""));
                    b.setShelfNumber(json.getOrDefault("shelfNumber", "A-1"));
                    b.setRackNumber(json.getOrDefault("rackNumber", "R1"));
                    try {
                        b.setTotalCopies(Integer.parseInt(json.getOrDefault("totalCopies", "5")));
                        b.setAvailableCopies(Integer.parseInt(json.getOrDefault("availableCopies", "5")));
                    } catch (Exception e) {
                        b.setTotalCopies(5);
                        b.setAvailableCopies(5);
                    }
                    boolean success = controller.updateBook(b);
                    if (success) {
                        sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Book updated successfully!\"}");
                    } else {
                        sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Failed to update book.\"}");
                    }
                    return;
                }

                Book b = new Book();
                b.setTitle(json.getOrDefault("title", ""));
                b.setAuthor(json.getOrDefault("author", ""));
                b.setIsbn(json.getOrDefault("isbn", ""));
                b.setPublisher(json.getOrDefault("publisher", ""));
                b.setCategoryName(json.getOrDefault("categoryName", "General"));
                b.setLanguage(json.getOrDefault("language", "English"));
                b.setEdition(json.getOrDefault("edition", "1st"));
                b.setDescription(json.getOrDefault("description", ""));
                b.setShelfNumber(json.getOrDefault("shelfNumber", "A-1"));
                b.setRackNumber(json.getOrDefault("rackNumber", "R1"));
                try {
                    b.setTotalCopies(Integer.parseInt(json.getOrDefault("totalCopies", "5")));
                    b.setAvailableCopies(Integer.parseInt(json.getOrDefault("availableCopies", json.getOrDefault("totalCopies", "5"))));
                } catch (Exception e) {
                    b.setTotalCopies(5);
                    b.setAvailableCopies(5);
                }

                boolean success = controller.addBook(b);
                if (success) {
                    sendJsonResponse(exchange, 201, "{\"success\":true,\"message\":\"Book added successfully!\"}");
                } else {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Failed to add book.\"}");
                }

            } else if ("PUT".equalsIgnoreCase(method)) {
                String body = readRequestBody(exchange);
                Map<String, String> json = parseSimpleJson(body);
                Book b = new Book();
                try {
                    b.setBookId(Integer.parseInt(json.get("bookId")));
                } catch (Exception e) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid Book ID\"}");
                    return;
                }
                b.setTitle(json.getOrDefault("title", ""));
                b.setAuthor(json.getOrDefault("author", ""));
                b.setIsbn(json.getOrDefault("isbn", ""));
                b.setPublisher(json.getOrDefault("publisher", ""));
                b.setCategoryName(json.getOrDefault("categoryName", "General"));
                b.setLanguage(json.getOrDefault("language", "English"));
                b.setDescription(json.getOrDefault("description", ""));
                b.setShelfNumber(json.getOrDefault("shelfNumber", "A-1"));
                try {
                    b.setTotalCopies(Integer.parseInt(json.getOrDefault("totalCopies", "5")));
                    b.setAvailableCopies(Integer.parseInt(json.getOrDefault("availableCopies", "5")));
                } catch (Exception e) {
                    b.setTotalCopies(5);
                    b.setAvailableCopies(5);
                }

                boolean success = controller.updateBook(b);
                if (success) {
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Book updated successfully!\"}");
                } else {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Failed to update book.\"}");
                }

            } else if ("DELETE".equalsIgnoreCase(method)) {
                Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
                String idStr = query.get("id");
                if (idStr != null) {
                    try {
                        int bookId = Integer.parseInt(idStr);
                        boolean success = controller.deleteBook(bookId);
                        if (success) {
                            sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Book deleted successfully!\"}");
                            return;
                        }
                    } catch (Exception ignored) {}
                }
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Could not delete book.\"}");
            } else {
                sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
            }
        }

        private String formatBookJson(Book b) {
            return String.format(
                    "{\"bookId\":%d,\"isbn\":\"%s\",\"title\":\"%s\",\"author\":\"%s\",\"publisher\":\"%s\",\"categoryName\":\"%s\",\"language\":\"%s\",\"edition\":\"%s\",\"description\":\"%s\",\"shelfNumber\":\"%s\",\"rackNumber\":\"%s\",\"floorNumber\":%d,\"totalCopies\":%d,\"availableCopies\":%d,\"coverImagePath\":\"%s\",\"qrCodePath\":\"%s\"}",
                    b.getBookId(), escapeJson(b.getIsbn()), escapeJson(b.getTitle()), escapeJson(b.getAuthor()),
                    escapeJson(b.getPublisher()), escapeJson(b.getCategoryName()), escapeJson(b.getLanguage()),
                    escapeJson(b.getEdition()), escapeJson(b.getDescription()), escapeJson(b.getShelfNumber()),
                    escapeJson(b.getRackNumber()), b.getFloorNumber(), b.getTotalCopies(), b.getAvailableCopies(),
                    escapeJson(b.getCoverImagePath()), escapeJson(b.getQrCodePath())
            );
        }
    }

    private class CategoriesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }

            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                List<Category> categories = controller.getAllCategories();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < categories.size(); i++) {
                    Category c = categories.get(i);
                    sb.append(String.format("{\"categoryId\":%d,\"categoryName\":\"%s\",\"description\":\"%s\",\"iconName\":\"%s\"}",
                            c.getCategoryId(), escapeJson(c.getCategoryName()), escapeJson(c.getDescription()), escapeJson(c.getIconName())));
                    if (i < categories.size() - 1) sb.append(",");
                }
                sb.append("]");
                sendJsonResponse(exchange, 200, sb.toString());
            } else if ("POST".equalsIgnoreCase(method)) {
                String body = readRequestBody(exchange);
                Map<String, String> json = parseSimpleJson(body);
                Category c = new Category(0, json.getOrDefault("categoryName", ""), json.getOrDefault("description", ""), json.getOrDefault("iconName", "book"));
                boolean success = controller.addCategory(c);
                if (success) {
                    sendJsonResponse(exchange, 201, "{\"success\":true,\"message\":\"Category created successfully!\"}");
                } else {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Failed to create category.\"}");
                }
            } else if ("DELETE".equalsIgnoreCase(method)) {
                Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
                try {
                    int id = Integer.parseInt(query.get("id"));
                    boolean success = controller.deleteCategory(id);
                    if (success) {
                        sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Category deleted.\"}");
                    } else {
                        sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Failed to delete category.\"}");
                    }
                } catch (Exception e) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid Category ID.\"}");
                }
            } else {
                sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
            }
        }
    }

    private class ReservationsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }

            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
                List<Reservation> list;
                if (query.containsKey("studentId")) {
                    int studentId = Integer.parseInt(query.get("studentId"));
                    list = controller.getReservationsByStudent(studentId);
                } else {
                    list = controller.getAllReservations();
                }

                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < list.size(); i++) {
                    Reservation r = list.get(i);
                    sb.append(String.format("{\"reservationId\":%d,\"studentId\":%d,\"studentName\":\"%s\",\"bookId\":%d,\"bookTitle\":\"%s\",\"reservationDate\":\"%s\",\"status\":\"%s\",\"queuePosition\":%d}",
                            r.getReservationId(), r.getStudentId(), escapeJson(r.getStudentName()), r.getBookId(),
                            escapeJson(r.getBookTitle()), r.getReservationDate() != null ? r.getReservationDate().toString() : "",
                            escapeJson(r.getStatus()), r.getQueuePosition()));
                    if (i < list.size() - 1) sb.append(",");
                }
                sb.append("]");
                sendJsonResponse(exchange, 200, sb.toString());
            } else if ("POST".equalsIgnoreCase(method)) {
                String body = readRequestBody(exchange);
                Map<String, String> json = parseSimpleJson(body);
                try {
                    int studentId = Integer.parseInt(json.get("studentId"));
                    int bookId = Integer.parseInt(json.get("bookId"));
                    boolean success = controller.reserveBook(studentId, bookId);
                    if (success) {
                        sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Book reserved successfully! You have joined the waiting queue.\"}");
                    } else {
                        sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Reservation failed or you are already in the waiting queue.\"}");
                    }
                } catch (Exception e) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid studentId or bookId.\"}");
                }
            } else if ("DELETE".equalsIgnoreCase(method)) {
                Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
                try {
                    int id = Integer.parseInt(query.get("id"));
                    boolean success = controller.cancelReservation(id);
                    if (success) {
                        sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Reservation cancelled.\"}");
                    } else {
                        sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Failed to cancel reservation.\"}");
                    }
                } catch (Exception e) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid reservation ID.\"}");
                }
            } else {
                sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
            }
        }
    }

    private class ReviewsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }

            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
                List<Review> reviews;
                double avgRating = 0.0;
                if (query.containsKey("bookId")) {
                    int bookId = Integer.parseInt(query.get("bookId"));
                    reviews = controller.getReviewsByBook(bookId);
                    avgRating = controller.getAverageRatingForBook(bookId);
                } else {
                    reviews = controller.getAllReviews();
                }

                StringBuilder sb = new StringBuilder("{\"averageRating\":").append(String.format(Locale.US, "%.1f", avgRating)).append(",\"reviews\":[");
                for (int i = 0; i < reviews.size(); i++) {
                    Review r = reviews.get(i);
                    sb.append(String.format("{\"reviewId\":%d,\"bookId\":%d,\"studentId\":%d,\"studentName\":\"%s\",\"rating\":%d,\"reviewText\":\"%s\",\"createdAt\":\"%s\"}",
                            r.getReviewId(), r.getBookId(), r.getStudentId(), escapeJson(r.getStudentName()), r.getRating(),
                            escapeJson(r.getReviewText()), r.getCreatedAt() != null ? r.getCreatedAt().toString() : ""));
                    if (i < reviews.size() - 1) sb.append(",");
                }
                sb.append("]}");
                sendJsonResponse(exchange, 200, sb.toString());
            } else if ("POST".equalsIgnoreCase(method)) {
                String body = readRequestBody(exchange);
                Map<String, String> json = parseSimpleJson(body);
                try {
                    int bookId = Integer.parseInt(json.get("bookId"));
                    int studentId = Integer.parseInt(json.get("studentId"));
                    int rating = Integer.parseInt(json.getOrDefault("rating", "5"));
                    String reviewText = json.getOrDefault("reviewText", "");

                    Review review = new Review(0, bookId, studentId, "", rating, reviewText, null);
                    boolean success = controller.addReview(review);
                    if (success) {
                        sendJsonResponse(exchange, 201, "{\"success\":true,\"message\":\"Review submitted successfully!\"}");
                    } else {
                        sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Failed to submit review.\"}");
                    }
                } catch (Exception e) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid review payload.\"}");
                }
            } else {
                sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
            }
        }
    }

    private class NotificationsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }

            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
                String userType = query.getOrDefault("userType", "ALL");
                int userId = 0;
                try {
                    if (query.containsKey("userId")) userId = Integer.parseInt(query.get("userId"));
                } catch (Exception ignored) {}

                List<Notification> list = controller.getNotificationsForUser(userType, userId);
                int unread = controller.getUnreadNotificationCount(userType, userId);

                StringBuilder sb = new StringBuilder("{\"unreadCount\":").append(unread).append(",\"notifications\":[");
                for (int i = 0; i < list.size(); i++) {
                    Notification n = list.get(i);
                    sb.append(String.format("{\"notificationId\":%d,\"userType\":\"%s\",\"userId\":%d,\"title\":\"%s\",\"message\":\"%s\",\"isRead\":%b,\"createdAt\":\"%s\"}",
                            n.getNotificationId(), escapeJson(n.getUserType()), n.getUserId(), escapeJson(n.getTitle()),
                            escapeJson(n.getMessage()), n.isRead(), n.getCreatedAt() != null ? n.getCreatedAt().toString() : ""));
                    if (i < list.size() - 1) sb.append(",");
                }
                sb.append("]}");
                sendJsonResponse(exchange, 200, sb.toString());
            } else if ("POST".equalsIgnoreCase(method)) {
                String body = readRequestBody(exchange);
                Map<String, String> json = parseSimpleJson(body);
                String userType = json.getOrDefault("userType", "ALL");
                int userId = Integer.parseInt(json.getOrDefault("userId", "0"));
                String title = json.getOrDefault("title", "Library Notification");
                String message = json.getOrDefault("message", "");

                boolean success = controller.createNotification(userType, userId, title, message);
                if (success) {
                    sendJsonResponse(exchange, 201, "{\"success\":true,\"message\":\"Notification sent!\"}");
                } else {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Failed to send notification.\"}");
                }
            } else if ("PUT".equalsIgnoreCase(method)) {
                Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
                if (query.containsKey("id")) {
                    int id = Integer.parseInt(query.get("id"));
                    controller.markNotificationAsRead(id);
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Marked as read.\"}");
                } else {
                    String userType = query.getOrDefault("userType", "ALL");
                    int userId = Integer.parseInt(query.getOrDefault("userId", "0"));
                    controller.markAllNotificationsAsRead(userType, userId);
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"All notifications marked as read.\"}");
                }
            } else {
                sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
            }
        }
    }

    private class FinesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }

            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
            List<FineHistory> list;
            if (query.containsKey("studentId")) {
                int studentId = Integer.parseInt(query.get("studentId"));
                list = controller.getFinesByStudent(studentId);
            } else {
                list = controller.getAllFines();
            }

            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                FineHistory fh = list.get(i);
                sb.append(String.format(Locale.US, "{\"fineId\":%d,\"studentId\":%d,\"studentName\":\"%s\",\"borrowId\":%d,\"bookTitle\":\"%s\",\"amount\":%.2f,\"reason\":\"%s\",\"paymentStatus\":\"%s\",\"paidAt\":\"%s\"}",
                        fh.getFineId(), fh.getStudentId(), escapeJson(fh.getStudentName()), fh.getBorrowId(),
                        escapeJson(fh.getBookTitle()), fh.getAmount(), escapeJson(fh.getReason()),
                        escapeJson(fh.getPaymentStatus()), fh.getPaidAt() != null ? fh.getPaidAt().toString() : ""));
                if (i < list.size() - 1) sb.append(",");
            }
            sb.append("]");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    private class PayFineHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }

            String body = readRequestBody(exchange);
            Map<String, String> json = parseSimpleJson(body);

            try {
                int studentId = Integer.parseInt(json.get("studentId"));
                boolean success;
                if (json.containsKey("fineId") && !json.get("fineId").isEmpty()) {
                    int fineId = Integer.parseInt(json.get("fineId"));
                    double amount = Double.parseDouble(json.getOrDefault("amount", "0.0"));
                    success = controller.payFine(fineId, studentId, amount);
                } else {
                    success = controller.payAllFinesForStudent(studentId);
                }

                if (success) {
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Fine payment processed successfully!\"}");
                } else {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Could not process fine payment.\"}");
                }
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid payment parameters.\"}");
            }
        }
    }

    private class AchievementsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }

            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
            int studentId = 0;
            try {
                if (query.containsKey("studentId")) studentId = Integer.parseInt(query.get("studentId"));
            } catch (Exception ignored) {}

            List<Achievement> list = controller.getStudentAchievements(studentId);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                Achievement a = list.get(i);
                sb.append(String.format("{\"achievementId\":%d,\"studentId\":%d,\"title\":\"%s\",\"badgeType\":\"%s\",\"unlockedAt\":\"%s\"}",
                        a.getAchievementId(), a.getStudentId(), escapeJson(a.getTitle()), escapeJson(a.getBadgeType()),
                        a.getUnlockedAt() != null ? a.getUnlockedAt().toString() : ""));
                if (i < list.size() - 1) sb.append(",");
            }
            sb.append("]");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    private class ReportExportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }

            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
            String type = query.getOrDefault("type", "books").toLowerCase();

            File tempFile = File.createTempFile("libraai_report_", ".csv");
            tempFile.deleteOnExit();

            if ("students".equals(type)) {
                controller.exportStudentsCsv(tempFile.getAbsolutePath());
            } else if ("circulations".equals(type) || "borrow".equals(type)) {
                controller.exportBorrowHistoryCsv(tempFile.getAbsolutePath());
            } else {
                controller.exportBooksCsv(tempFile.getAbsolutePath());
            }

            exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"libraai_" + type + "_report.csv\"");
            addCorsHeaders(exchange);
            exchange.sendResponseHeaders(200, tempFile.length());

            try (OutputStream os = exchange.getResponseBody();
                 FileInputStream fis = new FileInputStream(tempFile)) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = fis.read(buf)) > 0) {
                    os.write(buf, 0, n);
                }
            }
        }
    }

    private class IssueBookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }

            String body = readRequestBody(exchange);
            Map<String, String> json = parseSimpleJson(body);
            try {
                int studentId = Integer.parseInt(json.get("studentId"));
                int bookId = Integer.parseInt(json.get("bookId"));
                int loanDays = Integer.parseInt(json.getOrDefault("loanDays", "14"));

                boolean success = controller.issueBook(studentId, bookId, loanDays);
                if (success) {
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Book issued successfully!\"}");
                } else {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Failed to issue book. Check student borrow limit or book availability.\"}");
                }
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid request parameter formats.\"}");
            }
        }
    }

    private class ReturnBookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }

            String body = readRequestBody(exchange);
            Map<String, String> json = parseSimpleJson(body);
            try {
                int borrowId = Integer.parseInt(json.get("borrowId"));
                int studentId = Integer.parseInt(json.get("studentId"));
                int bookId = Integer.parseInt(json.get("bookId"));
                Timestamp dueDate = new Timestamp(System.currentTimeMillis());

                boolean success = controller.returnBook(borrowId, studentId, bookId, dueDate);
                if (success) {
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Book returned successfully!\"}");
                } else {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Failed to return book.\"}");
                }
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid request format.\"}");
            }
        }
    }

    private class BorrowHistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }

            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
            String studentIdStr = query.get("studentId");

            List<BorrowHistory> history;
            if (studentIdStr != null && !studentIdStr.isEmpty()) {
                try {
                    int studentId = Integer.parseInt(studentIdStr);
                    history = controller.getStudentBorrowHistory(studentId);
                } catch (Exception e) {
                    history = controller.getAllBorrowHistory();
                }
            } else {
                history = controller.getAllBorrowHistory();
            }

            long now = System.currentTimeMillis();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < history.size(); i++) {
                BorrowHistory bh = history.get(i);
                long overdueDays = 0;
                if (bh.getReturnDate() == null && bh.getDueDate() != null) {
                    long diff = now - bh.getDueDate().getTime();
                    if (diff > 0) overdueDays = diff / (1000L * 60 * 60 * 24);
                }
                sb.append(String.format(Locale.US,
                        "{\"borrowId\":%d,\"studentId\":%d,\"studentName\":\"%s\",\"studentCode\":\"%s\",\"bookId\":%d,\"bookTitle\":\"%s\",\"isbn\":\"%s\",\"borrowDate\":\"%s\",\"dueDate\":\"%s\",\"returnDate\":\"%s\",\"status\":\"%s\",\"fineAmount\":%.2f,\"finePaid\":%b,\"overdueDays\":%d}",
                        bh.getBorrowId(), bh.getStudentId(), escapeJson(bh.getStudentName()), escapeJson(bh.getStudentCode()),
                        bh.getBookId(), escapeJson(bh.getBookTitle()), escapeJson(bh.getIsbn()),
                        bh.getBorrowDate() != null ? bh.getBorrowDate().toString() : "",
                        bh.getDueDate() != null ? bh.getDueDate().toString() : "",
                        bh.getReturnDate() != null ? bh.getReturnDate().toString() : "",
                        escapeJson(bh.getStatus()), bh.getFineAmount(), bh.isFinePaid(), overdueDays
                ));
                if (i < history.size() - 1) sb.append(",");
            }
            sb.append("]");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    private class OverdueHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            List<BorrowHistory> allHistory = controller.getAllBorrowHistory();
            long now = System.currentTimeMillis();
            List<BorrowHistory> overdue = new ArrayList<>();
            for (BorrowHistory bh : allHistory) {
                if (bh.getReturnDate() == null && bh.getDueDate() != null && now > bh.getDueDate().getTime()) {
                    overdue.add(bh);
                }
            }
            StringBuilder sb = new StringBuilder("{\"overdueCount\":");
            sb.append(overdue.size()).append(",\"overdueStudents\":[");
            Set<String> seen = new java.util.LinkedHashSet<>();
            List<String> studentEntries = new ArrayList<>();
            for (BorrowHistory bh : overdue) {
                if (!seen.contains(bh.getStudentCode())) {
                    seen.add(bh.getStudentCode());
                    long days = (now - bh.getDueDate().getTime()) / (1000L * 60 * 60 * 24);
                    studentEntries.add(String.format(Locale.US,
                            "{\"studentId\":%d,\"studentName\":\"%s\",\"studentCode\":\"%s\",\"bookTitle\":\"%s\",\"daysOverdue\":%d,\"fineAmount\":%.2f}",
                            bh.getStudentId(), escapeJson(bh.getStudentName()), escapeJson(bh.getStudentCode()),
                            escapeJson(bh.getBookTitle()), days, bh.getFineAmount()
                    ));
                }
            }
            sb.append(String.join(",", studentEntries)).append("]}");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    private class StudentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }

            List<Student> students = controller.getAllStudents();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < students.size(); i++) {
                Student s = students.get(i);
                sb.append(String.format(Locale.US,
                        "{\"id\":%d,\"studentCode\":\"%s\",\"fullName\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"department\":\"%s\",\"yearOfStudy\":%d,\"maxBorrowLimit\":%d,\"currentBorrowed\":%d,\"totalFinesOwed\":%.2f,\"status\":\"%s\",\"passwordHash\":\"%s\"}",
                        s.getId(), escapeJson(s.getStudentCode()), escapeJson(s.getFullName()), escapeJson(s.getEmail()),
                        escapeJson(s.getPhone()), escapeJson(s.getDepartment()), s.getYearOfStudy(),
                        s.getMaxBorrowLimit(), s.getCurrentBorrowed(), s.getTotalFinesOwed(), escapeJson(s.getStatus()),
                        escapeJson(s.getPasswordHash())
                ));
                if (i < students.size() - 1) sb.append(",");
            }
            sb.append("]");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    private class StudentStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            String method = exchange.getRequestMethod();
            if (!"PUT".equalsIgnoreCase(method) && !"POST".equalsIgnoreCase(method)) {
                sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }

            String body = readRequestBody(exchange);
            Map<String, String> json = parseSimpleJson(body);
            try {
                int studentId = Integer.parseInt(json.get("studentId"));
                String status = json.getOrDefault("status", "ACTIVE");
                boolean success = controller.updateStudentStatus(studentId, status);
                if (success) {
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Student status updated to " + status + "\"}");
                } else {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Failed to update status.\"}");
                }
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid payload.\"}");
            }
        }
    }

    // Static File Server
    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String uri = exchange.getRequestURI().getPath();
            if (uri.equals("/")) {
                uri = "/index.html";
            }

            // Path security check
            if (uri.contains("..")) {
                sendJsonResponse(exchange, 403, "{\"error\":\"Access Denied\"}");
                return;
            }

            // 1. Look in src/web
            File file = new File("src/web" + uri);

            // 2. Look in src/web/html
            if (!file.exists() || file.isDirectory()) {
                file = new File("src/web/html" + uri);
            }

            // 3. Look in root directory or assets/
            if (!file.exists() || file.isDirectory()) {
                if (uri.startsWith("/")) {
                    file = new File("." + uri);
                }
            }

            if (!file.exists() || file.isDirectory()) {
                String errorHtml = "<html><body><h1>404 Not Found</h1><p>The requested URL " + uri + " was not found on this server.</p></body></html>";
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                addCorsHeaders(exchange);
                byte[] b = errorHtml.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, b.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(b);
                }
                return;
            }

            String contentType = getContentType(file.getName());
            exchange.getResponseHeaders().set("Content-Type", contentType);
            addCorsHeaders(exchange);
            exchange.sendResponseHeaders(200, file.length());

            try (OutputStream os = exchange.getResponseBody();
                 FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = fis.read(buffer)) >= 0) {
                    os.write(buffer, 0, count);
                }
            }
        }

        private String getContentType(String fileName) {
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=UTF-8";
            if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
            if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".svg")) return "image/svg+xml";
            if (lower.endsWith(".ico")) return "image/x-icon";
            return "application/octet-stream";
        }
    }

    private class AttendanceLogsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }

            List<model.AttendanceLog> logs = attendanceDAO.getAttendanceLogs();
            int inside = attendanceDAO.getCurrentlyInsideCount();
            int todayVisits = attendanceDAO.getTodayTotalVisits();

            StringBuilder sb = new StringBuilder("{");
            sb.append("\"success\":true,");
            sb.append("\"currentlyInside\":").append(inside).append(",");
            sb.append("\"totalVisitsToday\":").append(todayVisits).append(",");
            sb.append("\"logs\":[");
            for (int i = 0; i < logs.size(); i++) {
                model.AttendanceLog log = logs.get(i);
                sb.append("{");
                sb.append("\"attendanceId\":").append(log.getAttendanceId()).append(",");
                sb.append("\"studentId\":").append(log.getStudentId()).append(",");
                sb.append("\"studentCode\":\"").append(escapeJson(log.getStudentCode())).append("\",");
                sb.append("\"studentName\":\"").append(escapeJson(log.getStudentName())).append("\",");
                sb.append("\"department\":\"").append(escapeJson(log.getDepartment())).append("\",");
                sb.append("\"checkInTime\":\"").append(log.getCheckInTime() != null ? log.getCheckInTime().toString() : "").append("\",");
                sb.append("\"checkOutTime\":\"").append(log.getCheckOutTime() != null ? log.getCheckOutTime().toString() : "").append("\",");
                sb.append("\"status\":\"").append(log.getStatus()).append("\"");
                sb.append("}");
                if (i < logs.size() - 1) sb.append(",");
            }
            sb.append("]");
            sb.append("}");

            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    private class AttendanceScanHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }

            String body = readRequestBody(exchange);
            Map<String, String> json = parseSimpleJson(body);
            String qrData = json.getOrDefault("qrData", "").trim();

            if (qrData.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Missing qrData parameter.\"}");
                return;
            }

            String studentCode = qrData;
            if (qrData.startsWith("libraai:student:")) {
                String[] parts = qrData.split(":");
                if (parts.length >= 3) {
                    studentCode = parts[2];
                }
            }

            model.Student student = userDAO.getStudentByCode(studentCode);
            if (student == null) {
                sendJsonResponse(exchange, 404, "{\"success\":false,\"message\":\"Student not found in database.\"}");
                return;
            }

            Timestamp now = new Timestamp(System.currentTimeMillis());
            model.AttendanceLog active = attendanceDAO.getActiveAttendance(student.getId());
            String action;
            boolean dbSuccess;

            if (active != null) {
                long secondsInside = (now.getTime() - active.getCheckInTime().getTime()) / 1000L;
                if (secondsInside < 15) {
                    long remaining = 15 - secondsInside;
                    sendJsonResponse(exchange, 429,
                            String.format("{\"success\":false,\"message\":\"Too soon! Please wait %d more second%s before leaving.\",\"remainingSeconds\":%d}",
                                    remaining, remaining == 1 ? "" : "s", remaining));
                    return;
                }
                dbSuccess = attendanceDAO.checkOut(active.getAttendanceId(), now);
                action = "CHECK_OUT";
            } else {
                dbSuccess = attendanceDAO.checkIn(student.getId(), now);
                action = "CHECK_IN";
            }

            if (dbSuccess) {
                int inside = attendanceDAO.getCurrentlyInsideCount();
                int todayVisits = attendanceDAO.getTodayTotalVisits();

                String responseJson = String.format(Locale.US,
                        "{\"success\":true,\"action\":\"%s\",\"studentName\":\"%s\",\"studentCode\":\"%s\",\"department\":\"%s\",\"timestamp\":\"%s\",\"currentlyInside\":%d,\"totalVisitsToday\":%d}",
                        action,
                        escapeJson(student.getFullName()),
                        escapeJson(student.getStudentCode()),
                        escapeJson(student.getDepartment()),
                        now.toString(),
                        inside,
                        todayVisits
                );
                sendJsonResponse(exchange, 200, responseJson);
            } else {
                sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"Database operation failed.\"}");
            }
        }
    }

    private class TopVisitorsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }

            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
            int limit = 10;
            try {
                if (query.containsKey("limit")) {
                    limit = Integer.parseInt(query.get("limit"));
                }
            } catch (Exception ignored) {}

            List<Map<String, Object>> visitors = attendanceDAO.getTopLibraryVisitors(limit);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < visitors.size(); i++) {
                Map<String, Object> visitor = visitors.get(i);
                sb.append("{");
                sb.append("\"studentId\":").append(visitor.get("studentId")).append(",");
                sb.append("\"studentCode\":\"").append(escapeJson((String) visitor.get("studentCode"))).append("\",");
                sb.append("\"fullName\":\"").append(escapeJson((String) visitor.get("fullName"))).append("\",");
                sb.append("\"department\":\"").append(escapeJson((String) visitor.get("department"))).append("\",");
                sb.append("\"totalSeconds\":").append(visitor.get("totalSeconds"));
                sb.append("}");
                if (i < visitors.size() - 1) sb.append(",");
            }
            sb.append("]");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }
}
