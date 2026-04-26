package com.szzh.common.protocol;

import com.szzh.common.exception.ProtocolParseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * 协议消息工具测试。
 */
class ProtocolMessageUtilTest {

    /**
     * 验证合法协议包能够被正确解析。
     */
    @Test
    void shouldParseValidPacket() {
        byte[] packet = ProtocolMessageUtil.buildData(7, (short) 2100, 9, "payload");

        ProtocolData protocolData = ProtocolMessageUtil.parseData(packet);

        Assertions.assertEquals(7, protocolData.getSenderId());
        Assertions.assertEquals(2100, protocolData.getMessageType());
        Assertions.assertEquals(9, protocolData.getMessageCode());
        Assertions.assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), protocolData.getRawData());
    }

    /**
     * 验证非法包头会抛出协议解析异常。
     */
    @Test
    void shouldThrowWhenHeaderInvalid() {
        byte[] packet = ProtocolMessageUtil.buildData(7, (short) 2100, 9, "payload");
        packet[0] = 0x00;

        Assertions.assertThrows(ProtocolParseException.class, () -> ProtocolMessageUtil.parseData(packet));
    }

    /**
     * 验证非法数据长度会抛出协议解析异常。
     */
    @Test
    void shouldThrowWhenLengthInvalid() {
        byte[] packet = ProtocolMessageUtil.buildData(7, (short) 2100, 9, "payload");
        ByteBuffer.wrap(packet)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(12, 512);

        Assertions.assertThrows(ProtocolParseException.class, () -> ProtocolMessageUtil.parseData(packet));
    }
}
