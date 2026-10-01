package mod.runtime;























public final class NativeScrubberBridge {


    private static volatile boolean completionFlag = false;

    private NativeScrubberBridge() {}


    public static void setDone() {
        completionFlag = true;
    }


    public static boolean isCompletionFlagSet() {
        return completionFlag;
    }
}
