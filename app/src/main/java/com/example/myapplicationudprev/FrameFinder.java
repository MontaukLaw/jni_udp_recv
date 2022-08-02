package com.example.myapplicationudprev;

import static com.example.myapplicationudprev.Params.FRAME_BUF_SIZE;

import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

public class FrameFinder {

    private final static String TAG = FrameFinder.class.getSimpleName();
    private final static int BUF_SIZE = 3000000;

    private byte frameBuffer[] = new byte[BUF_SIZE];

    private byte SFrameBuffer[] = new byte[20];
    private byte PFrameBuffer[] = new byte[20];
    private byte EFrameBuffer[] = new byte[20];

    int lastPacketLen = 0;

    public int getDataFrameCounter() {
        return dataFrameCounter;
    }

    int dataFrameCounter = 0;

    public void setLastPacketLen(int lastPacketLen) {
        this.lastPacketLen = lastPacketLen;
    }

    public char getStateNow() {
        return stateNow;
    }

    char stateNow;
    char stateLast;

    public byte[] getSFrameBuffer() {
        return SFrameBuffer;
    }

    public byte[] getPFrameBuffer() {
        return PFrameBuffer;
    }

    public byte[] getEFrameBuffer() {
        return EFrameBuffer;
    }

    public byte[] getFrameBuffer() {
        return frameBuffer;
    }

    private int bufIdx = 0;

    private int frameByteCounter = 0;

    public int getDataFrameLen() {
        return dataFrameLen;
    }

    public void setDataFrameLen(int dataFrameLen) {
        this.dataFrameLen = dataFrameLen;
    }

    private int dataFrameLen = 0;

    private void restBuf() {
        for (int i = 0; i < BUF_SIZE; i++) {
            frameBuffer[i] = (byte) 0xFF;
        }

        bufIdx = 0;

        frameByteCounter = 0;
    }

    public FrameFinder() {
        restBuf();
    }

    public int addNewData(byte newData[], int newDataLen) {

        if (BUF_SIZE < bufIdx + newDataLen) {
            restBuf();
            return 0;
        }
        // 相当于memcpy
        for (int i = 0; i < newDataLen; i++) {
            frameBuffer[i + bufIdx] = newData[i];
        }

        // 指针移动
        bufIdx = bufIdx + newDataLen;

        int keyIdx = findFrame();

        if (keyIdx > 0) {

            Log.d(TAG, "New Frame start at " + keyIdx);

            restBuf();

            return keyIdx;

        }

        return 0;

    }

    public int findFrame() {

        byte b1 = 0, b2 = 0, b3 = 0, b4 = 0;

        // 对缓冲区所有数据进行遍历
        for (int i = 0; i < bufIdx; i++) {
            b1 = b2;
            b2 = b3;
            b3 = b4;
            b4 = frameBuffer[i];

            if (b1 == 0 && b2 == 0 && b3 == 0 && b4 == 1) {
                return i;
            }
        }

        return 0;
    }

    private void setFrameState(int newDataLen) {
        if (newDataLen == 19) {
            stateNow = 'S';  // SPS帧， 第1个帧
        } else if (newDataLen == 8) {
            stateNow = 'P';   // PPS帧  第2个帧
        } else if (newDataLen == 9) {
            stateNow = 'E';   // E帧  第3个帧
        } else {
            stateNow = 'D';    // 数据帧
        }
    }

    public int findFrame(byte newData[], int newDataLen, List<MyFrame> frameBufferList) {
        MyFrame myFrame;

        if (newData[0] == 0 && newData[1] == 0 && newData[2] == 0 && newData[3] == 1) {

            // 只有当出现分隔符的时候才返回非0值
            setFrameState(newDataLen);

            int rtn = lastPacketLen;

            dataFrameLen = lastPacketLen;

            lastPacketLen = 0;

            // 当前的数据帧是S帧
            if (stateNow == 'S') {

                // 如果缓冲区满了, 需要去头
                cutBufferHeadForS(frameBufferList);

                // 加入数据帧
                myFrame = new MyFrame(frameBuffer, dataFrameCounter, 'D');
                frameBufferList.add(myFrame);

                // 加入本身这个S帧
                myFrame = new MyFrame(newData, newDataLen, 'S');
                frameBufferList.add(myFrame);

                return newDataLen;

            } else if (stateNow == 'P') {
                cutBufferHead(frameBufferList);
                // 加入这个P帧
                myFrame = new MyFrame(newData, newDataLen, 'P');
                frameBufferList.add(myFrame);

                return newDataLen;
            } else if (stateNow == 'E') {
                cutBufferHead(frameBufferList);
                myFrame = new MyFrame(newData, newDataLen, 'E');
                frameBufferList.add(myFrame);

                return newDataLen;
            } else {

                myFrame = new MyFrame(frameBuffer, dataFrameCounter, 'D');
                frameBufferList.add(myFrame);

                // 新数据帧的开头部分
                for (int i = 0; i < newDataLen; i++) {

                    frameBuffer[i] = newData[i];
                }

                dataFrameCounter = newDataLen;

                return rtn;
            }

        }

        // 数据帧的后面的包
        for (int i = 0; i < newDataLen; i++) {

            frameBuffer[dataFrameCounter + i] = newData[i];

            dataFrameCounter++;
        }

        dataFrameCounter = dataFrameCounter + newDataLen;

        lastPacketLen = lastPacketLen + newDataLen;

        return 0;
    }

    public synchronized void cutBufferHeadForS(List<MyFrame> frameBufferList) {

        if (frameBufferList.size() == FRAME_BUF_SIZE) {

            frameBufferList.remove(0);
            frameBufferList.remove(0);
        }
    }

    public synchronized void cutBufferHead(List<MyFrame> frameBufferList) {

        if (frameBufferList.size() == FRAME_BUF_SIZE) {

            frameBufferList.remove(0);
        }
    }

    private void printListItemType(List<MyFrame> frameBufferList) {

        String str = "";
        for (int i = 0; i < frameBufferList.size(); i++) {
            str = str + frameBufferList.get(i).getFrameType() + frameBufferList.get(i).getDataLen() + " ";
        }
        Log.d(TAG, "" + str);
    }

    private int frameCounter = 0;
    boolean ifBehindEFrame = false;

    public void putData(byte[] buffer, ArrayBlockingQueue<byte[]> h264Queue) {

        // Log.d(TAG, "putData len " + buffer.length);
        if (h264Queue.size() >= FRAME_BUF_SIZE) {
            h264Queue.poll();
        }

        h264Queue.add(buffer);
    }

    int simpleCounter = 0;

    public void insertBufferFrame(byte[] data, int _dataLen, char type, ArrayBlockingQueue<byte[]> h264Queue) {
        // MyFrame myFrame = new MyFrame(data, _dataLen, type);
        byte[] frameData = new byte[_dataLen];

        for (int i = 0; i < _dataLen; i++) {
            frameData[i] = data[i];
        }

        putData(frameData, h264Queue);

        if (simpleCounter < 300) {
            saveFile(frameData);
        }
        simpleCounter++;
    }

    File file;
    FileOutputStream fos = null;
    int size = 0;

    private void saveFile(byte[] data) {
        file = new File("data/data/com.example.myapplicationudprev/aaa.h264");

        try {
            fos = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            assert fos != null;
            fos.write(data);
            size = size + data.length;
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean ifFindFrame(byte newData[], int newDataLen, ArrayBlockingQueue<byte[]> h264Queue) {

        MyFrame myFrame;
        // Log.d(TAG, "newDataLen: " + newDataLen);

        if (newData[0] == 0 && newData[1] == 0 && newData[2] == 0 && newData[3] == 1) {

            frameCounter++;
            // 只有当出现分隔符的时候才返回非0值
            setFrameState(newDataLen);
            // Log.d(TAG, "New frame: newDataLen:" + newDataLen + " stateNow: " + stateNow);
            // dataFrameLen = lastPacketLen;

            // 当前的数据帧是S帧
            if (stateNow == 'S') {

                // Log.d(TAG, "frameCounter in 1 sec: " + frameCounter);
                frameCounter = 0;
                // 如果缓冲区满了, 需要去头
                // cutBufferHeadForS(h264Queue);

                // 加入数据帧
                insertBufferFrame(frameBuffer, dataFrameCounter, 'D', h264Queue);

                // myFrame = new MyFrame(frameBuffer, dataFrameCounter, 'D');
                // frameBufferList.add(myFrame);
                // Log.d(TAG, "data frame:" + dataFrameCounter);

                insertBufferFrame(newData, newDataLen, 'S', h264Queue);
                // 加入本身这个S帧
                // myFrame = new MyFrame(newData, newDataLen, 'S');
                // frameBufferList.add(myFrame);

                // printListItemType(frameBufferList);

            } else if (stateNow == 'P') {
                // 加入这个P帧
                // cutBufferHead(frameBufferList);
                insertBufferFrame(newData, newDataLen, 'P', h264Queue);

                // myFrame = new MyFrame(newData, newDataLen, 'P');
                // frameBufferList.add(myFrame);

            } else if (stateNow == 'E') {
                // cutBufferHead(frameBufferList);

                insertBufferFrame(newData, newDataLen, 'E', h264Queue);
                // myFrame = new MyFrame(newData, newDataLen, 'E');
                // frameBufferList.add(myFrame);
                ifBehindEFrame = true;

            } else {

                if (!ifBehindEFrame) {
                    // cutBufferHead(frameBufferList);
                    insertBufferFrame(frameBuffer, dataFrameCounter, 'D', h264Queue);

                    // myFrame = new MyFrame(frameBuffer, dataFrameCounter, 'D');
                    // frameBufferList.add(myFrame);
                }

                if (ifBehindEFrame) {
                    ifBehindEFrame = false;
                }

                // Log.d(TAG, "data frame:" + dataFrameCounter);
                // 新数据帧的开头部分
                for (int i = 0; i < newDataLen; i++) {

                    frameBuffer[i] = newData[i];
                }

                dataFrameCounter = newDataLen;

            }

            return true;

        } else {

            // 数据帧的后面的包
            for (int i = 0; i < newDataLen; i++) {

                frameBuffer[dataFrameCounter] = newData[i];

                dataFrameCounter++;
            }

            // dataFrameCounter = dataFrameCounter + newDataLen;

            return false;
        }
    }
}
