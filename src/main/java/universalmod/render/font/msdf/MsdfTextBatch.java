package universalmod.render.font.msdf;

import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

 





public final class MsdfTextBatch {
    
    private static final int MAX_RUNS = 128;
    private static final ThreadLocal<MsdfTextBatch> ACTIVE = new ThreadLocal<>();

    private final List<Run> pending = new ArrayList<>();

    private MsdfTextBatch() {}

    public static void run(Runnable renderTextOnly) {
        if (renderTextOnly == null) return;
        
        if (ACTIVE.get() != null) {
            renderTextOnly.run();
            return;
        }
        MsdfTextBatch batch = new MsdfTextBatch();
        ACTIVE.set(batch);
        try {
            renderTextOnly.run();
        } finally {
            
            ACTIVE.remove();
            batch.flush();
        }
    }

    static boolean enqueue(MsdfFont font, String text, float x, float y, float size,
                           int tl, int tr, int br, int bl, Matrix4f matrix) {
        MsdfTextBatch batch = ACTIVE.get();
        if (batch == null) return false;
        if (batch.pending.size() >= MAX_RUNS) batch.flush();
        
        batch.pending.add(new Run(font, text, x, y, size, tl, tr, br, bl, new Matrix4f(matrix)));
        return true;
    }

     
    public static void flushPending() {
        MsdfTextBatch batch = ACTIVE.get();
        if (batch != null) batch.flush();
    }

    private void flush() {
        int start = 0;
        try {
            while (start < pending.size()) {
                MsdfFont font = pending.get(start).font();
                int end = start + 1;
                while (end < pending.size() && pending.get(end).font() == font) end++;
                font.drawBatch(pending, start, end);
                start = end;
            }
        } finally {
            pending.clear();
        }
    }

    record Run(MsdfFont font, String text, float x, float y, float size,
               int topLeft, int topRight, int bottomRight, int bottomLeft,
               Matrix4f matrix) {}
}
