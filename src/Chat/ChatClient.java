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
import javax.swing.JOptionPane; // Pour les boites de dialogue
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities; // Pour executer du code sur l'Event Dispatch Thread (EDT)
import javax.swing.filechooser.FileSystemView;

public class ChatClient {
    private static DataInputStream in;
    private static DataOutputStream out;
    private static JTextArea chatArea;
    private static JProgressBar progressBar;
    private static File downloadDir = FileSystemView.getFileSystemView().getHomeDirectory();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClient::createGUI);

        try {
            Socket socket = new Socket("localhost", 12345);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

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
        JFrame frame = new JFrame("Client Chat");
        frame.setSize(500, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);

        JTextField messageField = new JTextField();
        JButton sendButton = new JButton("Envoyer");
        JButton fileButton = new JButton("Envoyer Fichier");
        JButton dirButton = new JButton("Choisir Dossier");
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

        // Action pour envoyer un fichier
        fileButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setCurrentDirectory(FileSystemView.getFileSystemView().getHomeDirectory());
            int result = fileChooser.showOpenDialog(null);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                new Thread(() -> sendFile(file)).start();
            }
        });

        // Action pour choisir un dossier de telechargement
        dirButton.addActionListener(e -> {
            JFileChooser dirChooser = new JFileChooser();
            dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            dirChooser.setCurrentDirectory(FileSystemView.getFileSystemView().getHomeDirectory());
            int result = dirChooser.showOpenDialog(null);
            if (result == JFileChooser.APPROVE_OPTION) {
                downloadDir = dirChooser.getSelectedFile();
                SwingUtilities.invokeLater(() -> {
                    chatArea.append("Dossier de telechargement: " + downloadDir.getAbsolutePath() + "\n");
                });
            }
        });

        // Panneau pour le champ de texte et le bouton d'envoi
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(messageField, BorderLayout.CENTER);
        panel.add(sendButton, BorderLayout.EAST);

        // Panneau pour les boutons de fichier, dossier et la barre de progression
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(fileButton, BorderLayout.WEST);
        bottomPanel.add(dirButton, BorderLayout.CENTER);
        bottomPanel.add(progressBar, BorderLayout.SOUTH);

        // Ajout des composants a la fenetre
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(panel, BorderLayout.NORTH);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    private static void sendFile(File file) {
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