package com.szzh.loggerserver.util;

import lombok.Data;

@Data
public class ProtocolData {

    private int senderId;

    private int messageType;

    private int messageCode;

    private byte[] rawData;

}
