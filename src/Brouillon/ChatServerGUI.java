package Brouillon;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ChatServerGUI {
    private static final int PORT = 12345;
    private static Set<ClientHandler> clients = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Serveur en attente sur le port " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket);
                getClients().add(handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
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
            System.out.println("Envoi d'un message privé à " + recipient + " : " + message);
            if (!recipientFound) {
                sender.sendMessage("Erreur: L'utilisateur '" + recipient + "' n'est pas connecté.");
                sender.sendMessage("Utilisateurs connectés: " + getConnectedUsers());
            }
        }
    }

    static void broadcastFile(String fileName, byte[] fileData, ClientHandler sender) {
        synchronized (getClients()) {
            for (ClientHandler client : getClients()) {
                if (client != sender) {
                    client.sendFile(fileName, fileData);
                }
            }
        }
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
    }

    public static Set<ClientHandler> getClients() {
        return clients;
    }

    public static void setClients(Set<ClientHandler> clients) {
        ChatServerGUI.clients = clients;
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
            ChatServerGUI.updateUserList();

            while (true) {
                String type = in.readUTF();
                if (type.equals("message")) {
                    String msg = in.readUTF();
                    if (msg.equalsIgnoreCase("exit")) {
                        break;
                    }
                    ChatServerGUI.broadcastMessage(msg, this);
                } else if (type.equals("private")) {
                    String recipient = in.readUTF();
                    String msg = in.readUTF();
                    ChatServerGUI.sendPrivateMessage(msg, recipient, this);
                } else if (type.equals("checkUser")) {
                    String recipient = in.readUTF();
                    boolean userExists = false;
                    synchronized (ChatServerGUI.getClients()) {
                        for (ClientHandler client : ChatServerGUI.getClients()) {
                            if (client.getUsername().equals(recipient)) {
                                userExists = true;
                                break;
                            }
                        }
                    }
                    if (userExists) {
                        out.writeUTF("valid");
                    } else {
                        out.writeUTF(ChatServerGUI.getConnectedUsers());
                    }
                } else if (type.equals("file")) {
                    String recipient = in.readUTF(); // "all" pour broadcast, sinon le nom du destinataire
                    String fileName = in.readUTF();
                    int length = in.readInt();
                    byte[] fileData = new byte[length];
                    in.readFully(fileData);

                    if (recipient.equals("all")) {
                        ChatServerGUI.broadcastFile(fileName, fileData, this);
                    } else {
                        ChatServerGUI.sendPrivateFile(fileName, fileData, recipient, this);
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
            ChatServerGUI.removeClient(this);
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