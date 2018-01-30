package org.eclipse.jetty.benchmark;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;

import org.eclipse.jetty.util.thread.QueuedThreadPool;


public class TestServer implements Executor
{
    private final ConcurrentMap<String,Map<String,String>> _sessions= new ConcurrentHashMap<>();
    private final QueuedThreadPool _threadpool = new QueuedThreadPool(200);
    private final File _docroot;

    TestServer(File docroot)
    {
        _docroot=docroot;
    }
    
    TestServer()
    {
        this(new File("/tmp/"));
    }
    
    
    public Map<String,String> getSession(String sessionid)
    {
        Map<String,String> session = _sessions.get(sessionid);
        if (session==null)
        {
            session = new HashMap<>();
            session.put("id",sessionid);
            Map<String,String> s =_sessions.putIfAbsent(sessionid,session);
            if (s!=null)
                session=s;
        }
        return session;
    }
    
    public int getRandom(int max)
    {
        return ThreadLocalRandom.current().nextInt(max);
    }

    @Override
    public void execute(Runnable task)
    {
        _threadpool.execute(task);
    }

    public void start() throws Exception
    {
        _threadpool.start();
    }

    public void stop() throws Exception
    {
        _threadpool.stop();
    }
    
    public File getFile(String path)
    {
        return new File(_docroot,path);
    }
    
}
