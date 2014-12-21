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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.profile.LinuxPerfAsmProfiler;
import org.openjdk.jmh.profile.Profiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

@State(Scope.Benchmark)
public class EWYKBenchmark 
{
    static volatile TestServer server;
    static volatile File directory;
    
    @Setup(Level.Trial)
    public static void setupServer() throws Exception
    {
        // Make a test directory
        directory = File.createTempFile("ewyk","dir");
        if (directory.exists())
            directory.delete();
        directory.mkdir();
        directory.deleteOnExit();
        
        // Make some test files
        for (int i=0;i<75;i++)
        {
            File file =new File(directory,i+".txt");
            file.createNewFile();
            file.deleteOnExit();
        }
        
        server=new TestServer(directory);
        server.start();
    }

    @TearDown(Level.Trial)
    public static void stopServer() throws Exception
    {
        server.stop();
    }
    
    @State(Scope.Thread)
    public static class ThreadState
    {
        volatile TestConnection connection=new TestConnection(server);
    }

    @Benchmark
    @BenchmarkMode({Mode.Throughput})
    public long testPR(ThreadState state) 
    {
        state.connection.schedule();
        ExecutionStrategy strategy = new ExecutionStrategy.ProduceRun(state.connection);
        strategy.execute();
        return state.connection.getResult();
    }

    @Benchmark
    @BenchmarkMode({Mode.Throughput})
    public long testPER(ThreadState state) 
    {
        state.connection.schedule();
        ExecutionStrategy strategy = new ExecutionStrategy.ProduceExecuteRun(state.connection,server);
        strategy.execute();
        return state.connection.getResult();
    }

    @Benchmark
    @BenchmarkMode({Mode.Throughput})
    public long testEPR(ThreadState state) 
    {
        state.connection.schedule();
        ExecutionStrategy strategy = new ExecutionStrategy.ExecuteProduceRun(state.connection,server);
        strategy.execute();
        return state.connection.getResult();
    }

    
    public static void main(String[] args) throws RunnerException 
    {
        Options opt = new OptionsBuilder()
                .include(EWYKBenchmark.class.getSimpleName())
                .warmupIterations(4)
                .measurementIterations(8)
                .forks(0)
                .threads(2000)
                .syncIterations(true)
                .warmupTime(new TimeValue(8,TimeUnit.SECONDS))
                .measurementTime(new TimeValue(8,TimeUnit.SECONDS))
                .addProfiler(LinuxPerfAsmProfiler.class)
                .build();

        
        new Runner(opt).run();
        
    }
}


