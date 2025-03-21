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

        log("Interface serveur prête.");
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
        log("Diffusion par " + sender.getUsername() + ": " + message);
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
            sender.sendMessage("message", "Erreur: Utilisateur '" + recipient + "' non trouvé.");
        } else {
            log("Privé de " + sender.getUsername() + " à " + recipient + ": " + message);
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
        log("Fichier '" + fileName + "' diffusé par " + sender.getUsername());
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
            sender.sendMessage("message", "Erreur: Utilisateur '" + recipient + "' non trouvé.");
        } else {
            log("Fichier '" + fileName + "' envoyé par " + sender.getUsername() + " à " + recipient);
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
                log("Erreur envoi message à " + username);
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

                while (true) {
                    String command = in.readUTF();

                    if (command.equals("message")) {
                        String msg = in.readUTF();
                        ChatServer.broadcastMessage(msg, this);
                    } else if (command.equals("private")) {
                        String recipient = in.readUTF();
                        String msg = in.readUTF();
                        ChatServer.sendPrivateMessage(msg, recipient, this);
                    } else if (command.equals("file")) {
                        String recipient = in.readUTF();
                        String fileName = in.readUTF();
                        int length = in.readInt();
                        byte[] fileData = new byte[length];
                        in.readFully(fileData);

                        if (recipient.equals("all")) {
                            ChatServer.broadcastFile(fileName, fileData, this);
                        } else {
                            ChatServer.sendPrivateFile(fileName, fileData, recipient, this);
                        }
                    } else if (command.equals("getUsers")) {
                        ChatServer.sendUserList(this);
                    }
                }
            } catch (IOException e) {
                log("Déconnexion de " + username);
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {}
                clients.remove(this);
            }
        }
    }
}
