package cachemodeler_multyprocessor;

//MultyProcessor params
public class SystemParameters 
{
    //Quantity of processors
    public int processorsCount;
    //Memory size
    public long memorySize;
    //Cache-memory size
    public int cacheSize;
    //CaceLevels
    public int cacheLevelsCount;
    //Cache-Block size
    public int cacheBlockSize;
    //Quantity of entries in cache-memory (associate mapping)
    public int waysCount;        
    //Coherence protocol
    public CoherenceProtocols coherenceProtocol;
    
    public SystemParameters(int processorsCount, long memorySize, int cacheSize,
            int cacheLevelsCount, int cacheBlockSize, int waysCount, 
            CoherenceProtocols coherenceProtocol)
    {
        this.processorsCount = processorsCount;
        this.memorySize = memorySize;
        this.cacheSize = cacheSize;
        this.cacheLevelsCount = cacheLevelsCount;
        this.cacheBlockSize = cacheBlockSize;
        this.waysCount = waysCount;
        this.coherenceProtocol = coherenceProtocol;  
    }
}
