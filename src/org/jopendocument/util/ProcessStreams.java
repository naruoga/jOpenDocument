/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008-2013 jOpenDocument, by ILM Informatique. All rights reserved.
 * 
 * The contents of this file are subject to the terms of the GNU
 * General Public License Version 3 only ("GPL").  
 * You may not use this file except in compliance with the License. 
 * You can obtain a copy of the License at http://www.gnu.org/licenses/gpl-3.0.html
 * See the License for the specific language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each file.
 * 
 */

package org.jopendocument.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.ProcessBuilder.Redirect;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Redirect streams of a process to System.out and System.err.
 * 
 * @author Sylvain
 */
public class ProcessStreams implements AutoCloseable {

    // Don't set too low, this is a maximum, it would only fail on slow computers.
    private static final int MAX_SHUTTING_DOWN_DELAY = 500;

    // Added to Java 9
    public static final Redirect DISCARD = Redirect.to(StreamUtils.NULL_FILE);

    static public final ProcessBuilder redirect(final ProcessBuilder pb) {
        // ATTN don't use redirectErrorStream(true) as this would merge the error and output of pb
        // into the VM output.
        return pb.redirectError(Redirect.INHERIT).redirectOutput(Redirect.INHERIT);
    }

    /**
     * Consume all streams. Needed since some programs might fail (e.g. route on FreeBSD) if the
     * streams are just closed.
     * 
     * @param proc the process.
     * @return the passed process.
     */
    static public final Process consume(final Process proc) {
        try (final ProcessStreams streams = new ProcessStreams(proc)) {
            streams.start(StreamUtils.NULL_OS, StreamUtils.NULL_OS);
            streams.awaitTermination();
        } catch (Exception e) {
            throw new IllegalStateException("Couldn't consume output of " + proc, e);
        }
        return proc;
    }

    private final ExecutorService exec = Executors.newFixedThreadPool(2);
    private final Process process;
    private Future<?> out;
    private Future<?> err;

    /**
     * Create a new instance and start reading from the passed process. If a passed
     * {@link OutputStream} is <code>null</code>, then the corresponding {@link InputStream} is not
     * used at all, so the caller should handle it. If the output must be discarded, use
     * {@link StreamUtils#NULL_OS}.
     * 
     * @param p the process to read from.
     * @param out where to write the {@link Process#getInputStream() standard output}.
     * @param err where to write the {@link Process#getErrorStream() standard error}.
     */
    public ProcessStreams(final Process p) {
        this.process = p;
    }

    public final ProcessStreams start(final OutputStream out, final OutputStream err) {
        if (this.out != null)
            throw new IllegalStateException("Already started");
        this.out = writeToAsync(this.process::getInputStream, out);
        this.err = writeToAsync(this.process::getErrorStream, err);
        assert this.out != null && this.err != null;
        this.exec.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    // OK even if the future is cancelled before having started
                    try {
                        ProcessStreams.this.out.get();
                    } catch (Exception e) {
                    }
                    try {
                        ProcessStreams.this.err.get();
                    } catch (Exception e) {
                    }
                } finally {
                    ProcessStreams.this.exec.shutdown();
                }
            }
        });
        return this;
    }

    protected final void stopOut() {
        this.stop(this.out);
    }

    protected final void stopErr() {
        this.stop(this.err);
    }

    private final void stop(final Future<?> f) {
        if (f == null)
            throw new IllegalStateException("Not started");
        // ATTN Process returns InputStream which are not interruptible.
        f.cancel(true);
    }

    // From AutoCloseable : close() shouldn't throw InterruptedException
    @Override
    public void close() {
        this.exec.shutdownNow();
    }

    public final void awaitTermination() throws InterruptedException, ExecutionException {
        this.out.get();
        this.err.get();
        if (!this.exec.awaitTermination(MAX_SHUTTING_DOWN_DELAY, TimeUnit.MILLISECONDS))
            throw new IllegalStateException("Executor still not terminated after " + MAX_SHUTTING_DOWN_DELAY);
    }

    public final void awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        try {
            this.out.get(timeout, unit);
        } catch (CancellationException e) {
            // OK
        }
        try {
            this.err.get(timeout, unit);
        } catch (CancellationException e) {
            // OK
        }
        if (!this.exec.awaitTermination(MAX_SHUTTING_DOWN_DELAY, TimeUnit.MILLISECONDS))
            throw new TimeoutException("Executor still not terminated after " + MAX_SHUTTING_DOWN_DELAY);
    }

    private final Future<?> writeToAsync(final Supplier<InputStream> insSupplier, final Object outs) {
        if (outs == null) {
            return CompletableFuture.completedFuture(null);
        }
        return this.exec.submit(new Callable<Object>() {
            @Override
            public Void call() throws InterruptedException, IOException {
                try (final InputStream ins = insSupplier.get()) {
                    // PrintStream is also an OutputStream
                    if (outs instanceof PrintStream)
                        writeTo(ins, (PrintStream) outs);
                    else
                        StreamUtils.copy(ins, (OutputStream) outs);
                    return null;
                }
            }
        });
    }

    /**
     * Copy ins to outs, line by line.
     * 
     * @param ins the source.
     * @param outs the destination.
     * @throws InterruptedException if current thread is interrupted.
     * @throws IOException if I/O error.
     */
    public static final void writeTo(final InputStream ins, final PrintStream outs) throws InterruptedException, IOException {
        final BufferedReader r = new BufferedReader(new InputStreamReader(ins));
        String encodedName;
        while ((encodedName = r.readLine()) != null) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            outs.println(encodedName);
        }
    }

}
