package com.brunorozendo.mcphost.util;

import java.io.PrintWriter;

/**
 * A simple runnable class to display a loading animation in the console.
 * This provides visual feedback to the user during long-running operations.
 */
public class LoadingAnimator implements Runnable {
    // `volatile` ensures that changes to `running` are visible across threads.
    private volatile boolean running;
    private Thread thread;
    private final char[] animationChars = new char[]{'|', '/', '-', '\\'};
    private final PrintWriter writer;
    private String currentMessage = "Thinking...";

    public LoadingAnimator(PrintWriter writer) {
        this.writer = writer;
    }

    /**
     * Starts the animation with a given message. If already running, it just updates the message.
     * This method is synchronized to prevent race conditions when starting/stopping the animator.
     *
     * @param message The message to display next to the spinner.
     */
    public synchronized void start(String message) {
        if (running) {
            this.currentMessage = message;
            return;
        }
        this.currentMessage = message;
        this.running = true;

        // Create and start a new thread for the animation loop.
        this.thread = new Thread(this);
        this.thread.setName("LoadingAnimatorThread-" + System.currentTimeMillis());
        this.thread.setDaemon(true); // Allows the JVM to exit even if this thread is running.
        this.thread.start();
    }

    /**
     * Stops the animation and clears the line.
     * This method is synchronized to coordinate with the `start` method.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        this.running = false;
        if (thread != null && thread.isAlive()) {
            try {
                thread.interrupt(); // Interrupt the sleep in the run loop.
                thread.join(200);   // Wait briefly for the thread to finish.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // Clear the animation from the console line.
        try {
            if (writer != null) {
                // Overwrite the line with spaces and return the cursor to the beginning.
                writer.print("\r" + " ".repeat(currentMessage.length() + 20) + "\r");
                writer.flush();
            }
        } catch (Exception e) {
            // Log or handle writer exceptions if necessary, but don't let stop() fail.
        }
        thread = null; // Allow the old thread to be garbage collected.
    }

    @Override
    public void run() {
        int i = 0;
        Thread currentThreadInstance = Thread.currentThread();
        while (running && !currentThreadInstance.isInterrupted()) {
            try {
                if (writer != null) {
                    // Print the message and the next character in the animation sequence.
                    writer.print("\r" + currentMessage + " " + animationChars[i % animationChars.length] + " ");
                    writer.flush();
                }
                i++;
                Thread.sleep(150); // Pause for a short duration.
            } catch (InterruptedException e) {
                // This is the expected way to exit the loop when stop() is called.
                break;
            } catch (Exception e) {
                // Catch any other exception to prevent the animator thread from dying unexpectedly.
                break;
            }
        }
    }
}
