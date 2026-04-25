package com.szzh.loggerserver.util;

import com.szzh.loggerserver.support.exception.ProtocolParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 平台协议解析 工具类
 *
 * @author wr
 */
public class ProtocolMessageUtil {
    private static final int HEADER = 0x90EB;

    private static final int TAIL = 0x6F14;

    private static final int MIN_PACKET_LENGTH = 2 + 4 + 2 + 4 + 4 + 8 + 2;

    private static Logger logger = LoggerFactory.getLogger(ProtocolMessageUtil.class);

    /**
     * 解析平台消息协议。
     * 将协议数据包转为对象返回。
     *
     * @param rawData 协议数据包。
     * @return 协议对象。
     */
    public static ProtocolData parseData(byte[] rawData) {
        if (rawData == null || rawData.length < MIN_PACKET_LENGTH) {
            throw new ProtocolParseException("协议长度非法");
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN);
            int header = Short.toUnsignedInt(buffer.getShort());
            if (header != HEADER) {
                throw new ProtocolParseException("协议包头非法");
            }

            int senderId = buffer.getInt();
            int messageType = Short.toUnsignedInt(buffer.getShort());
            int messageCode = buffer.getInt();
            int dataLength = buffer.getInt();
            if (dataLength < 0) {
                throw new ProtocolParseException("协议数据长度非法");
            }

            int expectedPacketLength = MIN_PACKET_LENGTH + dataLength;
            if (rawData.length != expectedPacketLength) {
                throw new ProtocolParseException("协议包长度与数据域不匹配");
            }

            byte[] data = new byte[dataLength];
            buffer.get(data);
            buffer.getLong();

            int tail = Short.toUnsignedInt(buffer.getShort());
            if (tail != TAIL) {
                throw new ProtocolParseException("协议包尾非法");
            }

            ProtocolData protocolData = new ProtocolData();
            protocolData.setSenderId(senderId);
            protocolData.setMessageType(messageType);
            protocolData.setMessageCode(messageCode);
            protocolData.setRawData(data);
            return protocolData;
        } catch (ProtocolParseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            logger.debug("协议解析发生底层异常，rawLength={}", rawData.length, exception);
            throw new ProtocolParseException("协议解析失败", exception);
        }
    }

    public static byte[] buildData(int senderId, short messageType, int messageNum, String dataString) {
        byte[] dataBytes = dataString.getBytes(StandardCharsets.UTF_8);
        return buildData(senderId, messageType, messageNum, dataBytes);
    }

    public static byte[] buildData(int senderId, short messageType, int messageNum, byte[] dataBytes) {
        ByteBuffer buffer = ByteBuffer.allocate(2 + 4 + 2 + 4 + 4 + dataBytes.length + 8 + 2).order(ByteOrder.LITTLE_ENDIAN);
        // 协议头2字节
        buffer.putShort((short) HEADER);
        // 发送方标志4字节
        buffer.putInt(senderId);
        // 消息类型编号 2字节，固定为0
        buffer.putShort(messageType);
        // 消息编号 4字节，仿真开始为0，仿真结束为1
        buffer.putInt(messageNum);
        // 数据域长度 4字节
        buffer.putInt(dataBytes.length);
        // 数据域
        buffer.put(dataBytes);
        // 时间戳 8字节
//		byte[] time = {1, 2, 3, 4, 5, 6, 7, 8};
//		buffer.put(time);
        buffer.putLong(new Date().getTime());
        // 协议尾部2字节
        buffer.putShort((short) TAIL);
        byte[] output = buffer.array();

        String hexString = getHexString(output);
//		logger.info("构建数据包，长度：{}，数据：{}", output.length, hexString);

        return output;
    }

    /**
     * 二进制数组转整型
     *
     * @param bytes
     * @return
     */
    public static int bytesToInt(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        return buffer.getInt();
    }

    /**
     * 二进制数组转短整型
     *
     * @param bytes
     * @return
     */
    public static int bytesToShort(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        return buffer.getShort();
    }

    /**
     * 返回二进制字符串
     *
     * @param byteArray
     * @return
     */
    public static String byteArrayToBinaryString(byte[] byteArray) {
        StringBuilder binaryString = new StringBuilder();
        for (byte b : byteArray) {
            String binary = Integer.toBinaryString(b & 0xFF);
            while (binary.length() < 8) {
                binary = "0" + binary;
            }
            binaryString.append(binary).append("");
        }
        return binaryString.toString().trim();
    }

    /**
     * 打印十六进制数据
     *
     * @param data
     */
    public static String getHexString(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            String hexStr = String.format("%02X", b);
            sb.append(hexStr);
        }
        return sb.toString();
    }
}
