package com.game3cay.shared.network;

import java.io.*;
import java.net.Socket;

public final class PacketIO {
    private PacketIO() {
    }

    public static ObjectInputStream openInput(Socket socket) throws IOException {
        ObjectInputStream input =
                new ObjectInputStream(socket.getInputStream());

        input.setObjectInputFilter(ObjectInputFilter.Config.createFilter(
                "maxdepth=8;maxarray=0;"
                + Packet.class.getName() + ";"
                + PacketType.class.getName() + ";"
                + "java.lang.Enum;java.lang.String;!*"
        ));

        return input;
    }

    public static Packet read(ObjectInputStream input)
            throws IOException, ClassNotFoundException {

        Object object = input.readObject();

        if (!(object instanceof Packet packet)) {
            throw new IOException("Đối tượng nhận được không phải Packet.");
        }

        validate(packet);
        return packet;
    }

    public static void write(ObjectOutputStream output, Packet packet)
            throws IOException {

        validate(packet);

        synchronized (output) {
            output.reset();
            output.writeObject(packet);
            output.flush();
        }
    }

    private static void validate(Packet packet) throws IOException {
        if (packet == null
                || packet.getType() == null
                || packet.getProtocolVersion() != Packet.VERSION) {

            throw new IOException("Packet sai định dạng hoặc phiên bản.");
        }

        String id = packet.getRequestId();

        if (id != null && (id.isBlank() || id.length() > 64)) {
            throw new IOException("requestId không hợp lệ.");
        }

        Object data = packet.getData();

        // M1 chỉ trao đổi String hoặc null.
        if (data != null
                && (!(data instanceof String text) || text.length() > 1000)) {

            throw new IOException("M1 chỉ nhận chuỗi tối đa 1000 ký tự.");
        }
    }
}