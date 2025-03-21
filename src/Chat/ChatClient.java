package Chat;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class ChatClient {
    private static DataInputStream in;
    private static DataOutputStream out;
    private static JTextArea chatArea;
    private static JProgressBar progressBar;
    private static File downloadDir = new File(System.getProperty("user.home"));
    private static String username;
    private static JLabel userLabel;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClient::askUsernameAndStart);
    }

    private static void askUsernameAndStart() {
        username = JOptionPane.showInputDialog(null, "Entrez votre nom d'utilisateur:", "Connexion", JOptionPane.PLAIN_MESSAGE);
        if (username == null || username.trim().isEmpty()) {
            JOptionPane.showMessageDialog(null, "Nom d'utilisateur requis.", "Erreur", JOptionPane.ERROR_MESSAGE);
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
                            appendToChat(msg);
                        } else if (type.equals("file")) {
                            String fileName = in.readUTF();
                            int length = in.readInt();
                            byte[] fileData = new byte[length];
                            in.readFully(fileData);

                            File outFile = new File(downloadDir, fileName);
                            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                                fos.write(fileData);
                            }
                            appendToChat("Fichier reçu: " + outFile.getAbsolutePath());
                        } else if (type.equals("users")) {
                            int userCount = in.readInt();
                            StringBuilder userList = new StringBuilder("Utilisateurs connectés:\n");
                            for (int i = 0; i < userCount; i++) {
                                userList.append(in.readUTF()).append("\n");
                            }
                            appendToChat(userList.toString());
                        }
                    }
                } catch (IOException e) {
                    appendToChat("Connexion au serveur perdue.");
                }
            }).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Impossible de se connecter au serveur.", "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void createGUI() {
        JFrame frame = new JFrame("Client Chat");
        frame.setSize(700, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setBackground(new Color(245, 245, 245));
        chatArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(chatArea);

        JTextField messageField = new JTextField();
        JButton sendButton = new JButton("Envoyer");
        JButton privateButton = new JButton("Privé");
        JButton broadcastButton = new JButton("Tout le monde");
        JButton dirButton = new JButton("Dossier");
        JButton usersButton = new JButton("Utilisateurs");

        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String message = messageField.getText().trim();
                if (!message.isEmpty()) {
                    String[] options = {"Message", "Fichier"};
                    JOptionPane.showOptionDialog(frame, "Choisissez le type de message à envoyer", "Envoyer",
                            JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);

          
                        // Envoyer un message texte
                        try {
                            out.writeUTF("message");
                            out.writeUTF(message);
                            messageField.setText("");
                        } catch (IOException ex) {
                            appendToChat("Erreur lors de l'envoi du message.");
                        }
                    
                }
            }
        });

        privateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String recipient = JOptionPane.showInputDialog(frame, "Entrez le nom d'utilisateur du destinataire :", "Message privé", JOptionPane.PLAIN_MESSAGE);
                if (recipient != null && !recipient.trim().isEmpty()) {
                    String[] options = {"Message", "Fichier"};
                    int choice = JOptionPane.showOptionDialog(frame, "Choisissez le type de message à envoyer", "Envoyer",
                            JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);

                    if (choice == 0) {
                        // Envoyer un message privé
                        String privateMessage = JOptionPane.showInputDialog(frame, "Entrez votre message :", "Message privé à " + recipient, JOptionPane.PLAIN_MESSAGE);
                        if (privateMessage != null && !privateMessage.trim().isEmpty()) {
                            try {
                                out.writeUTF("private");
                                out.writeUTF(recipient);
                                out.writeUTF(privateMessage);
                            } catch (IOException ex) {
                                appendToChat("Erreur lors de l'envoi du message privé.");
                            }
                        }
                    } else if (choice == 1) {
                        // Envoyer un fichier privé
                        JFileChooser fileChooser = new JFileChooser();
                        int fileChoice = fileChooser.showOpenDialog(frame);
                        if (fileChoice == JFileChooser.APPROVE_OPTION) {
                            File selectedFile = fileChooser.getSelectedFile();
                            sendFile(selectedFile, recipient);
                        }
                    }
                }
            }
        });

        broadcastButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String[] options = {"Message", "Fichier"};
                int choice = JOptionPane.showOptionDialog(frame, "Choisissez le type de message à envoyer", "Envoyer",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);

                if (choice == 0) {
                    // Diffusion d'un message
                    String broadcastMessage = JOptionPane.showInputDialog(frame, "Entrez le message à diffuser à tous :", "Diffusion à tous", JOptionPane.PLAIN_MESSAGE);
                    if (broadcastMessage != null && !broadcastMessage.trim().isEmpty()) {
                        try {
                            out.writeUTF("message");
                            out.writeUTF(broadcastMessage);
                        } catch (IOException ex) {
                            appendToChat("Erreur lors de la diffusion du message.");
                        }
                    }
                } else if (choice == 1) {
                    // Diffusion d'un fichier
                    JFileChooser fileChooser = new JFileChooser();
                    int fileChoice = fileChooser.showOpenDialog(frame);
                    if (fileChoice == JFileChooser.APPROVE_OPTION) {
                        File selectedFile = fileChooser.getSelectedFile();
                        sendFile(selectedFile, "all");
                    }
                }
            }
        });

        dirButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chooseDownloadDir();
            }
        });

        usersButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                requestUserList();
            }
        });

        // Button color customization
        Color buttonColor = new Color(70, 130, 180); // SteelBlue
        Color textColor = Color.WHITE;

        sendButton.setBackground(buttonColor);
        sendButton.setForeground(textColor);

        privateButton.setBackground(buttonColor);
        privateButton.setForeground(textColor);

        broadcastButton.setBackground(buttonColor);
        broadcastButton.setForeground(textColor);

        dirButton.setBackground(buttonColor);
        dirButton.setForeground(textColor);

        usersButton.setBackground(buttonColor);
        usersButton.setForeground(textColor);

        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(messageField, BorderLayout.CENTER);
        panel.add(sendButton, BorderLayout.EAST);

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(privateButton);
        buttonPanel.add(broadcastButton);
        buttonPanel.add(dirButton);
        buttonPanel.add(usersButton);

        bottomPanel.add(buttonPanel);
        bottomPanel.add(progressBar);

        JPanel topPanel = new JPanel(new BorderLayout());
        userLabel = new JLabel(" Connecté en tant que: " + username);
        userLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        userLabel.setForeground(new Color(0, 102, 204));
        topPanel.add(userLabel, BorderLayout.WEST);

        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(bottomPanel, BorderLayout.EAST);

        frame.setVisible(true);
    }

    private static void appendToChat(String message) {
        chatArea.append(message + "\n");
    }

    private static void sendFile(File file, String recipient) {
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();

            out.writeUTF("file");
            out.writeUTF(recipient);
            out.writeUTF(file.getName());
            out.writeInt(data.length);
            out.write(data);

            appendToChat("Fichier envoyé à " + (recipient.equals("all") ? "tout le monde" : recipient));
        } catch (IOException e) {
            appendToChat("Erreur envoi fichier.");
        }
    }

    private static void chooseDownloadDir() {
        JFileChooser dirChooser = new JFileChooser(downloadDir);
        dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = dirChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            downloadDir = dirChooser.getSelectedFile();
            appendToChat("Dossier de téléchargement: " + downloadDir.getAbsolutePath());
        }
    }

    private static void requestUserList() {
        try {
            out.writeUTF("getUsers");
        } catch (IOException ex) {
            appendToChat("Erreur lors de la requête d'utilisateurs.");
        }
    }
}
