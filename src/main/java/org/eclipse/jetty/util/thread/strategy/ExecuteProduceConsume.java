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
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.ExecutionStrategy;
import org.eclipse.jetty.util.thread.SpinLock;
import org.eclipse.jetty.util.thread.SpinLock.Lock;

/**
 * <p>A strategy where the thread calls produce will always run the resulting task
 * itself.  The strategy may dispatches another thread to continue production.
 * </p>
 * <p>The strategy is also known by the nickname 'eat what you kill', which comes from 
 * the hunting ethic that says a person should not kill anything he or she does not 
 * plan on eating. In this case, the phrase is used to mean that a thread should 
 * not produce a task that it does not intend to run. By making producers run the 
 * task that they have just produced avoids execution delays and avoids parallel slow 
 * down by running the task in the same core, with good chances of having a hot CPU 
 * cache. It also avoids the creation of a queue of produced tasks that the system 
 * does not yet have capacity to consume, which can save memory and exert back 
 * pressure on producers.
 * </p>
 */
public class ExecuteProduceConsume implements ExecutionStrategy, Runnable
{
    private static final Logger LOG = Log.getLogger(ExecuteProduceConsume.class);
    private final SpinLock _lock = new SpinLock();
    private final Runnable _runExecute = new RunExecute();
    private final Producer _producer;
    private final Executor _executor;
    private boolean _idle=true;
    private boolean _execute;
    private boolean _producing;
    private boolean _pending;

    public ExecuteProduceConsume(Producer producer, Executor executor)
    {
        this._producer = producer;
        this._executor = executor;
    }

    @Override
    public void execute()
    {
        if (LOG.isDebugEnabled())
            LOG.debug("{} execute",this);
        boolean produce=false;
        try (Lock locked = _lock.lock())
        {
            // If we are idle and a thread is not producing
            if (_idle)
            {
                if (_producing)
                    throw new IllegalStateException();
                
                // Then this thread will do the producing
                produce=_producing=true;
                // and we are no longer idle
                _idle=false;
            }
            else
            {
                // Otherwise, lets tell the producing thread
                // that it should call produce again before going idle
                _execute=true;
            }
        }

        if (produce)
            produceAndRun();
    }

    @Override
    public void dispatch()
    {
        if (LOG.isDebugEnabled())
            LOG.debug("{} spawning",this);
        boolean dispatch=false;
        try (Lock locked = _lock.lock())
        {
            if (_idle)
                dispatch=true;
            else
                _execute=true;
        }
        if (dispatch)
            _executor.execute(_runExecute);
    }

    @Override
    public void run()
    {
        if (LOG.isDebugEnabled())
            LOG.debug("{} run",this);
        boolean produce=false;
        try (Lock locked = _lock.lock())
        {
            _pending=false;
            if (!_idle && !_producing)
            {
                produce=_producing=true;
            }
        }

        if (produce)
            produceAndRun();
    }
    
    private void produceAndRun()
    {
        if (LOG.isDebugEnabled())
            LOG.debug("{} produce enter",this);
        
        while (true)
        {
            // If we got here, then we are the thread that is producing.
            if (LOG.isDebugEnabled())
                LOG.debug("{} producing",this);
            
            Runnable task = _producer.produce();
            
            if (LOG.isDebugEnabled())
                LOG.debug("{} produced {}",this,task);
            
            boolean dispatch=false;
            try (Lock locked = _lock.lock())
            {
                // Finished producing
                _producing=false;
                
                // Did we produced a task?
                if (task == null)
                {
                    // There is no task.  
                    if (_execute)
                    {
                        _idle=false;
                        _producing=true;
                        _execute=false;
                        continue;
                    }

                    // ... and no additional calls to execute, so we are idle
                    _idle=true;
                    break;
                }
                
                // We have a task, which we will run ourselves,
                // so if we don't have another thread pending
                if (!_pending)
                {
                    // dispatch one
                    dispatch=_pending=true;
                }
                
                _execute=false;
            }
            
            // If we became pending
            if (dispatch)
            {
                // Spawn a new thread to continue production by running the produce loop.
                if (LOG.isDebugEnabled())
                    LOG.debug("{} dispatch",this);
                _executor.execute(this);
            }

            // Run the task.
            if (LOG.isDebugEnabled())
                LOG.debug("{} run {}",this,task);
            task.run();
            if (LOG.isDebugEnabled())
                LOG.debug("{} ran {}",this,task);
            
            // Once we have run the task, we can try producing again.
            try (Lock locked = _lock.lock())
            {
                // Is another thread already producing or we are now idle?
                if (_producing || _idle)
                    break;
                _producing=true;
            }
        }
    
        if (LOG.isDebugEnabled())
            LOG.debug("{} produce exit",this);
    }

    public Boolean isIdle()
    {
        try (Lock locked = _lock.lock())
        {
            return _idle;
        }
    }
    
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("EPR ");
        try (Lock locked = _lock.lock())
        {
            builder.append(_idle?"Idle/":"");
            builder.append(_producing?"Prod/":"");
            builder.append(_pending?"Pend/":"");
            builder.append(_execute?"Exec/":"");
        }
        builder.append(_producer);
        return builder.toString();
    }

    private class RunExecute implements Runnable
    {
        @Override
        public void run()
        {
            execute();
        }
    }
}