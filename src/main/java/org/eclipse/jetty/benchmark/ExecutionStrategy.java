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
        private final AtomicReference<Boolean> _producing = new AtomicReference<Boolean>(Boolean.FALSE);
        private volatile boolean _threadPending;
        private volatile int _max;

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
                Runnable task=null;
                try
                {
                    task=_producer.produce();

                    if (task==null)
                    {
                        _producer.onAllScheduled();
                        break;
                    }    

                    // since we are going to "eat"==run the task we 
                    // just "killed"==produced, 
                    // then we may need another thread to keep producing
                    if (!_threadPending)
                    {
                        // Dispatch a thread to continue producing
                        _threadPending=true;
                        _executor.execute(this);
                    }
                }
                finally
                {
                    _producing.set(false);
                }

                // run the task
                task.run();
            }
        }
    }

}
