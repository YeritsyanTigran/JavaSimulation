package cachemodeler_multyprocessor;

//Class for calculating the processor load and system coefficient
public class ProcessorMonitor implements Runnable
{
    private Processor[] processors;
    //unload tacts
    private long[] unloadTacts; 
    //loaded tacts
    private long[] loadTacts;
    //load factors array
    private double[] loadFactors;
    //monitor thread
    public Thread processorMonitorThread;
    private Multyprocessor multyproc;
    
    public ProcessorMonitor(Processor[] processors, Multyprocessor mp)
    {
        this.processors = processors;
        this.multyproc = mp;
        loadFactors = new double[processors.length + 1];
        processorMonitorThread = new Thread(this, "monitor_thread");
    }
    
    @Override
    public void run()
    {
        while (true)
        {
            //if all the processors are working than we should ask their state
            //otherwise stop the thread
            boolean allRun = false;
            for (int i=0;  i<processors.length; i++)
            {
                allRun = allRun || processors[i].getProcessorThread().isAlive();
            }
            if (!allRun) 
            {
                System.out.println("Modeling completed: " + java.util.Calendar.getInstance().getTime() + "\n");
                
                double[] hitRates = this.multyproc.getHitRate();
                double[] loadings = this.multyproc.getSystemLoading();
                double[] memCycle = this.multyproc.getAvgMemoryCycleTime();

                System.out.println("Loading: \n");
                //int i;
                for (int i=0; i<loadings.length-1; i++)
                {
                    System.out.println("  - Processor " + i + ": " + loadings[i] + "%\n");
                }
                System.out.println("  --Whole system: " + loadings[loadings.length-1] + "%\n");

                System.out.println("Cash-Hit coefficient: \n");
                for (int i=0; i<hitRates.length-1; i++)
                {
                    System.out.println("  - Processors cache " + i + ": " + hitRates[i] + "%\n");
                }
                System.out.println("  -- Whole Cache-Memory: " + hitRates[hitRates.length-1] + "%\n");

                System.out.println("Average time of memory cycle: \n");
                for (int i=0; i<memCycle.length-1; i++)
                {
                    System.out.println("  - For processor " + i +": " + memCycle[i] + "\n");
                }
                System.out.println("  -- Whole system: " + memCycle[memCycle.length-1] + "\n");

                System.out.println("  - Performance of the system (on 1000 tacts):\n   - " + multyproc.getSystemPower() + "\n");
                System.out.println("  - For ShBlocks:\n   - " + multyproc.getSystemBus().getSharedQueriesCount() + "%\n");
                
                if (this.multyproc.isMSI())
                {
                    int queriesToMBlockCount = multyproc.getSystemBus().getMStatesCounter();
                    int queriesToSBlockCount = multyproc.getSystemBus().getSStatesCounter();
                    int queriesToIBlockCount = multyproc.getSystemBus().getIStatesCounter();
                    double MProb = (double)queriesToMBlockCount / 
                            (queriesToMBlockCount + queriesToSBlockCount + queriesToIBlockCount);
                    double SProb = (double)queriesToSBlockCount / 
                            (queriesToMBlockCount + queriesToSBlockCount + queriesToIBlockCount);
                    double IProb = (double)queriesToIBlockCount / 
                            (queriesToMBlockCount + queriesToSBlockCount + queriesToIBlockCount);
                    System.out.println("  - For blocks state M:\n   - " + MProb*100 + "%\n");
                    System.out.println("  - For blocks state S:\n   - " + SProb*100 + "%\n");
                    System.out.println("  - For blocks state I:\n   - " + IProb*100 + "%\n");
                }
                if (this.multyproc.isMESI())
                {
                    int queriesToMBlockCount = multyproc.getSystemBus().getMStatesCounter();
                    int queriesToEBlockCount = multyproc.getSystemBus().getEStatesCounter();
                    int queriesToSBlockCount = multyproc.getSystemBus().getSStatesCounter();
                    int queriesToIBlockCount = multyproc.getSystemBus().getIStatesCounter();
                    double MProb = (double)queriesToMBlockCount / 
                            (queriesToMBlockCount + queriesToSBlockCount + queriesToIBlockCount + queriesToEBlockCount);
                    double EProb = (double)queriesToEBlockCount / 
                            (queriesToMBlockCount + queriesToSBlockCount + queriesToIBlockCount + queriesToEBlockCount);
                    double SProb = (double)queriesToSBlockCount / 
                            (queriesToMBlockCount + queriesToSBlockCount + queriesToIBlockCount + queriesToEBlockCount);
                    double IProb = (double)queriesToIBlockCount / 
                            (queriesToMBlockCount + queriesToSBlockCount + queriesToIBlockCount + queriesToEBlockCount);
                    System.out.println("  - For blocks state M:\n   - " + MProb*100 + "%\n");
                    System.out.println("  - For blocks state S:\n   - " + SProb*100 + "%\n");
                    System.out.println("  - For blocks state E:\n   - " + EProb*100 + "%\n");
                    System.out.println("  - For blocks state I:\n   - " + IProb*100 + "%\n");
                }
                
                        
                processorMonitorThread.stop();
                multyproc.modelingRun = false;
                break;
            }
            else
            {               
                try
                {
                    processorMonitorThread.sleep(50);
                }
                catch (InterruptedException ex) {}
            }
        }
    }
    
    //Calculation of load coefficient
    public double[] getLoadFactors()
    {
        double fullLoad = 0;
        for (int i=0;  i<processors.length; i++)
        {
            loadFactors[i] = (double)processors[i].getLoadCount() / 
                    (processors[i].getLoadCount() + processors[i].getUnloadCount()) * 100;
            fullLoad += loadFactors[i];
        }
        fullLoad /= processors.length;       
        loadFactors[processors.length] = fullLoad;
        return loadFactors;
    }
    
    public void start()
    {
        processorMonitorThread.start();
    }
}
