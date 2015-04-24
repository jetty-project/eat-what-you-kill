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

package org.eclipse.jetty.util.thread.strategy;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jetty.util.thread.ExecutionStrategy;

/**
 * <p>Simplified Eat What You Kill strategy.  The fully coded strategy
 * is in {@link ExecuteProduceConsume}.</p>
 */
public class EatWhatYouKill implements ExecutionStrategy, Runnable
{
    private final Producer _producer;
    private final Executor _executor;
    private volatile boolean _threadPending;
    private AtomicBoolean _producing = new AtomicBoolean(false);

    public EatWhatYouKill(Producer producer, Executor executor)
    {
        this._producer = producer;
        this._executor = executor;
    }

    @Override
    public void execute()
    {            
        _threadPending=false;
        while (true)
        {
            // If another thread is already producing, then we are not needed
            if (!_producing.compareAndSet(false,true))
                break;

            Runnable task=null;
            try
            {
                // We are producing
                task=_producer.produce();
                
                // If nothing produced. We are done!
                if (task==null)
                    break;

                // If another producing thread is not scheduled
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

    @Override
    public void dispatch()
    {
        _executor.execute(this);
    }

    @Override
    public void run()
    {
        execute();
    }
}