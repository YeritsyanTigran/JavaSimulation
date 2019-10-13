package cachemodeler_multyprocessor;

//Model system params
public class ModelingParameters 
{    
    //Duration of modeling in tacts
    public long tactsCount;
    //Average error of query to address from previous destination
    public int querySigma;   
    //Probability of generation of a query to address in another process tact
    public double queryProbability;
    //Probability of generating query to read
    public double readQueryProbability;   
    //Duration of main memory cycle in processor tacts
    public int memoryCycleTime;
    //Duration of pointer search in cache-memory
    public int searchCachePointerTime;
    //Duration of read(write) operation of cache-memory
    public int bufferOperationTime;
    //Bus delay coefficient
    public int systemBusDelay;
    //Probability of accessing ShBlock
    public double ShBlockRefProb;
    //Sh blocks count
    public int ShBlocksCount;
    
    public ModelingParameters(long tactsCount, int querySigma,
            double queryProbability, double readQueryProbability,
            int memoryCycleTime, int searchCachePointerTime, int bufferOperationTime,
            int systemBusDelay, double ShBlockRefProb, int ShBlocksCount)
    {
        this.tactsCount = tactsCount;
        this.querySigma = querySigma;
        this.queryProbability = queryProbability;
        this.readQueryProbability = readQueryProbability;
        this.memoryCycleTime = memoryCycleTime;
        this.searchCachePointerTime = searchCachePointerTime;
        this.bufferOperationTime = bufferOperationTime;
        this.systemBusDelay = systemBusDelay;
        this.ShBlockRefProb = ShBlockRefProb;
        this.ShBlocksCount = ShBlocksCount;
    }
}
