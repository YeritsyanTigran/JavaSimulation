package cachemodeler_multyprocessor;

/**
 * Structure that defines cache's position in the block
 */
public class BlockAddress
{    
    public int moduleNumber;    //Modules number where the block can be saved
    public int tag;             //Blocks number in the module (block's tag)
    
    public BlockAddress(int moduleNumber, int tag)
    {
        this.moduleNumber = moduleNumber;
        this.tag = tag;
    }
}
