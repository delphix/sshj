/*
 * Copyright (C)2009 - SSHJ Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.schmizz.sshj.common;

public class CircularBuffer<T extends CircularBuffer<T>> {

    public static class CircularBufferException
            extends SSHException {

        public CircularBufferException(String message) {
            super(message);
        }
    }

    public static final class PlainCircularBuffer
            extends CircularBuffer<PlainCircularBuffer> {

        public PlainCircularBuffer(int size, int maxSize) {
            super(size, maxSize);
        }
    }

    private int getNextSize(int i) {
        // Use next power of 2.
        int j = 1;
        while (j < i) {
            j <<= 1;
            if (j <= 0) throw new IllegalArgumentException("Cannot get next power of 2; "+i+" is too large");
        }
        return Math.min(j, maxSize); // limit to max size
    }

    // Maximum size of the internal array (one plus the maximum capacity of the buffer).
    private final int maxSize;
    // Internal array for the data. All bytes minus one can be used to avoid empty vs full ambiguity when rpos == wpos.
    private byte[] data;
    // Next read position. Wraps around the end of the internal array.  When it reaches wpos, the buffer becomes empty.
    private int rpos;
    // Next write position, wraps around. Equals rpos if buffer is empty (i.e. wpos cannot reach rpos from the left).
    private int wpos;

    public CircularBuffer(int size, int maxSize) {
        this.maxSize = maxSize;
        if (size > maxSize) {
            throw new IllegalArgumentException(
                String.format("Initial requested size %d larger than maximum size %d", size, maxSize));
        }
        int initialSize = getNextSize(size);
        this.data = new byte[initialSize];
        this.rpos = 0;
        this.wpos = 0;
    }

    public int available() {
        int available = wpos - rpos;
        return available >= 0 ? available : available + data.length; // adjust if wpos is left of rpos
    }

    // Max remaining capacity is always one less than the max possible remaining space.
    public int maxPossibleRemainingCapacity() {
        // Get remaining in current buffer.
        int remaining = rpos - wpos - 1;
        if (remaining < 0) {
            remaining += data.length; // adjust if rpos is left of wpos
        }
        // Add the maximum amount it can grow.
        return remaining + maxSize - data.length;
    }

    private void ensureAvailable(int a)
            throws CircularBufferException {
        if (available() < a) {
            throw new CircularBufferException("Underflow");
        }
    }

    void ensureCapacity(int capacity) throws CircularBufferException {
        int available = available();
        int remaining = data.length - available;
        // If capacity fits exactly in remaining space, expand it; otherwise, wpos would reach rpos from the left.
        if (remaining <= capacity) {
            int neededSize = available + capacity + 1;
            int nextSize = getNextSize(neededSize);
            if (nextSize < neededSize) {
                throw new CircularBufferException("Attempted overflow");
            }
            byte[] tmp = new byte[nextSize];
            // Copy the one or two segments between rpos and wpos and start again from 0.
            if (wpos >= rpos) {
                System.arraycopy(data, rpos, tmp, 0, available);
                wpos -= rpos; // move wpos left so that rpos starts from 0
            } else {
                int tail = data.length - rpos;
                System.arraycopy(data, rpos, tmp, 0, tail); // segment right of rpos
                System.arraycopy(data, 0, tmp, tail, wpos); // segment left of wpos
                wpos += tail; // move wpos right to let rpos wrap around to 0
            }
            rpos = 0;
            data = tmp;
        }
    }

    public void readRawBytes(byte[] buf, int off, int len)
            throws CircularBufferException {
        ensureAvailable(len);

        int rposNext = rpos + len;
        if (rposNext <= data.length) {
            System.arraycopy(data, rpos, buf, off, len);
        } else {
            int tail = data.length - rpos;
            System.arraycopy(data, rpos, buf, off, tail); // segment right of rpos
            rposNext = len - tail; // rpos wraps around the end of the buffer
            System.arraycopy(data, 0, buf, off + tail, rposNext); // remainder
        }
        rpos = rposNext;
    }

    @SuppressWarnings("unchecked")
    public T putRawBytes(byte[] d, int off, int len) throws CircularBufferException {
        ensureCapacity(len);

        int wposNext = wpos + len;
        if (wposNext <= data.length) {
            System.arraycopy(d, off, data, wpos, len);
        } else {
            int tail = data.length - wpos;
            System.arraycopy(d, off, data, wpos, tail); // segment right of wpos
            wposNext = len - tail; // wpos wraps around the end of the buffer
            System.arraycopy(d, off + tail, data, 0, wposNext); // remainder
        }
        wpos = wposNext;

        return (T) this;
    }

    // Used only for testing.
    int length() {
        return data.length;
    }

    @Override
    public String toString() {
        return "CircularBuffer [rpos=" + rpos + ", wpos=" + wpos + ", size=" + data.length + "]";
    }

}
