package com.example.myapplicationudprev;

import static java.lang.Thread.sleep;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.myapplicationudprev.databinding.ActivityMainBinding;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {
// public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback2 {

    private ActivityMainBinding binding;
    private final static String TAG = MainActivity.class.getSimpleName();
    SurfaceView sfv;

    TextView textView;
    TextureView vedioView;
    DataLenRefresher dataLenRefresher;
    UDPRev udpRev;

    private MediaCodec mediaCodec;

    private MediaFormat mediaFormat;
    private String mMimeType;
    private MyDecoder myDecoder;
    private SurfaceTexture surfaceTexture;

    private VideoDecoder mVideoDecoder;


    protected void onCreateShit(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // sfv = binding.sfv;
        // sfv.getHolder().addCallback(this);

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // textView = binding.myTextView;

        vedioView = binding.videoView;
        vedioView.setSurfaceTextureListener(this);
        try {
            mediaCodec = MediaCodec.createDecoderByType("video/avc");
        } catch (IOException e) {
            e.printStackTrace();
        }
        // initDecoderFormat();
        initDecoderFormat();
        udpRev = new UDPRev(mediaCodec);

        myDecoder = new MyDecoder(mediaCodec, udpRev);
        myDecoder.init();

        // dataLenRefresher = new DataLenRefresher(udpRev, textView, this);
    }

    private void initPlayer(Surface surface) {

        ExecutorService executorService = Executors.newFixedThreadPool(10);

        // 创建视频解码器
        mVideoDecoder = new VideoDecoder(sfv, surface);
        executorService.execute(mVideoDecoder);

        // 播放
        mVideoDecoder.resume();
    }

    // @Override
    public void surfaceCreated(SurfaceHolder holder) {
        initPlayer(holder.getSurface());
    }

    // @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

    }

    private void initDecoderFormat() {

        mediaFormat = new MediaFormat();
        mediaFormat.setString(MediaFormat.KEY_MIME, "video/avc");
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
        // mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);

        // PIXEL_FORMAT_YUV_SEMIPLANAR_420
        // mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface); // COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_WIDTH, 640);   // 360 * 640
        mediaFormat.setInteger(MediaFormat.KEY_HEIGHT, 360);  //
        // stVpssExtChnAttr.u32Height = 360;
        // stVpssExtChnAttr.u32Width = 640;
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
    }

    // @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {

        surfaceTexture = surface;

        while (!udpRev.isIfNotStartDecode()) {
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        mediaCodec.configure(mediaFormat, new Surface(surfaceTexture), null, 0);

        Log.d(TAG, "mediaCodec.start(): ");

        // 暂时注释掉
        // mediaCodec.start();

    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
        }
    }

    // @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopPlayer();
    }

    private void stopPlayer() {
        mVideoDecoder.release();
    }

    // @Override
    public void surfaceRedrawNeeded(@NonNull SurfaceHolder holder) {
    }
}

class DataLenRefresher {

    int counter = 0;

    public DataLenRefresher(UDPRev udpRev, TextView textView, Activity context) {

        new Thread(new Runnable() {
            @Override
            public void run() {

                while (true) {
                    counter++;
                    context.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textView.setText(udpRev.getSpecialPacket());
                        }
                    });
                    udpRev.getRevDataLen();
                    try {
                        sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }
}