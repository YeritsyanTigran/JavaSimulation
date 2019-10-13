package cachemodeler_multyprocessor;

//Cache memory (Tag Matrix)
public class CacheMemory
{
    public int[][] tags;
    public char[][] states;
    public int modulesCount;
    private SystemParameters systemParameters;
    
    public CacheMemory(SystemParameters systemParameters)            
    {
        //Defining the module quantity
        //Quantity of module's rows equals quantity of cache entries
        modulesCount = systemParameters.cacheSize / 
                (systemParameters.waysCount * systemParameters.cacheBlockSize);
        //Defining tag matrix
        //matrix row is a tag of a particular module
        tags = new int[modulesCount][];   
        states = new char[modulesCount][];
        for (int i=0; i<modulesCount; i++)
        {
            tags[i] = new int[systemParameters.waysCount];
            states[i] = new char[systemParameters.waysCount];
        }
        //Zeroing all tags (There are no caches in the beginning)
        for (int i=0; i<modulesCount; i++)
        {
            for (int j=0; j<systemParameters.waysCount; j++)
            {
                tags[i][j] = -1;
                states[i][j] = 'z';
            }
        }
        this.systemParameters = systemParameters;
    }    
    
    //Function that gives block's position in the cache
    //that contains byte in a given address
    public synchronized BlockAddress searchBlockPlace(long address)
    {
        int blockNumber = (int)(address / systemParameters.cacheBlockSize);
        int moduleNumber = blockNumber % modulesCount;
        int tag = blockNumber / modulesCount;        
        return new BlockAddress(moduleNumber, tag);
    }
    
    //Function that searches for block by blockAddress
    //If succeeded returns columns number
    public synchronized int searchBlock(BlockAddress ba)
    {
        //Searching for tag
        int tagNumber = 0;
        for (int i=0; i<systemParameters.waysCount; i++)
        {
            if (tags[ba.moduleNumber][i] == ba.tag)
            {                
                tagNumber = i;
                return tagNumber;
            }
        }
        return Const.NOT_FOUND;
    }
    
    //Function that searches for a free place in the cache's module with a giving number
    //Returns the number of a free block or NOT_FOUND
    public synchronized int searchFreePlace(int moduleNumber)
    {
        int freeBlockNumber = 0;
        for (int i=0; i<systemParameters.waysCount; i++)
        {
            if (tags[moduleNumber][i] == -1)
            {
                freeBlockNumber = i;
                return freeBlockNumber;
            }
        }
        return Const.NOT_FOUND;
    }
    
}
