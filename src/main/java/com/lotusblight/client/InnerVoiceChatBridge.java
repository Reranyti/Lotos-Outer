package com.lotusblight.client;

/** The inner voice's route into Chat Overhaul - kept as its own entry point, the work is in ChatDialogue. */
public final class InnerVoiceChatBridge {
    /** True while lines go to Chat Overhaul's chat (installed, and the route hasn't failed). */
    public static final boolean LOADED = ChatDialogue.LOADED;

    private InnerVoiceChatBridge() {}

    public static void postLine(String text) {
        ChatDialogue.postLine(ChatDialogue.INNER_VOICE, text);
    }
}
