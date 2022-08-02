package com.example.myapplicationudprev;

public class MyFrame {
    private char frameType;
    private byte[] frameData;

    private int dataLen;

    public long getTimeStampUs() {
        return timeStampUs;
    }

    private long timeStampUs;

    public MyFrame(byte[] data, int _dataLen, char _frameType) {
        frameType = _frameType;
        frameData = new byte[_dataLen];  // 待确定

        for (int i = 0; i < _dataLen; i++) {
            frameData[i] = data[i];
        }

        dataLen = _dataLen;
        timeStampUs = System.currentTimeMillis() * 1000;
    }

    public char getFrameType() {
        return frameType;
    }

    public byte[] getFrameData() {
        return frameData;
    }

    public int getDataLen() {
        return dataLen;
    }
}
