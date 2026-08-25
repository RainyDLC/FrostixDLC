package ru.white.bot.model;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.Getter;
import lombok.Setter;
import ru.white.bot.protocol.BotPacketHelper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class HeadlessBot {

    public enum BotState {
        DISCONNECTED("Отключен", 0xFFFF5555),
        CONNECTING("Подключение...", 0xFFFFAA00),
        LOGIN("Авторизация...", 0xFFFFFF55),
        CONFIGURATION("Конфигурация...", 0xFF55FFFF),
        PLAYING("В игре", 0xFF55FF55),
        ERROR("Ошибка", 0xFFFF3333);

        @Getter private final String display;
        @Getter private final int color;

        BotState(String display, int color) {
            this.display = display;
            this.color = color;
        }
    }

    private static final EventLoopGroup WORKER_GROUP = new NioEventLoopGroup(2);

    @Getter private final String name;
    @Getter private final UUID uuid;
    @Getter private final String host;
    @Getter private final int port;

    @Getter @Setter private volatile BotState state = BotState.DISCONNECTED;
    @Getter @Setter private volatile String statusMessage = "";

    @Getter @Setter private volatile float health = 20.0F;
    @Getter @Setter private volatile int food = 20;
    @Getter @Setter private volatile long ping = 0;
    @Getter @Setter private volatile double x = 0;
    @Getter @Setter private volatile double y = 0;
    @Getter @Setter private volatile double z = 0;
    @Getter @Setter private volatile float yaw = 0;
    @Getter @Setter private volatile float pitch = 0;
    @Getter @Setter private volatile boolean onGround = true;
    @Getter @Setter private volatile int selectedSlot = 0;

    @Getter @Setter private boolean following = false;
    @Getter @Setter private boolean attacking = false;
    @Getter @Setter private boolean autoRegister = true;
    @Getter @Setter private String password = "Password123";

    private Channel channel;
    private int compressionThreshold = -1;
    private int protocolState = 0; // 0 = Handshake, 1 = Status, 2 = Login, 3 = Config, 4 = Play
    private long lastPingSentTime = 0;

    public HeadlessBot(String name, String host, int port) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    public void connect() {
        if (state == BotState.CONNECTING || state == BotState.PLAYING) return;

        state = BotState.CONNECTING;
        statusMessage = "Подключение к " + host + ":" + port;

        new Thread(() -> {
            try {
                Bootstrap b = new Bootstrap();
                b.group(WORKER_GROUP)
                        .channel(NioSocketChannel.class)
                        .option(ChannelOption.TCP_NODELAY, true)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                channel = ch;
                                ch.pipeline().addLast(new BotChannelHandler());
                            }
                        });

                ChannelFuture future = b.connect(host, port).sync();
                future.channel().closeFuture().addListener(f -> {
                    if (state != BotState.ERROR) {
                        state = BotState.DISCONNECTED;
                        if (statusMessage.isEmpty() || statusMessage.equals("В игре!")) {
                            statusMessage = "Соединение закрыто";
                        }
                    }
                });
            } catch (Exception e) {
                state = BotState.ERROR;
                statusMessage = e.getMessage() != null ? e.getMessage() : "Ошибка подключения";
            }
        }, "Bot-" + name).start();
    }

    public void disconnect() {
        state = BotState.DISCONNECTED;
        statusMessage = "Отключен пользователем";
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
    }

    public boolean isConnected() {
        return channel != null && channel.isActive() && state == BotState.PLAYING;
    }

    public void sendPacket(ByteBuf packetData) {
        if (channel == null || !channel.isActive()) return;
        ByteBuf framed = BotPacketHelper.framePacket(packetData, compressionThreshold);
        channel.writeAndFlush(framed);
    }

    public void sendChat(String message) {
        if (!isConnected()) return;

        if (message.startsWith("/")) {
            // Chat command packet (0x04)
            String cmd = message.substring(1);
            ByteBuf buf = Unpooled.buffer();
            BotPacketHelper.writeVarInt(buf, 0x04);
            BotPacketHelper.writeString(buf, cmd);
            sendPacket(buf);
        } else {
            // Chat message packet (0x06 in 1.21.x)
            ByteBuf buf = Unpooled.buffer();
            BotPacketHelper.writeVarInt(buf, 0x06);
            BotPacketHelper.writeString(buf, message);
            buf.writeLong(System.currentTimeMillis());
            buf.writeLong(ThreadLocalRandom.current().nextLong());
            BotPacketHelper.writeVarInt(buf, 0); // Signature length: 0
            BotPacketHelper.writeVarInt(buf, 0); // Last seen count: 0
            BotPacketHelper.writeVarInt(buf, 0); // BitSet length: 0
            sendPacket(buf);
        }
    }

    public void moveTo(double targetX, double targetY, double targetZ, float targetYaw, float targetPitch, boolean onGroundState) {
        if (!isConnected()) return;
        this.x = targetX;
        this.y = targetY;
        this.z = targetZ;
        this.yaw = targetYaw;
        this.pitch = targetPitch;
        this.onGround = onGroundState;

        // Player Move Position and Rotation (0x1B)
        ByteBuf buf = Unpooled.buffer();
        BotPacketHelper.writeVarInt(buf, 0x1B);
        buf.writeDouble(targetX);
        buf.writeDouble(targetY);
        buf.writeDouble(targetZ);
        buf.writeFloat(targetYaw);
        buf.writeFloat(targetPitch);
        buf.writeBoolean(onGroundState);
        sendPacket(buf);
    }

    public void walk(double forward, double strafe, float cameraYaw, float cameraPitch, boolean jump, boolean sneak, boolean sprint) {
        if (!isConnected()) return;
        double rad = Math.toRadians(cameraYaw);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);

        double speed = sprint ? 0.32 : sneak ? 0.08 : 0.22;
        double dx = (strafe * cos - forward * sin) * speed;
        double dz = (forward * cos + strafe * sin) * speed;

        double newX = this.x + dx;
        double newY = this.y + (jump ? 0.42 : 0);
        double newZ = this.z + dz;

        moveTo(newX, newY, newZ, cameraYaw, cameraPitch, !jump);
    }

    public void jump() {
        if (!isConnected()) return;
        moveTo(x, y + 0.42, z, yaw, pitch, false);
    }

    public void swing() {
        if (!isConnected()) return;
        // Hand Swing (0x36)
        ByteBuf buf = Unpooled.buffer();
        BotPacketHelper.writeVarInt(buf, 0x36);
        BotPacketHelper.writeVarInt(buf, 0); // Main hand
        sendPacket(buf);
    }

    public void attack(int targetEntityId) {
        if (!isConnected()) return;
        // Interact Entity (0x16 in 1.21.x)
        ByteBuf buf = Unpooled.buffer();
        BotPacketHelper.writeVarInt(buf, 0x16);
        BotPacketHelper.writeVarInt(buf, targetEntityId);
        BotPacketHelper.writeVarInt(buf, 1); // Action: 1 = Attack
        buf.writeBoolean(false); // Using secondary action / Sneak
        sendPacket(buf);

        swing();
    }

    public void setSlot(int slot) {
        if (!isConnected()) return;
        this.selectedSlot = Math.max(0, Math.min(8, slot));
        // Update Selected Slot (0x2F in 1.21.x)
        ByteBuf buf = Unpooled.buffer();
        BotPacketHelper.writeVarInt(buf, 0x2F);
        buf.writeShort(this.selectedSlot);
        sendPacket(buf);
    }

    private void sendHandshake() {
        protocolState = 2; // Login state
        int protocolVersion = 767;

        // Handshake packet (0x00)
        ByteBuf buf = Unpooled.buffer();
        BotPacketHelper.writeVarInt(buf, 0x00);
        BotPacketHelper.writeVarInt(buf, protocolVersion);
        BotPacketHelper.writeString(buf, host);
        buf.writeShort(port);
        BotPacketHelper.writeVarInt(buf, 2); // Next State: 2 = Login
        sendPacket(buf);

        // Login Start packet (0x00)
        ByteBuf loginBuf = Unpooled.buffer();
        BotPacketHelper.writeVarInt(loginBuf, 0x00);
        BotPacketHelper.writeString(loginBuf, name);
        BotPacketHelper.writeUuid(loginBuf, uuid);
        sendPacket(loginBuf);

        state = BotState.LOGIN;
        statusMessage = "Вход под именем " + name;
    }

    private void sendClientInformation() {
        // Client Information (0x00 in Configuration)
        ByteBuf buf = Unpooled.buffer();
        BotPacketHelper.writeVarInt(buf, 0x00);
        BotPacketHelper.writeString(buf, "ru_ru");
        buf.writeByte(8); // View distance
        BotPacketHelper.writeVarInt(buf, 0); // Chat mode: Enabled
        buf.writeBoolean(true); // Chat colors
        buf.writeByte(127); // Skin parts: all enabled
        BotPacketHelper.writeVarInt(buf, 1); // Main hand: Right
        buf.writeBoolean(false); // Text filtering
        buf.writeBoolean(true); // Server listings
        BotPacketHelper.writeVarInt(buf, 0); // Particle status: All
        sendPacket(buf);

        // Plugin Message: minecraft:brand (0x02 in Configuration)
        sendBrandPacket(0x02);
    }

    private void sendBrandPacket(int packetId) {
        ByteBuf buf = Unpooled.buffer();
        BotPacketHelper.writeVarInt(buf, packetId);
        BotPacketHelper.writeString(buf, "minecraft:brand");
        BotPacketHelper.writeString(buf, "vanilla");
        sendPacket(buf);
    }

    private class BotChannelHandler extends ByteToMessageDecoder {

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            sendHandshake();
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, java.util.List<Object> out) {
            while (in.isReadable()) {
                in.markReaderIndex();
                int packetLength;
                try {
                    packetLength = BotPacketHelper.readVarInt(in);
                } catch (Exception e) {
                    in.resetReaderIndex();
                    return;
                }

                if (packetLength <= 0 || in.readableBytes() < packetLength) {
                    in.resetReaderIndex();
                    return;
                }

                ByteBuf packetSlice = in.readSlice(packetLength);
                ByteBuf decompressed = BotPacketHelper.decompressPacket(packetSlice, compressionThreshold);
                if (decompressed.isReadable()) {
                    handlePacket(decompressed);
                }
            }
        }

        private void handlePacket(ByteBuf buf) {
            int packetId = BotPacketHelper.readVarInt(buf);

            switch (protocolState) {
                case 2 -> handleLoginPacket(packetId, buf);
                case 3 -> handleConfigPacket(packetId, buf);
                case 4 -> handlePlayPacket(packetId, buf);
                default -> {}
            }
        }

        private void handleLoginPacket(int id, ByteBuf buf) {
            if (id == 0x00) {
                // Disconnect in Login
                String reason = BotPacketHelper.readString(buf, 2048);
                String clean = BotPacketHelper.cleanJsonText(reason);
                state = BotState.ERROR;
                statusMessage = "Кик: " + clean;
                disconnect();
            } else if (id == 0x02) {
                // Login Success
                UUID playerUuid = BotPacketHelper.readUuid(buf);
                String username = BotPacketHelper.readString(buf, 16);

                // Send Login Acknowledged (0x03) -> switch to Configuration
                ByteBuf ack = Unpooled.buffer();
                BotPacketHelper.writeVarInt(ack, 0x03);
                sendPacket(ack);

                protocolState = 3; // Config
                state = BotState.CONFIGURATION;
                statusMessage = "Конфигурация...";

                sendClientInformation();
            } else if (id == 0x03) {
                // Set Compression
                compressionThreshold = BotPacketHelper.readVarInt(buf);
            }
        }

        private void handleConfigPacket(int id, ByteBuf buf) {
            if (id == 0x00) {
                // Cookie Request in Config -> reply Cookie Response (0x01)
                try {
                    String cookieKey = BotPacketHelper.readString(buf, 256);
                    ByteBuf reply = Unpooled.buffer();
                    BotPacketHelper.writeVarInt(reply, 0x01);
                    BotPacketHelper.writeString(reply, cookieKey);
                    reply.writeBoolean(false); // No cookie data
                    sendPacket(reply);
                } catch (Exception ignored) {}
            } else if (id == 0x01) {
                // Plugin Message in Config
                try {
                    String channel = BotPacketHelper.readString(buf, 256);
                    if (channel.equals("minecraft:brand")) {
                        sendBrandPacket(0x02);
                    }
                } catch (Exception ignored) {}
            } else if (id == 0x02) {
                // Disconnect in Config
                String reason = BotPacketHelper.readString(buf, 2048);
                String clean = BotPacketHelper.cleanJsonText(reason);
                state = BotState.ERROR;
                statusMessage = "Кик: " + clean;
                disconnect();
            } else if (id == 0x03) {
                // Finish Configuration -> Send Acknowledge (0x03)
                ByteBuf ack = Unpooled.buffer();
                BotPacketHelper.writeVarInt(ack, 0x03);
                sendPacket(ack);

                protocolState = 4; // Play state
                state = BotState.PLAYING;
                statusMessage = "В игре!";

                // Brand in Play state
                sendBrandPacket(0x10);

                // Auto register if enabled
                if (autoRegister) {
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000);
                            sendChat("/register " + password + " " + password);
                            Thread.sleep(600);
                            sendChat("/login " + password);
                        } catch (Exception ignored) {}
                    }).start();
                }
            } else if (id == 0x04) {
                // Keep Alive in Config
                try {
                    long keepAliveId = buf.readLong();
                    ByteBuf reply = Unpooled.buffer();
                    BotPacketHelper.writeVarInt(reply, 0x04);
                    reply.writeLong(keepAliveId);
                    sendPacket(reply);
                } catch (Exception ignored) {}
            } else if (id == 0x05) {
                // Ping in Config
                try {
                    int pingId = buf.readInt();
                    ByteBuf reply = Unpooled.buffer();
                    BotPacketHelper.writeVarInt(reply, 0x05);
                    reply.writeInt(pingId);
                    sendPacket(reply);
                } catch (Exception ignored) {}
            } else if (id == 0x09) {
                // Resource Pack Send in Config -> reply 0x06
                try {
                    UUID packUuid = BotPacketHelper.readUuid(buf);
                    ByteBuf reply = Unpooled.buffer();
                    BotPacketHelper.writeVarInt(reply, 0x06);
                    BotPacketHelper.writeUuid(reply, packUuid);
                    BotPacketHelper.writeVarInt(reply, 3); // 3 = Accepted
                    sendPacket(reply);

                    ByteBuf replyLoaded = Unpooled.buffer();
                    BotPacketHelper.writeVarInt(replyLoaded, 0x06);
                    BotPacketHelper.writeUuid(replyLoaded, packUuid);
                    BotPacketHelper.writeVarInt(replyLoaded, 0); // 0 = Successfully loaded
                    sendPacket(replyLoaded);
                } catch (Exception ignored) {}
            } else if (id == 0x0E) {
                // Select Known Packs -> Echo back
                try {
                    int count = BotPacketHelper.readVarInt(buf);
                    ByteBuf reply = Unpooled.buffer();
                    BotPacketHelper.writeVarInt(reply, 0x07);
                    BotPacketHelper.writeVarInt(reply, count);
                    for (int i = 0; i < count; i++) {
                        String namespace = BotPacketHelper.readString(buf, 256);
                        String packId = BotPacketHelper.readString(buf, 256);
                        String version = BotPacketHelper.readString(buf, 256);

                        BotPacketHelper.writeString(reply, namespace);
                        BotPacketHelper.writeString(reply, packId);
                        BotPacketHelper.writeString(reply, version);
                    }
                    sendPacket(reply);
                } catch (Exception e) {
                    ByteBuf reply = Unpooled.buffer();
                    BotPacketHelper.writeVarInt(reply, 0x07);
                    BotPacketHelper.writeVarInt(reply, 1);
                    BotPacketHelper.writeString(reply, "minecraft");
                    BotPacketHelper.writeString(reply, "core");
                    BotPacketHelper.writeString(reply, "1.21.1");
                    sendPacket(reply);
                }
            }
        }

        private void handlePlayPacket(int id, ByteBuf buf) {
            // Keep Alive (0x26 / 0x24 / 0x23 in 1.21.x)
            if (id == 0x26 || id == 0x24 || id == 0x23 || id == 0x25 || id == 0x27) {
                try {
                    long keepAliveId = buf.readLong();
                    ByteBuf reply = Unpooled.buffer();
                    BotPacketHelper.writeVarInt(reply, 0x18);
                    reply.writeLong(keepAliveId);
                    sendPacket(reply);
                } catch (Exception ignored) {}
            }
            // Ping (0x35 / 0x34 / 0x33)
            else if (id == 0x35 || id == 0x34 || id == 0x33 || id == 0x36) {
                try {
                    int pingId = buf.readInt();
                    ByteBuf reply = Unpooled.buffer();
                    BotPacketHelper.writeVarInt(reply, 0x29);
                    reply.writeInt(pingId);
                    sendPacket(reply);
                    if (lastPingSentTime > 0) {
                        ping = Math.max(1, System.currentTimeMillis() - lastPingSentTime);
                    }
                    lastPingSentTime = System.currentTimeMillis();
                } catch (Exception ignored) {}
            }
            // Synchronize Player Position (0x40 / 0x3E / 0x3F)
            else if (id == 0x40 || id == 0x3E || id == 0x3F || id == 0x41) {
                try {
                    double px = buf.readDouble();
                    double py = buf.readDouble();
                    double pz = buf.readDouble();
                    float pyaw = buf.readFloat();
                    float ppitch = buf.readFloat();
                    byte flags = buf.readByte();
                    int teleportId = BotPacketHelper.readVarInt(buf);

                    x = px;
                    y = py;
                    z = pz;
                    yaw = pyaw;
                    pitch = ppitch;

                    // Teleport Confirm (0x00 in Play)
                    ByteBuf confirm = Unpooled.buffer();
                    BotPacketHelper.writeVarInt(confirm, 0x00);
                    BotPacketHelper.writeVarInt(confirm, teleportId);
                    sendPacket(confirm);

                    // Move Position Rotation (0x1B)
                    moveTo(x, y, z, yaw, pitch, true);
                } catch (Exception ignored) {}
            }
            // Update Health (0x5B / 0x59 / 0x5A)
            else if (id == 0x5B || id == 0x59 || id == 0x5A || id == 0x5C) {
                try {
                    health = buf.readFloat();
                    food = BotPacketHelper.readVarInt(buf);
                    if (health <= 0) {
                        // Client Command / Perform Respawn (0x09)
                        ByteBuf respawn = Unpooled.buffer();
                        BotPacketHelper.writeVarInt(respawn, 0x09);
                        BotPacketHelper.writeVarInt(respawn, 0);
                        sendPacket(respawn);
                    }
                } catch (Exception ignored) {}
            }
            // Disconnect (0x1D / 0x1B / 0x1C)
            else if (id == 0x1D || id == 0x1B || id == 0x1C || id == 0x1E) {
                try {
                    String reason = BotPacketHelper.readString(buf, 2048);
                    String clean = BotPacketHelper.cleanJsonText(reason);
                    state = BotState.DISCONNECTED;
                    statusMessage = "Кик: " + clean;
                } catch (Exception ignored) {}
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            state = BotState.ERROR;
            statusMessage = cause.getMessage() != null ? cause.getMessage() : "Ошибка сети";
            ctx.close();
        }
    }
}
