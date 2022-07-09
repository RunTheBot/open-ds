package com.boomaa.opends.networking;

import com.boomaa.opends.data.holders.Protocol;
import com.boomaa.opends.data.holders.Remote;
import com.boomaa.opends.data.receive.parser.ParserNull;
import com.boomaa.opends.display.MainJDEC;
import com.boomaa.opends.util.Clock;
import com.boomaa.opends.util.PacketCounters;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static com.boomaa.opends.display.DisplayEndpoint.CREATOR;
import static com.boomaa.opends.display.DisplayEndpoint.NET_IF_INIT;
import static com.boomaa.opends.display.DisplayEndpoint.UPDATER;
import static com.boomaa.opends.display.DisplayEndpoint.getPacketParser;

public class NetworkClock extends Clock {
    private NetworkInterface iface;
    private final Remote remote;
    private final Protocol protocol;

    public NetworkClock(Remote remote, Protocol protocol) {
        super(createName(remote, protocol), remote == Remote.ROBO_RIO ? 20 : 500);
        this.remote = remote;
        this.protocol = protocol;
        reloadInterface();
    }

    @Override
    public void onCycle() {
        boolean connFms = remote != Remote.FMS || MainJDEC.FMS_CONNECT.isSelected();
        if (UPDATER != null && CREATOR != null) {
            if (connFms) {
                if (NET_IF_INIT.get(remote, protocol)) {
                    iface.write(CREATOR.create(remote, protocol));
                    byte[] data = iface.read();
                    if (data == null || (protocol == Protocol.UDP && data.length == 0)) {
                        UPDATER.update(ParserNull.getInstance(), remote, protocol);
                        NET_IF_INIT.set(false, remote, protocol);
                    } else {
                        UPDATER.update(getPacketParser(remote, protocol, data), remote, protocol);
                    }
                } else {
                    UPDATER.update(ParserNull.getInstance(), remote, protocol);
                    reloadInterface();
                }
            } else {
                UPDATER.update(ParserNull.getInstance(), remote, protocol);
                NET_IF_INIT.set(false, remote, protocol);
            }
        }
    }

    public void reloadInterface() {
        PacketCounters.get(remote, protocol).reset();
        if (UPDATER != null) {
            UPDATER.update(ParserNull.getInstance(), remote, protocol);
        }
        if (iface != null) {
            iface.close();
            iface = null;
        }
        boolean isFms = remote == Remote.FMS;
        if (isFms && !MainJDEC.FMS_CONNECT.isSelected()) {
            NET_IF_INIT.set(false, remote, protocol);
            return;
        }
        try {
            String ip = isFms ?
                    AddressConstants.FMS_IP :
                    AddressConstants.getRioAddress();
            boolean reachable = exceptionPingTest(ip);
            if (!reachable) {
                uninitialize(isFms);
                return;
            }
            PortTriple ports = isFms ?
                    AddressConstants.getFMSPorts() :
                    AddressConstants.getRioPorts();
            iface = protocol == Protocol.TCP ?
                    new TCPInterface(ip, ports.getTcp()) :
                    new UDPInterface(ip, ports.getUdpClient(), ports.getUdpServer());
            NET_IF_INIT.set(true, remote, protocol);
        } catch (IOException e) {
            uninitialize(isFms);
        }
    }

    private void uninitialize(boolean isFms) {
        NET_IF_INIT.set(false, remote, protocol);
        if (isFms) {
            MainJDEC.FMS_CONNECT.setSelected(false);
        } else {
            MainJDEC.IS_ENABLED.setEnabled(false);
            if (MainJDEC.IS_ENABLED.isSelected()) {
                MainJDEC.IS_ENABLED.setSelected(false);
            }
        }
    }

    private static String createName(Remote remote, Protocol protocol) {
        String proto = protocol.name().charAt(0) + protocol.name().substring(1).toLowerCase();
        return (remote == Remote.ROBO_RIO ? "rio" : "fms") + proto;
    }

    public static boolean pingTest(String ip) {
        try {
            return exceptionPingTest(ip);
        } catch (IOException ignored) {
        }
        return false;
    }

    public static boolean exceptionPingTest(String ip) throws IOException {
        try {
            return InetAddress.getByName(ip).isReachable(1000);
        } catch (UnknownHostException ignored) {
            throw new IOException("Unknown host " + ip);
        }
    }
}
