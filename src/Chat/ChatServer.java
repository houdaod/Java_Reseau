package Chat;

import java.awt.BorderLayout;
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
    private static ChatServerGUI gui; // Interface graphique pour le serveur

    public static void main(String[] args) {
        // Démarrer l'interface graphique du serveur
        gui = new ChatServerGUI();
        gui.log("Serveur démarré sur le port " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket);
                getClients().add(handler);
                new Thread(handler).start();
                gui.log("Nouvelle connexion: " + handler.getUsername());
            }
        } catch (IOException e) {
            gui.log("Erreur: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static void broadcastMessage(String message, ClientHandler sender) {
        synchronized (getClients()) {
            for (ClientHandler client : getClients()) {
                if (client != sender) {
                    client.sendMessage("[" + sender.getUsername() + "]: " + message);
                }
            }
        }
        gui.log("Message diffusé par " + sender.getUsername() + ": " + message);
    }

    static void sendPrivateMessage(String message, String recipient, ClientHandler sender) {
        synchronized (getClients()) {
            boolean recipientFound = false;
            for (ClientHandler client : getClients()) {
                if (client.getUsername().equals(recipient)) {
                    client.sendMessage("[Privé de " + sender.getUsername() + "]: " + message);
                    recipientFound = true;
                    break;
                }
            }
            if (!recipientFound) {
                sender.sendMessage("Erreur: L'utilisateur '" + recipient + "' n'est pas connecté.");
                sender.sendMessage("Utilisateurs connectés: " + getConnectedUsers());
            }
        }
        gui.log("Message privé de " + sender.getUsername() + " à " + recipient + ": " + message);
    }

    static void broadcastFile(String fileName, byte[] fileData, ClientHandler sender) {
        synchronized (getClients()) {
            for (ClientHandler client : getClients()) {
                if (client != sender) {
                    client.sendFile(fileName, fileData);
                }
            }
        }
        gui.log("Fichier diffusé par " + sender.getUsername() + ": " + fileName);
    }

    static void sendPrivateFile(String fileName, byte[] fileData, String recipient, ClientHandler sender) {
        synchronized (getClients()) {
            boolean recipientFound = false;
            for (ClientHandler client : getClients()) {
                if (client.getUsername().equals(recipient)) {
                    client.sendFile(fileName, fileData);
                    recipientFound = true;
                    break;
                }
            }
            if (!recipientFound) {
                sender.sendMessage("Erreur: L'utilisateur '" + recipient + "' n'est pas connecté.");
            }
        }
        gui.log("Fichier privé de " + sender.getUsername() + " à " + recipient + ": " + fileName);
    }

    static String getConnectedUsers() {
        StringBuilder userList = new StringBuilder();
        synchronized (getClients()) {
            for (ClientHandler client : getClients()) {
                userList.append(client.getUsername()).append(", ");
            }
        }
        return userList.toString();
    }

    static void updateUserList() {
        String list = getConnectedUsers();
        synchronized (getClients()) {
            for (ClientHandler client : getClients()) {
                client.sendMessage("Utilisateurs connectés: " + list);
            }
        }
    }

    static void removeClient(ClientHandler client) {
        getClients().remove(client);
        updateUserList();
        gui.log("Déconnexion: " + client.getUsername());
    }

    public static Set<ClientHandler> getClients() {
        return clients;
    }

    public static void setClients(Set<ClientHandler> clients) {
        ChatServer.clients = clients;
    }

    // Interface graphique pour le serveur
    static class ChatServerGUI extends JFrame {
        private JTextArea logArea;

        public ChatServerGUI() {
            setTitle("Serveur de Chat");
            setSize(600, 400);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            logArea = new JTextArea();
            logArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(logArea);

            add(scrollPane, BorderLayout.CENTER);

            setVisible(true);
        }

        public void log(String message) {
            SwingUtilities.invokeLater(() -> {
                logArea.append(message + "\n");
            });
        }
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String username;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getUsername() {
        return username;
    }

    public void run() {
        try {
            // Demander l'identifiant de l'utilisateur
            out.writeUTF("Entrez votre nom d'utilisateur:");
            username = in.readUTF();
            ChatServer.updateUserList();

            while (true) {
                String type = in.readUTF();
                if (type.equals("message")) {
                    String msg = in.readUTF();
                    if (msg.equalsIgnoreCase("exit")) {
                        break;
                    }
                    ChatServer.broadcastMessage(msg, this);
                } else if (type.equals("private")) {
                    String recipient = in.readUTF();
                    String msg = in.readUTF();
                    ChatServer.sendPrivateMessage(msg, recipient, this);
                } else if (type.equals("checkUser")) {
                    String recipient = in.readUTF();
                    boolean userExists = false;
                    synchronized (ChatServer.getClients()) {
                        for (ClientHandler client : ChatServer.getClients()) {
                            if (client.getUsername().equals(recipient)) {
                                userExists = true;
                                break;
                            }
                        }
                    }
                    if (userExists) {
                        out.writeUTF("valid");
                    } else {
                        out.writeUTF(ChatServer.getConnectedUsers());
                    }
                } else if (type.equals("file")) {
                    String recipient = in.readUTF(); // "all" pour broadcast, sinon le nom du destinataire
                    String fileName = in.readUTF();
                    int length = in.readInt();
                    byte[] fileData = new byte[length];
                    in.readFully(fileData);

                    if (recipient.equals("all")) {
                        ChatServer.broadcastFile(fileName, fileData, this);
                    } else {
                        ChatServer.sendPrivateFile(fileName, fileData, recipient, this);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            ChatServer.removeClient(this);
        }
    }

    public void sendMessage(String msg) {
        try {
            out.writeUTF("message");
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendFile(String fileName, byte[] fileData) {
        try {
            out.writeUTF("file");
            out.writeUTF(fileName);
            out.writeInt(fileData.length);
            out.write(fileData);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}