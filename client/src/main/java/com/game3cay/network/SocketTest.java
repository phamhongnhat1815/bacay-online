package com.game3cay.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class SocketTest {
    private static SocketTest instance;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private SocketTest() {}

    public static synchronized SocketTest getInstance() {
        if (instance == null) {
            instance = new SocketTest();
        }
        return instance;
    }

    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        // Mở luồng chạy ngầm liên tục lắng nghe phản hồi từ Server
        new Thread(this::listen).start();
    }

    public void send(String msg) {
        if (out != null) {
            out.println(msg);
        }
    }

    private void listen() {
        try {
            String response;
            while ((response = in.readLine()) != null) {
                System.out.println("<- [Server trả về]: " + response);
            }
        } catch (IOException e) {
            System.out.println("[-] Mất kết nối tới Server.");
        }
    }
}