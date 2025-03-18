package Chat;

import java.awt.BorderLayout;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClient::createGUI);

        try {
            Socket socket = new Socket("localhost", 12345);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            // Demander l'identifiant de l'utilisateur
            username = JOptionPane.showInputDialog("Entrez votre nom d'utilisateur:");
            out.writeUTF(username);

            // Thread pour recevoir les messages et fichiers
            new Thread(() -> {
                try {
                    while (true) {
                        String type = in.readUTF();
                        if (type.equals("message")) {
                            String msg = in.readUTF();
                            SwingUtilities.invokeLater(() -> {
                                chatArea.append(msg + "\n");
                            });
                        } else if (type.equals("file")) {
                            String fileName = in.readUTF();
                            int length = in.readInt();
                            byte[] fileData = new byte[length];
                            in.readFully(fileData);

                            File outFile = new File(downloadDir, fileName);
                            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                                fos.write(fileData);
                            }

                            SwingUtilities.invokeLater(() -> {
                                chatArea.append("Fichier recu: " + outFile.getAbsolutePath() + "\n");
                            });
                        }
                    }
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> {
                        chatArea.append("Connexion au serveur perdue.\n");
                    });
                    e.printStackTrace();
                }
            }).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Impossible de se connecter au serveur.", "Erreur", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private static void createGUI() {
        JFrame frame = new JFrame("Client Chat - " + username);
        frame.setSize(600, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);

        JTextField messageField = new JTextField();
        JButton sendButton = new JButton("Envoyer");
        JButton privateButton = new JButton("Prive");
        JButton broadcastButton = new JButton("Tout le monde");
        JButton dirButton = new JButton("Choisir Dossier");
        progressBar = new JProgressBar();

        // Action pour envoyer un message public
        sendButton.addActionListener(e -> {
            String msg = messageField.getText();
            if (!msg.isEmpty()) {
                try {
                    out.writeUTF("message");
                    out.writeUTF(msg);
                    SwingUtilities.invokeLater(() -> {
                        chatArea.append("Moi (a tous): " + msg + "\n");
                        messageField.setText("");
                    });
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() -> {
                        chatArea.append("Erreur lors de l'envoi du message.\n");
                    });
                    ex.printStackTrace();
                }
            }
        });

        // Action pour envoyer un message ou fichier en prive
        privateButton.addActionListener(e -> {
            String[] options = {"Message", "Fichier"};
            int choice = JOptionPane.showOptionDialog(null, "Choisissez une option", "Prive",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);

            if (choice == 0) { // Message
                String recipient = JOptionPane.showInputDialog("Destinataire:");
                if (recipient != null && !recipient.isEmpty()) {
                    String msg = JOptionPane.showInputDialog("Message pour " + recipient + ":");
                    if (msg != null && !msg.isEmpty()) {
                        try {
                            out.writeUTF("private");
                            out.writeUTF(recipient);
                            out.writeUTF(msg);
                            SwingUtilities.invokeLater(() -> {
                                chatArea.append("Prive a " + recipient + ": " + msg + "\n");
                            });
                        } catch (IOException ex) {
                            SwingUtilities.invokeLater(() -> {
                                chatArea.append("Erreur lors de l'envoi du message prive.\n");
                            });
                            ex.printStackTrace();
                        }
                    }
                }
            } else if (choice == 1) { // Fichier
                String recipient = JOptionPane.showInputDialog("Destinataire:");
                if (recipient != null && !recipient.isEmpty()) {
                    JFileChooser fileChooser = new JFileChooser();
                    fileChooser.setCurrentDirectory(downloadDir);
                    int result = fileChooser.showOpenDialog(null);
                    if (result == JFileChooser.APPROVE_OPTION) {
                        File file = fileChooser.getSelectedFile();
                        new Thread(() -> sendFile(file, recipient)).start();
                    }
                }
            }
        });

        // Action pour envoyer un message ou fichier a tout le monde
        broadcastButton.addActionListener(e -> {
            String[] options = {"Message", "Fichier"};
            int choice = JOptionPane.showOptionDialog(null, "Choisissez une option", "Tout le monde",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);

            if (choice == 0) { // Message
                String msg = JOptionPane.showInputDialog("Message pour tout le monde:");
                if (msg != null && !msg.isEmpty()) {
                    try {
                        out.writeUTF("message");
                        out.writeUTF(msg);
                        SwingUtilities.invokeLater(() -> {
                            chatArea.append("Moi (a tous): " + msg + "\n");
                        });
                    } catch (IOException ex) {
                        SwingUtilities.invokeLater(() -> {
                            chatArea.append("Erreur lors de l'envoi du message.\n");
                        });
                        ex.printStackTrace();
                    }
                }
            } else if (choice == 1) { // Fichier
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setCurrentDirectory(downloadDir);
                int result = fileChooser.showOpenDialog(null);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File file = fileChooser.getSelectedFile();
                    new Thread(() -> sendFile(file, "all")).start();
                }
            }
        });

        // Action pour choisir un dossier de telechargement
        dirButton.addActionListener(e -> {
            JFileChooser dirChooser = new JFileChooser();
            dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            dirChooser.setCurrentDirectory(downloadDir);
            int result = dirChooser.showOpenDialog(null);
            if (result == JFileChooser.APPROVE_OPTION) {
                downloadDir = dirChooser.getSelectedFile();
                SwingUtilities.invokeLater(() -> {
                    chatArea.append("Dossier de telechargement: " + downloadDir.getAbsolutePath() + "\n");
                });
            }
        });

        // Panneau pour le champ de texte et les boutons
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(messageField, BorderLayout.CENTER);
        panel.add(sendButton, BorderLayout.EAST);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(privateButton, BorderLayout.WEST);
        bottomPanel.add(broadcastButton, BorderLayout.CENTER);
        bottomPanel.add(dirButton, BorderLayout.EAST);
        bottomPanel.add(progressBar, BorderLayout.SOUTH);

        // Ajout des composants a la fenetre
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(panel, BorderLayout.NORTH);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    private static void sendFile(File file, String recipient) {
        if (!file.exists()) {
            SwingUtilities.invokeLater(() -> {
                chatArea.append("Erreur: Fichier non trouve.\n");
            });
            return;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] fileData = new byte[(int) file.length()];
            fis.read(fileData);

            out.writeUTF("file");
            out.writeUTF(recipient);
            out.writeUTF(file.getName());
            out.writeInt(fileData.length);

            SwingUtilities.invokeLater(() -> {
                progressBar.setMaximum(fileData.length);
                progressBar.setValue(0);
            });

            for (int i = 0; i < fileData.length; i += 1024) {
                int len = Math.min(1024, fileData.length - i);
                out.write(fileData, i, len);
                int finalI = i + len;
                SwingUtilities.invokeLater(() -> progressBar.setValue(finalI));
            }

            SwingUtilities.invokeLater(() -> {
                chatArea.append("Fichier envoye: " + file.getName() + "\n");
                progressBar.setValue(0); // Reinitialiser la barre de progression
            });
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                chatArea.append("Erreur lors de l'envoi du fichier.\n");
            });
            e.printStackTrace();
        }
    }
}