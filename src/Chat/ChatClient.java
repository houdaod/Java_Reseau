package Chat;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

public class ChatClient {
    private static DataInputStream in;
    private static DataOutputStream out;
    private static JTextArea chatArea;
    private static File downloadDir = new File(System.getProperty("user.home"));
    private static String username;
    private static JFrame frame;
    private static JList<String> userList;
    private static DefaultListModel<String> userListModel;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClient::askUsernameAndStart);
    }

    private static void askUsernameAndStart() {
        username = JOptionPane.showInputDialog(null, 
            "Entrez votre nom d'utilisateur:", 
            "Connexion", JOptionPane.PLAIN_MESSAGE);
        
        if (username == null || username.trim().isEmpty()) {
            System.exit(0);
        }
        
        createGUI();
        startConnection();
    }

    private static void startConnection() {
        try {
            Socket socket = new Socket("localhost", 12345);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF(username);

            new Thread(() -> {
                try {
                    while (true) {
                        String type = in.readUTF();
                        
                        if (type.equals("message")) {
                            String msg = in.readUTF();
                            appendToChat(msg, false);
                        } else if (type.equals("file")) {
                            handleIncomingFile();
                        } else if (type.equals("users")) {
                            updateUserList();
                        } else if (type.equals("error")) {
                            String errorMsg = in.readUTF();
                            appendErrorToChat(errorMsg);
                        }
                    }
                } catch (IOException e) {
                    appendToChat("Déconnecté du serveur.", false);
                }
            }).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, 
                "Impossible de se connecter au serveur: " + e.getMessage(), 
                "Erreur", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private static void createGUI() {
        frame = new JFrame("Chat - " + username);
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // Zone de chat
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setBackground(new Color(240, 240, 240));
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        JScrollPane chatScroll = new JScrollPane(chatArea);

        // Liste des utilisateurs
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setBackground(new Color(230, 230, 250));
        userList.setFixedCellWidth(150);
        JScrollPane userScroll = new JScrollPane(userList);

        // Panel principal
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatScroll, userScroll);
        splitPane.setDividerLocation(600);
        frame.add(splitPane, BorderLayout.CENTER);

        // Panel de contrôle
        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.setBackground(new Color(220, 220, 220));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Champ de message avec indication
        JTextField messageField = new JTextField();
        messageField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        messageField.setToolTipText("Écrivez votre message ici puis cliquez sur Envoyer");
        messageField.addActionListener(e -> handleMessageSend(messageField));

        // Boutons principaux
        JPanel mainButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        mainButtonPanel.setBackground(new Color(220, 220, 220));

        JButton sendButton = createStyledButton("Envoyer Message", new Color(76, 175, 80));
        sendButton.addActionListener(e -> handleMessageSend(messageField));

        JButton fileButton = createStyledButton("Envoyer Fichier", new Color(33, 150, 243));
        fileButton.addActionListener(e -> showFileSendDialog());

        mainButtonPanel.add(sendButton);
        mainButtonPanel.add(fileButton);

        // Boutons secondaires
        JPanel secondaryButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        secondaryButtonPanel.setBackground(new Color(220, 220, 220));

        JButton dirButton = createStyledButton("Dossier Téléchargement", new Color(156, 39, 176));
        dirButton.addActionListener(e -> chooseDownloadDir());

        JButton usersButton = createStyledButton("Rafraîchir", new Color(255, 152, 0));
        usersButton.addActionListener(e -> requestUserList());

        JButton exitButton = createStyledButton("Quitter", new Color(244, 67, 54));
        exitButton.addActionListener(e -> disconnect());

        secondaryButtonPanel.add(dirButton);
        secondaryButtonPanel.add(usersButton);
        secondaryButtonPanel.add(exitButton);

        // Assemblage des panels
        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.add(mainButtonPanel, BorderLayout.WEST);
        buttonPanel.add(secondaryButtonPanel, BorderLayout.EAST);

        controlPanel.add(messageField, BorderLayout.CENTER);
        controlPanel.add(buttonPanel, BorderLayout.SOUTH);

        frame.add(controlPanel, BorderLayout.SOUTH);
        frame.setVisible(true);
        appendToChat("Connecté au serveur en tant que " + username, false);
        appendToChat("Dossier de téléchargement: " + downloadDir.getAbsolutePath(), false);
    }

    private static JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        
        // Calcul dynamique de la taille en fonction du contenu
        FontMetrics metrics = button.getFontMetrics(button.getFont());
        int width = metrics.stringWidth(text) + 30; // 15px de marge de chaque côté
        int height = metrics.getHeight() + 10;     // 5px de marge en haut et en bas
        
        button.setPreferredSize(new Dimension(width, height));
        button.setMinimumSize(new Dimension(width, height));
        
        return button;
    }

    // ... [Le reste des méthodes reste inchangé] ...
    private static void handleMessageSend(JTextField messageField) {
        String message = messageField.getText().trim();
        if (message.isEmpty()) {
            JOptionPane.showMessageDialog(frame,
                "Veuillez d'abord écrire un message",
                "Message vide",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        Object[] options = {"À tout le monde", "Privé", "Annuler"};
        int choice = JOptionPane.showOptionDialog(frame,
            "Envoyer ce message à :",
            "Destination du message",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]);

        if (choice == 0) { // À tout le monde
            sendMessageToServer(message, "all");
            appendToChat("Moi (à tous): " + message, true);
            messageField.setText("");
        } else if (choice == 1) { // Privé
            if (userListModel.isEmpty()) {
                JOptionPane.showMessageDialog(frame,
                    "Aucun utilisateur connecté",
                    "Erreur",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }

            String recipient = (String) JOptionPane.showInputDialog(frame,
                "Sélectionnez le destinataire:",
                "Message privé",
                JOptionPane.PLAIN_MESSAGE,
                null,
                userListModel.toArray(),
                null);

            if (recipient != null && !recipient.isEmpty()) {
                sendPrivateMessageToServer(message, recipient);
                appendToChat("Moi à " + recipient + ": " + message, true);
                messageField.setText("");
            }
        }
    }

    private static void chooseDownloadDir() {
        JFileChooser chooser = new JFileChooser(downloadDir);
        chooser.setDialogTitle("Choisir le dossier de téléchargement");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            downloadDir = chooser.getSelectedFile();
            appendToChat("Dossier de téléchargement changé: " + downloadDir.getAbsolutePath(), false);
        }
    }

    private static void showFileSendDialog() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            
            Object[] options = {"À tous", "Privé", "Annuler"};
            int choice = JOptionPane.showOptionDialog(frame,
                "Envoyer le fichier à :",
                "Destination",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);
            
            if (choice == 0) { // À tous
                sendFile(file, "all");
            } else if (choice == 1) { // Privé
                String recipient = (String) JOptionPane.showInputDialog(frame,
                    "Sélectionnez le destinataire:",
                    "Message privé",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    userListModel.toArray(),
                    null);
                
                if (recipient != null && !recipient.isEmpty()) {
                    sendFile(file, recipient);
                }
            }
        }
    }

    private static void sendFile(File file, String recipient) {
        new Thread(() -> {
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] fileData = new byte[(int) file.length()];
                fis.read(fileData);

                out.writeUTF("file");
                out.writeUTF(recipient);
                out.writeUTF(file.getName());
                out.writeInt(fileData.length);
                out.write(fileData);

                appendToChat("Fichier envoyé à " + recipient + ": " + file.getName(), true);
            } catch (IOException ex) {
                appendErrorToChat("Erreur lors de l'envoi du fichier: " + ex.getMessage());
            }
        }).start();
    }

    private static void sendMessageToServer(String message, String recipient) {
        try {
            out.writeUTF("message");
            out.writeUTF(message);
        } catch (IOException ex) {
            appendErrorToChat("Erreur lors de l'envoi du message");
        }
    }

    private static void sendPrivateMessageToServer(String message, String recipient) {
        try {
            out.writeUTF("private");
            out.writeUTF(recipient);
            out.writeUTF(message);
        } catch (IOException ex) {
            appendErrorToChat("Erreur lors de l'envoi du message privé");
        }
    }

    private static void handleIncomingFile() throws IOException {
        String fileName = in.readUTF();
        int length = in.readInt();
        byte[] fileData = new byte[length];
        in.readFully(fileData);

        File outFile = new File(downloadDir, fileName);
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(fileData);
        }
        appendToChat("Fichier reçu: " + outFile.getAbsolutePath(), false);
    }

    private static void updateUserList() throws IOException {
        int userCount = in.readInt();
        userListModel.clear();
        for (int i = 0; i < userCount; i++) {
            userListModel.addElement(in.readUTF());
        }
    }

    private static void requestUserList() {
        try {
            out.writeUTF("getUsers");
        } catch (IOException e) {
            appendErrorToChat("Erreur lors de la requête des utilisateurs");
        }
    }

    private static void disconnect() {
        try {
            if (out != null) {
                out.writeUTF("exit");
            }
            appendToChat("Déconnexion...", true);
            Thread.sleep(500);
            System.exit(0);
        } catch (Exception ex) {
            System.exit(0);
        }
    }

    private static void appendToChat(String msg, boolean isLocal) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append((isLocal ? "> " : "") + msg + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    private static void appendErrorToChat(String errorMsg) {
        appendToChat("Erreur: " + errorMsg, false);
    }
}