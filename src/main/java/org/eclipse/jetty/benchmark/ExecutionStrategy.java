//
//  ========================================================================
//  Copyright (c) 1995-2014 Mort Bay Consulting Pty. Ltd.
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


package org.eclipse.jetty.benchmark;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


/* ------------------------------------------------------------ */
/** Strategies to execute Producers
 */
public abstract class ExecutionStrategy implements Runnable
{    
    public interface Producer
    {
        /**
         * Produce a task to run
         * @return A task to run or null if we are complete.
         */
        Runnable produce();
        
        /**
         * Called to signal production is completed
         */
        void onAllScheduled();
    }

    protected final Producer _producer;
    protected final Executor _executor;

    protected ExecutionStrategy(Producer producer, Executor executor)
    {
        _producer=producer;
        _executor=executor;
    }
    
    /* ------------------------------------------------------------ */
    /** Simple iterative strategy.
     * Iterate over production until complete and execute each task.
     */
    public static class Iterative extends ExecutionStrategy
    {
        public Iterative(Producer producer, Executor executor)
        {
            super(producer,executor);
        }
        
        public void run()
        {
            try
            {
                // Iterate until we are complete
                while (true)
                {
                    // produce a task
                    Runnable task=_producer.produce(); 
                    
                    if (task==null)
                        break;
                    
                    // execute the task
                    _executor.execute(task);
                }
            }
            finally
            {
                _producer.onAllScheduled();
            }
        }
    }
    
    /* ------------------------------------------------------------ */
    /** 
     * A Strategy that allows threads to run the tasks that they have produced,
     * so execution is done with a hot cache (ie threads eat what they kill).
     */
    public static class EatWhatYouKill extends ExecutionStrategy
    {
        private final AtomicBoolean _producing = new AtomicBoolean(Boolean.FALSE);
        private volatile boolean _threadPending;

        public EatWhatYouKill(Producer producer, Executor executor)
        {
            super(producer,executor);
        }
        
        public void run()
        {
            _threadPending=false;
            while (true)
            {
                // If another thread is already producing, 
                if (!_producing.compareAndSet(false,true))
                    // break the loop even if not complete
                    break;

                // If we got here, then we are the thread that is producing
                Runnable task=_producer.produce();
                if (task==null)
                {
                    _producing.set(false);
                    _producer.onAllScheduled();
                    return;
                }
                
                boolean execute=false;
                if (!_threadPending)
                {
                    execute=true;
                    _threadPending=true;
                }

                _producing.set(false);
                
                if (execute)
                    _executor.execute(this);

                task.run();
            }
        }
    }
    
    /* ------------------------------------------------------------ */
    /** 
     * A Strategy that allows threads to run the tasks that they have produced,
     * so execution is done with a hot cache (ie threads eat what they kill).
     */
    public static class EatWhatYouKillSM extends ExecutionStrategy
    {
        private enum State {IDLE,PRODUCING,PENDING,REPRODUCING};
        private final AtomicReference<State> _state = new AtomicReference<>(State.IDLE);

        public EatWhatYouKillSM(Producer producer, Executor executor)
        {
            super(producer,executor);
        }
        
        public void run()
        {      
            // New Thread arriving to the strategy.
            loop:while(true)
            {
                State state=_state.get();
                switch(state)
                {
                    case IDLE:
                    case PENDING:
                        if (!_state.compareAndSet(state,State.PRODUCING))
                            continue;
                        break loop;
                        
                    case REPRODUCING:
                        if (!_state.compareAndSet(state,State.PRODUCING))
                            continue;
                        return;  // Another thread is already producing
                        
                    case PRODUCING:
                        return; // Another thread is already producing
                }
            }
            
            while (true)
            {                
                // If we got here, then we are the thread that is producing
                Runnable task=_producer.produce();

                if (task==null)
                {
                    loop:while(true)
                    {
                        State state=_state.get();
                        switch(state)
                        {
                            case PRODUCING:
                                if (!_state.compareAndSet(state,State.IDLE))
                                    continue;
                                break loop;
                            case REPRODUCING:
                                if (!_state.compareAndSet(state,State.IDLE))
                                    continue;
                                _state.set(State.PENDING);
                                break loop;
                            default:
                                throw new IllegalStateException();
                        }
                    }
                    _producer.onAllScheduled();
                    return;
                }

                loop:while(true)
                {
                    State state=_state.get();
                    switch(state)
                    {
                        case PRODUCING:
                            if (!_state.compareAndSet(state,State.PENDING))
                                continue;
                            _executor.execute(this);
                            break loop;
                        case REPRODUCING:
                            if (!_state.compareAndSet(state,State.PENDING))
                                continue;
                            break loop;
                        default:
                            throw new IllegalStateException();
                    }
                }
                
                task.run();

                loop:while(true)
                {
                    State state=_state.get();
                    switch(state)
                    {
                        case PENDING:
                            if (!_state.compareAndSet(state,State.REPRODUCING))
                                continue;
                            break loop;

                        default:
                            return; 
                    }
                }
            }
        }
    }
}
