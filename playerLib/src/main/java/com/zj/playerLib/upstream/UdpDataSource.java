package com.zj.playerLib.upstream;

import android.net.Uri;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketException;

public final class UdpDataSource extends BaseDataSource {
    public static final int DEFAULT_MAX_PACKET_SIZE = 2000;
    public static final int DEFAULT_SOCKET_TIMEOUT_MILLIS = 8000;
    private final int socketTimeoutMillis;
    private final byte[] packetBuffer;
    private final DatagramPacket packet;
    @Nullable
    private Uri uri;
    @Nullable
    private DatagramSocket socket;
    @Nullable
    private MulticastSocket multicastSocket;
    @Nullable
    private InetAddress address;
    @Nullable
    private InetSocketAddress socketAddress;
    private boolean opened;
    private int packetRemaining;

    public UdpDataSource() {
        this(2000);
    }

    public UdpDataSource(int maxPacketSize) {
        this(maxPacketSize, 8000);
    }

    public UdpDataSource(int maxPacketSize, int socketTimeoutMillis) {
        super(true);
        this.socketTimeoutMillis = socketTimeoutMillis;
        this.packetBuffer = new byte[maxPacketSize];
        this.packet = new DatagramPacket(this.packetBuffer, 0, maxPacketSize);
    }

    /** @deprecated */
    @Deprecated
    public UdpDataSource(@Nullable TransferListener listener) {
        this(listener, 2000);
    }

    /** @deprecated */
    @Deprecated
    public UdpDataSource(@Nullable TransferListener listener, int maxPacketSize) {
        this(listener, maxPacketSize, 8000);
    }

    /** @deprecated */
    @Deprecated
    public UdpDataSource(@Nullable TransferListener listener, int maxPacketSize, int socketTimeoutMillis) {
        this(maxPacketSize, socketTimeoutMillis);
        if (listener != null) {
            this.addTransferListener(listener);
        }

    }

    public long open(DataSpec dataSpec) throws UdpDataSourceException {
        this.uri = dataSpec.uri;
        String host = this.uri.getHost();
        int port = this.uri.getPort();
        this.transferInitializing(dataSpec);

        try {
            this.address = InetAddress.getByName(host);
            this.socketAddress = new InetSocketAddress(this.address, port);
            if (this.address.isMulticastAddress()) {
                this.multicastSocket = new MulticastSocket(this.socketAddress);
                this.multicastSocket.joinGroup(this.address);
                this.socket = this.multicastSocket;
            } else {
                this.socket = new DatagramSocket(this.socketAddress);
            }
        } catch (IOException var6) {
            throw new UdpDataSourceException(var6);
        }

        try {
            this.socket.setSoTimeout(this.socketTimeoutMillis);
        } catch (SocketException var5) {
            throw new UdpDataSourceException(var5);
        }

        this.opened = true;
        this.transferStarted(dataSpec);
        return -1L;
    }

    public int read(byte[] buffer, int offset, int readLength) throws UdpDataSourceException {
        if (readLength == 0) {
            return 0;
        } else {
            if (this.packetRemaining == 0) {
                try {
                    this.socket.receive(this.packet);
                } catch (IOException var6) {
                    throw new UdpDataSourceException(var6);
                }

                this.packetRemaining = this.packet.getLength();
                this.bytesTransferred(this.packetRemaining);
            }

            int packetOffset = this.packet.getLength() - this.packetRemaining;
            int bytesToRead = Math.min(this.packetRemaining, readLength);
            System.arraycopy(this.packetBuffer, packetOffset, buffer, offset, bytesToRead);
            this.packetRemaining -= bytesToRead;
            return bytesToRead;
        }
    }

    @Nullable
    public Uri getUri() {
        return this.uri;
    }

    public void close() {
        this.uri = null;
        if (this.multicastSocket != null) {
            try {
                this.multicastSocket.leaveGroup(this.address);
            } catch (IOException var2) {
            }

            this.multicastSocket = null;
        }

        if (this.socket != null) {
            this.socket.close();
            this.socket = null;
        }

        this.address = null;
        this.socketAddress = null;
        this.packetRemaining = 0;
        if (this.opened) {
            this.opened = false;
            this.transferEnded();
        }

    }

    public static final class UdpDataSourceException extends IOException {
        public UdpDataSourceException(IOException cause) {
            super(cause);
        }
    }
}
