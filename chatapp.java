import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Timer;
import javax.swing.*;
import javax.swing.border.*;

// ─── Entry Point ────────────────────────────────────────────────────────────
public class talkapp {

    static final String DB_URL  = "jdbc:mysql://localhost:3306/chatapp_db"
                                + "?useSSL=false&serverTimezone=Asia/Kolkata"
                                + "&allowPublicKeyRetrieval=true"
                                + "&createDatabaseIfNotExist=true";
    static final String DB_USER = "root";
    static final String DB_PASSWORD = "your pass";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                DB.connect();
                DB.createTables();
                new LoginFrame().setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,
                    "Startup failed:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}


// ─── Database ────────────────────────────────────────────────────────────────
class DB {
    private static Connection conn;

    static void connect() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(talkapp.DB_URL, talkapp.DB_USER, talkapp.DB_PASSWORD);
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver not found. Add mysql-connector-j to classpath.");
        }
    }

    static Connection get() throws SQLException {
        if (conn == null || conn.isClosed()) connect();
        return conn;
    }

    static void close() {
        try { if (conn != null) conn.close(); } catch (SQLException ignored) {}
    }

    static void createTables() throws SQLException {
        Statement st = get().createStatement();
        st.execute("""
            CREATE TABLE IF NOT EXISTS users (
                user_id    INT AUTO_INCREMENT PRIMARY KEY,
                username   VARCHAR(50)  NOT NULL UNIQUE,
                password   VARCHAR(255) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )""");
        st.execute("""
            CREATE TABLE IF NOT EXISTS messages (
                message_id  INT AUTO_INCREMENT PRIMARY KEY,
                sender_id   INT  NOT NULL,
                receiver_id INT  NOT NULL,
                content     TEXT NOT NULL,
                sent_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (sender_id)   REFERENCES users(user_id),
                FOREIGN KEY (receiver_id) REFERENCES users(user_id)
            )""");
        st.close();
    }

    // ── User queries ──────────────────────────────────────────────────────────

    /** Returns the logged-in User, or null if credentials are wrong. */
    static User login(String username, String password) throws SQLException {
        PreparedStatement ps = get().prepareStatement(
            "SELECT user_id, username FROM users WHERE username=? AND password=?");
        ps.setString(1, username); ps.setString(2, password);
        ResultSet rs = ps.executeQuery();
        User user = rs.next() ? new User(rs.getInt("user_id"), rs.getString("username")) : null;
        ps.close();
        return user;
    }

    /** Returns the new User, or null if the username is already taken. */
    static User signup(String username, String password) throws SQLException {
        PreparedStatement check = get().prepareStatement(
            "SELECT user_id FROM users WHERE username=?");
        check.setString(1, username);
        if (check.executeQuery().next()) { check.close(); return null; }
        check.close();

        PreparedStatement ps = get().prepareStatement(
            "INSERT INTO users (username, password) VALUES (?, ?)",
            Statement.RETURN_GENERATED_KEYS);
        ps.setString(1, username); ps.setString(2, password);
        ps.executeUpdate();
        ResultSet keys = ps.getGeneratedKeys();
        User user = keys.next() ? new User(keys.getInt(1), username) : null;
        ps.close();
        return user;
    }

    static java.util.List<User> getOtherUsers(int myId) throws SQLException {
        PreparedStatement ps = get().prepareStatement(
            "SELECT user_id, username FROM users WHERE user_id<>? ORDER BY username");
        ps.setInt(1, myId);
        ResultSet rs = ps.executeQuery();
        java.util.List<User> list = new java.util.ArrayList<>();
        while (rs.next()) list.add(new User(rs.getInt("user_id"), rs.getString("username")));
        ps.close();
        return list;
    }

    // ── Message queries ───────────────────────────────────────────────────────

    static void sendMessage(int senderId, int receiverId, String content) throws SQLException {
        PreparedStatement ps = get().prepareStatement(
            "INSERT INTO messages (sender_id, receiver_id, content) VALUES (?, ?, ?)");
        ps.setInt(1, senderId); ps.setInt(2, receiverId); ps.setString(3, content);
        ps.executeUpdate();
        ps.close();
    }

    static java.util.List<Message> getMessages(int myId, int otherId) throws SQLException {
        PreparedStatement ps = get().prepareStatement("""
            SELECT m.content, m.sent_at, u.username AS sender, m.sender_id
            FROM messages m JOIN users u ON m.sender_id = u.user_id
            WHERE (m.sender_id=? AND m.receiver_id=?) OR (m.sender_id=? AND m.receiver_id=?)
            ORDER BY m.sent_at ASC""");
        ps.setInt(1, myId); ps.setInt(2, otherId);
        ps.setInt(3, otherId); ps.setInt(4, myId);
        ResultSet rs = ps.executeQuery();

        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a, MMM dd");
        java.util.List<Message> list = new java.util.ArrayList<>();
        while (rs.next())
            list.add(new Message(rs.getString("sender"),
                                 rs.getString("content"),
                                 sdf.format(rs.getTimestamp("sent_at")),
                                 rs.getInt("sender_id") == myId));
        ps.close();
        return list;
    }

    static int countMessages(int id1, int id2) throws SQLException {
        PreparedStatement ps = get().prepareStatement(
            "SELECT COUNT(*) FROM messages WHERE (sender_id=? AND receiver_id=?) OR (sender_id=? AND receiver_id=?)");
        ps.setInt(1, id1); ps.setInt(2, id2); ps.setInt(3, id2); ps.setInt(4, id1);
        ResultSet rs = ps.executeQuery();
        int count = rs.next() ? rs.getInt(1) : 0;
        ps.close();
        return count;
    }
}

// ─── Simple Data Classes ─────────────────────────────────────────────────────
class User {
    final int    id;
    final String name;
    User(int id, String name) { this.id = id; this.name = name; }
    @Override public String toString() { return name; }
}

class Message {
    final String  sender, content, time;
    final boolean mine;
    Message(String sender, String content, String time, boolean mine) {
        this.sender = sender; this.content = content; this.time = time; this.mine = mine;
    }
}

// ─── Login / Signup Frame ─────────────────────────────────────────────────────
class LoginFrame extends JFrame {

    // Shared colors used across the whole app
    static final Color BG       = new Color(18,  18,  35);
    static final Color ACCENT   = new Color(99,  102, 241);
    static final Color ACCENT2  = new Color(139, 92,  246);
    static final Color TEXT     = new Color(240, 240, 255);
    static final Color SUBTEXT  = new Color(150, 150, 180);
    static final Color FIELD_BG = new Color(40,  40,  70);

    private JTextField     usernameField;
    private JPasswordField passwordField;
    private JLabel         statusLabel;
    private JButton        primaryBtn;
    private boolean        isLogin = true;

    LoginFrame() {
        setTitle("ChatApp — Login");
        setSize(420, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
        buildUI();
    }

    private void buildUI() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(40, 50, 40, 50));

        root.add(centered(bigLabel("Chat", 52, ACCENT)));
        root.add(centered(bigLabel("ChatApp", 26, TEXT)));
        root.add(gap(6));
        root.add(centered(smallLabel("Connect & Chat in real time")));
        root.add(gap(24));

        root.add(fieldLabel("Username"));
        usernameField = styledTextField();
        root.add(usernameField);
        root.add(gap(10));

        root.add(fieldLabel("Password"));
        passwordField = styledPasswordField();
        root.add(passwordField);
        root.add(gap(20));

        primaryBtn = styledButton("Login", ACCENT);
        primaryBtn.addActionListener(e -> submit());
        root.add(primaryBtn);
        root.add(gap(8));

        JButton toggleBtn = styledButton("Don't have an account? Sign Up", FIELD_BG);
        toggleBtn.setForeground(ACCENT2);
        toggleBtn.addActionListener(e -> {
            isLogin = !isLogin;
            primaryBtn.setText(isLogin ? "Login" : "Sign Up");
            toggleBtn.setText(isLogin ? "Don't have an account? Sign Up" : "Already have an account? Login");
            statusLabel.setText(" ");
            setTitle(isLogin ? "ChatApp — Login" : "ChatApp — Sign Up");
        });
        root.add(toggleBtn);
        root.add(gap(8));

        statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(239, 68, 68));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        root.add(statusLabel);

        // Enter key submits
        ActionListener enter = e -> submit();
        usernameField.addActionListener(enter);
        passwordField.addActionListener(enter);

        add(root);
        getContentPane().setBackground(BG);
    }

    private void submit() {
        String user = usernameField.getText().trim();
        String pass = new String(passwordField.getPassword()).trim();

        if (user.isEmpty() || pass.isEmpty()) { showStatus("Please fill in all fields.", false); return; }

        try {
            User loggedIn;
            if (isLogin) {
                loggedIn = DB.login(user, pass);
                if (loggedIn == null) { showStatus("Invalid username or password.", false); passwordField.setText(""); return; }
            } else {
                if (user.length() < 3) { showStatus("Username must be at least 3 characters.", false); return; }
                if (pass.length() < 4) { showStatus("Password must be at least 4 characters.", false); return; }
                loggedIn = DB.signup(user, pass);
                if (loggedIn == null) { showStatus("Username already taken. Try another.", false); return; }
            }
            User finalUser = loggedIn;
            showStatus("Welcome, " + finalUser.name + "!", true);
            SwingUtilities.invokeLater(() -> { new ChatFrame(finalUser).setVisible(true); dispose(); });
        } catch (SQLException e) {
            showStatus("DB Error: " + e.getMessage(), false);
        }
    }

    private void showStatus(String msg, boolean ok) {
        statusLabel.setForeground(ok ? new Color(74, 222, 128) : new Color(239, 68, 68));
        statusLabel.setText(msg);
    }

    // ── UI helpers ────────────────────────────────────────────────────────────
    private JLabel bigLabel(String text, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, size));
        l.setForeground(color);
        return l;
    }

    private JLabel smallLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        l.setForeground(SUBTEXT);
        return l;
    }

    private JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 13));
        l.setForeground(SUBTEXT);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JTextField styledTextField() {
        JTextField f = new JTextField();
        styleField(f);
        return f;
    }

    private JPasswordField styledPasswordField() {
        JPasswordField f = new JPasswordField();
        styleField(f);
        return f;
    }

    private void styleField(JTextField f) {
        f.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        f.setForeground(TEXT);
        f.setBackground(FIELD_BG);
        f.setCaretColor(ACCENT2);
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 100), 1, true),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)));
    }

    private JButton styledButton(String text, Color bg) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        b.setBackground(bg); b.setForeground(TEXT);
        b.setFocusPainted(false); b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(bg.brighter()); }
            public void mouseExited(MouseEvent e)  { b.setBackground(bg); }
        });
        return b;
    }

    private Component centered(JLabel l) { l.setAlignmentX(Component.CENTER_ALIGNMENT); return l; }
    private Component gap(int h)         { return Box.createRigidArea(new Dimension(0, h)); }
}

// ─── Main Chat Frame ──────────────────────────────────────────────────────────
class ChatFrame extends JFrame {

    private static final Color BG        = new Color(15,  15,  30);
    private static final Color SIDEBAR   = new Color(22,  22,  45);
    private static final Color HEADER    = new Color(28,  28,  55);
    private static final Color MY_BUBBLE = new Color(99,  102, 241);
    private static final Color OT_BUBBLE = new Color(35,  35,  65);
    private static final Color INPUT_BG  = new Color(30,  30,  60);
    private static final Color TEXT      = new Color(240, 240, 255);
    private static final Color SUBTEXT   = new Color(150, 150, 190);

    private final User currentUser;
    private       User selectedContact;
    private       int  lastMsgCount = 0;
    private       Timer pollTimer;

    private DefaultListModel<User> contactModel = new DefaultListModel<>();
    private JList<User>    contactList;
    private JPanel         chatPanel;
    private JScrollPane    chatScroll;
    private JTextArea      inputArea;
    private JLabel         headerLabel;

    ChatFrame(User currentUser) {
        this.currentUser = currentUser;
        setTitle("ChatApp — " + currentUser.name);
        setSize(920, 640);
        setMinimumSize(new Dimension(700, 500));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        buildUI();
        loadContacts();
        startPolling();
    }

    private void buildUI() {
        // ── Sidebar ───────────────────────────────────────────────────────────
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(SIDEBAR);
        sidebar.setPreferredSize(new Dimension(240, 0));

        JPanel sideTop = new JPanel(new BorderLayout());
        sideTop.setBackground(HEADER);
        sideTop.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        JLabel appName = new JLabel("ChatApp");
        appName.setFont(new Font("Segoe UI", Font.BOLD, 16));
        appName.setForeground(TEXT);

        JLabel meLabel = new JLabel("You: " + currentUser.name);
        meLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        meLabel.setForeground(SUBTEXT);

        sideTop.add(appName, BorderLayout.NORTH);
        sideTop.add(meLabel, BorderLayout.SOUTH);

        contactList = new JList<>(contactModel);
        contactList.setBackground(SIDEBAR);
        contactList.setForeground(TEXT);
        contactList.setSelectionBackground(new Color(50, 50, 90));
        contactList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        contactList.setFixedCellHeight(52);
        contactList.setCellRenderer(new ContactRenderer());
        contactList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && contactList.getSelectedValue() != null)
                openChat(contactList.getSelectedValue());
        });

        JScrollPane contactScroll = new JScrollPane(contactList);
        contactScroll.setBorder(null);
        contactScroll.getViewport().setBackground(SIDEBAR);

        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        logoutBtn.setForeground(new Color(239, 68, 68));
        logoutBtn.setBackground(SIDEBAR);
        logoutBtn.setBorderPainted(false); logoutBtn.setFocusPainted(false);
        logoutBtn.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        logoutBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logoutBtn.addActionListener(e -> logout());

        sidebar.add(sideTop,      BorderLayout.NORTH);
        sidebar.add(contactScroll, BorderLayout.CENTER);
        sidebar.add(logoutBtn,    BorderLayout.SOUTH);

        // ── Chat area ─────────────────────────────────────────────────────────
        JPanel chatHeader = new JPanel(new BorderLayout());
        chatHeader.setBackground(HEADER);
        chatHeader.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));

        headerLabel = new JLabel("Select a contact to start chatting");
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        headerLabel.setForeground(TEXT);
        chatHeader.add(headerLabel, BorderLayout.WEST);

        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(new Color(18, 18, 38));
        chatPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

        chatScroll = new JScrollPane(chatPanel);
        chatScroll.setBorder(null);
        chatScroll.getViewport().setBackground(new Color(18, 18, 38));
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);

        showPlaceholder("Select a contact from the left to begin");

        // Input bar
        inputArea = new JTextArea(2, 1);
        inputArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputArea.setForeground(TEXT);
        inputArea.setBackground(new Color(40, 40, 72));
        inputArea.setCaretColor(new Color(139, 92, 246));
        inputArea.setLineWrap(true); inputArea.setWrapStyleWord(true);
        inputArea.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 100), 1, true),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        inputArea.setEnabled(false);
        inputArea.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume(); sendMessage();
                }
            }
        });

        JButton sendBtn = new JButton("Send");
        sendBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        sendBtn.setForeground(Color.WHITE);
        sendBtn.setBackground(LoginFrame.ACCENT);
        sendBtn.setFocusPainted(false); sendBtn.setBorderPainted(false);
        sendBtn.setPreferredSize(new Dimension(100, 44));
        sendBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sendBtn.setEnabled(false);
        sendBtn.addActionListener(e -> sendMessage());

        // Enable send button when a contact is selected
        contactList.addListSelectionListener(e -> {
            boolean selected = contactList.getSelectedValue() != null;
            sendBtn.setEnabled(selected);
            inputArea.setEnabled(selected);
        });

        JPanel inputBar = new JPanel(new BorderLayout(10, 0));
        inputBar.setBackground(INPUT_BG);
        inputBar.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        inputBar.add(new JScrollPane(inputArea), BorderLayout.CENTER);
        inputBar.add(sendBtn, BorderLayout.EAST);

        JPanel chatArea = new JPanel(new BorderLayout());
        chatArea.setBackground(new Color(18, 18, 38));
        chatArea.add(chatHeader, BorderLayout.NORTH);
        chatArea.add(chatScroll, BorderLayout.CENTER);
        chatArea.add(inputBar,   BorderLayout.SOUTH);

        // ── Root layout ───────────────────────────────────────────────────────
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.add(sidebar,  BorderLayout.WEST);
        root.add(chatArea, BorderLayout.CENTER);
        setContentPane(root);
    }

    private void openChat(User contact) {
        selectedContact = contact;
        lastMsgCount    = 0;
        headerLabel.setText("Chat with " + contact.name);
        inputArea.setEnabled(true);
        inputArea.requestFocus();
        loadMessages();
    }

    private void loadMessages() {
        if (selectedContact == null) return;
        try {
            java.util.List<Message> msgs = DB.getMessages(currentUser.id, selectedContact.id);
            lastMsgCount = msgs.size();
            renderMessages(msgs);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Failed to load messages: " + e.getMessage());
        }
    }

    private void renderMessages(java.util.List<Message> msgs) {
        chatPanel.removeAll();
        if (msgs.isEmpty()) {
            showPlaceholder("No messages yet. Say hello!");
        } else {
            chatPanel.add(Box.createRigidArea(new Dimension(0, 8)));
            for (Message m : msgs) {
                chatPanel.add(makeBubble(m));
                chatPanel.add(Box.createRigidArea(new Dimension(0, 6)));
            }
        }
        chatPanel.revalidate();
        chatPanel.repaint();
        // Scroll to bottom
        SwingUtilities.invokeLater(() -> {
            JScrollBar sb = chatScroll.getVerticalScrollBar();
            sb.setValue(sb.getMaximum());
        });
    }

    private JPanel makeBubble(Message msg) {
        JPanel wrapper = new JPanel(new FlowLayout(msg.mine ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        wrapper.setBackground(new Color(18, 18, 38));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));

        Color bubbleColor = msg.mine ? MY_BUBBLE : OT_BUBBLE;

        JPanel bubble = new JPanel();
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setBackground(bubbleColor);
        bubble.setBorder(new RoundedBorder(bubbleColor, 14));

        if (!msg.mine) {
            JLabel sender = new JLabel(msg.sender);
            sender.setFont(new Font("Segoe UI", Font.BOLD, 11));
            sender.setForeground(new Color(139, 92, 246));
            bubble.add(sender);
            bubble.add(Box.createRigidArea(new Dimension(0, 3)));
        }

        JTextArea body = new JTextArea(msg.content);
        body.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.setForeground(TEXT);
        body.setBackground(bubbleColor);
        body.setEditable(false); body.setLineWrap(true);
        body.setWrapStyleWord(true); body.setFocusable(false);
        body.setBorder(null);
        body.setMaximumSize(new Dimension(420, Integer.MAX_VALUE));

        JLabel time = new JLabel(msg.time);
        time.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        time.setForeground(new Color(180, 180, 210));
        time.setAlignmentX(msg.mine ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT);

        bubble.add(body);
        bubble.add(Box.createRigidArea(new Dimension(0, 4)));
        bubble.add(time);
        wrapper.add(bubble);
        return wrapper;
    }

    private void sendMessage() {
        if (selectedContact == null) return;
        String text = inputArea.getText().trim();
        if (text.isEmpty()) return;
        try {
            DB.sendMessage(currentUser.id, selectedContact.id, text);
            inputArea.setText("");
            loadMessages();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Send failed: " + e.getMessage());
        }
    }

    private void loadContacts() {
        try {
            java.util.List<User> users = DB.getOtherUsers(currentUser.id);
            contactModel.clear();
            for (User u : users) contactModel.addElement(u);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Could not load contacts: " + e.getMessage());
        }
    }

    private void startPolling() {
        pollTimer = new Timer(true);
        pollTimer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                if (selectedContact == null) return;
                try {
                    int count = DB.countMessages(currentUser.id, selectedContact.id);
                    if (count != lastMsgCount) SwingUtilities.invokeLater(() -> loadMessages());
                } catch (SQLException ignored) {}
            }
        }, 2000, 2000);
    }

    private void logout() {
        if (pollTimer != null) pollTimer.cancel();
        DB.close();
        new LoginFrame().setVisible(true);
        dispose();
    }

    private void showPlaceholder(String text) {
        chatPanel.removeAll();
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.ITALIC, 14));
        l.setForeground(SUBTEXT);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        chatPanel.add(Box.createVerticalGlue());
        chatPanel.add(l);
        chatPanel.add(Box.createVerticalGlue());
    }

    // ── Contact list cell renderer ─────────────────────────────────────────────
    private static class ContactRenderer extends DefaultListCellRenderer {
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean selected, boolean focused) {

            JPanel panel = new JPanel(new BorderLayout(12, 0));
            panel.setBackground(selected ? new Color(50, 50, 90) : new Color(22, 22, 45));
            panel.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

            JLabel avatar = new JLabel(value.toString().substring(0, 1).toUpperCase());
            avatar.setFont(new Font("Segoe UI", Font.BOLD, 16));
            avatar.setForeground(Color.WHITE);
            avatar.setBackground(new Color(99, 102, 241));
            avatar.setOpaque(true);
            avatar.setPreferredSize(new Dimension(36, 36));
            avatar.setHorizontalAlignment(SwingConstants.CENTER);

            JLabel name = new JLabel(value.toString());
            name.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            name.setForeground(new Color(240, 240, 255));

            panel.add(avatar, BorderLayout.WEST);
            panel.add(name,   BorderLayout.CENTER);
            return panel;
        }
    }
}

// ─── Rounded bubble border ────────────────────────────────────────────────────
class RoundedBorder extends AbstractBorder {
    private final Color color;
    private final int   radius;

    RoundedBorder(Color color, int radius) { this.color = color; this.radius = radius; }

    public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.fillRoundRect(x, y, w - 1, h - 1, radius, radius);
    }

    public Insets getBorderInsets(Component c) { return new Insets(radius / 2, radius, radius / 2, radius); }
    public boolean isBorderOpaque()            { return true; }
}

