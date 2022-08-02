package com.example.myapplicationudprev;

import static java.lang.Thread.sleep;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import java.nio.ByteBuffer;

public abstract class BaseDecoder implements IDecoder {
    private final String TAG = "BaseDecoder";

    //解码器是否正在运行
    private boolean mIsRunning = true;

    //线程等待锁
    private Object mLock = new Object();

    protected MediaFormat mediaFormat;
    //--------------解码相关---------
    //音视频解码器
    protected MediaCodec mCodec = null;
    //解码输入缓冲区
    protected ByteBuffer[] mInputBuffers = null;
    //解码输出缓冲区
    protected ByteBuffer[] mOutputBuffers = null;

    //解码数据信息
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    //解码状态回调函数
    protected IStateListener mStateListener;

    //解码状态
    private volatile DecodeState mState = DecodeState.STOP;
    //解码类型
    private volatile DecodeType mDecodeType = DecodeType.VIDEO;

    //流数据是否结束
    private boolean mIsEOS = false;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;

    //音视频同步---音频和视频都同步到系统时钟
    private long mStartTimeForSync = -1L;
    //音视频同步---视频同步到音频
    private long mVideoClock = -1L;
    private long mAudioClock = -1L;

    protected UDPRev udpRev;

    @Override
    public void run() {
        if (mState == DecodeState.STOP) {
            mState = DecodeState.START;
        }

        if (mStateListener != null) {
            mStateListener.decoderPrepare(this);
        }

        //【解码步骤:1.初始化，并启动解码器】
        if (!init())
            return;

        Log.i(TAG, "开始解码");
        try {
            while (mIsRunning) {
                if (mState != DecodeState.START
                        && mState != DecodeState.DECODING
                        && mState != DecodeState.SEEKING) {
                    Log.i(TAG, "进入等待：" + mState);
                    waitDecode();

                    //----------【根据系统时间 同步时间矫正】------------
                    //恢复同步的起始时间,即去除等待流失的时间
                    mStartTimeForSync = System.currentTimeMillis() - getCurTimeStamp();
                }

                if (!mIsRunning || mState == DecodeState.STOP) {
                    mIsRunning = false;
                    break;
                }

                //------------【根据系统时间 音视频同步】-----------
                if (mStartTimeForSync == -1L) {
                    mStartTimeForSync = System.currentTimeMillis();
                }

                //如果数据没有解码完毕，将数据推入解码器
                //【解码步骤:2.将数据压入解码器缓冲】
                if (!mIsEOS) {
                    //【解码步骤:2.将数据压入解码器缓冲】
                    mIsEOS = pushBufferToDecoder();
                }
                Log.d(TAG, "pushBufferToDecoder pullBufferFromDecoder");

                //【解码步骤:3.将解码好的数据从缓冲区拉取出来】
                int index = pullBufferFromDecoder();

                if (index > 0) {
                    //-------------【视频同步到系统时钟】获取音视频PTS----------
                    if (mState == DecodeState.DECODING) {
                        sleepRender();
                    }

                    //【解码步骤:4.渲染】
                    //-------------【视频同步到音频】获取音视频PTS----------
                    if (mDecodeType == DecodeType.VIDEO) {
                        mVideoClock = getCurTimeStamp();
                    }

                    //【解码步骤:5.释放输出缓冲】
                    mCodec.releaseOutputBuffer(index, true);

                    if (mState == DecodeState.START) {
                        mState = DecodeState.PAUSE;
                    }
                }else{
                    Log.d(TAG, "pullBufferFromDecoder failed");
                }

                //【解码步骤:6.判断解码是否完成】
                if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                    mState = DecodeState.FINISH;
                    if (mStateListener != null)
                        mStateListener.decoderFinish(this);
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            doneDecode();
            //【解码步骤:7.释放解码器】
            release();
        }

    }

    /**
     * 从解码器拉取缓冲
     *
     * @return
     */
    private int pullBufferFromDecoder() {
        // 查询是否有解码完成的数据，index >=0 时，表示数据有效，并且index为缓冲区索引
        int index = mCodec.dequeueOutputBuffer(mBufferInfo, 1000);
        switch (index) {
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED: ");
                break;
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                Log.d(TAG, "INFO_TRY_AGAIN_LATER: ");
                break;
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                mOutputBuffers = mCodec.getOutputBuffers();
                break;
            default:
                Log.d(TAG, "pullBufferFromDecoder: " + index);
                return index;
        }
        return -1;
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
    /**
     * 将数据压入解码器缓冲
     *
     * @return
     */
    private boolean pushBufferToDecoder() {
        int inputBufferIndex = mCodec.dequeueInputBuffer(1000);
        boolean isEndOfStream = false;
        if (inputBufferIndex >= 0) {
            //ByteBuffer inputBuffer = mInputBuffers[inputBufferIndex];
            ByteBuffer inputBuffer =  mCodec.getInputBuffer(inputBufferIndex);
            // todo
            byte[] input = null;

            while (udpRev.getH264Queue().size() == 0) {
                try {
                    sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            input = udpRev.getH264Queue().poll();

            if (input != null) {
                Log.d(TAG, "input len " + input.length + " id: " + inputBufferIndex);
                inputBuffer.clear();
                inputBuffer.put(input);
                mCodec.queueInputBuffer(inputBufferIndex, 0, input.length, getPTSUs(), 0);
            }
//            int sampleSize = mExtractor.readBuffer(inputBuffer);
//            if (sampleSize < 0) {
//                //如果数据已经取完,压入数据结束标志:BUFFER_FLAG_END_OF_STREAM
//                mCodec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
//                isEndOfStream = true;
//            } else {
//                mCodec.queueInputBuffer(inputBufferIndex, 0, sampleSize, mExtractor.getCurrentTimeStamp(), 0);
//            }
        }
        return isEndOfStream;
    }

    private boolean initParams() {
        try {
            // 初始化format
            mediaFormat = new MediaFormat();
            mediaFormat.setString(MediaFormat.KEY_MIME, "video/avc");
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
            // PIXEL_FORMAT_YUV_SEMIPLANAR_420
            // mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface); // COLOR_FormatSurface);
            mediaFormat.setInteger(MediaFormat.KEY_WIDTH, 640);   // 360 * 640
            mediaFormat.setInteger(MediaFormat.KEY_HEIGHT, 360);  //
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        } catch (Exception e) {
            return false;
        }
        return true;
    }

    protected void notifyDecode() {
        synchronized (mLock) {
            mLock.notifyAll();
        }
        if (mState == DecodeState.DECODING) {
            if (mStateListener != null)
                mStateListener.decoderRunning(this);
        }
    }

    /**
     * 初始化渲染器
     */
    protected abstract boolean initRender();

    private boolean init() {

        //3.初始化参数
        if (!initParams()) {
            Log.w(TAG, "initParams() 失败");
            return false;
        }
        //4.初始化渲染器
        if (!initRender()) {
            Log.w(TAG, "initRender() 失败");
            return false;
        }
        //5.初始化解码器
        if (!initCodec()) {
            Log.w(TAG, "initCodec() 失败");
            return false;
        }
        return true;
    }

    protected abstract boolean configCodec(MediaCodec mCodec, MediaFormat format);

    private void waitDecode() {
        try {
            if (mState == DecodeState.PAUSE) {
                if (mStateListener != null)
                    mStateListener.decoderPause(this);
            }
            synchronized (mLock) {
                mLock.wait();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 【根据系统时间同步音视频】
     */
    private void sleepRender() {
        long passTime = System.currentTimeMillis() - mStartTimeForSync;
        long curTimeStamp = getCurTimeStamp();
        if (curTimeStamp > passTime) {
            try {
                Thread.sleep(curTimeStamp - passTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 初始化解码器
     */
    private boolean initCodec() {
        try {
            //1.根据音视频编码格式初始化解码器
            mCodec = MediaCodec.createDecoderByType("video/avc");
            udpRev = new UDPRev(mCodec);

            //2.配置解码器
            if (!configCodec(mCodec, mediaFormat)) {
                waitDecode();
            }
            //3.启动解码器
            mCodec.start();

            //4.获取解码器缓冲区
            mInputBuffers = mCodec.getInputBuffers();
            mOutputBuffers = mCodec.getOutputBuffers();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    private long getCurTimeStamp() {
        return mBufferInfo.presentationTimeUs / 1000;
    }

    /**
     * 释放解码器
     */
    public void release() {
        try {
            mState = DecodeState.STOP;
            mIsEOS = false;
            mCodec.stop();
            mCodec.release();
            mStateListener.decoderDestroy(this);
        } catch (Exception e) {
        }
    }

    /**
     * 结束解码
     */
    protected abstract void doneDecode();

    /**
     * 检查子类参数
     */
    protected abstract boolean check();

    /**
     * 配置子类特有参数
     */
    protected abstract void initSpecParams(MediaFormat format);

    @Override
    public void pause() {
        mState = DecodeState.PAUSE;
    }

    @Override
    public void resume() {
        Log.i(TAG, "resume");
        mState = DecodeState.DECODING;
        notifyDecode();
    }

    @Override
    public void stop() {
        mIsRunning = false;
        mState = DecodeState.STOP;
        notifyDecode();
    }

    @Override
    public boolean isDecoding() {
        return mState == DecodeState.DECODING;
    }

    @Override
    public boolean isSeeking() {
        return mState == DecodeState.SEEKING;
    }

    @Override
    public boolean isStop() {
        return mState == DecodeState.STOP;
    }

    @Override
    public void setStateListener(IStateListener stateListener) {
        mStateListener = stateListener;
    }

}
