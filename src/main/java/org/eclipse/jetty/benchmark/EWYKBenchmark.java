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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.toolchain.test.BenchmarkHelper;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.thread.ExecutionStrategy;
import org.eclipse.jetty.util.thread.ReservedThreadExecutor;
import org.eclipse.jetty.util.thread.strategy.EatWhatYouKill;
import org.eclipse.jetty.util.thread.strategy.ProduceExecuteConsume;
import org.eclipse.jetty.util.thread.strategy.ProduceConsume;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.profile.CompilerProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

@State(Scope.Benchmark)
public class EWYKBenchmark 
{
    static TestServer server;
    static ReservedThreadExecutor reserved;
    static File directory;
    static BenchmarkHelper benchmark;

    @Param({"PC","PEC","EWYK"})
    public static String strategyName;
    
    @Param({"true","false"})
    public static boolean sleeping;
       
    @Setup(Level.Trial)
    public static void setupServer() throws Exception
    {
        benchmark = new BenchmarkHelper();
        
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
        reserved = new ReservedThreadExecutor(server,20);
        reserved.start();
    }
    
    @TearDown(Level.Trial)
    public static void stopServer() throws Exception
    {
        reserved.stop();
        server.stop();
    }
    
    @State(Scope.Thread)
    public static class ThreadState
    {
        final TestConnection connection=new TestConnection(server,sleeping);
        final ExecutionStrategy strategy;
        {
            switch(strategyName)
            {
                case "PC":
                    strategy = new ProduceConsume(connection,server);
                    break;
                    
                case "PEC":
                    strategy = new ProduceExecuteConsume(connection,server);
                    break;
                    
                case "EWYK":
                    strategy = new EatWhatYouKill(connection,server,reserved);
                    break;

                default:
                    throw new IllegalStateException();
            }
            
            LifeCycle.start(strategy);
        }
    }

    @Benchmark
    @BenchmarkMode({Mode.Throughput})
    public long testStrategy(ThreadState state) throws Exception
    {
        int r;
        switch(server.getRandom(8))
        {
            case 0:
                r = 4;
                break;
            case 1:
            case 2:
                r = 2;
                break;
            default:
                r = 1;
                break;
        }

        List<CompletableFuture<String>> results = new ArrayList<>(r);
        for (int i=0;i<r;i++)
        {
            CompletableFuture<String> result = new CompletableFuture<String>();
            results.add(result);
            state.connection.submit(result);
        }
                
        state.strategy.produce();
        
        long hash = 0;
        for (CompletableFuture<String> result : results)
            hash ^= result.get().hashCode();
        
        return hash;
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(EWYKBenchmark.class.getSimpleName())
                .warmupIterations(3)
                .measurementIterations(3)
                .forks(1)
                .threads(40)
                // .syncIterations(true) // Don't start all threads at same time
                .warmupTime(new TimeValue(5000,TimeUnit.MILLISECONDS))
                .measurementTime(new TimeValue(5000,TimeUnit.MILLISECONDS))
                // .addProfiler(CompilerProfiler.class)
                .build();
        
        new Runner(opt).run();
    }
}


