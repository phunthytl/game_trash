package helper;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;

/** Đếm ngược đơn giản (tick mỗi 1s), phù hợp với GameView.startMatch() */
public class CountDownTimer {
    private int remaining;              // giây còn lại
    private Timer timer;                // Timer (daemon)
    private Callable<Void> onTick;      // gọi mỗi giây
    private Callable<Void> onFinish;    // gọi khi về 0

    public CountDownTimer(int seconds) {
        this.remaining = Math.max(0, seconds);
    }

    /** Bắt đầu đếm ngược. Nếu đang chạy sẽ restart. */
    public synchronized void start() {
        stop();
        if (remaining <= 0) {           // hết giờ ngay
            fireTickSafe();
            fireFinishSafe();
            return;
        }
        // Daemon để không giữ JVM khi đóng app
        timer = new Timer("countdown", /*isDaemon*/ true);
        // Delay 1s + period 1s để nhịp đúng mỗi giây
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                synchronized (CountDownTimer.this) {
                    remaining = Math.max(0, remaining - 1);
                }
                fireTickSafe();
                if (getRemaining() <= 0) {
                    stop();
                    fireFinishSafe();
                }
            }
        }, 1000L, 1000L);
    }

    /** Dừng lại nếu đang chạy */
    public synchronized void stop() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    /** Đặt lại thời gian (không tự start) */
    public synchronized void reset(int seconds) {
        stop();
        this.remaining = Math.max(0, seconds);
    }

    public synchronized int getRemaining() { return remaining; }

    public synchronized void setOnTick(Callable<Void> onTick)   { this.onTick = onTick; }
    public synchronized void setOnFinish(Callable<Void> onFinish){ this.onFinish = onFinish; }

    // --- helpers: gọi callback an toàn, nuốt exception để không giết Timer thread
    private void fireTickSafe()   { try { if (onTick   != null) onTick.call();   } catch (Exception ignored) {} }
    private void fireFinishSafe() { try { if (onFinish != null) onFinish.call(); } catch (Exception ignored) {} }
}
