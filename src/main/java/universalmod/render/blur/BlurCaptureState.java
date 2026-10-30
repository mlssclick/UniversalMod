package universalmod.render.blur;

 







final class BlurCaptureState {
    private long epoch;
    private long capturedEpoch = -1L;

    void beginFrame() {
        invalidate();
    }

    void invalidate() {
        epoch++;
    }

    boolean needsCapture() {
        return capturedEpoch != epoch;
    }

    void captured() {
        capturedEpoch = epoch;
    }

    long epoch() {
        return epoch;
    }

    void reset() {
        invalidate();
        capturedEpoch = -1L;
    }
}
