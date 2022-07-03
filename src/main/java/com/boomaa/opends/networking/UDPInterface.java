package com.boomaa.opends.networking;

import java.io.IOException;
import java.net.*;

public class UDPInterface implements NetworkInterface {
    private DatagramSocket clientSocket;
    private DatagramSocket serverSocket;
    private InetAddress ip;
    private int clientPort;
    private int bufSize = 1024;
    private boolean closed;

    public UDPInterface(String clientIp, int clientPort, int serverPort, int timeout) throws SocketException {
        try {
            this.ip = InetAddress.getByName(clientIp);
            this.clientPort = clientPort;
            this.clientSocket = new DatagramSocket();
            this.serverSocket = new DatagramSocket(serverPort);
            if (timeout != -1) {
                clientSocket.setSoTimeout(timeout);
                serverSocket.setSoTimeout(timeout);
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public UDPInterface(String clientIp, int clientPort, int serverPort) throws SocketException {
        this(clientIp, clientPort, serverPort, 1000);
    }

    @Override
    public void write(byte[] data) {
        if (!closed) {
            try {
                clientSocket.send(new DatagramPacket(data, data.length, ip, clientPort));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public byte[] read() {
        byte[] buffer = new byte[bufSize];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        try {
            if (closed) {
                return new byte[0];
            }
            serverSocket.receive(packet);
        } catch (SocketTimeoutException | SocketException e) {
            close();
            return new byte[0];
        } catch (IOException e) {
            e.printStackTrace();
        }
        return buffer;
    }

    public void setBufSize(int bufSize) {
        this.bufSize = bufSize;
    }

    @Override
    public void close() {
        closed = true;
        clientSocket.close();
        serverSocket.close();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }
}
