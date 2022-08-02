package com.example.myapplicationudprev;

import static com.example.myapplicationudprev.Params.EFRAME_LEN;
import static com.example.myapplicationudprev.Params.FRAME_BUF_SIZE;
import static com.example.myapplicationudprev.Params.PFRAME_LEN;
import static com.example.myapplicationudprev.Params.SFRAME_LEN;
import static java.lang.Thread.sleep;

import android.media.MediaCodec;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UDPRev {
    private final static String TAG = UDPRev.class.getSimpleName();

    private final static long TIME_US = 100000;
    DatagramSocket socket;
    byte[] data;
    private MediaCodec mediaCodec;

    FrameFinder frameFinder = new FrameFinder();

    public ArrayBlockingQueue<byte[]> getH264Queue() {
        return h264Queue;
    }

    private ArrayBlockingQueue<byte[]> h264Queue = new ArrayBlockingQueue<>(FRAME_BUF_SIZE);


    // private List<MyFrame> myFrameList = new ArrayList<>();

    public int getRevDataLen() {
        return revDataLen;
    }

    DatagramPacket packet;

    public FrameFinder getFrameFinder() {
        return frameFinder;
    }

    // public List<MyFrame> getMyFrameList() {
    // return myFrameList;
    // }

    public int getFrameSize() {
        return framesize;
    }

    int framesize = 0;
    int revDataLen = 0;
    int inputBufferId;
    ByteBuffer inputBuffer;
    long presentationTime = 0;
    boolean newPacketReceived = false;

    public boolean isNewPacketReceived() {
        return newPacketReceived;
    }

    public void setNewPacketReceived(boolean newPacketReceived) {
        this.newPacketReceived = newPacketReceived;
    }

    public boolean isIfNotStartDecode() {
        return ifNotStartDecode;
    }

    boolean ifNotStartDecode = true;

    public String getSpecialPacket() {
        return specialPacket;
    }

    String specialPacket = "";

    int lastPacketLen;
    MyFrame newFrame;

    public int getLastPacketLen() {
        return lastPacketLen;
    }

    public UDPRev(MediaCodec _mediaCodec) {

        this.mediaCodec = _mediaCodec;

        try {
            socket = new DatagramSocket(54321);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        executorService.execute(
                () -> {
                    while (true) {
                        data = new byte[200000];
                        packet = new DatagramPacket(data, data.length);
                        try {
                            socket.receive(packet);
                            int packetLength = packet.getLength();

                            if (frameFinder.ifFindFrame(data, packetLength, h264Queue)) {

                                if (frameFinder.getStateNow() == 'S' && ifNotStartDecode) {
                                    Log.d(TAG, "Decode start! ");
                                    // mediaCodec.start();
                                    ifNotStartDecode = false;
                                    mediaCodec.start();
                                }

                                // newPacketReceived = true;
                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        try {
                            sleep(5);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                });

        /*
        new Thread(new Runnable() {
            int dataFrameLen = 0;

            @Override
            public void run() {
                while (true) {
                    data = new byte[200000];
                    packet = new DatagramPacket(data, data.length);
                    try {
                        socket.receive(packet);
                        int tmp = packet.getLength();

                        // lastPacketLen = frameFinder.ifFindFrame(data, tmp, myFrameList);
                        // frameFinder.ifFindFrame(data, tmp, myFrameList);

                        if (frameFinder.ifFindFrame(data, tmp, h264Queue)) {

                            if (frameFinder.getStateNow() == 'S' && ifNotStartDecode == true) {
                                Log.d(TAG, "Decode start! ");
                                // mediaCodec.start();
                                ifNotStartDecode = false;
                                mediaCodec.start();
                            }

                            // 简单复位一下
                            // lastPacketLen = 0;

                            newPacketReceived = true;
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    try {
                        sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
         */
    }

//    public void printMyFrameBufferList() {
//        String result = "";
//        if (myFrameList.size() > 0) {
//
//            for (int i = 0; i < myFrameList.size(); i++) {
//                result = result + myFrameList.get(i).getFrameType();
//            }
//            // Log.d(TAG, "MyList like: " + result);
//        }
//    }
}
