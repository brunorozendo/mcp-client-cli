package com.brunorozendo.mcphost;

import java.io.PrintWriter;

public class LoadingAnimator implements Runnable {
    private volatile boolean running; // Make running volatile
    private Thread thread; // Don't make it final
    private final char[] animationChars = new char[]{'|', '/', '-', '\\'};
    private final PrintWriter writer;
    private String currentMessage = "Thinking...";

    public LoadingAnimator(PrintWriter writer) {
        this.writer = writer;
        // Don't create the thread here initially
    }

    public synchronized void start(String message) { // Synchronize start/stop
        if (running) { // If already running, just update message
            this.currentMessage = message;
            return;
        }
        this.currentMessage = message;
        this.running = true;

        // Create and start a new thread instance
        this.thread = new Thread(this);
        this.thread.setName("LoadingAnimatorThread-" + System.currentTimeMillis()); // Unique name
        this.thread.setDaemon(true);
        this.thread.start();
    }

    public synchronized void stop() { // Synchronize start/stop
        if (!running) {
            return;
        }
        this.running = false;
        if (thread != null && thread.isAlive()) {
            try {
                thread.interrupt(); // Interrupt the sleep
                thread.join(200);   // Wait a bit longer for it to clean up
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // Clear the animation line
        // Ensure writer is not null and no IOExceptions are thrown unhandled from here
        try {
            if (writer != null) {
                writer.print("\r" + " ".repeat(currentMessage.length() + 20) + "\r"); // Erase a bit more
                writer.flush();
            }
        } catch (Exception e) {
            // Log or handle writer exceptions if necessary, but don't crash stop()
        }
        thread = null; // Allow the old thread to be GC'd
    }

    @Override
    public void run() {
        int i = 0;
        // Capture currentThread as it might be nulled out by stop()
        Thread currentThreadInstance = Thread.currentThread();
        while (running && !currentThreadInstance.isInterrupted()) {
            try {
                if (writer != null) {
                    writer.print("\r" + currentMessage + " " + animationChars[i % animationChars.length] + " ");
                    writer.flush();
                }
                i++;
                Thread.sleep(150);
            } catch (InterruptedException e) {
                // If interrupted and running is false, it's a clean stop.
                // If interrupted and running is true, it's an external interrupt, so break.
                break;
            } catch (Exception e) {
                // Catch any other exception during write/flush to prevent thread death
                // Log this ideally
                break;
            }
        }
        // Final clear, only if this thread was responsible for the last animation
        // And ensure writer is still valid
        try {
            if (writer != null) {
                // Check if this thread is the 'active' animator thread before clearing.
                // This is a bit tricky if stop() creates a new thread quickly.
                // The synchronized methods help, but a simple clear is usually fine.
                writer.print("\r" + " ".repeat(currentMessage.length() + 20) + "\r");
                writer.flush();
            }
        } catch (Exception e) {
            // Log or handle
        }
    }
}