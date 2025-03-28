
package Chat;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;


public class ChatServer {
    private static final int PORT = 12345;
    private static Set<ClientHandler> clients = Collections.synchronizedSet(new HashSet<>());
    private static JTextArea logArea;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatServer::createAndShowGUI);
        startServer();
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Serveur Chat");
        frame.setSize(600, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(new Color(0, 255, 0));
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(logArea);

        frame.add(scrollPane, BorderLayout.CENTER);
        frame.setVisible(true);
        log("Serveur démarré. En attente de connexions...");
    }

    private static void startServer() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                log("Serveur démarré sur le port " + PORT);
                while (true) {
                    Socket socket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(socket);
                    clients.add(handler);
                    new Thread(handler).start();
                }
            } catch (IOException e) {
                log("Erreur serveur: " + e.getMessage());
                JOptionPane.showMessageDialog(null, 
                    "Erreur du serveur: " + e.getMessage(), 
                    "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        }).start();
    }

    private static void log(String message) {
        SwingUtilities.invokeLater(() -> logArea.append(message + "\n"));
    }

    private static void broadcastMessage(String message, ClientHandler sender) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client != sender) {
                    client.sendMessage("message", "[" + sender.getUsername() + "]: " + message);
                }
            }
        }
        log("Message diffusé par " + sender.getUsername() + ": " + message);
    }

    private static void sendPrivateMessage(String message, String recipient, ClientHandler sender) {
        boolean found = false;
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client.getUsername().equals(recipient)) {
                    client.sendMessage("message", "[Privé de " + sender.getUsername() + "]: " + message);
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            sender.sendMessage("error", "Utilisateur '" + recipient + "' non trouvé.");
        }
    }

    private static void broadcastFile(String fileName, byte[] fileData, ClientHandler sender) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client != sender) {
                    client.sendFile(fileName, fileData);
                }
            }
        }
        log("Fichier diffusé par " + sender.getUsername() + ": " + fileName);
    }

    private static void sendPrivateFile(String fileName, byte[] fileData, String recipient, ClientHandler sender) {
        boolean found = false;
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client.getUsername().equals(recipient)) {
                    client.sendFile(fileName, fileData);
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            sender.sendMessage("error", "Utilisateur '" + recipient + "' non trouvé.");
        } else {
            log("Fichier privé envoyé de " + sender.getUsername() + " à " + recipient + ": " + fileName);
        }
    }

    private static void sendUserList(ClientHandler requester) {
        synchronized (clients) {
            try {
                requester.getOut().writeUTF("users");
                requester.getOut().writeInt(clients.size());
                for (ClientHandler client : clients) {
                    requester.getOut().writeUTF(client.getUsername());
                }
            } catch (IOException e) {
                log("Erreur envoi liste utilisateurs à " + requester.getUsername());
            }
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private DataInputStream in;
        private DataOutputStream out;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public String getUsername() {
            return username;
        }

        public DataOutputStream getOut() {
            return out;
        }

        public void sendMessage(String type, String msg) {
            try {
                out.writeUTF(type);
                out.writeUTF(msg);
            } catch (IOException e) {
                log("Erreur envoi à " + username);
            }
        }

        public void sendFile(String fileName, byte[] data) {
            try {
                out.writeUTF("file");
                out.writeUTF(fileName);
                out.writeInt(data.length);
                out.write(data);
            } catch (IOException e) {
                log("Erreur envoi fichier à " + username);
            }
        }

        @Override
        public void run() {
            try {
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                username = in.readUTF();
                log(username + " connecté.");
                broadcastMessage(username + " a rejoint le chat.", this);

                while (true) {
                    String command = in.readUTF();

                    if (command.equals("exit")) {
                        break;
                    } else if (command.equals("message")) {
                        String msg = in.readUTF();
                        if (msg.equalsIgnoreCase("exit")) {
                            out.writeUTF("exit");
                            break;
                        }
                        broadcastMessage(msg, this);
                    } else if (command.equals("private")) {
                        String recipient = in.readUTF();
                        String msg = in.readUTF();
                        sendPrivateMessage(msg, recipient, this);
                    } else if (command.equals("file")) {
                        String recipient = in.readUTF();
                        String fileName = in.readUTF();
                        int length = in.readInt();
                        byte[] fileData = new byte[length];
                        in.readFully(fileData);

                        if (recipient.equals("all")) {
                            broadcastFile(fileName, fileData, this);
                        } else {
                            sendPrivateFile(fileName, fileData, recipient, this);
                        }
                    } else if (command.equals("getUsers")) {
                        sendUserList(this);
                    }
                }
            } catch (IOException e) {
                log("Erreur avec " + username + ": " + e.getMessage());
            } finally {
                try {
                    if (username != null) {
                        broadcastMessage(username + " a quitté le chat.", this);
                        log(username + " déconnecté.");
                    }
                    socket.close();
                } catch (IOException ignored) {}
                clients.remove(this);
            }
        }
    }
}