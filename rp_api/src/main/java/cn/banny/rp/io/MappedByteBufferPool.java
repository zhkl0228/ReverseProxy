//
//  ========================================================================
//  Copyright (c) 1995-2015 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package cn.banny.rp.io;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MappedByteBufferPool implements ByteBufferPool
{
    private final ConcurrentMap<Integer, Queue<ByteBuffer>> directBuffers = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, Queue<ByteBuffer>> heapBuffers = new ConcurrentHashMap<>();
    private final int factor;

    public MappedByteBufferPool()
    {
        this(1024);
    }

    public MappedByteBufferPool(int factor)
    {
        this.factor = factor;
    }

    @Override
    public ByteBuffer acquire(int size, boolean direct)
    {
        int bucket = bucketFor(size);
        ConcurrentMap<Integer, Queue<ByteBuffer>> buffers = buffersFor(direct);

        ByteBuffer result = null;
        Queue<ByteBuffer> byteBuffers = buffers.get(bucket);
        if (byteBuffers != null)
            result = byteBuffers.poll();

        if (result == null)
        {
            int capacity = bucket * factor;
            result = newByteBuffer(capacity, direct);
        }

        clear(result);
        return result;
    }


    /* ------------------------------------------------------------ */
    /** Clear the buffer to be empty in flush mode.
     * The position and limit are set to 0;
     * @param buffer The buffer to clear.
     */
    private static void clear(ByteBuffer buffer)
    {
        if (buffer != null)
        {
            buffer.position(0);
            buffer.limit(0);
        }
    }

    private long totalMemory;

    protected ByteBuffer newByteBuffer(int capacity, boolean direct)
    {
        ByteBuffer buffer = direct ? allocateDirect(capacity)
                      : allocate(capacity);
        totalMemory += buffer.capacity();
        return buffer;
    }

    @Override
    public long totalMemory() {
        return totalMemory;
    }
/* ------------------------------------------------------------ */
    /** Allocate ByteBuffer in flush mode.
     * The position and limit will both be zero, indicating that the buffer is
     * empty and must be flipped before any data is put to it.
     * @param capacity capacity of the allocated ByteBuffer
     * @return Buffer
     */
    private static ByteBuffer allocate(int capacity)
    {
        ByteBuffer buf = ByteBuffer.allocate(capacity);
        buf.limit(0);
        return buf;
    }

    /* ------------------------------------------------------------ */
    /** Allocate ByteBuffer in flush mode.
     * The position and limit will both be zero, indicating that the buffer is
     * empty and in flush mode.
     * @param capacity capacity of the allocated ByteBuffer
     * @return Buffer
     */
    private static ByteBuffer allocateDirect(int capacity)
    {
        ByteBuffer buf = ByteBuffer.allocateDirect(capacity);
        buf.limit(0);
        return buf;
    }

    @Override
    public void release(ByteBuffer buffer)
    {
        if (buffer == null)
            return; // nothing to do
        
        // validate that this buffer is from this pool
        assert((buffer.capacity() % factor) == 0);
        
        int bucket = bucketFor(buffer.capacity());
        ConcurrentMap<Integer, Queue<ByteBuffer>> buffers = buffersFor(buffer.isDirect());

        // Avoid to create a new queue every time, just to be discarded immediately
        Queue<ByteBuffer> byteBuffers = buffers.get(bucket);
        if (byteBuffers == null)
        {
            byteBuffers = new ConcurrentLinkedQueue<>();
            Queue<ByteBuffer> existing = buffers.putIfAbsent(bucket, byteBuffers);
            if (existing != null)
                byteBuffers = existing;
        }

        clear(buffer);
        byteBuffers.offer(buffer);
    }

    public void clear()
    {
        directBuffers.clear();
        heapBuffers.clear();
    }

    private int bucketFor(int size)
    {
        int bucket = size / factor;
        if (size % factor > 0)
            ++bucket;
        return bucket;
    }

    // Package local for testing
    ConcurrentMap<Integer, Queue<ByteBuffer>> buffersFor(boolean direct)
    {
        return direct ? directBuffers : heapBuffers;
    }

    public static class Tagged extends MappedByteBufferPool
    {
        private final AtomicInteger tag = new AtomicInteger();

        @Override
        protected ByteBuffer newByteBuffer(int capacity, boolean direct)
        {
            ByteBuffer buffer = super.newByteBuffer(capacity + 4, direct);
            buffer.limit(buffer.capacity());
            buffer.putInt(tag.incrementAndGet());
            ByteBuffer slice = buffer.slice();
            MappedByteBufferPool.clear(slice);
            return slice;
        }
    }
}
