package ru.white.bot.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class BotPacketHelper {

    private BotPacketHelper() {}

    public static void writeVarInt(ByteBuf buf, int value) {
        while ((value & -128) != 0) {
            buf.writeByte(value & 127 | 128);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    public static int readVarInt(ByteBuf buf) {
        int value = 0;
        int length = 0;
        byte currentByte;
        while (true) {
            if (!buf.isReadable()) return 0;
            currentByte = buf.readByte();
            value |= (currentByte & 127) << (length++ * 7);
            if (length > 5) throw new RuntimeException("VarInt too big");
            if ((currentByte & 128) != 128) break;
        }
        return value;
    }

    public static int getVarIntSize(int value) {
        for (int i = 1; i < 5; i++) {
            if ((value & -1 << i * 7) == 0) return i;
        }
        return 5;
    }

    public static void writeString(ByteBuf buf, String str) {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    public static String readString(ByteBuf buf, int maxLen) {
        int len = readVarInt(buf);
        if (len < 0 || len > maxLen * 4 || buf.readableBytes() < len) {
            return "";
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void writeUuid(ByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    public static UUID readUuid(ByteBuf buf) {
        long most = buf.readLong();
        long least = buf.readLong();
        return new UUID(most, least);
    }

    public static ByteBuf framePacket(ByteBuf packetData, int compressionThreshold) {
        ByteBuf framed = Unpooled.buffer();
        int dataLength = packetData.readableBytes();

        if (compressionThreshold <= 0) {
            writeVarInt(framed, dataLength);
            framed.writeBytes(packetData);
        } else {
            if (dataLength < compressionThreshold) {
                int uncompressedFlagSize = getVarIntSize(0);
                writeVarInt(framed, uncompressedFlagSize + dataLength);
                writeVarInt(framed, 0);
                framed.writeBytes(packetData);
            } else {
                byte[] uncompressed = new byte[dataLength];
                packetData.readBytes(uncompressed);

                byte[] compressed = new byte[dataLength + 64];
                Deflater deflater = new Deflater();
                deflater.setInput(uncompressed);
                deflater.finish();
                int compressedSize = deflater.deflate(compressed);
                deflater.end();

                int uncompressedSizeHeader = getVarIntSize(dataLength);
                writeVarInt(framed, uncompressedSizeHeader + compressedSize);
                writeVarInt(framed, dataLength);
                framed.writeBytes(compressed, 0, compressedSize);
            }
        }
        return framed;
    }

    public static ByteBuf decompressPacket(ByteBuf packetData, int compressionThreshold) {
        if (compressionThreshold <= 0) {
            return packetData;
        }

        int uncompressedLength = readVarInt(packetData);
        if (uncompressedLength == 0) {
            return packetData;
        }

        byte[] compressed = new byte[packetData.readableBytes()];
        packetData.readBytes(compressed);

        byte[] uncompressed = new byte[uncompressedLength];
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        try {
            inflater.inflate(uncompressed);
        } catch (Exception e) {
            return Unpooled.EMPTY_BUFFER;
        } finally {
            inflater.end();
        }

        return Unpooled.wrappedBuffer(uncompressed);
    }
}
