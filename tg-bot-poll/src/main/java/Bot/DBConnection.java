package Bot;

import java.sql.*;
import java.util.*;

public class DBConnection {
    
    // ================= ПОДКЛЮЧЕНИЕ =================
    
    public static Connection connect() {
        String jdbcURL = "jdbc:postgresql://localhost:5432/TGBot?currentSchema=public";
        String username = "bot";
        String password = "12345";
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("Драйвер PostgreSQL не найден! Проверьте зависимости.");
            e.printStackTrace();
        }
        try {
            return DriverManager.getConnection(jdbcURL, username, password);
        } catch (SQLException e) {
            System.err.println("Ошибка подключения к БД: " + e.getMessage());
            return null;
        }
    }

    // ================= РЕГИСТРАЦИЯ И ПОЛЬЗОВАТЕЛИ =================

    /**
     * Сохраняет нового пользователя и его теги в БД.
     */
public static void sendRegisteredUser(Long chatId, String userName, String userFamilyName, ArrayList<String> userTags) throws SQLException {
    String sqlUpsertUser = """
        INSERT INTO users (id_user, chatid, userstate, name, familyname) 
        VALUES (?, ?, 'REGISTERED', ?, ?)
        ON CONFLICT (chatid) DO UPDATE 
        SET userstate = 'REGISTERED',
            name = EXCLUDED.name,
            familyname = EXCLUDED.familyname
        RETURNING id_user
        """;
    String sqlDeleteOldTags = "DELETE FROM usertags WHERE id_user = ?";
    String sqlGetTag = "SELECT id_tag FROM tag WHERE callbackdata = ?";
    String sqlLinkTag = "INSERT INTO usertags (id_tag, id_user) VALUES (?, ?)";
    
    try (Connection conn = connect()) {
        // Включаем транзакцию
        conn.setAutoCommit(false);
        
        try {
            UUID userId;
            
            // 1. UPSERT пользователя
            try (PreparedStatement stmt = conn.prepareStatement(sqlUpsertUser)) {
                stmt.setObject(1, UUID.randomUUID());
                stmt.setLong(2, chatId);
                stmt.setString(3, userName);
                stmt.setString(4, userFamilyName);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        userId = rs.getObject("id_user", UUID.class);
                    } else {
                        throw new SQLException("Не удалось получить id_user");
                    }
                }
            }
            
            // 2. Удаляем старые теги
            try (PreparedStatement stmt = conn.prepareStatement(sqlDeleteOldTags)) {
                stmt.setObject(1, userId);
                stmt.executeUpdate();
            }
            
            // 3. Добавляем новые теги
            if (userTags != null && !userTags.isEmpty()) {
                try (PreparedStatement stmtTag = conn.prepareStatement(sqlGetTag);
                     PreparedStatement stmtLink = conn.prepareStatement(sqlLinkTag)) {
                    
                    for (String tagCallbackData : userTags) {
                        stmtTag.setString(1, tagCallbackData);
                        try (ResultSet rs = stmtTag.executeQuery()) {
                            if (rs.next()) {
                                UUID tagId = rs.getObject("id_tag", UUID.class);
                                stmtLink.setObject(1, tagId);
                                stmtLink.setObject(2, userId);
                                stmtLink.addBatch();  // ← пакетная вставка
                            }
                        }
                    }
                    stmtLink.executeBatch();
                }
            }
            
            // Фиксируем транзакцию
            conn.commit();
            
        } catch (SQLException e) {
            // Откатываем при ошибке
            conn.rollback();
            throw e;
        } finally {
            // Возвращаем режим auto-commit
            conn.setAutoCommit(true);
        }
    }
}
    /**
     * Проверяет, зарегистрирован ли пользователь.
     */
    public static boolean isUserRegistered(Long chatId) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE chatid = ?";
        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, chatId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Получает состояние пользователя (userState).
     */
    public static String getUserState(Long chatId) throws SQLException {
        String sql = "SELECT userstate FROM users WHERE chatid = ?";
        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, chatId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getString("userstate");
            }
        }
        return null;
    }

    /**
     * Обновляет состояние пользователя.
     */
    public static void updateUserState(Long chatId, String newState) throws SQLException {
        String sql = "UPDATE users SET userstate = ? WHERE chatid = ?";
        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newState);
            stmt.setLong(2, chatId);
            stmt.executeUpdate();
        }
    }

    /**
     * Получает информацию о пользователе для команды "Кто я?".
     */
    /**
     * Возвращает информацию о пользователе в виде Map:
     * - "name" -> String (имя пользователя)
     * - "tags" -> Map<String, String> (callbackdata → name тега)
     */
  /**
 * Возвращает список callbackdata тегов пользователя.
 */
public static ArrayList<String> getUserTagCallbacks(Long chatId) throws SQLException {
    String sql = """
        SELECT t.callbackdata
        FROM usertags ut
        JOIN tag t ON ut.id_tag = t.id_tag
        JOIN users u ON ut.id_user = u.id_user
        WHERE u.chatid = ?
        """;
    
    ArrayList<String> tags = new ArrayList<>();
    try (Connection conn = connect();
         PreparedStatement stmt = conn.prepareStatement(sql)) {
        
        stmt.setLong(1, chatId);
        
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                tags.add(rs.getString("callbackdata"));
            }
        }
    }
    return tags;
}
    // ================= ОПРОСЫ =================

    /**
     * Получает список ID опросов, доступных пользователю (по тегам) и ещё не пройденных им.
     */
    
    public static List<PollInfo> getAvailablePolls(Long chatId) throws SQLException {
        String sql = """
            SELECT DISTINCT p.id_poll, p.description,
                   (SELECT COUNT(*) FROM pollquestion WHERE id_poll = p.id_poll) AS question_count
            FROM poll p
            JOIN polltags pt ON p.id_poll = pt.id_poll
            JOIN usertags ut ON pt.id_tag = ut.id_tag
            JOIN users u ON ut.id_user = u.id_user
            WHERE u.chatid = ?
            AND p.id_poll NOT IN (
                SELECT q.id_poll
                FROM pollquestion q
                JOIN useranswers ua ON q.id_question = ua.id_pollquestion
                WHERE ua.id_user = u.id_user
                GROUP BY q.id_poll
                HAVING COUNT(DISTINCT q.id_question) = (
                    SELECT COUNT(*) FROM pollquestion sub_q WHERE sub_q.id_poll = p.id_poll
                )
            )
            """;
        
        List<PollInfo> polls = new ArrayList<>();
        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, chatId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID pollId = rs.getObject("id_poll", UUID.class);
                    String description = rs.getString("description");
                    int questionCount = rs.getInt("question_count");
                    
                    polls.add(new PollInfo(pollId, description, questionCount));
                }
            }
        }
        return polls;
    }

    /**
     * Вспомогательный класс для хранения информации об опросе.
     */
    public static class PollInfo {
        public UUID id;
        public String description;
        public int questionCount;
        
        public PollInfo(UUID id, String description, int questionCount) {
            this.id = id;
            this.description = description;
            this.questionCount = questionCount;
        }
    }
    
    
    
    
    /**
     * Загружает полный объект опроса (с вопросами и вариантами) из БД по его UUID.
     */
    public static TestPoll getPollFromDB(UUID pollId) throws SQLException {
        String sqlPoll = "SELECT description, isanonymous FROM poll WHERE id_poll = ?";
        String sqlQuestions = "SELECT id_question, type, description FROM pollquestion WHERE id_poll = ? ORDER BY id_question";
        String sqlVariants = "SELECT description FROM pollquestionvariants WHERE id_question = ?";
        
        TestPoll poll = null;
        try (Connection conn = connect()) {
            try (PreparedStatement stmt = conn.prepareStatement(sqlPoll)) {
                stmt.setObject(1, pollId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        poll = new TestPoll(pollId,rs.getString("description"), rs.getBoolean("isanonymous"));
                        poll.id = pollId;
                    }
                }
            }
            
            if (poll != null) {
                try (PreparedStatement stmt = conn.prepareStatement(sqlQuestions)) {
                    stmt.setObject(1, pollId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            UUID qId = rs.getObject("id_question", UUID.class);
                            QuestionType type = QuestionType.valueOf(rs.getString("type"));
                            TestQuestion question = new TestQuestion(type, rs.getString("description"));
                            question.id = qId;
                            
                            try (PreparedStatement stmtV = conn.prepareStatement(sqlVariants)) {
                                stmtV.setObject(1, qId);
                                try (ResultSet rsV = stmtV.executeQuery()) {
                                    while (rsV.next()) {
                                        question.variants.add(rsV.getString("description"));
                                    }
                                }
                            }
                            poll.questions.add(question);
                        }
                    }
                }
            }
        }
        return poll;
    }
/**
 * Удаляет ответы пользователя из бд, для тестирования
 * @throws SQLException 
 */
    public static void deleteAnswers(Long chatId) throws SQLException {
    	String sql ="DELETE FROM useranswers  WHERE id_user = (SELECT FROM users.chatid= ?)";
    	try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)){
    		stmt.setObject(1, chatId);
    		stmt.executeUpdate();
    	}
    }
    
    
    
    
    /**
     * Сохраняет или обновляет ответ пользователя на вопрос.
     */
    public static void saveUserAnswer(UUID userId, UUID questionId, String answer) throws SQLException {
        String sql = """
            INSERT INTO useranswers (id_user, id_pollquestion, answer) 
            VALUES (?, ?, ?)
            ON CONFLICT (id_user, id_pollquestion) 
            DO UPDATE SET answer = EXCLUDED.answer, timestamp = CURRENT_TIMESTAMP
            """;
        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            stmt.setObject(2, questionId);
            stmt.setString(3, answer);
            stmt.executeUpdate();
        }
    }

    // ================= СЕМЕЙСТВА ТЕГОВ =================

    public static class TagFamilyInfo {
        public UUID id;
        public String name;
        public String description;
        public List<TagInfo> tags = new ArrayList<>();
        
        public TagFamilyInfo(UUID id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
        }
    }

    public static class TagInfo {
        public UUID id;
        public String name;
        public String callbackData;
        
        public TagInfo(UUID id, String name, String callbackData) {
            this.id = id;
            this.name = name;
            this.callbackData = callbackData;
        }
    }

    /**
     * Загружает все семейства тегов с их тегами.
     */
    public static List<TagFamilyInfo> getAllTagFamilies() throws SQLException {
        String sql = """
            SELECT tf.id_tagfamily, tf.name AS familyname, tf.description,
                   t.id_tag, t.name AS tagname, t.callbackdata
            FROM tagfamily tf
            LEFT JOIN tag t ON tf.id_tagfamily = t.id_tagfamily
            ORDER BY tf.id_tagfamily, t.name
            """;
        
        Map<UUID, TagFamilyInfo> familiesMap = new LinkedHashMap<>();
        
        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                UUID familyId = rs.getObject("id_tagfamily", UUID.class);
                TagFamilyInfo family = familiesMap.get(familyId);
                if (family == null) {
                    family = new TagFamilyInfo(
                        familyId,
                        rs.getString("familyname"),
                        rs.getString("description")
                    );
                    familiesMap.put(familyId, family);
                }
                
                UUID tagId = rs.getObject("id_tag", UUID.class);
                if (tagId != null) {
                    family.tags.add(new TagInfo(
                        tagId,
                        rs.getString("tagname"),
                        rs.getString("callbackdata")
                    ));
                }
            }
        }
        return new ArrayList<>(familiesMap.values());
    }

    /**
     * Получает id_Tag по его callbackData.
     */
    public static UUID getTagIdByCallbackData(String callbackData) throws SQLException {
        String sql = "SELECT id_tag FROM tag WHERE callbackdata = ?";
        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, callbackData);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getObject("id_tag", UUID.class);
            }
        }
        return null;
    }

    /**
     * Получает id семейства тега по его callbackData.
     */
    public static UUID getTagFamilyIdByCallbackData(String callbackData) throws SQLException {
        String sql = "SELECT id_tagfamily FROM tag WHERE callbackdata = ?";
        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, callbackData);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getObject("id_tagfamily", UUID.class);
            }
            conn.close();
        }

        return null;
    }
}