package com.example.myapplicationudprev;

import static java.lang.Thread.sleep;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

public class MyDecoder {
    private static final String TAG = MyDecoder.class.getSimpleName();

    MediaFormat mVideoFormat;
    long presentationTime = 0;
    MediaCodec mDecoder;
    UDPRev udpRev;
    MyFrame myFrame;

    int framesOutPerSec = 0;
    int framesInPerSec = 0;
    long timeSec = 0;
    long timeOutMs = 0;
    long timeInSec = 0;
    long timeInMs = 0;

    // private ArrayBlockingQueue<byte[]> h264Queue = new ArrayBlockingQueue<>(10);

    ByteBuffer decoderInputBuffer;

    public MyDecoder(MediaCodec _mDecoder, UDPRev _udpRev) {
        mDecoder = _mDecoder;
        udpRev = _udpRev;
    }

//    private synchronized void protectedRemove() {
//        udpRev.getMyFrameList().remove(0);
//    }

    /*
    private void insertDecodeBuffer(int inputBufferId) {

        while (udpRev.getMyFrameList().size() == 0) {
            try {
                sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        framesInPerSec++;
//        long timeNowMs = System.currentTimeMillis();
//        while ((timeNowMs - timeInMs) < 20) {
//            timeNowMs = System.currentTimeMillis();
//            // Log.d(TAG, "framesInPerSec: " + framesInPerSec);
//            // timeInMs = timeNowMs;
//            // timeInMs = 0;
//            try {
//                sleep(10);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }

        myFrame = udpRev.getMyFrameList().get(0);
        if (myFrame != null) {

            int frameDataLen = myFrame.getDataLen();
            // Log.d(TAG, "frame type: " + myFrame.getFrameType() + " len: " + frameDataLen);
            decoderInputBuffer = mDecoder.getInputBuffer(inputBufferId);
            // int dataFrameLen = udpRev.frameFinder.getDataFrameLen();
            Log.d(TAG, "data frame len: " + myFrame.getDataLen() + " bid: " + inputBufferId);

            // Log.d(TAG, "new frame + " + myFrame.getDataLen());
            decoderInputBuffer.put(myFrame.getFrameData(), 0, frameDataLen);

            mDecoder.queueInputBuffer(
                    inputBufferId,
                    0,
                    frameDataLen,
                    // presentationTime,
                    System.currentTimeMillis(),
                    // myFrame.getTimeStampUs(),
                    0
            );

            protectedRemove();
            presentationTime = presentationTime + 30000;
        }

    }
     */

    private long lastFrameFreshMs = 0;

    private void countFramePerSec() {
        long timeNow = System.currentTimeMillis();
        if ((timeNow - timeSec) > 1000) {
            Log.d(TAG, "framesOutPerSec: " + framesOutPerSec);
            timeSec = timeNow;
            framesOutPerSec = 0;
        }
        framesOutPerSec++;
    }

    protected long prevOutputPTSUs = 0;

    protected long getPTSUs() {
        long result = System.nanoTime() / 1000L;
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < prevOutputPTSUs)
            result = (prevOutputPTSUs - result) + result;
        return result;
    }

    private void getDataFromQueue(MediaCodec codec, int inputBufferId) {

        byte[] input = null;

        while (udpRev.getH264Queue().size() == 0) {
            try {
                // Log.d(TAG, "Wait for start: ");
                sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
//        try {
//            // Log.d(TAG, "Wait for start: ");
//            sleep(10);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        input = udpRev.getH264Queue().poll();

        if (input != null) {
            // Log.d(TAG, "input len " + input.length + " id: " + inputBufferId);
            ByteBuffer inputBuffer = codec.getInputBuffer(inputBufferId);
            inputBuffer.clear();
            inputBuffer.put(input);
            codec.queueInputBuffer(inputBufferId, 0, input.length, getPTSUs(), 0);
        }
    }

    public void init() {

        mDecoder.setCallback(new MediaCodec.Callback() {

            @Override
            public void onInputBufferAvailable(@NonNull MediaCodec codec, int inputBufferId) {

                // Log.d(TAG, "onInputBufferAvailable inputBufferId: " + inputBufferId);

//                while (udpRev.isIfNotStartDecode()) {
//                    try {
//                        Log.d(TAG, "Wait for start: ");
//                        sleep(100);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }

                getDataFromQueue(codec, inputBufferId);

                // insertDecodeBuffer(inputBufferId);

            }

            @SuppressLint("SwitchIntDef")
            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec codec,
                                                int index, @NonNull MediaCodec.BufferInfo info) {

                // Log.d(TAG, "out idx: " + index);
                switch (info.flags) {
                    case MediaCodec.BUFFER_FLAG_CODEC_CONFIG:
                        Log.d(TAG, "BUFFER_FLAG_CODEC_CONFIG");
                        info.size = 0;
                        break;

                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        info.size = 0;
                        Log.d(TAG, "INFO_TRY_AGAIN_LATER");
                        break;

                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        info.size = 0;
                        Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED");
                        break;
                }

                if (info.size > 0) {
                    framesOutPerSec++;
                    if (framesOutPerSec > 25) {
                        framesOutPerSec = 0;
                        // Log.d(TAG, "28: ");
                    }
//                    try {
//                        Thread.sleep(40);
//                    } catch (InterruptedException e) {
//                    }
                    codec.releaseOutputBuffer(index, true);
                }
                // countFramePerSec();

                // framesOutPerSec++;
//                long timeNow = System.currentTimeMillis();
//                if ((timeNow - timeSec) > 1000) {
//                    // Log.d(TAG, "framesOutPerSec: " + framesOutPerSec);
//                    timeSec = timeNow;
//                    framesOutPerSec = 0;
//                }
//
//                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
//                    Log.d(TAG, "BUFFER_FLAG_CODEC_CONFIG: ");
//                    codec.releaseOutputBuffer(index, false);
//                    return;
//                }
                /*
                    ????????????????????????????????????????????????30ms???????????????????????????????????????????????????????????????????????????????????????????????????
                    ???????????????????????????????????????????????????????????????0????????????????????????5ms???
                    ???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
                    ???????????????????????????????????????????????????????????????sleep??????????????????????????????????????????????????????????????????????????????????????????????????????????????????

                try {
                    Thread.sleep(25);
                } catch (InterruptedException e) {
                }
  */
                // Log.d(TAG, "info: " + info.presentationTimeUs + " timeNowMs:" + timeNowMs);
//                while ((System.currentTimeMillis() - lastFrameFreshMs) < 30) {
//                    // timeNowMs = System.currentTimeMillis();
//                    try {
//                        Thread.sleep(10);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }


                // lastFrameFreshMs = System.currentTimeMillis();
            }

            @Override
            public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
                Log.d(TAG, "onError: ");
                codec.reset();
            }

            @Override
            public void onOutputFormatChanged(MediaCodec mc, MediaFormat format) {
                Log.d(TAG, "onOutputFormatChanged: ");

                mVideoFormat = format;
            }

        });
    }
}
