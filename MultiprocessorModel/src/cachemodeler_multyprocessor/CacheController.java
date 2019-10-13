package cachemodeler_multyprocessor;

public class CacheController implements Runnable
{ //Controllers number
    public int number;
    //Model's thread
    private Thread cacheControllerThread;
    //Cache memory-process
    private Processor processor;
    //SystemBus reference (for regulating the threads)
    private SystemBus systemBus;
    //System params for the model
    private SystemParameters systemParameters;
    //Model params
    private ModelingParameters modelingParameters;
    //Cache memory
    private CacheMemory cacheMemory;
    //Current query for processing
    private Query procQuery;
    //Cache Hit counter
    private int hitCounter;
    //Cache miss counter
    private int missCounter;
    // Sign that controller needs to be stopped
    private boolean isStopped;
            
    
    public CacheController(SystemParameters systemParameters,ModelingParameters modelingParameters)
    {
        this.systemParameters = systemParameters;
        this.modelingParameters = modelingParameters;
        cacheControllerThread = new Thread(this, "cache");            
        cacheMemory = new CacheMemory(systemParameters);
        hitCounter = missCounter = 0;
        isStopped = true;
    }
    
    public synchronized void restartController()
    {
        this.isStopped = false;
    }
    
    public synchronized CacheMemory getCacheMemory()
    {
        return this.cacheMemory;
    }
    
    public synchronized Processor getProcessor()
    {
        return this.processor;
    }
    
    public void setQuery(Query newQuery)
    {
        procQuery = newQuery;
    }
    
    //Getter for cache thread
    public synchronized Thread getCacheControllerThread()
    {
        return cacheControllerThread;
    }
        
    public void addHit()
    {
        this.hitCounter++;
    }
    
    public void addMiss()
    {
        this.missCounter++;
    }
    
    public void setSystemBus(SystemBus systemBus)
    {
        this.systemBus = systemBus;
    }
    
    //Processor set method
    public void setProcessor(Processor processor)
    {
        this.processor = processor;
        cacheControllerThread.setName("cache_" + processor.name);
        this.number = processor.number;
    }
    
    //Restarts the processor
    public void restartProcessor()
    {
        processor.restart();
    }
        
    @Override
    public void run()
    {
        while (processor.getProcessorThread().isAlive()) 
        {             
            try
            {                
                //If processor is not started we take the query and process it
                if (!processor.getRun() && !isStopped)
                {
                    //Search for data in cache
                    //Define the block's and module's number
                    BlockAddress ba = cacheMemory.searchBlockPlace(procQuery.address);                          
                    //Check if block is in cache
                    if (cacheMemory.searchBlock(ba) != Const.NOT_FOUND)
                    //Block is found. Cache hit
                    {
                        addHit();
                        if (procQuery.isRead)   
                        //Cache hit while reading
                        {
                            //Setting the query type
                            procQuery.queryType = QueryType.READ_HIT;                            
                        }
                        else    
                        //Cache hit while writing
                        {
                            //Setting the query type
                            procQuery.queryType = QueryType.WRITE_HIT;                            
                        }
                        //Adding the query to a que
                        //Stopping processor's work
                        isStopped = true;
                        this.systemBus.addQuery(procQuery);                 
                    }    //End of Cache-Hit scenario
                    else   
                    //Block not found in cache. Cache-Miss
                    {
                        addMiss();
                        //Setting the query type and adding it to a bus
                        if (procQuery.isRead)
                        {
                            procQuery.queryType = QueryType.READ_MISS;
                        }
                        else
                        {
                            procQuery.queryType = QueryType.WRITE_MISS;
                        }
                        //Stopping the controller
                        isStopped = true;
                        this.systemBus.addQuery(procQuery);                  
                    }       //End of Cache-Miss scenario

                }//End of controller work
                else
                {
                    cacheControllerThread.sleep(10);
                }
            }        
            catch (Exception ex)
            {
                System.out.println("------- !!! " + ex + " !!!\n");
                System.out.println("-------An error occured!!!\n");
            }
        }
    }
    
    public void start()
    {
        isStopped = false;
        cacheControllerThread.start();
    }  
    
    public double getAvrMemoryCycleTime()
    {        
        return (double)processor.getUnloadCount() / processor.getQueryCounter();
    }
    
    public double getHitRate()
    {
        return (double)hitCounter / (hitCounter + missCounter) * 100;        
    }        
}
