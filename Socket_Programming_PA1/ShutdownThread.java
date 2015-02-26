
// The ShutdownThread class

public class ShutdownThread extends Thread {

    private ShutdownThreadInterface mShutdownThreadParent;

    public ShutdownThread(ShutdownThreadInterface mShutdownThreadParent) {
        this.mShutdownThreadParent = mShutdownThreadParent;
    }

    @Override
    public void run() {
        this.mShutdownThreadParent.shutdown();
    }
}