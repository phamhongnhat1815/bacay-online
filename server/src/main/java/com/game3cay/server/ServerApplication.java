package com.game3cay.server;

import com.game3cay.server.network.ClientHandler;
import java.net.*;
import java.util.concurrent.*;

public final class ServerApplication {
    public static void main(String[] args) throws Exception {
        int port = args.length == 0 ? 8888 : Integer.parseInt(args[0]);

        var workers = new ThreadPoolExecutor(
                0, 32, 60, TimeUnit.SECONDS,
                new SynchronousQueue<>()
        );

        try (var server = new ServerSocket(port)) {
            System.out.println("TCP server đang nghe cổng " + port);

            while (!server.isClosed()) {
                Socket socket = server.accept();

                try {
                    workers.execute(new ClientHandler(socket));
                } catch (RejectedExecutionException e) {
                    socket.close();
                }
            }
        } finally {
            workers.shutdownNow();
        }
    }
}