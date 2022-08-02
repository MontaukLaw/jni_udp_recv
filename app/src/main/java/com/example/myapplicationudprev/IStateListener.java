package com.example.myapplicationudprev;

public interface IStateListener {

    void decoderPrepare(BaseDecoder baseDecoder);
    void decoderReady(BaseDecoder baseDecoder);
    void decoderRunning(BaseDecoder baseDecoder);
    void decoderPause(BaseDecoder baseDecoder);
    void decoderFinish(BaseDecoder baseDecoder);
    void decoderDestroy(BaseDecoder baseDecoder);
    void decoderError(BaseDecoder baseDecoder, String reason);

}
