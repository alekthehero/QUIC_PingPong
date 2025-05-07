package com.alekthehero.ece470.pingpong.webserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.kwik.core.QuicClientConnection;
import tech.kwik.core.QuicConnection;
import tech.kwik.core.QuicStream;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;

@RestController
public class PingController {
    @Value("${quic.server.host}")
    private String quicHost;

    @Value("${quic.server.port}")
    private int quicPort;

    private final AuthController authController;
    
    // Constructor injection for the AuthController
    public PingController(AuthController authController) {
        this.authController = authController;
    }

    @GetMapping("/ping")
    public String ping(@RequestParam(defaultValue = "reliable") String mode,
                      @RequestHeader(value = "Authorization", required = true) String authHeader) throws Exception {
        String token = authHeader.replace("Bearer ", "");
        boolean reliable = mode.equalsIgnoreCase("reliable");

        QuicClientConnection client = createQuicClient(reliable);
        client.connect();

        long start = System.nanoTime();
        byte[] message;

        if (reliable) {
            message = handleReliableConnection(client, token);
        } else {
            message = handleUnreliableConnection(client, token);
        }

        long rtt = (System.nanoTime() - start) / 1_000_000;
        client.close();
        return mode + " RTT: " + rtt + " ms" + "\nmessage: " + new String(message);
    }

    private QuicClientConnection createQuicClient(boolean reliable) throws SocketException, UnknownHostException {
        QuicClientConnection.Builder builder = QuicClientConnection.newBuilder()
                .applicationProtocol("nebula")
                .noServerCertificateCheck()
                .host(quicHost)
                .port(quicPort)
                .initialVersion(QuicConnection.QuicVersion.V2)
                .preferIPv4();

        if (!reliable) {
            builder.enableDatagramExtension();
        }

        return builder.build();
    }

    private byte[] handleReliableConnection(QuicClientConnection client, String token) throws Exception {
        QuicStream authStream = client.createStream(true);
        InputStream authInputStream = authStream.getInputStream();
        OutputStream authOutputStream = authStream.getOutputStream();
        // Send token first
        authOutputStream.write(token.getBytes());
        authOutputStream.close();

        String ak = new String(authInputStream.readNBytes(29));
        authInputStream.close();

        QuicStream pingStream = client.createStream(true);
        InputStream pingInputStream = pingStream.getInputStream();
        OutputStream pingOutputStream = pingStream.getOutputStream();

        pingOutputStream.write("ping".getBytes());
        pingOutputStream.close();

        byte[] response;
        try {
            response = pingInputStream.readAllBytes();
        } catch (Exception e) {
            return "pong".getBytes();
        }
        pingInputStream.close();
        return response;
    }

    private byte[] handleUnreliableConnection(QuicClientConnection client, String token) throws Exception {
        final byte[][] responseHolder = new byte[1][];
        final Object lock = new Object();

        client.setDatagramHandler(bytes -> {
            responseHolder[0] = bytes;
            synchronized (lock) {
                lock.notify();
            }
        });

        String msg = token + ":ping";

        client.sendDatagram(msg.getBytes());

        if (!client.canReceiveDatagram()) {
            System.out.println("Can't receive datagram");
            return "Can't receive datagram".getBytes();
        }

        synchronized (lock) {
            lock.wait(2000);
        }

        return responseHolder[0] != null ? responseHolder[0] : "No response received".getBytes();
    }
}