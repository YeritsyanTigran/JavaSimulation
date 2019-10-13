package cachemodeler_multyprocessor;

import java.util.*;

//Multyprocessor
//Includes all the modeling objects.Processor with cache-memory,SystemBys and main memory
public class Multyprocessor implements Runnable
{    
    private Thread MultyprocThread; 
    private SystemParameters systemParameters; 
    private ModelingParameters modelingParameters;
    private Processor processors[];
    private CacheController cacheControllers[];
    private SystemBus systemBus;
    private ProcessorMonitor  processorMonitor;
    public boolean modelingRun;
    
    public Multyprocessor(SystemParameters systemParameters, ModelingParameters modelingParameters) {
        //Setting the system and model params
        this.systemParameters = systemParameters;     
        this.modelingParameters = modelingParameters;
        modelingRun = false;
        //Initializing processors
        processors = new Processor[systemParameters.processorsCount];
        int firstQueryAddress;
        for (int i=0; i<systemParameters.processorsCount; i++)
        {
            firstQueryAddress = (new Random()).nextInt(
                    (int)(systemParameters.memorySize - systemParameters.cacheBlockSize * modelingParameters.ShBlocksCount) / 2);
            processors[i] = new Processor(
                    modelingParameters, systemParameters, firstQueryAddress,
                    systemParameters.memorySize - 1, "proc_" + i, i);
        }
        //Initializing cache-memory controllers and binding it with processors
        //Initializing system bus and and binding it with controllers
        cacheControllers = new CacheController[systemParameters.processorsCount];
        systemBus = new SystemBus(modelingParameters, systemParameters);
        for (int i=0; i<systemParameters.processorsCount; i++)
        {
            cacheControllers[i] = new CacheController(systemParameters, modelingParameters);
            cacheControllers[i].setProcessor(processors[i]);
            cacheControllers[i].setSystemBus(systemBus);
            processors[i].setController(cacheControllers[i]);
        }      
        systemBus.setControllers(cacheControllers);
        processorMonitor = new ProcessorMonitor(processors, this);
        MultyprocThread = new Thread(this, "multyproc_thread");       
    }
    
    public SystemBus getSystemBus()
    {
        return this.systemBus;
    }
    
    public void run()
    {
        for (int i=0; i<systemParameters.processorsCount; i++) 
        {
            processors[i].start();
            cacheControllers[i].start();
        }
        systemBus.start();
        processorMonitor.start(); 
        modelingRun = true;
    }
    
    
    
    //Returns average memory cycle time for each processor and system
    public double[] getAvgMemoryCycleTime()
    {        
        double cycleTimes[] = new double[systemParameters.processorsCount+1];
        double fullTime = 0;
        for (int i=0; i<systemParameters.processorsCount; i++)
        {
            double newCycleTime = cacheControllers[i].getAvrMemoryCycleTime();
            cycleTimes[i] = newCycleTime;
            fullTime += newCycleTime;
        }
        cycleTimes[systemParameters.processorsCount] = 
                fullTime / systemParameters.processorsCount;
        return cycleTimes;        
    }
    
    //Returns loading for every processor and system
    public double[] getSystemLoading()
    {
        return processorMonitor.getLoadFactors();
    }
    
    //Returns system power
    public double getSystemPower()
    {        
        double power = 0;
        double[] loadings = this.getSystemLoading();
        for (int i=0; i<systemParameters.processorsCount; i++)
        {
            power += loadings[i] * 10;
        }
        return power;
    }
    
    
    public void startModeling()
    {
        MultyprocThread.run();
    }
    
    public double[] getHitRate()
    {
        double[] hitRates = new double[systemParameters.processorsCount+1];
        double fullHitRate = 0;
        for (int i=0; i<systemParameters.processorsCount; i++)
        {
            hitRates[i] = cacheControllers[i].getHitRate();
            fullHitRate += hitRates[i];
        }
        fullHitRate /= systemParameters.processorsCount;        
        hitRates[systemParameters.processorsCount] = fullHitRate;
        return hitRates;
    }
    
    public void stop()
    {     
        try
        {
            for (int i=0; i<systemParameters.processorsCount; i++)
            {
                processors[i].getProcessorThread().stop();
                cacheControllers[i].getCacheControllerThread().stop();
            }
            processorMonitor.processorMonitorThread.stop();
        }
        catch(Exception ex)
        {}
    }       
    
    public boolean isMESI()
    {
        if (this.systemParameters.coherenceProtocol == CoherenceProtocols.MESI)
        {
            return true;
        }
        else
        {
            return false;
        }
    }
    
    public boolean isMSI()
    {
        if (this.systemParameters.coherenceProtocol == CoherenceProtocols.MSI)
        {
            return true;
        }
        else
        {
            return false;
        }
    }
}
