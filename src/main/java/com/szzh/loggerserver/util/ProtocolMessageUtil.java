package com.szzh.loggerserver.util;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;

/**
 * 平台协议解析 工具类
 *
 * @author wr
 */
public class ProtocolMessageUtil {
    private static Logger logger = LoggerFactory.getLogger(ProtocolMessageUtil.class);

    public static void main(String args[]) throws IOException {
        // 数据域
        String dataString = "test json";
        byte[] dataByte = dataString.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(2 + 4 + 4 + 4 + 4 + dataByte.length + 8 + 2).order(ByteOrder.LITTLE_ENDIAN);
        // 协议头2字节
        buffer.putShort((short) 0xAABB);
        // 发送方标志4字节
        buffer.putInt(0);
        // 消息类型编号 4字节，固定为0
        buffer.putInt(0);
        // 消息编号 4字节，仿真开始为0，仿真结束为1
        buffer.putInt(0);
        // 数据域长度 4字节
        buffer.putInt(dataByte.length);
        // 数据域
        buffer.put(dataByte);
        // 时间戳 8字节
        byte[] time = {1, 2, 3, 4, 5, 6, 7, 8};
        buffer.put(time);
        // 协议尾部2字节
        buffer.putShort((short) 0xAABB);
        byte[] output = buffer.array();
        logger.info("二进制消息长度为:" + output.length);
        logger.info("二进制消息为:" + Arrays.toString(output));
        logger.info("二进制数据域消息为:" + Arrays.toString(dataByte));

        // 2、解析
        // 解析协议数据包为对象
        ProtocolData protocolData = ProtocolMessageUtil.parseData(output);
        byte[] dataBytes = protocolData.getRawData();
        // 获取数据域
        String dataStr = new String(dataBytes, StandardCharsets.UTF_8);
        System.out.print("解析后的消息为：" + dataStr);
    }

    /**
     * 解析平台消息协议。
     * 将协议数据包转为对象返回。
     *
     * @param rawData 协议数据包。
     * @return
     * @throws IOException
     */
    public static ProtocolData parseData(byte[] rawData) {
        String hexString = getHexString(rawData);
//		logger.info("解析数据包，长度：{}，数据：{}", rawData.length, hexString);
        if (!hexString.startsWith("EB90")) {
            logger.info("数据格式不匹配");
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN);
        // 解析字段
        // 协议头，2个字节，固定AABB
        byte[] header = new byte[2];
        buffer.get(header);
        // 发送方标志 4字节
        byte[] flag = new byte[4];
        buffer.get(flag);
        int senderId = bytesToInt(flag);

        // 消息类型编号 4字节，固定为0
        byte[] shortFlag = new byte[2];
        buffer.get(shortFlag);

        int msgType = bytesToShort(shortFlag);

        // 消息编号 4字节，仿真开始为0，仿真结束为1
        buffer.get(flag);
        int msgCode = bytesToInt(flag);
        // 数据域长度，4字节
        buffer.get(flag);
        int length = bytesToInt(flag);
        if (length <= 0 || length > 1000 * 100) {
            logger.info("数据格式不匹配");
            return null;
        }
        // 数据域
        byte[] data = new byte[length];
        buffer.get(data);
        // 时间戳
        byte[] time = new byte[8];
        buffer.get(time);
        // 协议尾
        buffer.get(header);

        ProtocolData protocolData = new ProtocolData();
        protocolData.setSenderId(senderId);
        protocolData.setMessageType(msgType);
        protocolData.setMessageCode(msgCode);
        protocolData.setRawData(data);

        return protocolData;
    }

    public static byte[] buildData(int senderId, short messageType, int messageNum, String dataString) {
        byte[] dataBytes = dataString.getBytes(StandardCharsets.UTF_8);
        return buildData(senderId, messageType, messageNum, dataBytes);
    }

    public static byte[] buildData(int senderId, short messageType, int messageNum, byte[] dataBytes) {
        ByteBuffer buffer = ByteBuffer.allocate(2 + 4 + 2 + 4 + 4 + dataBytes.length + 8 + 2).order(ByteOrder.LITTLE_ENDIAN);
        // 协议头2字节
        buffer.putShort((short) 0x90EB);
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
        buffer.putShort((short) 0x6F14);
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
