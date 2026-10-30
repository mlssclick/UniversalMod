package universalmod.service.media.optmedia;

import com.sun.jna.win32.StdCallLibrary;

interface OptMediaNativeLibrary extends StdCallLibrary {
    boolean GetCurrentMediaInfo(OptMediaInfo info);
    void FreeMediaInfo(OptMediaInfo info);
    boolean Play();
    boolean Pause();
    boolean TogglePlayPause();
    boolean SkipNext();
    boolean SkipPrevious();
    void SeekToMs(long positionMillis);
    float GetVolume();
    boolean SetVolume(float volume);
    void Deinitialize();
}
