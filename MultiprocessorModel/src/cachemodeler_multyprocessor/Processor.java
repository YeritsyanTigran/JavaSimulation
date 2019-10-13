package cachemodeler_multyprocessor;
import java.util.*;
import javax.swing.JTextArea;

//One core processor
public class Processor implements Runnable
{
    //Processor name
    public String name;    
    //Processor number
    public int number;
    //Processor thread
    private Thread processorThread;    
    //Model params
    private ModelingParameters modelingParameters;
    //System params
    private SystemParameters systemParameters;
    //Processor state
    private boolean isRun;
    //Las Query Address
    private long lastQueryAddress;
    //Address of the highest byte in memory
    private long maximumAddress;
    //Cache Controller
    private CacheController cacheController;
    //Current tact number of the model
    public long tactNumber;
        
    //Query counter
    private long queryCounter;    
    //Load counter
    private long loadCount;
    //Unload Counter
    private long unloadCount;
    

    public synchronized long getLoadCount()
    {
        return loadCount;
    }
    

    public synchronized long getUnloadCount()
    {
        return unloadCount;
    }
    

    public synchronized void addLoad(int add)
    {
        loadCount += add;
    }
    

    public synchronized void addUnload(int add)
    {
        unloadCount += add;
    }
    

    public synchronized void addQueryCounter()
    {
        queryCounter++;
    }
    

    public synchronized long getQueryCounter()
    {
        return queryCounter;
    }
    

    public synchronized Thread getProcessorThread()
    {
        return processorThread;
    }    
    
    synchronized public boolean getRun()
    {
        return isRun;
    }
    
    synchronized public void setRun(boolean isRun)
    {
        this.isRun = isRun;
    }
    
    public void setController(CacheController cacheController)
    {
        this.cacheController = cacheController;
    }
    
    public Processor(ModelingParameters modelingParameters, SystemParameters systemParameters,
            long startAddress, long maximumAddress, String name, int number)
    {
        this.modelingParameters = modelingParameters;
        this.systemParameters = systemParameters;
        this.lastQueryAddress = startAddress;
        this.maximumAddress = maximumAddress;
        this.name = name;
        this.number = number;
        processorThread = new Thread(this, name);     
        isRun = false;        
        queryCounter = loadCount = unloadCount = tactNumber = 0;               
    }     
  
    //Query generator
    private Query generateQuery()
    {       
        Random r = new Random();
        long address = 0;
        //Generating query address
        if (r.nextDouble() <= modelingParameters.ShBlockRefProb)
        //Generating query address of SHBlock as a random value in the diapason  of divided addresses
        {
            int sharedAreaSize = systemParameters.cacheBlockSize * modelingParameters.ShBlocksCount;
            address = r.nextInt(sharedAreaSize) + systemParameters.memorySize - sharedAreaSize;
        }
        else
        //Query address generation to ShBlock as a random value with normal distribution
        //Mathematical Expectation - lastQueryAddress
        //Standard Deviation - querySigma
        {
            address = Math.round(lastQueryAddress + modelingParameters.querySigma*r.nextGaussian());            
            if (address > maximumAddress) address = maximumAddress;
            if (address < 0) address = 0;
            lastQueryAddress = address;
        }        
                
        if (r.nextDouble() <= modelingParameters.readQueryProbability)
        {
            //With probability of readQueryProbability we generate query to read
            return new Query(address, true, number);
        }
        else
        {
            //With the opposite probability we generate query to write
            return new Query(address, false, number);
        }
    }    
    
    //Complete the processor tact (if it doesn't wait for the answer from memory)
    //With the probability of queryProbability we generate query and stop the work of the processor otherwise do nothing(continue work)
    public void doNextTact(long i)
    {
        //In any case we increase load
        addLoad(1);
        if (new Random().nextDouble() <= modelingParameters.queryProbability)  
        {
            Query newQuery = generateQuery();
            addQueryCounter();
            String queryKind = newQuery.isRead ? "read" : "write";
            System.out.println("Processor " + name + " (Tact " + i + "): generated a query to memory\n");
            System.out.println("Address: " + newQuery.address + " " + queryKind + "\n");
            cacheController.setQuery(newQuery);
            setRun(false);                                    
        } 
        else
        {
            System.out.println("Processor " + name + " (Tact " + i + "): operation without query \n");
        }
    }   
    
    //Start the thread modeling the work of processor
    synchronized public void start()
    {
        setRun(true);
        processorThread.start();
    }
    
    synchronized public void restart()
    {
        setRun(true);
        System.out.println("Controller " + cacheController.getCacheControllerThread().getName()
                + "completed the query\n");
        this.cacheController.restartController();
    }
    
    @Override
    public void run()
    {
        for (int i=0; i<modelingParameters.tactsCount; i++) 
        {            
            try
            {
                tactNumber++;
                if (!getRun())
                {                    
                    processorThread.sleep(25);
                    continue;
                }
                
                doNextTact(i);
               
                if (i == modelingParameters.tactsCount-1)
                {
                    System.out.println("-----Processor " + name + " finished his work!!!\n");
                }
                processorThread.sleep(25);
            }
            catch (Exception ex)
            {
                this.processorThread.stop();
                System.out.println("-------Processor stopped\n");
            }
        }
    }    
}
