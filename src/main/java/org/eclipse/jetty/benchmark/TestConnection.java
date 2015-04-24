package org.eclipse.jetty.benchmark;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.util.thread.ExecutionStrategy.Producer;
import org.openjdk.jmh.infra.Blackhole;


public class TestConnection implements Producer
{
    private final TestServer _server;
    private final AtomicInteger _requests = new AtomicInteger();
    private final ConcurrentLinkedQueue<Map<String,String>> _responses = new ConcurrentLinkedQueue<>(); 
    private final String _sessionid;
    private volatile CountDownLatch _latch;
    
    public TestConnection(TestServer server)
    {
        _server=server;
        _sessionid="SESSION-"+server.getRandom(100000000);
    }
    
    public void schedule()
    {
        _responses.clear();
        _requests.set(15+_server.getRandom(5));
        _latch=new CountDownLatch(_requests.get());
    }
    
    @Override
    public Runnable produce()
    {
        if (_requests.getAndDecrement()<=0)
            return null;
        
        // We have to simulate parsing a request
        // and creating a Runnable to handle that request.
        
        // The Random will represent the IO subsystem from which requests will be read
        
        // The map will represent the request object
        Map<String,String> request = new HashMap<>();
        Map<String,String> response = new HashMap<>();
        request.put("sessionid",_sessionid);
        
        int uri=_server.getRandom(100);
        request.put("uri",uri+".txt"); // one of 100 resources on server
        request.put("delay",Integer.toString(uri%4==1?_server.getRandom(100):0)); // random processing delay 0-100ms on 25% of requests
        Blackhole.consumeCPU(_server.getRandom(500)); // random CPU
        return new Handler(request,response);
    }

    private class Handler implements Runnable
    {
        private final Map<String,String> _request;
        private final Map<String,String> _response;
        public Handler(Map<String, String> request,Map<String, String> response)
        {
            _request=request;
            _response=response;
        }
        
        @Override
        public void run()
        {
            // Obtain the session
            Map<String,String> session = _server.getSession(_request.get("sessionid"));
            
            // Check we are authenticated
            String userid;
            synchronized (session)
            {
                userid = session.get("userid");
                Blackhole.consumeCPU(100);
                if (userid==null)
                {
                    userid="USER-"+Math.abs(session.hashCode());
                    session.put("userid",userid);
                }
            }
            
            // simulate processing delay, blocking, etc.
            int delay = Integer.parseInt(_request.get("delay"));
            try
            {
                if (delay>0)
                    Thread.sleep(delay);
            }
            catch(InterruptedException e)
            {}
            
            // get the uri 
            String uri = _request.get("uri");
            
            // look for a file
            File file = _server.getFile(uri);
            if (file.exists())
            {
                _response.put("contentType","file");
                _response.put("lastModified",Long.toString(file.lastModified()));
                _response.put("length",Long.toString(file.length()));
                _response.put("content","This should be content from a file, but lets pretend it was cached");
            }
            else
            {
                _response.put("contentType","dynamic");

                String id;
                synchronized(session)
                {
                  id =session.get("id");   
                }
                String content="This is content for "+uri+
                        " generated for "+userid+
                        " with session "+id+
                        " at time "+System.currentTimeMillis()+
                        " on thread "+Thread.currentThread();
                //System.err.println(content);
                _response.put("content",content);
            }

            Blackhole.consumeCPU(1000);
            
            _responses.offer(_response);
            _latch.countDown();
        }
                
    }

    public long getResult()
    {
        try
        {
            _latch.await();
            
        }
        catch (InterruptedException e)
        {
            throw new IllegalStateException(e);
        }

        // System.err.println("onComplete "+_sessionid);
        // check all responses
        long hash=0;
        for (Map<String,String> response:_responses)
        {
            hash+=response.size();
            hash^=response.get("content").hashCode();
        }
        return hash;
    }
    
    public long getResponses()
    {
        try
        {
            _latch.await();
        }
        catch (InterruptedException e)
        {
            throw new IllegalStateException(e);
        }
        return _responses.size();
    }
    
}
