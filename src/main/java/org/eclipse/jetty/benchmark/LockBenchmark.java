/*
 * Copyright (c) 2014, Oracle America, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of Oracle nor the names of its contributors may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jetty.benchmark;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jetty.toolchain.test.BenchmarkHelper;
import org.eclipse.jetty.util.thread.ExecutionStrategy;
import org.eclipse.jetty.util.thread.Locker;
import org.eclipse.jetty.util.thread.strategy.ExecuteProduceConsume;
import org.eclipse.jetty.util.thread.strategy.ProduceExecuteConsume;
import org.eclipse.jetty.util.thread.strategy.ProduceConsume;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

@State(Scope.Benchmark)
public class LockBenchmark 
{
    
    @Setup(Level.Trial)
    public static void setupTrial() throws Exception
    {
    }
    
    @TearDown(Level.Trial)
    public static void teardownTrial() throws Exception
    {
    }
    

    @Setup(Level.Iteration)
    public static void setupIteration() throws Exception
    {
    }
    
    @TearDown(Level.Iteration)
    public static void teardownIteration() throws Exception
    {
    }
    
    
    @State(Scope.Thread)
    public static class ThreadState
    {
        long count;
        Locker lock = new Locker(false);
        Locker spin = new Locker(true);
    }

    @Benchmark
    @BenchmarkMode({Mode.Throughput})
    public long testSync(ThreadState state) 
    {
        long c=0;
        boolean even;
        synchronized (state)
        {
            c=state.count;
            even=state.count%2==0;
            state.count=++state.count%10000;
        }
        return even?c:-c;
    }
    
    @Benchmark
    @BenchmarkMode({Mode.Throughput})
    public long testLock(ThreadState state) 
    {
        long c=0;
        boolean even;
        try (Locker.Lock l = state.lock.lock())
        {
            c=state.count;
            even=state.count%2==0;
            state.count=++state.count%10000;
        }
        return even?c:-c;
    }

    @Benchmark
    @BenchmarkMode({Mode.Throughput})
    public long testSpinLock(ThreadState state) 
    {
        long c=0;
        boolean even;
        try (Locker.Lock l = state.spin.lock())
        {
            c=state.count;
            even=state.count%2==0;
            state.count=++state.count%10000;
        }
        return even?c:-c;
    }


    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(LockBenchmark.class.getSimpleName())
                .warmupIterations(4)
                .measurementIterations(2)
                .forks(1)
                .threads(1)
                .syncIterations(true)
                .warmupTime(new TimeValue(10,TimeUnit.SECONDS))
                .measurementTime(new TimeValue(10,TimeUnit.SECONDS))
                .build();

        new Runner(opt).run();
    }
}


