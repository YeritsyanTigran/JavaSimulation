package cachemodeler_multyprocessor;

import java.util.*;
import java.util.Collections;
import javax.swing.JTextArea;

//System Bus is needed for communication between processor and cache-memory
//Deliveres queries to the memory
public class SystemBus implements Runnable
{
    //system Bus Thread
    private Thread systemBusThread;
    //cache-controller array connected with bus
    private CacheController[] cacheControllers;
    //query list
    private LinkedList<Query> queries;
    //model params
    private ModelingParameters modelingParameters;
    //system params
    private SystemParameters systemParameters;
    //counter of queries
    private int sharedQueries;
    private int allQueries;
    //Counter for accounting the state of the blocks (For protocols MSI and MESI)
    private int mStateCounter;
    private int sStateCounter;
    private int iStateCounter;
    private int eStateCounter;


    public SystemBus(ModelingParameters modelingParameters, SystemParameters sp)
    {
        this.modelingParameters = modelingParameters;
        this.systemParameters = sp;
        systemBusThread = new Thread(this, "system_bus");
        queries = new LinkedList<Query>();
        sharedQueries = allQueries = 0;
        mStateCounter = sStateCounter = iStateCounter = this.eStateCounter = 0;
    }

    //Add query in the end of the que
    public synchronized void addQuery(Query newQuery)
    {
        queries.add(newQuery);
    }

    //Deletes and returns the first query in que
    //If the que is empty returns null
    public synchronized Query getQuery()
    {
        return queries.pollFirst();
    }

    public void setControllers(CacheController[] cacheControllers)
    {
        this.cacheControllers = cacheControllers;
    }

    public boolean isControllersAllive()
    {
        boolean controllersAllive = false;
        for (int i=0; i<cacheControllers.length; i++)
        {
            controllersAllive =
                    controllersAllive || cacheControllers[i].getCacheControllerThread().isAlive();
        }
        return controllersAllive;
    }

    public void run()
    {
        while(true)
        {
            if (isControllersAllive())   //Continues the work
            {
                if(queries.isEmpty()){
                //Que is empty - make pause between another tact and then continue the cycle of query processing to the bus                 {
                    try
                    {
                        systemBusThread.sleep(5 * modelingParameters.systemBusDelay);
                    }
                    catch (Exception ex) {}
                    finally
                    {
                        continue;
                    }
                }
                else
                //There are queries in th que. Process them
                {
                    Query curQuery;
                    //Get the query from que
                    curQuery = getQuery();

                    //We get the array of controller numbers whose queries are in the que (besides the processing ones)
                    //Corresponding processors are going to wait for current query
                    boolean isWaiting = queries.isEmpty();
                    int[] waitingProcessorsNumbers = null;
                    Object[] othersQueries;
                    if (isWaiting)
                    {
                        othersQueries = (Collections.synchronizedList(queries)).toArray();
                        //Creating an array of processes numbers whose queris are in the que
                        waitingProcessorsNumbers = new int[othersQueries.length];
                        for (int i=0; i<waitingProcessorsNumbers.length; i++)
                        {
                            waitingProcessorsNumbers[i] = ((Query)(othersQueries[i])).number;
                        }
                    }
                    //Determine the position of the block
                    BlockAddress ba =      //Modules number and tag
                            cacheControllers[curQuery.number].getCacheMemory().searchBlockPlace(curQuery.address);
                    int colNumber =        //cells number with block in the modules cash (if it is there)
                            cacheControllers[curQuery.number].getCacheMemory().searchBlock(ba);

                    //Increment the quantity of queries
                    allQueries++;
                    //Determine if the block is dividable
                    for (int i=0; i<cacheControllers.length; i++)
                    {
                        if (i == curQuery.number) continue;
                        //If the block is in caches process, than we increment quantity of queries to dividable blocks (ShBlock)

                        if (cacheControllers[i].getCacheMemory().searchBlock(ba)!= Const.NOT_FOUND)
                        {
                            sharedQueries++;
                            break;
                        }
                    }

                    //Next we continue accordingly with the coherence protocol
                    switch (systemParameters.coherenceProtocol)
                    {
                        case BROADCAST:
                        {
                            //Realisation of the broadcasting procotol
                            switch (curQuery.queryType)
                            {
                                case READ_HIT:
                                {
                                    if (cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][colNumber] != 'i')
                                    //If the block in cache is valid than we read from cache
                                    {
                                         cacheControllers[curQuery.number].getProcessor().addUnload(
                                                 modelingParameters.searchCachePointerTime + modelingParameters.bufferOperationTime);
                                    }
                                    else
                                    //If the block is invalid then it needs to be read from the main memory and all the
                                    //other processes need to because bus is envolved
                                    {
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                 modelingParameters.searchCachePointerTime + modelingParameters.bufferOperationTime);
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                 modelingParameters.memoryCycleTime);
                                        //Change the blocks status to Valid
                                        cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][colNumber] = 'v';
                                        //Increase the time of waiting of all the other processors on the same value
                                        if (isWaiting)
                                        {
                                            for (int i=0; i<waitingProcessorsNumbers.length; i++)
                                            {
                                                cacheControllers[waitingProcessorsNumbers[i]].getProcessor().addUnload(
                                                     modelingParameters.memoryCycleTime);
                                            }
                                        }
                                    }
                                }   // End of the READ_HIT / BROADCAST !!!
                                    break;

                                case READ_MISS:
                                {
                                    //Determine if there is a place for the new block
                                    int newCol;
                                    if ((newCol = cacheControllers[curQuery.number].getCacheMemory().searchFreePlace(ba.moduleNumber))
                                            != Const.NOT_FOUND)
                                    //No need of outing the block. Inseting it int the cache
                                    {
                                        //Setting the tag of the inserting block in the free row of the cache
                                        cacheControllers[curQuery.number].getCacheMemory().tags[ba.moduleNumber][newCol] = ba.tag;
                                        //Setting the status of the block Valid
                                        cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][newCol] = 'v';
                                        //Setting the memoryCycleTime for current processor
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.memoryCycleTime);
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.searchCachePointerTime + 2 * modelingParameters.bufferOperationTime);
                                        //Increasing the wait time for all the other processes
                                        if (isWaiting)
                                        {
                                            for (int i=0; i<waitingProcessorsNumbers.length; i++)
                                            {
                                                cacheControllers[waitingProcessorsNumbers[i]].getProcessor().addUnload(
                                                     modelingParameters.memoryCycleTime);
                                            }
                                        }
                                    }
                                    else
                                    //One of the blocks need to be outed
                                    //Randomly choosing it from all the blocks
                                    {
                                        int replBlock = new Random().nextInt(systemParameters.waysCount);
                                        cacheControllers[curQuery.number].getCacheMemory().tags[ba.moduleNumber][replBlock] = ba.tag;
                                        cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][replBlock] = 'v';
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.memoryCycleTime);
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.searchCachePointerTime + 2 * modelingParameters.bufferOperationTime);
                                        if (isWaiting)
                                        {
                                            for (int i=0; i<waitingProcessorsNumbers.length; i++)
                                            {
                                                cacheControllers[waitingProcessorsNumbers[i]].getProcessor().addUnload(
                                                     modelingParameters.searchCachePointerTime);
                                                cacheControllers[waitingProcessorsNumbers[i]].getProcessor().addUnload(
                                                     modelingParameters.memoryCycleTime);
                                            }
                                        }
                                    }
                                }     //End of the READ_MISS / BROADCAST !!!
                                    break;

                                case WRITE_HIT:
                                {
                                    //Executing write in the cache and in the main memory
                                    cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.searchCachePointerTime + modelingParameters.bufferOperationTime);
                                    cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.memoryCycleTime);

                                    //All the other controllers search for the block that needs to be written on in their cache
                                    for (int i=0; i<cacheControllers.length; i++)
                                    {
                                        if (i == curQuery.number) continue;
                                        cacheControllers[i].getProcessor().addUnload(
                                             modelingParameters.searchCachePointerTime);
                                        //If in the cache of the processor there is a block on wich the current processor writes
                                        //than that block will have status invalid
                                        int invNum;
                                        if ((invNum = cacheControllers[i].getCacheMemory().searchBlock(ba))!= Const.NOT_FOUND)
                                        {
                                            cacheControllers[i].getCacheMemory().states[ba.moduleNumber][invNum] = 'i';
                                        }
                                    }
                                    //All the other processors will wait
                                    if (isWaiting)
                                    {
                                        for (int i=0; i<waitingProcessorsNumbers.length; i++)
                                        {
                                            cacheControllers[waitingProcessorsNumbers[i]].getProcessor().addUnload(
                                                 modelingParameters.memoryCycleTime);
                                        }
                                    }
                                    //The state of the block in the cache after write is always Valid
                                    cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][colNumber] = 'v';
                                }
                                    break;     // End WRITE_HIT / BROADCAST !!!

                                case WRITE_MISS:
                                {
                                    //Do accordingly with the situation of write read and we also anylize the blocks in other caches.
                                    //We determine if there is a place for the new block in the cache
                                    int newCol;
                                    if ((newCol = cacheControllers[curQuery.number].getCacheMemory().searchFreePlace(ba.moduleNumber))
                                            != Const.NOT_FOUND)
                                    {
                                        cacheControllers[curQuery.number].getCacheMemory().tags[ba.moduleNumber][newCol] = ba.tag;
                                        cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][newCol] = 'v';
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.memoryCycleTime);
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.searchCachePointerTime + modelingParameters.bufferOperationTime);

                                    }
                                    else
                                    {
                                        int replBlock = new Random().nextInt(systemParameters.waysCount);
                                        cacheControllers[curQuery.number].getCacheMemory().tags[ba.moduleNumber][replBlock] = ba.tag;
                                        cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][replBlock] = 'v';
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.memoryCycleTime);
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.searchCachePointerTime + 2 * modelingParameters.bufferOperationTime);
                                    }
                                    if (isWaiting)
                                    {
                                        for (int i=0; i<waitingProcessorsNumbers.length; i++)
                                        {
                                            cacheControllers[waitingProcessorsNumbers[i]].getProcessor().addUnload(
                                                 modelingParameters.memoryCycleTime);
                                        }
                                    }
                                    for (int i=0; i<cacheControllers.length; i++)
                                    {
                                        if (i == curQuery.number) continue;
                                        cacheControllers[i].getProcessor().addUnload(
                                             modelingParameters.searchCachePointerTime);
                                        int invNum;
                                        if ((invNum = cacheControllers[i].getCacheMemory().searchBlock(ba))!= Const.NOT_FOUND)
                                        {
                                            cacheControllers[i].getCacheMemory().states[ba.moduleNumber][invNum] = 'i';
                                        }
                                    }
                                }
                                    break;    // End WRITE_MISS / BROADCAST !!!
                            }
                        }   //End of BROADCAST REALISATION !!!!!!!
                            break;

                        case WRITE_ONCE:
                        {
                            switch (curQuery.queryType)
                            {
                                case READ_HIT:
                                {
                                    if (cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][colNumber] != 'i')
                                    {
                                         cacheControllers[curQuery.number].getProcessor().addUnload(
                                                 modelingParameters.searchCachePointerTime + modelingParameters.bufferOperationTime);

                                    }
                                    else
                                    {
                                        //Processor tries to find the block in its cache - the bus is free
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                 modelingParameters.searchCachePointerTime);
                                        //Main memory read is happening and write to cache also - bus is busy
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                 modelingParameters.memoryCycleTime + modelingParameters.bufferOperationTime);
                                        //Processor reads from cache - bus is free
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                 modelingParameters.bufferOperationTime);

                                        //Change the status of the code to Valid
                                        cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][colNumber] = 'v';

                                        if (isWaiting)
                                        {
                                            for (int i=0; i<waitingProcessorsNumbers.length; i++)
                                            {
                                                cacheControllers[waitingProcessorsNumbers[i]].getProcessor().addUnload(
                                                     modelingParameters.memoryCycleTime + modelingParameters.bufferOperationTime);
                                            }
                                        }
                                    }
                                }
                                    break;    //End READ_HIT / WRITE_ONCE !!!

                                case READ_MISS:
                                {
                                    //Determine if that block has status Dirty in some the cache
                                    //All the processors are waiting
                                    boolean dirtyFound = false;
                                    for (int i=0; i<cacheControllers.length; i++)
                                    {
                                        cacheControllers[i].getProcessor().addUnload(modelingParameters.searchCachePointerTime);
                                        int colNum = cacheControllers[i].getCacheMemory().searchBlock(ba);
                                        if (colNum != Const.NOT_FOUND &&
                                                cacheControllers[i].getCacheMemory().states[ba.moduleNumber][colNum] == 'd')
                                        //If the copy of the block in the cache  with status dirty is found
                                        {
                                            dirtyFound = true;
                                            //Write him in the main memory and change his status to Valid
                                            //While writing the bus in the OS will be busy but the reading processor can have the block
                                            cacheControllers[i].getCacheMemory().states[ba.moduleNumber][colNum] = 'v';
                                            if (isWaiting)
                                            {
                                                for (int j=0; j<waitingProcessorsNumbers.length; j++)
                                                {
                                                    if (j==curQuery.number) continue;
                                                    cacheControllers[j].getProcessor().addUnload(
                                                            modelingParameters.memoryCycleTime);
                                                }
                                            }
                                            break;
                                        }
                                    }
                                    //If somewhere in the cache there is a block with status reserved we change that status to Valid
                                    //That operation happens concurrently with the search of the reference and doesn't need additional time
                                    for (int i=0; i<cacheControllers.length; i++)
                                    {
                                        if (i == curQuery.number) continue;
                                        int colNum = cacheControllers[i].getCacheMemory().searchBlock(ba);
                                        if (colNum != Const.NOT_FOUND &&
                                                cacheControllers[i].getCacheMemory().states[ba.moduleNumber][colNum] == 'r')
                                        {
                                            cacheControllers[i].getCacheMemory().states[ba.moduleNumber][colNum] = 'v';
                                        }
                                    }

                                    //Determine if there is a place for the new block in cache
                                    int newCol;
                                    if ((newCol = cacheControllers[curQuery.number].getCacheMemory().searchFreePlace(ba.moduleNumber))
                                            != Const.NOT_FOUND)
                                    {
                                        cacheControllers[curQuery.number].getCacheMemory().tags[ba.moduleNumber][newCol] = ba.tag;
                                        cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][newCol] = 'v';
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.searchCachePointerTime);
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.memoryCycleTime);
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.bufferOperationTime);
                                        if (!dirtyFound && isWaiting)
                                        {
                                            for (int i=0; i<waitingProcessorsNumbers.length; i++)
                                            {
                                                cacheControllers[waitingProcessorsNumbers[i]].getProcessor().addUnload(
                                                     modelingParameters.memoryCycleTime);
                                            }
                                        }

                                    }
                                    else
                                    {
                                        int replBlock = new Random().nextInt(systemParameters.waysCount);
                                        if (cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][replBlock] == 'd')
                                        {
                                            cacheControllers[curQuery.number].getProcessor().addUnload(modelingParameters.memoryCycleTime);
                                            if (isWaiting)
                                            {
                                                for (int j=0; j<waitingProcessorsNumbers.length; j++)
                                                {
                                                    cacheControllers[j].getProcessor().addUnload(modelingParameters.memoryCycleTime);
                                                }
                                            }
                                        }
                                        cacheControllers[curQuery.number].getCacheMemory().tags[ba.moduleNumber][replBlock] = ba.tag;
                                        cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][replBlock] = 'v';
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.searchCachePointerTime);
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.memoryCycleTime);
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.bufferOperationTime);
                                        if (!dirtyFound && isWaiting)
                                        {
                                            for (int i=0; i<waitingProcessorsNumbers.length; i++)
                                            {
                                                cacheControllers[waitingProcessorsNumbers[i]].getProcessor().addUnload(
                                                     modelingParameters.memoryCycleTime);
                                            }
                                        }
                                    }
                                }
                                    break;      // END READ_MISS / WRITE_ONCE !!!

                                case WRITE_HIT:
                                {
                                    //Searching for the reference in the cache - bus is free
                                    cacheControllers[curQuery.number].getProcessor().addUnload(
                                            modelingParameters.searchCachePointerTime);
                                    if (cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][colNumber] == 'v')
                                    //Block is valid
                                    {
                                        //Change the status to Reserved
                                        cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][colNumber] = 'r';
                                        //Write to the cache - bus is free
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                            modelingParameters.bufferOperationTime);
                                        //Write the block in the main memory - bus is busy
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                            modelingParameters.memoryCycleTime);
                                        if (isWaiting)
                                        {
                                            for (int i=0; i<waitingProcessorsNumbers.length; i++)
                                            {
                                                cacheControllers[waitingProcessorsNumbers[i]].getProcessor().addUnload(
                                                     modelingParameters.memoryCycleTime);
                                            }
                                        }
                                    }
                                    else
                                    //Block is either  reserved, dirty or invalid
                                    {
                                        //Change the status of the block to dirty
                                        cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][colNumber] = 'd';
                                        //Write to cache - bus is free
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                            modelingParameters.bufferOperationTime);
                                        //No need to write to main memory
                                    }
                                    for (int i=0; i<cacheControllers.length; i++)
                                    {
                                        if (i == curQuery.number) continue;
                                        cacheControllers[i].getProcessor().addUnload(
                                             modelingParameters.searchCachePointerTime);
                                        int invNum;
                                        if ((invNum = cacheControllers[i].getCacheMemory().searchBlock(ba))!= Const.NOT_FOUND)
                                        {
                                            cacheControllers[i].getCacheMemory().states[ba.moduleNumber][invNum] = 'i';
                                        }
                                    }
                                }
                                    break;     // END WRITE_HIT / WRITE_ONCE !!!

                                case WRITE_MISS:
                                {
                                    //Do accordingly in case of read-miss and also anylize the blocks in the cache
                                    boolean dirtyFound = false;
                                    for (int i=0; i<cacheControllers.length; i++)
                                    {
                                        cacheControllers[i].getProcessor().addUnload(modelingParameters.searchCachePointerTime);
                                        int colNum = cacheControllers[i].getCacheMemory().searchBlock(ba);
                                        if (colNum != Const.NOT_FOUND &&
                                                cacheControllers[i].getCacheMemory().states[ba.moduleNumber][colNum] == 'd')
                                        {
                                            dirtyFound = true;
                                            cacheControllers[i].getCacheMemory().states[ba.moduleNumber][colNum] = 'i';
                                            if (isWaiting)
                                            {
                                                for (int j=0; j<waitingProcessorsNumbers.length; j++)
                                                {
                                                    if (j==curQuery.number) continue;
                                                    cacheControllers[j].getProcessor().addUnload(
                                                            modelingParameters.memoryCycleTime);
                                                }
                                            }
                                            break;
                                        }
                                    }
                                    int newCol;
                                    if ((newCol = cacheControllers[curQuery.number].getCacheMemory().searchFreePlace(ba.moduleNumber))
                                            != Const.NOT_FOUND)
                                    {
                                        cacheControllers[curQuery.number].getCacheMemory().tags[ba.moduleNumber][newCol] = ba.tag;
                                        cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][newCol] = 'd';
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.memoryCycleTime);
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.searchCachePointerTime + 2 * modelingParameters.bufferOperationTime);
                                        if (!dirtyFound && isWaiting)
                                        {
                                            for (int j=0; j<waitingProcessorsNumbers.length; j++)
                                            {
                                                cacheControllers[j].getProcessor().addUnload(
                                                        modelingParameters.memoryCycleTime);
                                            }
                                        }
                                    }
                                    else
                                    {
                                        int replBlock = new Random().nextInt(systemParameters.waysCount);
                                        if (cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][replBlock] == 'd')
                                        {
                                            cacheControllers[curQuery.number].getProcessor().addUnload(modelingParameters.memoryCycleTime);
                                            if (isWaiting)
                                            {
                                                for (int j=0; j<waitingProcessorsNumbers.length; j++)
                                                {
                                                    cacheControllers[j].getProcessor().addUnload(modelingParameters.memoryCycleTime);
                                                }
                                            }
                                        }
                                        cacheControllers[curQuery.number].getCacheMemory().tags[ba.moduleNumber][replBlock] = ba.tag;
                                        cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][replBlock] = 'd';
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.searchCachePointerTime);
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.memoryCycleTime);
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.bufferOperationTime);
                                        if (!dirtyFound && isWaiting)
                                        {
                                            for (int i=0; i<waitingProcessorsNumbers.length; i++)
                                            {
                                                cacheControllers[waitingProcessorsNumbers[i]].getProcessor().addUnload(
                                                     modelingParameters.memoryCycleTime);
                                            }
                                        }
                                    }
                                    for (int i=0; i<cacheControllers.length; i++)
                                    {
                                        if (i == curQuery.number) continue;
                                        cacheControllers[i].getProcessor().addUnload(
                                             modelingParameters.searchCachePointerTime);
                                        int invNum;
                                        if ((invNum = cacheControllers[i].getCacheMemory().searchBlock(ba))!= Const.NOT_FOUND)
                                        {
                                            cacheControllers[i].getCacheMemory().states[ba.moduleNumber][invNum] = 'i';
                                        }
                                    }
                                }
                                    break;           //END WRITE_MISS / WRITE_ONCE !!!
                            }
                        }
                            break;
 /////////////////////////////////////////////////////////////////////////////////////////////////////
                        case MSI:
                        {
                            switch (curQuery.queryType)
                            {
                                case READ_HIT:
                                //Do accordningly with the protocol of write once with replacement of status shared to valid
                                {
                                    if (cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][colNumber] != 'i')
                                    {
                                        switch (cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][colNumber])
                                        {
                                            case 'm':
                                                this.mStateCounter++;
                                                break;
                                            case 's':
                                                this.sStateCounter++;
                                        }
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                 modelingParameters.searchCachePointerTime + modelingParameters.bufferOperationTime);

                                    }
                                    else
                                    {
                                        this.iStateCounter++;
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                 modelingParameters.searchCachePointerTime);
                                        boolean modifFound = false;
                                        for (int i=0; i<cacheControllers.length; i++)
                                        {
                                            cacheControllers[i].getProcessor().addUnload(modelingParameters.searchCachePointerTime);
                                            int colNum = cacheControllers[i].getCacheMemory().searchBlock(ba);
                                            if (colNum != Const.NOT_FOUND &&
                                                    cacheControllers[i].getCacheMemory().states[ba.moduleNumber][colNum] == 'm')
                                            {
                                                modifFound = true;
                                                cacheControllers[i].getCacheMemory().states[ba.moduleNumber][colNum] = 's';
                                                if (isWaiting)
                                                {
                                                    for (int j=0; j<waitingProcessorsNumbers.length; j++)
                                                    {
                                                        if (j==curQuery.number) continue;
                                                        cacheControllers[j].getProcessor().addUnload(
                                                                modelingParameters.memoryCycleTime);
                                                    }
                                                }
                                                cacheControllers[curQuery.number].getProcessor().addUnload(
                                                        modelingParameters.memoryCycleTime + modelingParameters.bufferOperationTime);
                                                break;
                                            }
                                        }
                                        cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][colNumber] = 's';

                                        if (!modifFound)
                                        {
                                            cacheControllers[curQuery.number].getProcessor().addUnload(
                                                        modelingParameters.memoryCycleTime + modelingParameters.bufferOperationTime);
                                            if (isWaiting)
                                            {
                                                for (int j=0; j<waitingProcessorsNumbers.length; j++)
                                                {
                                                    if (j==curQuery.number) continue;
                                                    cacheControllers[j].getProcessor().addUnload(
                                                            modelingParameters.memoryCycleTime);
                                                }
                                            }
                                        }
                                    }
                                }
                                    break;     //END READ_HIT / MSI

                                case READ_MISS:
                                {
                                    //Determine if the block is in the state modified in any of the caches
                                    boolean modifFound = false;
                                    for (int i=0; i<cacheControllers.length; i++)
                                    {
                                        cacheControllers[i].getProcessor().addUnload(modelingParameters.searchCachePointerTime);
                                        int colNum = cacheControllers[i].getCacheMemory().searchBlock(ba);
                                        if (colNum != Const.NOT_FOUND &&
                                                cacheControllers[i].getCacheMemory().states[ba.moduleNumber][colNum] == 'm')
                                        {
                                            modifFound = true;
                                            cacheControllers[i].getCacheMemory().states[ba.moduleNumber][colNum] = 's';
                                            if (isWaiting)
                                            {
                                                for (int j=0; j<waitingProcessorsNumbers.length; j++)
                                                {
                                                    if (j==curQuery.number) continue;
                                                    cacheControllers[j].getProcessor().addUnload(
                                                            modelingParameters.memoryCycleTime);
                                                }
                                            }
                                            break;
                                        }
                                    }

                                    int newCol;
                                    if ((newCol = cacheControllers[curQuery.number].getCacheMemory().searchFreePlace(ba.moduleNumber))
                                            != Const.NOT_FOUND)
                                    {
                                        cacheControllers[curQuery.number].getCacheMemory().tags[ba.moduleNumber][newCol] = ba.tag;
                                        cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][newCol] = 's';
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.searchCachePointerTime);
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.memoryCycleTime);
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.bufferOperationTime);
                                        if (!modifFound && isWaiting)
                                        {
                                            for (int i=0; i<waitingProcessorsNumbers.length; i++)
                                            {
                                                cacheControllers[waitingProcessorsNumbers[i]].getProcessor().addUnload(
                                                     modelingParameters.memoryCycleTime);
                                            }
                                        }
                                    }
                                    else
                                    {
                                        int replBlock = new Random().nextInt(systemParameters.waysCount);

                                        int replTag = cacheControllers[curQuery.number].getCacheMemory().tags[ba.moduleNumber][replBlock];
                                        if (cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][replBlock] == 'm')
                                        {
                                            cacheControllers[curQuery.number].getProcessor().addUnload(modelingParameters.memoryCycleTime);
                                            if (isWaiting)
                                            {
                                                for (int j=0; j<waitingProcessorsNumbers.length; j++)
                                                {
                                                    cacheControllers[j].getProcessor().addUnload(modelingParameters.memoryCycleTime);
                                                }
                                            }
                                            for (int k=0; k<cacheControllers.length; k++)
                                            {
                                                if (k == curQuery.number) continue;
                                                int replCol = cacheControllers[k].getCacheMemory().searchBlock(new BlockAddress(ba.moduleNumber, replTag));
                                                if (replCol != Const.NOT_FOUND)
                                                {
                                                    cacheControllers[k].getCacheMemory().states[ba.moduleNumber][replCol] = 'i';
                                                }
                                            }
                                        }
                                        cacheControllers[curQuery.number].getCacheMemory().tags[ba.moduleNumber][replBlock] = ba.tag;
                                        cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][replBlock] = 's';
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.searchCachePointerTime);
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.memoryCycleTime);
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.bufferOperationTime);
                                        if (!modifFound && isWaiting)
                                        {
                                            for (int i=0; i<waitingProcessorsNumbers.length; i++)
                                            {
                                                cacheControllers[waitingProcessorsNumbers[i]].getProcessor().addUnload(
                                                     modelingParameters.memoryCycleTime);
                                            }
                                        }

                                    }
                                }
                                    break;     //END READ_MISS / MSI

                                case WRITE_HIT:
                                {
                                    switch (cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][colNumber])
                                    {
                                        case 'm':
                                            this.mStateCounter++;
                                            break;
                                        case 'i':
                                            this.iStateCounter++;
                                            break;
                                        case 's':
                                            this.sStateCounter++;
                                            break;
                                    }
                                    cacheControllers[curQuery.number].getProcessor().addUnload(
                                            modelingParameters.searchCachePointerTime);
                                    cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][colNumber] = 'm';
                                    cacheControllers[curQuery.number].getProcessor().addUnload(
                                        modelingParameters.bufferOperationTime);

                                    for (int i=0; i<cacheControllers.length; i++)
                                    {
                                        if (i == curQuery.number) continue;
                                        cacheControllers[i].getProcessor().addUnload(
                                             modelingParameters.searchCachePointerTime);
                                        int invNum;
                                        if ((invNum = cacheControllers[i].getCacheMemory().searchBlock(ba))!= Const.NOT_FOUND)
                                        {
                                            cacheControllers[i].getCacheMemory().states[ba.moduleNumber][invNum] = 'i';
                                        }
                                    }
                                }
                                    break;  //END WRITE_HIT / MSI

                                case WRITE_MISS:                                 {
                                    //Do accordingly with the scenario fo read-miss and also anylize the blocks in other caches
                                    boolean modifFound = false;
                                    for (int i=0; i<cacheControllers.length; i++)
                                    {
                                        cacheControllers[i].getProcessor().addUnload(modelingParameters.searchCachePointerTime);
                                        int colNum = cacheControllers[i].getCacheMemory().searchBlock(ba);
                                        if (colNum != Const.NOT_FOUND &&
                                                cacheControllers[i].getCacheMemory().states[ba.moduleNumber][colNum] == 'm')
                                        {
                                            modifFound = true;
                                            cacheControllers[i].getCacheMemory().states[ba.moduleNumber][colNum] = 'i';
                                        }
                                    }
                                    int newCol;
                                    if ((newCol = cacheControllers[curQuery.number].getCacheMemory().searchFreePlace(ba.moduleNumber))
                                            != Const.NOT_FOUND)
                                    {
                                        cacheControllers[curQuery.number].getCacheMemory().tags[ba.moduleNumber][newCol] = ba.tag;
                                        cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][newCol] = 'm';
                                        if (modifFound)
                                        {
                                            cacheControllers[curQuery.number].getProcessor().addUnload(
                                                    modelingParameters.bufferOperationTime + modelingParameters.searchCachePointerTime);
                                        }
                                        else
                                        {
                                            cacheControllers[curQuery.number].getProcessor().addUnload(
                                                    modelingParameters.bufferOperationTime +
                                                    modelingParameters.memoryCycleTime +
                                                    modelingParameters.searchCachePointerTime);
                                        }

                                        if (isWaiting)
                                        {
                                            for (int j=0; j<waitingProcessorsNumbers.length; j++)
                                            {
                                                if (!modifFound)
                                                {
                                                    cacheControllers[j].getProcessor().addUnload(
                                                            modelingParameters.memoryCycleTime);
                                                }
                                                else
                                                {
                                                    cacheControllers[j].getProcessor().addUnload(
                                                        modelingParameters.bufferOperationTime +
                                                        modelingParameters.searchCachePointerTime);
                                                }
                                            }
                                        }
                                    }
                                    else
                                    {
                                        int replBlock = new Random().nextInt(systemParameters.waysCount);
                                        if (cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][replBlock] == 'm')
                                        {
                                            cacheControllers[curQuery.number].getProcessor().addUnload(modelingParameters.memoryCycleTime);
                                            if (isWaiting)
                                            {
                                                for (int j=0; j<waitingProcessorsNumbers.length; j++)
                                                {
                                                    cacheControllers[j].getProcessor().addUnload(modelingParameters.memoryCycleTime);
                                                }
                                            }
                                        }
                                        cacheControllers[curQuery.number].getCacheMemory().tags[ba.moduleNumber][replBlock] = ba.tag;
                                        cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][replBlock] = 'm';
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.searchCachePointerTime);
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.memoryCycleTime);
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.bufferOperationTime);
                                        if (isWaiting)
                                        {
                                            for (int i=0; i<waitingProcessorsNumbers.length; i++)
                                            {
                                                if (!modifFound)
                                                {
                                                    cacheControllers[waitingProcessorsNumbers[i]].getProcessor().addUnload(
                                                         modelingParameters.memoryCycleTime);
                                                }
                                                else
                                                {
                                                    cacheControllers[i].getProcessor().addUnload(
                                                        modelingParameters.bufferOperationTime +
                                                        modelingParameters.searchCachePointerTime);
                                                }
                                            }
                                        }
                                    }
                                    for (int i=0; i<cacheControllers.length; i++)
                                    {
                                        if (i == curQuery.number) continue;
                                        cacheControllers[i].getProcessor().addUnload(
                                             modelingParameters.searchCachePointerTime);
                                        int invNum;
                                        if ((invNum = cacheControllers[i].getCacheMemory().searchBlock(ba))!= Const.NOT_FOUND)
                                        {
                                            cacheControllers[i].getCacheMemory().states[ba.moduleNumber][invNum] = 'i';
                                        }
                                    }
                                }
                                    break;  //END WRITE_MISS / MSI
                            }
                        }
                            break;

                        case MESI:
                        //Protocol MESI is an extended version of protocol MSI.
                        //We define new status Exclusive
                        {
                            switch (curQuery.queryType)
                            {
                                case READ_HIT:
                                {
                                    switch (cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][colNumber])
                                    {
                                        case 'm':
                                            this.mStateCounter++;
                                            break;
                                        case 'i':
                                            this.iStateCounter++;
                                            break;
                                        case 's':
                                            this.sStateCounter++;
                                            break;
                                        case 'e':
                                            this.eStateCounter++;
                                            break;
                                    }
                                    if (cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][colNumber] != 'i')
                                    {
                                         cacheControllers[curQuery.number].getProcessor().addUnload(
                                                 modelingParameters.searchCachePointerTime + modelingParameters.bufferOperationTime);

                                    }
                                    else
                                    {
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                 modelingParameters.searchCachePointerTime);
                                        boolean modifFound = false;
                                        boolean blockFound = false;
                                        for (int i=0; i<cacheControllers.length; i++)
                                        {
                                            cacheControllers[i].getProcessor().addUnload(modelingParameters.searchCachePointerTime);
                                            int colNum = cacheControllers[i].getCacheMemory().searchBlock(ba);
                                            if (colNum != Const.NOT_FOUND)
                                            {
                                                blockFound = true;
                                                if (cacheControllers[i].getCacheMemory().states[ba.moduleNumber][colNum] == 'm')
                                                {
                                                    modifFound = true;
                                                    cacheControllers[i].getCacheMemory().states[ba.moduleNumber][colNum] = 's';
                                                    if (isWaiting)
                                                    {
                                                        for (int j=0; j<waitingProcessorsNumbers.length; j++)
                                                        {
                                                            if (j==curQuery.number) continue;
                                                            cacheControllers[j].getProcessor().addUnload(
                                                                    modelingParameters.memoryCycleTime);
                                                        }
                                                    }
                                                    cacheControllers[curQuery.number].getProcessor().addUnload(
                                                            modelingParameters.memoryCycleTime + modelingParameters.bufferOperationTime);
                                                    break;
                                                }
                                            }
                                        }
                                        //Change the status of the block either on shared or exclusive. It depends if there is a copy of a block in other cache.
                                        if (blockFound)
                                        {
                                            cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][colNumber] = 's';
                                        }
                                        else
                                        {
                                            cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][colNumber] = 'e';
                                        }

                                        if (!modifFound)
                                        {
                                            cacheControllers[curQuery.number].getProcessor().addUnload(
                                                        modelingParameters.memoryCycleTime + modelingParameters.bufferOperationTime);
                                            if (isWaiting)
                                            {
                                                for (int j=0; j<waitingProcessorsNumbers.length; j++)
                                                {
                                                    if (j==curQuery.number) continue;
                                                    cacheControllers[j].getProcessor().addUnload(
                                                            modelingParameters.memoryCycleTime);
                                                }
                                            }
                                        }
                                    }
                                }
                                    break;

                                case READ_MISS:
                                {
                                    boolean modifFound = false;
                                    boolean blockFound = false;
                                    for (int i=0; i<cacheControllers.length; i++)
                                    {
                                        cacheControllers[i].getProcessor().addUnload(modelingParameters.searchCachePointerTime);
                                        int colNum = cacheControllers[i].getCacheMemory().searchBlock(ba);
                                        if (colNum != Const.NOT_FOUND)
                                        {
                                            blockFound = true;
                                            if (cacheControllers[i].getCacheMemory().states[ba.moduleNumber][colNum] == 'm')
                                            {
                                                modifFound = true;
                                                cacheControllers[i].getCacheMemory().states[ba.moduleNumber][colNum] = 's';
                                                if (isWaiting)
                                                {
                                                    for (int j=0; j<waitingProcessorsNumbers.length; j++)
                                                    {
                                                        if (j==curQuery.number) continue;
                                                        cacheControllers[j].getProcessor().addUnload(
                                                                modelingParameters.memoryCycleTime);
                                                    }
                                                }
                                                break;
                                            }
                                        }
                                    }
                                    int newCol;
                                    if ((newCol = cacheControllers[curQuery.number].getCacheMemory().searchFreePlace(ba.moduleNumber))
                                            != Const.NOT_FOUND)
                                    {
                                        cacheControllers[curQuery.number].getCacheMemory().tags[ba.moduleNumber][newCol] = ba.tag;
                                        if (blockFound)
                                        {
                                            cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][newCol] = 's';
                                        }
                                        else
                                        {
                                            cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][newCol] = 'e';
                                        }

                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.searchCachePointerTime);
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.memoryCycleTime);
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.bufferOperationTime);
                                        if (!modifFound && isWaiting)
                                        {
                                            for (int i=0; i<waitingProcessorsNumbers.length; i++)
                                            {
                                                cacheControllers[waitingProcessorsNumbers[i]].getProcessor().addUnload(
                                                     modelingParameters.memoryCycleTime);
                                            }
                                        }
                                    }
                                    else
                                    {
                                        int replBlock = new Random().nextInt(systemParameters.waysCount);

                                        int replTag = cacheControllers[curQuery.number].getCacheMemory().tags[ba.moduleNumber][replBlock];
                                        if (cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][replBlock] == 'm')
                                        {
                                            cacheControllers[curQuery.number].getProcessor().addUnload(modelingParameters.memoryCycleTime);
                                            if (isWaiting)
                                            {
                                                for (int j=0; j<waitingProcessorsNumbers.length; j++)
                                                {
                                                    cacheControllers[j].getProcessor().addUnload(modelingParameters.memoryCycleTime);
                                                }
                                            }
                                            for (int k=0; k<cacheControllers.length; k++)
                                            {
                                                if (k == curQuery.number) continue;
                                                int replCol = cacheControllers[k].getCacheMemory().searchBlock(new BlockAddress(ba.moduleNumber, replTag));
                                                if (replCol != Const.NOT_FOUND)
                                                {
                                                    cacheControllers[k].getCacheMemory().states[ba.moduleNumber][replCol] = 'i';
                                                }
                                            }
                                        }
                                        else
                                        {
                                            int replCount = 0;
                                            int replCol = 0;
                                            int lastReplCol = 0;
                                            int lastReplFound = 0;
                                            for (int k=0; k<cacheControllers.length; k++)
                                            {
                                                if (k == curQuery.number) continue;
                                                replCol = cacheControllers[k].getCacheMemory().searchBlock(new BlockAddress(ba.moduleNumber, replTag));
                                                if (replCol != Const.NOT_FOUND)
                                                {
                                                   replCount++;
                                                   lastReplFound = k;
                                                   lastReplCol = replCol;
                                                }
                                            }
                                            if (replCount == 1)
                                            {
                                                try
                                                {
                                                    cacheControllers[lastReplFound].getCacheMemory().states[ba.moduleNumber][lastReplCol] = 'e';
                                                }
                                                catch (Exception ex)
                                                {
                                                    System.out.println(" --- !!! --- " + lastReplFound + " " + ba.moduleNumber + " " + replCol + "\n");
                                                }
                                            }
                                        }
                                        cacheControllers[curQuery.number].getCacheMemory().tags[ba.moduleNumber][replBlock] = ba.tag;
                                        if (blockFound)
                                        {
                                            cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][replBlock] = 's';
                                        }
                                        else
                                        {
                                            cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][replBlock] = 'e';
                                        }
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.searchCachePointerTime);
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.memoryCycleTime);
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.bufferOperationTime);
                                        if (!modifFound && isWaiting)
                                        {
                                            for (int i=0; i<waitingProcessorsNumbers.length; i++)
                                            {
                                                cacheControllers[waitingProcessorsNumbers[i]].getProcessor().addUnload(
                                                     modelingParameters.memoryCycleTime);
                                            }
                                        }
                                    }
                                }
                                    break;

                                case WRITE_HIT:
                                {
                                    switch (cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][colNumber])
                                    {
                                        case 'm':
                                            this.mStateCounter++;
                                            break;
                                        case 'i':
                                            this.iStateCounter++;
                                            break;
                                        case 's':
                                            this.sStateCounter++;
                                            break;
                                        case 'e':
                                            this.eStateCounter++;
                                            break;
                                    }
                                    cacheControllers[curQuery.number].getProcessor().addUnload(
                                            modelingParameters.searchCachePointerTime);
                                    char state = cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][colNumber];
                                    cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][colNumber] = 'm';
                                    cacheControllers[curQuery.number].getProcessor().addUnload(
                                        modelingParameters.bufferOperationTime);
                                    if (state != 'e')
                                    {
                                        for (int i=0; i<cacheControllers.length; i++)
                                        {
                                            if (i == curQuery.number) continue;
                                            cacheControllers[i].getProcessor().addUnload(
                                                 modelingParameters.searchCachePointerTime);
                                            int invNum;
                                            if ((invNum = cacheControllers[i].getCacheMemory().searchBlock(ba))!= Const.NOT_FOUND)
                                            {
                                                cacheControllers[i].getCacheMemory().states[ba.moduleNumber][invNum] = 'i';
                                            }
                                        }
                                    }
                                }
                                    break;

                                case WRITE_MISS:
                                {
                                    boolean modifFound = false;
                                    for (int i=0; i<cacheControllers.length; i++)
                                    {
                                        cacheControllers[i].getProcessor().addUnload(modelingParameters.searchCachePointerTime);
                                        int colNum = cacheControllers[i].getCacheMemory().searchBlock(ba);
                                        if (colNum != Const.NOT_FOUND &&
                                                cacheControllers[i].getCacheMemory().states[ba.moduleNumber][colNum] == 'm')
                                        {
                                            modifFound = true;
                                            cacheControllers[i].getCacheMemory().states[ba.moduleNumber][colNum] = 'i';
                                        }
                                    }
                                    int newCol;
                                    if ((newCol = cacheControllers[curQuery.number].getCacheMemory().searchFreePlace(ba.moduleNumber))
                                            != Const.NOT_FOUND)
                                    {
                                        cacheControllers[curQuery.number].getCacheMemory().tags[ba.moduleNumber][newCol] = ba.tag;
                                        cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][newCol] = 'm';
                                        if (modifFound)
                                        {
                                            cacheControllers[curQuery.number].getProcessor().addUnload(
                                                    modelingParameters.bufferOperationTime + modelingParameters.searchCachePointerTime);
                                        }
                                        else
                                        {
                                            cacheControllers[curQuery.number].getProcessor().addUnload(
                                                    modelingParameters.bufferOperationTime +
                                                    modelingParameters.memoryCycleTime +
                                                    modelingParameters.searchCachePointerTime);
                                        }
                                        if (isWaiting)
                                        {
                                            for (int j=0; j<waitingProcessorsNumbers.length; j++)
                                            {
                                                if (!modifFound)
                                                {
                                                    cacheControllers[j].getProcessor().addUnload(
                                                            modelingParameters.memoryCycleTime);
                                                }
                                                else
                                                {
                                                    cacheControllers[j].getProcessor().addUnload(
                                                        modelingParameters.bufferOperationTime +
                                                        modelingParameters.searchCachePointerTime);
                                                }
                                            }
                                        }
                                    }
                                    else
                                    {
                                        int replBlock = new Random().nextInt(systemParameters.waysCount);

                                        int replTag = cacheControllers[curQuery.number].getCacheMemory().tags[ba.moduleNumber][replBlock];
                                        if (cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][replBlock] == 'm')
                                        {
                                            cacheControllers[curQuery.number].getProcessor().addUnload(modelingParameters.memoryCycleTime);

                                            for (int k=0; k<cacheControllers.length; k++)
                                            {
                                                if (k == curQuery.number) continue;
                                                int replCol = cacheControllers[k].getCacheMemory().searchBlock(new BlockAddress(ba.moduleNumber, replTag));
                                                if (replCol != Const.NOT_FOUND)
                                                {
                                                    cacheControllers[k].getCacheMemory().states[ba.moduleNumber][replCol] = 'i';
                                                }
                                            }
                                            if (isWaiting)
                                            {
                                                for (int j=0; j<waitingProcessorsNumbers.length; j++)
                                                {
                                                    cacheControllers[j].getProcessor().addUnload(modelingParameters.memoryCycleTime);
                                                }
                                            }
                                        }
                                        else
                                        {
                                            int replCount = 0;
                                            int replCol = 0;
                                            int lastReplCol = 0;
                                            int lastReplFound = 0;
                                            for (int k=0; k<cacheControllers.length; k++)
                                            {
                                                if (k == curQuery.number) continue;
                                                replCol = cacheControllers[k].getCacheMemory().searchBlock(new BlockAddress(ba.moduleNumber, replTag));
                                                if (replCol != Const.NOT_FOUND)
                                                {
                                                   replCount++;
                                                   lastReplFound = k;
                                                   lastReplCol = replCol;
                                                }
                                            }
                                            if (replCount == 1)
                                            {
                                                try
                                                {
                                                    cacheControllers[lastReplFound].getCacheMemory().states[ba.moduleNumber][lastReplCol] = 'e';
                                                }
                                                catch (Exception ex)
                                                {
                                                    System.out.println(" --- !!! --- " + lastReplFound + " " + ba.moduleNumber + " " + replCol + "\n");
                                                }
                                            }
                                        }
                                        cacheControllers[curQuery.number].getCacheMemory().tags[ba.moduleNumber][replBlock] = ba.tag;
                                        cacheControllers[curQuery.number].getCacheMemory().states[ba.moduleNumber][replBlock] = 'm';
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.searchCachePointerTime);
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.memoryCycleTime);
                                        cacheControllers[curQuery.number].getProcessor().addUnload(
                                                modelingParameters.bufferOperationTime);
                                        if (isWaiting)
                                        {
                                            for (int i=0; i<waitingProcessorsNumbers.length; i++)
                                            {
                                                if (!modifFound)
                                                {
                                                    cacheControllers[waitingProcessorsNumbers[i]].getProcessor().addUnload(
                                                         modelingParameters.memoryCycleTime);
                                                }
                                                else
                                                {
                                                    cacheControllers[i].getProcessor().addUnload(
                                                        modelingParameters.bufferOperationTime +
                                                        modelingParameters.searchCachePointerTime);
                                                }
                                            }
                                        }
                                    }
                                    for (int i=0; i<cacheControllers.length; i++)
                                    {
                                        if (i == curQuery.number) continue;
                                        cacheControllers[i].getProcessor().addUnload(
                                             modelingParameters.searchCachePointerTime);
                                        int invNum;
                                        if ((invNum = cacheControllers[i].getCacheMemory().searchBlock(ba))!= Const.NOT_FOUND)
                                        {
                                            cacheControllers[i].getCacheMemory().states[ba.moduleNumber][invNum] = 'i';
                                            cacheControllers[i].getProcessor().addUnload(
                                             modelingParameters.searchCachePointerTime);
                                        }
                                    }
                                }
                                break;
                            }
                        }
                        break;

                    }

                    this.cacheControllers[curQuery.number].getProcessor().restart();
                    try
                    {
                        systemBusThread.sleep(5 * modelingParameters.systemBusDelay);
                    }
                    catch (Exception ex) {}
                }

            }
            //Modeling is complete. Stopping the work of the bus
            else
            {
                this.systemBusThread.stop();
                break;
            }

        }
    }


    public double getSharedQueriesCount()
    {
        return (double)sharedQueries / allQueries * 100;
    }

    public int getEStatesCounter()
    {
        return this.eStateCounter;
    }

    public int getMStatesCounter()
    {
        return this.mStateCounter;
    }

    public int getSStatesCounter()
    {
        return this.sStateCounter;
    }

    public int getIStatesCounter()
    {
        return this.iStateCounter;
    }

    public void start()
    {
        systemBusThread.start();
    }
}
