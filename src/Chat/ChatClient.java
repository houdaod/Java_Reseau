package Chat;

import java.awt.BorderLayout;
import java.awt.Toolkit;
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
import javax.swing.filechooser.FileSystemView;

public class ChatClient {
    private static DataInputStream in;
    private static DataOutputStream out;
    private static JTextArea chatArea;
    private static JProgressBar progressBar;
    private static File downloadDir = FileSystemView.getFileSystemView().getHomeDirectory();
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
                                Toolkit.getDefaultToolkit().beep();
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
                                chatArea.append("Fichier reçu: " + outFile.getAbsolutePath() + "\n");
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
        frame.setSize(500, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);

        JTextField messageField = new JTextField();
        JButton sendButton = new JButton("Envoyer");
        JButton privateButton = new JButton("Message Privé");
        JButton dirButton = new JButton("Choisir Dossier de téléchargement");
        progressBar = new JProgressBar();

        // Action pour envoyer un message
        sendButton.addActionListener(e -> {
            String msg = messageField.getText();
            if (!msg.isEmpty()) {
                try {
                    out.writeUTF("message");
                    out.writeUTF(msg);
                    SwingUtilities.invokeLater(() -> {
                        chatArea.append("Moi: " + msg + "\n");
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

        // Action pour envoyer un message privé ou un fichier
        privateButton.addActionListener(e -> {
            String[] options = {"Message", "Fichier"};
            int choice = JOptionPane.showOptionDialog(null, "Choisissez une option", "Message Privé",
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
                                chatArea.append("Privé à " + recipient + ": " + msg + "\n");
                            });
                        } catch (IOException ex) {
                            SwingUtilities.invokeLater(() -> {
                                chatArea.append("Erreur lors de l'envoi du message privé.\n");
                            });
                            ex.printStackTrace();
                        }
                    }
                }
            } else if (choice == 1) { // Fichier
                String recipient = JOptionPane.showInputDialog("Destinataire (ou 'all' pour tout le monde):");
                if (recipient != null && !recipient.isEmpty()) {
                    JFileChooser fileChooser = new JFileChooser();
                    fileChooser.setCurrentDirectory(FileSystemView.getFileSystemView().getHomeDirectory());
                    int result = fileChooser.showOpenDialog(null);
                    if (result == JFileChooser.APPROVE_OPTION) {
                        File file = fileChooser.getSelectedFile();
                        new Thread(() -> sendFile(file, recipient)).start();
                    }
                }
            }
        });

        // Action pour choisir un dossier de téléchargement
        dirButton.addActionListener(e -> {
            JFileChooser dirChooser = new JFileChooser();
            dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            dirChooser.setCurrentDirectory(FileSystemView.getFileSystemView().getHomeDirectory());
            int result = dirChooser.showOpenDialog(null);
            if (result == JFileChooser.APPROVE_OPTION) {
                downloadDir = dirChooser.getSelectedFile();
                SwingUtilities.invokeLater(() -> {
                    chatArea.append("Dossier de téléchargement: " + downloadDir.getAbsolutePath() + "\n");
                });
            }
        });

        // Panneau pour le champ de texte et les boutons
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(messageField, BorderLayout.CENTER);
        panel.add(sendButton, BorderLayout.EAST);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(privateButton, BorderLayout.WEST);
        bottomPanel.add(dirButton, BorderLayout.EAST);
        bottomPanel.add(progressBar, BorderLayout.SOUTH);

        // Ajout des composants à la fenêtre
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(panel, BorderLayout.NORTH);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    private static void sendFile(File file, String recipient) {
        if (!file.exists()) {
            SwingUtilities.invokeLater(() -> {
                chatArea.append("Erreur: Fichier non trouvé.\n");
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
                chatArea.append("Fichier envoyé: " + file.getName() + "\n");
                progressBar.setValue(0); // Réinitialiser la barre de progression
            });
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                chatArea.append("Erreur lors de l'envoi du fichier.\n");
            });
            e.printStackTrace();
        }
    }
}